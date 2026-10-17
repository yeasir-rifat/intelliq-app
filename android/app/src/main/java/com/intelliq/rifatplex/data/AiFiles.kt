package com.intelliq.rifatplex.data

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

enum class AiKind { Image, Pdf, Text }

/** One attachment of the AI dialog. Images / PDFs keep their bytes; DOCX / PPTX keep the extracted text. */
data class AiFile(
    val id: String,
    val name: String,
    val kind: AiKind,
    val mime: String = "",
    val bytes: ByteArray? = null,
    val text: String = "",
    val source: String = "", // "docx" | "pptx" for kind == Text
    val thumb: ImageBitmap? = null,
    val done: Boolean = false,
    val size: Long = 0, // raw bytes (images / PDFs)
)

/** Same limits as the website: about 60 MB of base64 per request (1.37 x the raw size). */
const val AI_MAX_FILES = 10
const val AI_MAX_REQUEST_MB = 60.0
fun estimatedRequestMb(rawBytes: Long): Double = 1.37 * rawBytes / 1048576.0

private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

class AiFileInfo(val name: String, val mime: String, val size: Long)

fun queryFileInfo(cr: ContentResolver, uri: Uri): AiFileInfo {
    var name = ""
    var size = -1L
    try {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
    } catch (e: Exception) {
        // fall through with defaults
    }
    if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var mime = cr.getType(uri).orEmpty()
    val lower = name.lowercase()
    if (mime.isEmpty() || mime == "application/octet-stream") {
        mime = when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".docx") -> MIME_DOCX
            lower.endsWith(".pptx") -> MIME_PPTX
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".heic") -> "image/heic"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> mime
        }
    }
    return AiFileInfo(name, mime, size)
}

fun isOldOfficeName(name: String): Boolean = Regex("""\.(doc|ppt|xls|xlsx)$""", RegexOption.IGNORE_CASE).containsMatchIn(name)

fun kindOf(info: AiFileInfo): AiKind? {
    val n = info.name.lowercase()
    return when {
        info.mime.startsWith("image/") -> AiKind.Image
        info.mime == "application/pdf" || n.endsWith(".pdf") -> AiKind.Pdf
        info.mime == MIME_DOCX || n.endsWith(".docx") || info.mime == MIME_PPTX || n.endsWith(".pptx") -> AiKind.Text
        else -> null
    }
}

fun sourceOf(info: AiFileInfo): String =
    if (info.mime == MIME_PPTX || info.name.lowercase().endsWith(".pptx")) "pptx" else "docx"

class TooLargeException : Exception()

/** Reads at most [max] bytes; throws [TooLargeException] when the stream is longer. */
fun readLimited(input: InputStream, max: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    input.use { ins ->
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw TooLargeException()
            out.write(buf, 0, n)
        }
    }
    return out.toByteArray()
}

fun makeThumb(bytes: ByteArray, targetPx: Int = 96): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
} catch (e: Throwable) {
    null
}

/** Text of a .docx (word/document.xml) or .pptx (ppt/slides/slideN.xml, in slide order). Same cleanup as the website. */
fun extractOfficeText(bytes: ByteArray, isPptx: Boolean): String {
    val wanted = HashMap<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
        while (true) {
            val e = zin.nextEntry ?: break
            val n = e.name
            val ok = if (isPptx) Regex("""^ppt/slides/slide\d+\.xml$""", RegexOption.IGNORE_CASE).matches(n)
            else n.equals("word/document.xml", ignoreCase = true)
            if (ok) wanted[n] = zin.readBytes().toString(Charsets.UTF_8)
            zin.closeEntry()
        }
    }
    if (wanted.isEmpty()) throw IllegalStateException("EMPTY_DOC")
    val names = if (isPptx) {
        wanted.keys.sortedBy { Regex("""slide(\d+)\.xml""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
    } else wanted.keys.toList()
    val parts = ArrayList<String>()
    for (n in names) {
        val t = wanted.getValue(n)
            .replace(Regex("</w:p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</a:p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (t.isNotEmpty()) parts.add(t)
    }
    val all = parts.joinToString("\n\n").trim()
    if (all.isEmpty()) throw IllegalStateException("EMPTY_DOC")
    return all
}
