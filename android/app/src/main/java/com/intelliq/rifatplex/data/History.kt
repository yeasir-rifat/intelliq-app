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
    private val deletedEntryIds = mutableSetOf<String>()

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
            deletedEntryIds.clear()
            deletedEntryIds.addAll(loadDeletedEntryIds())
            loaded = true
        }
    }

    companion object {
        @Volatile private var instance: HistoryStore? = null
        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) { instance ?: HistoryStore(context.applicationContext).also { instance = it } }
    }

    /** Ensures asynchronous startup loading has completed before cloud sync reads local data. */
    fun snapshotForCloudSync(): Pair<List<HistoryEntry>, List<Folder>> {
        ensureLoaded()
        return entries to folders
    }

    /** IDs deleted locally but not yet acknowledged by Firestore; persisted across app restarts. */
    fun pendingDeletedEntryIdsForCloudSync(): Set<String> {
        ensureLoaded()
        return deletedEntryIds.toSet()
    }

    fun acknowledgeCloudDeletedEntry(id: String) {
        ensureLoaded()
        if (deletedEntryIds.remove(id)) saveDeletedEntryIds()
    }

    /** Apply deletions observed in a subsequent complete cloud snapshot without re-uploading them. */
    fun removeCloudDeletedEntries(ids: Set<String>) {
        ensureLoaded()
        if (ids.isEmpty()) return
        val next = entries.filterNot { it.id in ids }
        if (next != entries) {
            entries = next
            saveEntries()
        }
    }

    fun removeCloudDeletedFolders(ids: Set<String>) {
        ensureLoaded()
        if (ids.isEmpty()) return
        val next = folders.filterNot { it.id in ids }
        if (next != folders) {
            folders = next
            saveFolders()
        }
    }

    private fun loadDeletedEntryIds(): Set<String> = try {
        val arr = JSONArray(prefs.getString("deletedEntryIds", "[]"))
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet()
    } catch (_: Exception) { emptySet() }

    private fun saveDeletedEntryIds() {
        prefs.edit().putString("deletedEntryIds", JSONArray(deletedEntryIds.toList()).toString()).apply()
    }

    /** Merge a cloud snapshot into local History without deleting local-only records. */
    fun mergeCloudEntries(remoteEntries: List<HistoryEntry>, remoteFolders: List<Folder>) {
        ensureLoaded()
        val folderMap = folders.associateBy { it.id }.toMutableMap()
        remoteFolders.forEach { incoming ->
            val old = folderMap[incoming.id]
            if (old == null || incoming.createdAt >= old.createdAt) folderMap[incoming.id] = incoming
        }
        val mergedFolders = folderMap.values.sortedBy { it.createdAt }
        if (mergedFolders != folders) {
            folders = mergedFolders
            saveFolders()
        }

        val entryMap = entries.associateBy { it.id }.toMutableMap()
        remoteEntries.filterNot { it.id in deletedEntryIds }.forEach { incoming ->
            val old = entryMap[incoming.id]
            if (old == null || incoming.updatedAt > old.updatedAt) entryMap[incoming.id] = incoming
        }
        val mergedEntries = entryMap.values.sortedByDescending { it.updatedAt }.take(HISTORY_MAX)
        if (mergedEntries != entries) {
            entries = mergedEntries
            saveEntries()
        }
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
        val existed = entries.any { it.id == id }
        entries = entries.filter { it.id != id }
        if (existed) {
            deletedEntryIds.add(id)
            saveDeletedEntryIds()
        }
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

