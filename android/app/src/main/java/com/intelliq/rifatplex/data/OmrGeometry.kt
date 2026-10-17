package com.intelliq.rifatplex.data

/*
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

internal object OmrGeometry {
    /** Bump this when ANY number below changes. It is printed into the sheet's QR code (step 4.2). */
    const val VERSION = 1

    const val PAGE_W_MM = 210f
    const val PAGE_H_MM = 297f

    /** Answers per sheet: 2 blocks of 25. More MCQs continue on the next sheet. */
    const val ROWS_PER_PAGE = 50
    const val ROWS_PER_BLOCK = 25
    const val BLOCKS = 2
    const val OPTIONS = 4

    /** Largest number of sets the set-code column can show. */
    const val MAX_SETS = 10

    // ---- bubbles (a printed ring: the centre line has radius RING_R, the ink is RING_STROKE thick)
    const val RING_R = 1.615f
    const val RING_STROKE = 0.17f
    /** Inside edge of the ring: the scanner only measures inside this circle (use a smaller window, e.g. 0.75 x). */
    const val BUBBLE_INNER_R = RING_R - RING_STROKE / 2f // 1.53 mm
    const val RING_PITCH = 6.096f // distance between two bubbles of a row

    // ---- answer blocks
    const val HEAD_TOP = 50.533f // top of the answer blocks and of the label boxes
    const val HEAD_BOTTOM = 58.022f // bottom of the answer-block header / label boxes
    const val BLOCK_W = 33.279f
    const val QNUM_W = 5.829f // question-number column
    const val HALF_BAND = 2.16f // half height of the tinted band behind one answer

    /** Left edge of the two answer blocks. */
    val BLOCK_X = floatArrayOf(23.343f, 62.953f)

    /** Centre x of the first (option 0) bubble of each block. */
    val FIRST_BUBBLE_X = floatArrayOf(33.753f, 73.363f)

    /** Centre y of the bubbles of answers 1..25 (the artwork is laid out by hand, so the spacing is not perfectly even). */
    val ANS_Y = floatArrayOf(
        60.183f, 68.824f, 77.466f, 86.107f, 94.749f, 103.390f, 112.032f, 120.673f, 129.314f, 137.956f, 146.597f, 155.239f, 163.880f,
        172.522f, 181.163f, 189.516f, 198.158f, 206.799f, 215.441f, 224.082f, 232.724f, 241.365f, 249.889f, 258.531f, 267.172f,
    )

    // ---- digit grids
    const val GRID_BOX_ROW = 4.878f // row of write-in boxes on top of a digit grid
    const val GRID_COL_W = 5.5116f // width of one digit column (roll, registration, subject)

    /** Bubble centres of every digit grid, measured from the top of the grid frame (the same for roll, set, registration and subject). */
    val GRID_DY = floatArrayOf(7.907f, 13.787f, 19.613f, 25.626f, 31.536f, 37.437f, 43.284f, 49.063f, 55.198f, 60.648f)

    const val GRID_TAIL_A = 4.42f // frame bottom below the last bubble (roll number)
    const val GRID_TAIL_B = 4.23f // registration number, subject code
    const val GRID_TAIL_SET = 3.93f

    // ---- scanner marks
    const val MARK_SIZE = 4.801f // the four corner squares
    const val TIMING_COUNT = 52
    const val TIMING_X = 13.471f
    const val TIMING_Y0 = 37.066f
    const val TIMING_PITCH = 4.5064f
    const val TIMING_W = MARK_SIZE
    const val TIMING_H = 1.114f

    // ---- QR code (step 4.2): top right, under the corner square, above the invigilator box

    /** The QR box including its quiet zone (white margin). */
    const val QR_BOX_MM = 18f
    const val QR_BOX_X = 172f
    const val QR_BOX_Y = 29.5f

    /** White margin inside the box on every side; the code itself is [QR_CODE_MM] square. */
    const val QR_QUIET_MM = 2f
    const val QR_CODE_MM = QR_BOX_MM - 2f * QR_QUIET_MM

    /** The sheet title is centred on x 105 and at most this wide, so it never reaches the QR box. */
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

    /** The four corner squares, in the order top-left, top-right, bottom-left, bottom-right. */
    fun cornerMarks(): List<RectMm> = listOf(
        RectMm(13.471f, 14.659f, MARK_SIZE, MARK_SIZE),
        RectMm(191.101f, 14.659f, MARK_SIZE, MARK_SIZE),
        RectMm(13.471f, 277.540f, MARK_SIZE, MARK_SIZE),
        RectMm(191.101f, 277.540f, MARK_SIZE, MARK_SIZE),
    )

    /** The 52 timing dashes down the left edge, top to bottom. */
    fun timingDashes(): List<RectMm> =
        (0 until TIMING_COUNT).map { i -> RectMm(TIMING_X, TIMING_Y0 + i * TIMING_PITCH, TIMING_W, TIMING_H) }

    // ---- answer bubbles

    /** Centre of option [option] (0 = first) of row [row] (0..24) in answer block [block] (0 or 1). */
    fun answerBubble(block: Int, row: Int, option: Int): PointMm =
        PointMm(FIRST_BUBBLE_X[block] + option * RING_PITCH, ANS_Y[row])

    /** Same, counting the answers of a sheet from 0 to 49 (0..24 = left block, 25..49 = right block). */
    fun answerBubbleOfSlot(slot: Int, option: Int): PointMm =
        answerBubble(slot / ROWS_PER_BLOCK, slot % ROWS_PER_BLOCK, option)

    // ---- digit grids (each is null when the block is switched off in the OMR settings)

    /** Roll number: 1..8 digits, every digit column has the bubbles 0..9. */
    fun rollGrid(omr: OmrSettings): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showRoll) return null
        val n = o.rollDigits.coerceIn(1, 8)
        val colW = minOf(GRID_COL_W, 33.07f / n)
        return DigitGridSpec(
            gx = 105.583f, gy = HEAD_BOTTOM,
            widths = List(n) { colW }, labelCounts = List(n) { 10 },
            dys = GRID_DY, tail = GRID_TAIL_A, stepped = false,
            labelBox = RectMm(102.678f, HEAD_TOP, n * colW + 5.8f, HEAD_BOTTOM - HEAD_TOP),
        )
    }

    /** How many bubbles the set-code column has: one per named set (at most [MAX_SETS]), 4 when no set is named. */
    fun setBubbleCount(setCodes: List<String>): Int {
        val n = setCodes.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_SETS).size
        return if (n == 0) 4 else n
    }

    /** Set code: three columns, only the middle one has bubbles (one per set). */
    fun setGrid(omr: OmrSettings, setCodes: List<String>): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showSet) return null
        return DigitGridSpec(
            gx = 148.248f, gy = HEAD_BOTTOM,
            widths = listOf(5.71f, 5.09f, 5.7f), labelCounts = listOf(0, setBubbleCount(setCodes), 0),
            dys = GRID_DY, tail = GRID_TAIL_SET, stepped = false,
            labelBox = RectMm(145.377f, HEAD_TOP, 22.239f, HEAD_BOTTOM - HEAD_TOP),
        )
    }

    /** Registration number: 1..12 digits. */
    fun regGrid(omr: OmrSettings): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showReg) return null
        val n = o.regDigits.coerceIn(1, 12)
        val colW = minOf(6.15f, 55.116f / n)
        return DigitGridSpec(
            gx = 105.559f, gy = 137.107f,
            widths = List(n) { colW }, labelCounts = List(n) { 10 },
            dys = GRID_DY, tail = GRID_TAIL_B, stepped = false,
            labelBox = RectMm(102.678f, 129.56f, n * colW + 5.762f, 7.489f),
        )
    }

    /** Subject code: 1..4 digits; the first digit only goes 0..3 on a code of three or more digits (its frame ends under the 3). */
    fun subjectGrid(omr: OmrSettings): DigitGridSpec? {
        val o = omr.effective()
        if (!o.showSubject) return null
        val n = o.subjectDigits.coerceIn(1, 4)
        val colW = GRID_COL_W
        val gx = 188.124f - n * colW // the grid ends at the same place whatever the number of digits
        val boxW = n * colW + 5.7f
        return DigitGridSpec(
            gx = gx, gy = 137.003f,
            widths = List(n) { colW }, labelCounts = List(n) { c -> if (c == 0 && n >= 3) 4 else 10 },
            dys = GRID_DY, tail = GRID_TAIL_B, stepped = true,
            labelBox = RectMm(gx - 2.85f, 129.508f, boxW, 7.489f),
        )
    }
}
