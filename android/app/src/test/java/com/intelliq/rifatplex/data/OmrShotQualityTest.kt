package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmrShotQualityTest {
    private fun ok(o: SyntheticOptions): Pair<GrayImage, AlignResult.Ok> {
        val img = SyntheticSheet(o).image
        val r = OmrAlign.align(img)
        assertTrue("align failed: " + (r as? AlignResult.Failed)?.detail, r is AlignResult.Ok)
        return img to (r as AlignResult.Ok)
    }

    @Test
    fun sharpPhotoHasNarrowEdges() {
        val (img, a) = ok(SyntheticOptions())
        val w = OmrShotQuality.edgeWidthMm(img, a.toImage)
        assertNotNull(w)
        assertTrue("edge $w mm", w!! < OmrShotQuality.MAX_EDGE_MM)
    }

    @Test
    fun blurGrowsTheEdgeWidth() {
        val sharp = ok(SyntheticOptions()).let { OmrShotQuality.edgeWidthMm(it.first, it.second.toImage)!! }
        val soft = ok(SyntheticOptions(blurPx = 4)).let { OmrShotQuality.edgeWidthMm(it.first, it.second.toImage)!! }
        assertTrue("sharp $sharp soft $soft", soft > sharp * 2)
        assertTrue("soft $soft", soft > OmrShotQuality.MAX_EDGE_MM)
    }

    @Test
    fun lightBlurIsAccepted() {
        val (img, a) = ok(SyntheticOptions(blurPx = 1))
        assertTrue(OmrShotQuality.edgeWidthMm(img, a.toImage)!! < OmrShotQuality.MAX_EDGE_MM)
    }

    @Test
    fun tiltedAndRotatedPhotoStillMeasures() {
        val (img, a) = ok(SyntheticOptions(tiltDeg = 7.0, quarterTurns = 2))
        assertTrue(OmrShotQuality.edgeWidthMm(img, a.toImage)!! < OmrShotQuality.MAX_EDGE_MM)
    }

    @Test
    fun sheetWithoutQrIsReported() {
        val (img, a) = ok(SyntheticOptions())
        assertEquals(ShotIssue.NO_QR, OmrShotQuality.issue(img, a))
    }

    @Test
    fun foreignQrIsReported() {
        val (img, a) = ok(SyntheticOptions(qr = "hello world"))
        assertEquals(ShotIssue.FOREIGN_QR, OmrShotQuality.issue(img, a))
    }

    @Test
    fun everyIssueHasAMessageKey() {
        for (i in ShotIssue.values()) assertTrue(OmrShotQuality.key(i).startsWith("live."))
    }

    @Test
    fun flatPhotoCannotBeMeasured() {
        val img = GrayImage(1800, 2400, ByteArray(1800 * 2400) { 200.toByte() })
        val a = ok(SyntheticOptions()).second
        assertNull(OmrShotQuality.edgeWidthMm(img, a.toImage))
    }
}
