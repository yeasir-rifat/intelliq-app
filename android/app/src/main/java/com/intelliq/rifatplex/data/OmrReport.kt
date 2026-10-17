package com.intelliq.rifatplex.data

/*
 * Step 4.7 - the batch report of an OMR evaluation: ranking and the CSV file. Pure Kotlin (JVM-testable).
 *
 *  - Only graded sheets are ranked (a sheet without an answer key or one that could not be read has no score).
 *  - Equal scores share a rank and the next rank skips ("1, 2, 2, 4"); inside a tie the order is by roll number.
 *  - A roll number that appears on more than one graded sheet is flagged ([RankedRow.duplicateRoll]): the same student
 *    scanned twice, or two students who filled the same roll. A roll with an unfilled column ("?") is never compared.
 *  - CSV: UTF-8 with a byte-order mark (so Excel shows Bangla), CRLF lines, quotes only where needed. Text cells that
 *    could be read as a formula by a spreadsheet (start with = + @ or a minus sign) get a leading apostrophe.
 */

internal class ReportRow(
    val sheet: String,
    val roll: String,
    val setIndex: Int?,
    val score: Double,
    val total: Double,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    /** The sheet still has rows or flags a teacher should look at. */
    val review: Boolean,
    /** 0-based sheet of the student's paper and how many sheets the exam has (> 50 MCQs = several sheets per student). */
    val page: Int = 0,
    val pages: Int = 1,
)

internal class RankedRow(val rank: Int, val row: ReportRow, val duplicateRoll: Boolean)

internal object OmrReport {

    /** The roll as a key for duplicate detection; null when it is empty, "-" or has an unfilled column. */
    fun rollKey(roll: String): String? = roll.trim().takeIf { it.isNotEmpty() && it != "-" && '?' !in it }

    /**
     * A student of an exam with more than 50 MCQs hands in several sheets (page 0, 1, ...) with the same roll number.
     * Those sheets are ONE student: scores and counts are added up, so the ranking is per student and the same roll on
     * page 0 and page 1 is not reported as a duplicate. A student with a sheet missing is added up too but marked for
     * review. When the same page appears twice for one roll (a real duplicate) nothing is merged, so [rank] flags it.
     */
    fun mergePages(rows: List<ReportRow>): List<ReportRow> {
        fun key(r: ReportRow): Pair<String, Int?>? = if (r.pages > 1) rollKey(r.roll)?.let { it to r.setIndex } else null
        val groups = LinkedHashMap<Pair<String, Int?>, MutableList<ReportRow>>()
        for (r in rows) key(r)?.let { groups.getOrPut(it) { ArrayList() }.add(r) }
        val out = ArrayList<ReportRow>(rows.size)
        val done = HashSet<Pair<String, Int?>>()
        for (r in rows) {
            val k = key(r)
            if (k == null) {
                out += r
                continue
            }
            if (!done.add(k)) continue
            val g = groups.getValue(k)
            val distinct = g.map { it.page }.toSet().size == g.size && g.map { it.pages }.toSet().size == 1
            if (!distinct) {
                out += g
                continue
            }
            val ordered = g.sortedBy { it.page }
            out += ReportRow(
                sheet = ordered.joinToString("+") { it.sheet },
                roll = ordered[0].roll,
                setIndex = k.second,
                score = ordered.sumOf { it.score },
                total = ordered.sumOf { it.total },
                correct = ordered.sumOf { it.correct },
                wrong = ordered.sumOf { it.wrong },
                blank = ordered.sumOf { it.blank },
                review = ordered.any { it.review } || ordered.size < ordered[0].pages,
            )
        }
        return out
    }

    fun rank(input: List<ReportRow>): List<RankedRow> {
        val rows = mergePages(input)
        val dup = rows.mapNotNull { rollKey(it.roll) }.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        val sorted = rows.sortedWith(compareByDescending<ReportRow> { it.score }.thenBy { it.roll }.thenBy { it.sheet })
        val out = ArrayList<RankedRow>(sorted.size)
        var rank = 0
        var prev = Double.NaN
        for ((i, r) in sorted.withIndex()) {
            if (i == 0 || Math.abs(r.score - prev) > 1e-9) rank = i + 1
            prev = r.score
            out += RankedRow(rank, r, rollKey(r.roll)?.let { it in dup } ?: false)
        }
        return out
    }

    /** 12 -> "12", 12.5 -> "12.5", 12.25 -> "12.25" (never an exponent, never trailing zeros). */
    fun num(v: Double): String {
        if (!v.isFinite()) return "0"
        if (v == Math.rint(v) && Math.abs(v) < 1e12) return v.toLong().toString()
        return java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun text(s: String): String {
        val t = if (s.isNotEmpty() && (s[0] in "=+@" || (s[0] == '-' && s.length > 1))) "'$s" else s
        return if (t.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    const val HEADER = "rank,sheet,roll,set,score,total,right,wrong,blank,review,note"

    fun csv(rows: List<RankedRow>): String {
        val sb = StringBuilder("\uFEFF").append(HEADER).append("\r\n")
        for (r in rows) {
            val x = r.row
            val note = listOfNotNull(if (r.duplicateRoll) "duplicate roll" else null, if (x.review) "needs review" else null).joinToString("; ")
            sb.append(r.rank).append(',')
                .append(text(x.sheet)).append(',')
                .append(text(x.roll)).append(',')
                .append(x.setIndex?.let { (it + 1).toString() } ?: "").append(',')
                .append(num(x.score)).append(',')
                .append(num(x.total)).append(',')
                .append(x.correct).append(',')
                .append(x.wrong).append(',')
                .append(x.blank).append(',')
                .append(if (x.review) "yes" else "no").append(',')
                .append(text(note)).append("\r\n")
        }
        return sb.toString()
    }
}
