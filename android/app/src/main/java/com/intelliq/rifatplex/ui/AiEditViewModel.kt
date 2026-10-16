package com.intelliq.rifatplex.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intelliq.rifatplex.data.AI_EDIT_ACTIONS
import com.intelliq.rifatplex.data.AiEditCtx
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.GeminiClient
import com.intelliq.rifatplex.data.GeminiError
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.aiEditApply
import com.intelliq.rifatplex.data.aiEditPrompt
import com.intelliq.rifatplex.data.aiEditSnapshot
import com.intelliq.rifatplex.data.aiEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State of the "AI Edit" dialog of one question (website `iq-aiedit`): form -> loading -> result.
 * It lives in a ViewModel so a running Gemini request survives a screen rotation.
 */
class AiEditViewModel : ViewModel() {
    /** Id of the question being edited; null = dialog closed. */
    var qid by mutableStateOf<String?>(null)
        private set

    /** Number shown on the card (section aware), for the dialog subtitle. */
    var number by mutableStateOf(1)
        private set

    var sel by mutableStateOf(setOf("fix"))
        private set
    var instr by mutableStateOf("")

    /** form | loading | result */
    var view by mutableStateOf("form")
        private set

    /** Translation key of the error, or [errRaw] (Google's own message) when the key is empty. */
    var errKey by mutableStateOf("")
        private set
    var errRaw by mutableStateOf("")
        private set

    var prop by mutableStateOf<Question?>(null)
        private set
    var summary by mutableStateOf("")
        private set

    private var job: Job? = null
    private var seq = 0

    fun open(id: String, num: Int) {
        seq++
        job?.cancel()
        qid = id
        number = num
        sel = setOf("fix")
        instr = ""
        view = "form"
        errKey = ""
        errRaw = ""
        prop = null
        summary = ""
    }

    fun toggle(action: String) {
        sel = if (action in sel) sel - action else sel + action
        errKey = ""
        errRaw = ""
    }

    fun close() {
        seq++
        job?.cancel()
        qid = null
        prop = null
        view = "form"
    }

    fun again() {
        view = "form"
        errKey = ""
        errRaw = ""
    }

    private fun fail(key: String, raw: String = "") {
        view = "form"
        errKey = key
        errRaw = raw
    }

    fun run(store: AppStore) {
        val id = qid ?: return
        val acts = AI_EDIT_ACTIONS.filter { it in sel }
        val ins = instr.trim()
        if (acts.isEmpty() && ins.isEmpty()) {
            fail("aie.errNeed")
            return
        }
        val q = store.data.questions.firstOrNull { it.id == id }?.ensured()
        if (q == null) {
            close()
            return
        }
        val apiKey = store.data.ai.apiKey.trim()
        if (apiKey.isEmpty()) {
            fail("aie.errNoKey")
            return
        }
        val ctx = AiEditCtx()
        val prompt = aiEditPrompt(q.kind(), aiEditSnapshot(q, ctx), acts, ins, store.data.language == "bn")
        val chem = store.data.settings.chemAuto
        val model = store.data.ai.model

        val my = ++seq
        view = "loading"
        errKey = ""
        errRaw = ""
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { GeminiClient.edit(apiKey, model, prompt) }
                if (my != seq || qid == null) return@launch
                val cur = store.data.questions.firstOrNull { it.id == id }?.ensured()
                if (cur == null) {
                    close()
                    return@launch
                }
                prop = aiEditApply(cur, res.data, ctx, chem)
                summary = aiEditText(res.data.opt("changes")).replace(Regex("<[^>]+>"), "").trim()
                view = "result"
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiError) {
                if (my == seq) {
                    when (e.code) {
                        "network" -> fail("aie.errNet")
                        "timeout" -> fail("aie.errTimeout")
                        "truncated" -> fail("aie.errCut")
                        "allUnavailable", "retired" -> fail("aie.errAll")
                        "api" -> if (e.detail.isNotBlank()) fail("", e.detail) else fail("aie.errBad")
                        else -> fail("aie.errBad")
                    }
                }
            } catch (e: Exception) {
                if (my == seq) fail("aie.errBad")
            }
        }
    }

    /** Puts the proposed version into the question. Returns the question as it was (for Undo), or null when nothing was applied. */
    fun apply(store: AppStore): Question? {
        val id = qid ?: return null
        val p = prop ?: return null
        val backup = store.data.questions.firstOrNull { it.id == id }
        if (backup == null) {
            close()
            return null
        }
        store.update { d ->
            d.copy(
                questions = d.questions.map {
                    if (it.id == id) {
                        it.copy(
                            text = p.text, options = p.options, layout = p.layout, explanation = p.explanation,
                            tfAnswer = p.tfAnswer, answer = p.answer, subs = p.subs,
                        )
                    } else it
                },
            )
        }
        close()
        return backup
    }

    /** Undo of [apply]: the question is exactly as it was before. */
    fun restore(store: AppStore, backup: Question) {
        store.update { d -> d.copy(questions = d.questions.map { if (it.id == backup.id) backup else it }) }
    }
}
