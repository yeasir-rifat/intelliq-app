package com.intelliq.rifatplex.ui

import androidx.compose.runtime.mutableStateListOf

/**
 * Switches the Input <-> Preview swipe off while a text selection is active in an editor
 * (website: `hasSel()` in iq-v5-swipe), so dragging selection handles never changes the page.
 * Every editor with a non-empty selection holds a lock (its own state object as the token).
 */
object SwipeGate {
    private val locks = mutableStateListOf<Any>()
    val selecting: Boolean get() = locks.isNotEmpty()
    fun lock(token: Any) { if (token !in locks) locks.add(token) }
    fun unlock(token: Any) { locks.remove(token) }
}
