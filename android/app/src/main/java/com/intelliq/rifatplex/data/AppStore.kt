package com.intelliq.rifatplex.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Single source of truth. Every change is auto-saved (debounced) to SharedPreferences. */
class AppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("intelliq_state", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    // JSON encoding happens off the main thread; a stale snapshot is skipped if newer data exists.
    private val saveRunnable = Runnable {
        val snap = data
        io.execute { if (snap === data) prefs.edit().putString("data", encodeData(snap, true)).apply() }
    }

    /** Type of the empty question kept at the end of the list; the editor sets it to the active type tab (Step 2). */
    @Volatile var trailingType: String = QType.MCQ

    /**
     * Website `syncTrailing`: on a type tab the empty question at the end follows that tab's type, so "add a question"
     * on the T/F tab gives a T/F card. On the Mix tab nothing is converted and new empty cards are plain MCQs.
     */
    fun setTrailingTab(tab: String) {
        if (tab !in QType.ALL) {
            trailingType = QType.MCQ
            return
        }
        trailingType = tab
        val last = data.questions.lastOrNull()
        if (last != null && last.isEmpty() && last.kind() != tab) setType(last.id, tab)
    }

    var data by mutableStateOf(load())
        private set

    companion object {
        @Volatile private var instance: AppStore? = null

        /** One store for the whole process, so a Gemini request that finishes after a screen rotation still lands in the live data. */
        fun get(context: Context): AppStore =
            instance ?: synchronized(this) { instance ?: AppStore(context.applicationContext).also { instance = it } }
    }

    private fun load(): AppData = try {
        val raw = prefs.getString("data", null)
        val d = normalize(if (raw == null) AppData() else decodeData(raw))
        // a save from before step 4.2 has no exam id: the one just made is saved at once, or the next start would make another
        if (raw == null || !raw.contains("\"examId\"")) prefs.edit().putString("data", encodeData(d, true)).apply()
        d
    } catch (e: Exception) {
        // unreadable save: keep the raw text once (the next save would overwrite it with an empty paper)
        try {
            prefs.getString("data", null)?.let { if (!prefs.contains("data.unreadable")) prefs.edit().putString("data.unreadable", it).apply() }
        } catch (e2: Exception) {
        }
        normalize(AppData())
    }

    /** The editor always keeps one empty question at the end (like the website). */
    private fun normalize(d: AppData): AppData {
        val qs = d.questions
        return if (qs.isEmpty() || !qs.last().isEmpty()) d.copy(questions = qs + newQuestion(trailingType)) else d
    }

    fun update(block: (AppData) -> AppData) {
        data = normalize(block(data))
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 350)
    }

    fun flush() {
        handler.removeCallbacks(saveRunnable)
        prefs.edit().putString("data", encodeData(data, true)).apply()
    }

    // ----- question operations -----
    private fun mapQuestion(id: String, f: (Question) -> Question) = update { d ->
        d.copy(questions = d.questions.map { if (it.id == id) f(it) else it })
    }

    fun setQuestionText(id: String, text: String) = mapQuestion(id) { it.copy(text = text) }

    fun setOptionText(qid: String, oid: String, text: String) = mapQuestion(qid) { q ->
        q.copy(options = q.options.map { if (it.id == oid) it.copy(text = text) else it })
    }

    fun toggleCorrect(qid: String, oid: String) = mapQuestion(qid) { q ->
        val opts = q.options.map { if (it.id == oid) it.copy(correct = !it.correct) else it }
        q.copy(options = opts, explanation = if (opts.any { it.correct }) q.explanation else "")
    }

    fun setExplanation(id: String, text: String) = mapQuestion(id) { it.copy(explanation = text) }

    // ----- question type, marks and type specific fields (website parity) -----

    /** Switching the type keeps every other type's data, so switching back restores it. */
    fun setType(id: String, type: String) = mapQuestion(id) { it.copy(type = QType.normalize(type)).ensured() }

    fun setMarks(id: String, value: String) = mapQuestion(id) { it.copy(marks = value.trim().take(8)) }
    fun setTfAnswer(id: String, value: Boolean?) = mapQuestion(id) { it.copy(tfAnswer = value) }
    fun setAnswer(id: String, text: String) = mapQuestion(id) { it.copy(answer = text) }
    fun setCols(id: String, cols: Int?) = mapQuestion(id) { it.copy(cols = cols?.takeIf { c -> c in 1..3 }) }

    private fun mapSub(qid: String, sid: String, f: (SubQuestion) -> SubQuestion) = mapQuestion(qid) { q ->
        q.copy(subs = q.subs.map { if (it.id == sid) f(it) else it })
    }

    fun setSubText(qid: String, sid: String, text: String) = mapSub(qid, sid) { it.copy(text = text) }
    fun setSubMarks(qid: String, sid: String, marks: String) = mapSub(qid, sid) { it.copy(marks = marks.trim().take(8)) }
    fun setSubAnswer(qid: String, sid: String, text: String) = mapSub(qid, sid) { it.copy(answer = text) }

    /** Adds a sub-question to a Creative question (max 8, like the website). */
    fun addSub(qid: String) = mapQuestion(qid) { q ->
        if (q.subs.size >= 8) q else q.copy(subs = q.subs + SubQuestion(uid("sq")))
    }

    /** Removes a sub-question; a Creative question always keeps at least one (the last one is just cleared). */
    fun deleteSub(qid: String, sid: String) = mapQuestion(qid) { q ->
        if (q.subs.size <= 1) q.copy(subs = q.subs.map { it.copy(text = "", answer = "") })
        else q.copy(subs = q.subs.filter { it.id != sid })
    }

    // ----- columns + sections (settings) -----

    /**
     * Website QCols, category button: sets the default of one category and removes the per-question overrides of that
     * category, so every question of the category follows the new value.
     */
    fun setCategoryCols(type: String, cols: Int) = update { d ->
        val m = d.settings.typeCols.toMutableMap()
        if (cols in 1..3) m[type] = cols
        d.copy(
            settings = d.settings.copy(typeCols = m),
            questions = d.questions.map { if (it.kind() == type) it.copy(cols = null) else it },
        )
    }

    /**
     * The Mix tab "all at once" buttons (website QCols): every question gets the same column override, or [cols] = null
     * (Auto) removes all overrides. The per-category defaults are not touched.
     */
    fun setAllCols(cols: Int?) = update { d ->
        val c = cols?.takeIf { it in 1..3 }
        d.copy(questions = d.questions.map { it.copy(cols = c) })
    }

    fun setSectionMode(on: Boolean) = setSettings { it.copy(sectionMode = on) }
    fun setSectionLayout(layout: String) = setSettings { it.copy(sectionLayout = if (layout == "center") "center" else "left") }

    /** Moves a section up (-1) or down (+1) in the arranged paper. */
    fun moveSection(type: String, delta: Int) = setSettings { st ->
        val list = st.orderedTypes().toMutableList()
        val i = list.indexOf(type)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) st
        else {
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            st.copy(sectionOrder = list)
        }
    }

    fun setSectionMeta(type: String, f: (SectionMeta) -> SectionMeta) = setSettings { st ->
        val m = st.sectionMeta.toMutableMap()
        val next = f(m[type] ?: SectionMeta())
        if (next.title.isBlank() && next.instruction.isBlank()) m.remove(type) else m[type] = next
        st.copy(sectionMeta = m)
    }

    fun setSectionDivider(type: String, on: Boolean) = setSettings { st ->
        st.copy(sectionDividerOff = if (on) st.sectionDividerOff - type else st.sectionDividerOff + type)
    }

    fun setSectionLine(layout: String, on: Boolean) = setSettings { st ->
        st.copy(sectionLineOff = if (on) st.sectionLineOff - layout else st.sectionLineOff + layout)
    }
    fun setLayout(id: String, layout: String) = mapQuestion(id) { it.copy(layout = layout) }

    /** Swaps the positions of two questions (up / down inside the visible list of the active tab / section). */
    fun swapQuestions(a: String, b: String) = update { d ->
        val list = d.questions.toMutableList()
        val i = list.indexOfFirst { it.id == a }
        val j = list.indexOfFirst { it.id == b }
        if (i < 0 || j < 0 || i == j) d
        else {
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            d.copy(questions = list)
        }
    }

    fun duplicateQuestion(id: String) = update { d ->
        val i = d.questions.indexOfFirst { it.id == id }
        if (i < 0) d
        else {
            val src = d.questions[i]
            val copy = src.copy(id = uid("q"))
            d.copy(questions = d.questions.take(i + 1) + copy + d.questions.drop(i + 1))
        }
    }

    /** Returns true when the question was only cleared (it was the last remaining content question). */
    fun deleteQuestion(id: String): Boolean {
        val nonEmpty = data.questions.count { !it.isEmpty() }
        val onlyOne = data.questions.size <= 2 && nonEmpty <= 1
        update { d ->
            val remaining = d.questions.filter { it.id != id }
            d.copy(questions = if (remaining.isEmpty()) listOf(newQuestion()) else remaining)
        }
        return onlyOne
    }

    fun setDoc(f: (DocFields) -> DocFields) = update { it.copy(doc = f(it.doc)) }
    fun setSettings(f: (DocSettings) -> DocSettings) = update { it.copy(settings = f(it.settings)) }
    fun setAi(f: (AiSettings) -> AiSettings) = update { it.copy(ai = f(it.ai)) }
    fun setBrand(f: (BrandSettings) -> BrandSettings) = update { it.copy(brand = f(it.brand)) }
    fun setGrading(f: (GradingRules) -> GradingRules) = update { it.copy(grading = f(it.grading).sane()) }

    fun setSetCode(f: (SetCodeSettings) -> SetCodeSettings) = update { d ->
        val sc = f(d.setCode)
        if (!sc.enabled) d.copy(setCode = sc, generatedSets = emptyList(), activeSetIndex = -1) else d.copy(setCode = sc)
    }

    fun generateSets(): Int {
        val sets = Randomizer.generate(data)
        update { it.copy(generatedSets = sets, activeSetIndex = 0) }
        return sets.size
    }

    /**
     * Puts AI generated questions into the editor like the website: append keeps the existing non-empty questions,
     * replace swaps them all. [language] ("bn" / "en") switches the document language and its default font.
     */
    /**
     * Puts generated questions into the paper. [onlyType] (generation started from a type tab) replaces just that
     * type and keeps the rest. [meta] fills the empty header fields (title, subject, chapter, time, note). The header marks
     * stay empty on purpose: the preview already shows the total of all questions automatically.
     */
    fun applyAiQuestions(qs: List<Question>, replace: Boolean, language: String?, onlyType: String? = null, meta: AiMeta? = null) = update { d ->
        val base = when {
            onlyType != null -> d.questions.filter { !it.isEmpty() && it.kind() != onlyType }
            replace -> emptyList()
            else -> d.questions.filter { !it.isEmpty() }
        }
        var nd = d.copy(questions = base + qs)
        if (language != null && language != d.language) {
            nd = nd.copy(
                language = language,
                settings = nd.settings.copy(
                    fontEn = "Times New Roman",
                    fontBn = "Kalpurush",
                ),
            )
        }
        if (meta != null) {
            val f = nd.doc
            fun pick(cur: String, v: String) = if (cur.isBlank() && v.isNotBlank()) v.trim() else cur
            val bn = nd.language == "bn"
            // the title is built from the subject that ends up in the Subject field (the user's own text wins over the AI's)
            val title = ExamTitles.title(pick(f.subtitle, meta.subtitle), bn).ifEmpty { meta.title }
            val all = nd.questions.filter { !it.isEmpty() }
            val mcqOnly = all.isNotEmpty() && all.all { it.kind() == QType.MCQ }
            val note = ExamTitles.note(mcqOnly, bn)
            nd = nd.copy(
                doc = f.copy(
                    title = pick(f.title, title),
                    subtitle = pick(f.subtitle, meta.subtitle),
                    chapter = pick(f.chapter, meta.chapter),
                    time = pick(f.time, meta.time),
                    note = pick(f.note, note),
                ),
            )
        }
        nd
    }

    fun updateAi(block: (AiSettings) -> AiSettings) = update { it.copy(ai = block(it.ai)) }

    fun setFolder(id: String?) = update { it.copy(folderId = id) }

    /** Loads a saved paper from History into the editor (keeps the theme, zoom and the AI key of this device). */
    fun loadSnapshot(s: AppData) = update { d -> s.copy(dark = d.dark, ai = d.ai, brand = d.brand, zoom = 100) }

    fun setActiveSet(i: Int) = update { it.copy(activeSetIndex = i) }
    fun setLanguage(lang: String) = update { it.copy(language = lang) }
    fun setDark(dark: Boolean) = update { it.copy(dark = dark) }
    fun setZoom(z: Int) = update { it.copy(zoom = z.coerceIn(50, 200)) }

    /**
     * Reset the document and settings to defaults, keeping the language, theme and AI key.
     * The institution branding is kept by default (toolbar reset); the settings panel's reset passes
     * [keepBrand] = false, which also clears the branding (logo, name, slogan, layout, positions).
     */
    fun resetDocument(keepBrand: Boolean = true) = update { d ->
        AppData(
            language = d.language, dark = d.dark, ai = d.ai,
            brand = if (keepBrand) d.brand else BrandSettings(),
            settings = DocSettings(chemAuto = d.settings.chemAuto),
        )
    }
}
