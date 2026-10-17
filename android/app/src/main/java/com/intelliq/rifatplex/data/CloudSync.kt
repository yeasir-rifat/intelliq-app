package com.intelliq.rifatplex.data

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class CloudUser(val uid: String, val name: String, val email: String, val photoUrl: String?)

/** The three AI settings the website also keeps in `users/{uid}` (geminiApiKey, geminiModel, aiInsertMode). */
data class AiSync(val apiKey: String, val model: String, val mode: String)

enum class SignInResult { OK, CANCELLED, NOT_CONFIGURED, ERROR }

/**
 * Step 7: Google sign-in (Credential Manager -> Firebase Auth) and Firestore sync.
 *
 * Firestore layout (same project and same `users/{uid}` as the website):
 *  - `users/{uid}`                 geminiApiKey, geminiModel, aiInsertMode (shared with the website, same field names)
 *  - `users/{uid}/folders/{id}`    {id, name, createdAt (ISO text), updatedAt} (shared with the website, same shape)
 *  - `users/{uid}/appPapers/{id}`  the app's own paper format (`state` = the app's JSON). The website keeps its papers in
 *                                  `papers`, so the two never read each other's papers (they use different markup).
 *  - `users/{uid}/appKeys/{examId.set}`  answer snapshots of step 4.3 (only printed numbers and right options, never the
 *                                  question text), so a sheet can be checked on another phone too. App only.
 *
 * Rules: local changes are pushed one by one (save, delete, folder); the cloud snapshot is the truth for the local list
 * (like the website). Firestore's own offline cache queues writes while offline. A different account signing in, or an
 * explicit sign-out, clears the local papers (like the website), they stay in the cloud.
 */
class CloudSync private constructor(private val app: Context) : HistorySyncListener {
    companion object {
        @Volatile private var instance: CloudSync? = null
        fun get(context: Context): CloudSync =
            instance ?: synchronized(this) { instance ?: CloudSync(context.applicationContext).also { instance = it } }

        private val KNOWN_MODELS = GeminiClient.MODELS.toSet()
        private const val MAX_STATE_BYTES = 900_000 // Firestore documents are limited to 1 MiB
    }

