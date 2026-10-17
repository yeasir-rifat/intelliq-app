package com.intelliq.rifatplex.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlin.math.floor
import kotlin.math.max

/*
 * Step 4.5 - read a photographed OMR sheet: the QR code, every bubble, the digit grids (pure Kotlin + ZXing, no Android).
 *
 * Input: the red-channel [GrayImage] and the [AlignResult.Ok] of step 4.4 (a map from sheet millimetres to photo pixels).
 * Nothing is warped: every bubble is measured at the pixel position the map gives.
 *
 * How a bubble is measured
 *  - window: a disc of radius windowFactor x BUBBLE_INNER_R (0.75 x 1.53 = 1.15 mm) around the centre. The printed ring and the
 *    printed letter are magenta, i.e. white in the red channel, so an empty bubble is paper-white and a pen mark is dark.
 *  - search: the disc is also tried at +-0.4 mm (0.2 mm steps) and the DARKEST position counts, so a small shift of the print or
 *    of the photo does not hide a mark.
 *  - paper: measured on the sheet itself, in the gaps between bubbles of the same row (median of 3 gaps per answer row; one
 *    smudge cannot spoil it). A shadow over one half of the sheet therefore does not matter.
 *  - ink: the median of the 4 black corner squares.
 *  - darkness = (1 - mean / paper) / (1 - ink / paper), clamped to 0..1. This is a multiplicative light model: a shadow scales
 *    paper and ink alike, so darkness stays the same. 0 = paper, 1 = as black as the printed squares.
 *
 * What a row is (thresholds in [OmrReadParams], defaults):
 *   FILLED    exactly one bubble >= 0.35, and the next one clearly weaker
 *   BLANK     all < 0.15
 *   MULTI     two or more >= 0.35
 *   UNCERTAIN the darkest is 0.15..0.35, or the second is >= 0.15 and more than 0.7 x the darkest (a teacher should look)
 */

/** Which sheet layout was printed (the grids depend on the OMR settings and on the number of named sets). */
internal class OmrLayout(
    val omr: OmrSettings = OmrSettings(),
    val setCodes: List<String> = emptyList(),
)

internal class OmrReadParams(
    val fillThreshold: Double = 0.35,
    val blankThreshold: Double = 0.15,
    /** second / darkest above this (and second >= blankThreshold) = unsure. */
    val uncertainRatio: Double = 0.7,
    val windowFactor: Double = 0.75,
    val searchMm: Double = 0.4,
    val searchStepMm: Double = 0.2,
    /** paper - ink below this many grey levels = the photo is too flat to read. */
    val minContrastLevels: Double = 50.0,
    /** pixels per mm of the upright crop that is handed to the QR reader. */
    val qrCropPxPerMm: Double = 10.0,
)

internal enum class MarkState { BLANK, FILLED, MULTI, UNCERTAIN }

/**
 * The bubbles of one question row (or one digit column) after measuring. [marked] = the options taken as marked: the one
 * filled bubble (FILLED), every bubble over the fill threshold (MULTI), the darkest one as a guess (UNCERTAIN), nothing (BLANK).
 */
internal class BubbleSet(val darkness: DoubleArray, val state: MarkState, val marked: Set<Int>) {
    val darkest: Int get() = darkness.indices.maxByOrNull { darkness[it] } ?: -1
}

/** A digit grid read column by column; a column that is not clearly filled is `?`. */
internal class DigitsRead(val text: String, val columns: List<BubbleSet>) {
    val complete: Boolean get() = text.isNotEmpty() && '?' !in text
    val blank: Boolean get() = columns.all { it.state == MarkState.BLANK }
}

internal sealed class QrStatus {
    class Found(val payload: QrPayload) : QrStatus()

    /** No readable code in the photo. */
    object None : QrStatus()

    /** An IntelliQ code of another format / sheet version (or damaged): the sheet was printed with other numbers. */
    class OtherVersion(val text: String) : QrStatus()

    /** A QR code that is not ours. */
    class Foreign(val text: String) : QrStatus()

    /**
     * The QR code of an ANSWER SHEET page ([KeyQrPayload], step 4.8): the photo shows the teacher's answer sheet, not a
     * student's OMR sheet. It cannot be marked; the teacher is told so instead of "other version of the sheet".
     */
    class KeyPage(val payload: KeyQrPayload) : QrStatus()
}

