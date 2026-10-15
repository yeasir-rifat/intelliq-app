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
        io.execute { if (snap === data) prefs.edit().putString("data", encodeData(snap)).apply() }
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
        normalize(if (raw == null) AppData() else decodeData(raw))
    } catch (e: Exception) {
        normalize(AppData())
    }

    /** The editor always keeps one empty question at the end (like the website). */
    private fun normalize(d: AppData): AppData {
        val qs = d.questions
        return if (qs.isEmpty() || !qs.last().isEmpty()) d.copy(questions = qs + newQuestion()) else d
    }

    fun update(block: (AppData) -> AppData) {
        data = normalize(block(data))
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 350)
    }

    fun flush() {
        handler.removeCallbacks(saveRunnable)
        prefs.edit().putString("data", encodeData(data)).apply()
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

    /** Parity Step 3: unknown category names are ignored. Used from Parity Step 4 (category tabs). */
    fun setQuestionCategory(id: String, category: String) {
        if (category in QUESTION_CATEGORIES) mapQuestion(id) { it.withCategory(category) }
    }
    /**
     * Parity Step 4: the trailing blank question follows the active category tab, so typing into it
     * keeps the question in that tab (the website does the same in syncTrailing).
     */
    fun ensureTrailingCategory(category: String) {
        if (category !in QUESTION_CATEGORIES) return
        val last = data.questions.lastOrNull() ?: return
        if (last.isEmpty() && last.category != category) mapQuestion(last.id) { it.withCategory(category) }
    }

    /**
     * Parity Step 4: "Add Questions" for a category tab. Adds [count] blank questions of [category].
     * They go before the trailing blank question, so that blank stays last.
     */
    fun addBlankQuestions(category: String, count: Int) {
        if (category !in QUESTION_CATEGORIES) return
        val n = count.coerceIn(1, 100)
        update { d ->
            val fresh = List(n) { newQuestion().withCategory(category) }
            val last = d.questions.lastOrNull()
            val list = if (last != null && last.isEmpty()) d.questions.dropLast(1) + fresh + last
            else d.questions + fresh
            d.copy(questions = list)
        }
    }

    /**
     * Parity Step 4: moves a question up or down among the questions of its own category, so the
     * arrows work inside a category tab. Other questions keep their positions.
     */
    fun moveQuestionInCategory(id: String, delta: Int) = update { d ->
        val cat = d.questions.firstOrNull { it.id == id }?.category
        if (cat == null) d
        else {
            val idx = d.questions.indices.filter { d.questions[it].category == cat }
            val pos = idx.indexOfFirst { d.questions[it].id == id }
            val target = pos + delta
            if (pos < 0 || target < 0 || target >= idx.size) d
            else {
                val list = d.questions.toMutableList()
                val a = idx[pos]
                val b = idx[target]
                val tmp = list[a]; list[a] = list[b]; list[b] = tmp
                d.copy(questions = list)
            }
        }
    }

    fun setLayout(id: String, layout: String) = mapQuestion(id) { it.copy(layout = layout) }

    /** Parity Step 6: optional per-question marks, preserved as typed text. */
    fun setQuestionMarks(id: String, marks: String) = mapQuestion(id) { it.copy(marks = marks.take(12)) }

    /** Parity Step 5: T/F answer. Tapping the chosen value again clears it, as on the website. */
    fun setTfAnswer(id: String, value: Boolean?) = mapQuestion(id) { it.copy(tfAnswer = value) }

    /** Parity Step 5: answer of a Blanks, SQ or BQ question. */
    fun setAnswer(id: String, html: String) = mapQuestion(id) { it.copy(answer = html) }

    /** Parity Step 5: adds a CQ sub-question. The website allows at most 8. */
    fun addSubQuestion(id: String) = mapQuestion(id) { q ->
        if (q.subQuestions.size >= 8) q else q.copy(subQuestions = q.subQuestions + SubQuestion(uid("sq")))
    }

    /** Parity Step 5: edits one CQ sub-question (text, marks or answer). */
    fun setSubQuestion(qid: String, sid: String, f: (SubQuestion) -> SubQuestion) = mapQuestion(qid) { q ->
        q.copy(subQuestions = q.subQuestions.map { if (it.id == sid) f(it) else it })
    }

    /** Parity Step 5: deletes a sub-question. The last one is cleared instead, as on the website. */
    fun removeSubQuestion(qid: String, sid: String) = mapQuestion(qid) { q ->
        if (q.subQuestions.size <= 1) {
            q.copy(subQuestions = q.subQuestions.map { if (it.id == sid) it.copy(text = "", answer = "") else it })
        } else {
            q.copy(subQuestions = q.subQuestions.filter { it.id != sid })
        }
    }

    fun moveQuestion(id: String, delta: Int) = update { d ->
        val list = d.questions.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) d
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

    /** Parity Step 3: per-category settings (order, count, columns, section layout, section line). */
    fun setCategory(key: String, f: (CategorySettings) -> CategorySettings) = update { d ->
        d.copy(categories = d.categories.map { if (it.key == key) f(it) else it })
    }

    /**
     * Parity Step 4: sets the tab order from [keys] (the category keys in the order shown on the tabs).
     * The Mix tab is not a category and always stays first.
     */
    fun reorderCategories(keys: List<String>) = update { d ->
        d.copy(categories = d.categories.map { cs ->
            val i = keys.indexOf(cs.key)
            if (i >= 0) cs.copy(order = i) else cs
        })
    }

    /** Parity Step 6 will use this for the "Show marks in [ ]" toggle. */
    fun setShowMarks(show: Boolean) = update { it.copy(showMarks = show) }

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
    fun applyAiQuestions(qs: List<Question>, replace: Boolean, language: String?) = update { d ->
        val base = if (replace) emptyList() else d.questions.filter { !it.isEmpty() }
        var nd = d.copy(questions = base + qs)
        if (language != null && language != d.language) {
            nd = nd.copy(
                language = language,
                settings = nd.settings.copy(fontFamily = if (language == "bn") "Kalpurush" else "Times New Roman"),
            )
        }
        nd
    }

    fun setFolder(id: String?) = update { it.copy(folderId = id) }

    /** Loads a saved paper from History into the editor (keeps the theme, zoom and the AI key of this device). */
    fun loadSnapshot(s: AppData) = update { d -> s.copy(dark = d.dark, ai = d.ai, zoom = 100) }

    fun setActiveSet(i: Int) = update { it.copy(activeSetIndex = i) }
    fun setLanguage(lang: String) = update { it.copy(language = lang) }
    fun setDark(dark: Boolean) = update { it.copy(dark = dark) }
    fun setZoom(z: Int) = update { it.copy(zoom = z.coerceIn(50, 200)) }

    /** Reset the document and settings to defaults, keeping the language, theme and AI key. */
    fun resetDocument() = update { d ->
        AppData(language = d.language, dark = d.dark, ai = d.ai, settings = DocSettings(chemAuto = d.settings.chemAuto))
    }
}
