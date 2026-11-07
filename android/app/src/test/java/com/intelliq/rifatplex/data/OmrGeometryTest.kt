package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Guard for the 200-answer sheet (geometry VERSION 2: 5 blocks of 40 answers). If one of these tests fails, the printed
 * sheet and the scanner no longer agree - bump OmrGeometry.VERSION and update the numbers on purpose, never by accident.
 */
class OmrGeometryTest {
    private val tol = 0.005f

    @Test
    fun versionAndCounts() {
        assertEquals(2, OmrGeometry.VERSION)
        assertEquals(200, OmrGeometry.ROWS_PER_PAGE)
        assertEquals(OmrGeometry.BLOCKS * OmrGeometry.ROWS_PER_BLOCK, OmrGeometry.ROWS_PER_PAGE)
        assertEquals(5, OmrGeometry.BLOCK_X.size)
        assertEquals(5, OmrGeometry.FIRST_BUBBLE_X.size)
        assertEquals(40, OmrGeometry.ANS_Y.size)
    }

    @Test
    fun answerBubbles() {
        val first = OmrGeometry.answerBubble(0, 0, 0)
        assertEquals(26.7f, first.x, tol)
        assertEquals(107.18f, first.y, tol)
        val last = OmrGeometry.answerBubble(4, 39, 3)
        assertEquals(OmrGeometry.BLOCK_X[4] + 7.0f + 1.7f + 3.0f + 18f, last.x, tol)
        assertEquals(105f + 4.36f * 39.5f, last.y, tol)
        assertEquals(first, OmrGeometry.answerBubbleOfSlot(0, 0))
        assertEquals(last, OmrGeometry.answerBubbleOfSlot(199, 3))
        assertEquals(OmrGeometry.answerBubble(1, 0, 2), OmrGeometry.answerBubbleOfSlot(40, 2))
        assertEquals(OmrGeometry.answerBubble(4, 0, 0), OmrGeometry.answerBubbleOfSlot(160, 0))
    }

    @Test
    fun scannerMarks() {
        val corners = OmrGeometry.cornerMarks()
        assertEquals(4, corners.size)
        assertEquals(8.0f, corners[0].x, tol)
        assertEquals(88.0f, corners[0].y, tol)
        assertEquals(197.2f, corners[3].x, tol)
        assertEquals(283.4f, corners[3].y, tol)
        assertEquals(4.8f, corners[0].w, tol)
        val dashes = OmrGeometry.timingDashes()
        assertEquals(40, dashes.size)
        assertEquals(95.0f, dashes.first().y, tol)
        assertEquals(95.0f + 39 * 4.5f, dashes.last().y, 0.01f)
        // the dashes sit between the two left squares, in the same column, and touch neither
        assertTrue(dashes.first().y > corners[0].y + corners[0].h + 1f)
        assertTrue(dashes.last().y + dashes.last().h < corners[2].y - 1f)
        assertEquals(corners[0].x, dashes.first().x, 0f)
    }

    @Test
    fun marksShapeIsAlmostSquare() {
        assertEquals(195.4f / 189.2f, OmrGeometry.MARKS_ASPECT, 0.001f)
        assertTrue(OmrGeometry.MARKS_ASPECT in 1.0f..1.1f)
    }

