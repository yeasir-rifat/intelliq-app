package com.intelliq.rifatplex.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.intelliq.rifatplex.data.AlignResult
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.KeyExisting
import com.intelliq.rifatplex.data.KeyQrAssembly
import com.intelliq.rifatplex.data.OmrFlag
import com.intelliq.rifatplex.data.OmrGeometry
import com.intelliq.rifatplex.data.OmrLayout
import com.intelliq.rifatplex.data.OmrReport
import com.intelliq.rifatplex.data.QResult
import com.intelliq.rifatplex.data.QStatus
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Step 4.6 - "OMR Evaluation": photograph (or pick) filled sheets, see the marks, fix what the reader was unsure about.
 *
 * The drawer section holds the camera / gallery buttons, the marking rules, a summary and one card per sheet. A card opens
 * the detail dialog: the photo with the right answers (green), the student's wrong marks (red) and marks to look at (amber)
 * drawn at the printed bubble positions, the roll number (editable) and the rows with their options as tap-to-correct chips.
 * All work happens in [OmrEvalViewModel]; photos stay on the device and only a small preview of each is kept.
 */

private val AMBER = Color(0xFFD97706)
private val GREEN = Color(0xFF16A34A)
private val RED = Color(0xFFDC2626)
private val BLUE = Color(0xFF2563EB)

private tailrec fun Context.findComponentActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> error("no activity")
}

private fun cleanNumber(v: String): String = v.filter { it.isDigit() || it == '.' }.take(6)

