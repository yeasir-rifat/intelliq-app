package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The three sheet sizes (50 / 100 / 200 answers): the geometry of each, the number the QR code carries, the sheet count
 * and, on synthetic photos, that the reader finds the marks of every size at the printed places.
 */
class OmrSheetKindTest {
    private val examId = "K7M2QX9A"
    private val kinds = OmrSheetKind.values().toList()

    @Test
    fun tableOfKinds() {
        assertEquals(listOf(50, 100, 200), kinds.map { it.rows }.sorted())
        assertEquals(listOf(25, 25, 40), kinds.sortedBy { it.rows }.map { it.rowsPerBlock })
        kinds.forEach { assertEquals(it.rows, it.blocks * it.rowsPerBlock) }
        assertEquals(kinds.size, kinds.map { it.geomVersion }.toSet().size) // every size has its own version
        assertEquals(2, OmrSheetKind.K200.geomVersion) // the original sheet keeps version 2
        assertEquals(OmrGeometry.VERSION, OmrSheetKind.K200.geomVersion)
        kinds.forEach { assertEquals(it, OmrSheetKind.ofVersion(it.geomVersion)); assertEquals(it, OmrSheetKind.ofRows(it.rows)) }
        assertNull(OmrSheetKind.ofVersion(1)) // the old 50-answer v1 sheet is still "another version"
        assertNull(OmrSheetKind.ofVersion(7))
        assertEquals(OmrSheetKind.K200, OmrSheetKind.ofRows(77))
    }

    @Test
    fun twoHundredIsUnchanged() {
        for (slot in 0 until 200) for (o in 0 until 4) {
            val old = OmrGeometry.answerBubbleOfSlot(slot, o)
            val now = OmrGeometry.answerBubbleOfSlot(OmrSheetKind.K200, slot, o)
            assertEquals(old.x, now.x, 0f)
            assertEquals(old.y, now.y, 0f)
        }
        for (b in 0 until 5) assertEquals(OmrGeometry.BLOCK_X[b], OmrGeometry.blockX(OmrSheetKind.K200, b), 0f)
        assertEquals(OmrGeometry.ROW_PITCH, OmrGeometry.rowPitch(OmrSheetKind.K200), 0f)
    }

    @Test
    fun bubblesStayInsideTheirBlocksAndNeverTouch() {
        val marks = OmrGeometry.cornerMarks()
        for (k in kinds) {
            val pts = ArrayList<PointMm>()
            for (slot in 0 until k.rows) for (o in 0 until OmrGeometry.OPTIONS) {
                val p = OmrGeometry.answerBubbleOfSlot(k, slot, o)
                val block = slot / k.rowsPerBlock
                val x0 = OmrGeometry.blockX(k, block)
                assertTrue("$k slot $slot left", p.x - OmrGeometry.RING_R > x0 + OmrGeometry.QNUM_W)
                assertTrue("$k slot $slot right", p.x + OmrGeometry.RING_R < x0 + OmrGeometry.BLOCK_W)
                assertTrue("$k slot $slot top", p.y - OmrGeometry.RING_R > OmrGeometry.HEAD_BOTTOM)
                assertTrue("$k slot $slot bottom", p.y + OmrGeometry.RING_R < OmrGeometry.TABLE_BOTTOM)
                pts += p
            }
            // no two bubbles overlap (grid of cells keeps it cheap)
            val byCell = pts.groupBy { (it.x / 8f).toInt() * 1000 + (it.y / 8f).toInt() }
            for (p in pts) for (dx in -1..1) for (dy in -1..1) {
                val near = byCell[((p.x / 8f).toInt() + dx) * 1000 + ((p.y / 8f).toInt() + dy)] ?: continue
                for (q in near) if (q !== p) assertTrue("$k overlap", hypot((p.x - q.x).toDouble(), (p.y - q.y).toDouble()) > 2 * OmrGeometry.RING_R)
            }
            // blocks sit between the scanner squares and do not overlap each other
            assertTrue(OmrGeometry.blockX(k, 0) > marks[0].x + marks[0].w)
            assertTrue(OmrGeometry.blockX(k, k.blocks - 1) + OmrGeometry.BLOCK_W < marks[1].x)
            for (b in 0 until k.blocks - 1) assertTrue(OmrGeometry.blockX(k, b) + OmrGeometry.BLOCK_W < OmrGeometry.blockX(k, b + 1))
            // fewer blocks are centred on the page
            val left = OmrGeometry.blockX(k, 0)
            val right = OmrGeometry.PAGE_W_MM - (OmrGeometry.blockX(k, k.blocks - 1) + OmrGeometry.BLOCK_W)
            assertEquals("$k centred", left, right, 0.01f)
            // the answer table keeps the height of the 200 sheet
            assertEquals(OmrGeometry.TABLE_BOTTOM, OmrGeometry.HEAD_BOTTOM + OmrGeometry.rowPitch(k) * k.rowsPerBlock, 0.01f)
        }
    }

