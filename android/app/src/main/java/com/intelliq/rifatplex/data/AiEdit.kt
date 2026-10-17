package com.intelliq.rifatplex.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * AI Edit (website `iq-aiedit` module): the AI edits ONE existing question and returns the complete revised question.
 *
 * Flow: [aiEditSnapshot] (question -> JSON text the AI understands) -> [aiEditPrompt] -> [GeminiClient.edit]
 * -> [aiEditApply] (AI JSON -> a proposed [Question], nothing is changed yet) -> the user reviews and applies it.
 */

/** The actions of the dialog, same ids and order as the website. */
val AI_EDIT_ACTIONS = listOf("fix", "polish", "change", "explain")

/**
 * Pictures are cut out of the text before it goes to the AI (they would be huge base64 strings) and are put back
 * afterwards. In the AI text a picture is the placeholder `⟦fig-N⟧`. [tokens] remembers which pictures sat in which field.
 */
class AiEditCtx {
    val figs = ArrayList<String>()
    val tokens = HashMap<String, MutableList<Int>>()
}

private val ANY_TAG_RE = Regex("<[^>]*>")
private val FIG_TAG_RE = Regex("<fig\\b[^>]*/>", RegexOption.IGNORE_CASE)
private val FRAC_TAG_RE = Regex("<frac\\s+n=\"([^\"]*)\"\\s+d=\"([^\"]*)\"\\s*/>")
private val TAG_NAME_RE = Regex("^<(/?)([a-zA-Z]+)")
private val SPAN_COLOR_RE = Regex("color\\s*:\\s*(#[0-9a-fA-F]{6})")
private val FIG_TOKEN_RE = Regex("\u27E6fig-(\\d+)\u27E7")

/** Attribute value -> the HTML-subset string it holds. */
private fun unAttr(s: String): String =
    s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")

/** Text between tags of the editor HTML -> real characters. */
private fun unText(s: String): String =
    s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

/**
 * Editor HTML -> the text the AI works with: `{{frac|n|d}}` for stacked fractions, `<sub> <sup> <b> <i> <u> <br>`
 * as they are, colour spans kept, pictures as `⟦fig-N⟧` (registered in [ctx]). Entities are decoded, because the
 * way back ([aiClean]) escapes `&`, `<` and `>` again.
 *
 * With [ctx] == null the text is only used to compare two versions, so a picture becomes a stable hash token.
 */
fun htmlToAi(html: String, key: String, ctx: AiEditCtx?): String {
    if (html.isEmpty()) return ""
    val sb = StringBuilder()
    val spans = ArrayList<Boolean>() // for every open <span>: was it written out
    var last = 0
    for (m in ANY_TAG_RE.findAll(html)) {
        sb.append(unText(html.substring(last, m.range.first)))
        last = m.range.last + 1
        val tag = m.value
        if (FIG_TAG_RE.matches(tag)) {
            if (ctx != null) {
                ctx.figs.add(tag)
                val n = ctx.figs.size
                ctx.tokens.getOrPut(key) { ArrayList() }.add(n)
                sb.append("\u27E6fig-").append(n).append("\u27E7")
            } else {
                sb.append("\u27E6fig#").append(tag.hashCode()).append("\u27E7")
            }
            continue
        }
        val fm = FRAC_TAG_RE.matchEntire(tag)
        if (fm != null) {
            val n = htmlToAi(unAttr(fm.groupValues[1]), key, ctx).replace("|", "/")
            val d = htmlToAi(unAttr(fm.groupValues[2]), key, ctx).replace("|", "/")
            sb.append("{{frac|").append(n).append('|').append(d).append("}}")
            continue
        }
        val nm = TAG_NAME_RE.find(tag) ?: continue
        val closing = nm.groupValues[1] == "/"
        when (val name = nm.groupValues[2].lowercase()) {
            "br" -> sb.append("<br>")
            "b", "strong" -> sb.append(if (closing) "</b>" else "<b>")
            "i", "em" -> sb.append(if (closing) "</i>" else "<i>")
            "u", "sub", "sup" -> sb.append(if (closing) "</$name>" else "<$name>")
            "span" -> {
                if (closing) {
                    if (spans.isNotEmpty() && spans.removeAt(spans.size - 1)) sb.append("</span>")
                } else {
                    val col = SPAN_COLOR_RE.find(tag)?.groupValues?.get(1)
                    if (col != null) {
                        sb.append("<span style=\"color:").append(col).append("\">")
                        spans.add(true)
                    } else {
                        spans.add(false)
                    }
                }
            }
            else -> {}
        }
    }
    sb.append(unText(html.substring(last)))
    return sb.toString()
}

