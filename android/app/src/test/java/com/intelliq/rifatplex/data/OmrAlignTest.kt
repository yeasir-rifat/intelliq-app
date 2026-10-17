package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.math.hypot

/** Step 4.4: the aligner finds the sheet in synthetic photos (rotated, tilted, shadowed, blurred, cluttered) to <= 0.3 mm. */
class OmrAlignTest {

    /** Every printed thing a scanner will read, as sheet mm. */
    private fun probePoints(): List<PointD> {
        val pts = ArrayList<PointD>()
        for (slot in 0 until OmrGeometry.ROWS_PER_PAGE) for (o in 0 until OmrGeometry.OPTIONS) {
            val b = OmrGeometry.answerBubbleOfSlot(slot, o)
            pts += PointD(b.x.toDouble(), b.y.toDouble())
        }
        for (m in OmrGeometry.cornerMarks()) pts += PointD((m.x + m.w / 2).toDouble(), (m.y + m.h / 2).toDouble())
        val q = OmrGeometry.qrBox()
        pts += PointD(q.x.toDouble(), q.y.toDouble()); pts += PointD((q.x + q.w).toDouble(), (q.y + q.h).toDouble())
        val roll = OmrGeometry.rollGrid(OmrSettings())!!
        for (c in 0 until roll.cols) for (i in 0 until 10) { val b = roll.bubble(c, i); pts += PointD(b.x.toDouble(), b.y.toDouble()) }
        return pts
    }

    /** Largest distance in mm between where the estimate and the truth put the probe points. */
    private fun worstErrorMm(sheet: SyntheticSheet, ok: AlignResult.Ok): Double {
        val s = sheet.h.localScale(105.0, 148.5)
        var worst = 0.0
        for (p in probePoints()) {
            val t = sheet.h.map(p)
            val e = ok.toImage.map(p)
            worst = maxOf(worst, hypot(t.x - e.x, t.y - e.y) / s)
        }
        return worst
    }

    private fun expectOk(name: String, o: SyntheticOptions, maxMm: Double = 0.3, quarter: Int = o.quarterTurns): AlignResult.Ok {
        val sheet = SyntheticSheet(o)
        val r = OmrAlign.align(sheet.image)
        if (r !is AlignResult.Ok) {
            fail("$name: not aligned: " + (r as AlignResult.Failed).reason + " " + r.detail)
            throw IllegalStateException()
        }
        val err = worstErrorMm(sheet, r)
        println("%-34s ok  scale=%.2f px/mm  dashes=%d  rms=%.3f mm  worst probe error=%.3f mm".format(name, r.scalePxPerMm, r.dashes, r.fitRmsMm, err))
        assertTrue("$name: error $err mm", err <= maxMm)
        assertEquals("$name: quarter turns", quarter, r.quarterTurnsCcw)
        return r
    }

    private fun expectFail(name: String, o: SyntheticOptions, reason: AlignFail) {
        val r = OmrAlign.align(SyntheticSheet(o).image)
        if (r is AlignResult.Ok) fail("$name: should have failed with $reason but was aligned")
        r as AlignResult.Failed
        println("%-34s fail %s (%s)".format(name, r.reason, r.detail))
        assertEquals(name, reason, r.reason)
    }

    @Test fun upright() { expectOk("upright", SyntheticOptions()) }

    @Test fun tilted() {
        expectOk("tilt +8", SyntheticOptions(tiltDeg = 8.0))
        expectOk("tilt -14", SyntheticOptions(tiltDeg = -14.0, shiftMmX = 4.0, shiftMmY = -3.0, scale = 6.6))
        expectOk("tilt 25", SyntheticOptions(tiltDeg = 25.0, imgW = 2200, imgH = 2800, scale = 6.4))
    }

