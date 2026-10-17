package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.data.AnswerKey
import com.intelliq.rifatplex.data.Option
import com.intelliq.rifatplex.data.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 4.8: the answer sheet has a QR code at the top right of every page (the header is as tall as the code), and the cells carry the option positions. */
class KeySheetLayoutTest {
    @Test
    fun a4PagesHoldOneHundredTenThenOneHundredFifteenAnswers() {
        assertEquals(listOf(110), keyPaginate(110, 297f))
        assertEquals(listOf(110, 115, 15), keyPaginate(240, 297f))
        assertEquals(listOf(0), keyPaginate(0, 297f))
    }

    @Test
    fun entriesKeepThePositionsForTheQrCode() {
        fun mcq(id: String, vararg correct: Int) =
            Question(id = id, text = "q $id", options = (0 until 4).map { Option("o$it", "option $it", it in correct) })
        val qs = listOf(mcq("a", 1), mcq("b", 0, 2), mcq("c"))
        val e = keyEntries(qs, AnswerKey.plainNumbers(qs, com.intelliq.rifatplex.data.DocSettings()), bn = false)
        assertEquals(listOf(setOf(1), setOf(0, 2), emptySet<Int>()), e.map { it.positions })
        assertEquals("B", e[0].answer)
        assertEquals("A, C", e[1].answer)
        assertTrue(e[2].answer == null)
    }
}
