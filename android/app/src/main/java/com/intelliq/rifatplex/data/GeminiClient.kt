package com.intelliq.rifatplex.data

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** One question as Gemini returned it (still in Gemini markup). */
class AiRawSubQuestion(val question: String, val marks: String, val answer: String)
class AiRawQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val answer: String = "",
    val tfAnswer: Boolean? = null,
    val stimulus: String = "",
    val subQuestions: List<AiRawSubQuestion> = emptyList(),
)

class AiGenResult(val questions: List<AiRawQuestion>, val usedModel: String)

/**
 * Error with a [code] the UI turns into a translated message:
 * network | retired | api | empty | parse | allUnavailable. For "api" [detail] is Google's own message.
 */
class GeminiError(val code: String, val detail: String = "", val status: Int = 0) : Exception(detail.ifEmpty { code })

object GeminiClient {
    /** Same fallback order as the website. */
    val MODELS = listOf("gemini-3-flash-preview", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite", "gemini-2.0-flash")

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
                return AiGenResult(parse(body), m)
            } catch (e: GeminiError) {
                lastError = e
                if (e.code == "retired") continue
                throw e
            }
        }
        throw lastError ?: GeminiError("allUnavailable")
    }

    private fun request(model: String, apiKey: String, prompt: String, files: List<AiFile>, onProgress: (Float) -> Unit): String {
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
        addText("]}],\"generationConfig\":{\"temperature\":0.4,\"responseMimeType\":\"application/json\"}}")

        var total = 0L
        for ((t, r) in segments) total += t?.size?.toLong() ?: (((r!!.size + 2L) / 3L) * 4L)

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${URLEncoder.encode(model, "UTF-8")}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 300_000
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
            val retired = status == 404 && Regex("no longer available|not found", RegexOption.IGNORE_CASE).containsMatchIn(message)
            if (retired) throw GeminiError("retired", message, status)
            throw GeminiError("api", message, status)
        } catch (e: IOException) {
            throw GeminiError("network")
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<AiRawQuestion> {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw GeminiError("parse")
        }
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val text = if (parts == null) "" else (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text", "").orEmpty() }
        if (text.isBlank()) throw GeminiError("empty")

        var u = text.trim()
        u = u.replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^```\\s*"), "")
            .replace(Regex("```\\s*$"), "")
            .trim()

        var arr: JSONArray? = null
        try {
            if (u.startsWith("[")) arr = JSONArray(u) else arr = JSONObject(u).optJSONArray("questions")
        } catch (e: Exception) {
            val m = Regex("\\{[\\s\\S]*\\}").find(u)
            if (m != null) {
                try {
                    arr = JSONObject(m.value).optJSONArray("questions")
                } catch (e2: Exception) {
                    // handled below
                }
            }
        }
        if (arr == null || arr.length() == 0) throw GeminiError("parse")

        val out = ArrayList<AiRawQuestion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val optArr = o.optJSONArray("options")
            val options = if (optArr == null) emptyList() else (0 until optArr.length()).map { optArr.opt(it)?.toString().orEmpty().trim() }
            val subArr = o.optJSONArray("subQuestions")
            val subs = if (subArr == null) emptyList() else (0 until subArr.length()).mapNotNull { si ->
                val sub = subArr.optJSONObject(si) ?: return@mapNotNull null
                AiRawSubQuestion(sub.optString("question").trim(), sub.optString("marks", "1"), sub.optString("answer").trim())
            }
            val answerValue = o.opt("answer")
            out.add(AiRawQuestion(
                question = o.optString("question").trim(),
                options = options,
                correctIndex = o.optInt("correctIndex", -1),
                explanation = o.optString("explanation").trim(),
                answer = if (answerValue is Boolean) answerValue.toString() else answerValue?.toString().orEmpty().trim(),
                tfAnswer = if (answerValue is Boolean) answerValue else null,
                stimulus = o.optString("stimulus", o.optString("question")).trim(),
                subQuestions = subs,
            ))
        }
        return out
    }
}
