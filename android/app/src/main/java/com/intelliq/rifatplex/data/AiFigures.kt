package com.intelliq.rifatplex.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Figure extraction (website `__iqCropFigures`): the AI returns the bounding box of a figure that exists in the attached
 * image / PDF; the figure is cut out of the source and put after the question text as a picture.
 * Any failure just skips that figure (the question stays valid).
 */
object AiFigures {

    /** Normalises the box like the website: at least 25/1000 in both directions, not the whole page, 12/1000 padding. */
    private fun validBox(b: List<Float>): FloatArray? {
        if (b.size != 4 || b.any { it.isNaN() || it.isInfinite() }) return null
        val y0 = max(0f, min(b[0], b[2]))
        val x0 = max(0f, min(b[1], b[3]))
        val y1 = min(1000f, max(b[0], b[2]))
        val x1 = min(1000f, max(b[1], b[3]))
        if (y1 - y0 < 25f || x1 - x0 < 25f) return null
        if ((y1 - y0) * (x1 - x0) > 0.85f * 1000f * 1000f) return null
        val pad = 12f
        return floatArrayOf(max(0f, y0 - pad), max(0f, x0 - pad), min(1000f, y1 + pad), min(1000f, x1 + pad))
    }

    private class Source(val files: List<AiFile>) {
        private val pages = HashMap<String, Bitmap?>()
        private val pdfs = HashMap<String, Pair<PdfRenderer, File>>()
        private val decoders = HashMap<String, BitmapRegionDecoder?>()

        fun close() {
            pages.values.forEach { it?.recycle() }
            pdfs.values.forEach {
                try { it.first.close() } catch (e: Exception) { /* ignore */ }
                it.second.delete()
            }
            decoders.values.forEach { it?.recycle() }
        }

        /** The cut-out region (box in 0..1000 as ymin, xmin, ymax, xmax) of the given file / page, or null. */
        fun region(file: AiFile, page: Int, box: FloatArray): Bitmap? {
            val bytes = file.bytes ?: return null
            if (file.kind == AiKind.Image) {
                val dec = decoders.getOrPut(file.id) {
                    try { BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) } catch (e: Throwable) { null }
                } ?: return null
                val w = dec.width
                val h = dec.height
                val r = Rect(
                    (box[1] / 1000f * w).roundToInt().coerceIn(0, w - 1),
                    (box[0] / 1000f * h).roundToInt().coerceIn(0, h - 1),
                    (box[3] / 1000f * w).roundToInt().coerceIn(1, w),
                    (box[2] / 1000f * h).roundToInt().coerceIn(1, h),
                )
                if (r.width() < 20 || r.height() < 20) return null
                var sample = 1
                while (max(r.width(), r.height()) / (sample * 2) >= 1400) sample *= 2
                return dec.decodeRegion(r, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            // PDF page, rendered at 1.5x..3x (like the website), then cropped
            val key = file.id + "#" + page
            val bmp = pages.getOrPut(key) { renderPage(file, bytes, page) } ?: return null
            val sx = (box[1] / 1000f * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val sy = (box[0] / 1000f * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val sw = ((box[3] - box[1]) / 1000f * bmp.width).roundToInt().coerceAtMost(bmp.width - sx)
            val sh = ((box[2] - box[0]) / 1000f * bmp.height).roundToInt().coerceAtMost(bmp.height - sy)
            if (sw < 20 || sh < 20) return null
            return Bitmap.createBitmap(bmp, sx, sy, sw, sh)
        }

        private fun renderPage(file: AiFile, bytes: ByteArray, page: Int): Bitmap? {
            val (renderer, _) = pdfs.getOrPut(file.id) {
                val f = File.createTempFile("aipdf_", ".pdf")
                f.writeBytes(bytes)
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd) to f
            }
            val pn = (page - 1).coerceIn(0, renderer.pageCount - 1)
            val pg = renderer.openPage(pn)
            try {
                val scale = min(3f, max(1.5f, 1800f / max(pg.width, pg.height)))
                val bmp = Bitmap.createBitmap((pg.width * scale).roundToInt(), (pg.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            } finally {
                pg.close()
            }
        }
    }

    /** Fills [AiRawQuestion.figTag] / [AiRawSub.figTag] for every record that carries a figure box. Runs on a background thread. */
    fun attach(items: List<AiRawQuestion>, files: List<AiFile>) {
        val media = files.filter { (it.kind == AiKind.Image || it.kind == AiKind.Pdf) && it.bytes != null }
        if (media.isEmpty()) return
        val src = Source(media)
        try {
            fun tagFor(fig: AiFigure?): String {
                fig ?: return ""
                return try {
                    val f = media.getOrNull(fig.file - 1) ?: return ""
                    val box = validBox(fig.box) ?: return ""
                    val bmp = src.region(f, fig.page, box) ?: return ""
                    val pic = ImageTools.fromBitmap(bmp)
                    FigData(listOf(pic), "M", "c").tag
                } catch (e: Throwable) {
                    ""
                }
            }
            for (q in items) {
                q.figTag = tagFor(q.figure)
                for (s in q.subs) s.figTag = tagFor(s.figure)
            }
        } finally {
            src.close()
        }
    }
}
