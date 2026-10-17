package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

/**
 * Step 4.5: the reader finds the QR code, the filled bubbles and the digits on synthetic photos (red channel) of a filled
 * sheet: black pen, light pencil, half-filled, double marks, blank rows, shadow, tilt, blur and noise.
 */
class OmrReaderTest {

    private enum class Kind { FILLED, PENCIL, BLANK, MULTI, LIGHT }

    private class Case(val kind: Kind, val opt: Int, val opt2: Int)

    /** 50 rows: a mix of everything, always the same. */
    private fun cases(): List<Case> {
        val rnd = Random(42)
        val kinds = listOf(Kind.FILLED, Kind.FILLED, Kind.FILLED, Kind.PENCIL, Kind.BLANK, Kind.MULTI, Kind.FILLED, Kind.LIGHT, Kind.PENCIL, Kind.BLANK)
        return (0 until 50).map { i ->
            val a = rnd.nextInt(4)
            Case(kinds[i % kinds.size], a, (a + 1 + rnd.nextInt(3)) % 4)
        }
    }

    private val qrText = "IQ1;K7M2QX9A;2;1;0;1"

    /** A paper with ten named sets: the set column has 10 bubbles. */
    private val tenSets = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
    private val layout = OmrLayout(OmrSettings(), tenSets)

    private fun discs(cs: List<Case>, roll: String, set: Int): List<SynDisc> {
        val out = ArrayList<SynDisc>()
        val rad = OmrGeometry.RING_R - 0.45
        for ((slot, c) in cs.withIndex()) {
            fun at(o: Int) = OmrGeometry.answerBubbleOfSlot(slot, o)
            when (c.kind) {
                Kind.FILLED -> at(c.opt).let { out += SynDisc(it.x.toDouble() + 0.15, it.y.toDouble() - 0.1, rad, 35) }
                Kind.PENCIL -> at(c.opt).let { out += SynDisc(it.x.toDouble(), it.y.toDouble(), rad, 110) }
                Kind.MULTI -> {
                    at(c.opt).let { out += SynDisc(it.x.toDouble(), it.y.toDouble(), rad, 35) }
                    at(c.opt2).let { out += SynDisc(it.x.toDouble(), it.y.toDouble(), rad, 35) }
                }
                Kind.LIGHT -> at(c.opt).let { out += SynDisc(it.x.toDouble(), it.y.toDouble(), 0.5, 35) } // tiny dot: unsure at best
                Kind.BLANK -> {}
            }
        }
        val rg = OmrGeometry.rollGrid(OmrSettings())!!
        for ((c, ch) in roll.withIndex()) rg.bubble(c, ch - '0').let { out += SynDisc(it.x.toDouble(), it.y.toDouble(), rad, 40) }
        val sg = OmrGeometry.setGrid(OmrSettings(), tenSets)!!
        sg.bubble(1, set).let { out += SynDisc(it.x.toDouble(), it.y.toDouble(), rad, 40) }
        return out
    }

    private fun check(name: String, o: SyntheticOptions, cs: List<Case>, roll: String, set: Int, exactOnly: Boolean = false) {
        val sheet = SyntheticSheet(o)
        val out = OmrReader.scan(sheet.image, layout)
        if (out !is ScanOutcome.Read) {
            fail("$name: not aligned: " + (out as ScanOutcome.NotAligned).failed.reason)
            return
        }
        val r = out.read
        var bad = 0
        var uncertain = 0
        for ((slot, c) in cs.withIndex()) {
            val row = r.rows[slot]
            val ok = when (c.kind) {
                Kind.FILLED, Kind.PENCIL -> row.state == MarkState.FILLED && row.marked == setOf(c.opt)
                Kind.BLANK -> row.state == MarkState.BLANK
                Kind.MULTI -> row.state == MarkState.MULTI && row.marked == setOf(c.opt, c.opt2)
                Kind.LIGHT -> row.state == MarkState.BLANK || row.state == MarkState.UNCERTAIN // must never be FILLED / MULTI
            }
            if (c.kind == Kind.LIGHT && row.state == MarkState.UNCERTAIN) uncertain++
            if (!ok) {
                bad++
                println("   $name: slot $slot ${c.kind} opt=${c.opt}/${c.opt2} read ${row.state} ${row.marked} d=" + row.darkness.joinToString(",") { "%.2f".format(it) })
            }
        }
        println("%-36s qr=%s roll=%s set=%s paper=%.0f ink=%.0f  bad rows=%d  %d ms".format(name, (r.qr as? QrStatus.Found)?.payload?.encode(), r.roll?.text, r.setFilled, r.paperLevel, r.inkLevel, bad, r.millis))
        assertEquals("$name: bad rows", 0, bad)
        assertTrue("$name: qr", r.qr is QrStatus.Found && (r.qr as QrStatus.Found).payload.encode() == qrText)
        assertEquals("$name: roll", roll, r.roll?.text)
        assertEquals("$name: set", set, r.setFilled)
        assertTrue("$name: contrast", !r.lowContrast)
        assertEquals("$name: out of image", 0, r.outOfImage)
    }