    @Test
    fun noAnswerBubblesTouchEachOtherOrLeaveThePage() {
        val pts = (0 until OmrGeometry.ROWS_PER_PAGE).flatMap { s -> (0 until OmrGeometry.OPTIONS).map { o -> OmrGeometry.answerBubbleOfSlot(s, o) } }
        assertEquals(800, pts.size)
        for (p in pts) {
            assertTrue(p.x - OmrGeometry.RING_R > 0f && p.x + OmrGeometry.RING_R < OmrGeometry.PAGE_W_MM)
            assertTrue(p.y - OmrGeometry.RING_R > 0f && p.y + OmrGeometry.RING_R < OmrGeometry.PAGE_H_MM)
        }
        // nearest neighbours only (same row, or the row above): every bubble keeps a free gap to the next ring
        for (i in pts.indices) for (j in i + 1 until minOf(pts.size, i + 12)) {
            assertTrue("bubbles $i and $j overlap", hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y) > 2f * OmrGeometry.RING_R + 0.8f)
        }
    }

    @Test
    fun tableFitsBetweenTheScannerMarks() {
        val c = OmrGeometry.cornerMarks()
        assertTrue(OmrGeometry.TABLE_BOTTOM < c[2].y - 2f)
        assertTrue(OmrGeometry.HEAD_TOP > c[0].y + c[0].h)
        assertTrue(OmrGeometry.BLOCK_X.first() > c[0].x + c[0].w)
        assertTrue(OmrGeometry.BLOCK_X.last() + OmrGeometry.BLOCK_W < c[1].x)
        for (b in 0 until 4) assertTrue(OmrGeometry.BLOCK_X[b] + OmrGeometry.BLOCK_W < OmrGeometry.BLOCK_X[b + 1])
    }

    @Test
    fun rollGridDefault() {
        val g = OmrGeometry.rollGrid(OmrSettings())!!
        assertEquals(8, g.cols)
        assertEquals(49.0f + 3.1f, g.bubble(0, 0).x, tol)
        assertEquals(34.5f + 6.9f, g.bubble(0, 0).y, tol)
        assertEquals(49.0f + 7 * 6.2f + 3.1f, g.bubble(7, 9).x, tol)
        assertEquals(34.5f + 6.9f + 9 * 3.55f, g.bubble(7, 9).y, tol)
        assertEquals(8 * 6.2f, g.labelBox.w, 0.02f)
        assertTrue(g.bottom < 78f)
    }

    @Test
    fun automaticModeIgnoresCustomValues() {
        assertEquals(8, OmrGeometry.rollGrid(OmrSettings(mode = OMR_AUTO, rollDigits = 3))!!.cols)
        assertEquals(3, OmrGeometry.rollGrid(OmrSettings(mode = OMR_CUSTOM, rollDigits = 3))!!.cols)
        assertNotNull(OmrGeometry.rollGrid(OmrSettings(mode = OMR_AUTO, showRoll = false)))
        assertNull(OmrGeometry.rollGrid(OmrSettings(mode = OMR_CUSTOM, showRoll = false)))
        assertNull(OmrGeometry.setGrid(OmrSettings(mode = OMR_CUSTOM, showSet = false), emptyList()))
        assertNull(OmrGeometry.regGrid(OmrSettings(mode = OMR_CUSTOM, showReg = false)))
        assertNull(OmrGeometry.subjectGrid(OmrSettings(mode = OMR_CUSTOM, showSubject = false)))
        assertNull(OmrGeometry.marksBox(OmrSettings(), emptyList()))
    }

    @Test
    fun setGrid() {
        val g = OmrGeometry.setGrid(OmrSettings(), listOf("A", "B", "C", "D"))!!
        assertEquals(listOf(4), g.labelCounts)
        assertEquals(105.5f, g.bubble(0, 0).x, tol)
        assertEquals(41.4f, g.bubble(0, 0).y, tol)
        assertEquals(4, OmrGeometry.setBubbleCount(emptyList()))
        assertEquals(4, OmrGeometry.setBubbleCount(listOf(" ", "")))
        assertEquals(2, OmrGeometry.setBubbleCount(listOf("A", " ", "B")))
        assertEquals(10, OmrGeometry.setBubbleCount((1..12).map { it.toString() }))
    }

    @Test
    fun smallGrids() {
        val centre = OmrGeometry.regGrid(OmrSettings())!!
        assertEquals(1, centre.cols)
        assertEquals(35.5f, centre.bubble(0, 0).x, tol)
        val sub = OmrGeometry.subjectGrid(OmrSettings())!!
        assertEquals(2, sub.cols)
        assertEquals(listOf(10, 10), sub.labelCounts)
        assertEquals(18.5f, sub.bubble(0, 0).x, tol)
        assertEquals(1, OmrGeometry.subjectGrid(OmrSettings(mode = OMR_CUSTOM, subjectDigits = 1))!!.cols)
        assertEquals(2, OmrGeometry.subjectGrid(OmrSettings(mode = OMR_CUSTOM, subjectDigits = 4))!!.cols)
    }

    @Test
    fun gridsDoNotOverlapEachOtherOrTheBoxes() {
        fun overlap(a: RectMm, b: RectMm) = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
        val wide = OmrSettings(mode = OMR_CUSTOM, rollDigits = 8, regDigits = 2, subjectDigits = 2)
        val setCodes = (1..10).map { "S$it" }
        val frames = listOfNotNull(
            OmrGeometry.subjectGrid(wide), OmrGeometry.regGrid(wide), OmrGeometry.rollGrid(wide), OmrGeometry.setGrid(wide, setCodes),
        ).map { RectMm(it.gx, OmrGeometry.GRID_LABEL_TOP, it.xs[it.cols], it.bottom - OmrGeometry.GRID_LABEL_TOP) }
        for (i in frames.indices) for (j in i + 1 until frames.size) assertTrue("grid $i / $j", !overlap(frames[i], frames[j]))
        val boxes = listOf(OmrGeometry.NAME_BOX, OmrGeometry.DISTRICT_BOX, OmrGeometry.SIGN_BOX, OmrGeometry.INVIG_BOX)
        for (f in frames) for (b in boxes) assertTrue("grid / box", !overlap(f, b))
        for (i in boxes.indices) for (j in i + 1 until boxes.size) assertTrue("box $i / $j", !overlap(boxes[i], boxes[j]))
        // the lowest grid bubble row and the answer table keep apart
        for (f in frames) assertTrue(f.y + f.h < 78f)
    }
}
