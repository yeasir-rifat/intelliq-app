package com.intelliq.rifatplex.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun encodeQuestion(q: Question): JSONObject {
    val o = JSONObject()
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
    // New (website parity) fields are written only when they carry something, so old and new data stay compatible.
    if (q.kind() != QType.MCQ) o.put("type", q.kind())
    if (q.marks.isNotBlank()) o.put("marks", q.marks)
    if (q.tfAnswer != null) o.put("tfAnswer", q.tfAnswer)
    if (q.answer.isNotEmpty()) o.put("answer", q.answer)
    if (q.cols != null) o.put("cols", q.cols)
    if (q.subs.isNotEmpty()) {
        o.put(
            "subs",
            JSONArray().apply {
                q.subs.forEach {
                    put(JSONObject().put("id", it.id).put("text", it.text).put("marks", it.marks).put("answer", it.answer))
                }
            },
        )
    }
    return o
}

private fun decodeQuestion(o: JSONObject): Question {
    val opts = o.optJSONArray("options")?.objects()?.map {
        Option(it.optString("id"), it.optString("text"), it.optBoolean("correct"))
    }
    val subs = o.optJSONArray("subs")?.objects()?.map {
        SubQuestion(it.optString("id", uid("sq")), it.optString("text"), it.optString("marks"), it.optString("answer"))
    } ?: emptyList()
    return Question(
        id = o.optString("id", uid("q")),
        text = o.optString("text"),
        layout = o.optString("layout", "vertical"),
        explanation = o.optString("explanation"),
        options = if (opts.isNullOrEmpty()) defaultOptions() else opts,
        type = QType.normalize(o.optString("type", QType.MCQ)),
        marks = o.optString("marks"),
        tfAnswer = if (o.has("tfAnswer") && !o.isNull("tfAnswer")) o.optBoolean("tfAnswer") else null,
        answer = o.optString("answer"),
        subs = subs,
        cols = o.optInt("cols", 0).takeIf { it in 1..3 },
    ).ensured()
}

private fun encodeSettings(st: DocSettings): JSONObject {
    val o = JSONObject().put("pageSize", st.pageSize).put("pageColor", st.pageColor)
        .put("fontFamily", st.fontFamily).put("fontSize", st.fontSize)
        .put("fontColor", st.fontColor).put("chemAuto", st.chemAuto)
        .put("sectionMode", st.sectionMode)
        .put("sectionOrder", JSONArray(st.orderedTypes()))
        .put("sectionLayout", st.sectionLayout)
        .put("sectionDividerOff", JSONArray(st.sectionDividerOff.toList()))
        .put("sectionLineOff", JSONArray(st.sectionLineOff.toList()))
        .put("showMarks", st.showMarks)
        .put("showPageNumber", st.showPageNumber)
        .put("autoFix", st.autoFix)
        .put("showAnswer", st.showAnswer)
        .put("showExplanation", st.showExplanation)
        .put("answerLayout", st.answerLayout)
        .put("answerColor", st.answerColor)
        .put("omr", encodeOmr(st.omr))
    o.put("typeCols", JSONObject().apply { st.typeCols.forEach { (k, v) -> put(k, v) } })
    o.put(
        "sectionMeta",
        JSONObject().apply {
            st.sectionMeta.forEach { (k, v) -> put(k, JSONObject().put("title", v.title).put("instruction", v.instruction)) }
        },
    )
    return o
}

private fun encodeOmr(m: OmrSettings): JSONObject = JSONObject()
    .put("mode", m.mode)
    .put("boardName", m.boardName).put("examLine", m.examLine).put("sheetTitle", m.sheetTitle)
    .put("banner1", m.banner1).put("banner2", m.banner2)
    .put("showYear", m.showYear).put("yearPrefix", m.yearPrefix)
    .put("showBanner1", m.showBanner1).put("showBanner2", m.showBanner2)
    .put("qHeader", m.qHeader).put("ansHeader", m.ansHeader).put("shading", m.shading)
    .put("showRoll", m.showRoll).put("rollLabel", m.rollLabel).put("rollDigits", m.rollDigits)
    .put("showSet", m.showSet).put("setLabel", m.setLabel)
    .put("showReg", m.showReg).put("regLabel", m.regLabel).put("regDigits", m.regDigits)
    .put("showSubject", m.showSubject).put("subjectLabel", m.subjectLabel).put("subjectDigits", m.subjectDigits)
    .put("showMarks", m.showMarks).put("marksLabel", m.marksLabel)
    .put("showInvigilator", m.showInvigilator).put("invigilatorLabel", m.invigilatorLabel)
    .put("showNote", m.showNote).put("note", m.note)
    .put("showRules", m.showRules).put("rulesTitle", m.rulesTitle).put("rules", m.rules)
    .put("showSignature", m.showSignature).put("signatureLabel", m.signatureLabel)

