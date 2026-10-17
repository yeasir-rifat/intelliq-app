package com.intelliq.rifatplex.data

/**
 * What the QR code of an OMR sheet says (step 4.2): `IQ1;<examId>;<setIndex>;<geomVersion>;<page>;<pages>`,
 * for example `IQ1;K7M2QX9A;2;1;0;1` (20 characters).
 *
 *  - [examId]       permanent id of the exam ([ExamId])
 *  - [setIndex]     0..9 = the sheet belongs to that set (position among the named sets); null = no set, written `-`
 *  - [geomVersion]  [OmrGeometry.VERSION] the sheet was printed with
 *  - [page]/[pages] 0-based number of this sheet and the number of sheets (51+ MCQs give more than one sheet)
 *
 * The roll number is NOT in the code: every student gets the same sheet and writes the roll in the grid.
 * Plain ASCII, so a code of this size is always a QR version 2 (25 x 25 modules).
 */
data class QrPayload(
    val examId: String,
    val setIndex: Int?,
    val geomVersion: Int,
    val page: Int,
    val pages: Int,
) {
    /** The text in the QR code. Only call it on a valid payload (see [forSheet] / [decode]). */
    fun encode(): String = "$PREFIX;$examId;${setIndex ?: NO_SET};$geomVersion;$page;$pages"

    companion object {
        const val PREFIX = "IQ1"
        const val NO_SET = "-"
        const val MAX_PAGES = 99
        const val MAX_SET_INDEX = 9

        private fun valid(examId: String, setIndex: Int?, geomVersion: Int, page: Int, pages: Int): Boolean =
            ExamId.isValid(examId) &&
                (setIndex == null || setIndex in 0..MAX_SET_INDEX) &&
                geomVersion in 1..99 &&
                pages in 1..MAX_PAGES &&
                page in 0 until pages

        /** The payload of one printed sheet, or null when a value is out of range (no QR is printed then). */
        fun forSheet(examId: String, setIndex: Int?, page: Int, pages: Int): QrPayload? =
            if (valid(examId, setIndex, OmrGeometry.VERSION, page, pages)) {
                QrPayload(examId, setIndex, OmrGeometry.VERSION, page, pages)
            } else null

        /** Does the text look like one of our codes (any version)? Lets the scanner say "other version of the sheet". */
        fun isIntelliQ(text: String): Boolean = text.startsWith("IQ")

        /** Reads the text of a QR code. Null when it is not exactly the format of [encode] (so `IQ2;...` is null too). */
        fun decode(text: String): QrPayload? {
            val p = text.split(';')
            if (p.size != 6 || p[0] != PREFIX) return null
            val set: Int? = if (p[2] == NO_SET) null else (p[2].toIntOrNull() ?: return null)
            val v = QrPayload(
                examId = p[1],
                setIndex = set,
                geomVersion = p[3].toIntOrNull() ?: return null,
                page = p[4].toIntOrNull() ?: return null,
                pages = p[5].toIntOrNull() ?: return null,
            )
            if (!valid(v.examId, v.setIndex, v.geomVersion, v.page, v.pages)) return null
            return if (v.encode() == text) v else null // refuses "01", "+1", " 1" and other non-canonical forms
        }
    }
}
