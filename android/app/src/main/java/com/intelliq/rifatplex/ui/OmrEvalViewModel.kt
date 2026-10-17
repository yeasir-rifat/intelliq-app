package com.intelliq.rifatplex.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intelliq.rifatplex.data.AlignResult
import com.intelliq.rifatplex.data.AnswerKeyImport
import com.intelliq.rifatplex.data.AnswerKeySnapshot
import com.intelliq.rifatplex.data.AnswerKeyStore
import com.intelliq.rifatplex.data.KeyExisting
import com.intelliq.rifatplex.data.KeyImport
import com.intelliq.rifatplex.data.KeyPdfLoader
import com.intelliq.rifatplex.data.KeyQrAssembly
import com.intelliq.rifatplex.data.KeyScan
import com.intelliq.rifatplex.data.BubbleSet
import com.intelliq.rifatplex.data.ExamId
import com.intelliq.rifatplex.data.GradeOptions
import com.intelliq.rifatplex.data.GradingRules
import com.intelliq.rifatplex.data.OmrAlign
import com.intelliq.rifatplex.data.OmrFlag
import com.intelliq.rifatplex.data.OmrGrade
import com.intelliq.rifatplex.data.OmrImageLoader
import com.intelliq.rifatplex.data.OmrLayout
import com.intelliq.rifatplex.data.OmrReadParams
import com.intelliq.rifatplex.data.OmrReader
import com.intelliq.rifatplex.data.OmrResult
import com.intelliq.rifatplex.data.OmrSheetRead
import com.intelliq.rifatplex.data.QrStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/*
 * Step 4.6 - the state behind the "OMR Evaluation" screen.
 *
 * What it holds: one [EvalSheet] per photographed sheet (a small JPEG preview, the alignment, what the reader saw, the grade).
 * The full photo is NOT kept: it is read, aligned and measured once on a background thread and dropped, so 50 sheets cost
 * about 50 x 150 KB of previews, not 50 bitmaps. Because the measured darkness of every bubble is kept, changing the marking
 * rules or the fill thresholds re-grades every sheet at once without opening a photo again.
 *
 * The ViewModel outlives screen rotation (it is created from the activity's store), so a running batch is not lost.
 */

internal enum class EvalState { OK, REVIEW, UNMATCHED, FAILED }

internal data class EvalSheet(
    val id: Int,
    val name: String,
    val imgW: Int,
    val imgH: Int,
    /** Small JPEG of the photo for the detail view, null when the photo could not be opened at all. */
    val jpeg: ByteArray?,
    val align: AlignResult.Ok?,
    /** Translation key of the failure ("scan.fail.X" / "scan.err.read") when the sheet was not aligned. */
    val failKey: String?,
    val failDetail: String,
    val read: OmrSheetRead?,
    /** The teacher's corrections: row slot -> options really marked (empty = blank). */
    val overrides: Map<Int, Set<Int>> = emptyMap(),
    /** The teacher's correction of the roll number, null = as read. */
    val rollFix: String? = null,
    val result: OmrResult? = null,
    val flags: Set<OmrFlag> = emptySet(),
    val state: EvalState,
    val ms: Long = 0,
) {
    /** The roll number to show: the fix if there is one, else what was read ("?" = a column that was not clearly filled). */
    val roll: String? get() = rollFix ?: result?.roll ?: read?.roll?.text
}

private fun OmrSheetRead.withRows(rows: List<BubbleSet>) = OmrSheetRead(
    qr = qr, rows = rows, roll = roll, reg = reg, subject = subject, setColumn = setColumn, setFilled = setFilled,
    paperLevel = paperLevel, inkLevel = inkLevel, lowContrast = lowContrast, outOfImage = outOfImage, millis = millis,
)

/**
 * Reads a number typed on any keyboard: Bangla or other Unicode digits ("০.২৫"), a comma as the decimal mark. Blank or not a
 * number gives null. (Plain toDoubleOrNull() only knows 0-9, so a rule typed in Bangla digits used to be ignored silently.)
 */
