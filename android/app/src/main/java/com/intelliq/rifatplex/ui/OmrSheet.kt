package com.intelliq.rifatplex.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import com.intelliq.rifatplex.data.BrandSettings
import com.intelliq.rifatplex.data.DigitGridSpec
import com.intelliq.rifatplex.data.DocFields
import com.intelliq.rifatplex.data.OmrGeometry
import com.intelliq.rifatplex.data.OmrSettings
import com.intelliq.rifatplex.data.QrCode
import com.intelliq.rifatplex.data.QrPayload
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.SheetStyle
import com.intelliq.rifatplex.ui.theme.SolaimanLipi
import com.intelliq.rifatplex.ui.theme.Tinos

/*
 * OMR bubble sheet (A4), laid out like the SSC / HSC board answer sheet. Everything is drawn on ONE Canvas in
 * millimetres, so the preview and the PDF are identical and the bubbles keep their exact size and place whatever the
 * phone's screen density is (a scanner needs that).
 *
 * All numbers below were measured on the reference sheet (the 50-answer SSC OMR PDF, A4) at 200 dpi:
 *
 * Layout (x, y in mm from the top-left corner of the A4 page):
 *   four corner squares (4.83 mm) + 52 timing dashes down the left edge (4.504 mm apart, y 37.1 .. 266.8)
 *   header        y 14..46    board / institution, exam line + 4 year boxes, sheet title, two banners
 *   answer blocks y 50.73..269.3   x 23.31..56.6 and 62.95..96.2, 25 rows each, rows about 8.63 mm apart,
 *                 4 bubbles per row (6.10 mm apart), always 50 rows
 *   right side    roll number, set code (blank), marks box, invigilator box, registration number, subject code
 *   bottom right  note, instructions with the filling examples
 *
 * The sheet always has 50 answer rows, however few MCQs the paper has; 51+ MCQs continue on the next sheet.
 * Automatic mode builds the texts from the document fields and the branding; custom mode ([OmrSettings]) lets the user
 * change every text, show / hide every block and set the digits of the roll, registration and subject code.
 */

/** Answers per sheet: 2 blocks of 25. More MCQs continue on the next sheet. */
internal const val OMR_ROWS_PER_PAGE = OmrGeometry.ROWS_PER_PAGE
private const val OMR_ROWS_PER_BLOCK = OmrGeometry.ROWS_PER_BLOCK
private const val OMR_OPTIONS = OmrGeometry.OPTIONS
private const val OMR_PAGE_W_MM = OmrGeometry.PAGE_W_MM

private val OMR_INK = Color(0xFFEC008C) // outlines and letters (the magenta of the reference sheet)
private val OMR_BLACK = Color(0xFF231F20) // scanner marks and text

private const val PT_MM = 0.35278f // 1 pt in mm

// ---- drawing-only values (mm)
private const val FRAME_SW = 0.13f // hairline of every frame
private const val RING_SW = OmrGeometry.RING_STROKE // stroke of a bubble
private const val TINT = 0.10f

// ---- measured geometry: since step 4.1 the numbers live in data/OmrGeometry.kt (the scanner reads the same ones)
private const val RING_R = OmrGeometry.RING_R
private const val HEAD_TOP = OmrGeometry.HEAD_TOP
private const val HEAD_BOTTOM = OmrGeometry.HEAD_BOTTOM
private const val HALF_BAND = OmrGeometry.HALF_BAND
private const val BLOCK_W = OmrGeometry.BLOCK_W
private const val QNUM_W = OmrGeometry.QNUM_W
private const val GRID_BOX_ROW = OmrGeometry.GRID_BOX_ROW
private val ANS_Y = OmrGeometry.ANS_Y

private class OmrFaces(val bn: Typeface, val bnBold: Typeface, val en: Typeface, val enBold: Typeface)

@Composable
private fun rememberOmrFaces(): OmrFaces {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver) {
        fun face(f: FontFamily, w: FontWeight): Typeface =
            resolver.resolve(f, w, FontStyle.Normal, FontSynthesis.All).value as? Typeface ?: Typeface.DEFAULT
        OmrFaces(
            face(SolaimanLipi, FontWeight.Normal), face(SolaimanLipi, FontWeight.Bold), // Bangla: fixed (SheetStyle)
            face(Tinos, FontWeight.Normal), face(Tinos, FontWeight.Bold),
        )
    }
}

