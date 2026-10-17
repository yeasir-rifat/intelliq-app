package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmrLiveCheckTest {
    private fun flat(w: Int, h: Int, v: Int) = GrayImage(w, h, ByteArray(w * h) { v.toByte() })

    @Test
    fun darkFrameIsTooDark() {
        assertEquals(LiveHint.TOO_DARK, OmrLiveCheck.check(flat(1280, 960, 40)).hint)
    }

    @Test
    fun emptyBrightFrameHasNoSheet() {
        assertEquals(LiveHint.NO_SHEET, OmrLiveCheck.check(flat(1280, 960, 220)).hint)
    }

    @Test
    fun everyFailureHasAMessageKey() {
        for (f in AlignFail.values()) assertTrue(OmrLiveCheck.key(OmrLiveCheck.hintFor(f)).startsWith("live."))
    }

    @Test
    fun syntheticSheetIsAcceptedLive() {
        val r = OmrLiveCheck.check(SyntheticSheet(SyntheticOptions()).image)
        assertEquals(LiveHint.OK, r.hint)
    }
}
