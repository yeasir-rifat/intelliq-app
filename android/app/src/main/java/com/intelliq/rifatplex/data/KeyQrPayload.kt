package com.intelliq.rifatplex.data

import kotlin.math.roundToInt

/**
 * Step 4.8: the QR code on every page of the answer sheet ("উত্তরমালা", DocKind.KEY).
 *
 * It is the BRIDGE between the answer sheet and the OMR sheets of the same exam:
 *  - it carries the same exam id / set / geometry version as the QR code of the OMR sheets ([QrPayload]), so a phone
 *    that scans both knows they belong together;
 *  - it also carries the answers of the printed page itself, so the answer sheet can be scanned on ANY phone (no cloud,
 *    no OCR, no AI) and turned back into an [AnswerKeySnapshot] with [assemble].
 *
 * Text: `IQK1:<examId>:<set>:<geom>:<page>:<pages>:<marks>:<neg>:<numbers>:<answers>`, for example
 * `IQK1:K7M2QX9A:2:1:0:1:100:25:1-4.7:B1CA0` (the numbers 1,2,3,4,7 with their answers).
 *
 *  - set / geom      the same meaning as in [QrPayload] (`-` = no set); geom = [OmrGeometry.VERSION]
 *  - page / pages    0-based number of this ANSWER-SHEET page and the number of answer-sheet pages (not OMR sheets)
 *  - marks / neg     marks per right answer and marks taken off per wrong one, in hundredths (100 = 1 mark, 25 = 0.25)
 *  - numbers         the printed question numbers of this page as ranges joined by `.`: `1-30.35-40.45`
 *  - answers         one character per number: a 5-bit mask of the right options (bit 0 = ক/A ... bit 4 = ঙ/E) written
 *                    with 0-9 A-V; `0` = no right option marked; `W` = a right option beyond the 5th (it cannot be
 *                    marked on the sheet anyway, [KeyItem.markable] is false for it; the exact position is not kept)
 *
 * Only characters of the QR "alphanumeric" set are used (digits, capital letters, `:` `-` `.`), so the code stays small:
 * 100 consecutive answers fit a version-6 code (41 x 41 modules); [MAX_TEXT] limits the size for scattered numbers.
 */