/** AI text -> editor HTML (the app's own AI converter), pictures put back. A field the AI dropped pictures from gets them again at the end. */
fun aiEditFromAi(text: String, key: String, ctx: AiEditCtx, chem: Boolean): String {
    var s = aiClean(text, chem)
    val used = HashSet<Int>()
    s = FIG_TOKEN_RE.replace(s) { m ->
        val n = m.groupValues[1].toIntOrNull() ?: 0
        used.add(n)
        ctx.figs.getOrNull(n - 1).orEmpty()
    }
    ctx.tokens[key]?.forEach { n -> if (n !in used) s += ctx.figs.getOrNull(n - 1).orEmpty() }
    return s
}

/** A JSON value as text (the website `txt`): strings, numbers, booleans, or an object with an `html` field. */
fun aiEditText(v: Any?): String = when {
    v == null || v == JSONObject.NULL -> ""
    v is JSONObject -> v.optString("html")
    else -> v.toString()
}

// ------------------------------------------------------------------------------------------------ question -> AI

fun aiEditSnapshot(q: Question, ctx: AiEditCtx): JSONObject {
    fun t(key: String, html: String) = htmlToAi(html, key, ctx)
    val o = JSONObject()
    when (q.kind()) {
        QType.MCQ -> {
            o.put("type", "mcq")
            o.put("question", t("q", q.text))
            val opts = JSONArray()
            q.options.forEachIndexed { i, op -> opts.put(t("o$i", op.text)) }
            o.put("options", opts)
            val ok = JSONArray()
            q.options.forEachIndexed { i, op -> if (op.correct) ok.put(i) }
            o.put("correctIndexes", ok)
            o.put("explanation", t("expl", q.explanation))
        }
        QType.TF -> {
            o.put("type", "tf")
            o.put("question", t("q", q.text))
            o.put("answer", q.tfAnswer ?: JSONObject.NULL)
            o.put("explanation", t("expl", q.explanation))
        }
        QType.CREATIVE -> {
            o.put("type", "creative")
            o.put("stimulus", t("q", q.text))
            val subs = JSONArray()
            q.subs.forEachIndexed { i, s ->
                subs.put(
                    JSONObject()
                        .put("question", t("s$i", s.text))
                        .put("marks", s.marks)
                        .put("answer", t("a$i", s.answer)),
                )
            }
            o.put("subQuestions", subs)
        }
        else -> {
            o.put("type", q.kind())
            o.put("question", t("q", q.text))
            o.put("answer", t("ans", q.answer))
        }
    }
    return o
}

// ------------------------------------------------------------------------------------------------------ prompt

private val EDIT_TYPE_NAMES = mapOf(
    QType.MCQ to "MCQ (multiple choice)",
    QType.TF to "True / False",
    QType.FILL to "Fill in the blanks",
    QType.SHORT to "Short answer",
    QType.DESC to "Descriptive / broad question",
    QType.CREATIVE to "Creative question (stimulus + sub-questions)",
)

private val EDIT_EXPLAIN_HINT = mapOf(
    QType.MCQ to "write or expand the \"explanation\" field with a detailed, accurate, step-by-step explanation of why the correct option is right and, briefly, why the other options are wrong.",
    QType.TF to "write or expand the \"explanation\" field with a detailed, accurate explanation of why the statement is true or false.",
    QType.FILL to "expand the \"answer\" field into a complete answer that also states the reasoning in one or two lines.",
    QType.SHORT to "expand the \"answer\" field into a complete, detailed answer with reasoning.",
    QType.DESC to "expand the \"answer\" field into a complete, well-structured, detailed model answer.",
    QType.CREATIVE to "expand every sub-question \"answer\" into a detailed, well-structured answer.",
)

