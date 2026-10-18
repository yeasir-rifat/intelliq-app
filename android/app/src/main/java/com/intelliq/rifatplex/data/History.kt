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

/** [entries] = the merged list (not yet trimmed to [HISTORY_MAX]); [changed] = papers added or replaced by a newer copy. */
class MergeResult(val entries: List<HistoryEntry>, val changed: Int)

/** Backup restore, papers: a new id is added, a known id is replaced only when the incoming copy is newer. Nothing is removed. */
fun mergeEntries(current: List<HistoryEntry>, incoming: List<HistoryEntry>): MergeResult {
    val byId = LinkedHashMap<String, HistoryEntry>()
    current.forEach { byId[it.id] = it }
    var changed = 0
    incoming.forEach { e ->
        val cur = byId[e.id]
        if (cur == null || e.updatedAt > cur.updatedAt) {
            byId[e.id] = e
            changed++
        }
    }
    return MergeResult(byId.values.toList(), changed)
}

/** Backup restore, folders: only the ids the phone does not know yet. */
fun foldersToAdd(current: List<Folder>, incoming: List<Folder>): List<Folder> {
    val known = current.map { it.id }.toSet()
    return incoming.filter { it.id !in known }.distinctBy { it.id }
}

/** Optional hook after every local change. Nothing is registered any more: history is local-only (backups go through Backup.kt). */
interface HistorySyncListener {
    fun entryChanged(e: HistoryEntry)
    fun entryDeleted(id: String)
    fun folderChanged(f: Folder)
}

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

    @Volatile var sync: HistorySyncListener? = null

    init {
        Thread { ensureLoaded() }.apply { priority = Thread.MIN_PRIORITY + 2 }.start()
    }

    /** Blocks until the saved papers are read from disk (used by the cloud sync before its first upload). */
    fun awaitLoaded() = ensureLoaded()

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

    @Synchronized
    fun createFolder(name: String): Folder {
        ensureLoaded()
        val f = Folder(uid("folder"), name.trim(), System.currentTimeMillis())
        folders = folders + f
        saveFolders()
        sync?.folderChanged(f)
        return f
    }

    @Synchronized
    fun renameFolder(id: String, name: String) {
        ensureLoaded()
        folders = folders.map { if (it.id == id) it.copy(name = name.trim()) else it }
        saveFolders()
        folders.firstOrNull { it.id == id }?.let { sync?.folderChanged(it) }
    }

    /** Saves the current document. A paper with the same title, subtitle and folder is updated in place. */
    @Synchronized
    fun record(d: AppData) {
        ensureLoaded()
        val count = d.questions.count { !it.isEmpty() }
        if (count == 0) return
        val title = d.doc.title.trim()
        val subtitle = d.doc.subtitle.trim()
        val folderId = d.folderId
        val now = System.currentTimeMillis()
        var idx = entries.indexOfFirst { it.title == title && it.subtitle == subtitle && it.folderId == folderId }
        // Same title but ANOTHER exam (e.g. after "reset" + the same title): keep the old paper, because its exam id is
        // printed on sheets already. An id that is only derived from the paper id (a save from before step 4.2, never
        // printed with a QR) may be replaced as before.
        entries.getOrNull(idx)?.let { o ->
            val oid = o.state.examId
            if (oid != d.examId && oid != ExamId.derive(o.id)) idx = -1
        }
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
        sync?.entryChanged(entry)
    }

    /**
     * Continuous autosave of the paper being edited. The saved copy is found by its exam id, so typing a new title
     * updates the same entry instead of adding one per keystroke. Nothing is written when nothing changed (so just
     * opening a saved paper does not move it to the top), and a document without a question is never saved.
     */
    @Synchronized
    fun autosave(d: AppData) {
        ensureLoaded()
        if (d.questions.none { !it.isEmpty() }) return
        val idx = entries.indexOfFirst { it.state.examId == d.examId }
        if (idx < 0) {
            record(d)
            return
        }
        val old = entries[idx]
        val clean = d.copy(ai = AiSettings(), dark = old.state.dark, zoom = old.state.zoom)
        if (old.state == clean) return
        val entry = old.copy(
            title = d.doc.title.trim(),
            subtitle = d.doc.subtitle.trim(),
            folderId = d.folderId,
            language = d.language,
            questionCount = d.questions.count { !it.isEmpty() },
            updatedAt = System.currentTimeMillis(),
            hasAnswerKey = d.hasAnswerKey(),
            state = d.copy(ai = AiSettings()),
        )
        entries = listOf(entry) + entries.filterIndexed { i, _ -> i != idx }
        saveEntries()
        sync?.entryChanged(entry)
    }

    @Synchronized
    fun delete(id: String) {
        ensureLoaded()
        entries = entries.filter { it.id != id }
        saveEntries()
        sync?.entryDeleted(id)
    }

    /** Cloud -> device. [newEntries] / [newFolders] null = leave that list alone. Does not call [sync] (no echo back). */
    @Synchronized
    fun applyRemote(newEntries: List<HistoryEntry>?, newFolders: List<Folder>?) {
        ensureLoaded()
        if (newEntries != null) {
            entries = newEntries.sortedByDescending { it.updatedAt }.take(HISTORY_MAX)
            saveEntries()
        }
        if (newFolders != null) {
            folders = newFolders.sortedBy { it.createdAt }
            saveFolders()
        }
    }

    /**
     * Restore from a backup file: MERGES, never deletes. A paper with a new id is added, a paper with a known id is replaced
     * only when the backup copy is newer; folders are added by id. Returns (papers added or updated, folders added).
     */
    @Synchronized
    fun mergeRestored(newEntries: List<HistoryEntry>, newFolders: List<Folder>): Pair<Int, Int> {
        ensureLoaded()
        val merged = mergeEntries(entries, newEntries)
        val addFolders = foldersToAdd(folders, newFolders)
        applyRemote(merged.entries, folders + addFolders)
        return merged.changed to addFolders.size
    }

    /** Wipes every saved paper and folder (not called by the app any more: a different account no longer clears them). */
    fun clearAll() = applyRemote(emptyList(), emptyList())

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
                    state = decodeData(o.getString("state"), ExamId.derive(o.getString("id"))),
                )
            } catch (e: Exception) {
                null // skip one corrupted entry, keep the rest
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
