package com.intelliq.rifatplex.data

import android.app.Activity
import android.content.Context
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class CloudUser(val uid: String, val name: String, val email: String, val photoUrl: String?)

enum class SignInResult { OK, CANCELLED, NOT_CONFIGURED, ERROR }

/**
 * Google sign-in only (Credential Manager -> Firebase Auth). There is no database, no plans, no admin.
 *
 * LOCAL-ONLY DATA: saved papers, folders, answer-key snapshots and the Gemini key stay on this phone
 * (HistoryStore / AnswerKeyStore / AppStore). The user may send a backup to their own Gmail (see Backup.kt).
 *
 * Signing out or switching account deletes nothing on the phone (it is the only copy). Only the previous owner's Gemini key
 * is removed when a DIFFERENT account signs in ([onOwnerChanged]).
 */
class CloudSync private constructor(private val app: Context) {
    companion object {
        @Volatile private var instance: CloudSync? = null
        fun get(context: Context): CloudSync =
            instance ?: synchronized(this) { instance ?: CloudSync(context.applicationContext).also { instance = it } }
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

    private val prefs = app.getSharedPreferences("intelliq_cloud", Context.MODE_PRIVATE)
    private var onOwnerChanged: () -> Unit = {}
    private var started = false

    private fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** Call once at app start. [ownerChanged] wipes this device's Gemini key when a different account signs in. */
    fun start(ownerChanged: () -> Unit) {
        if (started || !configured) return
        started = true
        this.onOwnerChanged = ownerChanged
        auth().addAuthStateListener { fa ->
            val u = fa.currentUser
            if (u == null) {
                user = null // nothing is deleted from the phone; "lastUid" stays so a different account is noticed next time
            } else {
                val last = prefs.getString("lastUid", null)
                if (last != null && last != u.uid) onOwnerChanged()
                prefs.edit().putString("lastUid", u.uid).apply()
                user = toUser(u)
            }
        }
    }

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

    private fun toUser(u: FirebaseUser) = CloudUser(
        uid = u.uid,
        name = u.displayName.orEmpty(),
        email = u.email.orEmpty(),
        photoUrl = u.photoUrl?.toString(),
    )
}