internal class OmrSheetRead(
    val qr: QrStatus,
    /** The 50 rows of the sheet, left block first (slot 0..49). */
    val rows: List<BubbleSet>,
    val roll: DigitsRead?,
    val reg: DigitsRead?,
    val subject: DigitsRead?,
    /** The set column; null when the sheet has none. */
    val setColumn: BubbleSet?,
    /** Position of the set the student filled (0..9), only when exactly one bubble is clearly filled. */
    val setFilled: Int?,
    /** Grey level of blank paper / of the black squares (median), NaN when not measurable. */
    val paperLevel: Double,
    val inkLevel: Double,
    /** Paper and ink are too close (or not measurable): nothing on this photo can be trusted. */
    val lowContrast: Boolean,
    /** Bubble windows that fell (partly) outside the photo. */
    val outOfImage: Int,
    val millis: Long,
) {
    val qrPayload: QrPayload? get() = (qr as? QrStatus.Found)?.payload
}

internal sealed class ScanOutcome {
    class Read(val read: OmrSheetRead, val align: AlignResult.Ok) : ScanOutcome()
    class NotAligned(val failed: AlignResult.Failed) : ScanOutcome()
}

internal object OmrReader {

    /** Aligns the photo (step 4.4) and reads it. */
    fun scan(
        img: GrayImage,
        layout: OmrLayout = OmrLayout(),
        params: OmrReadParams = OmrReadParams(),
        alignParams: OmrAlignParams = OmrAlignParams(),
    ): ScanOutcome = when (val a = OmrAlign.align(img, alignParams)) {
        is AlignResult.Ok -> ScanOutcome.Read(read(img, a, layout, params), a)
        is AlignResult.Failed -> ScanOutcome.NotAligned(a)
    }

    fun read(
        img: GrayImage,
        align: AlignResult.Ok,
        layout: OmrLayout = OmrLayout(),
        p: OmrReadParams = OmrReadParams(),
    ): OmrSheetRead {
        val t0 = System.nanoTime()
        val s = Sampler(img, align.toImage, p)

        val qr = readQr(img, align.toImage, p)

        // ink: the four black squares
        val inks = OmrGeometry.cornerMarks().map { m ->
            s.mean((m.x + m.w / 2f).toDouble(), (m.y + m.h / 2f).toDouble(), 1.5)
        }.filter { !it.isNaN() }
        val ink = median(inks)

        // answer rows: bubbles + local paper from the gaps of the same row
        val rowMeans = ArrayList<DoubleArray>(OmrGeometry.ROWS_PER_PAGE)
        val rowPaper = DoubleArray(OmrGeometry.ROWS_PER_PAGE)
        for (slot in 0 until OmrGeometry.ROWS_PER_PAGE) {
            val block = slot / OmrGeometry.ROWS_PER_BLOCK
            val row = slot % OmrGeometry.ROWS_PER_BLOCK
            rowMeans += DoubleArray(OmrGeometry.OPTIONS) { o ->
                val b = OmrGeometry.answerBubble(block, row, o)
                s.darkestMean(b.x.toDouble(), b.y.toDouble())
            }
            val y = OmrGeometry.ANS_Y[row].toDouble()
            val gaps = (0 until OmrGeometry.OPTIONS - 1).map { o ->
                s.gapMean(OmrGeometry.FIRST_BUBBLE_X[block] + (o + 0.5) * OmrGeometry.RING_PITCH, y)
            }.filter { !it.isNaN() }
            rowPaper[slot] = median(gaps)
        }
        val paper = median(rowPaper.filter { !it.isNaN() })

        val usable = !paper.isNaN() && !ink.isNaN() && paper - ink >= p.minContrastLevels && ink / paper < 0.7
        val inkRatio = if (usable) ink / paper else 0.0

        fun dark(mean: Double, localPaper: Double): Double {
            if (!usable || mean.isNaN()) return 0.0
            val pp = if (!localPaper.isNaN() && localPaper > ink * 1.3) localPaper else paper
            return ((1.0 - mean / pp) / (1.0 - inkRatio)).coerceIn(0.0, 1.0)
        }

        val rows = (0 until OmrGeometry.ROWS_PER_PAGE).map { slot ->
            classify(DoubleArray(OmrGeometry.OPTIONS) { o -> dark(rowMeans[slot][o], rowPaper[slot]) }, p)
        }

        // digit grids
        fun grid(spec: DigitGridSpec?, onlyCol: Int? = null): Pair<List<BubbleSet>, String>? {
            if (spec == null) return null
            val gaps = ArrayList<Double>()
            for (c in 0 until spec.cols) {
                if (onlyCol != null && c != onlyCol) continue
                for (i in 0 until spec.labelCounts[c] - 1) {
                    gaps += s.gapMean(spec.bubbleX(c).toDouble(), ((spec.bubbleY(i) + spec.bubbleY(i + 1)) / 2f).toDouble())
                }
            }
            val gp = median(gaps.filter { !it.isNaN() })
            val cols = ArrayList<BubbleSet>()
            val sb = StringBuilder()
            for (c in 0 until spec.cols) {
                if (onlyCol != null && c != onlyCol) continue
                val n = spec.labelCounts[c]
                if (n == 0) continue
                val b = classify(
                    DoubleArray(n) { i ->
                        val pt = spec.bubble(c, i)
                        dark(s.darkestMean(pt.x.toDouble(), pt.y.toDouble()), gp)
                    },
                    p,
                )
                cols += b
                sb.append(if (b.state == MarkState.FILLED) ('0' + b.marked.first()) else '?')
            }
            return cols to sb.toString()
        }

        fun digits(spec: DigitGridSpec?): DigitsRead? = grid(spec)?.let { DigitsRead(it.second, it.first) }

        val roll = digits(OmrGeometry.rollGrid(layout.omr))
        val reg = digits(OmrGeometry.regGrid(layout.omr))
        val subject = digits(OmrGeometry.subjectGrid(layout.omr))
        val setSpec = OmrGeometry.setGrid(layout.omr, layout.setCodes)
        val setCol = grid(setSpec, onlyCol = 1)?.first?.firstOrNull()
        val setFilled = setCol?.takeIf { it.state == MarkState.FILLED }?.marked?.first()

        return OmrSheetRead(
            qr = qr,
            rows = rows,
            roll = roll,
            reg = reg,
            subject = subject,
            setColumn = setCol,
            setFilled = setFilled,
            paperLevel = paper,
            inkLevel = ink,
            lowContrast = !usable,
            outOfImage = s.outOfImage,
            millis = (System.nanoTime() - t0) / 1_000_000,
        )
    }