private fun decodeOmr(o: JSONObject?): OmrSettings {
    if (o == null) return OmrSettings()
    val d = OmrSettings()
    return OmrSettings(
        mode = o.optString("mode", d.mode).takeIf { it == OMR_CUSTOM } ?: OMR_AUTO,
        boardName = o.optString("boardName"), examLine = o.optString("examLine"), sheetTitle = o.optString("sheetTitle"),
        banner1 = o.optString("banner1"), banner2 = o.optString("banner2"),
        showYear = o.optBoolean("showYear", true), yearPrefix = o.optString("yearPrefix", d.yearPrefix).take(4),
        showBanner1 = o.optBoolean("showBanner1", true), showBanner2 = o.optBoolean("showBanner2", true),
        qHeader = o.optString("qHeader"), ansHeader = o.optString("ansHeader"), shading = o.optBoolean("shading", true),
        showRoll = o.optBoolean("showRoll", true), rollLabel = o.optString("rollLabel"),
        rollDigits = o.optInt("rollDigits", d.rollDigits).coerceIn(1, 8),
        showSet = o.optBoolean("showSet", true), setLabel = o.optString("setLabel"),
        showReg = o.optBoolean("showReg", true), regLabel = o.optString("regLabel"),
        regDigits = o.optInt("regDigits", d.regDigits).coerceIn(1, 12),
        showSubject = o.optBoolean("showSubject", true), subjectLabel = o.optString("subjectLabel"),
        subjectDigits = o.optInt("subjectDigits", d.subjectDigits).coerceIn(1, 4),
        showMarks = o.optBoolean("showMarks", true), marksLabel = o.optString("marksLabel"),
        showInvigilator = o.optBoolean("showInvigilator", true), invigilatorLabel = o.optString("invigilatorLabel"),
        showNote = o.optBoolean("showNote", true), note = o.optString("note"),
        showRules = o.optBoolean("showRules", true), rulesTitle = o.optString("rulesTitle"), rules = o.optString("rules"),
        showSignature = o.optBoolean("showSignature", d.showSignature), signatureLabel = o.optString("signatureLabel"),
    )
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun decodeSettings(st: JSONObject, def: DocSettings): DocSettings {
    val cols = HashMap<String, Int>()
    st.optJSONObject("typeCols")?.let { c ->
        c.keys().forEach { k -> c.optInt(k, 0).takeIf { it in 1..3 }?.let { v -> cols[k] = v } }
    }
    val meta = HashMap<String, SectionMeta>()
    st.optJSONObject("sectionMeta")?.let { m ->
        m.keys().forEach { k ->
            m.optJSONObject(k)?.let { mo -> meta[k] = SectionMeta(mo.optString("title"), mo.optString("instruction")) }
        }
    }
    val order = st.optJSONArray("sectionOrder").strings().filter { it in QType.ALL }
    return DocSettings(
        pageSize = st.optString("pageSize", def.pageSize),
        pageColor = st.optString("pageColor", def.pageColor),
        fontFamily = st.optString("fontFamily", def.fontFamily)
            .let { if (it in com.intelliq.rifatplex.ui.theme.DOC_FONT_NAMES) it else def.fontFamily }, // e.g. the removed "Shonar Bangla" / "Nikosh BAN"
        fontSize = st.optInt("fontSize", def.fontSize),
        fontColor = st.optString("fontColor", def.fontColor),
        chemAuto = st.optBoolean("chemAuto", false),
        typeCols = cols,
        sectionMode = st.optBoolean("sectionMode", true),
        sectionOrder = if (order.isEmpty()) QType.ALL else order,
        sectionMeta = meta,
        sectionLayout = st.optString("sectionLayout", def.sectionLayout).takeIf { it == "left" || it == "center" } ?: def.sectionLayout,
        sectionDividerOff = st.optJSONArray("sectionDividerOff").strings().toSet(),
        sectionLineOff = if (st.has("sectionLineOff")) st.optJSONArray("sectionLineOff").strings().toSet() else def.sectionLineOff,
        showMarks = st.optBoolean("showMarks", true),
        showPageNumber = st.optBoolean("showPageNumber", true),
        autoFix = st.optBoolean("autoFix", false),
        showAnswer = st.optBoolean("showAnswer", true),
        showExplanation = st.optBoolean("showExplanation", true),
        answerColor = st.optString("answerColor", ANS_COLOR_GREEN).takeIf { it == ANS_COLOR_BLACK } ?: ANS_COLOR_GREEN,
        answerLayout = st.optString("answerLayout", ANS_LAYOUT_EXPL).takeIf { it in listOf(ANS_LAYOUT_FILL, ANS_LAYOUT_EXPL, ANS_LAYOUT_CORNER) } ?: ANS_LAYOUT_EXPL,
        omr = decodeOmr(st.optJSONObject("omr")),
    ).let { it.copy(sectionOrder = it.orderedTypes()) }
}

private fun encodeBrand(b: BrandSettings): JSONObject = JSONObject()
    .put("enabled", b.enabled).put("layout", b.layout).put("name", b.name).put("slogan", b.slogan)
    .put("logo", b.logo).put("logoW", b.logoW).put("logoH", b.logoH)
    .put("showLogo", b.showLogo).put("showName", b.showName).put("showSlogan", b.showSlogan)
    .put("showExam", b.showExam).put("showSubject", b.showSubject).put("showChapter", b.showChapter)
    .put("showTime", b.showTime).put("showMarks", b.showMarks).put("showSetCode", b.showSetCode)
    .put("logoPos", b.logoPos).put("codePos", b.codePos)
    .put("logoScale", b.logoScale).put("nameScale", b.nameScale).put("color", b.color).put("divider", b.divider)
    .put("address", b.address).put("contact", b.contact).put("eiin", b.eiin)
    .put("showAddress", b.showAddress).put("showContact", b.showContact).put("showEiin", b.showEiin)

private fun decodeBrand(o: JSONObject): BrandSettings {
    val def = BrandSettings()
    return BrandSettings(
        enabled = o.optBoolean("enabled", false),
        layout = o.optString("layout", def.layout).takeIf { it in listOf(BRAND_CLASSIC, BRAND_LETTER, BRAND_BOARD) } ?: def.layout,
        name = o.optString("name"), slogan = o.optString("slogan"),
        logo = o.optString("logo"), logoW = o.optInt("logoW"), logoH = o.optInt("logoH"),
        showLogo = o.optBoolean("showLogo", true), showName = o.optBoolean("showName", true),
        showSlogan = o.optBoolean("showSlogan", true), showExam = o.optBoolean("showExam", true),
        showSubject = o.optBoolean("showSubject", true), showChapter = o.optBoolean("showChapter", true),
        showTime = o.optBoolean("showTime", true), showMarks = o.optBoolean("showMarks", true),
        showSetCode = o.optBoolean("showSetCode", true),
        logoPos = o.optString("logoPos", def.logoPos).takeIf { it in listOf(POS_START, POS_CENTER, POS_END) } ?: def.logoPos,
        codePos = o.optString("codePos", def.codePos).takeIf { it == POS_START } ?: POS_END,
        logoScale = o.optInt("logoScale", 100).coerceIn(60, 200),
        nameScale = o.optInt("nameScale", 100).coerceIn(70, 150),
        color = o.optString("color").takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) } ?: "",
        divider = o.optString("divider").takeIf { it in listOf(DIV_NONE, DIV_THIN, DIV_THICK, DIV_DOUBLE) } ?: "",
        address = o.optString("address"), contact = o.optString("contact"), eiin = o.optString("eiin"),
        showAddress = o.optBoolean("showAddress", true), showContact = o.optBoolean("showContact", true),
        showEiin = o.optBoolean("showEiin", true),
    )
}

