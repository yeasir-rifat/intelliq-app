package com.intelliq.rifatplex.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONObject
import kotlinx.coroutines.tasks.await

/** Firebase auth and per-user Firestore sync. Local History remains available without sign-in. */
class CloudSync(context: Context) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val google = GoogleSignIn.getClient(
        appContext,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(appContext.getString(com.intelliq.rifatplex.R.string.default_web_client_id))
            .requestEmail().build(),
    )
    private var paperListener: ListenerRegistration? = null
    private var folderListener: ListenerRegistration? = null
    @Volatile private var lastUploadedPaperIds: Set<String> = emptySet()
    @Volatile private var lastUploadedFolderIds: Set<String> = emptySet()
    @Volatile private var knownRemotePaperIds: Set<String>? = null
    @Volatile private var knownRemoteFolderIds: Set<String>? = null

    val currentUser get() = auth.currentUser
    fun signInIntent(): Intent = google.signInIntent

    suspend fun completeSignIn(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = task.await()
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential).await()
    }

    suspend fun signOut() {
        stopSync()
        auth.signOut()
        google.signOut().await()
    }

    fun startSync(
        history: HistoryStore,
        userSettings: Map<String, Any?> = emptyMap(),
        onError: (String) -> Unit = {},
        onRemoteSettings: (Map<String, Any?>) -> Unit = {},
    ) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val userRef = db.collection("users").document(uid)
        // Ensure local History has finished loading before the initial upload.
        history.snapshotForCloudSync()
        // Read remote settings before writing local values so signing in on a second device
        // does not overwrite the account's existing settings with defaults.
        userRef.get().addOnSuccessListener { snap ->
            val remote = snap.data.orEmpty()
            val remoteSettings = mapOf(
                "geminiApiKey" to remote["geminiApiKey"],
                "geminiModel" to remote["geminiModel"],
                "aiInsertMode" to remote["aiInsertMode"],
            ).filterValues { it != null }
            onRemoteSettings(remoteSettings)
            val patch = mutableMapOf<String, Any>(
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: ""),
                "lastSyncAt" to System.currentTimeMillis(),
            )
            listOf("geminiApiKey", "geminiModel", "aiInsertMode").forEach { key ->
                if (remote[key] == null && userSettings[key] != null) patch[key] = userSettings[key]!!
            }
            userRef.set(patch, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { onError(it.message ?: "Could not sync account settings") }
        }.addOnFailureListener { onError(it.message ?: "Could not load account settings") }

        // Initial local-to-cloud merge uses stable IDs and merge writes, never a destructive overwrite.
        uploadLocalSnapshot(history, onError)
        stopListenersOnly()
        knownRemotePaperIds = null
        knownRemoteFolderIds = null
        folderListener = db.collection("users").document(uid).collection("folders")
            .addSnapshotListener { snap, error ->
                if (error != null) { onError(error.message ?: "Folder sync failed"); return@addSnapshotListener }
                val remote = snap?.documents.orEmpty().mapNotNull { d ->
                    val id = d.getString("id") ?: d.id
                    val name = d.getString("name") ?: return@mapNotNull null
                    Folder(id, name, timestampMillis(d.get("createdAt")))
                }
                val ids = remote.map { it.id }.toSet()
                knownRemoteFolderIds?.minus(ids)?.let(history::removeCloudDeletedFolders)
                knownRemoteFolderIds = ids
                history.mergeCloudEntries(emptyList(), remote)
            }
        paperListener = db.collection("users").document(uid).collection("papers")
            .addSnapshotListener { snap, error ->
                if (error != null) { onError(error.message ?: "History sync failed"); return@addSnapshotListener }
                val remote = snap?.documents.orEmpty().mapNotNull { d ->
                    try {
                        val stateValue = d.get("state")
                        val stateJson = when (stateValue) {
                            is Map<*, *> -> JSONObject(stateValue.filterKeys { it is String }.mapKeys { it.key as String }).toString()
                            is String -> stateValue
                            else -> d.getString("stateJson") ?: return@mapNotNull null
                        }
                        val state = decodeData(stateJson)
                        HistoryEntry(
                            id = d.getString("id") ?: d.id,
                            title = d.getString("title") ?: "",
                            subtitle = d.getString("subtitle") ?: "",
                            folderId = d.getString("folderId"),
                            language = d.getString("language") ?: "en",
                            questionCount = (d.getLong("questionCount") ?: 0L).toInt(),
                            createdAt = timestampMillis(d.get("createdAt")),
                            updatedAt = timestampMillis(d.get("updatedAt")),
                            hasAnswerKey = d.getBoolean("hasAnswerKey") ?: false,
                            state = state,
                        )
                    } catch (_: Exception) { null }
                }
                val ids = remote.map { it.id }.toSet()
                knownRemotePaperIds?.minus(ids)?.let(history::removeCloudDeletedEntries)
                knownRemotePaperIds = ids
                history.mergeCloudEntries(remote, emptyList())
            }
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            result[key] = when (value) {
                JSONObject.NULL -> null
                is JSONObject -> jsonObjectToMap(value)
                is org.json.JSONArray -> (0 until value.length()).map { index ->
                    when (val item = value.get(index)) {
                        JSONObject.NULL -> null
                        is JSONObject -> jsonObjectToMap(item)
                        is org.json.JSONArray -> item.toString()
                        else -> item
                    }
                }
                else -> value
            }
        }
        return result
    }

    private fun timestampMillis(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is com.google.firebase.Timestamp -> value.toDate().time
        is String -> try { java.time.Instant.parse(value).toEpochMilli() } catch (_: Exception) { value.toLongOrNull() ?: 0L }
        else -> 0L
    }

    /** Uploads current local papers and folders. Safe to call after local History changes. */
    fun uploadLocalSnapshot(history: HistoryStore, onError: (String) -> Unit = {}) {
        val user = auth.currentUser ?: return
        val (localEntries, localFolders) = history.snapshotForCloudSync()
        val userRef = db.collection("users").document(user.uid)
        val paperIds = localEntries.map { it.id }.toSet()
        val folderIds = localFolders.map { it.id }.toSet()
        // Persisted tombstones also cover papers deleted offline before the app restarted.
        val pendingDeletedIds = history.pendingDeletedEntryIdsForCloudSync()
        ((lastUploadedPaperIds - paperIds) + pendingDeletedIds).forEach { id ->
            userRef.collection("papers").document(id).delete()
                .addOnSuccessListener { history.acknowledgeCloudDeletedEntry(id) }
                .addOnFailureListener { onError(it.message ?: "Could not delete cloud paper") }
        }
        (lastUploadedFolderIds - folderIds).forEach { id ->
            userRef.collection("folders").document(id).delete()
                .addOnFailureListener { onError(it.message ?: "Could not delete cloud folder") }
        }
        localFolders.forEach { f ->
            userRef.collection("folders").document(f.id)
                .set(mapOf("id" to f.id, "name" to f.name, "createdAt" to java.time.Instant.ofEpochMilli(f.createdAt).toString()), com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { onError(it.message ?: "Could not sync folder") }
        }
        localEntries.forEach { e ->
            val data = mapOf(
                "id" to e.id, "title" to e.title, "subtitle" to e.subtitle,
                "folderId" to e.folderId, "language" to e.language,
                "questionCount" to e.questionCount,
                "createdAt" to java.time.Instant.ofEpochMilli(e.createdAt).toString(),
                "updatedAt" to java.time.Instant.ofEpochMilli(e.updatedAt).toString(),
                "hasAnswerKey" to e.hasAnswerKey,
                "state" to jsonObjectToMap(JSONObject(encodeData(e.state))),
            )
            userRef.collection("papers").document(e.id)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { onError(it.message ?: "Could not sync paper") }
        }
        lastUploadedPaperIds = paperIds
        lastUploadedFolderIds = folderIds
    }


    fun uploadUserSettings(userSettings: Map<String, Any?>, onError: (String) -> Unit = {}) {
        val user = auth.currentUser ?: return
        val patch = userSettings.filterValues { it != null }.toMutableMap()
        patch["email"] = user.email ?: ""
        patch["displayName"] = user.displayName ?: ""
        patch["lastSyncAt"] = System.currentTimeMillis()
        db.collection("users").document(user.uid)
            .set(patch, com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { onError(it.message ?: "Could not sync account settings") }
    }

    fun stopSync() {
        stopListenersOnly()
        lastUploadedPaperIds = emptySet()
        lastUploadedFolderIds = emptySet()
        knownRemotePaperIds = null
        knownRemoteFolderIds = null
    }
    private fun stopListenersOnly() {
        paperListener?.remove(); paperListener = null
        folderListener?.remove(); folderListener = null
    }
}
