package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmrReportTest {
    private fun row(sheet: String, roll: String, score: Double, set: Int? = null, review: Boolean = false) =
        ReportRow(sheet, roll, set, score, 50.0, score.toInt(), 50 - score.toInt(), 0, review)

    @Test
    fun tiesShareRankAndNextRankSkips() {
        val r = OmrReport.rank(listOf(row("#1", "003", 40.0), row("#2", "001", 45.0), row("#3", "002", 40.0), row("#4", "004", 10.0)))
        assertEquals(listOf(1, 2, 2, 4), r.map { it.rank })
        assertEquals(listOf("001", "002", "003", "004"), r.map { it.row.roll })
    }

    @Test
    fun duplicateRollIsFlaggedButUnfilledRollIsNot() {
        val r = OmrReport.rank(listOf(row("#1", "101", 5.0), row("#2", "101", 6.0), row("#3", "1?1", 7.0), row("#4", "1?1", 8.0), row("#5", "-", 1.0), row("#6", "-", 2.0)))
        val dup = r.filter { it.duplicateRoll }.map { it.row.sheet }.toSet()
        assertEquals(setOf("#1", "#2"), dup)
        assertNull(OmrReport.rollKey("1?1"))
    }

    @Test
    fun numbersAreCleanAscii() {
        assertEquals("12", OmrReport.num(12.0))
        assertEquals("12.5", OmrReport.num(12.5))
        assertEquals("12.25", OmrReport.num(12.25))
        assertEquals("-0.25", OmrReport.num(-0.25))
        assertEquals("0.33", OmrReport.num(1.0 / 3))
        assertEquals("0", OmrReport.num(Double.NaN))
    }

    @Test
    fun csvHasBomHeaderQuotingAndFormulaGuard() {
        val rows = OmrReport.rank(listOf(row("#1", "=1+1", 3.5, set = 1, review = true), row("#2", "a,\"b\"", 2.0)))
        val csv = OmrReport.csv(rows)
        assertTrue(csv.startsWith("\uFEFF" + OmrReport.HEADER + "\r\n"))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(3, lines.size)
        assertEquals("1,#1,'=1+1,2,3.5,50,3,47,0,yes,needs review", lines[1])
        assertEquals("2,#2,\"a,\"\"b\"\"\",,2,50,2,48,0,no,", lines[2])
        assertFalse(lines[2].contains("duplicate"))
    }

    private fun sheetRow(sheet: String, roll: String, score: Double, page: Int, pages: Int, set: Int? = null) =
        ReportRow(sheet, roll, set, score, 50.0, score.toInt(), 50 - score.toInt(), 0, false, page, pages)

    @Test
    fun severalSheetsOfOneStudentAreOneRankedRowAndNoDuplicate() {
        val r = OmrReport.rank(
            listOf(
                sheetRow("#1", "001", 30.0, 0, 2), sheetRow("#2", "002", 45.0, 0, 2),
                sheetRow("#3", "001", 25.0, 1, 2), sheetRow("#4", "002", 40.0, 1, 2),
            ),
        )
        assertEquals(2, r.size)
        assertEquals(listOf("002", "001"), r.map { it.row.roll })
        assertEquals(85.0, r[0].row.score, 1e-9)
        assertEquals(55.0, r[1].row.score, 1e-9)
        assertEquals(100.0, r[1].row.total, 1e-9)
        assertEquals("#1+#3", r[1].row.sheet)
        assertTrue(r.none { it.duplicateRoll || it.row.review })
    }

    @Test
    fun missingSheetNeedsReviewAndRealDuplicateStaysFlagged() {
        val miss = OmrReport.rank(listOf(sheetRow("#1", "007", 30.0, 0, 2)))
        assertEquals(1, miss.size)
        assertTrue(miss[0].row.review)
        // the same page twice for one roll: not merged, both flagged
        val dup = OmrReport.rank(listOf(sheetRow("#1", "009", 30.0, 0, 2), sheetRow("#2", "009", 31.0, 0, 2)))
        assertEquals(2, dup.size)
        assertTrue(dup.all { it.duplicateRoll })
        // unreadable roll is never merged
        assertEquals(2, OmrReport.rank(listOf(sheetRow("#1", "0?1", 1.0, 0, 2), sheetRow("#2", "0?1", 1.0, 1, 2))).size)
        // different sets of the same roll are different students
        assertEquals(2, OmrReport.rank(listOf(sheetRow("#1", "5", 1.0, 0, 2, set = 0), sheetRow("#2", "5", 1.0, 0, 2, set = 1))).size)
    }
}