@Composable
internal fun OmrEvalSection(d: AppData) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val bn = lang == Lang.BN
    val c = IQ.c
    val vm = remember { ViewModelProvider(ctx.findComponentActivity())[OmrEvalViewModel::class.java] }
    vm.bind(ctx, OmrLayout(d.settings.omr, if (d.setCode.enabled) d.generatedSets.map { it.code } else emptyList()), d.examId)

    var errKey by remember { mutableStateOf<String?>(null) }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var camFile by remember { mutableStateOf<File?>(null) }
    var openId by rememberSaveable { mutableStateOf(-1) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var exportErr by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    /** Builds the report off the main thread and saves it to Downloads/IntelliQ (or opens the share sheet on Android 8/9). */
    fun export(pdf: Boolean) {
        if (exporting || vm.sheets.isEmpty()) return
        exporting = true
        exportMsg = null
        val snapshot = vm.sheets.toList()
        val title = d.doc.title.trim().ifEmpty { trKey("eval.pdf.defaultTitle", lang) }
        scope.launch {
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val r = withContext(Dispatchers.Default) {
                    if (pdf) {
                        ExportResult("OMR_Results_$stamp.pdf", "application/pdf", OmrResultPdf.build(ctx, snapshot, title, lang), false)
                    } else {
                        val csv = OmrReport.csv(OmrReport.rank(OmrResultPdf.reportRows(snapshot)))
                        ExportResult("OMR_Results_$stamp.csv", "text/csv", csv.toByteArray(Charsets.UTF_8), false)
                    }
                }
                val saved = PdfExport.deliver(ctx.findComponentActivity(), r)
                exportErr = false
                exportMsg = trKey(if (saved) "eval.exported" else "eval.shared", lang).replace("{f}", r.fileName)
            } catch (e: Exception) {
                exportErr = true
                exportMsg = trKey("eval.exportFail", lang)
            } finally {
                exporting = false
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        errKey = null
        vm.add(uris, null)
    }
    val camGate = rememberCameraGate()
    var liveOpen by rememberSaveable { mutableStateOf(false) }
    if (liveOpen) OmrLiveCamera(onAccepted = { f -> errKey = null; vm.add(listOf(Uri.fromFile(f)), f) }, onClose = { liveOpen = false })
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        val f = camFile
        camUri = null
        camFile = null
        if (ok && u != null) {
            errKey = null
            vm.add(listOf(u), f)
        } else {
            f?.delete()
        }
    }

    fun pickGallery() {
        try {
            gallery.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.read"
        }
    }

    fun capture() {
        try {
            val dir = File(ctx.cacheDir, "omr").apply { mkdirs() }
            val f = File.createTempFile("scan_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camFile = f
            camUri = uri
            camGate { camera.launch(uri) }
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.noCamera"
        } catch (e: Exception) {
            errKey = "scan.err.read"
        }
    }

    // ---- answer-sheet import (step 4.9): camera / gallery / PDF ----
    var keyOpen by rememberSaveable { mutableStateOf(false) }
    var keyCamUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var keyCamFile by remember { mutableStateOf<File?>(null) }
    val keyGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        errKey = null
        vm.addKeyImages(uris, null)
    }
    val keyPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        errKey = null
        if (uri != null) vm.addKeyPdf(uri)
    }
    val keyCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = keyCamUri
        val f = keyCamFile
        keyCamUri = null
        keyCamFile = null
        if (ok && u != null) vm.addKeyImages(listOf(u), f) else f?.delete()
    }

    fun launchSafely(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.read"
        }
    }

    fun captureKey() {
        try {
            val dir = File(ctx.cacheDir, "omr").apply { mkdirs() }
            val f = File.createTempFile("key_", ".jpg", dir)
            keyCamFile = f
            keyCamUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camGate { keyCamera.launch(keyCamUri!!) }
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.noCamera"
        } catch (e: Exception) {
            errKey = "scan.err.read"
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("eval.title"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            InfoTip(tr("eval.intro") + "\n\n" + tr("scan.tip"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IQButton(tr("scan.camera"), BtnKind.Primary, Modifier.weight(1f)) { liveOpen = true }
            IQButton(tr("scan.gallery"), BtnKind.Secondary, Modifier.weight(1f)) { pickGallery() }
        }
        val e = errKey
        if (e != null) Text(tr(e), color = c.danger, fontSize = 13.sp, fontFamily = uiFont())

        // ---- answer sheet import by QR / PDF (step 4.9) ----
        Text(
            tr(if (keyOpen) "keyimp.hide" else "keyimp.show"),
            color = c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            modifier = Modifier.clickable { keyOpen = !keyOpen }.padding(vertical = 4.dp),
        )
        if (keyOpen) KeyImportPanel(vm, bn, { captureKey() }, { launchSafely { keyGallery.launch("image/*") } }, { launchSafely { keyPdf.launch("application/pdf") } })

        // ---- marking rules ----
        Text(tr("eval.rules"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("eval.marks"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
                InputBox(vm.marksText, { v -> vm.marksText = cleanNumber(v); vm.settingsChanged() }, tr("eval.asExam"), singleLine = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("eval.negative"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
                InputBox(vm.negText, { v -> vm.negText = cleanNumber(v); vm.settingsChanged() }, tr("eval.asExam"), singleLine = true)
            }
        }
        SwitchRow(tr("eval.multi"), tr("eval.multi.hint"), vm.penalizeMulti) { v -> vm.penalizeMulti = v; vm.settingsChanged() }
        Text(
            tr(if (advanced) "eval.adv.hide" else "eval.adv.show"),
            color = c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 4.dp),
        )
        if (advanced) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("eval.fill"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
                    InputBox(vm.fillText, { v -> vm.fillText = cleanNumber(v); vm.settingsChanged() }, "0.35", singleLine = true)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("eval.blank"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
                    InputBox(vm.blankText, { v -> vm.blankText = cleanNumber(v); vm.settingsChanged() }, "0.15", singleLine = true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("eval.adv.title"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
                InfoTip(tr("eval.adv.hint"))
            }
            IQButton(tr("eval.adv.reset"), BtnKind.Ghost, Modifier.fillMaxWidth()) { vm.resetThresholds() }
        }

        // ---- progress, summary, cards ----
        if (vm.pending > 0) {
            Text(
                tr("scan.working").replace("{n}", formatNumber(vm.pending, bn)),
                color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            )
        }
        val list = vm.sheets
        if (list.isNotEmpty()) {
            val ok = list.count { it.state == EvalState.OK }
            val review = list.count { it.state == EvalState.REVIEW || it.state == EvalState.UNMATCHED }
            val failed = list.count { it.state == EvalState.FAILED }
            Text(
                tr("eval.summary")
                    .replace("{n}", formatNumber(list.size, bn))
                    .replace("{ok}", formatNumber(ok, bn))
                    .replace("{r}", formatNumber(review, bn))
                    .replace("{x}", formatNumber(failed, bn)),
                color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            )
            for (s in list) EvalCard(s, bn) { openId = s.id }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IQButton(tr(if (exporting) "eval.exporting" else "eval.exportPdf"), BtnKind.Primary, Modifier.weight(1f)) { export(true) }
                IQButton(tr("eval.exportCsv"), BtnKind.Secondary, Modifier.weight(1f)) { export(false) }
            }
            val em = exportMsg
            if (em != null) Text(em, color = if (exportErr) c.danger else c.successInk, fontSize = 13.sp, fontFamily = uiFont())
            IQButton(tr("eval.clear"), BtnKind.Ghost, Modifier.fillMaxWidth()) { vm.clear(); openId = -1 }
        }
    }

    val open = vm.sheets.firstOrNull { it.id == openId }
    if (open != null) EvalDetail(vm, open, bn) { openId = -1 }
}

@Composable
private fun KeyImportPanel(vm: OmrEvalViewModel, bn: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onPdf: () -> Unit) {
    val c = IQ.c
    Column(
        Modifier.fillMaxWidth().clip(IQD.card).background(c.field).border(1.dp, c.border, IQD.card).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("keyimp.title"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            InfoTip(tr("keyimp.intro"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IQButton(tr("scan.camera"), BtnKind.Secondary, Modifier.weight(1f)) { onCamera() }
            IQButton(tr("scan.gallery"), BtnKind.Secondary, Modifier.weight(1f)) { onGallery() }
            IQButton("PDF", BtnKind.Secondary, Modifier.weight(1f)) { onPdf() }
        }
        if (vm.keyBusy > 0) {
            Text(tr("keyimp.working"), color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        }
        val imp = vm.keyImport
        val asm = imp.assembly
        val msg = vm.keyMsg
        if (msg != null) {
            Text(tr(msg).replace("{n}", formatNumber(vm.keyMsgArg.toIntOrNull() ?: 0, bn)), color = c.danger, fontSize = 13.sp, fontFamily = uiFont())
        }
        if (vm.keySaved) Text(tr("keyimp.saved"), color = c.successInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        when (asm) {
            is KeyQrAssembly.Missing -> {
                // QR pages are 0-based, people count from 1
                val left = asm.pages.joinToString(", ") { formatNumber(it + 1, bn) }
                Text(
                    tr("keyimp.progress")
                        .replace("{h}", formatNumber(imp.pagesHave, bn))
                        .replace("{t}", formatNumber(imp.pagesTotal, bn))
                        .replace("{p}", left),
                    color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                )
                IQButton(tr("keyimp.reset"), BtnKind.Ghost, Modifier.fillMaxWidth()) { vm.keyReset() }
            }
            is KeyQrAssembly.Mismatch -> {
                if (!imp.isEmpty) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IQButton(tr("keyimp.dropLast"), BtnKind.Secondary, Modifier.weight(1f)) { vm.keyDropLast() }
                        IQButton(tr("keyimp.reset"), BtnKind.Ghost, Modifier.weight(1f)) { vm.keyReset() }
                    }
                }
            }
            is KeyQrAssembly.Done -> {
                val s = asm.snapshot
                val ex = vm.keyResult()?.second ?: KeyExisting.NONE
                val set = s.setIndex?.let { formatNumber(it + 1, bn) } ?: "—"
                Text(
                    tr("keyimp.summary")
                        .replace("{id}", s.examId)
                        .replace("{set}", set)
                        .replace("{q}", formatNumber(s.items.size, bn))
                        .replace("{u}", formatNumber(s.unanswered, bn))
                        .replace("{m}", fmt(s.marksPerQ, bn))
                        .replace("{neg}", fmt(s.negativePerWrong, bn)),
                    color = c.text, fontSize = 13.sp, fontFamily = uiFont(),
                )
                Text(tr("keyimp.cloudNote"), color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
                when (ex) {
                    KeyExisting.SAME -> {
                        Text(tr("keyimp.same"), color = c.successInk, fontSize = 13.sp, fontFamily = uiFont())
                        IQButton(tr("keyimp.done"), BtnKind.Ghost, Modifier.fillMaxWidth()) { vm.keyReset() }
                    }
                    KeyExisting.DIFFERENT -> {
                        Text(tr("keyimp.different"), color = AMBER, fontSize = 13.sp, fontFamily = uiFont())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IQButton(tr("keyimp.keepOld"), BtnKind.Secondary, Modifier.weight(1f)) { vm.keyReset() }
                            IQButton(tr("keyimp.replace"), BtnKind.Primary, Modifier.weight(1f)) { vm.keySave(true) }
                        }
                    }
                    KeyExisting.NONE -> IQButton(tr("keyimp.save"), BtnKind.Primary, Modifier.fillMaxWidth()) { vm.keySave(false) }
                }
            }
        }
    }
}

@Composable
private fun stateColor(s: EvalState): Color = when (s) {
    EvalState.OK -> IQ.c.successInk
    EvalState.REVIEW -> AMBER
    EvalState.UNMATCHED -> AMBER
    EvalState.FAILED -> IQ.c.danger
}

/** "Roll 123456 · set 2 · 38 / 50" for a graded sheet, else the reason. */
@Composable
private fun sheetLine(s: EvalSheet, bn: Boolean): String {
    val res = s.result
    val roll = (s.roll ?: "-").let { if (bn) it.map { ch -> if (ch in '0'..'9') ('০' + (ch - '0')) else ch }.joinToString("") else it }
    if (res == null) {
        return when {
            s.state == EvalState.FAILED -> tr(s.failKey ?: "scan.err.read")
            else -> tr("eval.roll") + " " + roll + " · " + tr("eval.state.UNMATCHED")
        }
    }
    val set = res.setIndex?.let { " · " + tr("eval.set") + " " + formatNumber(it + 1, bn) } ?: ""
    return tr("eval.roll") + " " + roll + set + " · " + fmt(res.score, bn) + " / " + fmt(res.total, bn)
}

private fun fmt(v: Double, bn: Boolean): String {
    val t = if (v == Math.rint(v)) String.format(Locale.US, "%d", v.toLong()) else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    return if (bn) t.map { if (it in '0'..'9') '০' + (it - '0') else if (it == '.') '.' else it }.joinToString("") else t
}

@Composable
private fun EvalCard(s: EvalSheet, bn: Boolean, onOpen: () -> Unit) {
    val c = IQ.c
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IQD.card)
            .background(c.field)
            .border(1.dp, if (s.state == EvalState.OK) c.success else c.border, IQD.card)
            .clickable { onOpen() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            Text(sheetLine(s, bn), color = c.text, fontSize = 13.sp, fontFamily = uiFont())
            Text(tr("eval.state." + s.state.name), color = stateColor(s.state), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        }
        IQButton(tr("eval.open"), BtnKind.Ghost) { onOpen() }
    }
}

@Composable
private fun EvalDetail(vm: OmrEvalViewModel, s: EvalSheet, bn: Boolean, onClose: () -> Unit) {
    val c = IQ.c
    val res = s.result
    var showAll by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 10.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(IQD.dialog)
                .background(c.panel)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(s.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            Text(tr("eval.state." + s.state.name), color = stateColor(s.state), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())

            if (res != null) {
                Text(
                    tr("eval.score").replace("{s}", fmt(res.score, bn)).replace("{t}", fmt(res.total, bn)),
                    color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
                )
                Text(
                    tr("eval.counts")
                        .replace("{a}", formatNumber(res.correct, bn)).replace("{b}", formatNumber(res.wrong, bn))
                        .replace("{c}", formatNumber(res.blank, bn)).replace("{u}", formatNumber(res.review.size, bn)),
                    color = c.textSecondary, fontSize = 13.sp, fontFamily = uiFont(),
                )
            } else if (s.state == EvalState.FAILED) {
                Text(tr(s.failKey ?: "scan.err.read"), color = c.danger, fontSize = 13.sp, fontFamily = uiFont())
                if (s.failDetail.isNotEmpty()) Text(s.failDetail, color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
            }
            val shownFlags = s.flags.filter { it != OmrFlag.KEY_INCOMPLETE || res != null }
            for (f in shownFlags) {
                if (f == OmrFlag.ROLL_INCOMPLETE && s.rollFix != null) continue
                Text("• " + tr("eval.flag." + f.name), color = AMBER, fontSize = 13.sp, fontFamily = uiFont())
            }

            // roll number: what was read ("?" = a column that was not clearly filled), fixable by hand
            if (s.read != null) {
                Text(tr("eval.rollFix"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
                InputBox(
                    s.rollFix ?: (s.read.roll?.text ?: ""),
                    { v -> vm.setRoll(s.id, v.filter { it.isLetterOrDigit() || it == '?' }.take(16)) },
                    tr("eval.roll"),
                    singleLine = true,
                )
            }

            // the photo with the marks drawn on it
            val jpeg = s.jpeg
            if (jpeg != null && s.imgW > 0 && s.imgH > 0) {
                val bmp = remember(s.id) { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap() }
                if (bmp != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(s.imgW.toFloat() / s.imgH.toFloat())
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        Image(bmp, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                        val al = s.align
                        if (al != null) Canvas(Modifier.matchParentSize()) { drawResult(al, res?.rows ?: emptyList(), size.width / s.imgW) }
                    }
                    if (res != null) Text(tr("eval.legend"), color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
                }
            }

            // rows: the ones to look at first, every row on request
            if (res != null) {
                val review = res.review
                val rows = if (showAll) res.rows else review
                Text(
                    tr(if (showAll) "eval.allRows" else "eval.reviewRows").replace("{n}", formatNumber(review.size, bn)),
                    color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                )
                if (rows.isEmpty()) Text(tr("eval.noReview"), color = c.textFaint, fontSize = 13.sp, fontFamily = uiFont())
                for (q in rows) EvalRow(q, bn, s.overrides.containsKey(q.slot), onToggle = { o ->
                    val now = if (o in q.marked) q.marked - o else q.marked + o
                    vm.setOverride(s.id, q.slot, now)
                }, onBlank = { vm.setOverride(s.id, q.slot, emptySet()) }, onUndo = { vm.setOverride(s.id, q.slot, null) })
                IQButton(tr(if (showAll) "eval.showReview" else "eval.showAll"), BtnKind.Ghost, Modifier.fillMaxWidth()) { showAll = !showAll }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IQButton(tr("eval.delete"), BtnKind.Ghost, Modifier.weight(1f)) { vm.remove(s.id); onClose() }
                IQButton(tr("scan.close"), BtnKind.Secondary, Modifier.weight(1f), onClose)
            }
        }
    }
}

@Composable
private fun EvalRow(q: QResult, bn: Boolean, overridden: Boolean, onToggle: (Int) -> Unit, onBlank: () -> Unit, onUndo: () -> Unit) {
    val c = IQ.c
    val labels = if (bn) listOf("ক", "খ", "গ", "ঘ") else listOf("A", "B", "C", "D")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IQD.card)
            .background(c.field)
            .border(1.dp, c.border, IQD.card)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("eval.q") + " " + formatNumber(q.printedNumber, bn),
                color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            )
            Text(tr("eval.qs." + q.status.name), color = rowColor(q.status), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (o in labels.indices) {
                OptChip(labels[o], o in q.marked, o in q.correct) { onToggle(o) }
            }
            Box(
                Modifier
                    .height(34.dp)
                    .width(44.dp)
                    .clip(CircleShape)
                    .background(if (q.marked.isEmpty()) c.primarySoft else c.field)
                    .border(1.dp, c.border, CircleShape)
                    .clickable { onBlank() },
                contentAlignment = Alignment.Center,
            ) { Text("—", color = c.text, fontSize = 14.sp, fontFamily = uiFont()) }
            if (overridden) {
                Text(
                    tr("eval.undo"), color = c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                    modifier = Modifier.clickable { onUndo() }.padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun rowColor(s: QStatus): Color = when (s) {
    QStatus.CORRECT -> IQ.c.successInk
    QStatus.WRONG -> IQ.c.danger
    QStatus.UNCERTAIN, QStatus.MULTI -> AMBER
    else -> IQ.c.textFaint
}

/** A round option button: filled = marked by the student (or the teacher), green ring = the right option. */
@Composable
private fun OptChip(label: String, marked: Boolean, correct: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (marked) c.primary else c.field)
            .border(if (correct) 2.dp else 1.dp, if (correct) c.success else c.border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (marked) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
    }
}

/**
 * Draws on the photo, at the printed bubble positions (the aligner's map): the right option green, a wrong mark red, a mark
 * the reader was unsure of amber, plus the four corner squares in blue so a misalignment shows at a glance.
 */
private fun DrawScope.drawResult(al: AlignResult.Ok, rows: List<QResult>, k: Float) {
    val r = (OmrGeometry.BUBBLE_INNER_R * al.scalePxPerMm * k).toFloat().coerceAtLeast(2f)
    fun centre(slot: Int, o: Int): Offset {
        val b = OmrGeometry.answerBubbleOfSlot(slot, o)
        val p = al.toImage.map(b.x.toDouble(), b.y.toDouble())
        return Offset((p.x * k).toFloat(), (p.y * k).toFloat())
    }
    for (q in rows) {
        for (o in q.correct) {
            if (o in 0 until OmrGeometry.OPTIONS) drawCircle(GREEN, r * 1.25f, centre(q.slot, o), style = Stroke(width = 2.6f))
        }
        val wrong = when (q.status) {
            QStatus.WRONG -> q.marked - q.correct
            QStatus.MULTI -> q.marked - q.correct
            else -> emptySet()
        }
        for (o in wrong) if (o in 0 until OmrGeometry.OPTIONS) drawCircle(RED, r * 1.25f, centre(q.slot, o), style = Stroke(width = 2.6f))
        if (q.status == QStatus.UNCERTAIN) {
            for (o in q.marked) if (o in 0 until OmrGeometry.OPTIONS) drawCircle(AMBER, r * 1.45f, centre(q.slot, o), style = Stroke(width = 2.6f))
        }
    }
    for (m in OmrGeometry.cornerMarks()) {
        val p = al.toImage.map((m.x + m.w / 2f).toDouble(), (m.y + m.h / 2f).toDouble())
        drawCircle(BLUE, r * 1.8f, Offset((p.x * k).toFloat(), (p.y * k).toFloat()), style = Stroke(width = 2.2f))
    }
}