private val EDIT_SPECIFIC = mapOf(
    QType.MCQ to "- \"options\": array of option texts (keep the same number unless the instruction asks otherwise; 2 to 8). Keep empty options empty unless asked. \"correctIndexes\": zero-based indexes of the correct option(s); keep exactly one unless the original has several. Distractors must be plausible and exactly one option must be unambiguously correct.",
    QType.TF to "- \"answer\" must be true or false and the statement must be unambiguously true or false.",
    QType.FILL to "- Keep \"_____\" for each blank in the question. \"answer\" lists the blank answer(s), comma-separated if several.",
    QType.CREATIVE to "- Keep the number of sub-questions and their marks unless the instruction says otherwise. The sub-questions are in order: knowledge, comprehension, application, higher-order.",
)

private val EDIT_SCHEMA = mapOf(
    QType.MCQ to """{"question":"...","options":["...","..."],"correctIndexes":[0],"explanation":"...","changes":"..."}""",
    QType.TF to """{"question":"...","answer":true,"explanation":"...","changes":"..."}""",
    QType.FILL to """{"question":"...","answer":"...","changes":"..."}""",
    QType.SHORT to """{"question":"...","answer":"...","changes":"..."}""",
    QType.DESC to """{"question":"...","answer":"...","changes":"..."}""",
    QType.CREATIVE to """{"stimulus":"...","subQuestions":[{"question":"...","marks":"1","answer":"..."}],"changes":"..."}""",
)

/** Prompt of one AI Edit request (website `buildPrompt`); the formatting rules are the app's own markup. */
fun aiEditPrompt(type: String, snap: JSONObject, acts: List<String>, instr: String, bn: Boolean): String {
    val act = mapOf(
        "fix" to "FIX MISTAKES: carefully verify facts, calculations, formulas, units, spelling, grammar and punctuation. Correct every error you find, including a wrong marked answer (update the answer key to match). Leave anything that is already correct untouched.",
        "polish" to "POLISH: improve wording, clarity, grammar and exam-paper tone WITHOUT changing the meaning, difficulty or correct answer.",
        "change" to "CHANGE: modify the question as described in the user's instruction. Keep the answer key, explanation and every dependent part consistent with the changed question.",
        "explain" to "ADD DETAILED EXPLANATION: " + (EDIT_EXPLAIN_HINT[type] ?: ""),
    )
    val lines = ArrayList<String>()
    lines.add("You are an expert exam-question editor and subject specialist. Edit exactly ONE existing question and return the COMPLETE revised question as JSON.")
    lines.add("")
    lines.add("Question type: " + (EDIT_TYPE_NAMES[type] ?: type))
    lines.add("Write the \"changes\" note in " + (if (bn) "Bangla" else "English") + ". Keep the question's own language unless the user explicitly asks to translate it.")
    lines.add("")
    lines.add("Requested actions:")
    lines.add(if (acts.isEmpty()) "- Follow the user's instruction below." else acts.joinToString("\n") { "- " + (act[it] ?: "") })
    lines.add("Extra instruction from the user: " + instr.ifBlank { "none" })
    lines.add("")
    lines.add("CURRENT QUESTION (JSON):")
    lines.add(snap.toString())
    lines.add("")
    lines.add("RULES:")
    lines.add("- Never change the question type. Return every field of the schema with the full revised text (not a diff). Fields that need no change must be returned exactly as given.")
    lines.add("- Leave empty fields empty unless a requested action needs them filled.")
    lines.add("- Do not invent facts. If you are unsure, be conservative and keep the original.")
    EDIT_SPECIFIC[type]?.let { lines.add(it) }
    lines.add("- When you write or rewrite an answer or explanation, phrase it freshly and naturally; do not fall back on a stock opening such as \"The correct answer is\" / \"সঠিক উত্তর হলো\" unless the user asked for that wording.")
    lines.add("- \"changes\": 1 to 3 short sentences saying exactly what you changed, or that no change was needed.")
    lines.add("")
    lines.add(GeminiPrompts.FORMATTING)
    lines.add("")
    lines.add(GeminiPrompts.STYLE)
    lines.add("- Keep figure placeholders such as \u27E6fig-1\u27E7 exactly as written, in the field where they appear. Keep any <span style=\"color:#RRGGBB\"> tags that already exist in the given text exactly as they are.")
    lines.add("")
    lines.add("OUTPUT: return ONLY one JSON object shaped like " + (EDIT_SCHEMA[type] ?: "{}") + " - no markdown fences, no comments, no text outside the JSON.")
    return lines.joinToString("\n")
}

