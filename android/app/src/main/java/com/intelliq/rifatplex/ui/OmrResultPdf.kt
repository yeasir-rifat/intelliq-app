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
import com.intelliq.rifatplex.data.OmrReport
import com.intelliq.rifatplex.data.QResult
import com.intelliq.rifatplex.data.QStatus
import com.intelliq.rifatplex.data.RankedRow
import com.intelliq.rifatplex.data.ReportRow
import com.intelliq.rifatplex.data.formatNumber
import java.io.ByteArrayOutputStream

/*
 * Step 4.7 - the result PDF of an OMR evaluation (A4, 595 x 842 pt).
 *
 *  - Page 1..n: the batch summary table (rank, roll, set, score, right / wrong / blank, notes), 34 rows a page.
 *  - Then one page per sheet: roll, set, score and counts on top, then THE STUDENT'S OWN PHOTO with the marks drawn on it at the
 *    printed bubble positions: right answer green, wrong mark red, unsure mark amber (the same drawing as the detail screen).
 *    A sheet that could not be read or has no answer key gets a page with the reason and its photo.
 *
 * Why the photo and not a re-drawn blank sheet (the plan said re-draw): the photo shows what the student really marked, so a
 * teacher can compare marks and ink at a glance and spot a misread; and it needs no second layout pass of the sheet.
 * Text uses the app's Bangla font (Kalpurush) so Bangla names and digits print on every phone.
 */
