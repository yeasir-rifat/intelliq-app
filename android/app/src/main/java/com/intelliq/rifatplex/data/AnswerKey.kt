package com.intelliq.rifatplex.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * Step 4.3: answer snapshot.
 *
 * When a student's sheet is scanned (steps 4.4 / 4.5) the app must know, for every PRINTED question number, which
 * option is right. The question paper can change after printing, and with Document AutoFix the printed numbers are not
 * the order of the list, so the answers are frozen into a snapshot when the sheet is exported or the exam is saved.
 *
 * One snapshot = one exam id + one set (the same two things the QR code of the sheet carries).
 */

/**
 * Marking rules of one exam. Used by the grading of step 4.5; the settings screen for them comes with step 4.6.
 * [marksPerQ] marks for a right answer, [negativePerWrong] marks taken off for a wrong one (0 = no negative marking).
 */
data class GradingRules(
    val marksPerQ: Double = 1.0,
    val negativePerWrong: Double = 0.0,
) {
    /** The same rules with impossible values (negative, NaN, huge) replaced by the defaults. */
    fun sane(): GradingRules {
        val m = marksPerQ.takeIf { it.isFinite() && it > 0.0 && it <= MAX } ?: 1.0
        val n = negativePerWrong.takeIf { it.isFinite() && it >= 0.0 && it <= MAX } ?: 0.0
        return GradingRules(m, n)
    }

    companion object {
        const val MAX = 1000.0
    }
}

/**
 * One MCQ of the sheet. [printedNumber] is the number printed on the question paper (and on the OMR row).
 * [correct] holds the POSITIONS of the right options as printed: 0 = ক / A, 1 = খ / B ... In a shuffled set these are
 * the positions after the shuffle. Empty = the MCQ has no right option marked.
 */
data class KeyItem(val printedNumber: Int, val correct: Set<Int>) {
    val hasAnswer: Boolean get() = correct.isNotEmpty()

    /** Every right option has a bubble on the sheet (the sheet has [OmrGeometry.OPTIONS] bubbles per row). */
    val markable: Boolean get() = correct.all { it in 0 until OmrGeometry.OPTIONS }

    /** "All must match": with several right options the student has to fill exactly those. No answer = never right. */
    fun matches(marked: Set<Int>): Boolean = correct.isNotEmpty() && marked == correct
}

/** Fewer than one sheet still prints one sheet; every 50 answers start another. */
fun omrSheetCount(mcqCount: Int): Int =
    if (mcqCount <= 0) 1 else (mcqCount + OmrGeometry.ROWS_PER_PAGE - 1) / OmrGeometry.ROWS_PER_PAGE

/**
 * The rows of every OMR sheet: the printed numbers of the MCQs, 50 per sheet. A sheet is always full (50 rows): the
 * rows after the last MCQ are simply numbered on, so 20 MCQs still give a 50-row sheet. Those extra rows have no
 * [KeyItem]; a scanner must ignore them.
 */
fun omrRows(numbers: List<Int>): List<List<Int>> {
    val rows = OmrGeometry.ROWS_PER_PAGE
    if (numbers.isEmpty()) return listOf(emptyList())
    return numbers.chunked(rows).map { c ->
        if (c.size >= rows) c else c + (1..(rows - c.size)).map { c.last() + it }
    }
}

data class AnswerKeySnapshot(
    val examId: String,
    /** 0..9 = position among the named sets (same as the QR code), null = the paper has no sets. */
    val setIndex: Int?,
    val geomVersion: Int,
    /** Sorted by printed number. Item number i sits on sheet i / 50, row i % 50 (see [itemAt]). */
    val items: List<KeyItem>,
    val marksPerQ: Double,
    val negativePerWrong: Double,
    val createdAt: Long,
) {
    val storeKey: String get() = AnswerKey.keyOf(examId, setIndex)

    /** Number of printed OMR sheets of this set. */
    val sheets: Int get() = omrSheetCount(items.size)

    /** The MCQ that sits in row [slot] (0..49, left block first) of sheet [page] (0-based); null for a blank extra row. */
    fun itemAt(page: Int, slot: Int): KeyItem? {
        if (page < 0 || slot < 0 || slot >= OmrGeometry.ROWS_PER_PAGE) return null
        return items.getOrNull(page * OmrGeometry.ROWS_PER_PAGE + slot)
    }

    /** MCQs without a right option marked (they are not counted when checking). */
    val unanswered: Int get() = items.count { !it.hasAnswer }

    /** MCQs whose right option has no bubble on the sheet (more than 4 options). */
    val unmarkable: Int get() = items.count { !it.markable }

    /** Marks of a student who got every counted MCQ right. */
    val maxScore: Double get() = items.count { it.hasAnswer } * marksPerQ

    /** Same answers and rules (the time of creation does not count). */
    fun sameContent(o: AnswerKeySnapshot): Boolean =
        examId == o.examId && setIndex == o.setIndex && geomVersion == o.geomVersion &&
            marksPerQ == o.marksPerQ && negativePerWrong == o.negativePerWrong && items == o.items
}