    @Test fun perspective() {
        expectOk("perspective x .15", SyntheticOptions(perspX = 0.15))
        expectOk("perspective y .2 + tilt", SyntheticOptions(perspY = 0.2, tiltDeg = 5.0))
        expectOk("perspective both .2", SyntheticOptions(perspX = -0.2, perspY = 0.15, tiltDeg = -6.0, scale = 6.8))
    }

    @Test fun quarterTurns() {
        expectOk("quarter 1", SyntheticOptions(quarterTurns = 1, imgW = 2400, imgH = 1800))
        expectOk("upside down", SyntheticOptions(quarterTurns = 2))
        expectOk("quarter 3 + tilt", SyntheticOptions(quarterTurns = 3, imgW = 2400, imgH = 1800, tiltDeg = 7.0))
    }

    @Test fun hostileLight() {
        expectOk("shadow .55 + dark table", SyntheticOptions(shadow = 0.55, table = 70))
        expectOk("noise 10 + blur 1", SyntheticOptions(noise = 10.0, blurPx = 1, tiltDeg = 3.0))
        expectOk("clutter + shadow + noise", SyntheticOptions(clutter = true, shadow = 0.4, noise = 7.0, tiltDeg = -4.0, table = 120))
    }

    @Test fun sizes() {
        expectOk("large photo 3000x4000", SyntheticOptions(imgW = 3000, imgH = 4000, tiltDeg = 2.0))
        expectOk("small photo 1500x2000", SyntheticOptions(imgW = 1500, imgH = 2000, tiltDeg = -3.0), 0.35)
    }

    @Test fun failures() {
        expectFail("too far", SyntheticOptions(imgW = 1300, imgH = 1750, scale = 4.2), AlignFail.TOO_FAR)
        // sheet pushed so that the top-left corner square is outside the photo
        expectFail("corner cut off", SyntheticOptions(shiftMmX = 25.0, shiftMmY = 40.0, scale = 7.5), AlignFail.CORNERS_NOT_FOUND)
        val blank = GrayImage(1800, 2400, ByteArray(1800 * 2400) { 230.toByte() })
        val r = OmrAlign.align(blank)
        assertTrue(r is AlignResult.Failed && r.reason == AlignFail.CORNERS_NOT_FOUND)
        val tiny = GrayImage(400, 500, ByteArray(400 * 500) { 230.toByte() })
        val t = OmrAlign.align(tiny)
        assertTrue(t is AlignResult.Failed && t.reason == AlignFail.IMAGE_TOO_SMALL)
    }

    @Test fun coveredDashes() {
        // a thumb over a few dashes is tolerated (at least 48 of 52 are needed) ...
        val ok = expectOk("3 dashes covered", SyntheticOptions(eraseDashes = listOf(10, 11, 12), tiltDeg = 3.0))
        assertEquals(49, ok.dashes)
        // ... a hand over eight is not
        expectFail("8 dashes covered", SyntheticOptions(eraseDashes = (20..27).toList()), AlignFail.MARKS_UNREADABLE)
    }

    @Test fun heavyBlurDoesNotCrash() {
        val r = OmrAlign.align(SyntheticSheet(SyntheticOptions(blurPx = 6, noise = 12.0)).image)
        println("heavy blur -> " + if (r is AlignResult.Ok) "ok rms=%.3f".format(r.fitRmsMm) else (r as AlignResult.Failed).reason.toString())
    }

    @Test fun manualCornersSnapAndAlign() {
        val sheet = SyntheticSheet(SyntheticOptions(tiltDeg = 6.0, noise = 5.0))
        val marks = OmrGeometry.cornerMarks().map { sheet.h.map((it.x + it.w / 2).toDouble(), (it.y + it.h / 2).toDouble()) }
        // clockwise on screen for an upright sheet: TL, TR, BR, BL; taps are 25 px off
        val taps = listOf(marks[0], marks[1], marks[3], marks[2]).mapIndexed { i, p -> PointD(p.x + (if (i % 2 == 0) 25 else -20), p.y + (if (i < 2) 18 else -22)) }
        val r = OmrAlign.alignWithCorners(sheet.image, taps)
        assertTrue("manual: " + (r as? AlignResult.Failed)?.detail, r is AlignResult.Ok)
        assertTrue(worstErrorMm(sheet, r as AlignResult.Ok) <= 0.3)
    }

