package com.intelliq.rifatplex.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup file holds once it is read. */
class BackupContent(val folders: List<Folder>, val entries: List<HistoryEntry>, val keys: List<AnswerKeySnapshot>)

sealed class BackupResult {
    class Ready(val file: File, val papers: Int, val folders: Int, val keys: Int) : BackupResult()
    object Empty : BackupResult()
    object Failed : BackupResult()
}

sealed class RestoreResult {
    class Done(val papers: Int, val folders: Int, val keys: Int) : RestoreResult()
    object Invalid : RestoreResult()
    object TooBig : RestoreResult()
    object Failed : RestoreResult()
}

/**
 * The user's own backup. Saved papers, folders and answer-key snapshots live ONLY on this phone; nothing is uploaded to
 * Firebase. When the user asks, they are packed into one .zip (a single backup.json inside) and saved on the phone
 * (Downloads/IntelliQ). Nothing is sent anywhere.
 * Restore reads such a .zip (or a plain .json) and MERGES it: nothing on the phone is deleted, a paper or answer key
 * from the file only replaces the local one when it is newer.
 *
 * Never inside: the Gemini API key (papers are written with default AI settings) and the logo.
 */
object Backup {
    const val FORMAT = "intelliq-backup-1"
    const val GMAIL_PACKAGE = "com.google.android.gm"
    private const val ENTRY = "backup.json"
    private const val MAX_FILE_BYTES = 120L * 1024 * 1024
    private const val MAX_JSON_BYTES = 300L * 1024 * 1024

    // ------------------------------------------------------------------ build

    fun toJson(history: HistoryStore, keys: AnswerKeyStore, email: String, now: Long = System.currentTimeMillis()): String {
        history.awaitLoaded()
        return toJson(history.folders, history.entries, keys.all(), email, now)
    }

    /** Pure (no Android store), so JVM tests cover it. */
    fun toJson(
        folders: List<Folder>, entries: List<HistoryEntry>, keys: List<AnswerKeySnapshot>, email: String, now: Long,
    ): String {
        val o = JSONObject()
        o.put("app", "intelliq")
        o.put("format", FORMAT)
        o.put("createdAt", now)
        o.put("email", email)
        o.put(
            "folders",
            JSONArray().apply {
                folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt)) }
            },
        )
        o.put(
            "papers",
            JSONArray().apply {
                entries.forEach { e ->
                    put(
                        JSONObject()
                            .put("id", e.id).put("title", e.title).put("subtitle", e.subtitle)
                            .put("folderId", e.folderId ?: JSONObject.NULL)
                            .put("language", e.language).put("questionCount", e.questionCount)
                            .put("createdAt", e.createdAt).put("updatedAt", e.updatedAt)
                            .put("hasAnswerKey", e.hasAnswerKey)
                            .put("state", encodeData(e.state.copy(ai = AiSettings()))), // never the API key
                    )
                }
            },
        )
        o.put("keys", JSONArray().apply { keys.forEach { put(AnswerKey.encode(it)) } })
        return o.toString()
    }

    fun parse(json: String): BackupContent? = try {
        val o = JSONObject(json)
        if (o.optString("format") != FORMAT) null
        else {
            val folders = ArrayList<Folder>()
            o.optJSONArray("folders")?.let { a ->
                for (i in 0 until a.length()) {
                    val f = a.optJSONObject(i) ?: continue
                    val id = f.optString("id")
                    if (id.isNotEmpty()) folders.add(Folder(id, f.optString("name"), f.optLong("createdAt")))
                }
            }
            val entries = ArrayList<HistoryEntry>()
            o.optJSONArray("papers")?.let { a ->
                for (i in 0 until a.length()) {
                    try {
                        val p = a.getJSONObject(i)
                        val id = p.getString("id")
                        entries.add(
                            HistoryEntry(
                                id = id,
                                title = p.optString("title"),
                                subtitle = p.optString("subtitle"),
                                folderId = if (p.isNull("folderId")) null else p.optString("folderId").takeIf { it.isNotEmpty() },
                                language = p.optString("language", "en"),
                                questionCount = p.optInt("questionCount"),
                                createdAt = p.optLong("createdAt"),
                                updatedAt = p.optLong("updatedAt"),
                                hasAnswerKey = p.optBoolean("hasAnswerKey"),
                                state = decodeData(p.getString("state"), ExamId.derive(id)),
                            ),
                        )
                    } catch (e: Exception) {
                        // one broken paper must not stop the rest
                    }
                }
            }
            val keys = ArrayList<AnswerKeySnapshot>()
            o.optJSONArray("keys")?.let { a ->
                for (i in 0 until a.length()) {
                    val s = a.optString(i)
                    if (s.isNotEmpty()) AnswerKey.decode(s)?.let { keys.add(it) }
                }
            }
            BackupContent(folders, entries, keys)
        }
    } catch (e: Exception) {
        null
    }

    fun zip(json: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry(ENTRY))
            z.write(json.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        return out.toByteArray()
    }

    /** Builds the file in the cache folder (old backup files there are removed). Runs on a background thread. */
    fun create(context: Context, email: String, asJson: Boolean = false): BackupResult = try {
        val history = HistoryStore.get(context)
        val keys = AnswerKeyStore.get(context)
        history.awaitLoaded()
        val papers = history.entries.size
        val folders = history.folders.size
        val keyCount = keys.all().size
        if (papers == 0 && folders == 0 && keyCount == 0) BackupResult.Empty
        else {
            val now = System.currentTimeMillis()
            val json = toJson(history, keys, email, now)
            val bytes = if (asJson) json.toByteArray(Charsets.UTF_8) else zip(json)
            val dir = File(context.cacheDir, "backups").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now))
            val f = File(dir, "intelliq-backup-$stamp." + (if (asJson) "json" else "zip"))
            f.writeBytes(bytes)
            BackupResult.Ready(f, papers, folders, keyCount)
        }
    } catch (e: Exception) {
        BackupResult.Failed
    } catch (e: OutOfMemoryError) {
        BackupResult.Failed
    }

    /** ACTION_SEND to [email] with the file attached. The caller tries [GMAIL_PACKAGE] first, then a chooser. */
    fun sendIntent(context: Context, file: File, email: String, subject: String, body: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ------------------------------------------------------------------ restore

    /** Reads the picked file and merges it into the phone's data. Runs on a background thread. */
    fun restoreFrom(context: Context, uri: Uri): RestoreResult {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return RestoreResult.Failed
            val bytes = stream.use { readCapped(it, MAX_FILE_BYTES) } ?: return RestoreResult.TooBig
            val content = unpack(bytes)?.let { parse(it) } ?: return RestoreResult.Invalid
            val history = HistoryStore.get(context)
            val keys = AnswerKeyStore.get(context)
            val (papers, folders) = history.mergeRestored(content.entries, content.folders)
            keys.putRemote(content.keys) // a key only replaces the local one when it is newer; no cloud call
            return RestoreResult.Done(papers, folders, content.keys.size)
        } catch (e: Exception) {
            return RestoreResult.Failed
        } catch (e: OutOfMemoryError) {
            return RestoreResult.TooBig
        }
    }

    internal fun unpack(bytes: ByteArray): String? {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return String(bytes, Charsets.UTF_8)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.substringAfterLast('/') == ENTRY) {
                    val raw = readCapped(zin, MAX_JSON_BYTES) ?: return null
                    return String(raw, Charsets.UTF_8)
                }
                e = zin.nextEntry
            }
        }
        return null
    }

    /** Null when the stream is longer than [max]. */
    internal fun readCapped(input: InputStream, max: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
