package com.intelliq.rifatplex.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

data class Folder(val id: String, val name: String, val createdAt: Long)

data class HistoryEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val folderId: String?,
    val language: String,
    val questionCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val hasAnswerKey: Boolean,
    val state: AppData,
)

const val HISTORY_MAX = 200

/** Saved papers + folders (same rules as the website's history.js), kept in SharedPreferences. */
class HistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("intelliq_history", Context.MODE_PRIVATE)

    private val io = Executors.newSingleThreadExecutor()
    private val loadLock = Any()
    @Volatile private var loaded = false

    // Loaded in the background (up to 200 saved papers = a lot of JSON); the lists fill in a moment after start.
    var entries by mutableStateOf(emptyList<HistoryEntry>())
        private set
    var folders by mutableStateOf(emptyList<Folder>())
        private set

    init {
        Thread { ensureLoaded() }.apply { priority = Thread.MIN_PRIORITY + 2 }.start()
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            entries = loadEntries()
            folders = loadFolders()
            loaded = true
        }
    }

    companion object {
        @Volatile private var instance: HistoryStore? = null
        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) { instance ?: HistoryStore(context.applicationContext).also { instance = it } }
    }

    fun findFolder(id: String?): Folder? = if (id == null) null else folders.firstOrNull { it.id == id }

    fun createFolder(name: String): Folder {
        ensureLoaded()
        val f = Folder(uid("folder"), name.trim(), System.currentTimeMillis())
        folders = folders + f
        saveFolders()
        return f
    }

    fun renameFolder(id: String, name: String) {
        ensureLoaded()
        folders = folders.map { if (it.id == id) it.copy(name = name.trim()) else it }
        saveFolders()
    }

    /** Saves the current document. A paper with the same title, subtitle and folder is updated in place. */
    fun record(d: AppData) {
        ensureLoaded()
        val count = d.questions.count { !it.isEmpty() }
        if (count == 0) return
        val title = d.doc.title.trim()
        val subtitle = d.doc.subtitle.trim()
        val folderId = d.folderId
        val now = System.currentTimeMillis()
        val idx = entries.indexOfFirst { it.title == title && it.subtitle == subtitle && it.folderId == folderId }
        val old = entries.getOrNull(idx)
        val entry = HistoryEntry(
            id = old?.id ?: uid("hist"),
            title = title,
            subtitle = subtitle,
            folderId = folderId,
            language = d.language,
            questionCount = count,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            hasAnswerKey = d.hasAnswerKey(),
            state = d.copy(ai = AiSettings()), // never store the API key inside history
        )
        entries = (listOf(entry) + entries.filterIndexed { i, _ -> i != idx }).take(HISTORY_MAX)
        saveEntries()
    }

    fun delete(id: String) {
        ensureLoaded()
        entries = entries.filter { it.id != id }
        saveEntries()
    }

    // ----- persistence -----

    private fun saveEntries() {
        val list = entries
        io.execute { writeEntries(list) } // encoding up to 200 papers must not block the screen
    }

    private fun writeEntries(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id).put("title", e.title).put("subtitle", e.subtitle)
                    .put("folderId", e.folderId ?: JSONObject.NULL)
                    .put("language", e.language).put("questionCount", e.questionCount)
                    .put("createdAt", e.createdAt).put("updatedAt", e.updatedAt)
                    .put("hasAnswerKey", e.hasAnswerKey)
                    .put("state", encodeData(e.state)),
            )
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    private fun saveFolders() {
        val arr = JSONArray()
        folders.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt)) }
        prefs.edit().putString("folders", arr.toString()).apply()
    }

    private fun loadFolders(): List<Folder> = try {
        val arr = JSONArray(prefs.getString("folders", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Folder(o.getString("id"), o.optString("name"), o.optLong("createdAt"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun loadEntries(): List<HistoryEntry> = try {
        val arr = JSONArray(prefs.getString("entries", "[]"))
        (0 until arr.length()).mapNotNull {
            try {
                val o = arr.getJSONObject(it)
                HistoryEntry(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    subtitle = o.optString("subtitle"),
                    folderId = if (o.isNull("folderId")) null else o.optString("folderId").takeIf { f -> f.isNotEmpty() },
                    language = o.optString("language", "en"),
                    questionCount = o.optInt("questionCount"),
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt"),
                    hasAnswerKey = o.optBoolean("hasAnswerKey"),
                    state = decodeData(o.getString("state")),
                )
            } catch (e: Exception) {
                null // skip one corrupted entry, keep the rest
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
