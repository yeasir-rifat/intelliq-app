package com.intelliq.rifatplex.data

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Test helper (step 4.4): draws what the RED channel of a photo of the OMR sheet looks like, from OmrGeometry alone:
 * paper light grey, the 4 corner squares and 52 timing dashes black. The magenta print (bubbles, boxes, letters) is
 * white in the red channel, so it is left out on purpose. Optional clutter (black text blobs, pen strokes), shadow,
 * a dark table around the sheet, blur and noise make the picture hostile.
 */
internal class SyntheticOptions(
    val imgW: Int = 1800,
    val imgH: Int = 2400,
    /** px per mm; 0 = as large as fits. */
    val scale: Double = 0.0,
    /** extra turn of the sheet, counter-clockwise on screen, degrees */
    val tiltDeg: Double = 0.0,
    /** quarter turns counter-clockwise on screen (0..3) */
    val quarterTurns: Int = 0,
    /** perspective strength: 0 none, 0.2 = far edge 20 % smaller than the near one, along x / y */
    val perspX: Double = 0.0,
    val perspY: Double = 0.0,
    val shiftMmX: Double = 0.0,
    val shiftMmY: Double = 0.0,
    val shadow: Double = 0.0, // 0..0.6 darkening toward the right/bottom
    val table: Int = 235, // grey of what is outside the paper
    val clutter: Boolean = false,
    val blurPx: Int = 0,
    val noise: Double = 0.0,
    val seed: Long = 1,
    /** timing dashes (0..51) left out, as if a thumb covered them */
    val eraseDashes: List<Int> = emptyList(),
    /** Step 4.5: pen marks (filled discs, grey value 0..255 in the red channel). */
    val discs: List<SynDisc> = emptyList(),
    /** Step 4.5: print this QR code in the QR box (black modules). */
    val qr: String? = null,
)

/** A round pen mark: centre and radius in mm of the sheet, grey level (30 = black pen, 120 = light pencil). */
internal class SynDisc(val x: Double, val y: Double, val r: Double, val value: Int = 40)

internal class SyntheticSheet(val opt: SyntheticOptions) {
    /** mm -> px, the truth. */
    val h: Homography
    val image: GrayImage

    private class R(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

    private val rects = ArrayList<R>()
    private val cells = HashMap<Int, ArrayList<SynDisc>>()
    private var qrM: QrMatrix? = null
    private val cellMm = 4.0

    private fun cellKey(cx: Int, cy: Int) = cx * 1000 + cy

    init {
        val q = opt.quarterTurns
        val landscape = q % 2 == 1
        val w = opt.imgW
        val hh = opt.imgH
        val s = if (opt.scale > 0) opt.scale else {
            val fitW = (if (landscape) 297.0 else 210.0) * 1.08
            val fitH = (if (landscape) 210.0 else 297.0) * 1.08
            minOf(w / fitW, hh / fitH)
        }
        // mm -> px : T(centre of image) * P * R(ccw angle) * S(s) * T(-(105,148.5) - shift)
        val phi = (opt.tiltDeg + 90.0 * q) * PI / 180
        val c = cos(phi)
        val sn = sin(phi)
        val t0 = Homography(doubleArrayOf(1.0, 0.0, -(105.0 + opt.shiftMmX), 0.0, 1.0, -(148.5 + opt.shiftMmY), 0.0, 0.0, 1.0))
        val sc = Homography.scaling(s, s)
        // ccw on screen with y down: x' = x cos + y sin, y' = -x sin + y cos
        val rot = Homography(doubleArrayOf(c, sn, 0.0, -sn, c, 0.0, 0.0, 0.0, 1.0))
        val a = opt.perspX / (105.0 * s * 2)
        val b = opt.perspY / (148.5 * s * 2)
        val persp = Homography(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, a, b, 1.0))
        val t1 = Homography(doubleArrayOf(1.0, 0.0, w / 2.0, 0.0, 1.0, hh / 2.0, 0.0, 0.0, 1.0))
        h = t1.times(persp).times(rot).times(sc).times(t0)

        for (m in OmrGeometry.cornerMarks()) rects += R(m.x.toDouble(), m.y.toDouble(), (m.x + m.w).toDouble(), (m.y + m.h).toDouble())
        if (opt.clutter) {
            val rnd = Random(opt.seed)
            // text-like blobs and pen strokes in the middle of the sheet (never over the marks)
            repeat(40) {
                val x = 25 + rnd.nextDouble() * 150
                val y = 45 + rnd.nextDouble() * 220
                val ww = 2 + rnd.nextDouble() * 30
                val hh2 = 0.3 + rnd.nextDouble() * 3
                rects += R(x, y, x + ww, y + hh2)
            }
            // a solid, nearly square black blob that is NOT a corner mark (a stamp), near the top-left region
            rects += R(40.0, 20.0, 45.0, 25.0)
            // a pen line along the left margin but outside the dash column
            rects += R(21.0, 60.0, 22.0, 200.0)
        }
        for (d in opt.discs) {
            for (cx in floor((d.x - d.r) / cellMm).toInt()..floor((d.x + d.r) / cellMm).toInt())
                for (cy in floor((d.y - d.r) / cellMm).toInt()..floor((d.y + d.r) / cellMm).toInt())
                    cells.getOrPut(cellKey(cx, cy)) { ArrayList() }.add(d)
        }
        qrM = opt.qr?.let { QrCode.encode(it) }
        image = render()
    }