internal object OmrResultPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private const val ROWS_PER_PAGE = 34

    fun reportRows(sheets: List<EvalSheet>): List<ReportRow> = sheets.mapNotNull { s ->
        val r = s.result ?: return@mapNotNull null
        ReportRow(
            sheet = s.name, roll = s.roll ?: "-", setIndex = r.setIndex, score = r.score, total = r.total,
            correct = r.correct, wrong = r.wrong, blank = r.blank, review = s.state != EvalState.OK,
            page = r.page, pages = r.pages,
        )
    }

    fun build(ctx: Context, sheets: List<EvalSheet>, title: String, lang: Lang): ByteArray {
        val bn = lang == Lang.BN
        val face = ResourcesCompat.getFont(ctx, R.font.kalpurush) ?: Typeface.DEFAULT
        val ranked = OmrReport.rank(reportRows(sheets))
        val doc = PdfDocument()
        var pageNo = 0
        fun newPage(): PdfDocument.Page {
            pageNo += 1
            return doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        }

        fun paint(size: Float, color: Int = Color.BLACK, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = face
            isFakeBoldText = bold
        }

        fun digits(s: String) = if (bn) s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("") else s
        fun num(v: Double) = digits(OmrReport.num(v))
        fun n(v: Int) = formatNumber(v, bn)
        fun t(key: String) = trKey(key, lang)

        // ---------- summary pages ----------
        val chunks = if (ranked.isEmpty()) listOf(emptyList<RankedRow>()) else ranked.chunked(ROWS_PER_PAGE)
        val cols = floatArrayOf(M, M + 40, M + 150, M + 205, M + 300, M + 405) // rank, roll, set, score, R/W/B, note
        for ((ci, chunk) in chunks.withIndex()) {
            val page = newPage()
            val c = page.canvas
            c.drawText(title.take(70), M, M + 16, paint(16f, bold = true))
            c.drawText(
                t("eval.pdf.summary") + " · " + n(sheets.size) + " " + t("eval.pdf.sheets") + (if (chunks.size > 1) " · " + n(ci + 1) + "/" + n(chunks.size) else ""),
                M, M + 36, paint(11f, Color.DKGRAY),
            )
            var y = M + 66
            val head = paint(10f, Color.DKGRAY, bold = true)
            val heads = listOf("eval.pdf.rank", "eval.roll", "eval.set", "eval.pdf.score", "eval.pdf.rwb", "eval.pdf.note")
            for (i in heads.indices) c.drawText(t(heads[i]), cols[i], y, head)
            val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }
            c.drawLine(M, y + 5, W - M, y + 5, rule)
            y += 20
            val body = paint(11f)
            for (r in chunk) {
                val x = r.row
                c.drawText(n(r.rank), cols[0], y, body)
                c.drawText(digits(x.roll).take(14), cols[1], y, body)
                c.drawText(x.setIndex?.let { n(it + 1) } ?: "-", cols[2], y, body)
                c.drawText(num(x.score) + " / " + num(x.total), cols[3], y, body)
                c.drawText(n(x.correct) + " / " + n(x.wrong) + " / " + n(x.blank), cols[4], y, body)
                val note = listOfNotNull(
                    if (r.duplicateRoll) t("eval.pdf.dup") else null,
                    if (x.review) t("eval.pdf.review") else null,
                ).joinToString(", ")
                if (note.isNotEmpty()) c.drawText(note, cols[5], y, paint(9f, Color.rgb(0xB4, 0x53, 0x09)))
                y += 20
            }
            if (ranked.isEmpty()) c.drawText(t("eval.pdf.none"), M, y, body)
            doc.finishPage(page)
        }

        // ---------- one page per sheet ----------
        for (s in sheets) {
            val page = newPage()
            val c = page.canvas
            val res = s.result
            var y = M + 14
            c.drawText(
                s.name + " · " + t("eval.roll") + " " + digits(s.roll ?: "-") +
                    (res?.setIndex?.let { " · " + t("eval.set") + " " + n(it + 1) } ?: ""),
                M, y, paint(14f, bold = true),
            )
            y += 22
            if (res != null) {
                c.drawText(t("eval.score").replace("{s}", num(res.score)).replace("{t}", num(res.total)), M, y, paint(16f, Color.BLACK, bold = true))
                y += 18
                c.drawText(
                    t("eval.counts").replace("{a}", n(res.correct)).replace("{b}", n(res.wrong)).replace("{c}", n(res.blank)).replace("{u}", n(res.review.size)),
                    M, y, paint(10.5f, Color.DKGRAY),
                )
                y += 15
            } else {
                val why = if (s.state == EvalState.FAILED) t(s.failKey ?: "scan.err.read") else t("eval.state.UNMATCHED")
                y = wrapText(c, why, M, y, W - 2 * M, paint(11f, Color.rgb(0xDC, 0x26, 0x26)), 14f)
            }
            for (f in s.flags) {
                if (f.name == "KEY_INCOMPLETE" && res == null) continue
                y = wrapText(c, "• " + t("eval.flag." + f.name), M, y, W - 2 * M, paint(9f, Color.rgb(0xB4, 0x53, 0x09)), 11.5f)
            }
            y += 6
            val jpeg = s.jpeg
            val bmp = if (jpeg != null) BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) else null
            if (bmp != null && s.imgW > 0 && s.imgH > 0) {
                val availW = W - 2 * M
                val availH = H - M - y - 16
                val scale = minOf(availW / bmp.width, availH / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val left = M + (availW - dw) / 2
                c.drawBitmap(bmp, null, RectF(left, y, left + dw, y + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                val al = s.align
                if (al != null) drawMarks(c, al, res?.rows ?: emptyList(), left, y, dw / s.imgW)
                bmp.recycle()
                c.drawText(t("eval.legend"), M, H - M + 4, paint(8f, Color.GRAY))
            }
            doc.finishPage(page)
        }

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** Draws [text] wrapped to [maxW]; returns the y below the last line. */
    private fun wrapText(c: Canvas, text: String, x: Float, y0: Float, maxW: Float, p: Paint, lineH: Float): Float {
        var y = y0
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val tryLine = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(tryLine) > maxW && line.isNotEmpty()) {
                c.drawText(line.toString(), x, y, p)
                y += lineH
                line = StringBuilder(word)
            } else {
                line = StringBuilder(tryLine)
            }
        }
        if (line.isNotEmpty()) {
            c.drawText(line.toString(), x, y, p)
            y += lineH
        }
        return y
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
