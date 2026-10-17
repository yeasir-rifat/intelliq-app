package com.intelliq.rifatplex.data

import kotlin.math.abs
import kotlin.math.floor

/** What is wrong with a photo that was aligned fine (shown to the person right after the shot, who may retake it). */
internal enum class ShotIssue { BLUR, NO_QR, KEY_PAGE, FOREIGN_QR }

/**
 * Photo quality checks on top of a successful alignment (pure Kotlin).
 *  - Sharpness: the black corner squares are printed with razor-sharp edges, so the width of their edge in the photo (rise
 *    from 10 % to 90 % of the paper-to-ink contrast, measured along the sheet's x axis through all four squares, median)
 *    tells how blurred the photo is. It is measured in millimetres of the sheet, so it does not depend on the resolution.
 *  - QR code: it must be readable and ours, otherwise the sheet cannot be matched with its answer key.
 */
internal object OmrShotQuality {
    /** Widest accepted edge, mm. A sharp photo gives about 0.1 - 0.2 mm (one or two pixels). */
    const val MAX_EDGE_MM = 0.55
    private const val STEP = 0.05
    private const val HALF = 4.0 // mm each side of a square's centre (the square itself is 4.8 mm)

    fun issue(img: GrayImage, ok: AlignResult.Ok): ShotIssue? {
        val w = edgeWidthMm(img, ok.toImage)
        if (w != null && w > MAX_EDGE_MM) return ShotIssue.BLUR
        return when (OmrReader.readQr(img, ok.toImage)) {
            is QrStatus.Found -> null
            is QrStatus.KeyPage -> ShotIssue.KEY_PAGE
            is QrStatus.None -> ShotIssue.NO_QR
            else -> ShotIssue.FOREIGN_QR
        }
    }

    fun key(i: ShotIssue): String = when (i) {
        ShotIssue.BLUR -> "live.blur"
        ShotIssue.NO_QR -> "live.noQr"
        ShotIssue.KEY_PAGE -> "live.keyPage"
        ShotIssue.FOREIGN_QR -> "live.foreignQr"
    }

    /** Median edge width in mm over the corner squares, or null when it cannot be measured (too little contrast, off the photo). */
    fun edgeWidthMm(img: GrayImage, toImage: Homography): Double? {
        val widths = ArrayList<Double>()
        val n = (2 * HALF / STEP).toInt() + 1
        for (m in OmrGeometry.cornerMarks()) {
            val cx = (m.x + m.w / 2f).toDouble()
            val cy = (m.y + m.h / 2f).toDouble()
            val v = DoubleArray(n)
            var inside = true
            for (i in 0 until n) {
                val p = toImage.map(cx + (-HALF + i * STEP), cy)
                val g = grey(img, p.x, p.y)
                if (g == null) {
                    inside = false
                    break
                }
                v[i] = g
            }
            if (!inside) continue
            var paperSum = 0.0
            var paperN = 0
            var inkSum = 0.0
            var inkN = 0
            for (i in 0 until n) {
                val a = abs(-HALF + i * STEP)
                if (a >= 3.2) {
                    paperSum += v[i]
                    paperN++
                } else if (a <= 1.4) {
                    inkSum += v[i]
                    inkN++
                }
            }
            if (paperN == 0 || inkN == 0) continue
            val paper = paperSum / paperN
            val contrast = paper - inkSum / inkN
            if (contrast < 50) continue
            rise(v, paper, contrast, true)?.let { widths.add(it) }
            rise(v, paper, contrast, false)?.let { widths.add(it) }
        }
        if (widths.size < 3) return null
        widths.sort()
        return widths[widths.size / 2]
    }

    /** Distance (mm) between the 10 % and the 90 % point of the drop paper -> ink, scanning inwards from one end. */
    private fun rise(v: DoubleArray, paper: Double, contrast: Double, fromStart: Boolean): Double? {
        val hi = paper - 0.1 * contrast
        val lo = paper - 0.9 * contrast
        val mid = v.size / 2
        var a = -1
        var b = -1
        val range = if (fromStart) (0..mid) else (v.size - 1 downTo mid)
        for (i in range) {
            if (a < 0 && v[i] <= hi) a = i
            if (a >= 0 && v[i] <= lo) {
                b = i
                break
            }
        }
        if (a < 0 || b < 0) return null
        return abs(b - a) * STEP
    }

    /** Bilinear grey at continuous photo coordinates (pixel (i, j) has its centre at (i + 0.5, j + 0.5)); null outside the photo. */
    private fun grey(img: GrayImage, x: Double, y: Double): Double? {
        val fx = x - 0.5
        val fy = y - 0.5
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        if (x0 < 0 || y0 < 0 || x0 + 1 >= img.w || y0 + 1 >= img.h) return null
        val ax = fx - x0
        val ay = fy - y0
        val top = img.at(x0, y0) * (1 - ax) + img.at(x0 + 1, y0) * ax
        val bottom = img.at(x0, y0 + 1) * (1 - ax) + img.at(x0 + 1, y0 + 1) * ax
        return top * (1 - ay) + bottom * ay
    }
}
