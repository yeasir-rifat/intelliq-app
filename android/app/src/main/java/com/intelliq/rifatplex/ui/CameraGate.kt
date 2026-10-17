package com.intelliq.rifatplex.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private fun hasCamera(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Once the manifest declares CAMERA, the system camera app (ACTION_IMAGE_CAPTURE) throws SecurityException unless the permission
 * is granted. Every TakePicture launch therefore goes through this gate: ask first, launch afterwards.
 */
@Composable
fun rememberCameraGate(): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pending
        pending = null
        if (ok && p != null) runSafely(p)
    }
    return { action ->
        if (hasCamera(ctx)) runSafely(action) else {
            pending = action
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}

private fun runSafely(a: () -> Unit) {
    try {
        a()
    } catch (e: ActivityNotFoundException) {
    } catch (e: SecurityException) {
    }
}
