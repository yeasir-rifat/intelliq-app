package com.intelliq.rifatplex.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intelliq.rifatplex.data.AI_MAX_FILES
import com.intelliq.rifatplex.data.AI_MAX_REQUEST_MB
import com.intelliq.rifatplex.data.AiFile
import com.intelliq.rifatplex.data.AiKind
import com.intelliq.rifatplex.data.AiGenConfig
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.GeminiClient
import com.intelliq.rifatplex.data.GeminiError
import com.intelliq.rifatplex.data.GeminiPrompts
import com.intelliq.rifatplex.data.TooLargeException
import com.intelliq.rifatplex.data.AiFigures
import com.intelliq.rifatplex.data.aiToQuestions
import com.intelliq.rifatplex.data.detectLanguage
import com.intelliq.rifatplex.data.estimatedRequestMb
import com.intelliq.rifatplex.data.extractOfficeText
import com.intelliq.rifatplex.data.isOldOfficeName
import com.intelliq.rifatplex.data.kindOf
import com.intelliq.rifatplex.data.makeThumb
import com.intelliq.rifatplex.data.queryFileInfo
import com.intelliq.rifatplex.data.readLimited
import com.intelliq.rifatplex.data.sourceOf
import com.intelliq.rifatplex.data.uid
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A status line of the AI dialog. [key] is a translation key ([vars] fill {placeholders}); [raw] is text shown as is. */
class AiStatus(
    val key: String = "",
    val vars: Map<String, String> = emptyMap(),
    val error: Boolean = false,
    val raw: String = "",
    val loading: Boolean = false,
    val pct: Int = -1,
)

/** Result of a generation that the main screen turns into a toast. */
class AiEvent(
    val count: Int = 0,
    val requestedModel: String = "",
    val usedModel: String = "",
    val error: AiStatus? = null,
    val summary: String = "", // "MCQ: 20/20 · Gap Filling: 10/10"
    val key: Long = System.nanoTime(),
)

fun aiStatusText(st: AiStatus, lang: Lang): String {
    if (st.key.isEmpty()) return st.raw
    if (st.loading && st.pct in 0..99) return trKey("ai.uploadingPct", lang) + " " + st.pct + "%"
    var s = trKey(st.key, lang)
    st.vars.forEach { (k, v) -> s = s.replace("{$k}", v) }
    return s
}

/** Very short version of an AI error for the bottom toast (the dialog keeps the long text). */
fun aiToastText(st: AiStatus, lang: Lang): String {
    if (st.key.isEmpty()) return st.raw.lineSequence().first().let { if (it.length > 48) it.take(47) + "…" else it }
    val short = trKey(st.key.replace("ai.err.", "toast.ai."), lang)
    if (short == st.key.replace("ai.err.", "toast.ai.")) return aiStatusText(st, lang)
    var s = short
    st.vars.forEach { (k, v) -> s = s.replace("{$k}", v) }
    return s
}

/**
 * State of the "Generate MCQs with AI" dialog. It lives in a ViewModel so a running Gemini request
 * (and the attached files) survive a screen rotation or the camera app restarting the activity.
 */
class AiViewModel : ViewModel() {
    var open by mutableStateOf(false)
    var customPrompt by mutableStateOf(false)
    var prompt by mutableStateOf("")
    val files = mutableStateListOf<AiFile>()
    var status by mutableStateOf<AiStatus?>(null)
    var busy by mutableStateOf(false)
    var event by mutableStateOf<AiEvent?>(null)

    /** Set when the dialog was opened from a type tab: only that type is generated and replaced. */
    var onlyType by mutableStateOf<String?>(null)

    fun openModal(only: String? = null) {
        if (!busy) {
            onlyType = only
            files.clear()
            customPrompt = false
            status = null
        }
        open = true
    }

    fun closeModal() {
        open = false
    }

    fun setCustom(on: Boolean) {
        customPrompt = on
        status = null
    }

    fun setError(key: String, vars: Map<String, String> = emptyMap()) {
        status = AiStatus(key = key, vars = vars, error = true)
    }

