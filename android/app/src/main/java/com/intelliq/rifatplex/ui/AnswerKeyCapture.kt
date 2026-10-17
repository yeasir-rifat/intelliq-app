package com.intelliq.rifatplex.ui

import android.app.Activity
import com.intelliq.rifatplex.data.AnswerKey
import com.intelliq.rifatplex.data.AnswerKeyStore
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.ExamId
import com.intelliq.rifatplex.data.OmrGeometry
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.formatNumber

/**
 * What [AnswerKeyCapture.capture] found, for the warnings the teacher sees.
 *  - [unanswered]  MCQs with no right option marked (they are not counted when sheets are checked)
 *  - [unmarkable]  MCQs whose right option is the 5th or later (the sheet has 4 bubbles per row)
 *  - [skippedSets] sets after the 10th: the QR code of their sheets has no set number, so they cannot be kept apart
 *  - [failed]      sets whose printed numbers could not be learned in time (AutoFix layout), nothing was saved for them
 */
data class KeyCaptureResult(val saved: Int, val unanswered: Int, val unmarkable: Int, val skippedSets: Int, val failed: Int) {
    val hasWarning: Boolean get() = unanswered > 0 || unmarkable > 0 || skippedSets > 0 || failed > 0
}

/**
 * Step 4.3: freezes the answers of the exam into answer snapshots ([AnswerKeyStore]), one per set (one for a paper
 * without sets). Called whenever an OMR sheet / answer sheet is exported and whenever the exam is saved.
 *
 * The printed numbers come from [AnswerKey.plainNumbers] without Document AutoFix and from a real layout pass
 * ([PdfExport.probeNumbers]) with it, the same two sources the OMR sheet itself uses, so the snapshot always matches
 * the numbers printed on the paper and on the sheet.
 */
object AnswerKeyCapture {

    /** Must run on the main thread (the AutoFix layout pass uses a view). */
    suspend fun capture(activity: Activity, d: AppData, store: AnswerKeyStore): KeyCaptureResult {
        if (!ExamId.isValid(d.examId)) return KeyCaptureResult(0, 0, 0, 0, 0)
        val setCodes = if (d.setCode.enabled) d.generatedSets.map { it.code } else emptyList()
        val groups: List<Pair<String?, List<Question>>> =
            if (d.setCode.enabled && d.generatedSets.isNotEmpty()) d.generatedSets.map { it.code to it.questions.filter { q -> !q.isEmpty() } }
            else listOf<Pair<String?, List<Question>>>(null to d.questions.filter { !it.isEmpty() })

        var saved = 0
        var unanswered = 0
        var unmarkable = 0
        var skipped = 0
        var failed = 0
        for ((code, qs) in groups) {
            if (qs.none { it.kind() == QType.MCQ }) continue
            val setIndex = OmrGeometry.setIndexOf(code, setCodes)
            if (code != null && setIndex == null) {
                skipped++
                continue
            }
            val numbers = if (d.settings.autoFix) PdfExport.probeNumbers(activity, d, qs, code) else AnswerKey.plainNumbers(qs, d.settings)
            if (numbers == null) {
                failed++
                continue
            }
            val snap = AnswerKey.build(d.examId, setIndex, qs, numbers, d.grading)
            store.put(snap)
            saved++
            unanswered += snap.unanswered
            unmarkable += snap.unmarkable
        }
        return KeyCaptureResult(saved, unanswered, unmarkable, skipped, failed)
    }
}

/**
 * The warning sentences for a capture, each one starting with a space so they can follow the "saved" message
 * directly; empty when there is nothing to warn about.
 */
fun keyWarningText(cap: KeyCaptureResult, lang: Lang): String {
    if (!cap.hasWarning) return ""
    val bn = lang == Lang.BN
    val sb = StringBuilder()
    if (cap.unanswered > 0) sb.append(' ').append(trKey("key.warn.unanswered", lang).replace("{n}", formatNumber(cap.unanswered, bn)))
    if (cap.unmarkable > 0) sb.append(' ').append(trKey("key.warn.unmarkable", lang).replace("{n}", formatNumber(cap.unmarkable, bn)))
    if (cap.skippedSets > 0) sb.append(' ').append(trKey("key.warn.sets", lang))
    if (cap.failed > 0) sb.append(' ').append(trKey("key.warn.failed", lang))
    return sb.toString()
}
