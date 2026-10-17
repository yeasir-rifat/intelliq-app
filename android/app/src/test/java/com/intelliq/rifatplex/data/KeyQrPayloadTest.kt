package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 4.8: the QR code of the answer sheet (উত্তরমালা), the bridge to the OMR sheets. */
class KeyQrPayloadTest {
    private val id = "K7M2QX9A"
    private val rules = GradingRules(1.0, 0.25)

    private fun items(from: Int, n: Int, pos: (Int) -> Set<Int> = { setOf(it % 4) }) =
        (0 until n).map { KeyItem(from + it, pos(it)) }

    @Test
    fun exampleOfTheSpec() {
        val text = "IQK1:K7M2QX9A:2:1:0:1:100:25:1-4.7:B1CA0"
        val p = KeyQrPayload.decode(text)!!
        assertEquals(listOf(1, 2, 3, 4, 7), p.numbers)
        // B = 11 = bits 0,1,3 | 1 = bit 0 | C = 12 = bits 2,3 | A = 10 = bits 1,3 | 0 = nothing marked
        assertEquals(listOf(setOf(0, 1, 3), setOf(0), setOf(2, 3), setOf(1, 3), emptySet<Int>()), p.answers)
        assertEquals(text, p.encode())
        assertEquals(2, p.setIndex); assertEquals(100, p.marksX100); assertEquals(25, p.negX100)
    }

    @Test
    fun sameIdentityAsTheOmrSheetCode() {
        // the bridge: the answer sheet and the OMR sheet of one exam / set say the same exam id, set and geometry version
        val omr = QrPayload.forSheet(id, 2, 0, 1)!!
        val key = KeyQrPayload.forPage(id, 2, 0, 1, items(1, 10), rules)!!
        assertEquals(omr.examId, key.examId)
        assertEquals(omr.setIndex, key.setIndex)
        assertEquals(omr.geomVersion, key.geomVersion)
    }

    @Test
    fun roundTripAndNoSet() {
        for (set in listOf(null, 0, 9)) {
            val p = KeyQrPayload.forPage(id, set, 1, 3, items(101, 30) { setOf(it % 4) }, rules)!!
            assertEquals(p, KeyQrPayload.decode(p.encode()))
        }
        assertTrue(KeyQrPayload.forPage(id, null, 0, 1, items(1, 5), rules)!!.encode().startsWith("IQK1:$id:-:"))
    }

    @Test
    fun everyAnswerShape() {
        val shapes = listOf(emptySet(), setOf(0), setOf(1), setOf(2), setOf(3), setOf(4), setOf(0, 2), setOf(1, 2, 3), setOf(0, 1, 2, 3, 4))
        val p = KeyQrPayload.forPage(id, 1, 0, 1, shapes.mapIndexed { i, s -> KeyItem(i + 1, s) }, rules)!!
        assertEquals(shapes, KeyQrPayload.decode(p.encode())!!.answers)
    }

    @Test
    fun optionBeyondTheFifthIsKeptAsUnmarkable() {
        val p = KeyQrPayload.forPage(id, 1, 0, 1, listOf(KeyItem(1, setOf(6)), KeyItem(2, setOf(1))), rules)!!
        val back = KeyQrPayload.decode(p.encode())!!.items()
        assertTrue(back[0].hasAnswer); assertTrue(!back[0].markable) // the exact position is not kept, it cannot be marked anyway
        assertEquals(setOf(1), back[1].correct)
    }

    @Test
    fun numbersAreWrittenAsRanges() {
        val p = KeyQrPayload.forPage(id, 1, 0, 1, (1..30).map { KeyItem(it, setOf(0)) } + (35..40).map { KeyItem(it, setOf(1)) } + KeyItem(45, setOf(2)), rules)!!
        assertTrue(p.encode().contains(":1-30.35-40.45:"))
        assertEquals((1..30).toList() + (35..40).toList() + 45, KeyQrPayload.decode(p.encode())!!.numbers)
    }