internal fun parseRuleNumber(text: String): Double? {
    val t = text.trim().map { ch -> ch.digitToIntOrNull()?.let { '0' + it } ?: if (ch == ',') '.' else ch }.joinToString("")
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal class OmrEvalViewModel : ViewModel() {
    val sheets = mutableStateListOf<EvalSheet>()

    /** Photos still waiting to be read. */
    var pending by mutableStateOf(0)
        private set

    // ---- marking rules (an empty text = use the rules saved with the exam's answer snapshot) ----
    var marksText by mutableStateOf("")
    var negText by mutableStateOf("")
    var penalizeMulti by mutableStateOf(false)

    @Volatile private var layout = OmrLayout()

    /** The exam on screen: used when a sheet has no readable QR code. */
    @Volatile private var fallbackExam: String? = null
    @Volatile private var keys: AnswerKeyStore? = null
    private var appCtx: Context? = null
    private var counter = 0
    private var prefsLoaded = false

    /** Called from the screen on every recomposition (cheap): tells the ViewModel which printed layout and exam are current. */
    fun bind(ctx: Context, layout: OmrLayout, examId: String) {
        val app = ctx.applicationContext
        appCtx = app
        keys = AnswerKeyStore.get(app)
        this.layout = layout
        fallbackExam = examId.takeIf { ExamId.isValid(it) }
        if (!prefsLoaded) {
            prefsLoaded = true
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            marksText = p.getString("marks", "") ?: ""
            negText = p.getString("neg", "") ?: ""
            penalizeMulti = p.getBoolean("multi", false)
        }
    }

    /** Call after any marking rule was edited: saves them and grades every sheet again. */
    fun settingsChanged() {
        appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("marks", marksText)?.putString("neg", negText)?.putBoolean("multi", penalizeMulti)
            // the reading thresholds are fixed (an older version saved them: drop those values)
            ?.remove("fill")?.remove("blank")?.apply()
        for (i in sheets.indices) sheets[i] = evaluate(sheets[i])
    }

    // ---- adding photos ----

    /** Reads the photos one after the other in the background. [temp] (a camera file) is deleted afterwards. */
    fun add(uris: List<Uri>, temp: File?) {
        val app = appCtx ?: return
        if (uris.isEmpty()) return
        pending += uris.size
        viewModelScope.launch {
            try {
                for (u in uris) {
                    counter += 1
                    val id = counter
                    try {
                        val s = try {
                            withContext(Dispatchers.Default) { process(app, u, id) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            // one bad photo (out of memory, an unexpected reader error) must not crash the app or stop the batch
                            EvalSheet(id, "#$id", 0, 0, null, null, "scan.err.read", "", null, state = EvalState.FAILED)
                        }
                        sheets.add(s)
                    } finally {
                        pending -= 1
                    }
                }
            } finally {
                temp?.delete()
            }
        }
    }

    private fun process(app: Context, uri: Uri, id: Int): EvalSheet {
        val name = "#$id"
        val t0 = System.currentTimeMillis()
        val img = OmrImageLoader.load(app.contentResolver, uri)
            ?: return EvalSheet(id, name, 0, 0, null, null, "scan.err.read", "", null, state = EvalState.FAILED, ms = System.currentTimeMillis() - t0)
        val jpeg = try {
            val bmp = OmrImageLoader.toBitmap(img, PREVIEW_SIDE)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
            bmp.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        return when (val a = OmrAlign.align(img)) {
            is AlignResult.Failed -> EvalSheet(
                id, name, img.w, img.h, jpeg, null, "scan.fail." + a.reason.name, a.detail, null,
                state = EvalState.FAILED, ms = System.currentTimeMillis() - t0,
            )
            is AlignResult.Ok -> {
                val read = OmrReader.read(img, a, layout)
                evaluate(EvalSheet(id, name, img.w, img.h, jpeg, a, null, "", read, state = EvalState.FAILED, ms = System.currentTimeMillis() - t0))
            }
        }
    }

    // ---- the teacher's corrections ----

    private fun indexOf(id: Int) = sheets.indexOfFirst { it.id == id }

    /** [options] = the options really marked in row [slot] (empty = blank); null removes the correction. */
    fun setOverride(id: Int, slot: Int, options: Set<Int>?) {
        val i = indexOf(id)
        if (i < 0) return
        val s = sheets[i]
        val m = s.overrides.toMutableMap()
        if (options == null) m.remove(slot) else m[slot] = options
        sheets[i] = evaluate(s.copy(overrides = m))
    }

    fun setRoll(id: Int, text: String?) {
        val i = indexOf(id)
        if (i < 0) return
        sheets[i] = evaluate(sheets[i].copy(rollFix = text?.takeIf { it.isNotBlank() }))
    }

    fun remove(id: Int) {
        sheets.removeAll { it.id == id }
    }

    /** Removes every sheet and every photo file still on the device (camera temp files). Only results leave the phone, never photos. */
    fun clear() {
        sheets.clear()
        try {
            appCtx?.let { File(it.cacheDir, "omr").listFiles()?.forEach { f -> f.delete() } }
        } catch (e: Exception) {
            // nothing to delete
        }
    }

    // ---- importing an answer sheet by its QR codes (step 4.9) ----

    /** The answer-sheet pages scanned so far. */
    var keyImport by mutableStateOf(KeyImport())
        private set

    /** Translation key of the last import message (with {n} / {m} / {p} filled by [keyArg]), null = none. */
    var keyMsg by mutableStateOf<String?>(null)
        private set
    var keyMsgArg by mutableStateOf("")
        private set

    /** Pictures / PDF pages still being read for the import. */
    var keyBusy by mutableStateOf(0)
        private set

    /** Set after a successful save, to show the confirmation. */
    var keySaved by mutableStateOf(false)
        private set

    private fun setKeyMsg(key: String?, arg: String = "") {
        keyMsg = key
        keyMsgArg = arg
    }

    /** What the snapshot made from the scanned pages is, and how it relates to the one already kept. Null until all pages are in. */
    fun keyResult(): Pair<AnswerKeySnapshot, KeyExisting>? {
        val a = keyImport.assembly as? KeyQrAssembly.Done ?: return null
        val old = keys?.get(a.snapshot.examId, a.snapshot.setIndex)
        return a.snapshot to AnswerKeyImport.existing(old, a.snapshot)
    }

    private fun takeScan(sc: KeyScan): String? { // returns a message key when the picture was no usable page
        return when (sc) {
            is KeyScan.Page -> { keyImport = keyImport.with(sc.payload); null }
            KeyScan.OtherVersion -> "keyimp.otherVersion"
            KeyScan.NotKey -> "keyimp.notKey"
            KeyScan.None -> "keyimp.noQr"
        }
    }

    /** Reads photos of answer-sheet pages (camera or gallery). */
    fun addKeyImages(uris: List<Uri>, temp: File?) {
        val app = appCtx ?: return
        if (uris.isEmpty()) return
        keySaved = false
        setKeyMsg(null)
        keyBusy += uris.size
        viewModelScope.launch {
            var bad: String? = null
            try {
                for (u in uris) {
                    try {
                        val sc = try {
                            withContext(Dispatchers.Default) {
                                val img = OmrImageLoader.load(app.contentResolver, u, luma = true)
                                if (img == null) null else AnswerKeyImport.readImage(img)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            null
                        }
                        val m = if (sc == null) "scan.err.read" else takeScan(sc)
                        if (m != null) bad = m
                    } finally {
                        keyBusy -= 1
                    }
                }
            } finally {
                temp?.delete()
            }
            afterKeyScan(bad, 0)
        }
    }

    /** Reads an answer-sheet PDF, one page at a time. */
    fun addKeyPdf(uri: Uri) {
        val app = appCtx ?: return
        keySaved = false
        setKeyMsg(null)
        keyBusy += 1
        viewModelScope.launch {
            val r = try {
                withContext(Dispatchers.Default) { KeyPdfLoader.scan(app.contentResolver, uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            } finally {
                keyBusy -= 1
            }
            if (r == null) {
                setKeyMsg("keyimp.pdfUnreadable")
                return@launch
            }
            var found = 0
            var other = false
            var foreign = false
            for (sc in r.scans) {
                when (sc) {
                    is KeyScan.Page -> { keyImport = keyImport.with(sc.payload); found++ }
                    KeyScan.OtherVersion -> other = true
                    KeyScan.NotKey -> foreign = true
                    KeyScan.None -> {}
                }
            }
            val bad = when {
                found > 0 -> null
                other -> "keyimp.otherVersion"
                foreign -> "keyimp.notKey"
                else -> "keyimp.pdfNoQr"
            }
            afterKeyScan(bad, r.scans.size - found)
        }
    }

    private fun afterKeyScan(bad: String?, pagesWithoutQr: Int) {
        if (bad != null) {
            setKeyMsg(bad)
            return
        }
        val a = keyImport.assembly
        if (a is KeyQrAssembly.Mismatch) setKeyMsg("keyimp.mismatch")
        else if (pagesWithoutQr > 0 && a is KeyQrAssembly.Missing) setKeyMsg("keyimp.pdfSomeNoQr", pagesWithoutQr.toString())
    }

    /** Takes back the page added last (after "these pages are not from one answer sheet"). */
    fun keyDropLast() {
        keyImport = keyImport.dropLast()
        setKeyMsg(if (keyImport.assembly is KeyQrAssembly.Mismatch) "keyimp.mismatch" else null)
    }

    fun keyReset() {
        keyImport = KeyImport()
        keySaved = false
        setKeyMsg(null)
    }

    /** Keeps the scanned answer snapshot. [replace] must be true when a different one is already kept (the teacher said so). */
    fun keySave(replace: Boolean) {
        val (snap, ex) = keyResult() ?: return
        if (ex == KeyExisting.DIFFERENT && !replace) return
        val store = keys ?: return
        if (ex != KeyExisting.SAME) {
            // a snapshot made now is newer than the old one, so cloud sync and putRemote keep it
            store.put(snap.copy(createdAt = System.currentTimeMillis()))
        }
        keyImport = KeyImport()
        keySaved = true
        setKeyMsg(null)
        for (i in sheets.indices) sheets[i] = evaluate(sheets[i]) // sheets that had no key are graded now
    }

    // ---- grading ----

    /** The reading thresholds are always the defaults: they are not a setting any more. */
    private fun params(): OmrReadParams =
        OmrReadParams(fillThreshold = DEFAULT_FILL.toDouble(), blankThreshold = DEFAULT_BLANK.toDouble())

    /** Sorts the rows with the current thresholds, picks the answer snapshot and marks the sheet. */
    private fun evaluate(s: EvalSheet): EvalSheet {
        val rd0 = s.read ?: return s
        val p = params()
        val rd = rd0.withRows(rd0.rows.map { OmrReader.classify(it.darkness, p) })
        // the teacher photographed a page of the answer sheet: there is nothing to mark, whatever exam is on screen
        if (rd.qr is QrStatus.KeyPage) {
            return s.copy(result = null, flags = LinkedHashSet<OmrFlag>().apply { add(OmrFlag.QR_KEY_PAGE) }, state = EvalState.UNMATCHED)
        }
        val qr = rd.qrPayload
        val examId = qr?.examId ?: fallbackExam
        val store = keys
        val snaps = if (examId != null && store != null) store.forExam(examId) else emptyList()
        val pick = OmrGrade.pickSnapshot(snaps, qr?.setIndex, rd.setFilled)
        val page = qr?.page ?: 0
        val snap = pick.snapshot?.takeIf { page < it.sheets }
        if (snap == null) {
            val fl = LinkedHashSet<OmrFlag>(pick.flags)
            when (rd.qr) {
                is QrStatus.None -> fl += OmrFlag.QR_MISSING
                is QrStatus.OtherVersion -> fl += OmrFlag.QR_OTHER_VERSION
                is QrStatus.Foreign -> fl += OmrFlag.QR_FOREIGN
                is QrStatus.KeyPage -> fl += OmrFlag.QR_KEY_PAGE
                is QrStatus.Found -> {}
            }
            if (examId == null || pick.snapshot != null) fl += OmrFlag.NO_SNAPSHOT
            return s.copy(result = null, flags = fl, state = EvalState.UNMATCHED)
        }
        val m = parseRuleNumber(marksText)
        val n = parseRuleNumber(negText)
        val rules = if (m == null && n == null) null else GradingRules(m ?: snap.marksPerQ, n ?: snap.negativePerWrong)
        val res = OmrGrade.grade(rd, snap, page, qr?.pages ?: 1, rules, GradeOptions(penalizeMulti), s.overrides, pick.flags)
        var fl = res.flags - OmrFlag.KEY_INCOMPLETE // a teacher's key problem, not a problem of this sheet
        if (s.rollFix != null) fl = fl - OmrFlag.ROLL_INCOMPLETE
        return s.copy(result = res, flags = res.flags, state = if (fl.isEmpty()) EvalState.OK else EvalState.REVIEW)
    }

    companion object {
        private const val PREFS = "intelliq_eval"
        private const val PREVIEW_SIDE = 1600
        const val DEFAULT_FILL = "0.35"
        const val DEFAULT_BLANK = "0.15"
    }
}
