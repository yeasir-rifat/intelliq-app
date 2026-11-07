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
import com.intelliq.rifatplex.data.OmrSheetKind
import com.intelliq.rifatplex.data.QrCode
import com.intelliq.rifatplex.data.QrPayload
import com.intelliq.rifatplex.data.RectMm
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.SheetStyle
import com.intelliq.rifatplex.ui.theme.Kalpurush
import com.intelliq.rifatplex.ui.theme.TimesNewRoman

/*
 * 200-answer OMR sheet (A4, geometry version 2): header with two title lines, exam-code / centre / roll / set grids,
 * name / district / signature / invigilator boxes, QR code, then FIVE blocks of 40 answers. No third-party branding.
 * (The comment below describes the old 50-answer sheet this file used to draw; the numbers now come from OmrGeometry.)
 *
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

/** Answers on the biggest sheet (200: 5 blocks of 40). The user can also choose 100 (4 x 25) or 50 (2 x 25), see OmrSheetKind. */
internal const val OMR_ROWS_PER_PAGE = OmrGeometry.ROWS_PER_PAGE
private const val OMR_OPTIONS = OmrGeometry.OPTIONS
private const val OMR_PAGE_W_MM = OmrGeometry.PAGE_W_MM

private val OMR_INK = Color(0xFFEC008C) // outlines and letters (the magenta of the reference sheet)
private val OMR_BLACK = Color(0xFF231F20) // scanner marks and text

private const val PT_MM = 0.35278f // 1 pt in mm

// ---- drawing-only values (mm)
private const val FRAME_SW = 0.13f // hairline of every frame
private const val RING_SW = OmrGeometry.RING_STROKE // stroke of a bubble
private const val TINT = 0.08f

/** Opacity of the number and bubbles of the extra rows after the last MCQ (see [OmrPage] liveRows): pale, so nobody fills them. */
private const val UNUSED_ALPHA = 0.28f

// ---- measured geometry: since step 4.1 the numbers live in data/OmrGeometry.kt (the scanner reads the same ones)
private const val RING_R = OmrGeometry.RING_R
private const val HEAD_TOP = OmrGeometry.HEAD_TOP
private const val HEAD_BOTTOM = OmrGeometry.HEAD_BOTTOM
private const val HALF_BAND = OmrGeometry.HALF_BAND
private const val BLOCK_W = OmrGeometry.BLOCK_W
private const val QNUM_W = OmrGeometry.QNUM_W
private const val GRID_BOX_ROW = OmrGeometry.GRID_BOX_ROW

private class OmrFaces(val bn: Typeface, val bnBold: Typeface, val en: Typeface, val enBold: Typeface)

