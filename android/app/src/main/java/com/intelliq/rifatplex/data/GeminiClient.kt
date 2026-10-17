package com.intelliq.rifatplex.data

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** One question as Gemini returned it (still in Gemini markup). */
class AiRawSub(val question: String, val marks: String, val answer: String) {
    /** Figure the AI located in the attached file (website `figure`) and the picture tag cut out of it. */
    var figure: AiFigure? = null
    var figTag: String = ""
}

/** Where a figure sits in an attached image / PDF: 1-based [file] (among the images / PDFs sent), [page], [box] = ymin, xmin, ymax, xmax in 0..1000. */
class AiFigure(val file: Int, val page: Int, val box: List<Float>)

class AiRawQuestion(
    val type: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val tfAnswer: Boolean?,
    val answer: String,
    val explanation: String,
    val marks: String,
    val subs: List<AiRawSub>,
) {
    var figure: AiFigure? = null
    var figTag: String = ""
}

/** Header suggestion of the AI (website `meta`). */
class AiMeta(val title: String, val subtitle: String, val marks: String, val time: String, val note: String, val chapter: String = "", val examType: String = "")

class AiParsed(val questions: List<AiRawQuestion>, val meta: AiMeta?)

class AiGenResult(val questions: List<AiRawQuestion>, val usedModel: String, val meta: AiMeta? = null)

/**
 * Error with a [code] the UI turns into a translated message:
 * network | retired | api | empty | parse | truncated | allUnavailable. For "api" [detail] is Google's own message.
 */
class GeminiError(val code: String, val detail: String = "", val status: Int = 0) : Exception(detail.ifEmpty { code })

object GeminiClient {
    /** Models with a free-tier row on the Gemini API (checked Oct 2026). Order = fallback order. */
    val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

    /** Tries [model] first, then the other models when one has been retired. [onProgress] gets 0..1 while uploading. */
    fun generate(
        apiKey: String,
        model: String,
        prompt: String,
        files: List<AiFile>,
        onProgress: (Float) -> Unit,
    ): AiGenResult {
        var lastError: GeminiError? = null
        for (m in listOf(model) + MODELS.filter { it != model }) {
            try {
                val body = request(m, apiKey, prompt, files, onProgress)
                val parsed = parse(body)
                return AiGenResult(parsed.questions, m, parsed.meta)
            } catch (e: GeminiError) {
                lastError = e
                if (e.code == "retired") continue
                throw e
            }
        }
        throw lastError ?: GeminiError("allUnavailable")
    }

    /** Result of one AI Edit request: the revised question as Gemini's JSON object (still in Gemini markup). */
    class EditResult(val data: JSONObject, val usedModel: String)

    /** AI Edit (website `iq-aiedit` callAi): one JSON object back, same model fallback as [generate], 150 s limit. */
    fun edit(apiKey: String, model: String, prompt: String): EditResult {
        var lastError: GeminiError? = null
        for (m in listOf(model) + MODELS.filter { it != model }) {
            try {
                val body = request(m, apiKey, prompt, emptyList(), {}, readTimeoutMs = 150_000, temperature = 0.3)
                return EditResult(parseObject(body), m)
            } catch (e: GeminiError) {
                lastError = e
                if (e.code == "retired") continue
                throw e
            }
        }
        throw lastError ?: GeminiError("allUnavailable")
    }

    private fun parseObject(body: String): JSONObject {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw GeminiError("parse")
        }
        val cand = root.optJSONArray("candidates")?.optJSONObject(0)
        val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
        val text = if (parts == null) "" else (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text", "").orEmpty() }
        if (text.isBlank()) throw GeminiError("empty")
        val u = text.trim()
            .replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^```\\s*"), "")
            .replace(Regex("```\\s*$"), "")
            .trim()
        try {
            if (u.startsWith("{")) return JSONObject(u)
        } catch (e: Exception) {
            // try the {...} block below
        }
        val m = Regex("\\{[\\s\\S]*\\}").find(u)
        if (m != null) {
            try {
                return JSONObject(m.value)
            } catch (e: Exception) {
                // handled below
            }
        }
        if (cand?.optString("finishReason") == "MAX_TOKENS") throw GeminiError("truncated")
        throw GeminiError("parse")
    }

    private fun request(
        model: String,
        apiKey: String,
        prompt: String,
        files: List<AiFile>,
        onProgress: (Float) -> Unit,
        readTimeoutMs: Int = 300_000,
        temperature: Double = 0.4,
    ): String {
        val media = files.filter { (it.kind == AiKind.Image || it.kind == AiKind.Pdf) && it.bytes != null }

        // The body is streamed (base64 is encoded chunk by chunk) so big files never need a giant String in memory.
        val segments = ArrayList<Pair<ByteArray?, ByteArray?>>() // (utf8 text, raw bytes to base64) - exactly one is set
        fun addText(s: String) { segments.add(s.toByteArray(Charsets.UTF_8) to null) }
        addText("{\"contents\":[{\"parts\":[{\"text\":" + JSONObject.quote(prompt) + "}")
        for (f in media) {
            addText(",{\"inline_data\":{\"mime_type\":" + JSONObject.quote(f.mime) + ",\"data\":\"")
            segments.add(null to f.bytes)
            addText("\"}}")
        }
        addText("]}],\"generationConfig\":{\"temperature\":" + temperature + ",\"responseMimeType\":\"application/json\"}}")

        var total = 0L
        for ((t, r) in segments) total += t?.size?.toLong() ?: (((r!!.size + 2L) / 3L) * 4L)

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${URLEncoder.encode(model, "UTF-8")}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.setFixedLengthStreamingMode(total)

            var written = 0L
            var lastReported = -1
            fun report() {
                val pct = if (total > 0) (written * 100 / total).toInt() else 100
                if (pct != lastReported) {
                    lastReported = pct
                    onProgress(pct / 100f)
                }
            }
            conn.outputStream.use { out ->
                for ((t, r) in segments) {
                    if (t != null) {
                        out.write(t)
                        written += t.size
                    } else {
                        val raw = r!!
                        var off = 0
                        val chunk = 3 * 24 * 1024 // multiple of 3, so every chunk encodes without padding except the last
                        while (off < raw.size) {
                            val len = minOf(chunk, raw.size - off)
                            val enc = Base64.encode(raw, off, len, Base64.NO_WRAP)
                            out.write(enc)
                            written += enc.size
                            off += len
                            report()
                        }
                    }
                }
                out.flush()
            }
            onProgress(1f)

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status in 200..299) return text