    /** Sorts the darkness of the bubbles of one row / column into a [MarkState]. */
    fun classify(d: DoubleArray, p: OmrReadParams): BubbleSet {
        if (d.isEmpty()) return BubbleSet(d, MarkState.BLANK, emptySet())
        var top = 0
        for (i in d.indices) if (d[i] > d[top]) top = i
        var second = 0.0
        for (i in d.indices) if (i != top && d[i] > second) second = d[i]
        val over = d.indices.filter { d[it] >= p.fillThreshold }.toSet()
        return when {
            over.size >= 2 -> BubbleSet(d, MarkState.MULTI, over)
            over.size == 1 ->
                if (second >= p.blankThreshold && second > p.uncertainRatio * d[top]) BubbleSet(d, MarkState.UNCERTAIN, setOf(top))
                else BubbleSet(d, MarkState.FILLED, setOf(top))
            d[top] >= p.blankThreshold -> BubbleSet(d, MarkState.UNCERTAIN, setOf(top))
            else -> BubbleSet(d, MarkState.BLANK, emptySet())
        }
    }

    // ---- QR -----------------------------------------------------------------------------------------------------

    /**
     * The code is looked for first in an UPRIGHT crop of the QR box (made through the alignment map, so tilt and perspective
     * are gone and the scale is fixed), then in the whole photo.
     */
    fun readQr(img: GrayImage, toImage: Homography, p: OmrReadParams = OmrReadParams()): QrStatus {
        val box = OmrGeometry.qrBox()
        val margin = 3.0
        val x0 = box.x - margin
        val y0 = box.y - margin
        val sizeMm = box.w + 2 * margin
        val n = (sizeMm * p.qrCropPxPerMm).toInt()
        val crop = ByteArray(n * n)
        for (j in 0 until n) for (i in 0 until n) {
            val m = toImage.map(x0 + (i + 0.5) / p.qrCropPxPerMm, y0 + (j + 0.5) / p.qrCropPxPerMm)
            crop[j * n + i] = bilinear(img, m.x, m.y).toInt().coerceIn(0, 255).toByte()
        }
        val first = decodeQr(GrayImage(n, n, crop))
        if (first != null) return interpret(first)
        val second = decodeQr(img)
        return if (second != null) interpret(second) else QrStatus.None
    }

