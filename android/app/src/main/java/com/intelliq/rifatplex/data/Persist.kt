package com.intelliq.rifatplex.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun encodeQuestion(q: Question): JSONObject = JSONObject()
    .put("id", q.id)
    .put("text", q.text)
    .put("layout", q.layout)
    .put("explanation", q.explanation)
    .put(
        "options",
        JSONArray().apply {
            q.options.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("correct", it.correct))
            }
        },
    )

private fun decodeQuestion(o: JSONObject): Question {
    val opts = o.optJSONArray("options")?.objects()?.map {
        Option(it.optString("id"), it.optString("text"), it.optBoolean("correct"))
    }
    return Question(
        id = o.optString("id", uid("q")),
        text = o.optString("text"),
        layout = o.optString("layout", "vertical"),
        explanation = o.optString("explanation"),
        options = if (opts.isNullOrEmpty()) defaultOptions() else opts,
    )
}

fun encodeData(d: AppData): String {
    val o = JSONObject()
    o.put("language", d.language)
    o.put("dark", d.dark)
    o.put("zoom", d.zoom)
    o.put("folderId", d.folderId ?: JSONObject.NULL)
    o.put(
        "doc",
        JSONObject().put("title", d.doc.title).put("subtitle", d.doc.subtitle)
            .put("marks", d.doc.marks).put("time", d.doc.time).put("note", d.doc.note),
    )
    o.put("questions", JSONArray().apply { d.questions.forEach { put(encodeQuestion(it)) } })
    o.put(
        "settings",
        JSONObject().put("pageSize", d.settings.pageSize).put("pageColor", d.settings.pageColor)
            .put("fontFamily", d.settings.fontFamily).put("fontSize", d.settings.fontSize)
            .put("fontColor", d.settings.fontColor).put("chemAuto", d.settings.chemAuto),
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
    o.put(
        "ai",
        JSONObject().put("apiKey", d.ai.apiKey).put("model", d.ai.model).put("insertMode", d.ai.insertMode),
    )
    return o.toString()
}

fun decodeData(json: String): AppData {
    val o = JSONObject(json)
    val def = AppData()
    val doc = o.optJSONObject("doc")
    val st = o.optJSONObject("settings")
    val sc = o.optJSONObject("setCode")
    val ai = o.optJSONObject("ai")
    return AppData(
        language = o.optString("language", "en"),
        dark = o.optBoolean("dark", false),
        zoom = o.optInt("zoom", 100),
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
        ai = if (ai == null) def.ai else AiSettings(
            ai.optString("apiKey"), ai.optString("model", def.ai.model), ai.optString("insertMode", "append"),
        ),
    )
}
