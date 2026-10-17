package com.intelliq.rifatplex.data

import java.security.SecureRandom

/**
 * The permanent id of one exam (step 4.2). It is printed into the QR code of every OMR sheet, so a scanned sheet can
 * be matched with the right answer snapshot (step 4.3) even months later or on another phone.
 *
 * 8 characters from [ALPHABET] (no 0 / O / 1 / I / L, which look alike on paper), e.g. `K7M2QX9A`. 31^8 is about
 * 8.5 * 10^11 possible ids, so two exams never get the same one in practice.
 *
 * When is an id made?
 *  - a new document ([AppData] with its default value) gets a new id, so "new paper" and a reset give a new exam;
 *  - a saved paper (History) keeps its id: opening it again gives the same exam;
 *  - an old save from before step 4.2 has no id: the editor makes one when it loads (and saves it at once), a paper in
 *    History / the cloud gets [derive] of its own paper id, so every device derives the same one.
 */
object ExamId {
    const val LENGTH = 8

    /** 23 letters (no I, L, O) + the digits 2..9 = 31 characters. */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private val random = SecureRandom()

    fun generate(): String {
        val sb = StringBuilder(LENGTH)
        repeat(LENGTH) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    fun isValid(s: String?): Boolean = s != null && s.length == LENGTH && s.all { it in ALPHABET }

    /** The same [seed] always gives the same id (FNV-1a 64 bit, written out as 8 characters of [ALPHABET]). */
    fun derive(seed: String): String {
        var h = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
        for (b in seed.toByteArray(Charsets.UTF_8)) {
            h = (h xor (b.toLong() and 0xFF)) * 0x100000001b3L
        }
        val base = ALPHABET.length.toLong()
        var v = java.lang.Long.remainderUnsigned(h, 852_891_037_441L) // 31^8
        val chars = CharArray(LENGTH)
        for (i in LENGTH - 1 downTo 0) {
            chars[i] = ALPHABET[(v % base).toInt()]
            v /= base
        }
        return String(chars)
    }
}
