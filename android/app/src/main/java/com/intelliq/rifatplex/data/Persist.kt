package com.intelliq.rifatplex.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun encodeQuestion(q: Question): JSONObject = JSONObject()
    .put("id", q.id)
    .put("text", q.text)
    .put("question", JSONObject().put("html", q.text))
    .put("layout", q.layout)
    .put("optionsLayout", q.layout)
    .put("explanation", q.explanation)
    .put("explanationHtml", q.explanation)
    .put("category", q.category)
    .put("marks", q.marks)
    .put("tfAnswer", q.tfAnswer ?: JSONObject.NULL)
    .put("answer", q.answer)
    .put(
        "subQuestions",
        JSONArray().apply {
            q.subQuestions.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("marks", it.marks).put("answer", it.answer))
            }
        },
    )
    .put(
        "options",
        JSONArray().apply {
            q.options.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("html", it.text).put("correct", it.correct))
            }
        },
    )

private fun decodeQuestion(o: JSONObject): Question {
    val opts = o.optJSONArray("options")?.objects()?.map {
        Option(it.optString("id"), it.optString("text").ifBlank { it.optString("html") }, it.optBoolean("correct"))
    }
    return Question(
        id = o.optString("id", uid("q")),
        text = o.optString("text").ifBlank { o.optJSONObject("question")?.optString("html", "").orEmpty() },
        layout = o.optString("layout", o.optString("optionsLayout", "vertical")),
        explanation = o.optString("explanation").ifBlank { o.optString("explanationHtml") },
        options = if (opts.isNullOrEmpty()) defaultOptions() else opts,
        // Papers saved before Parity Step 3 have no category: they are all MCQ.
        category = o.optString("category", DEFAULT_CATEGORY).takeIf { it in QUESTION_CATEGORIES } ?: DEFAULT_CATEGORY,
        marks = o.optString("marks", ""),
        // Parity Step 5: a missing key (papers saved before Step 5) gives null, "" or an empty list.
        tfAnswer = if (o.isNull("tfAnswer")) null else o.optBoolean("tfAnswer"),
        answer = o.optString("answer"),
        subQuestions = o.optJSONArray("subQuestions")?.objects()?.map {
            SubQuestion(it.optString("id", uid("sq")), it.optString("text"), it.optString("marks"), it.optString("answer"))
        }.orEmpty(),
    )
}

private fun encodeCategory(c: CategorySettings): JSONObject = JSONObject()
    .put("key", c.key)
    .put("order", c.order)
    .put("count", c.count)
    .put("columns", c.columns)
    .put("section", c.section)
    .put("sectionLine", c.sectionLine)

/**
 * Old saves have no categories array: every known category gets its defaults.
 * Unknown keys are dropped, bad values fall back to the default.
 */
private fun decodeCategories(arr: JSONArray?): List<CategorySettings> {
    val saved = arr?.objects()?.mapNotNull { o ->
        val key = o.optString("key")
        if (key !in QUESTION_CATEGORIES) null
        else CategorySettings(
            key = key,
            order = o.optInt("order", QUESTION_CATEGORIES.indexOf(key)),
            count = o.optInt("count", 1).coerceIn(1, 50),
            columns = o.optString("columns", "auto").takeIf { it in COLUMN_LAYOUTS } ?: "auto",
            section = o.optString("section", "left").takeIf { it in SECTION_LAYOUTS } ?: "left",
            sectionLine = o.optBoolean("sectionLine", false),
        )
    }.orEmpty()
    return QUESTION_CATEGORIES
        .map { key -> saved.firstOrNull { it.key == key } ?: CategorySettings(key, order = QUESTION_CATEGORIES.indexOf(key)) }
        .sortedBy { it.order }
}