object AnswerKey {
    const val FORMAT = 1
    private const val MAX_ITEMS = 5000

    /** Name under which a snapshot is stored: exam id and set position, e.g. `K7M2QX9A.2` or `K7M2QX9A.-`. */
    fun keyOf(examId: String, setIndex: Int?): String = "$examId.${setIndex ?: "-"}"

    /**
     * Printed number of every question without Document AutoFix: exactly what the paper prints
     * ([arrangeSections]). With AutoFix on the numbers must come from the real layout (NumberingProbe).
     */
    fun plainNumbers(questions: List<Question>, settings: DocSettings): Map<String, Int> =
        arrangeSections(questions, settings).associate { it.q.id to it.n }

    /**
     * The MCQs of [questions] with their printed number, in number order. [numbers] maps question id -> printed
     * number. The OMR rows, the answer sheet grid and the snapshot all use this one list, so they can never disagree.
     */
    fun printedMcqs(questions: List<Question>, numbers: Map<String, Int>): List<Pair<Int, Question>> =
        questions
            .filter { !it.isEmpty() && it.kind() == QType.MCQ }
            .mapNotNull { q -> numbers[q.id]?.let { n -> n to q } }
            .sortedBy { it.first }

    /** Positions (0 = first) of the options marked correct, as printed. */
    fun correctPositions(q: Question): Set<Int> = q.options.indices.filter { q.options[it].correct }.toSet()

    fun build(
        examId: String,
        setIndex: Int?,
        questions: List<Question>,
        numbers: Map<String, Int>,
        rules: GradingRules,
        now: Long = System.currentTimeMillis(),
    ): AnswerKeySnapshot {
        val r = rules.sane()
        return AnswerKeySnapshot(
            examId = examId,
            setIndex = setIndex,
            geomVersion = OmrGeometry.VERSION,
            items = printedMcqs(questions, numbers).map { (n, q) -> KeyItem(n, correctPositions(q)) },
            marksPerQ = r.marksPerQ,
            negativePerWrong = r.negativePerWrong,
            createdAt = now,
        )
    }

    // ----- JSON (hand written, like Persist.kt) -----

    fun encode(s: AnswerKeySnapshot): String {
        val o = JSONObject()
            .put("v", FORMAT)
            .put("examId", s.examId)
            .put("geom", s.geomVersion)
            .put("marks", s.marksPerQ)
            .put("neg", s.negativePerWrong)
            .put("at", s.createdAt)
        if (s.setIndex != null) o.put("set", s.setIndex)
        o.put(
            "items",
            JSONArray().apply {
                s.items.forEach { it0 ->
                    put(JSONArray().put(it0.printedNumber).put(JSONArray(it0.correct.sorted())))
                }
            },
        )
        return o.toString()
    }

