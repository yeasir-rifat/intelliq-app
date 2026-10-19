package com.intelliq.rifatplex.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.intelliq.rifatplex.R
import com.intelliq.rifatplex.data.OmrReport
import com.intelliq.rifatplex.data.RankedRow
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The "Report PDF" of an OMR evaluation: every marked sheet in rank order, A4 portrait.
 *
 *  - Page 1: a brand-blue header (logo, kicker, title, exam, date chip, a gold hairline), four summary cards, a
 *    "Top performers" panel (first three ranks) beside a score-distribution chart with the median.
 *  - Then the full ranking table: rank, roll, (set), score with a coloured progress bar, percent, right / wrong / blank
 *    and a short note (same roll on two sheets, or a sheet that still needs a look). It continues on more pages with a
 *    slim header and the same column header.
 *  - Ranking comes from [OmrReport.rank]: equal scores share a rank, several sheets of one student are added up.
 *  - The last page ends with two signature lines (prepared by / checked by) when there is room.
 *  - Every page has a footer: "©IntelliQ • 2026" and "Page x / y".
 *
 * English uses Lato, Bangla uses Kalpurush (which also has the Latin letters and the digits).
 */
internal object OmrReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private const val FOOT_Y = 806f
    private const val ROW_H = 24f
    private const val HEAD_ROW_H = 26f

    private val BLUE = Color.rgb(0x14, 0x6E, 0xF5)
    private val NAVY = Color.rgb(0x0B, 0x2A, 0x6B)
    private val INK = Color.rgb(0x14, 0x15, 0x1A)
    private val MUTED = Color.rgb(0x5B, 0x64, 0x74)
    private val LINE = Color.rgb(0xE3, 0xE7, 0xEE)
    private val ZEBRA = Color.rgb(0xF5, 0xF7, 0xFB)
    private val GREEN = Color.rgb(0x0B, 0x9A, 0x6B)
    private val RED = Color.rgb(0xCC, 0x24, 0x56)
    private val AMBER = Color.rgb(0xD9, 0x77, 0x06)
    private val INDIGO = Color.rgb(0x4F, 0x46, 0xE5)
    private val GOLD = Color.rgb(0xE9, 0xB9, 0x49)

    fun hasRows(sheets: List<EvalSheet>): Boolean = OmrResultPdf.reportRows(sheets).isNotEmpty()

    fun build(ctx: Context, sheets: List<EvalSheet>, examTitle: String, examSub: String, lang: Lang): ByteArray {
        val bn = lang == Lang.BN
        val ranked = OmrReport.rank(OmrResultPdf.reportRows(sheets))
        val lato = ResourcesCompat.getFont(ctx, R.font.lato_regular) ?: Typeface.SANS_SERIF
        val latoBold = ResourcesCompat.getFont(ctx, R.font.lato_bold) ?: Typeface.DEFAULT_BOLD
        val kalpurush = ResourcesCompat.getFont(ctx, R.font.kalpurush) ?: Typeface.DEFAULT

        fun digits(s: String) = if (bn) s.map { if (it in '0'..'9') '\u09E6' + (it - '0') else it }.joinToString("") else s
        fun num(v: Double) = digits(OmrReport.num(v))
        fun t(key: String) = trKey(key, lang)
        fun caps(x: String) = if (bn) x else x.uppercase(Locale.ENGLISH)

        fun paint(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, alpha: Int = 255, spacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color
                this.alpha = alpha
                textSize = size
                if (!bn) letterSpacing = spacing
                typeface = if (bn) kalpurush else if (bold) latoBold else lato
                isFakeBoldText = bn && bold
                textAlign = align
            }

        fun fit(s: String, p: Paint, maxW: Float): String {
            if (p.measureText(s) <= maxW) return s
            var n = s.length
            while (n > 1 && p.measureText(s.substring(0, n) + "\u2026") > maxW) n--
            return s.substring(0, n) + "\u2026"
        }

        fun fill(c: Canvas, r: RectF, rad: Float, color: Int, alpha: Int = 255) =
            c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = alpha })

        fun stroke(c: Canvas, r: RectF, rad: Float, color: Int, w: Float = 0.8f) =
            c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = Paint.Style.STROKE; strokeWidth = w
            })

        val total = ranked.maxOfOrNull { it.row.total } ?: 0.0
        fun pct(r: RankedRow): Int = if (r.row.total > 0) Math.round((r.row.score / r.row.total * 100).coerceIn(0.0, 100.0)).toInt() else 0
        fun barColor(p: Int) = when {
            p >= 80 -> GREEN
            p >= 50 -> BLUE
            p >= 33 -> AMBER
            else -> RED
        }

        val showSet = ranked.any { it.row.setIndex != null }
        // columns of the table: x positions and widths (sum = W - 2 * M = 523)
        val wRank = 38f
        val wSet = if (showSet) 34f else 0f
        val wRoll = if (showSet) 92f else 126f
        val wScore = 70f
        val wBar = 88f
        val wPct = 38f
        val wCnt = 36f
        val xRank = M
        val xRoll = xRank + wRank
        val xSet = xRoll + wRoll
        val xScore = xSet + wSet
        val xBar = xScore + wScore
        val xPct = xBar + wBar
        val xRight = xPct + wPct
        val xWrong = xRight + wCnt
        val xBlank = xWrong + wCnt
        val xNote = xBlank + wCnt
        val xEnd = W - M

        // ----- pagination: page 1 has the header + podium, the others a slim header -----
        val firstTop = 378f
        val nextTop = 74f
        val bottom = FOOT_Y - 18f
        val firstRows = ((bottom - firstTop - HEAD_ROW_H) / ROW_H).toInt()
        val nextRows = ((bottom - nextTop - HEAD_ROW_H) / ROW_H).toInt()
        val pages = ArrayList<List<RankedRow>>()
        var from = 0
        var cap = firstRows
        do {
            val to = minOf(ranked.size, from + cap)
            pages.add(ranked.subList(from, to))
            from = to
            cap = nextRows
        } while (from < ranked.size)

        val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)
        val doc = PdfDocument()
        val stamp = digits(SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date()))
        val title = examTitle.trim().ifEmpty { t("eval.pdf.defaultTitle") }

        for ((pi, rows) in pages.withIndex()) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pi + 1).create())
            val c = page.canvas
            var y: Float

            if (pi == 0) {
                // ---------- header ----------
                val hh = 168f
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, W.toFloat(), hh, BLUE, NAVY, Shader.TileMode.CLAMP) }
                c.drawRect(0f, 0f, W.toFloat(), hh, bg)
                val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 20 }
                c.drawCircle(W - 30f, 10f, 130f, soft)
                c.drawCircle(W - 170f, hh + 24f, 74f, soft)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 46 }
                var gx = 372f
                while (gx < W - 14f) {
                    var gy = 72f
                    while (gy < hh - 14f) { c.drawCircle(gx, gy, 1.1f, dot); gy += 13f }
                    gx += 13f
                }
                // gold hairline along the bottom edge of the header
                c.drawRect(0f, hh - 2.2f, W.toFloat(), hh, Paint().apply {
                    shader = LinearGradient(0f, 0f, W.toFloat(), 0f, GOLD, Color.argb(0, 0xE9, 0xB9, 0x49), Shader.TileMode.CLAMP)
                })
                // logo tile + brand name
                fill(c, RectF(M, 28f, M + 34f, 62f), 9f, Color.WHITE)
                if (logo != null) c.drawBitmap(logo, null, RectF(M + 5f, 33f, M + 29f, 57f), Paint(Paint.FILTER_BITMAP_FLAG))
                c.drawText("IntelliQ", M + 44f, 51f, paint(Color.WHITE, 16f, true))
                // date chip
                val chipP = paint(Color.WHITE, 9f, true)
                val chipW = chipP.measureText(stamp) + 22f
                fill(c, RectF(xEnd - chipW, 34f, xEnd, 54f), 10f, Color.WHITE, 40)
                c.drawText(stamp, xEnd - chipW / 2f, 47.2f, paint(Color.WHITE, 9f, true, Paint.Align.CENTER))
                // title block
                c.drawText(caps(t("eval.rep.kicker")), M, 84f, paint(GOLD, 8.5f, true, spacing = 0.18f))
                c.drawText(t("eval.rep.title"), M, 110f, paint(Color.WHITE, 27f, true))
                val ep = paint(Color.WHITE, 13.5f, true)
                c.drawText(fit(title, ep, xEnd - M), M, 129f, ep)
                val sub = examSub.trim()
                if (sub.isNotEmpty()) c.drawText(fit(sub, paint(Color.WHITE, 10f), xEnd - M), M, 144f, paint(Color.WHITE, 10f, false, Paint.Align.LEFT, 210))

                // ---------- summary cards (overlap the header edge) ----------
                val scores = ranked.map { it.row.score }
                val avg = if (scores.isEmpty()) 0.0 else scores.average()
                val sortedScores = scores.sorted()
                val median = when {
                    sortedScores.isEmpty() -> 0.0
                    sortedScores.size % 2 == 1 -> sortedScores[sortedScores.size / 2]
                    else -> (sortedScores[sortedScores.size / 2 - 1] + sortedScores[sortedScores.size / 2]) / 2.0
                }
                val hi = ranked.firstOrNull()
                val lo = ranked.lastOrNull()
                val gap = 10f
                val cw = (xEnd - M - gap * 3) / 4f
                val cy = 154f
                val ch = 66f
                data class Card(val label: String, val value: String, val unit: String, val color: Int)
                val cards = listOf(
                    Card(t("eval.rep.evaluated"), digits(ranked.size.toString()), "", BLUE),
                    Card(t("eval.rep.avg"), num(avg), "/ " + num(total), INDIGO),
                    Card(t("eval.rep.high"), num(hi?.row?.score ?: 0.0), "/ " + num(hi?.row?.total ?: 0.0), GREEN),
                    Card(t("eval.rep.low"), num(lo?.row?.score ?: 0.0), "/ " + num(lo?.row?.total ?: 0.0), RED),
                )
                for ((i, k) in cards.withIndex()) {
                    val x = M + i * (cw + gap)
                    val r = RectF(x, cy, x + cw, cy + ch)
                    fill(c, RectF(r.left + 1f, r.top + 3.5f, r.right - 1f, r.bottom + 3.5f), 12f, Color.BLACK, 16)
                    fill(c, RectF(r.left, r.top + 1.5f, r.right, r.bottom + 1.5f), 12f, Color.BLACK, 14)
                    fill(c, r, 12f, Color.WHITE)
                    stroke(c, r, 12f, LINE)
                    fill(c, RectF(x + 14f, cy - 0.5f, x + 38f, cy + 2.8f), 1.6f, k.color)
                    val lp = paint(MUTED, 8f, true, spacing = 0.1f)
                    c.drawText(fit(caps(k.label), lp, cw - 24f), x + 14f, cy + 22f, lp)
                    val vp = paint(INK, 23f, true)
                    c.drawText(k.value, x + 14f, cy + 51f, vp)
                    if (k.unit.isNotEmpty()) c.drawText(k.unit, x + 14f + vp.measureText(k.value) + 4f, cy + 51f, paint(MUTED, 9.5f))
                }

                // ---------- top performers (left) ----------
                val py = 244f
                c.drawText(t("eval.rep.top"), M, py, paint(INK, 12f, true))
                fill(c, RectF(M, py + 6f, M + 26f, py + 8f), 1f, BLUE)
                val top = ranked.take(3)
                val lw = 300f
                val medal = listOf(
                    Triple(Color.rgb(0xF2, 0xB8, 0x1C), Color.rgb(0xFE, 0xF6, 0xDC), Color.rgb(0xF3, 0xD0, 0x7A)),
                    Triple(Color.rgb(0x8E, 0x9A, 0xAE), Color.rgb(0xF0, 0xF3, 0xF8), Color.rgb(0xC9, 0xD1, 0xDD)),
                    Triple(Color.rgb(0xC9, 0x7B, 0x3D), Color.rgb(0xFC, 0xEE, 0xE2), Color.rgb(0xE6, 0xB9, 0x8E)),
                )
                for ((i, r) in top.withIndex()) {
                    val ty = py + 18f + i * 38f
                    val rr = RectF(M, ty, M + lw, ty + 32f)
                    val (strong, soft2, edge) = medal[minOf(i, 2)]
                    fill(c, rr, 10f, soft2)
                    stroke(c, rr, 10f, edge, 0.9f)
                    c.drawCircle(M + 20f, ty + 16f, 11.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strong })
                    c.drawText(digits(r.rank.toString()), M + 20f, ty + 20.3f, paint(Color.WHITE, 12f, true, Paint.Align.CENTER))
                    val rollTxt = r.row.roll.ifEmpty { "-" }
                    c.drawText(fit(digits(rollTxt), paint(INK, 11.5f, true), 120f), M + 40f, ty + 14f, paint(INK, 11.5f, true))
                    val setTxt = r.row.setIndex?.let { t("eval.set") + " " + digits((it + 1).toString()) } ?: t("eval.roll")
                    c.drawText(setTxt, M + 40f, ty + 26f, paint(MUTED, 8.5f))
                    c.drawText(num(r.row.score), M + lw - 74f, ty + 20.5f, paint(INK, 15f, true, Paint.Align.RIGHT))
                    c.drawText("/ " + num(r.row.total), M + lw - 70f, ty + 20.5f, paint(MUTED, 9f))
                    c.drawText(digits(pct(r).toString()) + "%", M + lw - 12f, ty + 19.5f, paint(barColor(pct(r)), 10.5f, true, Paint.Align.RIGHT))
                }

                // ---------- score distribution (right) ----------
                val dx = M + lw + 18f
                val dw = xEnd - dx
                c.drawText(t("eval.rep.dist"), dx, py, paint(INK, 12f, true))
                fill(c, RectF(dx, py + 6f, dx + 26f, py + 8f), 1f, BLUE)
                val counts = IntArray(5)
                for (r in ranked) counts[minOf(4, pct(r) / 20)]++
                val maxCount = maxOf(1, counts.maxOrNull() ?: 0)
                val bandColor = intArrayOf(RED, AMBER, INDIGO, BLUE, GREEN)
                val labelW = 46f
                val countW = 22f
                for (b in 0 until 5) {
                    val by2 = py + 20f + (4 - b) * 17f
                    val lbl = digits("${b * 20}–${b * 20 + 20}%")
                    c.drawText(lbl, dx, by2 + 9f, paint(MUTED, 8.5f))
                    val tr0 = RectF(dx + labelW, by2 + 1.5f, dx + dw - countW, by2 + 10.5f)
                    fill(c, tr0, 4.5f, Color.rgb(0xE9, 0xED, 0xF3))
                    if (counts[b] > 0) {
                        val fw = maxOf(9f, (tr0.width()) * counts[b] / maxCount)
                        fill(c, RectF(tr0.left, tr0.top, tr0.left + fw, tr0.bottom), 4.5f, bandColor[b])
                    }
                    c.drawText(digits(counts[b].toString()), dx + dw, by2 + 9.2f, paint(INK, 9.5f, true, Paint.Align.RIGHT))
                }
                c.drawText(
                    t("eval.rep.median") + "  " + num(median) + " / " + num(total),
                    dx, py + 20f + 5 * 17f + 10f, paint(MUTED, 9f, true),
                )
                y = firstTop
            } else {
                // slim header
                val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, BLUE, NAVY, Shader.TileMode.CLAMP) }
                c.drawRect(0f, 0f, W.toFloat(), 6f, strip)
                if (logo != null) c.drawBitmap(logo, null, RectF(M, 24f, M + 22f, 46f), Paint(Paint.FILTER_BITMAP_FLAG))
                c.drawText("IntelliQ", M + 30f, 40f, paint(BLUE, 13f, true))
                c.drawText(fit(title, paint(INK, 11f, true), 300f), xEnd, 40f, paint(INK, 11f, true, Paint.Align.RIGHT))
                c.drawRect(M, 54f, xEnd, 54.8f, Paint().apply { color = LINE })
                y = nextTop
            }

            // ---------- table header ----------
            fill(c, RectF(M, y, xEnd, y + HEAD_ROW_H), 8f, NAVY)
            val hp = paint(Color.WHITE, 8.5f, true, spacing = 0.08f)
            val hpC = paint(Color.WHITE, 8.5f, true, Paint.Align.CENTER, spacing = 0.08f)
            val hy = y + HEAD_ROW_H / 2f + 3.4f
            c.drawText(caps(t("eval.pdf.rank")), xRank + wRank / 2f, hy, hpC)
            c.drawText(caps(t("eval.roll")), xRoll + 6f, hy, hp)
            if (showSet) c.drawText(caps(t("eval.set")), xSet + wSet / 2f, hy, hpC)
            c.drawText(caps(t("eval.pdf.score")), xScore, hy, hp)
            c.drawText("%", xPct + wPct / 2f, hy, hpC)
            c.drawText(caps(t("eval.c.right")), xRight + wCnt / 2f, hy, hpC)
            c.drawText(caps(t("eval.c.wrong")), xWrong + wCnt / 2f, hy, hpC)
            c.drawText(caps(t("eval.c.blank")), xBlank + wCnt / 2f, hy, hpC)
            c.drawText(caps(t("eval.pdf.note")), xNote + 4f, hy, hp)
            y += HEAD_ROW_H

            // ---------- rows ----------
            for ((i, r) in rows.withIndex()) {
                val top = y + i * ROW_H
                if (r.rank <= 3) {
                    val tint = when (r.rank) { 1 -> Color.rgb(0xFE, 0xF6, 0xDC); 2 -> Color.rgb(0xF0, 0xF3, 0xF8); else -> Color.rgb(0xFC, 0xEE, 0xE2) }
                    val bar = when (r.rank) { 1 -> Color.rgb(0xF2, 0xB8, 0x1C); 2 -> Color.rgb(0x8E, 0x9A, 0xAE); else -> Color.rgb(0xC9, 0x7B, 0x3D) }
                    c.drawRect(M, top, xEnd, top + ROW_H, Paint().apply { color = tint })
                    c.drawRect(M, top, M + 3f, top + ROW_H, Paint().apply { color = bar })
                } else if (i % 2 == 1) {
                    c.drawRect(M, top, xEnd, top + ROW_H, Paint().apply { color = ZEBRA })
                }
                c.drawRect(M, top + ROW_H - 0.5f, xEnd, top + ROW_H, Paint().apply { color = LINE })
                val by = top + ROW_H / 2f + 3.4f
                // rank: a coloured disc for the first three ranks
                if (r.rank <= 3) {
                    val col = when (r.rank) { 1 -> Color.rgb(0xF2, 0xB8, 0x1C); 2 -> Color.rgb(0x8E, 0x9A, 0xAE); else -> Color.rgb(0xC9, 0x7B, 0x3D) }
                    c.drawCircle(xRank + wRank / 2f, top + ROW_H / 2f, 8.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
                    c.drawText(digits(r.rank.toString()), xRank + wRank / 2f, by, paint(Color.WHITE, 9.5f, true, Paint.Align.CENTER))
                } else {
                    c.drawText(digits(r.rank.toString()), xRank + wRank / 2f, by, paint(INK, 10f, true, Paint.Align.CENTER))
                }
                c.drawText(fit(digits(r.row.roll.ifEmpty { "-" }), paint(INK, 10f, true), wRoll - 8f), xRoll + 6f, by, paint(INK, 10f, true))
                if (showSet) {
                    val st = r.row.setIndex?.let { digits((it + 1).toString()) } ?: "-"
                    c.drawText(st, xSet + wSet / 2f, by, paint(MUTED, 10f, false, Paint.Align.CENTER))
                }
                c.drawText(num(r.row.score) + " / " + num(r.row.total), xScore, by, paint(INK, 10f, true))
                // progress bar
                val p = pct(r)
                val bx = xBar + 2f
                val bw = wBar - 10f
                val br = RectF(bx, top + ROW_H / 2f - 3f, bx + bw, top + ROW_H / 2f + 3f)
                fill(c, br, 3f, Color.rgb(0xE4, 0xE8, 0xEF))
                if (p > 0) fill(c, RectF(br.left, br.top, br.left + maxOf(6f, bw * p / 100f), br.bottom), 3f, barColor(p))
                c.drawText(digits(p.toString()) + "%", xPct + wPct / 2f, by, paint(barColor(p), 9.5f, true, Paint.Align.CENTER))
                c.drawText(digits(r.row.correct.toString()), xRight + wCnt / 2f, by, paint(GREEN, 10f, true, Paint.Align.CENTER))
                c.drawText(digits(r.row.wrong.toString()), xWrong + wCnt / 2f, by, paint(RED, 10f, true, Paint.Align.CENTER))
                c.drawText(digits(r.row.blank.toString()), xBlank + wCnt / 2f, by, paint(MUTED, 10f, false, Paint.Align.CENTER))
                val note = when {
                    r.duplicateRoll -> t("eval.rep.dup") to RED
                    r.row.review -> t("eval.rep.chk") to AMBER
                    else -> null
                }
                if (note != null) {
                    val np = paint(note.second, 8.5f, true)
                    val txt = fit(note.first, np, xEnd - xNote - 16f)
                    val tw2 = np.measureText(txt)
                    fill(c, RectF(xNote + 2f, top + 5.5f, xNote + 2f + tw2 + 10f, top + ROW_H - 5.5f), 6f, note.second, 36)
                    c.drawText(txt, xNote + 7f, by - 0.2f, np)
                }
            }
            val endY = y + rows.size * ROW_H
            c.drawRect(M, endY, xEnd, endY + 1.2f, Paint().apply { color = NAVY })
            if (pi == pages.lastIndex) {
                if (endY + 26f < FOOT_Y - 8f) c.drawText(t("eval.rep.tie"), M, endY + 17f, paint(MUTED, 8.5f))
                // signature lines, only when there is comfortable room
                val sigY = FOOT_Y - 30f
                if (endY + 60f < sigY) {
                    val sigW = 150f
                    val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; strokeWidth = 0.7f }
                    c.drawLine(M, sigY, M + sigW, sigY, lp)
                    c.drawLine(xEnd - sigW, sigY, xEnd, sigY, lp)
                    c.drawText(t("eval.rep.prepared"), M, sigY + 12f, paint(MUTED, 8.5f))
                    c.drawText(t("eval.rep.checked"), xEnd - sigW, sigY + 12f, paint(MUTED, 8.5f))
                }
            }

            // ---------- footer ----------
            c.drawRect(M, FOOT_Y, xEnd, FOOT_Y + 1f, Paint().apply {
                shader = LinearGradient(M, 0f, xEnd, 0f, BLUE, LINE, Shader.TileMode.CLAMP)
            })
            c.drawText("\u00A9IntelliQ \u2022 " + digits("2026"), M, FOOT_Y + 17f, paint(MUTED, 8.5f, true, spacing = 0.04f))
            c.drawText(
                t("eval.rep.page").replace("{p}", digits((pi + 1).toString())).replace("{n}", digits(pages.size.toString())),
                xEnd, FOOT_Y + 17f, paint(MUTED, 8.5f, true, Paint.Align.RIGHT, spacing = 0.04f),
            )
            doc.finishPage(page)
        }
        logo?.recycle()
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
