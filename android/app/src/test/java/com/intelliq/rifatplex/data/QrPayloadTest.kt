package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {
    private val id = "K7M2QX9A"

    @Test
    fun exampleOfTheSpec() {
        val p = QrPayload(id, 2, 1, 0, 1)
        assertEquals("IQ1;K7M2QX9A;2;1;0;1", p.encode())
        assertEquals(p, QrPayload.decode("IQ1;K7M2QX9A;2;1;0;1"))
    }

    @Test
    fun noSetIsWrittenAsDash() {
        val p = QrPayload(id, null, 1, 0, 1)
        assertEquals("IQ1;K7M2QX9A;-;1;0;1", p.encode())
        assertEquals(p, QrPayload.decode(p.encode()))
    }

    @Test
    fun roundTripOfEverySetAndEveryPageCount() {
        for (set in listOf<Int?>(null) + (0..9)) for (pages in 1..99) for (page in 0 until pages) {
            val p = QrPayload(id, set, 1, page, pages)
            assertEquals(p, QrPayload.decode(p.encode()))
        }
    }

    @Test
    fun longestPayloadStillFitsQrVersion2() {
        // QR version 2, level M holds 26 bytes of text
        val longest = QrPayload("ZZZZZZZZ", 9, 99, 98, 99).encode()
        assertTrue("length ${longest.length}", longest.length <= 26)
    }

    @Test
    fun decodeRefusesWrongFormats() {
        val bad = listOf(
            "", "hello", "IQ1", "IQ1;K7M2QX9A;2;1;0", // too short
            "IQ1;K7M2QX9A;2;1;0;1;9", // too long
            "IQ2;K7M2QX9A;2;1;0;1", // other version of the format
            "iq1;K7M2QX9A;2;1;0;1",
            "IQ1;K7M2QX9;2;1;0;1", // id of 7 characters
            "IQ1;K7M2QX90;2;1;0;1", // 0 is not in the alphabet
            "IQ1;K7M2QX9A;10;1;0;1", // set 10
            "IQ1;K7M2QX9A;-1;1;0;1", // negative set
            "IQ1;K7M2QX9A;x;1;0;1",
            "IQ1;K7M2QX9A;2;0;0;1", // geometry version 0
            "IQ1;K7M2QX9A;2;1;1;1", // page 1 of 1 (0-based)
            "IQ1;K7M2QX9A;2;1;0;0", // 0 pages
            "IQ1;K7M2QX9A;2;1;0;100", // more than 99 pages
            "IQ1;K7M2QX9A;2;1;-1;1",
            "IQ1;K7M2QX9A;02;1;0;1", // not the canonical form
            "IQ1;K7M2QX9A;2;1;00;1",
            "IQ1;K7M2QX9A;2;+1;0;1",
            " IQ1;K7M2QX9A;2;1;0;1",
            "IQ1;K7M2QX9A;2;1;0;1 ",
        )
        for (s in bad) assertNull("'$s' must be refused", QrPayload.decode(s))
    }

    @Test
    fun forSheetChecksTheRange() {
        assertNotNull(QrPayload.forSheet(id, 3, 0, 1))
        assertEquals(OmrGeometry.VERSION, QrPayload.forSheet(id, 3, 0, 1)!!.geomVersion)
        assertNull(QrPayload.forSheet("", 3, 0, 1)) // no exam id yet
        assertNull(QrPayload.forSheet(id, 10, 0, 1))
        assertNull(QrPayload.forSheet(id, 3, 1, 1))
        assertNull(QrPayload.forSheet(id, 3, 0, 100))
    }

    @Test
    fun scannerCanTellOurCodesFromOthers() {
        assertTrue(QrPayload.isIntelliQ("IQ2;whatever"))
        assertTrue(QrPayload.isIntelliQ("IQ1;K7M2QX9A;2;1;0;1"))
        assertEquals(false, QrPayload.isIntelliQ("https://example.com"))
    }

    @Test
    fun setIndexFollowsTheBubbleOfTheSet() {
        val names = listOf("ক", " খ ", "", "গ", "ঘ")
        assertEquals(0, OmrGeometry.setIndexOf("ক", names))
        assertEquals(1, OmrGeometry.setIndexOf("খ", names)) // trimmed
        assertEquals(2, OmrGeometry.setIndexOf("গ", names)) // the blank name has no bubble, so গ is the third one
        assertEquals(3, OmrGeometry.setIndexOf(" ঘ", names))
        assertNull(OmrGeometry.setIndexOf(null, names))
        assertNull(OmrGeometry.setIndexOf("", names))
        assertNull(OmrGeometry.setIndexOf("ঙ", names))
        assertNull(OmrGeometry.setIndexOf("ক", emptyList()))
        // only ten sets have a bubble
        val twelve = (1..12).map { "S$it" }
        assertEquals(9, OmrGeometry.setIndexOf("S10", twelve))
        assertNull(OmrGeometry.setIndexOf("S11", twelve))
    }
}
