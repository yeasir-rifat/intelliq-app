package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 4.5: marks, statuses and the choice of the answer key. No images here: the reading is faked row by row. */
class OmrGradeTest {
    private val id = "K7M2QX9A"

    private fun row(state: MarkState, vararg m: Int): BubbleSet {
        val d = DoubleArray(4) { if (it in m) 0.9 else 0.02 }
        return BubbleSet(d, state, m.toSet())
    }

    private fun sheet(rows: Map<Int, BubbleSet>, roll: String = "123456", qr: QrStatus = QrStatus.Found(QrPayload(id, 0, 1, 0, 1)), lowContrast: Boolean = false): OmrSheetRead {
        val all = (0 until 50).map { rows[it] ?: row(MarkState.BLANK) }
        return OmrSheetRead(
            qr, all, DigitsRead(roll, emptyList()), null, null, null, null, 220.0, 30.0, lowContrast, 0, 0,
        )
    }

    private fun snap(n: Int, correct: (Int) -> Set<Int> = { setOf(it % 4) }, set: Int? = 0, marks: Double = 1.0, neg: Double = 0.0) =
        AnswerKeySnapshot(id, set, 1, (1..n).map { KeyItem(it, correct(it - 1)) }, marks, neg, 0L)

    @Test
    fun basicScore() {
        val s = snap(5)
        val read = sheet(mapOf(0 to row(MarkState.FILLED, 0), 1 to row(MarkState.FILLED, 3), 2 to row(MarkState.FILLED, 2)))
        val r = OmrGrade.grade(read, s)
        assertEquals(5, r.rows.size) // rows after the last MCQ are ignored
        assertEquals(2, r.correct); assertEquals(1, r.wrong); assertEquals(2, r.blank)
        assertEquals(2.0, r.score, 1e-9); assertEquals(5.0, r.total, 1e-9)
        assertEquals("123456", r.roll)
        assertTrue(r.flags.isEmpty())
    }

    @Test
    fun negativeMarkingNeverOnBlank() {
        val s = snap(4, neg = 0.25, marks = 2.0)
        val read = sheet(mapOf(0 to row(MarkState.FILLED, 0), 1 to row(MarkState.FILLED, 0), 2 to row(MarkState.FILLED, 0)))
        val r = OmrGrade.grade(read, s)
        // Q1 right (+2), Q2 wrong (-0.25), Q3 wrong (-0.25), Q4 blank (0)
        assertEquals(1.5, r.score, 1e-9); assertEquals(8.0, r.total, 1e-9)
    }

    @Test
    fun multiAndUncertain() {
        val s = snap(4, neg = 0.5)
        val read = sheet(mapOf(0 to row(MarkState.MULTI, 0, 1), 1 to row(MarkState.UNCERTAIN, 1), 2 to row(MarkState.FILLED, 2)))
        val r = OmrGrade.grade(read, s)
        assertEquals(QStatus.MULTI, r.rows[0].status); assertEquals(QStatus.UNCERTAIN, r.rows[1].status)
        assertEquals(1.0, r.score, 1e-9) // only Q3; no deduction for multi / uncertain by default
        assertEquals(2, r.review.size)
        assertTrue(OmrFlag.NEEDS_REVIEW in r.flags)
        val r2 = OmrGrade.grade(read, s, options = GradeOptions(penalizeMulti = true))
        assertEquals(0.5, r2.score, 1e-9)
    }

    @Test
    fun teacherOverrideResolvesUncertain() {
        val s = snap(3)
        val read = sheet(mapOf(1 to row(MarkState.UNCERTAIN, 1)))
        val r = OmrGrade.grade(read, s, overrides = mapOf(1 to setOf(1), 2 to setOf(0)))
        assertEquals(QStatus.CORRECT, r.rows[1].status); assertTrue(r.rows[1].overridden)
        assertEquals(QStatus.WRONG, r.rows[2].status) // Q3's right option is 2
        assertTrue(OmrFlag.NEEDS_REVIEW !in r.flags)
        assertEquals(1.0, r.score, 1e-9)
    }

