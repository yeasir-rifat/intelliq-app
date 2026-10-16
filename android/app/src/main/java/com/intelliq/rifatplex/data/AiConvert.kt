package com.intelliq.rifatplex.data

private val DEFAULT_MARKS = mapOf(QType.MCQ to "1", QType.TF to "1", QType.FILL to "1", QType.SHORT to "2", QType.DESC to "5")

private val LIST_TAGS = Regex("<\\s*/?\\s*(?:ul|ol)\\s*>", RegexOption.IGNORE_CASE)
private val FRAC_TAG = Regex("<frac>((?:(?!<frac>|</frac>)[\\s\\S])*?)<over>((?:(?!<frac>|</frac>|<over>)[\\s\\S])*?)</frac>", RegexOption.IGNORE_CASE)
private val SQRT_TAG = Regex("<sqrt>((?:(?!<sqrt>|</sqrt>)[\\s\\S])*?)</sqrt>", RegexOption.IGNORE_CASE)

/**
 * Safety net before [aiMarkupToHtml]: models sometimes still answer with the website's tags
 * (<frac>a<over>b</frac>, <sqrt>, <ul><li>). They are turned into the app's markup / plain bullet lines.
 */
fun aiClean(raw: String, chemAuto: Boolean): String {
    var s = raw
    s = s.replace(Regex("<\\s*li\\s*>", RegexOption.IGNORE_CASE), "<br>• ").replace(Regex("<\\s*/\\s*li\\s*>", RegexOption.IGNORE_CASE), "")
    s = s.replace(LIST_TAGS, "").replace(Regex("^(?:<br\\s*/?>\\s*)+", RegexOption.IGNORE_CASE), "")
    var g = 0
    while (g++ < 30 && FRAC_TAG.containsMatchIn(s)) s = FRAC_TAG.replace(s) { m -> "{{frac|" + m.groupValues[1].trim() + "|" + m.groupValues[2].trim() + "}}" }
    g = 0
    while (g++ < 20 && SQRT_TAG.containsMatchIn(s)) {
        s = SQRT_TAG.replace(s) { m ->
            val inner = m.groupValues[1]
            val t = inner.replace(Regex("<[^>]+>"), "").trim()
            "\u221A" + (if (Regex("[\\s+\\-\\u2212\\u00D7\\u00F7\\u00B1=<>]").containsMatchIn(t)) "($inner)" else inner)
        }
    }
    s = s.replace(Regex("</?(?:frac|over|sqrt)>", RegexOption.IGNORE_CASE), "")
    return aiMarkupToHtml(s, chemAuto)
}

/**
 * Gemini JSON -> editor questions (website AI conversion): only the enabled categories, sorted by the configured
 * order, default marks per type, answers / explanations only when switched on. Unusable records are skipped.
 */
fun aiToQuestions(raw: List<AiRawQuestion>, cfg: AiGenConfig, chemAuto: Boolean): List<Question> {
    val order = cfg.types.map { it.type }
    return raw.filter { it.type in order }
        .sortedBy { order.indexOf(it.type) }
        .mapNotNull { aiConvert(it, cfg, chemAuto) }
}

private fun aiConvert(r: AiRawQuestion, cfg: AiGenConfig, chem: Boolean): Question? {
    val q = aiClean(r.question, chem) + r.figTag
    if (plainText(q).isBlank()) return null
    fun ans(s: String) = if (cfg.answers) aiClean(s, chem) else ""
    val base = Question(
        id = uid("q"),
        text = q,
        explanation = if (cfg.explanations) aiClean(r.explanation, chem) else "",
        marks = r.marks.ifBlank { DEFAULT_MARKS[r.type].orEmpty() },
        type = r.type,
    )
    return when (r.type) {
        QType.MCQ -> {
            if (r.options.size < 2) return null
            val ids = "abcdefgh"
            val opts = r.options.take(8).mapIndexed { i, t -> Option(id = ids[i].toString(), text = aiClean(t, chem), correct = i == r.correctIndex) }
            val mq = base.copy(options = opts)
            mq.copy(layout = autoLayout(mq))
        }
        QType.TF -> base.copy(tfAnswer = r.tfAnswer)
        QType.FILL, QType.DESC -> base.copy(answer = ans(r.answer))
        QType.SHORT -> base.copy(answer = ans(r.answer.ifBlank { r.explanation }))
        QType.CREATIVE -> {
            val subs = r.subs.take(8).map { SubQuestion(id = uid("sq"), text = aiClean(it.question, chem) + it.figTag, marks = it.marks, answer = ans(it.answer)) }
            if (subs.isEmpty()) null else base.copy(subs = subs)
        }
        else -> null
    }
}