    fun removeFile(id: String) {
        files.removeAll { it.id == id }
    }

    fun consumeEvent() {
        event = null
    }

    /* ───────────── attachments ───────────── */

    private fun mb1(bytes: Long) = String.format(Locale.US, "%.1f", bytes / 1048576.0)

    fun addFiles(cr: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val room = AI_MAX_FILES - files.size
        if (room <= 0) {
            setError("ai.err.max10Remove")
            return
        }
        val take = uris.take(room)
        if (uris.size > take.size) setError("ai.err.onlyMore", mapOf("n" to room.toString()))
        for (uri in take) {
            val info = queryFileInfo(cr, uri)
            if (isOldOfficeName(info.name)) {
                setError("ai.err.oldFormat")
                continue
            }
            val kind = kindOf(info)
            if (kind == null) {
                setError("ai.err.badType")
                continue
            }
            if (kind != AiKind.Text && info.size >= 0) {
                val mb = estimatedRequestMb(info.size)
                if (mb > AI_MAX_REQUEST_MB) {
                    setError("ai.err.tooLarge", mapOf("name" to info.name, "size" to mb1(info.size)))
                    continue
                }
                val used = files.filter { it.kind != AiKind.Text }.sumOf { estimatedRequestMb(it.size) }
                if (used + mb > AI_MAX_REQUEST_MB) {
                    setError("ai.err.overLimit", mapOf("name" to info.name))
                    continue
                }
            }
            val id = uid("f")
            files.add(AiFile(id = id, name = info.name, kind = kind, mime = info.mime, source = if (kind == AiKind.Text) sourceOf(info) else ""))
            viewModelScope.launch { loadFile(cr, uri, id, info.name, info.mime, kind, sourceOf(info)) }
        }
    }