            var message = ""
            try {
                message = JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
            } catch (e: Exception) {
                // not JSON
            }
            // like the website: a retired model, rate limit or server error moves on to the next model
            val retired = status == 404 || status == 429 || status >= 500
            if (retired) throw GeminiError("retired", message, status)
            throw GeminiError("api", message, status)
        } catch (e: java.net.SocketTimeoutException) {
            throw GeminiError(if (readTimeoutMs < 300_000) "timeout" else "network")
        } catch (e: IOException) {
            throw GeminiError("network")
        } finally {
            conn.disconnect()
        }
    }

    private fun txt(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is JSONObject -> v.optString("html")
        else -> v.toString()
    }.trim()

    private fun isTrue(v: Any?): Boolean {
        val t = txt(v).lowercase()
        return v == true || t == "true" || t == "সত্য"
    }

    private fun parseFigure(o: JSONObject?): AiFigure? {
        o ?: return null
        val b = o.optJSONArray("box") ?: return null
        if (b.length() != 4) return null
        val box = (0 until 4).map { b.optDouble(it, Double.NaN).toFloat() }
        if (box.any { it.isNaN() }) return null
        return AiFigure(o.optInt("file", 1), o.optInt("page", 1), box)
    }

    private fun parse(body: String): AiParsed {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw GeminiError("parse")
        }
        val cand = root.optJSONArray("candidates")?.optJSONObject(0)
        val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
        val text = if (parts == null) "" else (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text", "").orEmpty() }
        if (text.isBlank()) throw GeminiError("empty")

        var u = text.trim()
        u = u.replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^```\\s*"), "")
            .replace(Regex("```\\s*$"), "")
            .trim()

        var arr: JSONArray? = null
        var metaObj: JSONObject? = null
        try {
            if (u.startsWith("[")) {
                arr = JSONArray(u)
            } else {
                val o = JSONObject(u)
                arr = o.optJSONArray("questions")
                metaObj = o.optJSONObject("meta")
            }
        } catch (e: Exception) {
            val m = Regex("\\{[\\s\\S]*\\}").find(u)
            if (m != null) {
                try {
                    val o = JSONObject(m.value)
                    arr = o.optJSONArray("questions")
                    metaObj = o.optJSONObject("meta")
                } catch (e2: Exception) {
                    // handled below
                }
            }
        }
        if (arr == null || arr.length() == 0) {
            if (cand?.optString("finishReason") == "MAX_TOKENS") throw GeminiError("truncated")
            throw GeminiError("parse")
        }

        val out = ArrayList<AiRawQuestion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val optArr = o.optJSONArray("options")
            val options = if (optArr == null) emptyList() else (0 until optArr.length()).map { txt(optArr.opt(it)) }
            val subArr = o.optJSONArray("subQuestions")
            val subs = if (subArr == null) emptyList() else (0 until subArr.length()).mapNotNull { k ->
                val so = subArr.optJSONObject(k) ?: return@mapNotNull null
                AiRawSub(
                    question = txt(if (so.has("question")) so.opt("question") else so.opt("text")),
                    marks = txt(so.opt("marks")),
                    answer = txt(so.opt("answer")),
                ).also { it.figure = parseFigure(so.optJSONObject("figure")) }
            }
            val tf: Boolean? = when {
                o.opt("tfAnswer") is Boolean -> o.getBoolean("tfAnswer")
                o.has("answer") && !o.isNull("answer") -> isTrue(o.opt("answer"))
                else -> null
            }
            out.add(
                AiRawQuestion(
                    type = txt(o.opt("type")).lowercase().ifEmpty { "mcq" },
                    question = txt(if (o.has("question")) o.opt("question") else o.opt("stimulus")),
                    options = options,
                    correctIndex = o.optInt("correctIndex", 0),
                    tfAnswer = tf,
                    answer = txt(if (o.has("answer")) o.opt("answer") else o.opt("modelAnswer")),
                    explanation = txt(if (o.has("explanation")) o.opt("explanation") else o.opt("markingGuide")),
                    marks = txt(o.opt("marks")),
                    subs = subs,
                ).also { it.figure = parseFigure(o.optJSONObject("figure")) },
            )
        }
        val meta = metaObj?.let {
            AiMeta(txt(it.opt("title")), txt(it.opt("subtitle")), txt(it.opt("marks")), txt(it.opt("time")), txt(it.opt("note")), txt(it.opt("chapter")), txt(it.opt("examType")))
        }
        return AiParsed(out, meta)
    }
}
