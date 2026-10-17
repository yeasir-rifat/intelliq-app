package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The fixed look of the OMR sheet and the answer sheet must not drift (see [SheetStyle]). */
class SheetStyleTest {
    @Test
    fun marginIsThreeQuartersOfAnInch() {
        assertEquals(19.05f, SheetStyle.MARGIN_MM, 0.001f)
    }

    @Test
    fun sizesAreBody10Heading14Sub12() {
        assertEquals(10f, SheetStyle.BODY_PT, 0f)
        assertEquals(14f, SheetStyle.HEADING_PT, 0f)
        assertEquals(12f, SheetStyle.SUBHEADING_PT, 0f)
    }

    @Test
    fun pageIsOpaqueWhite() {
        assertEquals(0xFFFFFFFFL, SheetStyle.PAGE_COLOR_ARGB)
    }
}
