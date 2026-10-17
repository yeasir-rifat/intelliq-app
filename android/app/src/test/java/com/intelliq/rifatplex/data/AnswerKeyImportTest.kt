package com.intelliq.rifatplex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 4.9: answer-sheet QR pictures -> pages -> snapshot, on synthetic pictures (a QR drawn into a grey image). */
class AnswerKeyImportTest {
    private val exam = "K7M2QX9A"
    private val rules = GradingRules(1.0, 0.25)

    private fun items(from: Int, n: Int) = (from until from + n).map { KeyItem(it, if (it % 7 == 0) emptySet() else setOf(it % 4)) }

    /** A QR picture with a white border, like a photo of the printed page. */
    private fun picture(text: String, scale: Int = 8): GrayImage {
        val m = QrCode.encode(text)
        val quiet = 6
        val side = (m.size + 2 * quiet) * scale
        val px = ByteArray(side * side) { 0xFF.toByte() }
        for (y in 0 until m.size) for (x in 0 until m.size) if (m[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                px[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0
            }
        }
        return GrayImage(side, side, px)
    }

    private fun page(exam: String, set: Int?, p: Int, pages: Int, it: List<KeyItem>) =
        KeyQrPayload.forPage(exam, set, p, pages, it, rules)!!

    private fun scan(p: KeyQrPayload) = AnswerKeyImport.readImage(picture(p.encode()))

    private fun import(vararg ps: KeyQrPayload): KeyImport {
        var imp = KeyImport()
        for (p in ps) imp = imp.with((scan(p) as KeyScan.Page).payload)
        return imp
    }

    @Test
    fun picturesInAnyOrderGiveTheSameSnapshot() {
        val all = items(1, 120)
        val p0 = page(exam, 2, 0, 2, all.take(60))
        val p1 = page(exam, 2, 1, 2, all.drop(60))
        val a = import(p0, p1).assembly as KeyQrAssembly.Done
        val b = import(p1, p0, p1).assembly as KeyQrAssembly.Done // reversed, and page 1 scanned twice
        assertEquals(all, a.snapshot.items)
        assertTrue(a.snapshot.sameContent(b.snapshot))
        assertEquals(exam, a.snapshot.examId)
        assertEquals(2, a.snapshot.setIndex)
        assertEquals(1.0, a.snapshot.marksPerQ, 0.0)
        assertEquals(0.25, a.snapshot.negativePerWrong, 0.0)
    }

    @Test
    fun missingPageIsReported() {
        val all = items(1, 120)
        val imp = import(page(exam, null, 1, 2, all.drop(60)))
        val a = imp.assembly as KeyQrAssembly.Missing
        assertEquals(listOf(0), a.pages)
        assertEquals(1, imp.pagesHave)
        assertEquals(2, imp.pagesTotal)
    }

    @Test
    fun pagesOfTwoExamsAreAMismatchAndCanBeTakenBack() {
        val p0 = page(exam, null, 0, 2, items(1, 60))
        val other = page("A2B3C4D5", null, 1, 2, items(61, 60))
        val imp = import(p0, other)
        assertTrue(imp.assembly is KeyQrAssembly.Mismatch)
        assertTrue(imp.dropLast().assembly is KeyQrAssembly.Missing)
    }

    @Test
    fun otherPicturesAreClassified() {
        assertTrue(AnswerKeyImport.classify(null) is KeyScan.None)
        assertTrue(AnswerKeyImport.classify("IQ1;K7M2QX9A;2;1;0;1") is KeyScan.NotKey)
        assertTrue(AnswerKeyImport.classify("https://example.com") is KeyScan.NotKey)
        assertTrue(AnswerKeyImport.classify("IQK2:K7M2QX9A") is KeyScan.OtherVersion)
        assertTrue(AnswerKeyImport.readImage(GrayImage(300, 300, ByteArray(300 * 300) { 0xFF.toByte() })) is KeyScan.None)
    }

    @Test
    fun existingSnapshotIsComparedByContent() {
        val all = items(1, 10)
        val snap = (import(page(exam, null, 0, 1, all)).assembly as KeyQrAssembly.Done).snapshot
        assertEquals(KeyExisting.NONE, AnswerKeyImport.existing(null, snap))
        assertEquals(KeyExisting.SAME, AnswerKeyImport.existing(snap.copy(createdAt = 5), snap))
        assertEquals(KeyExisting.DIFFERENT, AnswerKeyImport.existing(snap.copy(items = all.drop(1)), snap))
    }
}