    private fun interpret(text: String): QrStatus {
        if (KeyQrPayload.isKeyPage(text)) { // checked first: "IQK1:..." also starts with "IQ"
            val k = KeyQrPayload.decode(text) ?: return QrStatus.OtherVersion(text)
            return QrStatus.KeyPage(k)
        }
        if (!QrPayload.isIntelliQ(text)) return QrStatus.Foreign(text)
        val q = QrPayload.decode(text) ?: return QrStatus.OtherVersion(text)
        return if (q.geomVersion != OmrGeometry.VERSION) QrStatus.OtherVersion(text) else QrStatus.Found(q)
    }

    private class GraySource(private val g: GrayImage) : LuminanceSource(g.w, g.h) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val r = if (row == null || row.size < g.w) ByteArray(g.w) else row
            System.arraycopy(g.px, y * g.w, r, 0, g.w)
            return r
        }

        override fun getMatrix(): ByteArray = g.px
    }

    /** The text of the QR code found anywhere in [g], or null. Also used by the answer-sheet import (no sheet corners needed). */
    internal fun decodeQr(g: GrayImage): String? {
        val hints = mapOf<DecodeHintType, Any>(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        val src = GraySource(g)
        for (kind in 0..1) {
            val bin = if (kind == 0) HybridBinarizer(src) else GlobalHistogramBinarizer(src)
            try {
                return MultiFormatReader().also { it.setHints(hints) }.decodeWithState(BinaryBitmap(bin)).text
            } catch (e: Exception) {
                // not found (NotFoundException / ChecksumException / FormatException): try the next binarizer
            }
        }
        return null
    }

    // ---- sampling -----------------------------------------------------------------------------------------------

    private fun bilinear(img: GrayImage, x: Double, y: Double): Double {
        // pixel (i, j) has its centre at (i + 0.5, j + 0.5)
        val fx = x - 0.5
        val fy = y - 0.5
        val ix = floor(fx).toInt()
        val iy = floor(fy).toInt()
        val ax = fx - ix
        val ay = fy - iy
        fun v(i: Int, j: Int) = img.at(i.coerceIn(0, img.w - 1), j.coerceIn(0, img.h - 1)).toDouble()
        return (v(ix, iy) * (1 - ax) + v(ix + 1, iy) * ax) * (1 - ay) + (v(ix, iy + 1) * (1 - ax) + v(ix + 1, iy + 1) * ax) * ay
    }

    private class Sampler(val img: GrayImage, val h: Homography, val p: OmrReadParams) {
        var outOfImage = 0
        private val window = p.windowFactor * OmrGeometry.BUBBLE_INNER_R
        private val offsets: DoubleArray = run {
            val k = max(0, Math.round(p.searchMm / p.searchStepMm).toInt())
            DoubleArray(2 * k + 1) { (it - k) * p.searchStepMm }
        }

        /** Mean grey inside the disc (radius [rMm]) at sheet point (x, y) mm; NaN when the disc leaves the photo. */
        fun mean(x: Double, y: Double, rMm: Double): Double {
            val c = h.map(x, y)
            val r = max(1.0, rMm * h.localScale(x, y))
            val xa = floor(c.x - r).toInt()
            val xb = floor(c.x + r).toInt()
            val ya = floor(c.y - r).toInt()
            val yb = floor(c.y + r).toInt()
            if (xa < 0 || ya < 0 || xb >= img.w || yb >= img.h) return Double.NaN
            val r2 = r * r
            var sum = 0L
            var cnt = 0
            for (j in ya..yb) {
                val dy = j + 0.5 - c.y
                for (i in xa..xb) {
                    val dx = i + 0.5 - c.x
                    if (dx * dx + dy * dy <= r2) {
                        sum += img.px[j * img.w + i].toInt() and 0xFF
                        cnt++
                    }
                }
            }
            return if (cnt == 0) Double.NaN else sum.toDouble() / cnt
        }

        /** The darkest mean of the bubble window over the small search grid around (x, y). */
        fun darkestMean(x: Double, y: Double): Double {
            var best = Double.NaN
            for (dy in offsets) for (dx in offsets) {
                val m = mean(x + dx, y + dy, window)
                if (!m.isNaN() && (best.isNaN() || m < best)) best = m
            }
            if (best.isNaN()) outOfImage++
            return best
        }

        /** Paper between bubbles: one small disc, no search. */
        fun gapMean(x: Double, y: Double): Double = mean(x, y, 0.9)
    }

    private fun median(v: List<Double>): Double {
        if (v.isEmpty()) return Double.NaN
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}