    @Test
    fun qrCarriesTheSize() {
        for (k in kinds) {
            val p = QrPayload.forSheet(examId, 2, 0, 1, k.rows)
            assertNotNull(p)
            assertEquals(k.geomVersion, p!!.geomVersion)
            assertEquals(p, QrPayload.decode(p.encode()))
        }
        assertEquals("IQ1;K7M2QX9A;2;2;0;1", QrPayload.forSheet(examId, 2, 0, 1)!!.encode()) // default = 200
        assertEquals("IQ1;K7M2QX9A;-;3;1;2", QrPayload.forSheet(examId, null, 1, 2, 100)!!.encode())
        assertEquals("IQ1;K7M2QX9A;0;4;0;1", QrPayload.forSheet(examId, 0, 0, 1, 50)!!.encode())
    }

    @Test
    fun sheetCountAndRows() {
        assertEquals(1, omrSheetCount(0, 50))
        assertEquals(1, omrSheetCount(50, 50))
        assertEquals(2, omrSheetCount(51, 50))
        assertEquals(1, omrSheetCount(100, 100))
        assertEquals(2, omrSheetCount(101, 100))
        assertEquals(1, omrSheetCount(200))
        assertEquals(2, omrSheetCount(201))
        assertEquals(2, omrSheetCount(201, 200))
        // sheets are always full: 20 MCQs still print 50 / 100 / 200 numbered rows
        for (r in listOf(50, 100, 200)) {
            val rows = omrRows((1..20).toList(), r)
            assertEquals(1, rows.size)
            assertEquals(r, rows[0].size)
            assertEquals(r, rows[0].last())
        }
        assertEquals(listOf(100, 100), omrRows((1..101).toList(), 100).map { it.size })
        assertEquals(listOf(50, 50, 50), omrRows((1..120).toList(), 50).map { it.size })
    }

    private fun mcq(id: String, correct: Int) =
        Question(id = id, text = "q $id", options = (0 until 4).map { Option("o$it", "opt $it", it == correct) })

    @Test
    fun snapshotKnowsItsSheetSize() {
        val qs = (1..130).map { mcq("q$it", it % 4) }
        val nums = qs.associate { it.id to it.id.drop(1).toInt() }
        for (r in listOf(50, 100, 200)) {
            val snap = AnswerKey.build(examId, null, qs, nums, GradingRules(), now = 1L, sheetRows = r)
            assertEquals(OmrSheetKind.ofRows(r).geomVersion, snap.geomVersion)
            assertEquals(omrSheetCount(130, r), snap.sheets)
            assertEquals(1, snap.itemAt(0, 0)!!.printedNumber)
            if (130 > r) assertEquals(r + 1, snap.itemAt(1, 0)!!.printedNumber) // first row of sheet 2 (when there is one)
            else assertNull(snap.itemAt(1, 0))
            assertNull(snap.itemAt(0, r)) // there is no row r on a sheet of r rows
            // JSON round trip keeps the size
            val back = AnswerKey.decode(AnswerKey.encode(snap))!!
            assertEquals(snap.geomVersion, back.geomVersion)
            assertEquals(snap.sheets, back.sheets)
        }
        // the default is the 200 sheet
        assertEquals(OmrGeometry.VERSION, AnswerKey.build(examId, null, qs, nums, GradingRules(), now = 1L).geomVersion)
    }