/** Draws text with the Android canvas (same way as the MCQ option badges): real vector text in the PDF. */
private class OmrText(private val nc: android.graphics.Canvas, private val k: Float, private val faces: OmrFaces) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun hasBangla(s: String) = s.any { it.code in 0x0980..0x09FF }

    /** Width in mm of [s] at [pt] points (not shrunk). */
    fun width(s: String, pt: Float, bold: Boolean = false): Float {
        if (s.isEmpty()) return 0f
        paint.typeface = if (hasBangla(s)) (if (bold) faces.bnBold else faces.bn) else (if (bold) faces.enBold else faces.en)
        paint.textSize = pt * PT_MM * k
        return paint.measureText(s) / k
    }

    /** Same as [draw] but turned 90 degrees clockwise around ([x], [cy]): reads from top to bottom. */
    fun drawDown(s: String, x: Float, cy: Float, pt: Float, color: Color, bold: Boolean = false, maxW: Float = Float.MAX_VALUE) {
        nc.save()
        nc.rotate(90f, x * k, cy * k)
        draw(s, x, cy, pt, color, bold, Paint.Align.CENTER, maxW)
        nc.restore()
    }

    /** [x] / [cy] and [maxW] are in mm; [pt] in points. Text wider than [maxW] is shrunk to fit. */
    fun draw(
        s: String, x: Float, cy: Float, pt: Float, color: Color,
        bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER, maxW: Float = Float.MAX_VALUE,
    ) {
        if (s.isEmpty()) return
        paint.typeface = if (hasBangla(s)) (if (bold) faces.bnBold else faces.bn) else (if (bold) faces.enBold else faces.en)
        paint.color = color.toArgb()
        paint.textAlign = align
        paint.textSize = pt * PT_MM * k
        val w = paint.measureText(s)
        val limit = maxW * k
        if (w > limit) paint.textSize *= (limit / w).coerceAtLeast(0.55f)
        val fm = paint.fontMetrics
        nc.drawText(s, x * k, cy * k - (fm.ascent + fm.descent) / 2f, paint)
    }
}

/**
 * One OMR sheet. [numbers] are the 50 question numbers of this sheet (rows past the last MCQ are numbered on, so the
 * sheet is always full). [setCodes] are the set names offered in the (blank) set-code column. [omr] is the OMR setting
 * of the document: automatic or custom.
 */
