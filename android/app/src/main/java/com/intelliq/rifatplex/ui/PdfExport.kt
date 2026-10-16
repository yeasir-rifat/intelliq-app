package com.intelliq.rifatplex.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Choreographer
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.PAGE_SIZES_MM
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.ui.theme.IntelliQTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class ExportResult(val fileName: String, val mime: String, val bytes: ByteArray, val isZip: Boolean)

/**
 * PDF export. The pages are drawn by the SAME composable as the preview ([PaperPages] in print mode), one page at a
 * time, into an off-screen ComposeView whose size is exactly one page. The view is drawn onto a [PdfDocument] canvas,
 * so the PDF always matches the preview (fonts, Bangla, fractions, sub/superscripts, set code box, page numbers) and
 * the text stays real vector text.
 */
object PdfExport {

    private suspend fun nextFrame() {
        val choreographer = Choreographer.getInstance()
        suspendCancellableCoroutine<Unit> { cont ->
            val cb = Choreographer.FrameCallback { cont.resume(Unit) }
            choreographer.postFrameCallback(cb)
            cont.invokeOnCancellation { choreographer.removeFrameCallback(cb) }
        }
    }

    /** Renders one paper (or one answer key) to PDF bytes. Must run on the main thread. */
    suspend fun renderPdf(
        activity: Activity,
        d: AppData,
        questions: List<Question>,
        setCode: String?,
        answers: Boolean,
    ): ByteArray {
        val lang = if (d.language == "bn") Lang.BN else Lang.EN
        val size = PAGE_SIZES_MM[d.settings.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
        val density = activity.resources.displayMetrics.density
        val wPx = (size.first * 96f / 25.4f * density).roundToInt()
        val hPx = (size.second * 96f / 25.4f * density).roundToInt()
        val wPt = (size.first * 72f / 25.4f).roundToInt()
        val hPt = (size.second * 72f / 25.4f).roundToInt()

        val laidPage = intArrayOf(-1)
        val laidCount = intArrayOf(0)
        val want = mutableIntStateOf(0)

        val view = ComposeView(activity)
        view.setContent {
            IntelliQTheme(false) {
                CompositionLocalProvider(LocalLang provides lang, LocalChemAuto provides d.settings.chemAuto) {
                    PaperPages(
                        questions = questions,
                        setCode = setCode,
                        doc = d.doc,
                        lang = lang,
                        settings = d.settings,
                        brand = d.brand,
                        showAnswers = answers,
                        scale = 1f,
                        containerWidth = 0.dp,
                        printPage = want.intValue,
                        onPrintLayout = { p, n ->
                            laidPage[0] = p
                            laidCount[0] = n
                        },
                    )
                }
            }
        }

        // Attached (so Compose runs) but invisible and parked off-screen.
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        view.alpha = 0f
        view.translationX = -2f * wPx
        root.addView(view, FrameLayout.LayoutParams(wPx, hPx))

        val pdf = PdfDocument()
        try {
            suspend fun awaitPage(p: Int) {
                var tries = 0
                while ((laidPage[0] != p || laidCount[0] == 0) && tries < 180) {
                    nextFrame()
                    tries++
                }
                if (laidPage[0] != p) error("PDF page layout timed out")
                nextFrame() // let pending recomposition settle before drawing
            }

            awaitPage(0)
            val count = laidCount[0]
            for (p in 0 until count) {
                if (p > 0) {
                    want.intValue = p
                    awaitPage(p)
                }
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(wPt, hPt, p + 1).create())
                val canvas = page.canvas
                canvas.scale(wPt.toFloat() / wPx, hPt.toFloat() / hPx)
                view.draw(canvas)
                pdf.finishPage(page)
            }
            val out = ByteArrayOutputStream()
            pdf.writeTo(out)
            return out.toByteArray()
        } finally {
            pdf.close()
            root.removeView(view)
        }
    }

    // ----- file naming (same rules as the website's PdfGen) -----

    private fun safeName(s: String): String {
        val t = s.replace(Regex("[\\\\/:*?\"<>|]+"), " ")
            .replace(Regex("[\\u0000-\\u001F\\u007F]+"), " ")
            .trim()
            .replace(Regex("\\s+"), "_")
        return t.ifEmpty { "Document" }.take(60)
    }

    /**
     * Question paper or answer key of [d] as PDF, or as an editable Word file when [docx] is true.
     * Several sets -> one ZIP with one file per set.
     */
    suspend fun export(activity: Activity, d: AppData, answers: Boolean, docx: Boolean = false): ExportResult {
        val bn = d.language == "bn"
        val subtitle = d.doc.subtitle.ifEmpty { if (bn) "উপশিরোনাম" else "Subtitle" }
        val title = d.doc.title.ifEmpty { if (bn) "শিরোনাম" else "Examination_Title" }
        val base = "${safeName(subtitle)}_${safeName(title)}"
        val tag = if (bn) (if (answers) "উত্তরপত্র" else "প্রশ্নপত্র") else (if (answers) "Answer" else "Question")
        val ext = if (docx) "docx" else "pdf"
        val mime = if (docx) DocxExport.MIME else "application/pdf"

        suspend fun render(qs: List<Question>, code: String?): ByteArray =
            if (docx) withContext(Dispatchers.Default) { DocxExport.build(activity, d, qs, code, answers) }
            else renderPdf(activity, d, qs, code, answers)

        if (d.setCode.enabled && d.generatedSets.size > 1) {
            val seen = HashMap<String, Int>()
            val zipBytes = ByteArrayOutputStream()
            ZipOutputStream(zipBytes).use { zip ->
                for (s in d.generatedSets) {
                    val raw = safeName(s.code)
                    val n = (seen[raw] ?: 0) + 1
                    seen[raw] = n
                    val code = if (n == 1) raw else "${raw}_$n"
                    val bytes = render(s.questions.filter { !it.isEmpty() }, s.code)
                    zip.putNextEntry(ZipEntry("${base}_Set_${code}_$tag.$ext"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            return ExportResult("${base}_All_Sets_$tag.zip", "application/zip", zipBytes.toByteArray(), true)
        }
        if (d.setCode.enabled && d.generatedSets.size == 1) {
            val s = d.generatedSets[0]
            val bytes = render(s.questions.filter { !it.isEmpty() }, s.code)
            return ExportResult("${base}_Set_${safeName(s.code)}_$tag.$ext", mime, bytes, false)
        }
        val bytes = render(d.questions.filter { !it.isEmpty() }, null)
        return ExportResult("${base}_$tag.$ext", mime, bytes, false)
    }

    /**
     * Saves into Downloads/IntelliQ (Android 10+). On Android 8/9, where that needs a storage permission,
     * the file is handed to the share sheet instead. Returns true when it was saved to Downloads.
     */
    suspend fun deliver(activity: Activity, r: ExportResult): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            withContext(Dispatchers.IO) {
                val resolver = activity.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, r.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, r.mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IntelliQ")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create the file in Downloads")
                try {
                    resolver.openOutputStream(uri)!!.use { it.write(r.bytes) }
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            }
            return true
        }
        val file = withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
            File(dir, r.fileName).also { it.writeBytes(r.bytes) }
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = r.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, r.fileName))
        return false
    }
}
