package com.intelliq.rifatplex.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Size as ASize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.intelliq.rifatplex.data.AlignResult
import com.intelliq.rifatplex.data.GrayImage
import com.intelliq.rifatplex.data.LiveHint
import com.intelliq.rifatplex.data.OmrAlign
import com.intelliq.rifatplex.data.OmrImageLoader
import com.intelliq.rifatplex.data.OmrLiveCheck
import com.intelliq.rifatplex.data.OmrShotQuality
import com.intelliq.rifatplex.data.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val GREEN = Color(0xFF22C55E)
private val AMBER = Color(0xFFF59E0B)
private val RED = Color(0xFFEF4444)

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

/** The preview frame as the RED channel (the sheet is printed in magenta: bubbles vanish in red, like in the final photo). */
private fun ImageProxy.toRedGrey(): GrayImage {
    val plane = planes[0]
    val buf = plane.buffer
    val rs = plane.rowStride
    val ps = plane.pixelStride
    val w = width
    val h = height
    val out = ByteArray(w * h)
    for (y in 0 until h) {
        val base = y * rs
        val o = y * w
        for (x in 0 until w) out[o + x] = buf.get(base + x * ps)
    }
    return GrayImage(w, h, out)
}

/** Strict check of the final photo (same aligner and limits as the real evaluation). Returns null = fine, else a message key. */
private fun strictCheck(ctx: Context, f: File): String? = try {
    val img = OmrImageLoader.load(ctx.contentResolver, Uri.fromFile(f))
    if (img == null) "live.fail.read" else when (val r = OmrAlign.align(img)) {
        is AlignResult.Ok ->
            if (OmrLiveCheck.paperLevel(img) < OmrLiveCheck.MIN_PAPER_LEVEL) OmrLiveCheck.key(LiveHint.TOO_DARK)
            else OmrShotQuality.issue(img, r)?.let { OmrShotQuality.key(it) }
        is AlignResult.Failed -> OmrLiveCheck.key(OmrLiveCheck.hintFor(r.reason))
    }
} catch (e: Throwable) {
    "live.fail.read"
}

/**
 * Full-screen camera with a live indicator. Red = sheet not usable yet (the message says what to fix), amber = nearly,
 * green = good. After each shot the photo is checked again with the strict limits, so a bad photo is caught at once and
 * can be retaken. Accepted photos are handed to [onAccepted] one by one (the caller deletes the file when done).
 */
@Composable
fun OmrLiveCamera(onAccepted: (File) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasCamera(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) CameraBody(onAccepted, onClose) else {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(tr("live.perm"), color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center, fontFamily = uiFont())
                    IQButton(tr("live.close"), BtnKind.Primary) { onClose() }
                }
            }
        }
    }
}

@Composable
private fun CameraBody(onAccepted: (File) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf(LiveHint.NO_SHEET) }
    var ready by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<Pair<File, String>?>(null) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var failed by remember { mutableStateOf(false) }

    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        val last = AtomicLong(0)
        val streak = AtomicInteger(0)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(ASize(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { frame ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - last.get() >= 350) { // about 3 checks a second at most
                            last.set(now)
                            val r = OmrLiveCheck.check(frame.toRedGrey())
                            hint = r.hint
                            // green only after 3 good frames in a row: a lucky blurry frame must not say "good"
                            ready = if (r.hint == LiveHint.OK) streak.incrementAndGet() >= 3 else { streak.set(0); false }
                        }
                    } catch (e: Throwable) {
                        // one bad frame is ignored
                    } finally {
                        frame.close()
                    }
                }
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
            } catch (e: Exception) {
                failed = true
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { provider?.unbindAll() }
    }

    fun shoot() {
        if (busy || review != null) return
        busy = true
        val f = try {
            File.createTempFile("scan_", ".jpg", File(ctx.cacheDir, "omr").apply { mkdirs() })
        } catch (e: Exception) {
            busy = false
            return
        }
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val key = withContext(Dispatchers.Default) { strictCheck(ctx, f) }
                        if (key == null) {
                            count += 1
                            onAccepted(f)
                        } else {
                            review = f to key
                        }
                        busy = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    f.delete()
                    busy = false
                    failed = true
                }
            },
        )
    }

    val color = if (ready) GREEN else if (hint == LiveHint.OK) AMBER else RED
    val message = if (ready) tr("live.ok") else if (hint == LiveHint.OK) tr("live.hold") else tr(OmrLiveCheck.key(hint))

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // A4 guide frame, coloured by the state
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height * 0.86f
            val w = minOf(h * 210f / 297f, size.width * 0.94f)
            val hh = w * 297f / 210f
            drawRoundRect(
                color = color, topLeft = Offset((size.width - w) / 2f, (size.height - hh) / 2f), size = Size(w, hh),
                cornerRadius = CornerRadius(16f, 16f), style = Stroke(width = 6f),
            )
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                Text(if (failed) tr("live.err") else message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            }
            if (count > 0) {
                Text(
                    tr("live.count").replace("{n}", formatNumber(count, LocalLang.current == Lang.BN)),
                    color = Color.White, fontSize = 13.sp, fontFamily = uiFont(),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                tr("live.done"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).clickable { onClose() }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Box(
                Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(6.dp).clip(CircleShape)
                    .background(if (busy) Color.Gray else color).clickable(enabled = !busy && !failed) { shoot() },
            )
            Text(
                tr("live.torch"), color = if (torch) Color.Yellow else Color.White, fontSize = 16.sp, fontFamily = uiFont(),
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x99000000))
                    .clickable { torch = !torch; camera?.cameraControl?.enableTorch(torch) }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (busy) {
            Text(
                tr("live.checking"), color = Color.White, fontSize = 15.sp, fontFamily = uiFont(),
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(16.dp),
            )
        }

        val rv = review
        if (rv != null) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF2000000)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tr("live.failTitle"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                Text(tr(rv.second), color = Color(0xFFFCA5A5), fontSize = 15.sp, fontFamily = uiFont())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IQButton(tr("live.retake"), BtnKind.Primary, Modifier.weight(1f)) {
                        rv.first.delete()
                        review = null
                    }
                    IQButton(tr("live.useAnyway"), BtnKind.Secondary, Modifier.weight(1f)) {
                        count += 1
                        onAccepted(rv.first)
                        review = null
                    }
                }
            }
        }
    }
}
