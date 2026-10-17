package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 4.2: the QR box sits in free space of the sheet: inside the 15 mm margin and clear of everything else printed. */
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
    fun boxIsInsideTheMarginOfFifteenMillimetres() {
        assertTrue(box.x >= 15f)
        assertTrue(box.y >= 15f)
        assertTrue(box.x + box.w <= OmrGeometry.PAGE_W_MM - 15f)
        assertTrue(box.y + box.h <= OmrGeometry.PAGE_H_MM - 15f)
    }

    @Test
    fun boxDoesNotTouchTheCornerSquares() {
        OmrGeometry.cornerMarks().forEach { assertTrue("corner mark $it", !overlap(box, it)) }
        // and it keeps at least 5 mm from the top-right one so a corner finder never mistakes it
        val tr = OmrGeometry.cornerMarks()[1]
        assertTrue(box.y - (tr.y + tr.h) >= 5f)
    }

    @Test
    fun boxIsAboveTheLabelBoxesAndTheInvigilatorBox() {
        val widest = OmrSettings(mode = OMR_CUSTOM, rollDigits = 8, regDigits = 12, subjectDigits = 4)
        val setCodes = (1..10).map { "S$it" }
        val top = listOfNotNull(
            OmrGeometry.rollGrid(widest)?.labelBox, OmrGeometry.setGrid(widest, setCodes)?.labelBox,
            OmrGeometry.regGrid(widest)?.labelBox, OmrGeometry.subjectGrid(widest)?.labelBox,
        )
        top.forEach { assertTrue("label box $it", !overlap(box, it)) }
        assertTrue("invigilator box starts at ${OmrGeometry.HEAD_TOP}", box.y + box.h < OmrGeometry.HEAD_TOP)
        val invigilator = RectMm(171.68f, OmrGeometry.HEAD_TOP, 19.44f, 72.337f)
        assertTrue(!overlap(box, invigilator))
        // at least 2 mm of free page between the white box and the printed frame below it
        assertTrue(OmrGeometry.HEAD_TOP - (box.y + box.h) >= 2f)
    }

    @Test
    fun boxIsRightOfTheBannersAndTheTitleIsKeptShortEnough() {
        val bannerRight = 49.14f + 111.38f // x 160.52, y 33.9 .. 45.2
        assertTrue(box.x - bannerRight >= 10f)
        assertTrue("title ends at ${105f + OmrGeometry.TITLE_MAX_W / 2f}", 105f + OmrGeometry.TITLE_MAX_W / 2f < box.x)
    }

    @Test
    fun yearBoxesAndExamLineEndAboveTheBox() {
        // exam line and year boxes: y 22.72 .. 27.8; they may reach x 191 but are above the box
        assertTrue(27.8f < box.y)
    }

    @Test
    fun boxIsClearOfTheAnswerBlocks() {
        for (b in 0 until OmrGeometry.BLOCKS) {
            val block = RectMm(OmrGeometry.BLOCK_X[b], OmrGeometry.HEAD_TOP, OmrGeometry.BLOCK_W, 220f)
            assertTrue(!overlap(box, block))
        }
    }
}