@Composable
internal fun OmrPage(
    numbers: List<Int>,
    pageIndex: Int,
    pageCount: Int,
    doc: DocFields,
    brand: BrandSettings,
    setCodes: List<String>,
    omr: OmrSettings,
    lang: Lang,
    modifier: Modifier = Modifier,
    /** What the QR code of this sheet says (step 4.2); null = no QR code is printed. */
    qr: QrPayload? = null,
) {
    val bn = lang == Lang.BN
    val faces = rememberOmrFaces()
    val qrMatrix = remember(qr) { qr?.let { QrCode.encode(it.encode()) } }
    fun t(key: String) = trKey(key, lang)
    val o = omr.effective()
    fun pick(custom: String, key: String) = custom.trim().ifEmpty { t(key) }

    // ---- texts: automatic from the document + branding, custom ones win when filled in
    val brandName = if (brand.enabled && brand.showName) brand.name.trim() else ""
    val autoLine1 = brandName.ifEmpty { doc.title.trim() }
    val autoLine2 = (if (brandName.isNotEmpty()) listOf(doc.title.trim(), doc.subtitle.trim()) else listOf(doc.subtitle.trim()))
        .filter { it.isNotEmpty() }.joinToString(" • ")
    val line1 = o.boardName.trim().ifEmpty { autoLine1 }
    val line2 = o.examLine.trim().ifEmpty { autoLine2 }
    val sheetTitle = pick(o.sheetTitle, "omr.sheetName")
    val banner1 = pick(o.banner1, "omr.banner1")
    val banner2 = pick(o.banner2, "omr.banner")
    val rules = o.rules.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(6)
        .ifEmpty { (1..5).map { t("omr.rule$it") } }
    val optionLabels = if (bn) listOf("ক", "খ", "গ", "ঘ", "ঙ") else listOf("A", "B", "C", "D", "E")
    val setLabels = setCodes.map { it.trim() }.filter { it.isNotEmpty() }.take(10).ifEmpty { optionLabels.take(4) }
    val pageLabel = if (pageCount > 1) "${t("doc.page")} ${formatNumber(pageIndex + 1, bn)}/${formatNumber(pageCount, bn)}" else ""
    fun digitsIn(s: String) = if (bn) s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("") else s

    Canvas(modifier) {
        val k = size.width / OMR_PAGE_W_MM
        fun mm(v: Float) = v * k
        val text = OmrText(drawContext.canvas.nativeCanvas, k, faces)

        fun rect(x: Float, y: Float, w: Float, h: Float, sw: Float = FRAME_SW, c: Color = OMR_INK) =
            drawRect(c, Offset(mm(x), mm(y)), Size(mm(w), mm(h)), style = Stroke(mm(sw)))

        fun fillRect(x: Float, y: Float, w: Float, h: Float, c: Color) =
            drawRect(c, Offset(mm(x), mm(y)), Size(mm(w), mm(h)))

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, sw: Float = FRAME_SW, c: Color = OMR_INK) =
            drawLine(c, Offset(mm(x1), mm(y1)), Offset(mm(x2), mm(y2)), strokeWidth = mm(sw))

        fun bubble(cx: Float, cy: Float, label: String) {
            drawCircle(OMR_INK, mm(RING_R), Offset(mm(cx), mm(cy)), style = Stroke(mm(RING_SW)))
            text.draw(label, cx, cy, 6.5f, OMR_INK, maxW = 2.4f)
        }

        /** Label box on top of a block: outline + black title. */
        fun labelBox(x: Float, y: Float, w: Float, h: Float, label: String, pt: Float = 10f) {
            rect(x, y, w, h)
            text.draw(label, x + w / 2f, y + h / 2f, pt, OMR_BLACK, maxW = w - 2f)
        }

        /**
         * A digit grid: a row of write-in boxes on top and one bubble column per column of [spec] (the positions come
         * from [OmrGeometry]). [labelsOf] gives the bubble labels of every column, [tinted] says which columns carry the
         * pink tint. Returns the bottom y of the frame.
         */
        fun digitGrid(spec: DigitGridSpec, labelsOf: (Int) -> List<String>, tinted: (Int) -> Boolean): Float {
            val cols = spec.cols
            val gx = spec.gx
            val gy = spec.gy
            val xs = spec.xs
            for (c in 0 until cols) if (tinted(c)) {
                fillRect(gx + xs[c], gy + GRID_BOX_ROW, spec.widths[c], spec.colHeight(c) - GRID_BOX_ROW, OMR_INK.copy(alpha = TINT))
            }
            line(gx, gy, gx + xs[cols], gy)
            line(gx, gy + GRID_BOX_ROW, gx + xs[cols], gy + GRID_BOX_ROW)
            for (c in 0..cols) {
                val h = when (c) {
                    0 -> spec.colHeight(0)
                    cols -> spec.colHeight(cols - 1)
                    else -> maxOf(spec.colHeight(c - 1), spec.colHeight(c))
                }
                line(gx + xs[c], gy, gx + xs[c], gy + h)
            }
            for (c in 0 until cols) line(gx + xs[c], gy + spec.colHeight(c), gx + xs[c + 1], gy + spec.colHeight(c))
            for (c in 0 until cols) labelsOf(c).forEachIndexed { i, lab ->
                val p = spec.bubble(c, i)
                bubble(p.x, p.y, lab)
            }
            return spec.bottom
        }
        // the digits of the roll / registration / subject grids are always 0-9 (as on the reference sheet)
        val digitLabels = (0..9).map { it.toString() }

        // ---- scanner marks: four corner squares
        OmrGeometry.cornerMarks().forEach { r -> fillRect(r.x, r.y, r.w, r.h, OMR_BLACK) }
        // ---- timing dashes: 52 of them, down the whole left edge, independent of the number of answers
        OmrGeometry.timingDashes().forEach { r -> fillRect(r.x, r.y, r.w, r.h, OMR_BLACK) }

        // ---- QR code: black modules only, in ONE path (no hairline gaps between the rows, vector in the PDF).
        // The 18 mm box keeps a white quiet zone of 2 mm around the 14 mm code; the scanner looks in the same box.
        if (qrMatrix != null) {
            val box = OmrGeometry.qrBox()
            val mod = OmrGeometry.QR_CODE_MM / qrMatrix.size
            val x0 = box.x + OmrGeometry.QR_QUIET_MM
            val y0 = box.y + OmrGeometry.QR_QUIET_MM
            val path = Path()
            qrMatrix.runs().forEach { (row, col, len) ->
                path.addRect(Rect(mm(x0 + col * mod), mm(y0 + row * mod), mm(x0 + (col + len) * mod), mm(y0 + (row + 1) * mod)))
            }
            drawPath(path, OMR_BLACK)
        }

        // ---- header
        if (line1.isNotEmpty()) text.draw(line1, 105f, 18.5f, SheetStyle.HEADING_PT, OMR_BLACK, bold = true, maxW = 165f)
        run { // exam line centred on the page, the year boxes follow it
            val boxW = 4.95f
            val boxes = if (o.showYear) 4 else 0
            val gap = if (line2.isNotEmpty() && boxes > 0) 1.0f else 0f
            val textMax = 2f * (86f - gap - boxes * boxW)
            val tw = if (line2.isEmpty()) 0f else minOf(text.width(line2, SheetStyle.SUBHEADING_PT, true), textMax)
            if (line2.isNotEmpty()) text.draw(line2, 105f - tw / 2f, 25.3f, SheetStyle.SUBHEADING_PT, OMR_BLACK, bold = true, align = Paint.Align.LEFT, maxW = textMax)
            val bx0 = if (line2.isEmpty()) 105f - boxes * boxW / 2f else 105f + tw / 2f + gap
            val prefix = digitsIn(o.yearPrefix.trim()).take(boxes)
            for (i in 0 until boxes) {
                val bx = bx0 + i * boxW
                rect(bx, 22.72f, boxW, 5.08f, 0.25f, OMR_BLACK)
                prefix.getOrNull(i)?.let { text.draw(it.toString(), bx + boxW / 2f, 25.3f, 10f, OMR_BLACK, bold = true) }
            }
        }
        text.draw(sheetTitle, 105f, 30.5f, SheetStyle.SUBHEADING_PT, OMR_BLACK, bold = true, maxW = OmrGeometry.TITLE_MAX_W) // keeps clear of the QR box
        if (o.showBanner1) {
            fillRect(49.14f, 33.97f, 111.38f, 5.46f, OMR_INK)
            text.draw(banner1, 104.83f, 36.7f, 8f, Color.White, bold = true, maxW = 109f)
        }
        if (o.showBanner2) { // the outlined box also frames the pink banner above it
            val by = if (o.showBanner1) 33.9f else 39.43f
            rect(49.14f, by, 111.38f, 45.2f - by, FRAME_SW, OMR_BLACK)
            text.draw(banner2, 104.83f, 42.3f, 9f, OMR_BLACK, bold = true, maxW = 109f)
        }
        if (pageLabel.isNotEmpty()) text.draw(pageLabel, 105f, 290.5f, 7f, OMR_INK)

        // ---- answer blocks: always 25 rows each
        val qHead = pick(o.qHeader, "omr.qNo")
        val aHead = pick(o.ansHeader, "omr.answer")
        fun answerBlock(block: Int, nums: List<Int>) {
            if (nums.isEmpty()) return
            val x0 = OmrGeometry.BLOCK_X[block]
            val bottom = ANS_Y[nums.size - 1] + HALF_BAND
            nums.indices.forEach { i ->
                val cy = ANS_Y[i]
                if (o.shading) { // every answer has its own pink band, the gap between two answers stays white
                    val y0 = maxOf(cy - HALF_BAND, HEAD_BOTTOM)
                    fillRect(x0, y0, BLOCK_W, cy + HALF_BAND - y0, OMR_INK.copy(alpha = TINT))
                    if (i == 0) fillRect(x0 + QNUM_W, y0, BLOCK_W - QNUM_W, cy + HALF_BAND - y0, OMR_INK.copy(alpha = TINT)) // darker first band
                } else if (i > 0) line(x0, (cy + ANS_Y[i - 1]) / 2f, x0 + BLOCK_W, (cy + ANS_Y[i - 1]) / 2f, 0.12f, OMR_INK.copy(alpha = 0.35f))
            }
            rect(x0, HEAD_TOP, BLOCK_W, bottom - HEAD_TOP)
            line(x0, HEAD_BOTTOM, x0 + BLOCK_W, HEAD_BOTTOM)
            line(x0 + QNUM_W, HEAD_TOP, x0 + QNUM_W, bottom)
            val qLines = qHead.split('\n')
            qLines.forEachIndexed { li, s ->
                val dy = (li - (qLines.size - 1) / 2f) * 2.7f
                text.draw(s.trim(), x0 + QNUM_W / 2f, HEAD_TOP + (HEAD_BOTTOM - HEAD_TOP) / 2f + dy, 5.5f, OMR_BLACK, bold = true, maxW = QNUM_W - 0.8f)
            }
            text.draw(aHead, x0 + QNUM_W + (BLOCK_W - QNUM_W) / 2f, HEAD_TOP + (HEAD_BOTTOM - HEAD_TOP) / 2f, 12f, OMR_BLACK, maxW = BLOCK_W - QNUM_W - 2f)
            nums.forEachIndexed { i, n ->
                val cy = ANS_Y[i]
                text.draw(formatNumber(n, bn), x0 + QNUM_W / 2f, cy, 7.5f, OMR_BLACK, bold = true, maxW = QNUM_W - 0.8f)
                for (op in 0 until OMR_OPTIONS) {
                    val p = OmrGeometry.answerBubble(block, i, op)
                    bubble(p.x, p.y, optionLabels[op])
                }
            }
        }
        val first = numbers.take(OMR_ROWS_PER_BLOCK)
        val second = numbers.drop(OMR_ROWS_PER_BLOCK).take(OMR_ROWS_PER_BLOCK)
        answerBlock(0, first)
        answerBlock(1, second)

        // ---- roll number
        val rollGrid = OmrGeometry.rollGrid(o)
        if (rollGrid != null) {
            val lb = rollGrid.labelBox
            labelBox(lb.x, lb.y, lb.w, lb.h, pick(o.rollLabel, "omr.roll"))
            digitGrid(rollGrid, { digitLabels }, { it % 2 == 0 })
        }

        // ---- set code: blank, the candidate fills the bubble of the set (only the middle column has bubbles)
        var setBottom = HEAD_BOTTOM
        val setGrid = OmrGeometry.setGrid(o, setCodes)
        if (setGrid != null) {
            val lb = setGrid.labelBox
            labelBox(lb.x, lb.y, lb.w, lb.h, pick(o.setLabel, "omr.set"))
            setBottom = digitGrid(setGrid, { c -> if (c == 1) setLabels else emptyList() }, { it == 1 })
        }

        // ---- marks box (the examiner writes the marks), only when there is room under the set code
        if (o.showMarks) {
            val my = maxOf(92.77f, setBottom + 4f)
            val mh = 122.87f - my
            if (mh >= 16f) {
                rect(148.7f, my, 16.12f, mh)
                line(159.12f, my, 159.12f, my + mh)
                text.drawDown(pick(o.marksLabel, "omr.marks"), 159.12f + 2.85f, my + mh / 2f, 8f, OMR_BLACK, maxW = mh - 2f)
            }
        }

        // ---- invigilator box
        if (o.showInvigilator) {
            val h = 122.87f - HEAD_TOP
            rect(171.68f, HEAD_TOP, 19.44f, h)
            line(185.4f, HEAD_TOP, 185.4f, HEAD_TOP + h)
            text.drawDown(pick(o.invigilatorLabel, "omr.invig"), 185.4f + 2.85f, HEAD_TOP + h / 2f, 8f, OMR_BLACK, maxW = h - 3f)
        }

        // ---- registration number
        val regGrid = OmrGeometry.regGrid(o)
        if (regGrid != null) {
            val lb = regGrid.labelBox
            labelBox(lb.x, lb.y, lb.w, lb.h, pick(o.regLabel, "omr.reg"))
            digitGrid(regGrid, { digitLabels }, { it % 2 == 0 })
        }

        // ---- subject code: the first digit only goes 0..3 on a three-digit code (its frame ends under the 3)
        val subjectGrid = OmrGeometry.subjectGrid(o)
        if (subjectGrid != null) {
            val lb = subjectGrid.labelBox
            labelBox(lb.x, lb.y, lb.w, lb.h, pick(o.subjectLabel, "omr.subject"))
            digitGrid(subjectGrid, { c -> digitLabels.take(subjectGrid.labelCounts[c]) }, { it % 2 == 0 })
        }

        // ---- note + instructions (both centred on x 143.3 like the reference)
        if (o.showNote) {
            text.draw(pick(o.note, "omr.note"), 143.3f, 208.85f, 10f, OMR_INK, maxW = 63.5f)
            line(111.5f, 212.05f, 175.0f, 212.05f, FRAME_SW)
        }
        if (o.showRules) {
            val ix = 102.5f
            val iw = 88.6f
            fillRect(132.7f, 214.5f, 21.6f, 6.7f, OMR_INK)
            text.draw(pick(o.rulesTitle, "omr.rules"), 143.5f, 217.85f, 11f, Color.White, maxW = 19f)
            val y0 = 226.35f
            val pitch = 5.87f
            rules.forEachIndexed { i, r ->
                val slot = if (i == 0) 0 else i + 1 // the filling example sits under rule 1
                text.draw("${formatNumber(i + 1, bn)}. $r", ix, y0 + slot * pitch, 6.8f, OMR_BLACK, align = Paint.Align.LEFT, maxW = iw)
            }
            // how to fill: one correct and four wrong marks
            val ey = y0 + pitch
            text.draw(t("omr.correct"), 106.7f, ey, 6.8f, OMR_BLACK, bold = true, align = Paint.Align.LEFT, maxW = 15.5f)
            drawCircle(OMR_BLACK, mm(1.8f), Offset(mm(124.8f), mm(ey)))
            text.draw(t("omr.wrong"), 133f, ey, 6.8f, OMR_BLACK, bold = true, align = Paint.Align.LEFT, maxW = 13.5f)
            val wx = listOf(148.8f, 155.7f, 162.6f, 169.4f)
            wx.forEachIndexed { i, x ->
                drawCircle(OMR_INK, mm(1.8f), Offset(mm(x), mm(ey)), style = Stroke(mm(RING_SW)))
                text.draw(optionLabels[i], x, ey, 6.5f, OMR_INK, maxW = 2.6f)
            }
            line(wx[0] - 1.0f, ey + 0.9f, wx[0] + 1.0f, ey - 0.9f, 0.5f, OMR_INK) // slash across the letter
            drawCircle(OMR_BLACK, mm(0.9f), Offset(mm(wx[1]), mm(ey))) // small blob
            line(wx[2] - 1.0f, ey + 0.1f, wx[2] - 0.3f, ey + 1.0f, 0.4f, OMR_INK) // tick
            line(wx[2] - 0.3f, ey + 1.0f, wx[2] + 1.2f, ey - 1.1f, 0.4f, OMR_INK)
            line(wx[3] - 1.2f, ey - 1.2f, wx[3] + 1.2f, ey + 1.2f, 0.45f, OMR_BLACK) // cross
            line(wx[3] - 1.2f, ey + 1.2f, wx[3] + 1.2f, ey - 1.2f, 0.45f, OMR_BLACK)
        }

        // ---- candidate signature (off by default: the reference sheet has none; custom mode can switch it on)
        if (o.showSignature) {
            line(120f, 268.5f, 178f, 268.5f, 0.25f, OMR_BLACK)
            text.draw(pick(o.signatureLabel, "omr.sigStudent"), 149f, 272f, 7f, OMR_BLACK, maxW = 56f)
        }
    }
}
