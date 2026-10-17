package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Step 4.1 guard: the expected numbers below were read from the vector data of the reference sheet (Omr-Sheet-For-SSC,
 * 50 answers, A4). If one of these tests fails, the printed sheet and the scanner no longer agree - bump
 * OmrGeometry.VERSION and update the numbers on purpose, never by accident.
 */
class OmrGeometryTest {
    private val tol = 0.005f // the reference PDF gives 3 decimals

    @Test
    fun answerBubblesMatchTheReferenceSheet() {
        val first = OmrGeometry.answerBubble(0, 0, 0)
        assertEquals(33.753f, first.x, tol)
        assertEquals(60.183f, first.y, tol)
        val last = OmrGeometry.answerBubble(1, 24, 3)
        assertEquals(91.651f, last.x, tol)
        assertEquals(267.172f, last.y, tol)
        // slot numbering: 0..24 left block, 25..49 right block
        assertEquals(first, OmrGeometry.answerBubbleOfSlot(0, 0))
        assertEquals(last, OmrGeometry.answerBubbleOfSlot(49, 3))
        assertEquals(OmrGeometry.answerBubble(1, 0, 2), OmrGeometry.answerBubbleOfSlot(25, 2))
    }

    @Test
    fun scannerMarks() {
        val corners = OmrGeometry.cornerMarks()
        assertEquals(4, corners.size)
        assertEquals(13.471f, corners[0].x, tol)
        assertEquals(14.659f, corners[0].y, tol)
        assertEquals(191.101f, corners[3].x, tol)
        assertEquals(277.540f, corners[3].y, tol)
        assertEquals(4.801f, corners[0].w, tol)
        val dashes = OmrGeometry.timingDashes()
        assertEquals(52, dashes.size)
        assertEquals(37.066f, dashes.first().y, tol)
        assertEquals(266.890f, dashes.last().y, 0.01f)
    }

    @Test
    fun noAnswerBubblesTouchEachOtherOrLeaveThePage() {
        val pts = (0 until OmrGeometry.ROWS_PER_PAGE).flatMap { s -> (0 until OmrGeometry.OPTIONS).map { o -> OmrGeometry.answerBubbleOfSlot(s, o) } }
        assertEquals(200, pts.size)
        for (p in pts) {
            assertTrue(p.x - OmrGeometry.RING_R > 0f && p.x + OmrGeometry.RING_R < OmrGeometry.PAGE_W_MM)
            assertTrue(p.y - OmrGeometry.RING_R > 0f && p.y + OmrGeometry.RING_R < OmrGeometry.PAGE_H_MM)
        }
        for (i in pts.indices) for (j in i + 1 until pts.size) {
            assertTrue("bubbles $i and $j overlap", hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y) > 2f * OmrGeometry.RING_R)
        }
    }

    @Test
    fun rollGridDefault() {
        val g = OmrGeometry.rollGrid(OmrSettings())!!
        assertEquals(6, g.cols)
        assertEquals(108.339f, g.bubble(0, 0).x, tol)
        assertEquals(65.929f, g.bubble(0, 0).y, tol)
        assertEquals(135.897f, g.bubble(5, 9).x, tol)
        assertEquals(118.670f, g.bubble(5, 9).y, tol)
        assertEquals(38.88f, g.labelBox.w, 0.02f)
        assertEquals(123.09f, g.bottom, 0.02f)
    }

    @Test
    fun automaticModeIgnoresCustomValues() {
        assertEquals(6, OmrGeometry.rollGrid(OmrSettings(mode = OMR_AUTO, rollDigits = 3))!!.cols)
        assertEquals(3, OmrGeometry.rollGrid(OmrSettings(mode = OMR_CUSTOM, rollDigits = 3))!!.cols)
        assertNotNull(OmrGeometry.rollGrid(OmrSettings(mode = OMR_AUTO, showRoll = false)))
        assertNull(OmrGeometry.rollGrid(OmrSettings(mode = OMR_CUSTOM, showRoll = false)))
        assertNull(OmrGeometry.setGrid(OmrSettings(mode = OMR_CUSTOM, showSet = false), emptyList()))
        assertNull(OmrGeometry.regGrid(OmrSettings(mode = OMR_CUSTOM, showReg = false)))
        assertNull(OmrGeometry.subjectGrid(OmrSettings(mode = OMR_CUSTOM, showSubject = false)))
    }

    @Test
    fun setGrid() {
        val g = OmrGeometry.setGrid(OmrSettings(), listOf("A", "B", "C", "D"))!!
        assertEquals(listOf(0, 4, 0), g.labelCounts)
        assertEquals(156.503f, g.bubble(1, 0).x, tol)
        assertEquals(65.929f, g.bubble(1, 0).y, 0.02f) // the reference's set rows differ from the roll rows by up to 0.02 mm
        assertEquals(87.578f, g.bottom, 0.02f)
        // number of bubbles: blank names are ignored, at most 10, 4 when nothing is named
        assertEquals(4, OmrGeometry.setBubbleCount(emptyList()))
        assertEquals(4, OmrGeometry.setBubbleCount(listOf(" ", "")))
        assertEquals(2, OmrGeometry.setBubbleCount(listOf("A", " ", "B")))
        assertEquals(10, OmrGeometry.setBubbleCount((1..12).map { it.toString() }))
    }

    @Test
    fun registrationAndSubjectGrids() {
        val reg = OmrGeometry.regGrid(OmrSettings())!!
        assertEquals(10, reg.cols)
        assertEquals(60.878f, reg.labelBox.w, tol)
        assertEquals(157.920f, reg.bubble(9, 9).x, tol)
        assertEquals(197.755f, reg.bubble(9, 9).y, tol)
        val sub = OmrGeometry.subjectGrid(OmrSettings())!!
        assertEquals(listOf(4, 10, 10), sub.labelCounts) // first digit only goes 0..3 on a three-digit code
        assertEquals(29.856f, sub.colHeight(0), tol)
        assertEquals(64.878f, sub.colHeight(1), tol)
        assertEquals(171.589f, sub.gx, tol)
        assertEquals(174.345f, sub.bubble(0, 0).x, tol)
        assertEquals(144.910f, sub.bubble(0, 0).y, tol)
        assertEquals(168.736f, sub.labelBox.x, tol)
        assertEquals(listOf(10, 10), OmrGeometry.subjectGrid(OmrSettings(mode = OMR_CUSTOM, subjectDigits = 2))!!.labelCounts)
    }
}
