package com.intelliq.rifatplex.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intelliq.rifatplex.data.CloudSync

/** Exports and OMR checks need a signed-in account (no plans, no limits). When Firebase is not configured nothing is blocked. */
object SignInGate {
    var signInRequested by mutableStateOf(false)
    /** Translation key of a message for IntelliQApp to toast once. */
    var toastKey by mutableStateOf<String?>(null)

    /** True = go ahead. Otherwise the sign-in screen has been requested. */
    fun check(cloud: CloudSync): Boolean {
        if (!cloud.configured || cloud.user != null) return true
        toastKey = "gate.needSignIn"
        signInRequested = true
        return false
    }
}
