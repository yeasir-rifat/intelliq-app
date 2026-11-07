package com.intelliq.rifatplex.data

/*
 * 200-answer sheet (geometry VERSION 2): 5 blocks of 40 answers (50 and 100 answer sheets: see OmrSheetKind), header with exam-code / centre / roll / set grids, name,
 * district and signature boxes (same arrangement as the common 200-question BCS style sheet, no third-party branding).
 *
 * Step 4.1 - the geometry of the OMR sheet in ONE place.
 *
 * Until now the positions of the bubbles, scanner marks and digit grids lived inside the drawing code of
 * `ui/OmrSheet.kt`. The scanner (step 4.4 / 4.5) must read the printed sheet back at exactly the same places, so both
 * the printer (OmrSheet) and the scanner (OmrReader) now take their numbers from here. Nothing in this file draws
 * anything or touches Compose / Android, so it is plain Kotlin and can be unit-tested on the JVM.
 *
 * Units: millimetres from the top-left corner of the A4 page (210 x 297 mm), x to the right, y downwards.
 *
 * Every value was taken from the VECTOR data of the reference sheet (Omr-Sheet-For-SSC, 50 answers, A4; Illustrator PDF),
 * not from a raster scan: the centres of all 388 bubbles (200 answers, roll 6x10, set 4, registration 10x10, subject
 * 4+10+10), the four corner squares and the 52 timing dashes agree with it to better than 0.01 mm (step 4.1b check).
 * DO NOT change a number here without also bumping OmrGeometry.VERSION, because old printed sheets would stop matching.
 */

/** A point on the sheet, in mm. */
internal data class PointMm(val x: Float, val y: Float)