fun encodeData(d: AppData): String {
    val o = JSONObject()
    o.put("language", d.language)
    o.put("dark", d.dark)
    o.put("zoom", d.zoom)
    o.put("folderId", d.folderId ?: JSONObject.NULL)
    val document = JSONObject().put("title", d.doc.title).put("subtitle", d.doc.subtitle)
        .put("marks", d.doc.marks).put("time", d.doc.time).put("note", d.doc.note)
    o.put("doc", document)
    o.put("document", JSONObject(document.toString()))
    o.put("questions", JSONArray().apply { d.questions.forEach { put(encodeQuestion(it)) } })
    o.put(
        "settings",
        JSONObject().put("theme", if (d.dark) "dark" else "light")
            .put("pageSize", d.settings.pageSize).put("pageColor", d.settings.pageColor)
            .put("fontFamily", d.settings.fontFamily).put("fontSize", d.settings.fontSize)
            .put("fontColor", d.settings.fontColor).put("chemAuto", d.settings.chemAuto)
            .put("zoom", d.zoom).put("fitZoom", d.zoom),
    )
    o.put(
        "setCode",
        JSONObject().put("enabled", d.setCode.enabled).put("numberOfSets", d.setCode.numberOfSets)
            .put("codeNames", d.setCode.codeNames).put("randomization", d.setCode.randomization),
    )
    o.put(
        "generatedSets",
        JSONArray().apply {
            d.generatedSets.forEach { s ->
                put(
                    JSONObject().put("code", s.code)
                        .put("questions", JSONArray().apply { s.questions.forEach { put(encodeQuestion(it)) } }),
                )
            }
        },
    )
    o.put("activeSetIndex", d.activeSetIndex)
    o.put("showMarks", d.showMarks)
    o.put("categories", JSONArray().apply { d.categories.forEach { put(encodeCategory(it)) } })
    o.put(
        "ai",
        JSONObject().put("apiKey", d.ai.apiKey).put("model", d.ai.model).put("insertMode", d.ai.insertMode),
    )
    return o.toString()
}

fun decodeData(json: String): AppData {
    val o = JSONObject(json)
    val def = AppData()
    val doc = o.optJSONObject("doc") ?: o.optJSONObject("document")
    val st = o.optJSONObject("settings")
    val sc = o.optJSONObject("setCode")
    val ai = o.optJSONObject("ai")
    return AppData(
        language = o.optString("language", "en"),
        dark = o.optBoolean("dark", st?.optString("theme", "light") == "dark"),
        zoom = o.optInt("zoom", st?.optInt("zoom", 100) ?: 100),
        folderId = if (o.isNull("folderId")) null else o.optString("folderId").takeIf { it.isNotEmpty() },
        doc = if (doc == null) def.doc else DocFields(
            doc.optString("title"), doc.optString("subtitle"), doc.optString("marks"),
            doc.optString("time"), doc.optString("note"),
        ),
        questions = o.optJSONArray("questions")?.objects()?.map { decodeQuestion(it) }
            ?.takeIf { it.isNotEmpty() } ?: def.questions,
        settings = if (st == null) def.settings else DocSettings(
            pageSize = st.optString("pageSize", def.settings.pageSize),
            pageColor = st.optString("pageColor", def.settings.pageColor),
            fontFamily = st.optString("fontFamily", def.settings.fontFamily),
            fontSize = st.optInt("fontSize", def.settings.fontSize),
            fontColor = st.optString("fontColor", def.settings.fontColor),
            chemAuto = st.optBoolean("chemAuto", false),
        ),
        setCode = if (sc == null) def.setCode else SetCodeSettings(
            enabled = sc.optBoolean("enabled", false),
            numberOfSets = sc.optInt("numberOfSets", 1),
            codeNames = sc.optString("codeNames", def.setCode.codeNames),
            randomization = sc.optString("randomization", "both"),
        ),
        generatedSets = o.optJSONArray("generatedSets")?.objects()?.map { s ->
            GeneratedSet(
                s.optString("code"),
                s.optJSONArray("questions")?.objects()?.map { decodeQuestion(it) } ?: emptyList(),
            )
        } ?: emptyList(),
        activeSetIndex = o.optInt("activeSetIndex", -1),
        showMarks = o.optBoolean("showMarks", false),
        categories = decodeCategories(o.optJSONArray("categories")),
        ai = if (ai == null) def.ai else AiSettings(
            ai.optString("apiKey"), ai.optString("model", def.ai.model), ai.optString("insertMode", "append"),
        ),
    )
}
