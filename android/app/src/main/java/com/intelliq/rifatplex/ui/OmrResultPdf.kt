package com.intelliq.rifatplex.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.intelliq.rifatplex.R
import com.intelliq.rifatplex.data.OmrGeometry
import com.intelliq.rifatplex.data.OmrLayout
import com.intelliq.rifatplex.data.OmrReport
import com.intelliq.rifatplex.data.QResult
import com.intelliq.rifatplex.data.QStatus
import com.intelliq.rifatplex.data.RankedRow
import com.intelliq.rifatplex.data.ReportRow
import com.intelliq.rifatplex.data.formatNumber
import java.io.ByteArrayOutputStream

/*
 * The result PDF of an OMR evaluation: ONE page per marked sheet and nothing else.
 *
 *  - Roll number, registration number, set code and subject code (the ones the sheet has) get a border: green when the student
 *    filled them completely, red when not. A sheet that was marked without any doubt (EvalState.OK) also gets a big soft green
 *    tick over the page.
 *
 *  - The page has the shape of the student's own photo and the photo fills it edge to edge (no margin, no header, no legend).
 *  - On the photo: right answer green, wrong mark red, unsure mark amber (the same drawing as the detail screen), and the score
 *    ("23/30") written by hand-style into the printed marks box of the sheet.
 *  - A sheet that could not be read, or has no answer key, has no result to show and gets no page.
 */
internal object OmrResultPdf {
    private const val PAGE_W = 595

    fun reportRows(sheets: List<EvalSheet>): List<ReportRow> = sheets.mapNotNull { s ->
        val r = s.result ?: return@mapNotNull null
        ReportRow(
            sheet = s.name, roll = s.roll ?: "-", setIndex = r.setIndex, score = r.score, total = r.total,
            correct = r.correct, wrong = r.wrong, blank = r.blank, review = s.state != EvalState.OK,
            page = r.page, pages = r.pages,
        )
    }

    /** True when [build] would have at least one page. */
    fun hasPages(sheets: List<EvalSheet>): Boolean = sheets.any { it.result != null && it.jpeg != null && it.imgW > 0 && it.imgH > 0 }

    fun build(ctx: Context, sheets: List<EvalSheet>, layout: OmrLayout, lang: Lang): ByteArray {
        val bn = lang == Lang.BN
        val face = ResourcesCompat.getFont(ctx, R.font.kalpurush) ?: Typeface.DEFAULT
        fun digits(s: String) = if (bn) s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("") else s
        fun num(v: Double) = digits(OmrReport.num(v))

        val doc = PdfDocument()
        var pageNo = 0
        for (s in sheets) {
            val res = s.result ?: continue
            val jpeg = s.jpeg ?: continue
            if (s.imgW <= 0 || s.imgH <= 0) continue
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
            val pageH = Math.round(PAGE_W.toFloat() * s.imgH / s.imgW)
            pageNo += 1
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, pageH, pageNo).create())
            val c = page.canvas
            c.drawBitmap(bmp, null, RectF(0f, 0f, PAGE_W.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            val k = PAGE_W.toFloat() / s.imgW
            val al = s.align
            if (al != null) {
                drawFieldBorders(c, al, layout, s, k)
                drawMarks(c, al, res.rows, 0f, 0f, k)
                drawScore(c, al, layout, num(res.score) + "/" + num(res.total), face, k)
            }
            if (s.state == EvalState.OK) drawTick(c, PAGE_W.toFloat(), pageH.toFloat())
            bmp.recycle()
            doc.finishPage(page)
        }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private val BORDER_GREEN = Color.rgb(0x16, 0xA3, 0x4A)
    private val BORDER_RED = Color.rgb(0xDC, 0x26, 0x26)

    /** Border around roll / registration / set / subject blocks that exist on this sheet: green = filled, red = not filled. */
    private fun drawFieldBorders(c: Canvas, al: com.intelliq.rifatplex.data.AlignResult.Ok, layout: OmrLayout, s: EvalSheet, k: Float) {
        val rd = s.read ?: return
        val rollText = s.rollFix ?: s.result?.roll ?: rd.roll?.text
        val rollOk = !rollText.isNullOrEmpty() && '?' !in rollText

        fun border(spec: com.intelliq.rifatplex.data.DigitGridSpec?, ok: Boolean) {
            if (spec == null) return
            val pad = 0.9f
            val lb = spec.labelBox
            val x0 = minOf(lb.x, spec.gx) - pad
            val x1 = maxOf(lb.x + lb.w, spec.gx + spec.xs[spec.cols]) + pad
            val y0 = lb.y - pad
            val y1 = spec.bottom + pad
            val path = android.graphics.Path()
            val corners = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
            corners.forEachIndexed { i, (x, y) ->
                val p = al.toImage.map(x.toDouble(), y.toDouble())
                val px = (p.x * k).toFloat()
                val py = (p.y * k).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (ok) BORDER_GREEN else BORDER_RED
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                strokeJoin = Paint.Join.ROUND
            })
        }

        border(OmrGeometry.rollGrid(layout.omr), rollOk)
        border(OmrGeometry.regGrid(layout.omr), rd.reg?.complete == true)
        // the set column only counts when the paper really has named sets
        if (layout.setCodes.any { it.isNotBlank() }) border(OmrGeometry.setGrid(layout.omr, layout.setCodes), rd.setFilled != null)
        border(OmrGeometry.subjectGrid(layout.omr), rd.subject?.complete == true)
    }