    @Test
    fun settingsKeepTheSizeInAutoMode() {
        assertEquals(200, OmrSettings().sheetKind)
        assertEquals(100, OmrSettings(sheetKind = 100).effective().sheetKind)
        assertEquals(50, OmrSettings(mode = OMR_CUSTOM, sheetKind = 50).effective().sheetKind)
        assertEquals(OmrSheetKind.K50, OmrLayout(OmrSettings(sheetKind = 50)).kind)
        assertEquals(OmrSheetKind.K200, OmrLayout(OmrSettings(sheetKind = 33)).kind)
    }

    // ---------------------------------------------------------------- improvements: extra rows, size mismatch, size hint

    @Test
    fun liveRowsStopAfterTheLastMcq() {
        assertEquals(45, omrLiveRows(45, 50, 0))
        assertEquals(50, omrLiveRows(120, 50, 0))
        assertEquals(50, omrLiveRows(120, 50, 1))
        assertEquals(20, omrLiveRows(120, 50, 2))
        assertEquals(0, omrLiveRows(100, 50, 2)) // a page past the last MCQ has none
        assertEquals(30, omrLiveRows(130, 100, 1))
        assertEquals(200, omrLiveRows(200, 200, 0))
        assertEquals(0, omrLiveRows(0, 200, 0))
        // the rows with a real MCQ are exactly the rows the answer key has an item for (the scanner ignores the others)
        for (r in listOf(50, 100, 200)) for (n in listOf(1, 49, 50, 51, 99, 100, 101, 199, 200, 201, 450)) {
            val qs = (1..n).map { mcq("q$it", it % 4) }
            val nums = qs.associate { it.id to it.id.drop(1).toInt() }
            val snap = AnswerKey.build(examId, null, qs, nums, GradingRules(), now = 1L, sheetRows = r)
            val pages = omrRows((1..n).toList(), r)
            assertEquals("r=$r n=$n pages", snap.sheets, pages.size)
            for (page in 0 until snap.sheets) {
                val live = omrLiveRows(n, r, page)
                assertEquals(r, pages[page].size) // the sheet is still full, the extra rows are only drawn pale
                for (slot in 0 until r) assertEquals("r=$r n=$n page=$page slot=$slot", slot < live, snap.itemAt(page, slot) != null)
            }
        }
    }

    @Test
    fun sizeHintPicksTheSmallestSheet() {
        assertNull(OmrSheetKind.suggest(0))
        assertNull(OmrSheetKind.suggest(-3))
        assertEquals(OmrSheetKind.K50, OmrSheetKind.suggest(1))
        assertEquals(OmrSheetKind.K50, OmrSheetKind.suggest(45))
        assertEquals(OmrSheetKind.K50, OmrSheetKind.suggest(50))
        assertEquals(OmrSheetKind.K100, OmrSheetKind.suggest(51))
        assertEquals(OmrSheetKind.K100, OmrSheetKind.suggest(100))
        assertEquals(OmrSheetKind.K200, OmrSheetKind.suggest(101))
        assertEquals(OmrSheetKind.K200, OmrSheetKind.suggest(200))
        assertEquals(OmrSheetKind.K200, OmrSheetKind.suggest(450)) // more than one sheet of 200
    }

    @Test
    fun keySizeMismatchNamesBothSizes() {
        val qs = (1..30).map { mcq("q$it", it % 4) }
        val nums = qs.associate { it.id to it.id.drop(1).toInt() }
        fun key(rows: Int) = AnswerKey.build(examId, null, qs, nums, GradingRules(), now = 1L, sheetRows = rows)
        fun qr(rows: Int) = QrPayload.forSheet(examId, null, 0, 1, rows)!!
        assertEquals(OmrSizeMismatch(100, 200), sizeMismatchOf(qr(100), key(200)))
        assertEquals(OmrSizeMismatch(200, 50), sizeMismatchOf(qr(200), key(50)))
        assertEquals(OmrSizeMismatch(50, 100), sizeMismatchOf(qr(50), key(100)))
        for (r in listOf(50, 100, 200)) assertNull(sizeMismatchOf(qr(r), key(r)))
        assertNull(sizeMismatchOf(null, key(200))) // no QR code: nothing to compare
        assertNull(sizeMismatchOf(qr(100), null))
        // a key of an unknown (old version 1) layout stays "other version", it has no size to name
        assertNull(sizeMismatchOf(qr(100), key(200).copy(geomVersion = 1)))
        assertNull(sizeMismatchOf(QrPayload(examId, null, 7, 0, 1), key(200)))
    }

