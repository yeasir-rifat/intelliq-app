package com.intelliq.rifatplex.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Step 4.4 - find the printed OMR sheet in a photo and work out where every printed thing is (pure Kotlin, no Android).
 *
 * Result: a homography H that turns a point of the sheet in millimetres (the units of OmrGeometry) into a pixel of the
 * photo. Step 4.5 measures every bubble through it, so the photo is never warped as a whole (fast, little memory).
 *
 * Pixel convention used everywhere in this file: the pixel with index (i, j) covers [i, i+1) x [j, j+1), its centre is
 * (i + 0.5, j + 0.5). Coordinates are continuous. Scaling an image by f therefore scales every coordinate by exactly f.
 *
 * Steps (all on a copy of the photo reduced to at most OmrAlignParams.workMaxSide pixels):
 *  1. grey = the RED channel of the photo (the sheet is printed in magenta, which is almost white in red; black and blue
 *     ink stay dark. Colour dropout: bubbles, boxes and printed letters disappear, marks and pen strokes remain).
 *  2. adaptive threshold with an integral image (window = 1/10 of the width): shadows and uneven light are harmless.
 *  3. the four corner squares: connected dark blobs in the four quarters of the photo that are square and solid; of the
 *     candidates the combination that forms a sensible A4 quadrilateral wins.
 *  4. rotation: the 52 timing dashes are only on the LEFT edge. For each of the 4 ways to name the corners, count the
 *     dashes found where that guess predicts them; the best guess is the true orientation (upside-down photos work).
 *  5. refine: measure the real centre of every dash and fit one homography to 4 corners + up to 52 dashes (weighted
 *     least squares), throw out dashes that disagree, fit again.
 *  6. quality gate: scale, number of dashes and fit error, each with its own failure reason.
 *
 * Limits (on purpose): the sheet must be roughly upright in the photo (any multiple of 90 degrees, tilt up to about
 * 35 degrees) so that each corner of the sheet is in its own quarter of the photo; one sheet per photo.
 */

/** A point, in pixels of a photo or in millimetres of the sheet. */
internal data class PointD(val x: Double, val y: Double)

/** 8-bit grey image, row by row. [px] holds unsigned bytes. */
internal class GrayImage(val w: Int, val h: Int, val px: ByteArray) {
    init {
        require(w > 0 && h > 0 && px.size == w * h) { "bad grey image size" }
    }

    fun at(x: Int, y: Int): Int = px[y * w + x].toInt() and 0xFF

