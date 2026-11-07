package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The QR box sits in free space of the 200-answer sheet: clear of the corner squares, the grids, the boxes and the table. */
class OmrQrGeometryTest {
    private val box = OmrGeometry.qrBox()

    private fun overlap(a: RectMm, b: RectMm): Boolean =
        a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

    @Test
    fun boxIsSquareAndTheCodeInsideKeepsTheQuietZone() {
        assertEquals(18f, box.w, 0f)
        assertEquals(18f, box.h, 0f)
        assertEquals(14f, OmrGeometry.QR_CODE_MM, 0f)
        assertEquals(2f, OmrGeometry.QR_QUIET_MM, 0f)
    }

    @Test
    fun boxIsInsideThePage() {
        assertTrue(box.x >= 15f)
        assertTrue(box.x + box.w <= OmrGeometry.PAGE_W_MM - 15f)
        assertTrue(box.y + box.h <= OmrGeometry.PAGE_H_MM - 15f)
    }

    @Test
    fun boxDoesNotTouchTheCornerSquares() {
        OmrGeometry.cornerMarks().forEach { assertTrue("corner mark $it", !overlap(box, it)) }
        val tr = OmrGeometry.cornerMarks()[1]
        assertTrue(tr.x - (box.x + box.w) >= 15f)
    }

    @Test
    fun boxIsBelowTheHeaderBoxesAndAboveTheTable() {
        val boxes = listOf(OmrGeometry.NAME_BOX, OmrGeometry.DISTRICT_BOX, OmrGeometry.SIGN_BOX, OmrGeometry.INVIG_BOX)
        boxes.forEach { assertTrue(!overlap(box, it)) }
        assertTrue(OmrGeometry.HEAD_TOP - (box.y + box.h) >= 2f)
        assertTrue(box.y - (OmrGeometry.DISTRICT_BOX.y + OmrGeometry.DISTRICT_BOX.h) >= 0f)
        for (b in 0 until OmrGeometry.BLOCKS) {
            val block = RectMm(OmrGeometry.BLOCK_X[b], OmrGeometry.HEAD_TOP, OmrGeometry.BLOCK_W, OmrGeometry.TABLE_BOTTOM - OmrGeometry.HEAD_TOP)
            assertTrue(!overlap(box, block))
        }
    }

    @Test
    fun titleIsKeptShortEnough() {
        assertTrue(105f + OmrGeometry.TITLE_MAX_W / 2f < 300f)
    }
}
