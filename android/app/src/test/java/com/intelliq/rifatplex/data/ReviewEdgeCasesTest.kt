package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edge cases found while reviewing step 4.9: no MCQ, more than 100 MCQs over several sheets, damaged saved data. */
class ReviewEdgeCasesTest {
    private val id = "K7M2QX9A"

    private fun mcq(n: Int, vararg correct: Int) =
        Question(id = "q$n", text = "q$n", options = (0 until 4).map { Option("o$it", "o$it", it in correct) })

    @Test
    fun noMcqGivesOneEmptySheetAndNoQr() {
        val snap = AnswerKey.build(id, null, emptyList(), emptyMap(), GradingRules(), now = 1L)
        assertEquals(0, snap.items.size)
        assertEquals(1, snap.sheets)
        assertNull(snap.itemAt(0, 0))
        assertEquals(0.0, snap.maxScore, 0.0)
        assertEquals(listOf(emptyList<Int>()), omrRows(emptyList()))
        assertNull(KeyQrPayload.forPage(id, null, 0, 1, emptyList(), GradingRules()))
        assertTrue(KeyQrPayload.assemble(emptyList()) is KeyQrAssembly.Mismatch)
    }

    @Test
    fun hundredTwentyMcqsAreThreeSheetsAndThreeKeyPagesThatAssembleBack() {
        val qs = (1..120).map { mcq(it, it % 4) }
        val numbers = qs.associate { it.id to it.id.drop(1).toInt() }
        val own = AnswerKey.build(id, 3, qs, numbers, GradingRules(2.0, 0.5), now = 1L)
        assertEquals(3, own.sheets)
        val rows = omrRows(own.items.map { it.printedNumber })
        assertEquals(listOf(50, 50, 50), rows.map { it.size })
        assertEquals((121..130).toList(), rows[2].drop(20).take(10)) // the extra rows are numbered on
        assertNotNull(own.itemAt(2, 19))
        assertNull(own.itemAt(2, 20))
        // the key sheet prints 40 answers a page here: three QR pages
        val pages = own.items.chunked(40).mapIndexed { i, c -> KeyQrPayload.forPage(id, 3, i, 3, c, GradingRules(2.0, 0.5))!! }
        val back = KeyQrPayload.assemble(pages.reversed().map { KeyQrPayload.decode(it.encode())!! }, 1L) as KeyQrAssembly.Done
        assertTrue(own.sameContent(back.snapshot))
        assertEquals(2.0, back.snapshot.marksPerQ, 0.0)
        assertEquals(0.5, back.snapshot.negativePerWrong, 0.0)
    }

    @Test
    fun damagedSavedSnapshotIsRefused() {
        val ok = AnswerKey.encode(AnswerKey.build(id, null, listOf(mcq(1, 0), mcq(2, 1)), mapOf("q1" to 1, "q2" to 2), GradingRules(), now = 1L))
        assertNotNull(AnswerKey.decode(ok))
        // two items with the same printed number
        assertNull(AnswerKey.decode(ok.replace("[2,[1]]", "[1,[1]]")))
        // an option position out of range, a number below 1, an empty / wrong id
        assertNull(AnswerKey.decode(ok.replace("[2,[1]]", "[2,[40]]")))
        assertNull(AnswerKey.decode(ok.replace("[2,[1]]", "[0,[1]]")))
        assertNull(AnswerKey.decode(ok.replace(id, "")))
        assertNull(AnswerKey.decode(""))
    }

    @Test
    fun setPickingWithTenSetsAndMissingSnapshots() {
        fun snap(set: Int?) = AnswerKeySnapshot(id, set, 1, listOf(KeyItem(1, setOf(0))), 1.0, 0.0, 1L)
        val all = (0..9).map { snap(it) }
        for (s in 0..9) assertEquals(s, OmrGrade.pickSnapshot(all, s, null).snapshot?.setIndex)
        assertNull(OmrGrade.pickSnapshot(all, null, null).snapshot)
        assertTrue(OmrFlag.SET_NEEDED in OmrGrade.pickSnapshot(all, null, null).flags)
        assertTrue(OmrFlag.NO_SNAPSHOT in OmrGrade.pickSnapshot(all.take(5), 7, null).flags)
        assertEquals(null, OmrGeometry.setIndexOf("K", listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")))
    }
}