    private suspend fun loadFile(cr: ContentResolver, uri: Uri, id: String, name: String, mime: String, kind: AiKind, source: String) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                val stream = cr.openInputStream(uri) ?: throw IllegalStateException("NO_STREAM")
                if (kind == AiKind.Text) {
                    val bytes = readLimited(stream, 80_000_000L)
                    val text = extractOfficeText(bytes, source == "pptx")
                    AiFile(id = id, name = name, kind = kind, mime = mime, text = text, source = source, done = true, size = bytes.size.toLong())
                } else {
                    val bytes = readLimited(stream, (AI_MAX_REQUEST_MB / 1.37 * 1048576.0).toLong())
                    val fixedMime = mime.ifEmpty { if (kind == AiKind.Pdf) "application/pdf" else "image/jpeg" }
                    AiFile(
                        id = id, name = name, kind = kind, mime = fixedMime, bytes = bytes,
                        thumb = if (kind == AiKind.Image) makeThumb(bytes) else null,
                        done = true, size = bytes.size.toLong(),
                    )
                }
            }
            val idx = files.indexOfFirst { it.id == id }
            if (idx < 0) return // the user removed it meanwhile
            if (loaded.kind != AiKind.Text) {
                val others = files.filter { it.id != id && it.kind != AiKind.Text }.sumOf { estimatedRequestMb(it.size) }
                if (others + estimatedRequestMb(loaded.size) > AI_MAX_REQUEST_MB) {
                    files.removeAll { it.id == id }
                    setError("ai.err.overLimit", mapOf("name" to name))
                    return
                }
            }
            files[idx] = loaded
        } catch (e: TooLargeException) {
            files.removeAll { it.id == id }
            setError("ai.err.tooLarge", mapOf("name" to name, "size" to ">43"))
        } catch (e: Exception) {
            files.removeAll { it.id == id }
            if (kind == AiKind.Text) setError("ai.err.docRead", mapOf("name" to name))
            else setError("ai.err.cantRead", mapOf("name" to name))
        }
    }

    /* ───────────── generation ───────────── */

    fun generate(store: AppStore) {
        if (busy) return
        val ai = store.data.ai
        if (ai.apiKey.isBlank()) {
            setError("ai.err.missingKey")
            return
        }
        if (customPrompt) {
            if (prompt.isBlank()) {
                setError("ai.err.noTopic")
                return
            }
        } else {
            if (files.isEmpty()) {
                setError("ai.err.noFile")
                return
            }
            if (files.any { !it.done }) {
                setError("ai.err.waitUpload")
                return
            }
        }
        val only = onlyType
        val chosen = if (only != null) ai.types.filter { it.type == only }.map { it.copy(enabled = true) } else ai.types.filter { it.enabled }
        if (chosen.isEmpty()) {
            setError("ai.err.noTypes")
            return
        }
        if (chosen.sumOf { it.count } > 120) {
            setError("ai.err.tooMany")
            return
        }
        val gen = AiGenConfig(chosen, ai.difficulty, ai.answers, ai.explanations)
        val request = prompt.trim().ifEmpty { "Generate questions from this source." }
        val used = if (customPrompt) emptyList() else files.toList()
        val builtPrompt = GeminiPrompts.build(request, used, customPrompt, gen)
        val baseKey = when {
            customPrompt -> "ai.researchingTopic"
            used.size > 1 -> "ai.analyzingMultiple"
            else -> "ai.analyzingSingle"
        }
        val baseVars = mapOf("n" to used.size.toString())
        val hasMedia = used.any { it.kind != AiKind.Text }

        busy = true
        status = AiStatus(key = baseKey, vars = baseVars, loading = true)
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    GeminiClient.generate(ai.apiKey, ai.model, builtPrompt, used) { p ->
                        if (hasMedia) status = AiStatus(key = baseKey, vars = baseVars, loading = true, pct = if (p >= 1f) -1 else (p * 100).toInt())
                    }
                }
                if (hasMedia) {
                    status = AiStatus(key = "ai.figures", loading = true)
                    withContext(Dispatchers.IO) {
                        try {
                            AiFigures.attach(res.questions, used)
                        } catch (e: Throwable) {
                            // figure extraction is optional: the questions stay valid without pictures
                        }
                    }
                }
                val chem = store.data.settings.chemAuto
                val qs = aiToQuestions(res.questions, gen, chem)
                if (qs.isEmpty()) throw GeminiError("noUsable")
                val replace = store.data.ai.insertMode == "replace"
                store.applyAiQuestions(qs, replace, detectLanguage(qs), only, res.meta)
                val lang = if (store.data.language == "bn") Lang.BN else Lang.EN
                val actual = qs.groupingBy { it.kind() }.eachCount()
                val summary = chosen.joinToString(" · ") { trKey("ai.type." + it.type, lang) + ": " + (actual[it.type] ?: 0) + "/" + it.count }
                event = AiEvent(count = qs.size, requestedModel = ai.model, usedModel = res.usedModel, summary = if (chosen.all { (actual[it.type] ?: 0) >= it.count }) "" else summary)
                status = null
                open = false
            } catch (e: GeminiError) {
                fail(errorStatus(e))
            } catch (e: Exception) {
                fail(AiStatus(key = "ai.err.generic", error = true))
            } finally {
                busy = false
            }
        }
    }

    private fun fail(st: AiStatus) {
        status = st
        if (!open) event = AiEvent(error = st) // dialog was closed meanwhile, so tell the user with a toast
    }

    private fun errorStatus(e: GeminiError): AiStatus = when (e.code) {
        "network" -> AiStatus(key = "ai.err.network", error = true)
        "empty" -> AiStatus(key = "ai.err.empty", error = true)
        "parse" -> AiStatus(key = "ai.err.parse", error = true)
        "truncated" -> AiStatus(key = "ai.err.truncated", error = true)
        "noUsable" -> AiStatus(key = "ai.err.noUsable", error = true)
        "allUnavailable" -> AiStatus(key = "ai.err.allUnavailable", error = true)
        else -> if (e.detail.isNotBlank()) AiStatus(raw = e.detail, error = true)
        else AiStatus(key = "ai.err.request", vars = mapOf("code" to e.status.toString()), error = true)
    }
}
