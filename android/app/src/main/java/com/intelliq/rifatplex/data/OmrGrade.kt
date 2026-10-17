package com.intelliq.rifatplex.data

/*
 * Step 4.5 - from what the reader saw to marks. Pure Kotlin.
 *
 * Marking rules (the defaults of the plan, the settings screen comes with step 4.6):
 *  - right answer: + marksPerQ; wrong answer: - negativePerWrong (0 = no negative marking).
 *  - blank row: 0, never a deduction.
 *  - several bubbles filled (MULTI): 0 marks and listed for review. [GradeOptions.penalizeMulti] makes it cost like a wrong
 *    answer. Exception: when the question has SEVERAL right options and the student filled exactly those, it is right.
 *  - UNCERTAIN row (faint or two similar marks): 0 marks until the teacher decides; always listed for review. The teacher's
 *    decision comes in as an override (slot -> options), and the row is then graded as if the sheet said so.
 *  - question with a right option marked "none" in the snapshot, or a right option that has no bubble (> 4 options): not
 *    counted, and flagged once for the whole sheet.
 *  - the extra rows of the last sheet (rows after the last MCQ) are ignored.
 */

internal enum class QStatus { CORRECT, WRONG, BLANK, MULTI, UNCERTAIN, NO_KEY, UNMARKABLE }

internal enum class OmrFlag {
    /** No QR code, or one of another sheet version: the exam was not identified from the sheet. */
    QR_MISSING,
    QR_OTHER_VERSION,
    QR_FOREIGN,

    /** The photo shows a page of the ANSWER SHEET (its QR code starts with IQK), not a student's OMR sheet. */
    QR_KEY_PAGE,

    /** The QR code names a set but the student filled another set bubble. */
    SET_MISMATCH,

    /** The paper has named sets, the QR code has none and the student did not fill exactly one set bubble. */
    SET_NEEDED,

    /** No snapshot of this exam / set on this device. */
    NO_SNAPSHOT,

    /** The roll number has a column that is not clearly filled. */
    ROLL_INCOMPLETE,
    LOW_CONTRAST,
    OUT_OF_IMAGE,

    /** At least one row is UNCERTAIN or MULTI. */
    NEEDS_REVIEW,

    /** Some questions of the snapshot have no right option / no bubble for it, so they are not counted. */
    KEY_INCOMPLETE,
}

/** What one question of the sheet came to. [slot] = row of the sheet (0..49). */
internal class QResult(
    val printedNumber: Int,
    val slot: Int,
    val status: QStatus,
    /** Options taken as marked (after any override). */
    val marked: Set<Int>,
    val correct: Set<Int>,
    /** Marks this question gave (negative for a deduction). */
    val delta: Double,
    /** True when the row came from the teacher's override, not from the photo. */
    val overridden: Boolean = false,
)

internal class GradeOptions(val penalizeMulti: Boolean = false)

internal class OmrResult(
    val examId: String?,
    val setIndex: Int?,
    val page: Int,
    val pages: Int,
    val roll: String?,
    val reg: String?,
    val subject: String?,
    val rows: List<QResult>,
    val score: Double,
    /** Marks of a student with every counted question right (this sheet only). */
    val total: Double,
    val flags: Set<OmrFlag>,
    /** The marking rules this result was graded with (typed rules or the exam's saved ones). */
    val marksPerQ: Double = 1.0,
    val negativePerWrong: Double = 0.0,
) {
    val correct: Int get() = rows.count { it.status == QStatus.CORRECT }
    val wrong: Int get() = rows.count { it.status == QStatus.WRONG }
    val blank: Int get() = rows.count { it.status == QStatus.BLANK }
    val multi: Int get() = rows.count { it.status == QStatus.MULTI }
    val uncertain: Int get() = rows.count { it.status == QStatus.UNCERTAIN }

    /** Rows a teacher has to look at (sorted by sheet position). */
    val review: List<QResult> get() = rows.filter { it.status == QStatus.UNCERTAIN || it.status == QStatus.MULTI }
}

/** Which answer key to use for a sheet, and why not when none. */
internal class SnapshotPick(val snapshot: AnswerKeySnapshot?, val flags: Set<OmrFlag>)

internal object OmrGrade {

    /**
     * Chooses the snapshot of one exam ([snaps] = every set of the exam) for a sheet.
     *  - QR names a set: that set. A different set bubble filled by the student is only reported ([OmrFlag.SET_MISMATCH]).
     *  - QR has no set: the exam without sets if it has no named set; else the set the student filled; else [OmrFlag.SET_NEEDED].
     */
    fun pickSnapshot(snaps: List<AnswerKeySnapshot>, qrSet: Int?, filledSet: Int?): SnapshotPick {
        val flags = LinkedHashSet<OmrFlag>()
        val named = snaps.filter { it.setIndex != null }
        val chosen: AnswerKeySnapshot? = when {
            qrSet != null -> {
                if (filledSet != null && filledSet != qrSet) flags += OmrFlag.SET_MISMATCH
                snaps.firstOrNull { it.setIndex == qrSet }
            }
            named.isEmpty() -> snaps.firstOrNull { it.setIndex == null }
            filledSet != null -> named.firstOrNull { it.setIndex == filledSet }
            else -> {
                flags += OmrFlag.SET_NEEDED
                null
            }
        }
        if (chosen == null && OmrFlag.SET_NEEDED !in flags) flags += OmrFlag.NO_SNAPSHOT
        return SnapshotPick(chosen, flags)
    }