    /** grey value of a pen mark / QR module at the point, or -1 */
    private fun penValue(x: Double, y: Double): Int {
        val m = qrM
        if (m != null) {
            val b = OmrGeometry.qrBox()
            val q = OmrGeometry.QR_QUIET_MM.toDouble()
            val cx = (x - b.x - q) / OmrGeometry.QR_CODE_MM * m.size
            val cy = (y - b.y - q) / OmrGeometry.QR_CODE_MM * m.size
            if (cx >= 0 && cy >= 0 && cx < m.size && cy < m.size && m[cx.toInt(), cy.toInt()]) return 30
        }
        val list = cells[cellKey(floor(x / cellMm).toInt(), floor(y / cellMm).toInt())] ?: return -1
        var v = -1
        for (d in list) if ((x - d.x) * (x - d.x) + (y - d.y) * (y - d.y) <= d.r * d.r) v = if (v < 0) d.value else minOf(v, d.value)
        return v
    }

    private fun dark(x: Double, y: Double): Boolean {
        for (r in rects) if (x >= r.x0 && x < r.x1 && y >= r.y0 && y < r.y1) return true
        if (x >= OmrGeometry.TIMING_X && x < OmrGeometry.TIMING_X + OmrGeometry.TIMING_W) {
            val k = floor((y - OmrGeometry.TIMING_Y0) / OmrGeometry.TIMING_PITCH).toInt()
            if (k in 0 until OmrGeometry.TIMING_COUNT && k !in opt.eraseDashes && y - (OmrGeometry.TIMING_Y0 + k * OmrGeometry.TIMING_PITCH) < OmrGeometry.TIMING_H) return true
        }
        return false
    }

    private fun render(): GrayImage {
        val w = opt.imgW
        val hh = opt.imgH
        val inv = h.inverse()!!
        val out = FloatArray(w * hh)
        val ss = 2
        for (y in 0 until hh) for (x in 0 until w) {
            var sum = 0.0
            for (j in 0 until ss) for (i in 0 until ss) {
                val m = inv.map(x + (i + 0.5) / ss, y + (j + 0.5) / ss)
                val onPaper = m.x in 0.0..210.0 && m.y in 0.0..297.0
                val pen = if (onPaper) penValue(m.x, m.y) else -1
                var v = if (!onPaper) opt.table.toDouble() else if (pen >= 0) pen.toDouble() else if (dark(m.x, m.y)) 30.0 else 225.0
                if (opt.shadow > 0) {
                    val t = (x.toDouble() / w + y.toDouble() / hh) / 2
                    v *= 1 - opt.shadow * t
                }
                sum += v
            }
            out[y * w + x] = (sum / (ss * ss)).toFloat()
        }
        var data = out
        if (opt.blurPx > 0) data = boxBlur(data, w, hh, opt.blurPx)
        val rnd = Random(opt.seed * 7 + 3)
        val bytes = ByteArray(w * hh)
        for (i in data.indices) {
            var v = data[i].toDouble()
            if (opt.noise > 0) v += rnd.nextGaussian() * opt.noise
            bytes[i] = v.toInt().coerceIn(0, 255).toByte()
        }
        return GrayImage(w, hh, bytes)
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            var n = 0
            for (k in -r..r) { val xx = x + k; if (xx in 0 until w) { s += src[y * w + xx]; n++ } }
            tmp[y * w + x] = s / n
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            var n = 0
            for (k in -r..r) { val yy = y + k; if (yy in 0 until h) { s += tmp[yy * w + x]; n++ } }
            out[y * w + x] = s / n
        }
        return out
    }
}