data class KeyQrPayload(
    val examId: String,
    val setIndex: Int?,
    val geomVersion: Int,
    val page: Int,
    val pages: Int,
    val marksX100: Int,
    val negX100: Int,
    val numbers: List<Int>,
    val answers: List<Set<Int>>,
) {
    /** The text in the QR code. Only call it on a valid payload (see [forPage] / [decode]). */
    fun encode(): String = listOf(
        PREFIX, examId, setIndex?.toString() ?: NO_SET, geomVersion.toString(), page.toString(), pages.toString(),
        marksX100.toString(), negX100.toString(), ranges(numbers), answers.joinToString("") { answerChar(it).toString() },
    ).joinToString(SEP.toString())

    /** The page's answers as snapshot items. */
    fun items(): List<KeyItem> = numbers.indices.map { KeyItem(numbers[it], answers[it]) }

    companion object {
        const val PREFIX = "IQK1"
        const val SEP = ':'
        const val NO_SET = "-"
        const val MAX_PAGES = 99
        const val MAX_ENTRIES = 500

        /**
         * Longest text a printed page may carry. 220 characters is still a version-8 code (49 modules, 0.53 mm each in
         * the 26 mm code), about as fine as the OMR sheet's own QR code. A page with many scattered question numbers
         * (an MCQ every other number) can exceed it; [forPage] then gives null and no QR is printed on that page.
         */
        const val MAX_TEXT = 220
        private const val MAX_X100 = 100_000
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUV" // 32 values = 5 bits
        private const val BEYOND = 'W'
        private const val MASK_OPTIONS = 5

        /** Is the text from an answer-sheet QR code (any version of the format)? */
        fun isKeyPage(text: String): Boolean = text.startsWith("IQK")

        private fun answerChar(correct: Set<Int>): Char {
            if (correct.any { it < 0 || it >= MASK_OPTIONS }) return BEYOND
            var mask = 0
            for (p in correct) mask = mask or (1 shl p)
            return ALPHABET[mask]
        }

        private fun answerSet(c: Char): Set<Int>? {
            if (c == BEYOND) return setOf(MASK_OPTIONS)
            val mask = ALPHABET.indexOf(c)
            if (mask < 0) return null
            return (0 until MASK_OPTIONS).filter { mask and (1 shl it) != 0 }.toSet()
        }

        private fun ranges(n: List<Int>): String {
            val sb = StringBuilder()
            var i = 0
            while (i < n.size) {
                var j = i
                while (j + 1 < n.size && n[j + 1] == n[j] + 1) j++
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(n[i])
                if (j > i) sb.append('-').append(n[j])
                i = j + 1
            }
            return sb.toString()
        }

        /** Null when the text is not a well-formed list of ranges (or is longer than [MAX_ENTRIES]). */
        private fun parseRanges(s: String): List<Int>? {
            val out = ArrayList<Int>()
            for (part in s.split('.')) {
                val ab = part.split('-')
                when (ab.size) {
                    1 -> out.add(ab[0].toIntOrNull() ?: return null)
                    2 -> {
                        val a = ab[0].toIntOrNull() ?: return null
                        val b = ab[1].toIntOrNull() ?: return null
                        if (b <= a || b - a + 1 > MAX_ENTRIES) return null
                        for (v in a..b) out.add(v)
                    }
                    else -> return null
                }
                if (out.size > MAX_ENTRIES) return null
            }
            return out
        }

        private fun valid(p: KeyQrPayload): Boolean =
            ExamId.isValid(p.examId) &&
                (p.setIndex == null || p.setIndex in 0..QrPayload.MAX_SET_INDEX) &&
                p.geomVersion in 1..99 &&
                p.pages in 1..MAX_PAGES &&
                p.page in 0 until p.pages &&
                p.marksX100 in 1..MAX_X100 &&
                p.negX100 in 0..MAX_X100 &&
                p.numbers.isNotEmpty() && p.numbers.size <= MAX_ENTRIES &&
                p.numbers.size == p.answers.size &&
                p.numbers.first() >= 1 &&
                p.numbers.zipWithNext().all { (a, b) -> b > a }

        /**
         * The payload of one answer-sheet page, or null when a value is out of range or the page has no MCQ
         * (no QR is printed then). [items] are the entries of THIS page in number order.
         */
        fun forPage(examId: String, setIndex: Int?, page: Int, pages: Int, items: List<KeyItem>, rules: GradingRules): KeyQrPayload? {
            val r = rules.sane()
            val p = KeyQrPayload(
                examId = examId,
                setIndex = setIndex,
                geomVersion = OmrGeometry.VERSION,
                page = page,
                pages = pages,
                marksX100 = (r.marksPerQ * 100).roundToInt().coerceAtLeast(1),
                negX100 = (r.negativePerWrong * 100).roundToInt().coerceAtLeast(0),
                numbers = items.map { it.printedNumber },
                answers = items.map { it.correct },
            )
            return if (valid(p) && p.encode().length <= MAX_TEXT) p else null
        }

        /** Reads the text of a QR code. Null when it is not exactly the format of [encode] (so `IQK2:...` is null too). */
        fun decode(text: String): KeyQrPayload? {
            val f = text.split(SEP)
            if (f.size != 10 || f[0] != PREFIX) return null
            val set: Int? = if (f[2] == NO_SET) null else (f[2].toIntOrNull() ?: return null)
            val numbers = parseRanges(f[8]) ?: return null
            val answers = f[9].map { answerSet(it) ?: return null }
            val p = KeyQrPayload(
                examId = f[1],
                setIndex = set,
                geomVersion = f[3].toIntOrNull() ?: return null,
                page = f[4].toIntOrNull() ?: return null,
                pages = f[5].toIntOrNull() ?: return null,
                marksX100 = f[6].toIntOrNull() ?: return null,
                negX100 = f[7].toIntOrNull() ?: return null,
                numbers = numbers,
                answers = answers,
            )
            if (!valid(p)) return null
            return if (p.encode() == text) p else null // refuses "01", "+1", " 1" and other non-canonical forms
        }

        /**
         * Puts the scanned pages of ONE answer sheet together. All pages must say the same exam id, set, geometry
         * version, page count and marking rules; every page 0 until pages must be there. Pages may come in any order,
         * and the same page scanned twice counts once.
         */
        fun assemble(parts: List<KeyQrPayload>, now: Long = System.currentTimeMillis()): KeyQrAssembly {
            if (parts.isEmpty()) return KeyQrAssembly.Mismatch
            val first = parts[0]
            val sameSheet = parts.all {
                it.examId == first.examId && it.setIndex == first.setIndex && it.geomVersion == first.geomVersion &&
                    it.pages == first.pages && it.marksX100 == first.marksX100 && it.negX100 == first.negX100
            }
            if (!sameSheet) return KeyQrAssembly.Mismatch
            val byPage = HashMap<Int, KeyQrPayload>()
            for (p in parts) {
                val old = byPage[p.page]
                if (old != null && old != p) return KeyQrAssembly.Mismatch // two different codes claim the same page
                byPage[p.page] = p
            }
            val missing = (0 until first.pages).filter { it !in byPage }
            if (missing.isNotEmpty()) return KeyQrAssembly.Missing(missing)
            val items = (0 until first.pages).flatMap { byPage.getValue(it).items() }
            if (!items.zipWithNext().all { (a, b) -> b.printedNumber > a.printedNumber }) return KeyQrAssembly.Mismatch
            return KeyQrAssembly.Done(
                AnswerKeySnapshot(
                    examId = first.examId,
                    setIndex = first.setIndex,
                    geomVersion = first.geomVersion,
                    items = items,
                    marksPerQ = first.marksX100 / 100.0,
                    negativePerWrong = first.negX100 / 100.0,
                    createdAt = now,
                ),
            )
        }
    }
}

/** What [KeyQrPayload.assemble] made of the scanned answer-sheet pages. */
sealed class KeyQrAssembly {
    /** All pages are there: the answer snapshot of the exam / set. */
    class Done(val snapshot: AnswerKeySnapshot) : KeyQrAssembly()

    /** These 0-based pages of the answer sheet have not been scanned yet. */
    class Missing(val pages: List<Int>) : KeyQrAssembly()

    /** The codes do not belong to one answer sheet (another exam, set, version or rules), or contradict each other. */
    object Mismatch : KeyQrAssembly()
}
