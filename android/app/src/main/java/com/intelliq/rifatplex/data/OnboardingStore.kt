package com.intelliq.rifatplex.data

import android.content.Context

/**
 * Remembers that the first-launch intro (branding, purpose, features) was seen or skipped, so it shows once.
 *
 * A phone that already holds a saved paper (an update from a version without the intro) never sees it automatically:
 * [skipForExistingUser] must run before AppStore is created (MainActivity does this first thing in onCreate).
 * The intro can still be opened again from the developer dialog (tap the logo).
 */
class OnboardingStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("intelliq_onboarding", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var instance: OnboardingStore? = null
        fun get(context: Context): OnboardingStore =
            instance ?: synchronized(this) { instance ?: OnboardingStore(context.applicationContext).also { instance = it } }
    }

    val done: Boolean get() = prefs.getBoolean("done", false)

    fun markDone() {
        prefs.edit().putBoolean("done", true).apply()
    }

    /** First start of a version that has the intro, on a phone that was already in use: no intro. */
    fun skipForExistingUser(context: Context) {
        if (prefs.contains("done")) return
        val hadData = context.applicationContext.getSharedPreferences("intelliq_state", Context.MODE_PRIVATE).contains("data")
        if (hadData) markDone()
    }
}