    /**
     * Area-averaged copy whose longer side is at most [maxSide] (this image itself when it is small enough). Each new
     * pixel is the exact weighted mean of the old pixels it covers, so thin marks keep their darkness.
     */
    fun downscaled(maxSide: Int): GrayImage {
        val longSide = max(w, h)
        if (longSide <= maxSide) return this
        val k = maxSide.toDouble() / longSide
        val nw = max(1, (w * k).roundToInt())
        val nh = max(1, (h * k).roundToInt())
        // horizontal pass: w -> nw, every row
        val tmp = FloatArray(nw * h)
        val fx = w.toDouble() / nw
        for (x in 0 until nw) {
            val a = x * fx
            val b = (x + 1) * fx
            val i0 = a.toInt()
            val i1 = min(w - 1, (b - 1e-9).toInt())
            for (y in 0 until h) {
                var sum = 0.0
                var wsum = 0.0
                val row = y * w
                for (i in i0..i1) {
                    val cov = min(b, i + 1.0) - max(a, i.toDouble())
                    sum += cov * (px[row + i].toInt() and 0xFF)
                    wsum += cov
                }
                tmp[y * nw + x] = (sum / wsum).toFloat()
            }
        }
        val out = ByteArray(nw * nh)
        val fy = h.toDouble() / nh
        for (y in 0 until nh) {
            val a = y * fy
            val b = (y + 1) * fy
            val j0 = a.toInt()
            val j1 = min(h - 1, (b - 1e-9).toInt())
            for (x in 0 until nw) {
                var sum = 0.0
                var wsum = 0.0
                for (j in j0..j1) {
                    val cov = min(b, j + 1.0) - max(a, j.toDouble())
                    sum += cov * tmp[j * nw + x]
                    wsum += cov
                }
                out[y * nw + x] = (sum / wsum).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return GrayImage(nw, nh, out)
    }

    /** The image as a viewer shows it, given the EXIF orientation tag (1..8; anything else = unchanged). */
    fun oriented(exif: Int): GrayImage {
        if (exif !in 2..8) return this
        val swap = exif >= 5
        val nw = if (swap) h else w
        val nh = if (swap) w else h
        val out = ByteArray(nw * nh)
        for (y in 0 until nh) for (x in 0 until nw) {
            val sx: Int
            val sy: Int
            when (exif) {
                2 -> { sx = w - 1 - x; sy = y }
                3 -> { sx = w - 1 - x; sy = h - 1 - y }
                4 -> { sx = x; sy = h - 1 - y }
                5 -> { sx = y; sy = x }
                6 -> { sx = y; sy = h - 1 - x }
                7 -> { sx = w - 1 - y; sy = h - 1 - x }
                else -> { sx = w - 1 - y; sy = x } // 8
            }
            out[y * nw + x] = px[sy * w + sx]
        }
        return GrayImage(nw, nh, out)
    }
}

/** A 3x3 projective map, row-major, m[8] = 1. */
internal class Homography(val m: DoubleArray) {
    init {
        require(m.size == 9)
    }

    fun map(x: Double, y: Double): PointD {
        val w = m[6] * x + m[7] * y + m[8]
        return PointD((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    fun map(p: PointD): PointD = map(p.x, p.y)

    /** this applied after [o]: (this * o)(p) = this(o(p)). */
    fun times(o: Homography): Homography {
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += m[i * 3 + k] * o.m[k * 3 + j]
            r[i * 3 + j] = s
        }
        return Homography(normalized(r))
    }

    fun inverse(): Homography? {
        val a = m
        val c00 = a[4] * a[8] - a[5] * a[7]
        val c01 = a[5] * a[6] - a[3] * a[8]
        val c02 = a[3] * a[7] - a[4] * a[6]
        val det = a[0] * c00 + a[1] * c01 + a[2] * c02
        if (abs(det) < 1e-300 || det.isNaN()) return null
        val r = doubleArrayOf(
            c00, a[2] * a[7] - a[1] * a[8], a[1] * a[5] - a[2] * a[4],
            c01, a[0] * a[8] - a[2] * a[6], a[2] * a[3] - a[0] * a[5],
            c02, a[1] * a[6] - a[0] * a[7], a[0] * a[4] - a[1] * a[3],
        )
        return Homography(normalized(r))
    }

    /** Pixels per unit (mm) around (x, y): square root of the area scale of the map there. */
    fun localScale(x: Double, y: Double): Double {
        val o = map(x, y)
        val px = map(x + 1.0, y)
        val py = map(x, y + 1.0)
        val a = (px.x - o.x) * (py.y - o.y) - (px.y - o.y) * (py.x - o.x)
        return sqrt(abs(a))
    }

    companion object {
        private fun normalized(r: DoubleArray): DoubleArray {
            val d = r[8]
            if (abs(d) > 1e-300) for (i in 0 until 9) r[i] /= d
            return r
        }

        fun scaling(sx: Double, sy: Double) = Homography(doubleArrayOf(sx, 0.0, 0.0, 0.0, sy, 0.0, 0.0, 0.0, 1.0))

        /**
         * The homography that sends [src] to [dst] (weighted least squares, [weights] default 1). Needs 4+ points, no
         * three of the four main ones on a line. Coordinates are centred and scaled first, so pixel and millimetre
         * numbers of very different size do not hurt the precision. Null if the system is degenerate.
         */
        fun fit(src: List<PointD>, dst: List<PointD>, weights: DoubleArray? = null): Homography? {
            val n = src.size
            if (n < 4 || dst.size != n) return null
            val ts = normalizer(src)
            val td = normalizer(dst)
            val a = Array(8) { DoubleArray(8) }
            val b = DoubleArray(8)
            val r1 = DoubleArray(8)
            val r2 = DoubleArray(8)
            for (i in 0 until n) {
                val wgt = weights?.get(i) ?: 1.0
                val s = ts.map(src[i])
                val d = td.map(dst[i])
                r1[0] = s.x; r1[1] = s.y; r1[2] = 1.0; r1[3] = 0.0; r1[4] = 0.0; r1[5] = 0.0
                r1[6] = -s.x * d.x; r1[7] = -s.y * d.x
                r2[0] = 0.0; r2[1] = 0.0; r2[2] = 0.0; r2[3] = s.x; r2[4] = s.y; r2[5] = 1.0
                r2[6] = -s.x * d.y; r2[7] = -s.y * d.y
                for (p in 0 until 8) {
                    for (q in 0 until 8) a[p][q] += wgt * (r1[p] * r1[q] + r2[p] * r2[q])
                    b[p] += wgt * (r1[p] * d.x + r2[p] * d.y)
                }
            }
            val h = solve(a, b) ?: return null
            val hn = Homography(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
            val tdInv = td.inverse() ?: return null
            val full = tdInv.times(hn).times(ts)
            if (full.m.any { it.isNaN() || it.isInfinite() }) return null
            return full
        }

        private fun normalizer(p: List<PointD>): Homography {
            val cx = p.sumOf { it.x } / p.size
            val cy = p.sumOf { it.y } / p.size
            val md = p.sumOf { sqrt((it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy)) } / p.size
            val s = if (md > 1e-12) sqrt(2.0) / md else 1.0
            return Homography(doubleArrayOf(s, 0.0, -s * cx, 0.0, s, -s * cy, 0.0, 0.0, 1.0))
        }

        /** Gaussian elimination with partial pivoting; [a] and [b] are destroyed. Null when (nearly) singular. */
        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            for (c in 0 until n) {
                var piv = c
                for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
                if (abs(a[piv][c]) < 1e-12) return null
                if (piv != c) {
                    val t = a[piv]; a[piv] = a[c]; a[c] = t
                    val tb = b[piv]; b[piv] = b[c]; b[c] = tb
                }
                for (r in c + 1 until n) {
                    val f = a[r][c] / a[c][c]
                    if (f == 0.0) continue
                    for (k in c until n) a[r][k] -= f * a[c][k]
                    b[r] -= f * b[c]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var s = b[r]
                for (k in r + 1 until n) s -= a[r][k] * x[k]
                x[r] = s / a[r][r]
            }
            return x
        }
    }
}

/** Why a photo could not be aligned. The UI turns each into a short message (step 4.6). */
internal enum class AlignFail {
    /** Fewer than ~900 px on the long side: nothing to measure. */
    IMAGE_TOO_SMALL,

    /** One or more of the four black corner squares was not found (cut off, covered, far too dark). */
    CORNERS_NOT_FOUND,

    /** The sheet is found but too small in the photo: under [OmrAlignParams.minScalePxPerMm]. */
    TOO_FAR,

    /** Corners found but the timing dashes were not readable: blurred, dim, glare, a hand on the edge. */
    MARKS_UNREADABLE,

    /** Dashes found but they do not lie on a flat sheet (heavily bent / folded paper, strong lens distortion). */
    BAD_FIT,
}

internal class OmrAlignParams(
    /** Detection runs on a copy with at most this many pixels on the long side. */
    val workMaxSide: Int = 2000,
    /** Pixels per millimetre needed (in the photo as given), see README step 4.2 (section 2, 3). */
    val minScalePxPerMm: Double = 6.0,
    /** Timing dashes that must be found, of OmrGeometry.TIMING_COUNT. */
    val minDashes: Int = 48,
    /** Largest accepted root-mean-square error of the fit, in mm. */
    val maxFitRmsMm: Double = 0.5,
    /** Dashes that miss the fitted position by more than this are dropped from the fit (mm). */
    val dashOutlierMm: Double = 0.8,
)

internal sealed class AlignResult {
    /**
     * The sheet is aligned. [toImage] maps mm of the sheet to pixels of the photo that was passed in, [toMm] the reverse.
     * [quarterTurnsCcw] says how the sheet sits in the photo: 0 upright, 1 turned a quarter counter-clockwise, 2 upside
     * down, 3 a quarter clockwise. [corners] are the measured centres of the corner squares in the photo, in the order
     * top-left, top-right, bottom-left, bottom-right OF THE SHEET.
     */
    class Ok(
        val toImage: Homography,
        val toMm: Homography,
        val scalePxPerMm: Double,
        val quarterTurnsCcw: Int,
        val dashes: Int,
        val fitRmsMm: Double,
        val fitMaxMm: Double,
        val corners: List<PointD>,
    ) : AlignResult()

    /** [corners] = the four corner blobs found (photo pixels, clockwise from the photo's top-left) or null. */
    class Failed(val reason: AlignFail, val detail: String, val corners: List<PointD>?) : AlignResult()
}

internal object OmrAlign {
    private const val SHEET_W = OmrGeometry.PAGE_W_MM.toDouble()

    /** Sheet corner indices (as in OmrGeometry.cornerMarks: TL, TR, BL, BR) in clockwise order. */
    private val SHEET_CLOCKWISE = intArrayOf(0, 1, 3, 2)

    private const val WEIGHT_CORNER = 8.0

    /** Aligns [img] (red channel of the photo, upright as the viewer shows it). */
    fun align(img: GrayImage, params: OmrAlignParams = OmrAlignParams()): AlignResult = run(img, null, params)

    /**
     * Same, but with the four corner squares given by hand (photo pixels, clockwise as seen in the photo; each point is
     * snapped to the nearest corner blob when there is one). For the "drag the 4 corners" fallback of step 4.6.
     */
    fun alignWithCorners(img: GrayImage, clockwise: List<PointD>, params: OmrAlignParams = OmrAlignParams()): AlignResult {
        require(clockwise.size == 4) { "four corners needed" }
        return run(img, clockwise, params)
    }

    // -------------------------------------------------------------------------------------------------------------

    private fun run(full: GrayImage, manual: List<PointD>?, p: OmrAlignParams): AlignResult {
        if (max(full.w, full.h) < 900 || min(full.w, full.h) < 500) {
            return AlignResult.Failed(AlignFail.IMAGE_TOO_SMALL, "photo ${full.w}x${full.h}", null)
        }
        val work = full.downscaled(p.workMaxSide)
        val fx = full.w.toDouble() / work.w
        val fy = full.h.toDouble() / work.h
        val up = Homography.scaling(fx, fy)
        val mask = darkMask(work)

        // ---- 3. corner squares
        val corners: List<PointD> = if (manual != null) {
            val rad = max(work.w, work.h) * 0.05
            manual.map { t ->
                val w = PointD(t.x / fx, t.y / fy)
                val x0 = max(0, (w.x - rad).toInt())
                val x1 = min(work.w, (w.x + rad).toInt() + 1)
                val y0 = max(0, (w.y - rad).toInt())
                val y1 = min(work.h, (w.y + rad).toInt() + 1)
                val c = if (x1 > x0 && y1 > y0) candidates(work, mask, x0, y0, x1, y1, loose = true) else emptyList()
                val best = c.minByOrNull { (it.x - w.x) * (it.x - w.x) + (it.y - w.y) * (it.y - w.y) }
                if (best != null) PointD(best.x, best.y) else w
            }
        } else {
            findCorners(work, mask) ?: return AlignResult.Failed(AlignFail.CORNERS_NOT_FOUND, "corner squares", null)
        }
        val cornersFull = corners.map { PointD(it.x * fx, it.y * fy) }

        // ---- scale gate (cheap, so before the heavy part)
        val sheetMark = marksCenters()
        val rough = Homography.fit(SHEET_CLOCKWISE.map { sheetMark[it] }, corners)
            ?: return AlignResult.Failed(AlignFail.CORNERS_NOT_FOUND, "degenerate corners", cornersFull)
        val roughScaleFull = up.times(rough).localScale(SHEET_W / 2, OmrGeometry.PAGE_H_MM / 2.0)
        if (roughScaleFull < p.minScalePxPerMm) {
            return AlignResult.Failed(AlignFail.TOO_FAR, "scale %.1f px/mm".format(roughScaleFull), cornersFull)
        }

        // ---- 4. rotation: the guess that finds most dashes
        var bestK = -1
        var bestCount = -1
        var bestH: Homography? = null
        for (k in 0 until 4) {
            val src = ArrayList<PointD>(4)
            val dst = ArrayList<PointD>(4)
            for (i in 0 until 4) {
                src += sheetMark[SHEET_CLOCKWISE[(i + k) % 4]]
                dst += corners[i]
            }
            val h = Homography.fit(src, dst) ?: continue
            val inv = h.inverse() ?: continue
            var count = 0
            for (d in 0 until OmrGeometry.TIMING_COUNT) {
                val m = measureDash(work, mask, h, inv, d, 3.0, 1.5)
                if (m != null) count++
            }
            if (count > bestCount) {
                bestCount = count; bestK = k; bestH = h
            }
        }
        var h = bestH ?: return AlignResult.Failed(AlignFail.CORNERS_NOT_FOUND, "degenerate corners", cornersFull)
        if (bestCount < OmrGeometry.TIMING_COUNT / 2) {
            return AlignResult.Failed(AlignFail.MARKS_UNREADABLE, "timing dashes $bestCount/${OmrGeometry.TIMING_COUNT}", cornersFull)
        }

        // ---- 5. refine on the dashes
        val cornerSrc = ArrayList<PointD>(4)
        val cornerDst = ArrayList<PointD>(4)
        for (i in 0 until 4) {
            cornerSrc += sheetMark[SHEET_CLOCKWISE[(i + bestK) % 4]]
            cornerDst += corners[i]
        }
        val dashSrc = (0 until OmrGeometry.TIMING_COUNT).map { dashCentre(it) }
        var used = BooleanArray(OmrGeometry.TIMING_COUNT)
        var rms = 0.0
        var maxErr = 0.0
        var inliers = 0
        for (round in 0 until 3) {
            val inv = h.inverse() ?: break
            val pts = arrayOfNulls<PointD>(OmrGeometry.TIMING_COUNT)
            for (d in 0 until OmrGeometry.TIMING_COUNT) pts[d] = measureDash(work, mask, h, inv, d, 3.0, 1.5)
            val scale = h.localScale(SHEET_W / 2, OmrGeometry.PAGE_H_MM / 2.0)
            val src = ArrayList<PointD>()
            val dst = ArrayList<PointD>()
            val wts = ArrayList<Double>()
            for (i in 0 until 4) {
                src += cornerSrc[i]; dst += cornerDst[i]; wts += WEIGHT_CORNER
            }
            val keep = BooleanArray(OmrGeometry.TIMING_COUNT)
            for (d in 0 until OmrGeometry.TIMING_COUNT) {
                val m = pts[d] ?: continue
                // already a clear outlier against the current map?
                val pred = h.map(dashSrc[d])
                val errMm = sqrt((pred.x - m.x) * (pred.x - m.x) + (pred.y - m.y) * (pred.y - m.y)) / scale
                if (round > 0 && errMm > p.dashOutlierMm) continue
                keep[d] = true
                src += dashSrc[d]; dst += m; wts += 1.0
            }
            val nh = Homography.fit(src, dst, wts.toDoubleArray()) ?: break
            h = nh
            used = keep
            inliers = keep.count { it }
            // residuals of the new map
            val sc = h.localScale(SHEET_W / 2, OmrGeometry.PAGE_H_MM / 2.0)
            var sum = 0.0
            var mx = 0.0
            for (i in src.indices) {
                val pr = h.map(src[i])
                val e = sqrt((pr.x - dst[i].x) * (pr.x - dst[i].x) + (pr.y - dst[i].y) * (pr.y - dst[i].y)) / sc
                sum += e * e
                if (e > mx) mx = e
            }
            rms = sqrt(sum / src.size)
            maxErr = mx
        }
        if (inliers < p.minDashes) {
            return AlignResult.Failed(AlignFail.MARKS_UNREADABLE, "timing dashes $inliers/${OmrGeometry.TIMING_COUNT}", cornersFull)
        }
        if (rms > p.maxFitRmsMm) {
            return AlignResult.Failed(AlignFail.BAD_FIT, "fit error %.2f mm".format(rms), cornersFull)
        }

        // ---- 7. back to the photo as given
        val toImage = up.times(h)
        val toMm = toImage.inverse() ?: return AlignResult.Failed(AlignFail.BAD_FIT, "singular", cornersFull)
        val scaleFull = toImage.localScale(SHEET_W / 2, OmrGeometry.PAGE_H_MM / 2.0)
        if (scaleFull < p.minScalePxPerMm) {
            return AlignResult.Failed(AlignFail.TOO_FAR, "scale %.1f px/mm".format(scaleFull), cornersFull)
        }
        val ordered = marksCenters().map { toImage.map(it) } // where the fitted map puts the 4 marks: TL, TR, BL, BR
        return AlignResult.Ok(
            toImage = toImage, toMm = toMm, scalePxPerMm = scaleFull, quarterTurnsCcw = bestK,
            dashes = used.count { it }, fitRmsMm = rms, fitMaxMm = maxErr, corners = ordered,
        )
    }

    private fun marksCenters(): List<PointD> =
        OmrGeometry.cornerMarks().map { PointD((it.x + it.w / 2f).toDouble(), (it.y + it.h / 2f).toDouble()) }

    private fun dashCentre(i: Int): PointD = PointD(
        (OmrGeometry.TIMING_X + OmrGeometry.TIMING_W / 2f).toDouble(),
        (OmrGeometry.TIMING_Y0 + i * OmrGeometry.TIMING_PITCH + OmrGeometry.TIMING_H / 2f).toDouble(),
    )

    // ---- 2. threshold -------------------------------------------------------------------------------------------

    /** 1 where the pixel is clearly darker than its surroundings (window about 1/10 of the width), else 0. */
    fun darkMask(img: GrayImage): ByteArray {
        val w = img.w
        val h = img.h
        val sw = w + 1
        val integ = IntArray(sw * (h + 1)) // sums may wrap around Int; differences stay exact (the true sum fits)
        for (y in 0 until h) {
            var row = 0
            for (x in 0 until w) {
                row += img.px[y * w + x].toInt() and 0xFF
                integ[(y + 1) * sw + x + 1] = integ[y * sw + x + 1] + row
            }
        }
        val r = max(8, w / 20)
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - r)
            val y1 = min(h, y + r + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - r)
                val x1 = min(w, x + r + 1)
                val area = (x1 - x0) * (y1 - y0)
                val sum = integ[y1 * sw + x1] - integ[y0 * sw + x1] - integ[y1 * sw + x0] + integ[y0 * sw + x0]
                val v = img.px[y * w + x].toInt() and 0xFF
                // v < 0.80 * mean, and at least 24 grey levels darker (flat noise is not a mark)
                if (v.toLong() * area * 100 < sum.toLong() * 80 && sum.toLong() - v.toLong() * area > 24L * area) {
                    out[y * w + x] = 1
                }
            }
        }
        return out
    }

    // ---- 3. corner squares --------------------------------------------------------------------------------------

    private class Blob(val x: Double, val y: Double, val side: Double, val score: Double)

    /**
     * Square, solid, dark blobs inside the pixel rectangle [x0,x1) x [y0,y1) of the work image. A filled square of side
     * a has both principal variances a^2/12, whatever its rotation, so: squareness = sqrt(small/large) and
     * solidity = area / (12 sqrt(large*small)) are both 1 for it (a ring or a letter has solidity < 1).
     */
    private fun candidates(
        img: GrayImage, mask: ByteArray, x0: Int, y0: Int, x1: Int, y1: Int, loose: Boolean = false,
    ): List<Blob> {
        val rw = x1 - x0
        val rh = y1 - y0
        val longSide = max(img.w, img.h).toDouble()
        // at 297 mm on the long side a corner square is 1.6 % of it; allow a sheet filling 35 %..100 % of the photo
        val minSide = longSide * 0.005
        val maxSide = longSide * 0.05
        val seen = BooleanArray(rw * rh)
        val stack = IntArray(rw * rh)
        val out = ArrayList<Blob>()
        for (ly in 0 until rh) for (lx in 0 until rw) {
            val li = ly * rw + lx
            if (seen[li] || mask[(y0 + ly) * img.w + x0 + lx].toInt() == 0) continue
            var sp = 0
            stack[sp++] = li
            seen[li] = true
            var area = 0L
            var sx = 0L
            var sy = 0L
            var sxx = 0L
            var syy = 0L
            var sxy = 0L
            while (sp > 0) {
                val c = stack[--sp]
                val cy = c / rw
                val cx = c - cy * rw
                area++
                sx += cx; sy += cy
                sxx += cx.toLong() * cx; syy += cy.toLong() * cy; sxy += cx.toLong() * cy
                if (cx > 0) sp = push(c - 1, mask, img.w, x0, y0, rw, seen, stack, sp)
                if (cx < rw - 1) sp = push(c + 1, mask, img.w, x0, y0, rw, seen, stack, sp)
                if (cy > 0) sp = push(c - rw, mask, img.w, x0, y0, rw, seen, stack, sp)
                if (cy < rh - 1) sp = push(c + rw, mask, img.w, x0, y0, rw, seen, stack, sp)
            }
            val side = sqrt(area.toDouble())
            if (side < minSide || side > maxSide) continue
            val n = area.toDouble()
            val mx = sx / n
            val my = sy / n
            val vxx = sxx / n - mx * mx + 1.0 / 12
            val vyy = syy / n - my * my + 1.0 / 12
            val vxy = sxy / n - mx * my
            val tr = (vxx + vyy) / 2
            val dd = sqrt(((vxx - vyy) / 2) * ((vxx - vyy) / 2) + vxy * vxy)
            val l1 = tr + dd
            val l2 = tr - dd
            if (l2 <= 0) continue
            val squareness = sqrt(l2 / l1)
            val solidity = n / (12 * sqrt(l1 * l2))
            val minSq = if (loose) 0.6 else 0.72
            if (squareness < minSq || solidity < 0.78 || solidity > 1.25) continue
            val score = squareness * (1 - min(1.0, abs(solidity - 1) * 3))
            out += Blob(x0 + mx + 0.5, y0 + my + 0.5, side, score)
        }
        return out
    }

    private fun push(
        li: Int, mask: ByteArray, imgW: Int, x0: Int, y0: Int, rw: Int, seen: BooleanArray, stack: IntArray, sp: Int,
    ): Int {
        if (seen[li]) return sp
        val y = li / rw
        val x = li - y * rw
        if (mask[(y0 + y) * imgW + x0 + x].toInt() == 0) return sp
        seen[li] = true
        stack[sp] = li
        return sp + 1
    }

    /** The four corner centres clockwise from the photo's top-left, or null. */
    private fun findCorners(img: GrayImage, mask: ByteArray): List<PointD>? {
        val rx = img.w / 2
        val ry = img.h / 2
        val regions = listOf(
            intArrayOf(0, 0, rx, ry), // TL
            intArrayOf(img.w - rx, 0, img.w, ry), // TR
            intArrayOf(img.w - rx, img.h - ry, img.w, img.h), // BR
            intArrayOf(0, img.h - ry, rx, img.h), // BL
        )
        val perCorner = regions.map { r ->
            candidates(img, mask, r[0], r[1], r[2], r[3]).sortedByDescending { it.score }.take(4)
        }
        if (perCorner.any { it.isEmpty() }) return null
        var best: List<Blob>? = null
        var bestScore = -1.0
        for (a in perCorner[0]) for (b in perCorner[1]) for (c in perCorner[2]) for (d in perCorner[3]) {
            val q = listOf(a, b, c, d)
            val s = quadScore(q, img) ?: continue
            if (s > bestScore) {
                bestScore = s; best = q
            }
        }
        return best?.map { PointD(it.x, it.y) }
    }

    /** Null when the four blobs cannot be the corner squares of an A4 sheet; else a score (higher = better). */
    private fun quadScore(q: List<Blob>, img: GrayImage): Double? {
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val c = q[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross <= 0) return null // not convex clockwise
        }
        fun dist(i: Int, j: Int) = sqrt((q[i].x - q[j].x) * (q[i].x - q[j].x) + (q[i].y - q[j].y) * (q[i].y - q[j].y))
        val top = dist(0, 1)
        val right = dist(1, 2)
        val bottom = dist(2, 3)
        val left = dist(3, 0)
        if (top <= 0 || right <= 0 || bottom <= 0 || left <= 0) return null
        if (top / bottom !in 0.6..1.67 || right / left !in 0.6..1.67) return null
        val hor = (top + bottom) / 2
        val ver = (right + left) / 2
        val aspect = max(hor, ver) / min(hor, ver)
        if (aspect !in 1.15..1.9) return null // the marks of the sheet are 177.6 x 262.9 mm apart (1.48)
        val scale = max(hor, ver) / (OmrGeometry.PAGE_H_MM - 2 * 14.659 - OmrGeometry.MARK_SIZE) // px per mm, roughly
        val expected = OmrGeometry.MARK_SIZE * scale
        for (b in q) if (b.side < expected * 0.5 || b.side > expected * 1.8) return null
        var area = 0.0
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            area += a.x * b.y - b.x * a.y
        }
        area = abs(area) / 2
        return q.sumOf { it.score } + 3.0 * area / (img.w.toDouble() * img.h)
    }