    private fun opts(
        tilt: Double = 0.0, q: Int = 0, shadow: Double = 0.0, blur: Int = 0, noise: Double = 0.0,
        perspX: Double = 0.0, perspY: Double = 0.0, w: Int = 1800, h: Int = 2400, cs: List<Case>, roll: String, set: Int,
    ) = SyntheticOptions(
        imgW = w, imgH = h, tiltDeg = tilt, quarterTurns = q, shadow = shadow, blurPx = blur, noise = noise,
        perspX = perspX, perspY = perspY, discs = discs(cs, roll, set), qr = qrText, clutter = false,
    )

    @Test
    fun straight() {
        val cs = cases()
        check("straight", opts(cs = cs, roll = "204817", set = 3), cs, "204817", 3)
    }

    @Test
    fun tiltedAndUpsideDown() {
        val cs = cases()
        check("tilt +9", opts(tilt = 9.0, cs = cs, roll = "031592", set = 0), cs, "031592", 0)
        check("tilt -11", opts(tilt = -11.0, cs = cs, roll = "999000", set = 9), cs, "999000", 9)
        check("upside down", opts(q = 2, cs = cs, roll = "123456", set = 1), cs, "123456", 1)
        check("quarter turn", opts(q = 1, w = 2400, h = 1800, cs = cs, roll = "654321", set = 2), cs, "654321", 2)
    }

    @Test
    fun shadowBlurNoise() {
        val cs = cases()
        check("shadow 50%", opts(shadow = 0.5, cs = cs, roll = "204817", set = 3), cs, "204817", 3)
        check("blur 1 + noise 8", opts(blur = 1, noise = 8.0, cs = cs, roll = "204817", set = 3), cs, "204817", 3)
        check("perspective + tilt + shadow", opts(tilt = 6.0, perspX = 0.12, perspY = 0.15, shadow = 0.35, cs = cs, roll = "777777", set = 5), cs, "777777", 5)
    }

    @Test
    fun smallPhoto() {
        val cs = cases()
        check("small 1500x2000", opts(w = 1500, h = 2000, cs = cs, roll = "000001", set = 4), cs, "000001", 4)
    }

    @Test
    fun blankSheetReadsBlankAndNoQr() {
        val o = SyntheticOptions(qr = null)
        val out = OmrReader.scan(SyntheticSheet(o).image) as ScanOutcome.Read
        val r = out.read
        assertTrue(r.qr is QrStatus.None)
        assertTrue(r.rows.all { it.state == MarkState.BLANK })
        assertEquals("??????", r.roll!!.text)
        assertEquals(null, r.setFilled)
    }

    @Test
    fun otherQrKinds() {
        val a = SyntheticSheet(SyntheticOptions(qr = "IQ1;K7M2QX9A;2;7;0;1")).image
        assertTrue("other geometry version", (OmrReader.scan(a) as ScanOutcome.Read).read.qr is QrStatus.OtherVersion)
        val b = SyntheticSheet(SyntheticOptions(qr = "https://example.com/x")).image
        assertTrue("foreign code", (OmrReader.scan(b) as ScanOutcome.Read).read.qr is QrStatus.Foreign)
        val c = SyntheticSheet(SyntheticOptions(qr = "IQ2;whatever")).image
        assertTrue("IQ2", (OmrReader.scan(c) as ScanOutcome.Read).read.qr is QrStatus.OtherVersion)
        val d = SyntheticSheet(SyntheticOptions(qr = "IQ1;K7M2QX9A;-;1;2;3")).image
        val q = ((OmrReader.scan(d) as ScanOutcome.Read).read.qr as QrStatus.Found).payload
        assertEquals(null, q.setIndex); assertEquals(2, q.page); assertEquals(3, q.pages)
    }

    @Test
    fun answerSheetQrIsRecognisedAsKeyPage() {
        // step 4.8: a photo of the teacher's answer sheet must not be reported as "other version of the sheet"
        val text = "IQK1:K7M2QX9A:2:1:0:1:100:0:1-3:123"
        val img = SyntheticSheet(SyntheticOptions(qr = text)).image
        val q = (OmrReader.scan(img) as ScanOutcome.Read).read.qr
        assertTrue("key page", q is QrStatus.KeyPage)
        assertEquals(text, (q as QrStatus.KeyPage).payload.encode())
        // a damaged / unknown version of the key code is still "other version", not a student sheet
        val bad = SyntheticSheet(SyntheticOptions(qr = "IQK2:K7M2QX9A")).image
        assertTrue((OmrReader.scan(bad) as ScanOutcome.Read).read.qr is QrStatus.OtherVersion)
    }

    @Test
    fun classifyRules() {
        val p = OmrReadParams()
        fun st(vararg d: Double) = OmrReader.classify(d, p).state
        assertEquals(MarkState.BLANK, st(0.0, 0.05, 0.1, 0.02))
        assertEquals(MarkState.FILLED, st(0.9, 0.05, 0.1, 0.02))
        assertEquals(MarkState.FILLED, st(0.4, 0.1, 0.0, 0.0))
        assertEquals(MarkState.MULTI, st(0.9, 0.5, 0.0, 0.0))
        assertEquals(MarkState.UNCERTAIN, st(0.25, 0.0, 0.0, 0.0))
        assertEquals(MarkState.UNCERTAIN, st(0.4, 0.3, 0.0, 0.0)) // second > 0.7 x darkest
        assertEquals(MarkState.FILLED, st(0.9, 0.3, 0.0, 0.0))
        assertEquals(setOf(0), OmrReader.classify(doubleArrayOf(0.25, 0.0), p).marked)
    }
}