    /**
     * Marks one sheet. [page] / [pages] come from the QR code (0 / 1 when there is none). [overrides] = the teacher's
     * corrections, slot -> options really marked (empty set = blank). [rules] replaces the marks stored in the snapshot.
     */
    fun grade(
        read: OmrSheetRead,
        snap: AnswerKeySnapshot,
        page: Int = read.qrPayload?.page ?: 0,
        pages: Int = read.qrPayload?.pages ?: 1,
        rules: GradingRules? = null,
        options: GradeOptions = GradeOptions(),
        overrides: Map<Int, Set<Int>> = emptyMap(),
        extraFlags: Set<OmrFlag> = emptySet(),
    ): OmrResult {
        val r = (rules ?: GradingRules(snap.marksPerQ, snap.negativePerWrong)).sane()
        val results = ArrayList<QResult>()
        var score = 0.0
        var total = 0.0
        var keyIncomplete = false
        for (slot in 0 until OmrGeometry.ROWS_PER_PAGE) {
            val item = snap.itemAt(page, slot) ?: continue // extra row of the last sheet
            val row = read.rows[slot]
            val ov = overrides[slot]
            val marked: Set<Int> = ov ?: row.marked
            val state: MarkState = if (ov != null) (if (ov.isEmpty()) MarkState.BLANK else if (ov.size == 1) MarkState.FILLED else MarkState.MULTI) else row.state

            val q: QResult = when {
                !item.hasAnswer -> { keyIncomplete = true; QResult(item.printedNumber, slot, QStatus.NO_KEY, marked, item.correct, 0.0, ov != null) }
                !item.markable -> { keyIncomplete = true; QResult(item.printedNumber, slot, QStatus.UNMARKABLE, marked, item.correct, 0.0, ov != null) }
                else -> {
                    total += r.marksPerQ
                    when (state) {
                        MarkState.BLANK -> QResult(item.printedNumber, slot, QStatus.BLANK, emptySet(), item.correct, 0.0, ov != null)
                        MarkState.UNCERTAIN -> QResult(item.printedNumber, slot, QStatus.UNCERTAIN, marked, item.correct, 0.0)
                        MarkState.FILLED, MarkState.MULTI ->
                            if (item.matches(marked)) QResult(item.printedNumber, slot, QStatus.CORRECT, marked, item.correct, r.marksPerQ, ov != null)
                            else if (state == MarkState.MULTI && item.correct.size == 1)
                                QResult(item.printedNumber, slot, QStatus.MULTI, marked, item.correct, if (options.penalizeMulti) -r.negativePerWrong else 0.0, ov != null)
                            else QResult(item.printedNumber, slot, QStatus.WRONG, marked, item.correct, -r.negativePerWrong, ov != null)
                    }
                }
            }
            score += q.delta
            results += q
        }

        val flags = LinkedHashSet<OmrFlag>(extraFlags)
        when (read.qr) {
            is QrStatus.None -> flags += OmrFlag.QR_MISSING
            is QrStatus.OtherVersion -> flags += OmrFlag.QR_OTHER_VERSION
            is QrStatus.Foreign -> flags += OmrFlag.QR_FOREIGN
            is QrStatus.KeyPage -> flags += OmrFlag.QR_KEY_PAGE
            is QrStatus.Found -> {}
        }
        if (read.lowContrast) flags += OmrFlag.LOW_CONTRAST
        if (read.outOfImage > 0) flags += OmrFlag.OUT_OF_IMAGE
        if (read.roll != null && !read.roll.complete) flags += OmrFlag.ROLL_INCOMPLETE
        if (results.any { it.status == QStatus.UNCERTAIN || it.status == QStatus.MULTI }) flags += OmrFlag.NEEDS_REVIEW
        if (keyIncomplete) flags += OmrFlag.KEY_INCOMPLETE

        return OmrResult(
            examId = snap.examId,
            setIndex = snap.setIndex,
            page = page,
            pages = pages,
            roll = read.roll?.text,
            reg = read.reg?.text,
            subject = read.subject?.text,
            rows = results,
            score = score,
            total = total,
            flags = flags,
            marksPerQ = r.marksPerQ,
            negativePerWrong = r.negativePerWrong,
        )
    }
}