/** A rectangle on the sheet, in mm ([x], [y] = top-left corner). */
internal data class RectMm(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * A grid of bubble columns (roll number, set code, registration number, subject code): one column per entry of
 * [widths]; column c holds [labelCounts]`[c]` bubbles, top to bottom, at the y offsets [dys] (from the top [gy] of the
 * grid frame). With [stepped] every column's frame ends under its own last bubble, otherwise all columns are as tall as
 * the tallest one. [labelBox] is the titled box above the grid.
 */
internal class DigitGridSpec(
    val gx: Float,
    val gy: Float,
    val widths: List<Float>,
    val labelCounts: List<Int>,
    val dys: FloatArray,
    val tail: Float,
    val stepped: Boolean,
    val labelBox: RectMm,
) {
    val cols: Int = widths.size

    /** Left edge of every column, relative to [gx]; one extra entry for the right edge of the last column. */
    val xs: FloatArray = FloatArray(cols + 1).also { a -> for (c in 0 until cols) a[c + 1] = a[c] + widths[c] }

    private val rowsMax: Int = labelCounts.maxOrNull() ?: 0

    /** Height of the frame of column [c], measured from [gy]. */
    fun colHeight(c: Int): Float =
        dys[((if (stepped) labelCounts[c] else rowsMax) - 1).coerceIn(0, dys.size - 1)] + tail

    /** Centre x of the bubbles of column [c]. */
    fun bubbleX(c: Int): Float = gx + xs[c] + widths[c] / 2f

    /** Centre y of bubble number [i] (0 = top) of any column. */
    fun bubbleY(i: Int): Float = gy + dys[i.coerceAtMost(dys.size - 1)]

    /** Centre of bubble [i] of column [c]. For a digit grid, bubble i is the digit i. */
    fun bubble(c: Int, i: Int): PointMm = PointMm(bubbleX(c), bubbleY(i))

    /** Bottom y of the frame (on the page, not relative). */
    val bottom: Float get() = gy + (0 until cols).maxOf { colHeight(it) }
}

/**
 * The three sheet sizes the user can choose (number of answer rows on one A4 sheet). Everything except the answer table
 * is identical (corner squares, timing dashes, QR box, header grids), so the aligner needs no change. [geomVersion] is
 * what the QR code carries, so the scanner knows the layout of a sheet from its code: 2 = 200 (the original v2 sheet,
 * unchanged), 3 = 100, 4 = 50.
 */
internal enum class OmrSheetKind(val rows: Int, val blocks: Int, val geomVersion: Int) {
    K50(50, 2, 4),
    K100(100, 4, 3),
    K200(200, 5, 2);

    val rowsPerBlock: Int get() = rows / blocks

    companion object {
        val DEFAULT = K200

        /** The kind with [rows] answer rows, [DEFAULT] for any other number (old saved settings, bad input). */
        fun ofRows(rows: Int): OmrSheetKind = values().firstOrNull { it.rows == rows } ?: DEFAULT

        /** The kind a QR code's geometry version stands for, null when the version is unknown. */
        fun ofVersion(v: Int): OmrSheetKind? = values().firstOrNull { it.geomVersion == v }

        /**
         * The smallest sheet that holds [mcqCount] MCQs on one page (50, then 100, then 200); more than 200 give [K200] (several
         * sheets). Null for no MCQ at all. Only a hint for the user: nothing here changes the chosen size.
         */
        fun suggest(mcqCount: Int): OmrSheetKind? =
            if (mcqCount <= 0) null else values().sortedBy { it.rows }.firstOrNull { it.rows >= mcqCount } ?: K200
    }
}

internal object OmrGeometry {
    /** Bump this when ANY number below changes. It is printed into the sheet's QR code (step 4.2). 1 = old 50-answer sheet. */
    const val VERSION = 2

    const val PAGE_W_MM = 210f
    const val PAGE_H_MM = 297f

    /** Answers per sheet: 5 blocks of 40. More MCQs continue on the next sheet. */
    const val ROWS_PER_PAGE = 200
    const val ROWS_PER_BLOCK = 40
    const val BLOCKS = 5
    const val OPTIONS = 4

    /** Largest number of sets the set-code column can show. */
    const val MAX_SETS = 10

    // ---- bubbles (a printed ring: the centre line has radius RING_R, the ink is RING_STROKE thick)
    const val RING_R = 1.5f
    const val RING_STROKE = 0.17f
    /** Ring of the small digit-grid bubbles (roll, set, ...). */
    const val GRID_RING_R = 1.2f
    /** Inside edge of the answer ring: the scanner only measures inside this circle (use a smaller window, e.g. 0.75 x). */
    const val BUBBLE_INNER_R = RING_R - RING_STROKE / 2f
    const val GRID_INNER_R = GRID_RING_R - RING_STROKE / 2f
    const val RING_PITCH = 6.0f // distance between two bubbles of a row

    // ---- answer table
    const val HEAD_TOP = 100.0f // top of the answer table
    const val HEAD_BOTTOM = 105.0f // bottom of the table header ("Q.No | Answer")
    const val ROW_PITCH = 4.36f
    const val BLOCK_W = 34.4f
    const val QNUM_W = 7.0f // question-number column
    const val HALF_BAND = ROW_PITCH / 2f
    const val TABLE_BOTTOM = HEAD_BOTTOM + ROWS_PER_BLOCK * ROW_PITCH // 279.4

    /** Left edge of the five answer blocks. */
    val BLOCK_X = FloatArray(BLOCKS) { 15.0f + it * 36.4f }

    /** Centre x of the first (option 0) bubble of each block. */
    val FIRST_BUBBLE_X = FloatArray(BLOCKS) { BLOCK_X[it] + QNUM_W + 1.7f + RING_PITCH / 2f }

    /** Centre y of the bubbles of answers 1..40 of a block. */
    val ANS_Y = FloatArray(ROWS_PER_BLOCK) { HEAD_BOTTOM + ROW_PITCH * (it + 0.5f) }

    // ---- digit grids
    const val GRID_LABEL_TOP = 29.0f // top of the titled boxes
    const val GRID_TOP = 34.5f // bottom of the label boxes = top of the write-in boxes
    const val GRID_BOX_ROW = 4.6f // row of write-in boxes on top of a digit grid

    /** Bubble centres of every digit grid, measured from the top of the grid frame. */
    val GRID_DY = FloatArray(10) { 6.9f + it * 3.55f }
    const val GRID_TAIL = 2.6f

    // ---- scanner marks
    const val MARK_SIZE = 4.8f // the four corner squares
    const val MARK_X_LEFT = 8.0f
    const val MARK_X_RIGHT = 197.2f
    const val MARK_Y_TOP = 88.0f
    const val MARK_Y_BOTTOM = 283.4f
    /** Distance between the mark centres, horizontally and vertically. */
    const val MARKS_SPAN_X = MARK_X_RIGHT - MARK_X_LEFT
    const val MARKS_SPAN_Y = MARK_Y_BOTTOM - MARK_Y_TOP
    /** Height / width of the rectangle of mark centres (1.03): the scanner prefers the corner quad closest to it. */
    const val MARKS_ASPECT = MARKS_SPAN_Y / MARKS_SPAN_X

    const val TIMING_COUNT = 40
    const val TIMING_X = MARK_X_LEFT
    const val TIMING_Y0 = 95.0f
    const val TIMING_PITCH = 4.5f
    const val TIMING_W = MARK_SIZE
    const val TIMING_H = 1.114f

    // ---- QR code (step 4.2): right of the lower "do not mark" box, above the answer table

    /** The QR box including its quiet zone (white margin). */
    const val QR_BOX_MM = 18f
    const val QR_BOX_X = 146f
    const val QR_BOX_Y = 77f

    /** White margin inside the box on every side; the code itself is [QR_CODE_MM] square. */
    const val QR_QUIET_MM = 2f
    const val QR_CODE_MM = QR_BOX_MM - 2f * QR_QUIET_MM

    /** The sheet title is centred on x 105 and at most this wide. */
    const val TITLE_MAX_W = 130f

    /** Where the scanner looks for the QR code first (and where the sheet prints it). */
    fun qrBox(): RectMm = RectMm(QR_BOX_X, QR_BOX_Y, QR_BOX_MM, QR_BOX_MM)

    /**
     * Position of the sheet's set among the named sets, 0..9, or null (no set, or not among the first ten). Follows the
     * same filtering as the set-code column of the sheet, so it is the number of the bubble the set owns.
     */
    fun setIndexOf(setCode: String?, setCodes: List<String>): Int? {
        val code = setCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return setCodes.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_SETS).indexOf(code).takeIf { it >= 0 }
    }

    /** The 200-answer sheet has no marks box (kept for the result PDF, which then draws nothing). */
    @Suppress("UNUSED_PARAMETER")
    fun marksBox(omr: OmrSettings, setCodes: List<String>): RectMm? = null

    /** The four corner squares, in the order top-left, top-right, bottom-left, bottom-right. */
    fun cornerMarks(): List<RectMm> = listOf(
        RectMm(MARK_X_LEFT, MARK_Y_TOP, MARK_SIZE, MARK_SIZE),
        RectMm(MARK_X_RIGHT, MARK_Y_TOP, MARK_SIZE, MARK_SIZE),
        RectMm(MARK_X_LEFT, MARK_Y_BOTTOM, MARK_SIZE, MARK_SIZE),
        RectMm(MARK_X_RIGHT, MARK_Y_BOTTOM, MARK_SIZE, MARK_SIZE),
    )

    /** The timing dashes down the left edge between the two left squares, top to bottom. */
    fun timingDashes(): List<RectMm> =
        (0 until TIMING_COUNT).map { i -> RectMm(TIMING_X, TIMING_Y0 + i * TIMING_PITCH, TIMING_W, TIMING_H) }

    // ---- answer bubbles

    /** Centre of option [option] (0 = first) of row [row] (0..39) in answer block [block] (0..4). */
    fun answerBubble(block: Int, row: Int, option: Int): PointMm =
        PointMm(FIRST_BUBBLE_X[block] + option * RING_PITCH, ANS_Y[row])

    /** Same, counting the answers of a sheet from 0 to 199 (block after block, 40 each). */
    fun answerBubbleOfSlot(slot: Int, option: Int): PointMm =
        answerBubble(slot / ROWS_PER_BLOCK, slot % ROWS_PER_BLOCK, option)

    // ---- sheet sizes (50 / 100 / 200 answers): only the answer table changes, the table keeps its height

    /** Height of one answer block's rows (the same for every sheet size). */
    const val TABLE_H = ROWS_PER_BLOCK * ROW_PITCH

    /** Gap between two answer blocks. */
    const val BLOCK_GAP = 2.0f

    /** Distance between two rows: the 200 sheet keeps its measured pitch, the others share the same table height. */
    fun rowPitch(kind: OmrSheetKind): Float = if (kind == OmrSheetKind.K200) ROW_PITCH else TABLE_H / kind.rowsPerBlock

    /** Left edge of answer block [block]: the 200 sheet keeps its numbers, fewer blocks are centred on the page. */
    fun blockX(kind: OmrSheetKind, block: Int): Float {
        if (kind == OmrSheetKind.K200) return BLOCK_X[block]
        val span = kind.blocks * BLOCK_W + (kind.blocks - 1) * BLOCK_GAP
        return PAGE_W_MM / 2f - span / 2f + block * (BLOCK_W + BLOCK_GAP)
    }

    /** Centre x of option 0 of answer block [block]. */
    fun firstBubbleX(kind: OmrSheetKind, block: Int): Float =
        if (kind == OmrSheetKind.K200) FIRST_BUBBLE_X[block] else blockX(kind, block) + QNUM_W + 1.7f + RING_PITCH / 2f

    /** Centre y of the bubbles of row [row] (0-based) of any block. */
    fun ansY(kind: OmrSheetKind, row: Int): Float =
        if (kind == OmrSheetKind.K200) ANS_Y[row] else HEAD_BOTTOM + rowPitch(kind) * (row + 0.5f)

    fun answerBubble(kind: OmrSheetKind, block: Int, row: Int, option: Int): PointMm =
        PointMm(firstBubbleX(kind, block) + option * RING_PITCH, ansY(kind, row))

    /** Centre of option [option] of the answer in row [slot] (0 until kind.rows, block after block). */
    fun answerBubbleOfSlot(kind: OmrSheetKind, slot: Int, option: Int): PointMm =
        answerBubble(kind, slot / kind.rowsPerBlock, slot % kind.rowsPerBlock, option)

    // ---- digit grids (each is null when the block is switched off in the OMR settings)

    private fun grid(gx: Float, colW: Float, n: Int, labelCount: Int, labelW: Float = n * colW): DigitGridSpec =
        DigitGridSpec(
            gx = gx, gy = GRID_TOP,
            widths = List(n) { colW }, labelCounts = List(n) { labelCount },
            dys = GRID_DY, tail = GRID_TAIL, stepped = false,
            labelBox = RectMm(gx, GRID_LABEL_TOP, labelW, GRID_TOP - GRID_LABEL_TOP),
        )

    /** Exam / subject code: 1..2 digits (the first small block on the left). */
    fun subjectGrid(omr: OmrSettings): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showSubject) return null
        return grid(15.0f, 7.0f, o.subjectDigits.coerceIn(1, 2), 10)
    }

    /** Centre code: 1..2 digits (the second small block). */
    fun regGrid(omr: OmrSettings): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showReg) return null
        return grid(32.0f, 7.0f, o.regDigits.coerceIn(1, 2), 10)
    }

    /** Roll / registration number: 1..8 digits, every digit column has the bubbles 0..9. */
    fun rollGrid(omr: OmrSettings): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showRoll) return null
        return grid(49.0f, 6.2f, o.rollDigits.coerceIn(1, 8), 10)
    }

    /** How many bubbles the set-code column has: one per named set (at most [MAX_SETS]), 4 when no set is named. */
    fun setBubbleCount(setCodes: List<String>): Int {
        val n = setCodes.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_SETS).size
        return if (n == 0) 4 else n
    }

    /** Set code: ONE column with one bubble per set (column 0). */
    fun setGrid(omr: OmrSettings, setCodes: List<String>): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showSet) return null
        return grid(101.5f, 8.0f, 1, setBubbleCount(setCodes))
    }

    /** Right-hand boxes of the header (name, district, candidate signature, invigilator), for the sheet drawing. */
    val NAME_BOX = RectMm(113.0f, 29.0f, 70.0f, 22.0f)
    val DISTRICT_BOX = RectMm(113.0f, 53.0f, 32.0f, 22.95f)
    val SIGN_BOX = RectMm(147.0f, 53.0f, 36.0f, 22.95f)
    val INVIG_BOX = RectMm(185.0f, 29.0f, 10.0f, 46.95f)
}