    // ---- 4/5. timing dashes -------------------------------------------------------------------------------------

    /**
     * Looks for dash [i] where the map [h] (mm -> work pixels, inverse [inv]) predicts it: all dark pixels whose
     * millimetre position is within [halfX] x [halfY] of the predicted centre. Returns the centre of those pixels in
     * work pixels, or null if there are too few or too many (about 0.45..1.8 times a dash).
     */
    private fun measureDash(
        img: GrayImage, mask: ByteArray, h: Homography, inv: Homography, i: Int, halfX: Double, halfY: Double,
    ): PointD? {
        val c = dashCentre(i)
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val q = h.map(c.x + sx * halfX, c.y + sy * halfY)
            minX = min(minX, q.x); maxX = max(maxX, q.x)
            minY = min(minY, q.y); maxY = max(maxY, q.y)
        }
        val x0 = max(0, minX.toInt())
        val x1 = min(img.w - 1, maxX.toInt() + 1)
        val y0 = max(0, minY.toInt())
        val y1 = min(img.h - 1, maxY.toInt() + 1)
        if (x1 < x0 || y1 < y0) return null
        var n = 0
        var sx = 0.0
        var sy = 0.0
        for (y in y0..y1) {
            val row = y * img.w
            for (x in x0..x1) {
                if (mask[row + x].toInt() == 0) continue
                val m = inv.map(x + 0.5, y + 0.5)
                if (abs(m.x - c.x) <= halfX && abs(m.y - c.y) <= halfY) {
                    n++; sx += x + 0.5; sy += y + 0.5
                }
            }
        }
        val scale = h.localScale(c.x, c.y)
        val expected = OmrGeometry.TIMING_W * OmrGeometry.TIMING_H * scale * scale
        if (n < expected * 0.45 || n > expected * 1.8) return null
        return PointD(sx / n, sy / n)
    }
}