    /** Null when the text is not a valid snapshot (wrong format, bad id, impossible numbers). */
    fun decode(json: String): AnswerKeySnapshot? = try {
        val o = JSONObject(json)
        val examId = o.optString("examId")
        val set: Int? = if (o.has("set") && !o.isNull("set")) o.getInt("set") else null
        val arr = o.getJSONArray("items")
        if (o.optInt("v", 0) != FORMAT || !ExamId.isValid(examId) || (set != null && set !in 0..QrPayload.MAX_SET_INDEX) || arr.length() > MAX_ITEMS) {
            null
        } else {
            val items = ArrayList<KeyItem>(arr.length())
            var ok = true
            for (i in 0 until arr.length()) {
                val row = arr.getJSONArray(i)
                val n = row.getInt(0)
                val c = row.getJSONArray(1)
                val set0 = HashSet<Int>()
                for (j in 0 until c.length()) {
                    val p = c.getInt(j)
                    if (p < 0 || p > 25) ok = false else set0.add(p)
                }
                if (n < 1) ok = false
                items.add(KeyItem(n, set0))
            }
            val rules = GradingRules(o.optDouble("marks", 1.0), o.optDouble("neg", 0.0)).sane()
            if (!ok) null else AnswerKeySnapshot(
                examId = examId,
                setIndex = set,
                geomVersion = o.optInt("geom", OmrGeometry.VERSION),
                items = items.sortedBy { it.printedNumber },
                marksPerQ = rules.marksPerQ,
                negativePerWrong = rules.negativePerWrong,
                createdAt = o.optLong("at", 0L),
            )
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * The snapshots of this device, in SharedPreferences (one entry per exam id + set). A new snapshot of the same exam id
 * and set REPLACES the old one: the last export or save is the one the printed sheets match. At most [MAX_SNAPSHOTS]
 * are kept, the oldest go first. The cloud copy (CloudSync) follows through [sync].
 */
class AnswerKeyStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("intelliq_answer_keys", Context.MODE_PRIVATE)
    private val lock = Any()

    /** Called after every LOCAL change so the cloud copy follows. Never called for changes that came from the cloud. */
    @Volatile var sync: ((AnswerKeySnapshot) -> Unit)? = null

    companion object {
        @Volatile private var instance: AnswerKeyStore? = null
        fun get(context: Context): AnswerKeyStore =
            instance ?: synchronized(this) { instance ?: AnswerKeyStore(context.applicationContext).also { instance = it } }

        const val MAX_SNAPSHOTS = 300
        private const val PREFIX = "k."
    }

    private fun read(key: String): AnswerKeySnapshot? = prefs.getString(PREFIX + key, null)?.let { AnswerKey.decode(it) }

    fun get(examId: String, setIndex: Int?): AnswerKeySnapshot? = synchronized(lock) { read(AnswerKey.keyOf(examId, setIndex)) }

    /** Every set of one exam. */
    fun forExam(examId: String): List<AnswerKeySnapshot> = all().filter { it.examId == examId }

    fun all(): List<AnswerKeySnapshot> = synchronized(lock) {
        prefs.all.keys.filter { it.startsWith(PREFIX) }.mapNotNull { read(it.removePrefix(PREFIX)) }
    }

    /**
     * Keeps [s] as the snapshot of its exam id + set. The same content as the one already kept changes nothing (no new
     * time, no upload). Returns true when something was written.
     */
    fun put(s: AnswerKeySnapshot): Boolean {
        val changed = synchronized(lock) {
            val old = read(s.storeKey)
            if (old != null && old.sameContent(s)) false
            else {
                prefs.edit().putString(PREFIX + s.storeKey, AnswerKey.encode(s)).apply()
                prune()
                true
            }
        }
        if (changed) sync?.invoke(s)
        return changed
    }

    /** Cloud -> device: a remote snapshot wins only when it is newer than the one kept here. Does not call [sync]. */
    fun putRemote(list: List<AnswerKeySnapshot>) {
        synchronized(lock) {
            val ed = prefs.edit()
            var any = false
            list.forEach { s ->
                val old = read(s.storeKey)
                if (old == null || old.createdAt < s.createdAt) {
                    ed.putString(PREFIX + s.storeKey, AnswerKey.encode(s))
                    any = true
                }
            }
            if (any) ed.apply()
            prune()
        }
    }

    /** Another account signed in (or this one signed out): nothing of the previous owner stays on the device. */
    fun clearAll() {
        synchronized(lock) {
            val ed = prefs.edit()
            prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach { ed.remove(it) }
            ed.apply()
        }
    }

    // callers hold [lock]
    private fun prune() {
        val keys = prefs.all.keys.filter { it.startsWith(PREFIX) }
        if (keys.size <= MAX_SNAPSHOTS) return
        val ed = prefs.edit()
        keys.map { k -> k to (read(k.removePrefix(PREFIX))?.createdAt ?: 0L) }
            .sortedBy { it.second }
            .take(keys.size - MAX_SNAPSHOTS)
            .forEach { ed.remove(it.first) }
        ed.apply()
    }
}
