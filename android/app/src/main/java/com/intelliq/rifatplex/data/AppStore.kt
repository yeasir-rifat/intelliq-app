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
    fun setLayout(id: String, layout: String) = mapQuestion(id) { it.copy(layout = layout) }

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