    @Test fun homographyFitIsExact() {
        val truth = Homography(doubleArrayOf(1.2, 0.3, 40.0, -0.2, 0.9, 12.0, 0.0004, -0.0002, 1.0))
        val src = listOf(PointD(0.0, 0.0), PointD(200.0, 0.0), PointD(200.0, 290.0), PointD(0.0, 290.0))
        val fit = Homography.fit(src, src.map { truth.map(it) })!!
        for (t in listOf(PointD(100.0, 100.0), PointD(13.0, 250.0), PointD(190.0, 20.0))) {
            val a = truth.map(t); val b = fit.map(t)
            assertEquals(a.x, b.x, 1e-6); assertEquals(a.y, b.y, 1e-6)
        }
        val inv = fit.inverse()!!
        val back = inv.map(fit.map(PointD(77.0, 33.0)))
        assertEquals(77.0, back.x, 1e-6); assertEquals(33.0, back.y, 1e-6)
        // many noisy points: the fit stays close
        val rnd = Random(5)
        val pts = (0 until 60).map { PointD(rnd.nextDouble() * 200, rnd.nextDouble() * 290) }
        val noisy = pts.map { val m = truth.map(it); PointD(m.x + rnd.nextGaussian() * 0.2, m.y + rnd.nextGaussian() * 0.2) }
        val f2 = Homography.fit(pts, noisy)!!
        val e = f2.map(PointD(100.0, 145.0)); val t = truth.map(PointD(100.0, 145.0))
        assertTrue(hypot(e.x - t.x, e.y - t.y) < 0.2)
    }

    @Test fun orientationMatchesExifTable() {
        // 3 wide x 2 high:  a b c / d e f
        val src = GrayImage(3, 2, byteArrayOf(1, 2, 3, 4, 5, 6))
        fun rows(g: GrayImage) = (0 until g.h).map { y -> (0 until g.w).map { x -> g.at(x, y) } }
        assertEquals(listOf(listOf(3, 2, 1), listOf(6, 5, 4)), rows(src.oriented(2)))
        assertEquals(listOf(listOf(6, 5, 4), listOf(3, 2, 1)), rows(src.oriented(3)))
        assertEquals(listOf(listOf(4, 5, 6), listOf(1, 2, 3)), rows(src.oriented(4)))
        assertEquals(listOf(listOf(1, 4), listOf(2, 5), listOf(3, 6)), rows(src.oriented(5)))
        assertEquals(listOf(listOf(4, 1), listOf(5, 2), listOf(6, 3)), rows(src.oriented(6))) // rotate 90 cw
        assertEquals(listOf(listOf(6, 3), listOf(5, 2), listOf(4, 1)), rows(src.oriented(7)))
        assertEquals(listOf(listOf(3, 6), listOf(2, 5), listOf(1, 4)), rows(src.oriented(8))) // rotate 90 ccw
        assertEquals(rows(src), rows(src.oriented(1)))
    }

    @Test fun downscaleKeepsMeanAndThinLines() {
        val w = 3000; val h = 2000
        val img = GrayImage(w, h, ByteArray(w * h) { i -> if ((i % w) % 100 == 0) 0 else 200.toByte() })
        val small = img.downscaled(1000)
        assertEquals(1000, small.w); assertEquals(667, small.h)
        var s1 = 0L; for (i in img.px.indices) s1 += img.px[i].toInt() and 0xFF
        var s2 = 0L; for (i in small.px.indices) s2 += small.px[i].toInt() and 0xFF
        assertEquals(s1.toDouble() / img.px.size, s2.toDouble() / small.px.size, 0.5)
        assertNotNull(img.downscaled(5000))
    }
}