    @Test
    fun severalRightOptionsMustAllBeFilled() {
        val s = snap(2, correct = { if (it == 0) setOf(0, 2) else setOf(1) })
        val read = sheet(mapOf(0 to row(MarkState.MULTI, 0, 2), 1 to row(MarkState.MULTI, 1, 3)))
        val r = OmrGrade.grade(read, s)
        assertEquals(QStatus.CORRECT, r.rows[0].status)
        assertEquals(QStatus.MULTI, r.rows[1].status)
        val r2 = OmrGrade.grade(sheet(mapOf(0 to row(MarkState.FILLED, 0))), s)
        assertEquals(QStatus.WRONG, r2.rows[0].status) // only half of the right options
    }

    @Test
    fun keyWithoutAnswerIsNotCounted() {
        val s = snap(3, correct = { if (it == 1) emptySet() else if (it == 2) setOf(5) else setOf(0) })
        val r = OmrGrade.grade(sheet(mapOf(0 to row(MarkState.FILLED, 0), 1 to row(MarkState.FILLED, 1))), s)
        assertEquals(QStatus.NO_KEY, r.rows[1].status); assertEquals(QStatus.UNMARKABLE, r.rows[2].status)
        assertEquals(1.0, r.total, 1e-9); assertEquals(1.0, r.score, 1e-9)
        assertTrue(OmrFlag.KEY_INCOMPLETE in r.flags)
    }

    @Test
    fun secondSheetUsesItsOwnItems() {
        val s = snap(60)
        val read = sheet(mapOf(0 to row(MarkState.FILLED, 0)), qr = QrStatus.Found(QrPayload(id, 0, 1, 1, 2)))
        val r = OmrGrade.grade(read, s) // page 1 comes from the QR code: items 51..60
        assertEquals(10, r.rows.size)
        assertEquals(51, r.rows[0].printedNumber)
        assertEquals(2, r.pages)
        assertEquals(QStatus.WRONG, r.rows[0].status) // Q51's right option is (50 % 4) = 2
    }

    @Test
    fun flagsFromTheReader() {
        val s = snap(2)
        val r = OmrGrade.grade(sheet(emptyMap(), roll = "12?456", qr = QrStatus.None, lowContrast = true), s, page = 0, pages = 1)
        assertTrue(OmrFlag.QR_MISSING in r.flags); assertTrue(OmrFlag.LOW_CONTRAST in r.flags); assertTrue(OmrFlag.ROLL_INCOMPLETE in r.flags)
    }

    @Test
    fun pickSnapshot() {
        val none = snap(2, set = null)
        val a = snap(2, set = 0)
        val b = snap(2, set = 1)
        // QR names the set
        assertEquals(b, OmrGrade.pickSnapshot(listOf(a, b), 1, null).snapshot)
        val mis = OmrGrade.pickSnapshot(listOf(a, b), 1, 0)
        assertEquals(b, mis.snapshot); assertTrue(OmrFlag.SET_MISMATCH in mis.flags)
        // QR has no set; the exam has none either
        assertEquals(none, OmrGrade.pickSnapshot(listOf(none), null, null).snapshot)
        // QR has no set; the paper has sets: the student's bubble decides
        assertEquals(a, OmrGrade.pickSnapshot(listOf(a, b), null, 0).snapshot)
        val need = OmrGrade.pickSnapshot(listOf(a, b), null, null)
        assertNull(need.snapshot); assertTrue(OmrFlag.SET_NEEDED in need.flags)
        // nothing on this device
        val ns = OmrGrade.pickSnapshot(emptyList(), 0, null)
        assertNull(ns.snapshot); assertTrue(OmrFlag.NO_SNAPSHOT in ns.flags)
        assertNotNull(OmrGrade.pickSnapshot(listOf(a), 0, null).snapshot)
    }
}
