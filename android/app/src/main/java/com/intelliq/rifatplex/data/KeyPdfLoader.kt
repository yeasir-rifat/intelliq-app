package com.intelliq.rifatplex.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri

/**
 * Step 4.9 - an answer-sheet PDF -> the QR code of every page. [PdfRenderer] is Android only, so this lives apart from
 * the pure [AnswerKeyImport]. Pages are rendered one at a time, 2000 px wide (the 26 mm QR is then about 180 px, plenty),
 * read and recycled at once, so memory stays at one page.
 */
internal object KeyPdfLoader {
    const val RENDER_WIDTH = 2000
    private const val MAX_PAGES = 120

    class Result(val scans: List<KeyScan>, val pageCount: Int)

    /** null when the file cannot be opened as a PDF. Slow: call from a background thread. */
    fun scan(cr: ContentResolver, uri: Uri): Result? {
        val pfd = try {
            cr.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            PdfRenderer(pfd).use { r ->
                val n = minOf(r.pageCount, MAX_PAGES)
                val out = ArrayList<KeyScan>()
                for (i in 0 until n) out.add(scanPage(r, i))
                Result(out, r.pageCount)
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                pfd.close()
            } catch (e: Exception) {
            }
        }
    }

    private fun scanPage(r: PdfRenderer, index: Int): KeyScan {
        r.openPage(index).use { page ->
            val w = RENDER_WIDTH
            val h = (w.toDouble() * page.height / page.width).toInt().coerceIn(1, 4000)
            var bmp: Bitmap? = null
            try {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE) // the renderer leaves the page transparent
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val gray = ByteArray(w * h)
                val row = IntArray(w)
                for (y in 0 until h) {
                    bmp.getPixels(row, 0, w, 0, y, w, 1)
                    for (x in 0 until w) gray[y * w + x] = OmrImageLoader.lumaOf(row[x]).toByte()
                }
                return AnswerKeyImport.readImage(GrayImage(w, h, gray))
            } catch (e: OutOfMemoryError) {
                return KeyScan.None
            } catch (e: Exception) {
                return KeyScan.None // one page that cannot be drawn must not throw away the other pages
            } finally {
                bmp?.recycle()
            }
        }
    }

    private inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
        try {
            return block(this)
        } finally {
            this?.close()
        }
    }
}