/** [includeBrand] is true only for the device's own copy: saved papers and cloud sync never carry the logo. */
fun encodeData(d: AppData, includeBrand: Boolean = false): String {
    val o = JSONObject()
    if (includeBrand) o.put("brand", encodeBrand(d.brand))
    o.put("language", d.language)
    o.put("dark", d.dark)
    o.put("zoom", d.zoom)
    o.put("folderId", d.folderId ?: JSONObject.NULL)
    o.put("examId", d.examId)
    o.put("grading", JSONObject().put("marks", d.grading.marksPerQ).put("neg", d.grading.negativePerWrong))
    o.put(
        "doc",
        JSONObject().put("title", d.doc.title).put("subtitle", d.doc.subtitle)
            .put("marks", d.doc.marks).put("time", d.doc.time).put("note", d.doc.note)
            .put("chapter", d.doc.chapter),
    )
    o.put("questions", JSONArray().apply { d.questions.forEach { put(encodeQuestion(it)) } })
    o.put("settings", encodeSettings(d.settings))
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
        JSONObject().put("apiKey", d.ai.apiKey).put("model", d.ai.model).put("insertMode", d.ai.insertMode)
            .put("difficulty", d.ai.difficulty).put("answers", d.ai.answers).put("explanations", d.ai.explanations)
            .put(
                "types",
                org.json.JSONArray(d.ai.types.map { JSONObject().put("type", it.type).put("enabled", it.enabled).put("count", it.count) }),
            ),
    )
    return o.toString()
}