    @Test
    fun rulesAreKeptInHundredths() {
        val p = KeyQrPayload.forPage(id, 1, 0, 1, items(1, 3), GradingRules(2.0, 0.5))!!
        assertEquals(200, p.marksX100); assertEquals(50, p.negX100)
        val done = KeyQrPayload.assemble(listOf(p), 5L) as KeyQrAssembly.Done
        assertEquals(2.0, done.snapshot.marksPerQ, 0.0); assertEquals(0.5, done.snapshot.negativePerWrong, 0.0)
    }

    @Test
    fun badTextIsNull() {
        val good = "IQK1:K7M2QX9A:2:1:0:1:100:25:1-4.7:B1CA0"
        assertNotNull(KeyQrPayload.decode(good))
        assertNull(KeyQrPayload.decode("IQK2:K7M2QX9A:2:1:0:1:100:25:1-4.7:B1CA0"))   // other format version
        assertNull(KeyQrPayload.decode("IQ1;K7M2QX9A;2;1;0;1"))                       // an OMR sheet code
        assertNull(KeyQrPayload.decode(good.replace("K7M2QX9A", "K7M2QX0A")))         // 0 is not in the id alphabet
        assertNull(KeyQrPayload.decode(good.replace(":2:1:0:1:", ":2:1:1:1:")))       // page 1 of 1
        assertNull(KeyQrPayload.decode(good.replace("B1CA0", "B1CA")))                // fewer answers than numbers
        assertNull(KeyQrPayload.decode(good.replace("B1CA0", "B1CA!")))               // character outside the alphabet
        assertNull(KeyQrPayload.decode(good.replace("1-4.7", "4-1.7")))               // backwards range
        assertNull(KeyQrPayload.decode(good.replace("1-4.7", "1-1.7")))               // a one-number range must be written plain
        assertNull(KeyQrPayload.decode(good.replace("1-4.7", "7.1-4")))               // numbers must grow
        assertNull(KeyQrPayload.decode(good.replace(":100:25:", ":0:25:")))           // marks must be > 0
        assertNull(KeyQrPayload.decode(good.replace(":100:", ":0100:")))              // not canonical
        assertNull(KeyQrPayload.decode(""))
        assertNull(KeyQrPayload.decode("IQK1"))
    }

    @Test
    fun outOfRangeGivesNoCode() {
        assertNull(KeyQrPayload.forPage("bad", 1, 0, 1, items(1, 3), rules))
        assertNull(KeyQrPayload.forPage(id, 10, 0, 1, items(1, 3), rules))
        assertNull(KeyQrPayload.forPage(id, 1, 1, 1, items(1, 3), rules))
        assertNull(KeyQrPayload.forPage(id, 1, 0, 1, emptyList(), rules))            // a page without MCQ prints no code
    }

    @Test
    fun scatteredNumbersMayExceedTheCodeSizeAndThenPrintNoCode() {
        val scattered = (0 until 100).map { KeyItem(1 + it * 2, setOf(1)) }          // an MCQ on every other number
        assertNull(KeyQrPayload.forPage(id, 1, 0, 1, scattered, rules))
        val contiguous = KeyQrPayload.forPage(id, 1, 0, 1, items(1, 115), rules)!!    // the biggest later page of an A4 answer sheet
        assertTrue(contiguous.encode().length <= KeyQrPayload.MAX_TEXT)
    }

    @Test
    fun theCodeIsAlphanumericAndFitsVersion6To8() {
        val p = KeyQrPayload.forPage(id, 2, 0, 2, items(1, 100), rules)!!
        val m = QrCode.encode(p.encode())
        assertTrue("size ${m.size}", m.size in 25..49)
        assertEquals(0, m.size % 4 - 1) // QR sizes are 17 + 4 * version
    }

    // ----- assemble -----