    @Test
    fun sizeTagSettingDefaultsToOnAndAutoModeAlwaysPrintsIt() {
        assertTrue(OmrSettings().showSizeTag)
        assertFalse(OmrSettings(mode = OMR_CUSTOM, showSizeTag = false).effective().showSizeTag) // custom mode can hide it
        assertTrue(OmrSettings(showSizeTag = false).effective().showSizeTag) // the automatic sheet ignores custom values
    }

    // ---------------------------------------------------------------- the reader, per size

    private fun discsFor(k: OmrSheetKind, picks: Map<Int, Int>): List<SynDisc> {
        val rad = OmrGeometry.RING_R - 0.45
        return picks.map { (slot, o) ->
            val p = OmrGeometry.answerBubbleOfSlot(k, slot, o)
            SynDisc(p.x.toDouble(), p.y.toDouble(), rad, 35)
        }
    }

    private fun picksFor(k: OmrSheetKind): Map<Int, Int> {
        val m = LinkedHashMap<Int, Int>()
        for (slot in 0 until k.rows) if (slot % 3 != 2) m[slot] = (slot * 7 + 1) % 4 // every third row stays blank
        m[0] = 0
        m[k.rows - 1] = 3
        return m
    }

    private fun readBack(k: OmrSheetKind, withQr: Boolean, layoutSize: Int) {
        val picks = picksFor(k)
        val qr = if (withQr) QrPayload.forSheet(examId, 2, 0, 1, k.rows)!!.encode() else null
        val img = SyntheticSheet(SyntheticOptions(discs = discsFor(k, picks), qr = qr)).image
        val out = OmrReader.scan(img, OmrLayout(OmrSettings(sheetKind = layoutSize), (1..4).map { "S$it" }))
        assertTrue("$k aligned", out is ScanOutcome.Read)
        val r = (out as ScanOutcome.Read).read
        assertEquals("$k kind", k, r.kind)
        assertEquals("$k rows", k.rows, r.rows.size)
        if (withQr) assertEquals(k.geomVersion, r.qrPayload!!.geomVersion)
        var bad = 0
        for (slot in 0 until k.rows) {
            val want = picks[slot]
            val row = r.rows[slot]
            val ok = if (want == null) row.state == MarkState.BLANK else row.state == MarkState.FILLED && row.marked == setOf(want)
            if (!ok) {
                bad++
                println("   $k slot $slot want=$want read ${row.state} ${row.marked}")
            }
        }
        assertEquals("$k bad rows", 0, bad)
    }

    @Test
    fun readerFindsTheMarksOfEverySize() {
        // the QR code names the size; the exam's own setting is deliberately different to prove the code wins
        for (k in kinds) readBack(k, withQr = true, layoutSize = 200)
    }

    @Test
    fun readerFallsBackToTheExamSettingWithoutQr() {
        for (k in kinds) readBack(k, withQr = false, layoutSize = k.rows)
    }

    @Test
    fun gradeUsesTheSnapshotSize() {
        val k = OmrSheetKind.K50
        val qs = (1..60).map { mcq("q$it", it % 4) }
        val nums = qs.associate { it.id to it.id.drop(1).toInt() }
        val snap = AnswerKey.build(examId, null, qs, nums, GradingRules(), now = 1L, sheetRows = 50)
        // sheet 2 (page 1) of a 50-row exam holds MCQs 51..60 in rows 0..9, the other rows are blank extras
        val rows = (0 until 50).map { slot ->
            val c = snap.itemAt(1, slot)?.correct?.first() ?: 0
            BubbleSet(DoubleArray(4) { if (it == c) 0.9 else 0.0 }, MarkState.FILLED, setOf(c))
        }
        val read = OmrSheetRead(
            qr = QrStatus.Found(QrPayload(examId, null, k.geomVersion, 1, 2)), rows = rows, roll = null, reg = null, subject = null,
            setColumn = null, setFilled = null, paperLevel = 200.0, inkLevel = 30.0, lowContrast = false, outOfImage = 0, millis = 0, kind = k,
        )
        val res = OmrGrade.grade(read, snap)
        assertEquals(10, res.rows.size)
        assertEquals(10, res.correct)
        assertEquals(k, res.kind)
        assertEquals(51, res.rows.first().printedNumber)
        assertEquals(60, res.rows.last().printedNumber)
    }
}