/**
 * [fallbackExamId]: the exam id to use when the save has none (a save from before step 4.2). Papers of History / the
 * cloud pass an id derived from their own id so it is the same on every device; without it a new one is made.
 */
fun decodeData(json: String, fallbackExamId: String? = null): AppData {
    val o = JSONObject(json)
    val def = AppData(examId = "") // only for the other defaults; the real id is set below
    val doc = o.optJSONObject("doc")
    val st = o.optJSONObject("settings")
    val sc = o.optJSONObject("setCode")
    val ai = o.optJSONObject("ai")
    return AppData(
        language = o.optString("language", "en"),
        dark = o.optBoolean("dark", false),
        zoom = o.optInt("zoom", 100),
        folderId = if (o.isNull("folderId")) null else o.optString("folderId").takeIf { it.isNotEmpty() },
        examId = o.optString("examId").takeIf { ExamId.isValid(it) } ?: fallbackExamId ?: ExamId.generate(),
        grading = o.optJSONObject("grading")?.let { GradingRules(it.optDouble("marks", 1.0), it.optDouble("neg", 0.0)).sane() } ?: def.grading,
        doc = if (doc == null) def.doc else DocFields(
            doc.optString("title"), doc.optString("subtitle"), doc.optString("marks"),
            doc.optString("time"), doc.optString("note"), doc.optString("chapter"),
        ),
        brand = o.optJSONObject("brand")?.let { decodeBrand(it) } ?: def.brand,
        questions = o.optJSONArray("questions")?.objects()?.map { decodeQuestion(it) }
            ?.takeIf { it.isNotEmpty() } ?: def.questions,
        settings = if (st == null) def.settings else decodeSettings(st, def.settings),
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
            apiKey = ai.optString("apiKey"),
            model = ai.optString("model", def.ai.model).let { if (it in GeminiClient.MODELS) it else def.ai.model }, // retired model -> default
            insertMode = ai.optString("insertMode", "append"),
            types = decodeAiTypes(ai.optJSONArray("types")),
            difficulty = ai.optString("difficulty", "mixed").ifEmpty { "mixed" },
            answers = ai.optBoolean("answers", true),
            explanations = ai.optBoolean("explanations", true),
        ),
    )
}


/** Every type exactly once; missing ones come back with the website defaults (old saves have no "types"). */
private fun decodeAiTypes(arr: org.json.JSONArray?): List<AiTypeCfg> {
    val def = defaultAiTypes()
    if (arr == null) return def
    val out = ArrayList<AiTypeCfg>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val t = o.optString("type")
        val d = def.firstOrNull { it.type == t } ?: continue
        if (out.any { it.type == t }) continue
        out.add(AiTypeCfg(t, o.optBoolean("enabled", d.enabled), o.optInt("count", d.count).coerceIn(1, 100)))
    }
    def.forEach { d -> if (out.none { it.type == d.type }) out.add(d) }
    return out
}
