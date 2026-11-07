package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.data.OmrFlag
import com.intelliq.rifatplex.data.OmrSizeMismatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The texts of the sheet size feature: the size-mismatch message, the size hint and the corner tag, in both languages. */
class OmrMessagesTest {
    @Test
    fun sizeMismatchMessageNamesBothSizesInBothLanguages() {
        val sizes = OmrSizeMismatch(sheetRows = 100, keyRows = 200)
        assertEquals(
            "এই শিট ১০০ প্রশ্নের, কিন্তু সেভ করা উত্তরমালা ২০০-এর। সেটিংসে মাপ ঠিক করে আবার ছাপুন।",
            evalFlagText(OmrFlag.KEY_SIZE_MISMATCH, sizes, Lang.BN),
        )
        assertEquals(
            "This sheet has 100 questions, but the saved answer key is for 200. Fix the size in Settings and print again.",
            evalFlagText(OmrFlag.KEY_SIZE_MISMATCH, sizes, Lang.EN),
        )
        // no placeholder is ever shown
        for (lang in Lang.values()) assertFalse(evalFlagText(OmrFlag.KEY_SIZE_MISMATCH, sizes, lang).contains("{"))
        assertFalse(evalFlagText(OmrFlag.KEY_SIZE_MISMATCH, null, Lang.EN).contains("{"))
    }

    @Test
    fun everyFlagHasATextInBothLanguages() {
        for (lang in Lang.values()) for (f in OmrFlag.values()) {
            val key = "eval.flag." + f.name
            assertNotEquals("$key missing in $lang", key, trKey(key, lang))
        }
        // the new flag is not the old "other version" message
        for (lang in Lang.values()) {
            assertNotEquals(trKey("eval.flag.QR_OTHER_VERSION", lang), trKey("eval.flag.KEY_SIZE_MISMATCH", lang))
        }
    }

    @Test
    fun otherFlagsAreUnchangedByTheSizeNote() {
        for (f in OmrFlag.values()) if (f != OmrFlag.KEY_SIZE_MISMATCH) {
            assertEquals(trKey("eval.flag." + f.name, Lang.BN), evalFlagText(f, OmrSizeMismatch(50, 100), Lang.BN))
        }
    }

    @Test
    fun sizeHintFollowsTheMcqCount() {
        assertEquals("৪৫টি MCQ: ৫০-এর শিটই যথেষ্ট", omrSizeHint(45, Lang.BN))
        assertEquals("45 MCQs: a 50-question sheet is enough", omrSizeHint(45, Lang.EN))
        assertEquals("৬০টি MCQ: ১০০-এর শিটই যথেষ্ট", omrSizeHint(60, Lang.BN))
        assertEquals("150 MCQs: a 200-question sheet is enough", omrSizeHint(150, Lang.EN))
        assertEquals("৪৫০টি MCQ: ২০০-এর ৩টি শিট লাগবে", omrSizeHint(450, Lang.BN))
        assertEquals("450 MCQs: 3 sheets of 200 questions are needed", omrSizeHint(450, Lang.EN))
        assertNull(omrSizeHint(0, Lang.BN)) // no MCQ: nothing is shown
        assertNull(omrSizeHint(0, Lang.EN))
    }

    @Test
    fun cornerTagAndSettingTextExistInBothLanguages() {
        assertEquals("{n} প্রশ্ন", trKey("omr.sizeTag", Lang.BN))
        assertEquals("{n} Questions", trKey("omr.sizeTag", Lang.EN))
        for (lang in Lang.values()) {
            assertNotEquals("settings.omr.sizeTag", trKey("settings.omr.sizeTag", lang))
            assertTrue(trKey("omr.sizeTag", lang).contains("{n}"))
        }
    }
}