// ------------------------------------------------------------------------------------------------- AI -> question

private fun firstValue(d: JSONObject, vararg names: String): Any? {
    for (n in names) {
        val v = d.opt(n)
        if (v != null && v != JSONObject.NULL) return v
    }
    return null
}

/**
 * The proposed version: [q] with the AI's fields applied. An empty AI field keeps the original (never wipes content).
 * Ids of options / sub-questions are kept by position; nothing outside the edited fields changes.
 */
fun aiEditApply(q: Question, d: JSONObject, ctx: AiEditCtx, chem: Boolean): Question {
    fun field(v: Any?, key: String, orig: String): String {
        val s = aiEditText(v).trim()
        return if (s.isEmpty()) orig else aiEditFromAi(s, key, ctx, chem)
    }

    val text = field(firstValue(d, "question", "stimulus", "statement"), "q", q.text)
    return when (q.kind()) {
        QType.MCQ -> {
            val arr = d.optJSONArray("options")
            var res = q.copy(text = text)
            if (arr != null && arr.length() >= 2) {
                val n = minOf(arr.length(), 8)
                val idx: List<Int>? = run {
                    val ci = d.optJSONArray("correctIndexes")
                    val list: List<Int> = if (ci != null) {
                        (0 until ci.length()).map { ci.optInt(it, -1) }
                    } else if (d.has("correctIndex") && !d.isNull("correctIndex")) {
                        listOf(d.optInt("correctIndex", -1))
                    } else {
                        emptyList()
                    }
                    list.filter { it in 0 until n }.ifEmpty { null }
                }
                val old = q.options
                val opts = (0 until n).map { i ->
                    val prev = old.getOrNull(i)
                    Option(
                        id = prev?.id ?: "abcdefgh"[i].toString(),
                        text = field(arr.opt(i), "o$i", prev?.text.orEmpty()),
                        correct = if (idx != null) i in idx else prev?.correct == true,
                    )
                }
                res = res.copy(options = opts)
                // the option count changed, so the layout is chosen again (website: unless the layout is locked)
                if (n != old.size) res = res.copy(layout = autoLayout(res))
            }
            res.copy(explanation = field(d.opt("explanation"), "expl", q.explanation))
        }
        QType.TF -> {
            val a = d.opt("answer")
            val tf: Boolean? = when {
                a is Boolean -> a
                a is String -> when (a.trim().lowercase()) {
                    "true", "\u09B8\u09A4\u09CD\u09AF" -> true
                    "false", "\u09AE\u09BF\u09A5\u09CD\u09AF\u09BE" -> false
                    else -> q.tfAnswer
                }
                else -> q.tfAnswer
            }
            q.copy(text = text, tfAnswer = tf, explanation = field(d.opt("explanation"), "expl", q.explanation))
        }
        QType.CREATIVE -> {
            val arr = d.optJSONArray("subQuestions")
            if (arr == null || arr.length() == 0) {
                q.copy(text = text)
            } else {
                val old = q.subs
                val subs = (0 until minOf(arr.length(), 8)).map { i ->
                    val s = arr.optJSONObject(i) ?: JSONObject()
                    val p = old.getOrNull(i)
                    val m = aiEditText(s.opt("marks")).trim()
                    SubQuestion(
                        id = p?.id ?: uid("sq"),
                        text = field(firstValue(s, "question", "text"), "s$i", p?.text.orEmpty()),
                        marks = if (m.isNotEmpty()) m.take(8) else p?.marks.orEmpty(),
                        answer = field(s.opt("answer"), "a$i", p?.answer.orEmpty()),
                    )
                }
                q.copy(text = text, subs = subs)
            }
        }
        else -> q.copy(text = text, answer = field(d.opt("answer"), "ans", q.answer))
    }
}

/** Text used only to decide whether a field changed (website `norm`): whitespace collapsed, pictures by hash. */
fun aiEditNorm(html: String, plain: Boolean, correct: Boolean): String =
    (if (plain) html else htmlToAi(html, "_", null)).replace(Regex("\\s+"), " ").trim() + "|" + (if (correct) 1 else 0)