@Composable
private fun rememberOmrFaces(): OmrFaces {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver) {
        fun face(f: FontFamily, w: FontWeight): Typeface =
            resolver.resolve(f, w, FontStyle.Normal, FontSynthesis.All).value as? Typeface ?: Typeface.DEFAULT
        OmrFaces(
            face(Kalpurush, FontWeight.Normal), face(Kalpurush, FontWeight.Bold), // Bangla: fixed (SheetStyle)
            face(TimesNewRoman, FontWeight.Normal), face(TimesNewRoman, FontWeight.Bold),
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
 * One OMR sheet. [numbers] are the question numbers of this sheet, 50, 100 or 200 as chosen in the OMR setting (rows past the last MCQ are numbered on, so the
 * sheet is always full). Only the first [liveRows] rows (counting block after block) hold a real MCQ; the number and the bubbles of the
 * later rows are drawn pale, so a student does not fill them (the scanner ignores them anyway). [setCodes] are the set names offered in the (blank) set-code column. [omr] is the OMR setting
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
    /** Rows with a real MCQ on this sheet; the default means every row is real. */
    liveRows: Int = Int.MAX_VALUE,
) {
    val bn = lang == Lang.BN
    val faces = rememberOmrFaces()
    val qrMatrix = remember(qr) { qr?.let { QrCode.encode(it.encode()) } }
    fun t(key: String) = trKey(key, lang)
    val o = omr.effective()
    val kind = OmrSheetKind.ofRows(o.sheetKind) // 50, 100 or 200 answers on this sheet
    fun pick(custom: String, key: String) = custom.trim().ifEmpty { t(key) }

    // ---- texts: automatic from the document + branding, custom ones win when filled in
    val brandName = if (brand.enabled && brand.showName) brand.name.trim() else ""
    val autoLine1 = brandName.ifEmpty { doc.title.trim() }
    val autoLine2 = (if (brandName.isNotEmpty()) listOf(doc.title.trim(), doc.subtitle.trim()) else listOf(doc.subtitle.trim()))
        .filter { it.isNotEmpty() }.joinToString(" \u2022 ")
    val line1 = o.boardName.trim().ifEmpty { autoLine1 }
    val line2 = o.examLine.trim().ifEmpty { autoLine2 }
    val sheetTitle = pick(o.sheetTitle, "omr.sheetName")
    val noMark = pick(o.banner1, "omr.banner1")
    val banner = pick(o.banner2, "omr.banner")
    val cap = pick(o.note, "omr.note")
    val optionLabels = if (bn) listOf("ক", "খ", "গ", "ঘ", "ঙ") else listOf("A", "B", "C", "D", "E")
    val setLabels = setCodes.map { it.trim() }.filter { it.isNotEmpty() }.take(10).ifEmpty { optionLabels.take(4) }
    val pageLabel = if (pageCount > 1) "${t("doc.page")} ${formatNumber(pageIndex + 1, bn)}/${formatNumber(pageCount, bn)}" else ""

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

        fun bubble(cx: Float, cy: Float, label: String, r: Float, pt: Float, alpha: Float = 1f) {
            drawCircle(OMR_INK.copy(alpha = alpha), mm(r), Offset(mm(cx), mm(cy)), style = Stroke(mm(RING_SW)))
            text.draw(label, cx, cy, pt, OMR_INK.copy(alpha = alpha), maxW = r * 1.6f)
        }

        /** Label box on top of a block: outline + black title. */
        fun labelBox(r: RectMm, label: String, pt: Float = 8f) {
            rect(r.x, r.y, r.w, r.h)
            text.draw(label, r.x + r.w / 2f, r.y + r.h / 2f, pt, OMR_BLACK, bold = true, maxW = r.w - 1.2f)
        }

        /** A digit grid: a row of write-in boxes on top and one bubble column per column of [spec]. */
        fun digitGrid(spec: DigitGridSpec, labelsOf: (Int) -> List<String>, tinted: (Int) -> Boolean) {
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
                bubble(p.x, p.y, lab, OmrGeometry.GRID_RING_R, 5.5f)
            }
        }
        val digitLabels = (0..9).map { it.toString() }

        // ---- scanner marks: four corner squares + timing dashes down the left edge between the two left squares
        OmrGeometry.cornerMarks().forEach { r -> fillRect(r.x, r.y, r.w, r.h, OMR_BLACK) }
        OmrGeometry.timingDashes().forEach { r -> fillRect(r.x, r.y, r.w, r.h, OMR_BLACK) }

        // ---- QR code: black modules only, in ONE path (no hairline gaps between the rows, vector in the PDF).
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

        // ---- header: two title lines, the sheet title as a pink badge on the right
        if (line1.isNotEmpty()) text.draw(line1, 105f, 9.5f, SheetStyle.HEADING_PT, OMR_BLACK, bold = true, maxW = 90f)
        if (line2.isNotEmpty()) text.draw(line2, 105f, 17.0f, SheetStyle.SUBHEADING_PT, OMR_BLACK, bold = true, maxW = 90f)
        fillRect(152f, 5f, 43f, 6.5f, OMR_INK)
        text.draw(sheetTitle, 173.5f, 8.25f, 7.5f, Color.White, bold = true, maxW = 41f)
        // sheet size ("৫০ প্রশ্ন" / "50 Questions") in the top-left corner: the title lines start at x 60, the grids at y 29, the scanner marks are far below
        if (o.showSizeTag) {
            text.draw(t("omr.sizeTag").replace("{n}", formatNumber(kind.rows, bn)), 15f, 8.25f, 7.5f, OMR_BLACK, bold = true, align = Paint.Align.LEFT, maxW = 38f)
        }
        if (o.showBanner1) {
            rect(80f, 20.5f, 50f, 6.5f, 0.25f)
            text.draw(noMark, 105f, 23.75f, 7f, OMR_BLACK, maxW = 48f)
        }

        // ---- grids: exam code, centre code, roll number, set code
        val subjectGrid = OmrGeometry.subjectGrid(o)
        if (subjectGrid != null) {
            labelBox(subjectGrid.labelBox, pick(o.subjectLabel, "omr.subject"))
            digitGrid(subjectGrid, { digitLabels }, { it % 2 == 0 })
        }
        val regGrid = OmrGeometry.regGrid(o)
        if (regGrid != null) {
            labelBox(regGrid.labelBox, pick(o.regLabel, "omr.reg"))
            digitGrid(regGrid, { digitLabels }, { it % 2 == 0 })
        }
        val rollGrid = OmrGeometry.rollGrid(o)
        if (rollGrid != null) {
            labelBox(rollGrid.labelBox, pick(o.rollLabel, "omr.roll"))
            digitGrid(rollGrid, { digitLabels }, { it % 2 == 0 })
        }
        val setGrid = OmrGeometry.setGrid(o, setCodes)
        if (setGrid != null) {
            labelBox(setGrid.labelBox, pick(o.setLabel, "omr.set"))
            digitGrid(setGrid, { setLabels }, { true })
        }

        // ---- name / district / signature / invigilator boxes
        fun writeBox(r: RectMm, label: String, hint: String, lineAt: Float) {
            rect(r.x, r.y, r.w, r.h)
            line(r.x + 6f, r.y + r.h * lineAt, r.x + r.w - 6f, r.y + r.h * lineAt, 0.2f, OMR_INK)
            text.draw(label, r.x + r.w / 2f, r.y + r.h * (lineAt + 0.17f), 8f, OMR_BLACK, maxW = r.w - 3f)
            if (hint.isNotEmpty()) text.draw(hint, r.x + r.w / 2f, r.y + r.h * (lineAt + 0.35f), 6.5f, OMR_BLACK, maxW = r.w - 3f)
        }
        writeBox(OmrGeometry.NAME_BOX, t("omr.name"), t("omr.nameHint"), 0.5f)
        writeBox(OmrGeometry.DISTRICT_BOX, t("omr.district"), t("omr.districtHint"), 0.45f)
        if (o.showSignature) writeBox(OmrGeometry.SIGN_BOX, pick(o.signatureLabel, "omr.sigStudent"), "", 0.55f)
        if (o.showInvigilator) {
            val r = OmrGeometry.INVIG_BOX
            rect(r.x, r.y, r.w, r.h)
            text.drawDown(pick(o.invigilatorLabel, "omr.invig"), r.x + r.w / 2f, r.y + r.h / 2f, 7f, OMR_BLACK, maxW = r.h - 3f)
        }

        // ---- lower "do not mark" box + the two instruction lines (left of the QR code)
        if (o.showBanner1) {
            rect(70f, 78f, 50f, 7f, 0.25f)
            text.draw(noMark, 95f, 81.5f, 7f, OMR_BLACK, maxW = 48f)
        }
        if (o.showBanner2) text.draw("($banner)", 95f, 89.2f, 6.8f, OMR_BLACK, maxW = 92f)
        if (o.showNote) text.draw(cap, 95f, 93.4f, 6.8f, OMR_BLACK, maxW = 92f)

        // ---- answer blocks: 200 = five blocks of 40 rows, 100 = four blocks of 25, 50 = two blocks of 25 (same table height)
        val qHead = pick(o.qHeader, "omr.qNo")
        val aHead = pick(o.ansHeader, "omr.answer")
        val rowsPerBlock = kind.rowsPerBlock
        val pitch = OmrGeometry.rowPitch(kind)
        for (block in 0 until kind.blocks) {
            val nums = numbers.drop(block * rowsPerBlock).take(rowsPerBlock)
            val x0 = OmrGeometry.blockX(kind, block)
            val bottom = OmrGeometry.TABLE_BOTTOM
            if (o.shading) for (i in 0 until rowsPerBlock) if (i % 2 == 1) {
                fillRect(x0, HEAD_BOTTOM + pitch * i, BLOCK_W, pitch, OMR_INK.copy(alpha = TINT))
            }
            for (i in 1 until rowsPerBlock) line(x0, HEAD_BOTTOM + pitch * i, x0 + BLOCK_W, HEAD_BOTTOM + pitch * i, 0.1f, OMR_INK.copy(alpha = 0.7f))
            rect(x0, HEAD_TOP, BLOCK_W, bottom - HEAD_TOP)
            line(x0, HEAD_BOTTOM, x0 + BLOCK_W, HEAD_BOTTOM)
            line(x0 + QNUM_W, HEAD_TOP, x0 + QNUM_W, bottom)
            val qLines = qHead.split('\n')
            qLines.forEachIndexed { li, s ->
                val dy = (li - (qLines.size - 1) / 2f) * 2.2f
                text.draw(s.trim(), x0 + QNUM_W / 2f, HEAD_TOP + (HEAD_BOTTOM - HEAD_TOP) / 2f + dy, if (qLines.size > 1) 4.5f else 6.5f, OMR_BLACK, bold = true, maxW = QNUM_W - 0.8f)
            }
            text.draw(aHead, x0 + QNUM_W + (BLOCK_W - QNUM_W) / 2f, HEAD_TOP + (HEAD_BOTTOM - HEAD_TOP) / 2f, 8f, OMR_BLACK, bold = true, maxW = BLOCK_W - QNUM_W - 2f)
            nums.forEachIndexed { i, n ->
                val cy = OmrGeometry.ansY(kind, i)
                val fade = if (block * rowsPerBlock + i < liveRows) 1f else UNUSED_ALPHA // extra row after the last MCQ
                text.draw(formatNumber(n, bn), x0 + QNUM_W / 2f, cy, 7f, OMR_BLACK.copy(alpha = fade), bold = true, maxW = QNUM_W - 0.8f)
                for (op in 0 until OMR_OPTIONS) {
                    val p = OmrGeometry.answerBubble(kind, block, i, op)
                    bubble(p.x, p.y, optionLabels[op], OmrGeometry.RING_R, 6f, fade)
                }
            }
        }

        if (pageLabel.isNotEmpty()) text.draw(pageLabel, 105f, 285.8f, 7f, OMR_INK)
    }
}