    /** False when google-services.json was not part of the build. */
    val configured: Boolean = try {
        FirebaseApp.getApps(app).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    var user by mutableStateOf<CloudUser?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** Papers that were too large for one Firestore document in the last upload (they stay on this device). */
    var tooLarge by mutableStateOf(0)
        private set

    private val prefs = app.getSharedPreferences("intelliq_cloud", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var history: HistoryStore? = null
    private var keys: AnswerKeyStore? = null
    private var readAi: () -> AiSync = { AiSync("", "", "") }
    private var applyAi: (AiSync) -> Unit = {}
    private var onOwnerChanged: () -> Unit = {}
    private var started = false

    private var uid: String? = null
    private val listeners = ArrayList<ListenerRegistration>()
    private val remoteEntries = HashMap<String, HistoryEntry>()
    private val remoteFolders = HashMap<String, Folder>()
    private val remoteKeys = HashMap<String, AnswerKeySnapshot>()
    private var userDocReady = false
    private var lastAi: AiSync? = null
    private val pendingEntries = LinkedHashMap<String, HistoryEntry>()
    private val flushRunnable = Runnable { flushPending() }

    private fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()
    private fun userRef(id: String) = db().collection("users").document(id)

    /** Call once at app start. [readAi] / [applyAi] connect the Gemini settings, [ownerChanged] wipes this device's API key. */
    fun start(history: HistoryStore, keys: AnswerKeyStore, readAi: () -> AiSync, applyAi: (AiSync) -> Unit, ownerChanged: () -> Unit) {
        if (started || !configured) return
        started = true
        this.history = history
        this.keys = keys
        keys.sync = { s -> pushKey(s) }
        this.readAi = readAi
        this.applyAi = applyAi
        this.onOwnerChanged = ownerChanged
        history.sync = this
        auth().addAuthStateListener { fa ->
            val u = fa.currentUser
            if (u == null) onSignedOut() else onSignedIn(u)
        }
    }

    // ---------------------------------------------------------------- sign in / out

    suspend fun signIn(activity: Activity): SignInResult {
        if (!configured) return SignInResult.NOT_CONFIGURED
        val resId = app.resources.getIdentifier("default_web_client_id", "string", app.packageName)
        if (resId == 0) return SignInResult.NOT_CONFIGURED
        busy = true
        try {
            val option = GetSignInWithGoogleOption.Builder(app.getString(resId)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(cred.data)
                val ok = suspendCoroutine<Boolean> { cont ->
                    auth().signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                        .addOnCompleteListener { t -> cont.resume(t.isSuccessful) }
                }
                return if (ok) SignInResult.OK else SignInResult.ERROR
            }
            return SignInResult.ERROR
        } catch (e: GetCredentialCancellationException) {
            return SignInResult.CANCELLED
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            return SignInResult.ERROR
        } finally {
            busy = false
        }
    }

    suspend fun signOut(context: Context) {
        if (!configured) return
        auth().signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // not signed in through Credential Manager: nothing to clear
        }
    }

    private fun onSignedIn(u: FirebaseUser) {
        if (uid == u.uid) {
            user = toUser(u) // same account, e.g. the profile photo changed
            return
        }
        stopWatchers()
        uid = u.uid
        user = toUser(u)
        val last = prefs.getString("lastUid", null)
        if (last != null && last != u.uid) {
            history?.clearAll()
            keys?.clearAll()
            onOwnerChanged()
        }
        prefs.edit().putString("lastUid", u.uid).apply()
        val id = u.uid
        io.execute {
            history?.awaitLoaded()
            migrateIfNeeded(id) { if (uid == id) startWatchers(id) }
        }
    }

    private fun onSignedOut() {
        val hadAccount = prefs.getString("lastUid", null) != null
        stopWatchers()
        uid = null
        user = null
        main.removeCallbacks(flushRunnable)
        pendingEntries.clear()
        if (hadAccount) {
            // same as the website: another person may use this device next, so nothing of this account stays on it
            history?.clearAll()
            keys?.clearAll()
            onOwnerChanged()
            prefs.edit().remove("lastUid").apply()
        }
    }

    private fun toUser(u: FirebaseUser) = CloudUser(
        uid = u.uid,
        name = u.displayName.orEmpty(),
        email = u.email.orEmpty(),
        photoUrl = u.photoUrl?.toString(),
    )

    // ---------------------------------------------------------------- first upload

    private fun migrateIfNeeded(id: String, done: () -> Unit) {
        val flag = "migrated.$id"
        if (prefs.getBoolean(flag, false)) {
            main.post { done() }
            return
        }
        val h = history ?: return
        val ops = ArrayList<(WriteBatch) -> Unit>()
        var big = 0
        h.folders.forEach { f -> ops.add { b -> b.set(userRef(id).collection("folders").document(f.id), folderMap(f), SetOptions.merge()) } }
        h.entries.forEach { e ->
            val m = paperMap(e)
            if (m == null) big++ else ops.add { b -> b.set(userRef(id).collection("appPapers").document(e.id), m, SetOptions.merge()) }
        }
        val ai = readAi()
        val patch = HashMap<String, Any>()
        if (ai.apiKey.isNotEmpty()) patch["geminiApiKey"] = ai.apiKey
        if (ai.apiKey.isNotEmpty() && ai.model.isNotEmpty()) patch["geminiModel"] = ai.model
        if (ai.apiKey.isNotEmpty() && ai.mode.isNotEmpty()) patch["aiInsertMode"] = ai.mode
        if (patch.isNotEmpty()) {
            patch["updatedAt"] = FieldValue.serverTimestamp()
            ops.add { b -> b.set(userRef(id), patch, SetOptions.merge()) }
        }
        main.post { tooLarge = big }
        val chunks = ops.chunked(400)
        if (chunks.isEmpty()) {
            prefs.edit().putBoolean(flag, true).apply()
            main.post { done() }
            return
        }
        var finished = 0
        var failed = false
        chunks.forEach { chunk ->
            val b = db().batch()
            chunk.forEach { it(b) }
            b.commit().addOnCompleteListener { t ->
                finished++
                if (!t.isSuccessful) failed = true
                if (finished == chunks.size) {
                    if (!failed) prefs.edit().putBoolean(flag, true).apply()
                    done() // Firestore calls back on the main thread
                }
            }
        }
    }

    // ---------------------------------------------------------------- cloud -> device

    private fun stopWatchers() {
        listeners.forEach { try { it.remove() } catch (e: Exception) { } }
        listeners.clear()
        io.execute { // the cloud snapshots are decoded on this same thread
            remoteEntries.clear()
            remoteFolders.clear()
            remoteKeys.clear()
        }
        userDocReady = false
        lastAi = null
    }

    private fun startWatchers(id: String) {
        val h = history ?: return
        listeners += userRef(id).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                if (!snap.metadata.isFromCache) userDocReady = true // really no settings in the cloud yet
                return@addSnapshotListener
            }
            val key = snap.getString("geminiApiKey").orEmpty()
            val model = snap.getString("geminiModel").orEmpty()
            val mode = snap.getString("aiInsertMode").orEmpty()
            val cur = readAi()
            val next = AiSync(
                apiKey = key.ifEmpty { cur.apiKey }, // an empty cloud key never erases the key typed on this device
                model = if (model in KNOWN_MODELS) model else cur.model,
                mode = if (mode == "append" || mode == "replace") mode else cur.mode,
            )
            if (next != cur) applyAi(next)
            lastAi = next
            userDocReady = true
        }
        listeners += userRef(id).collection("folders").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
            io.execute {
                snap.documentChanges.forEach { ch ->
                    if (ch.type == DocumentChange.Type.REMOVED) remoteFolders.remove(ch.document.id)
                    else decodeFolder(ch.document)?.let { remoteFolders[it.id] = it }
                }
                h.applyRemote(null, remoteFolders.values.toList())
            }
        }
        val k = keys
        if (k != null) {
            listeners += userRef(id).collection("appKeys").addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
                io.execute {
                    snap.documentChanges.forEach { ch ->
                        if (ch.type == DocumentChange.Type.REMOVED) remoteKeys.remove(ch.document.id)
                        else decodeKey(ch.document)?.let { remoteKeys[ch.document.id] = it }
                    }
                    k.putRemote(remoteKeys.values.toList())
                    // snapshots made on this device that the cloud does not have yet (or has an older one of) go up now
                    k.all().forEach { s ->
                        val r = remoteKeys[s.storeKey]
                        if (r == null || r.createdAt < s.createdAt) pushKeyTo(id, s)
                    }
                }
            }
        }
        listeners += userRef(id).collection("appPapers").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
            io.execute {
                snap.documentChanges.forEach { ch ->
                    if (ch.type == DocumentChange.Type.REMOVED) remoteEntries.remove(ch.document.id)
                    else decodeEntry(ch.document)?.let { remoteEntries[it.id] = it }
                }
                h.applyRemote(remoteEntries.values.toList(), null)
            }
        }
    }

    private fun decodeEntry(d: DocumentSnapshot): HistoryEntry? {
        return try {
            val state = d.getString("state") ?: return null
            HistoryEntry(
                id = d.id,
                title = d.getString("title").orEmpty(),
                subtitle = d.getString("subtitle").orEmpty(),
                folderId = d.getString("folderId")?.takeIf { it.isNotEmpty() },
                language = d.getString("language") ?: "en",
                questionCount = (d.getLong("questionCount") ?: 0L).toInt(),
                createdAt = d.getLong("createdAt") ?: 0L,
                updatedAt = d.getLong("updatedAt") ?: 0L,
                hasAnswerKey = d.getBoolean("hasAnswerKey") ?: false,
                state = decodeData(state, ExamId.derive(d.id)),
            )
        } catch (e: Exception) {
            null // one broken document must not stop the rest
        }
    }

    private fun decodeKey(d: DocumentSnapshot): AnswerKeySnapshot? {
        return try {
            val state = d.getString("state") ?: return null
            AnswerKey.decode(state)
        } catch (e: Exception) {
            null // one broken document must not stop the rest
        }
    }

    private fun decodeFolder(d: DocumentSnapshot): Folder? {
        return try {
            val created = when (val v = d.get("createdAt")) {
                is Number -> v.toLong()
                is String -> try { Instant.parse(v).toEpochMilli() } catch (e: Exception) { 0L }
                else -> 0L
            }
            Folder(d.id, d.getString("name").orEmpty(), created)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- device -> cloud

    private fun folderMap(f: Folder): Map<String, Any> = mapOf(
        "id" to f.id,
        "name" to f.name,
        "createdAt" to Instant.ofEpochMilli(f.createdAt).toString(), // the website keeps an ISO text here
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun paperMap(e: HistoryEntry): Map<String, Any?>? {
        val state = encodeData(e.state)
        if (state.toByteArray(Charsets.UTF_8).size > MAX_STATE_BYTES) return null
        return mapOf(
            "id" to e.id,
            "title" to e.title,
            "subtitle" to e.subtitle,
            "folderId" to e.folderId,
            "language" to e.language,
            "questionCount" to e.questionCount,
            "createdAt" to e.createdAt,
            "updatedAt" to e.updatedAt,
            "hasAnswerKey" to e.hasAnswerKey,
            "format" to "app1",
            "state" to state,
        )
    }

    private fun keyMap(s: AnswerKeySnapshot): Map<String, Any> = mapOf(
        "id" to s.storeKey,
        "examId" to s.examId,
        "setIndex" to (s.setIndex ?: -1).toLong(),
        "geomVersion" to s.geomVersion.toLong(),
        "createdAt" to s.createdAt,
        "format" to "key1",
        "state" to AnswerKey.encode(s),
    )

    private fun pushKeyTo(id: String, s: AnswerKeySnapshot) {
        userRef(id).collection("appKeys").document(s.storeKey).set(keyMap(s), SetOptions.merge())
    }

    /** A new answer snapshot was made on this device (the store calls this). Offline writes are queued by Firestore. */
    private fun pushKey(s: AnswerKeySnapshot) {
        val u = uid ?: return
        io.execute { pushKeyTo(u, s) }
    }

    override fun entryChanged(e: HistoryEntry) {
        if (uid == null) return
        main.post {
            pendingEntries[e.id] = e
            main.removeCallbacks(flushRunnable)
            main.postDelayed(flushRunnable, 1200) // a burst of saves becomes one upload
        }
    }

    private fun flushPending() {
        val id = uid ?: return
        val list = pendingEntries.values.toList()
        pendingEntries.clear()
        io.execute {
            var big = 0
            list.forEach { e ->
                val m = paperMap(e)
                if (m == null) big++ else userRef(id).collection("appPapers").document(e.id).set(m, SetOptions.merge())
            }
            if (big > 0) main.post { tooLarge = big }
        }
    }

    override fun entryDeleted(id: String) {
        val u = uid ?: return
        main.post { pendingEntries.remove(id) }
        userRef(u).collection("appPapers").document(id).delete()
    }

    override fun folderChanged(f: Folder) {
        val u = uid ?: return
        userRef(u).collection("folders").document(f.id).set(folderMap(f), SetOptions.merge())
    }

    /** Call (debounced) when the Gemini key / model / insert mode changes on this device. */
    fun pushAi(s: AiSync) {
        val u = uid ?: return
        if (!userDocReady || s == lastAi) return
        lastAi = s
        userRef(u).set(
            mapOf(
                "geminiApiKey" to s.apiKey,
                "geminiModel" to s.model,
                "aiInsertMode" to s.mode,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
    }
}