    /** A big soft green tick in the middle of the page, drawn last so it lies over the sheet. */
    private fun drawTick(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val u = w * 0.22f
        val path = android.graphics.Path().apply {
            moveTo(cx - u, cy + u * 0.05f)
            lineTo(cx - u * 0.3f, cy + u * 0.7f)
            lineTo(cx + u * 1.05f, cy - u * 0.8f)
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BORDER_GREEN
            alpha = 80
            style = Paint.Style.STROKE
            strokeWidth = w * 0.07f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }

    /** One ZIP with a PDF per marked sheet, named "001_roll-123456.pdf" (the number keeps equal rolls apart). */
    fun buildZip(ctx: Context, sheets: List<EvalSheet>, layout: OmrLayout, lang: Lang): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            var n = 0
            for (s in sheets) {
                if (!hasPages(listOf(s))) continue
                n += 1
                val roll = (s.roll ?: "").map { if (it.isLetterOrDigit()) it else 'x' }.joinToString("").take(16).ifEmpty { "unknown" }
                z.putNextEntry(java.util.zip.ZipEntry(String.format(java.util.Locale.US, "%03d_roll-%s.pdf", n, roll)))
                z.write(build(ctx, listOf(s), layout, lang))
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * Writes [text] into the left cell of the printed marks box, turned like the box label (top to bottom). The cell is mapped
     * through the same alignment as the bubbles, so the text follows the sheet's tilt and perspective in the photo.
     */
    private fun drawScore(c: Canvas, al: com.intelliq.rifatplex.data.AlignResult.Ok, layout: OmrLayout, text: String, face: Typeface, k: Float) {
        val box = OmrGeometry.marksBox(layout.omr, layout.setCodes) ?: return
        val cellW = 159.12f - box.x
        val cx = box.x + cellW / 2f
        val cy = box.y + box.h / 2f
        // mm -> photo pixels -> page points, as an affine fit through the centre and one step along each axis
        fun pt(x: Float, y: Float): Pair<Float, Float> {
            val p = al.toImage.map(x.toDouble(), y.toDouble())
            return (p.x * k).toFloat() to (p.y * k).toFloat()
        }
        val o = pt(cx, cy)
        val px = pt(cx + 1f, cy)
        val py = pt(cx, cy + 1f)
        val m = android.graphics.Matrix()
        m.setPolyToPoly(
            floatArrayOf(cx, cy, cx + 1f, cy, cx, cy + 1f), 0,
            floatArrayOf(o.first, o.second, px.first, px.second, py.first, py.second), 0, 3,
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x0B, 0x2A, 0x6F)
            typeface = face
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            textSize = 1f
        }
        // text size in mm: as large as fits the cell (along the box and across it)
        val unitW = p.measureText(text).coerceAtLeast(0.01f)
        val size = minOf(5.4f, (box.h - 3f) / unitW, (cellW - 2.4f) / 0.8f)
        p.textSize = size
        c.save()
        c.concat(m)
        c.rotate(90f, cx, cy)
        // centre the line on the cell: shift by half of the text height so the letters sit in the middle
        val fm = p.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, p)
        c.restore()
    }

    /** Same marks as the detail screen: [k] = PDF points per photo pixel, (left, top) = where the photo starts on the page. */
    private fun drawMarks(c: Canvas, al: com.intelliq.rifatplex.data.AlignResult.Ok, rows: List<QResult>, left: Float, top: Float, k: Float) {
        val r = (OmrGeometry.BUBBLE_INNER_R * al.scalePxPerMm * k).toFloat().coerceAtLeast(1.5f)
        fun ring(color: Int, rad: Float, slot: Int, o: Int) {
            val b = OmrGeometry.answerBubbleOfSlot(slot, o)
            val p = al.toImage.map(b.x.toDouble(), b.y.toDouble())
            c.drawCircle(left + (p.x * k).toFloat(), top + (p.y * k).toFloat(), rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
            })
        }
        val green = Color.rgb(0x16, 0xA3, 0x4A)
        val red = Color.rgb(0xDC, 0x26, 0x26)
        val amber = Color.rgb(0xD9, 0x77, 0x06)
        for (q in rows) {
            for (o in q.correct) if (o in 0 until OmrGeometry.OPTIONS) ring(green, r * 1.25f, q.slot, o)
            if (q.status == QStatus.WRONG || q.status == QStatus.MULTI) {
                for (o in q.marked - q.correct) if (o in 0 until OmrGeometry.OPTIONS) ring(red, r * 1.25f, q.slot, o)
            }
            if (q.status == QStatus.UNCERTAIN) for (o in q.marked) if (o in 0 until OmrGeometry.OPTIONS) ring(amber, r * 1.45f, q.slot, o)
        }
    }
}
