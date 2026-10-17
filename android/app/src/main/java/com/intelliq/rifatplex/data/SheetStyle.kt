package com.intelliq.rifatplex.data

/**
 * Fixed look of the two scanner-related documents: the OMR sheet and the answer sheet ("উত্তরমালা").
 *
 * These numbers do NOT come from Settings. The OMR sheet is read by the scanner at fixed millimetre positions and the
 * answer sheet is the examiner's copy of it, so a user-chosen font, font size, page colour or margin could move or
 * resize things and break the measuring / evaluation. Nothing in [com.intelliq.rifatplex.data.DocSettings]
 * (fontFamily, fontSize, fontColor, pageColor) is read by either document any more.
 *
 * Fonts (see `sheetFonts` in ui/PaperFonts.kt): Bangla = SolaimanLipi, English = Times New Roman look (Tinos).
 * Page: white. Text colour: near-black.
 * Sizes: body 10 pt, heading 14 pt bold, sub-heading 12 pt bold.
 * Margin: 0.75 inch on all four sides.
 */
object SheetStyle {
    const val MM_PER_INCH = 25.4f

    /** Page margin on every side: 0.75 inch = 19.05 mm. */
    const val MARGIN_MM = 0.75f * MM_PER_INCH

    /** Body text, in points. */
    const val BODY_PT = 10f

    /** Heading (bold), in points. */
    const val HEADING_PT = 14f

    /** Sub-heading (bold), in points. */
    const val SUBHEADING_PT = 12f

    /** Page colour, 0xAARRGGBB: always white. */
    const val PAGE_COLOR_ARGB: Long = 0xFFFFFFFFL

    /** Text colour, 0xAARRGGBB: near-black (the same value as the default document text colour). */
    const val INK_ARGB: Long = 0xFF111111L
}
