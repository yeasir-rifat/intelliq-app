package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerKeyTest {
    private val examId = "K7M2QX9A"
    private val rules = GradingRules()

    /** An MCQ whose option at [correct] (0-based positions) is the right one. */
    private fun mcq(id: String, vararg correct: Int, options: Int = 4): Question =
        Question(
            id = id,
            text = "question $id",
            options = (0 until options).map { Option("o$it", "option $it", it in correct) },
        )

    private fun tf(id: String): Question = Question(id = id, text = "statement $id", type = QType.TF, tfAnswer = true)

    private fun build(qs: List<Question>, numbers: Map<String, Int>, set: Int? = null) =
        AnswerKey.build(examId, set, qs, numbers, rules, now = 1L)

    // ---------------------------------------------------------------- printed numbers

    @Test
    fun itemsFollowThePrintedNumbersNotTheListOrder() {
        // what Document AutoFix does: the list order and the printed numbers differ
        val qs = listOf(mcq("a", 0), mcq("b", 1), mcq("c", 2))
        val snap = build(qs, mapOf("a" to 3, "b" to 1, "c" to 2))
        assertEquals(listOf(1, 2, 3), snap.items.map { it.printedNumber })
        assertEquals(listOf(setOf(1), setOf(2), setOf(0)), snap.items.map { it.correct })
    }

    @Test
    fun onlyNonEmptyMcqsAreInTheSnapshot() {
        val qs = listOf(mcq("a", 0), tf("t"), Question(id = "empty"), mcq("b", 3))
        val numbers = mapOf("a" to 1, "t" to 1, "empty" to 2, "b" to 2)
        val snap = build(qs, numbers)
        assertEquals(listOf(1, 2), snap.items.map { it.printedNumber })
        assertEquals(listOf(setOf(0), setOf(3)), snap.items.map { it.correct })
    }

    @Test
    fun plainNumbersMatchTheArrangedPaper() {
        val settings = DocSettings()
        val sectioned = listOf(mcq("m1", 0), tf("t1"), mcq("m2", 1), tf("t2"), mcq("m3", 2))
        // two types + "arrange by section": numbering restarts in every section
        val n1 = AnswerKey.plainNumbers(sectioned, settings)
        assertEquals(listOf(1, 2, 3), AnswerKey.printedMcqs(sectioned, n1).map { it.first })
        assertEquals(listOf("m1", "m2", "m3"), AnswerKey.printedMcqs(sectioned, n1).map { it.second.id })

        // sections off: one numbering 1..n over all questions, so the MCQ numbers are not 1, 2, 3
        val flat = settings.copy(sectionMode = false)
        val n2 = AnswerKey.plainNumbers(sectioned, flat)
        assertEquals(listOf(1, 3, 5), AnswerKey.printedMcqs(sectioned, n2).map { it.first })
        val snap = AnswerKey.build(examId, null, sectioned, n2, rules, 1L)
        assertEquals(listOf(1, 3, 5), snap.items.map { it.printedNumber })
    }

    // ---------------------------------------------------------------- OMR rows

    @Test
    fun omrRowsAreAlwaysFullSheets() {
        assertEquals(listOf(emptyList<Int>()), omrRows(emptyList()))

        val twenty = omrRows((1..20).toList())
        assertEquals(1, twenty.size)
        assertEquals(50, twenty[0].size)
        assertEquals((1..50).toList(), twenty[0]) // rows after the last MCQ are simply numbered on

        val sixty = omrRows((1..60).toList())
        assertEquals(2, sixty.size)
        assertEquals(50, sixty[0].size)
        assertEquals(50, sixty[1].size)
        assertEquals(51, sixty[1][0])
        assertEquals(100, sixty[1][49])
    }

    @Test
    fun sheetCountOfTheSnapshotEqualsThePrintedSheets() {
        for (n in listOf(0, 1, 49, 50, 51, 100, 101, 150)) {
            val qs = (1..n).map { mcq("q$it", it % 4) }
            val numbers = qs.mapIndexed { i, q -> q.id to i + 1 }.toMap()
            val snap = build(qs, numbers)
            val rows = omrRows(AnswerKey.printedMcqs(qs, numbers).map { it.first })
            assertEquals("n=$n", rows.size, snap.sheets)
        }
    }

    @Test
    fun itemAtFindsTheMcqOfARow() {
        val qs = (1..60).map { mcq("q$it", it % 4) }
        val snap = build(qs, qs.mapIndexed { i, q -> q.id to i + 1 }.toMap())
        assertEquals(1, snap.itemAt(0, 0)?.printedNumber)
        assertEquals(50, snap.itemAt(0, 49)?.printedNumber)
        assertEquals(51, snap.itemAt(1, 0)?.printedNumber)
        assertEquals(60, snap.itemAt(1, 9)?.printedNumber)
        assertNull(snap.itemAt(1, 10)) // a blank extra row
        assertNull(snap.itemAt(2, 0))
        assertNull(snap.itemAt(0, 50))
        assertNull(snap.itemAt(-1, 0))
    }

    // ---------------------------------------------------------------- right options

    @Test
    fun correctPositionsAreThePrintedPositions() {
        assertEquals(setOf(1), AnswerKey.correctPositions(mcq("a", 1)))
        assertEquals(setOf(0, 2), AnswerKey.correctPositions(mcq("a", 0, 2)))
        assertEquals(emptySet<Int>(), AnswerKey.correctPositions(mcq("a")))
    }

    @Test
    fun shuffledSetsHaveTheirOwnRightPositions() {
        val source = (1..30).map { mcq("q$it", it % 4) }
        val d = AppData(
            questions = source,
            setCode = SetCodeSettings(enabled = true, numberOfSets = 4, randomization = "both"),
        )
        val sets = Randomizer.generate(d)
        assertEquals(4, sets.size)
        sets.forEachIndexed { si, s ->
            val numbers = AnswerKey.plainNumbers(s.questions, DocSettings())
            val snap = AnswerKey.build(examId, si, s.questions, numbers, rules, 1L)
            assertEquals(30, snap.items.size)
            // item i belongs to the i-th question of the shuffled set, and its right position is where the shuffle put it
            s.questions.forEachIndexed { i, q ->
                val expected = q.options.indexOfFirst { it.correct }
                assertEquals("set $si question ${i + 1}", setOf(expected), snap.items[i].correct)
            }
        }
        // shuffling really moved things: not every set has the same answers
        val keys = sets.mapIndexed { si, s ->
            AnswerKey.build(examId, si, s.questions, AnswerKey.plainNumbers(s.questions, DocSettings()), rules, 1L).items.map { it.correct }
        }
        assertTrue(keys.distinct().size > 1)
    }

    @Test
    fun unansweredAndUnmarkableAreCounted() {
        val qs = listOf(mcq("a", 0), mcq("b"), mcq("c"), mcq("d", 4, options = 5), mcq("e", 3))
        val snap = build(qs, qs.mapIndexed { i, q -> q.id to i + 1 }.toMap())
        assertEquals(2, snap.unanswered)
        assertEquals(1, snap.unmarkable)
        assertEquals(3.0, snap.maxScore, 0.0) // a, d, e have an answer
    }

    @Test
    fun allMustMatchWhenSeveralOptionsAreRight() {
        val item = KeyItem(1, setOf(0, 2))
        assertTrue(item.matches(setOf(0, 2)))
        assertFalse(item.matches(setOf(0)))
        assertFalse(item.matches(setOf(0, 1, 2)))
        assertFalse(item.matches(emptySet()))
        assertFalse(KeyItem(2, emptySet()).matches(emptySet())) // no answer is never right
    }

    // ---------------------------------------------------------------- rules + replacing

    @Test
    fun savedRulesAreSane() {
        assertEquals(GradingRules(1.0, 0.0), GradingRules(0.0, -1.0).sane())
        assertEquals(GradingRules(1.0, 0.0), GradingRules(Double.NaN, Double.POSITIVE_INFINITY).sane())
        assertEquals(GradingRules(2.5, 0.25), GradingRules(2.5, 0.25).sane())
        val snap = AnswerKey.build(examId, null, listOf(mcq("a", 0)), mapOf("a" to 1), GradingRules(-3.0, 0.5), 1L)
        assertEquals(1.0, snap.marksPerQ, 0.0)
        assertEquals(0.5, snap.negativePerWrong, 0.0)
    }

    @Test
    fun sameContentIgnoresTheTime() {
        val qs = listOf(mcq("a", 1))
        val a = AnswerKey.build(examId, 2, qs, mapOf("a" to 1), rules, 100L)
        val b = AnswerKey.build(examId, 2, qs, mapOf("a" to 1), rules, 999L)
        assertTrue(a.sameContent(b))
        assertFalse(a.sameContent(AnswerKey.build(examId, 2, listOf(mcq("a", 2)), mapOf("a" to 1), rules, 100L)))
        assertFalse(a.sameContent(AnswerKey.build(examId, 3, qs, mapOf("a" to 1), rules, 100L)))
        assertFalse(a.sameContent(AnswerKey.build(examId, 2, qs, mapOf("a" to 1), GradingRules(2.0, 0.0), 100L)))
    }

    @Test
    fun storeKeyHasExamIdAndSet() {
        assertEquals("K7M2QX9A.2", AnswerKey.keyOf(examId, 2))
        assertEquals("K7M2QX9A.-", AnswerKey.keyOf(examId, null))
    }

    // ---------------------------------------------------------------- JSON

    @Test
    fun jsonRoundTrip() {
        val qs = listOf(mcq("a", 1), mcq("b"), mcq("c", 0, 3))
        for (set in listOf<Int?>(null, 0, 9)) {
            val snap = AnswerKey.build(examId, set, qs, mapOf("a" to 2, "b" to 1, "c" to 3), GradingRules(2.0, 0.5), 1234567890123L)
            val back = AnswerKey.decode(AnswerKey.encode(snap))
            assertNotNull(back)
            assertEquals(snap, back)
        }
    }

    @Test
    fun badJsonIsRefused() {
        val good = AnswerKey.encode(AnswerKey.build(examId, 1, listOf(mcq("a", 1)), mapOf("a" to 1), rules, 1L))
        assertNotNull(AnswerKey.decode(good))
        assertNull(AnswerKey.decode("not json"))
        assertNull(AnswerKey.decode("{}"))
        assertNull(AnswerKey.decode(good.replace("\"v\":1", "\"v\":2"))) // another format
        assertNull(AnswerKey.decode(good.replace(examId, "K7M2QX0O"))) // 0 and O are not in the alphabet
        assertNull(AnswerKey.decode(good.replace("\"set\":1", "\"set\":10"))) // only sets 0..9
        assertNull(AnswerKey.decode(good.replace("[1,[1]]", "[0,[1]]"))) // numbers start at 1
        assertNull(AnswerKey.decode(good.replace("[1,[1]]", "[1,[-1]]"))) // no negative positions
    }

    @Test
    fun theSnapshotHoldsNoQuestionText() {
        val json = AnswerKey.encode(build(listOf(mcq("a", 1)), mapOf("a" to 1)))
        assertFalse(json.contains("question"))
        assertFalse(json.contains("option"))
    }
}
