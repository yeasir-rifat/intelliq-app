package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamIdTest {
    @Test
    fun alphabetHasNoConfusingCharacters() {
        assertEquals(31, ExamId.ALPHABET.length)
        assertEquals(31, ExamId.ALPHABET.toSet().size)
        for (c in "01OIL") assertFalse("$c must not be in the alphabet", c in ExamId.ALPHABET)
    }

    @Test
    fun generatedIdsAreValidAndDifferent() {
        val ids = (1..2000).map { ExamId.generate() }
        assertTrue(ids.all { ExamId.isValid(it) })
        assertEquals("2000 random ids must all differ", 2000, ids.toSet().size)
    }

    @Test
    fun isValidRejectsBadValues() {
        assertTrue(ExamId.isValid("K7M2QX9A"))
        assertFalse(ExamId.isValid(null))
        assertFalse(ExamId.isValid(""))
        assertFalse(ExamId.isValid("K7M2QX9")) // 7 characters
        assertFalse(ExamId.isValid("K7M2QX9AB")) // 9 characters
        assertFalse(ExamId.isValid("k7m2qx9a")) // lower case
        assertFalse(ExamId.isValid("K7M2QX90")) // 0
        assertFalse(ExamId.isValid("K7M2QX9I")) // I
    }

    @Test
    fun deriveIsStableAndValid() {
        val a = ExamId.derive("hist_abc_123")
        assertEquals(a, ExamId.derive("hist_abc_123"))
        assertTrue(ExamId.isValid(a))
        assertNotEquals(a, ExamId.derive("hist_abc_124"))
        // fixed values (checked against an independent FNV-1a 64 implementation): every device and every app version
        // must derive the same id, or the QR of a sheet printed from one device would not match on another
        assertEquals("2DV5GA37", a)
        assertEquals("2XAUAPFU", ExamId.derive(""))
    }

    @Test
    fun deriveSpreadsOverManySeeds() {
        val ids = (1..5000).map { ExamId.derive("hist_x_$it") }
        assertTrue(ids.all { ExamId.isValid(it) })
        assertEquals(5000, ids.toSet().size)
    }
}