    @Test
    fun pagesPutTogetherInAnyOrder() {
        val a = KeyQrPayload.forPage(id, 1, 0, 3, items(1, 100), rules)!!
        val b = KeyQrPayload.forPage(id, 1, 1, 3, items(101, 100), rules)!!
        val c = KeyQrPayload.forPage(id, 1, 2, 3, items(201, 20), rules)!!
        val done = KeyQrPayload.assemble(listOf(c, a, b, a), 9L) as KeyQrAssembly.Done // a scanned twice counts once
        val s = done.snapshot
        assertEquals(220, s.items.size)
        assertEquals((1..220).toList(), s.items.map { it.printedNumber })
        assertEquals(id, s.examId); assertEquals(1, s.setIndex); assertEquals(OmrGeometry.VERSION, s.geomVersion)
        assertEquals(1.0, s.marksPerQ, 0.0); assertEquals(0.25, s.negativePerWrong, 0.0)
        assertEquals(9L, s.createdAt)
        // item i sits on sheet i / 50, as in the snapshot made on the device that printed it
        assertEquals(5, s.sheets)
    }

    @Test
    fun missingPagesAreNamed() {
        val a = KeyQrPayload.forPage(id, 1, 0, 3, items(1, 100), rules)!!
        val c = KeyQrPayload.forPage(id, 1, 2, 3, items(201, 20), rules)!!
        val r = KeyQrPayload.assemble(listOf(a, c)) as KeyQrAssembly.Missing
        assertEquals(listOf(1), r.pages)
    }

    @Test
    fun codesOfDifferentSheetsDoNotMix() {
        val a = KeyQrPayload.forPage(id, 1, 0, 2, items(1, 100), rules)!!
        assertTrue(KeyQrPayload.assemble(listOf(a, KeyQrPayload.forPage(id, 2, 1, 2, items(101, 5), rules)!!)) is KeyQrAssembly.Mismatch)              // other set
        assertTrue(KeyQrPayload.assemble(listOf(a, KeyQrPayload.forPage("ABCDEFGH", 1, 1, 2, items(101, 5), rules)!!)) is KeyQrAssembly.Mismatch)     // other exam
        assertTrue(KeyQrPayload.assemble(listOf(a, KeyQrPayload.forPage(id, 1, 1, 2, items(101, 5), GradingRules(2.0, 0.0))!!)) is KeyQrAssembly.Mismatch) // other rules
        assertTrue(KeyQrPayload.assemble(listOf(a, KeyQrPayload.forPage(id, 1, 1, 2, items(50, 5), rules)!!)) is KeyQrAssembly.Mismatch)               // numbers go back
        assertTrue(KeyQrPayload.assemble(listOf(a, KeyQrPayload.forPage(id, 1, 0, 2, items(1, 100) { setOf(0) }, rules)!!)) is KeyQrAssembly.Mismatch) // two codes for page 0
        assertTrue(KeyQrPayload.assemble(emptyList()) is KeyQrAssembly.Mismatch)
    }

    @Test
    fun assembledSnapshotEqualsTheOneMadeOnTheDevice() {
        // answers on the printed page -> QR -> assembled snapshot must equal AnswerKey.build for the same questions
        val numbers = mapOf("a" to 1, "b" to 2, "c" to 3)
        val qs = listOf(mcq("a", 0), mcq("b", 2, 3), mcq("c")) // c: no right option marked
        val own = AnswerKey.build(id, 1, qs, numbers, GradingRules(1.0, 0.25), now = 1L)
        val page = KeyQrPayload.forPage(id, 1, 0, 1, own.items, GradingRules(1.0, 0.25))!!
        val back = (KeyQrPayload.assemble(listOf(KeyQrPayload.decode(page.encode())!!), 1L) as KeyQrAssembly.Done).snapshot
        assertTrue(own.sameContent(back))
    }

    /** An MCQ whose options at [correct] (0-based positions) are the right ones (same helper as AnswerKeyTest). */
    private fun mcq(id: String, vararg correct: Int): Question =
        Question(id = id, text = "question $id", options = (0 until 4).map { Option("o$it", "option $it", it in correct) })
}
