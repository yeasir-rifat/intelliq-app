package com.intelliq.rifatplex.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.intelliq.rifatplex.data.FigData
import com.intelliq.rifatplex.data.ImageTools
import com.intelliq.rifatplex.data.MAX_FIG_PICS
import com.intelliq.rifatplex.data.Pic
import com.intelliq.rifatplex.data.PicCache
import com.intelliq.rifatplex.data.figWidthPct
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/* ───────────── a figure on the paper (preview + PDF) and in the dialog ───────────── */

/**
 * Pictures of one figure block, side by side. Sizes and gaps follow the website (`.iq-fig` / `.iq-row`):
 * one picture 35 / 60 / 100 % of the column, several pictures share 60 / 85 / 100 %.
 */
@Composable
fun FigureBlock(
    fig: FigData,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onPicClick: ((Int) -> Unit)? = null,
) {
    val n = fig.pics.size
    if (n == 0) return
    val images = remember(fig.pics) { fig.pics.map { p -> p to PicCache.get(p.url) } }
    val pct = figWidthPct(fig.size, n) / 100f
    val align = fig.align
    Layout(
        content = {
            images.forEachIndexed { i, (p, bmp) ->
                // In the editor ([onPicClick] set) every picture is tappable, sits on white (a "cleaned" diagram can be
                // transparent) and the chosen one gets a highlighted frame, like the website's click popover target.
                val base = Modifier.aspectRatio(p.ratio)
                val m = if (onPicClick == null) base else {
                    val on = selected == i
                    base
                        .background(Color.White)
                        .border(if (on) 2.dp else 1.dp, if (on) IQ.c.primary else IQ.c.glassBorder)
                        .clickable { onPicClick(i) }
                }
                if (bmp != null) Image(bmp, null, m, contentScale = ContentScale.Fit)
                else Box(m)
            }
        },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) { measurables, constraints ->
        val total = if (constraints.hasBoundedWidth) constraints.maxWidth else 600
        val gap = if (n > 1) (total * 0.02f).toInt() else 0
        val w = (total * pct).toInt().coerceAtLeast(1)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(w)) }
        val rowW = placeables.size * w + (placeables.size - 1).coerceAtLeast(0) * gap
        val h = placeables.maxOfOrNull { it.height } ?: 0
        var x = when (align) {
            "l" -> 0
            "r" -> total - rowW
            else -> (total - rowW) / 2
        }.coerceAtLeast(0)
        layout(total, h) {
            placeables.forEach {
                it.placeRelative(x, 0)
                x += w + gap
            }
        }
    }
}

/* ───────────── add / edit a figure ───────────── */

@Composable
private fun ActionChip(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .height(32.dp)
            .clip(IQD.ctl)
            .background(if (danger) c.dangerSoft else c.primarySoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (danger) c.danger else c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(), maxLines = 1)
    }
}

/**
 * Insert pictures (gallery / camera, up to 3, side by side) or edit an existing figure: size, alignment, crop,
 * clean, add / remove pictures. [onDelete] is only given when editing.
 */
@Composable
fun FigureDialog(
    initial: FigData?,
    onDismiss: () -> Unit,
    onApply: (FigData) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = IQ.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val family = uiFont()
    var pics by remember { mutableStateOf(initial?.pics ?: emptyList()) }
    var size by remember { mutableStateOf(initial?.size ?: "M") }
    var align by remember { mutableStateOf(initial?.align ?: "c") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var cropIdx by remember { mutableStateOf<Int?>(null) }
    var cleanIdx by remember { mutableStateOf<Int?>(null) }
    val room = MAX_FIG_PICS - pics.size

    fun addFrom(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy = true
        failed = false
        scope.launch {
            val got = withContext(Dispatchers.IO) { uris.take(room.coerceAtLeast(0)).mapNotNull { ImageTools.fromUri(ctx.contentResolver, it) } }
            if (got.isEmpty()) failed = true
            pics = (pics + got).take(MAX_FIG_PICS)
            busy = false
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> addFrom(uris) }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camGate = rememberCameraGate()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        camUri = null
        if (ok && u != null) addFrom(listOf(u))
    }

    fun pickGallery() {
        try {
            gallery.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            failed = true
        }
    }

    fun capture() {
        try {
            val dir = File(ctx.cacheDir, "ai").apply { mkdirs() }
            val f = File.createTempFile("fig_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camUri = uri
            camGate { camera.launch(uri) }
        } catch (e: Exception) {
            failed = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(IQD.dialog)
                .background(c.panel)
                .border(1.dp, c.border, IQD.dialog)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr(if (initial != null) "fig.edit" else "fig.title"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = family)

            // preview of the pictures, each with crop / clean / remove
            if (pics.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 90.dp).clip(IQD.ctl).background(c.field).border(1.dp, c.glassBorder, IQD.ctl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tr(if (busy) "fig.busy" else if (failed) "fig.readErr" else "fig.none"),
                        color = if (failed) c.danger else c.textFaint, fontSize = 14.sp, fontFamily = family, textAlign = TextAlign.Center,
                    )
                }
            } else {
                pics.forEachIndexed { i, p ->
                    Column(
                        Modifier.fillMaxWidth().clip(IQD.ctl).background(c.field).border(1.dp, c.glassBorder, IQD.ctl).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val bmp = remember(p.url) { PicCache.get(p.url) }
                        Box(Modifier.fillMaxWidth().heightIn(max = 180.dp).background(Color.White), contentAlignment = Alignment.Center) {
                            if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionChip(tr("fig.crop")) { cropIdx = i }
                            ActionChip(tr("fig.clean")) { cleanIdx = i }
                            ActionChip(tr("fig.remove"), danger = true) { pics = pics.filterIndexed { k, _ -> k != i } }
                        }
                    }
                }
                if (busy) Text(tr("fig.busy"), color = c.textFaint, fontSize = 13.sp, fontFamily = family)
                if (failed) Text(tr("fig.readErr"), color = c.danger, fontSize = 13.sp, fontFamily = family)
            }

            if (room > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        IQButton(tr("fig.pick") + " (" + room + ")", BtnKind.Secondary, Modifier.fillMaxWidth()) { pickGallery() }
                    }
                    Box(Modifier.weight(1f)) {
                        IQButton(tr("fig.camera"), BtnKind.Secondary, Modifier.fillMaxWidth()) { capture() }
                    }
                }
            }

            Text(tr("fig.size"), color = c.textSecondary, fontSize = 13.sp, fontFamily = family)
            Segmented(listOf("S" to tr("fig.small"), "M" to tr("fig.medium"), "L" to tr("fig.large")), size) { size = it }
            Text(tr("fig.align"), color = c.textSecondary, fontSize = 13.sp, fontFamily = family)
            Segmented(listOf("l" to tr("fig.left"), "c" to tr("fig.center"), "r" to tr("fig.right")), align) { align = it }
            if (tr("fig.note").isNotBlank()) {
                Text(tr("fig.note"), color = c.textFaint, fontSize = 12.sp, fontFamily = family)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IQButton(tr("common.cancel"), BtnKind.Ghost, Modifier.weight(1f)) { onDismiss() }
                IQButton(tr(if (initial != null) "fig.update" else "fig.insert"), BtnKind.Primary, Modifier.weight(1f)) {
                    if (pics.isNotEmpty() && !busy) onApply(FigData(pics, size, align))
                }
            }
            if (onDelete != null) {
                IQButton(tr("fig.delete"), BtnKind.Ghost, Modifier.fillMaxWidth()) { onDelete() }
            }
        }
    }

    cropIdx?.let { idx ->
        val p = pics.getOrNull(idx)
        if (p == null) cropIdx = null
        else CropDialog(p, onDismiss = { cropIdx = null }, onDone = { np ->
            pics = pics.mapIndexed { k, old -> if (k == idx) np else old }
            cropIdx = null
        })
    }
    cleanIdx?.let { idx ->
        val p = pics.getOrNull(idx)
        if (p == null) cleanIdx = null
        else CleanDialog(p, onDismiss = { cleanIdx = null }, onDone = { np ->
            pics = pics.mapIndexed { k, old -> if (k == idx) np else old }
            cleanIdx = null
        })
    }
}

/* ───────────── crop ───────────── */

private class CropBox(val fx: Float, val fy: Float, val fw: Float, val fh: Float)

private val CROP_START = CropBox(0.06f, 0.06f, 0.88f, 0.88f)

/** Drag the box or its handles (corners / edges) to choose the part to keep; same behaviour as the website. */
@Composable
fun CropDialog(pic: Pic, onDismiss: () -> Unit, onDone: (Pic) -> Unit, logo: Boolean = false) {
    val c = IQ.c
    val family = uiFont()
    val scope = rememberCoroutineScope()
    val img = remember(pic.url) { PicCache.get(pic.url) }
    var box by remember { mutableStateOf(CROP_START) }
    var busy by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(IQD.dialog)
                .background(c.panel)
                .border(1.dp, c.border, IQD.dialog)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("fig.cropTitle"), color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = family)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val maxW = maxWidth
                val maxH = 380.dp
                val ratio = pic.ratio
                val dw = if (maxW / ratio <= maxH) maxW else maxH * ratio
                val dh = dw / ratio
                val dwPx = with(density) { dw.toPx() }
                val dhPx = with(density) { dh.toPx() }
                val slop = with(density) { 28.dp.toPx() }
                val handleR = with(density) { 8.dp.toPx() }
                val stroke = with(density) { 2.dp.toPx() }
                val primary = c.primary
                var mode by remember { mutableStateOf("") }
                Box(
                    Modifier
                        .size(dw, dh)
                        .background(Color.White)
                        .pointerInput(dwPx, dhPx) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val b = box
                                    val l = b.fx * dwPx
                                    val t = b.fy * dhPx
                                    val r = (b.fx + b.fw) * dwPx
                                    val bt = (b.fy + b.fh) * dhPx
                                    val inX = pos.x >= l - slop && pos.x <= r + slop
                                    val inY = pos.y >= t - slop && pos.y <= bt + slop
                                    val sb = StringBuilder()
                                    if (inY && abs(pos.x - l) < slop) sb.append('w')
                                    if (inY && abs(pos.x - r) < slop) sb.append('e')
                                    if (inX && abs(pos.y - t) < slop) sb.append('n')
                                    if (inX && abs(pos.y - bt) < slop) sb.append('s')
                                    mode = when {
                                        sb.isNotEmpty() -> sb.toString()
                                        pos.x in l..r && pos.y in t..bt -> "move"
                                        else -> ""
                                    }
                                },
                                onDragEnd = { mode = "" },
                                onDragCancel = { mode = "" },
                            ) { change, drag ->
                                change.consume()
                                val m = mode
                                if (m.isNotEmpty()) {
                                    val b = box
                                    val dx = drag.x / dwPx
                                    val dy = drag.y / dhPx
                                    val mx = min(0.5f, 28f / dwPx)
                                    val my = min(0.5f, 28f / dhPx)
                                    var x0 = b.fx
                                    var y0 = b.fy
                                    var x1 = b.fx + b.fw
                                    var y1 = b.fy + b.fh
                                    if (m == "move") {
                                        x0 = (b.fx + dx).coerceIn(0f, 1f - b.fw)
                                        y0 = (b.fy + dy).coerceIn(0f, 1f - b.fh)
                                        x1 = x0 + b.fw
                                        y1 = y0 + b.fh
                                    } else {
                                        if ('w' in m) x0 = (x0 + dx).coerceIn(0f, x1 - mx)
                                        if ('e' in m) x1 = (x1 + dx).coerceIn(x0 + mx, 1f)
                                        if ('n' in m) y0 = (y0 + dy).coerceIn(0f, y1 - my)
                                        if ('s' in m) y1 = (y1 + dy).coerceIn(y0 + my, 1f)
                                    }
                                    box = CropBox(x0, y0, x1 - x0, y1 - y0)
                                }
                            }
                        },
                ) {
                    if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Canvas(Modifier.fillMaxSize()) {
                        val b = box
                        val l = b.fx * size.width
                        val t = b.fy * size.height
                        val w = b.fw * size.width
                        val h = b.fh * size.height
                        val dim = Color(0x8C0F1219)
                        drawRect(dim, Offset(0f, 0f), Size(size.width, t))
                        drawRect(dim, Offset(0f, t + h), Size(size.width, size.height - t - h))
                        drawRect(dim, Offset(0f, t), Size(l, h))
                        drawRect(dim, Offset(l + w, t), Size(size.width - l - w, h))
                        drawRect(primary, Offset(l, t), Size(w, h), style = Stroke(stroke))
                        val xs = listOf(l, l + w / 2, l + w)
                        val ys = listOf(t, t + h / 2, t + h)
                        for (xi in 0..2) for (yi in 0..2) {
                            if (xi == 1 && yi == 1) continue
                            drawCircle(Color.White, handleR, Offset(xs[xi], ys[yi]))
                            drawCircle(primary, handleR, Offset(xs[xi], ys[yi]), style = Stroke(stroke))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IQButton(tr("common.cancel"), BtnKind.Ghost, Modifier.weight(1f)) { onDismiss() }
                IQButton(tr("fig.reset"), BtnKind.Secondary, Modifier.weight(1f)) { box = CROP_START }
                IQButton(tr("fig.cropApply"), BtnKind.Primary, Modifier.weight(1f)) {
                    if (!busy) {
                        busy = true
                        val b = box
                        scope.launch {
                            val out = withContext(Dispatchers.Default) { if (logo) ImageTools.cropLogo(pic, b.fx, b.fy, b.fw, b.fh) else ImageTools.crop(pic, b.fx, b.fy, b.fw, b.fh) }
                            busy = false
                            if (out != null) onDone(out) else onDismiss()
                        }
                    }
                }
            }
        }
    }
}

/* ───────────── clean ───────────── */

/** Removes the grey / shadowed paper background of a scanned diagram and keeps the lines (live preview). */
@Composable
fun CleanDialog(pic: Pic, onDismiss: () -> Unit, onDone: (Pic) -> Unit) {
    val c = IQ.c
    val family = uiFont()
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ImageTools.CleanData?>(null) }
    var failed by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(40f) }
    var transparent by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(pic.url) {
        val d = withContext(Dispatchers.Default) {
            try {
                ImageTools.decode(pic.url)?.let { ImageTools.cleanPrepare(it) }
            } catch (e: Throwable) {
                null
            }
        }
        if (d == null) failed = true else data = d
    }
    LaunchedEffect(data, level, transparent) {
        val d = data ?: return@LaunchedEffect
        delay(40)
        val lv = level.toInt()
        val tr = transparent
        preview = withContext(Dispatchers.Default) {
            try { ImageTools.cleanRender(d, lv, tr).asImageBitmap() } catch (e: Throwable) { null }
        } ?: preview
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(IQD.dialog)
                .background(c.panel)
                .border(1.dp, c.border, IQD.dialog)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tr("fig.cleanTitle").isNotBlank()) {
                Text(tr("fig.cleanTitle"), color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = family)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 320.dp)
                    .clip(IQD.ctl)
                    .border(1.dp, c.borderStrong, IQD.ctl)
                    .drawBehind {
                        val s = 16.dp.toPx()
                        val cols = (size.width / s).toInt() + 1
                        val rows = (size.height / s).toInt() + 1
                        drawRect(Color.White)
                        for (r in 0 until rows) for (k in 0 until cols) {
                            if ((r + k) % 2 == 0) drawRect(Color(0xFFD4D6DA), Offset(k * s, r * s), Size(s, s))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val pv = preview
                if (pv != null) Image(pv, null, Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit)
                else Text(tr(if (failed) "fig.readErr" else "fig.busy"), color = c.textFaint, fontSize = 14.sp, fontFamily = family)
            }
            Text(tr("fig.cleanLevel"), color = c.textSecondary, fontSize = 13.sp, fontFamily = family)
            Slider(value = level, onValueChange = { level = it }, valueRange = 0f..100f, enabled = data != null)
            SwitchRow(tr("fig.cleanTransp"), tr("fig.cleanTranspHint"), transparent) { transparent = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IQButton(tr("common.cancel"), BtnKind.Ghost, Modifier.weight(1f)) { onDismiss() }
                IQButton(tr("fig.apply"), BtnKind.Primary, Modifier.weight(1f)) {
                    val d = data
                    if (d != null && !busy) {
                        busy = true
                        val lv = level.toInt()
                        val tp = transparent
                        scope.launch {
                            val out = withContext(Dispatchers.Default) { ImageTools.cleanFinish(d, lv, tp) }
                            busy = false
                            if (out != null) onDone(out) else onDismiss()
                        }
                    }
                }
            }
        }
    }
}


/* ───────────── pictures inside the editor field + the website's click popover ───────────── */

@Composable
private fun FigTbBtn(selected: Boolean, danger: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .height(32.dp)
            .defaultMinSize(minWidth = 32.dp)
            .clip(IQD.ctl)
            .background(if (selected) c.primarySoft else if (danger) c.dangerSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun FigTbText(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(), maxLines = 1)
}

/** Three-line alignment glyph (same strokes as the website popover icons). */
@Composable
private fun AlignGlyph(align: String, color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val sw = 2.dp.toPx()
        val w = size.width
        val h = size.height
        fun line(y: Float, x0: Float, x1: Float) =
            drawLine(color, Offset(x0 / 24f * w, y / 24f * h), Offset(x1 / 24f * w, y / 24f * h), strokeWidth = sw, cap = StrokeCap.Round)
        when (align) {
            "l" -> { line(6f, 3f, 21f); line(12f, 3f, 14f); line(18f, 3f, 18f) }
            "r" -> { line(6f, 3f, 21f); line(12f, 10f, 21f); line(18f, 6f, 21f) }
            else -> { line(6f, 3f, 21f); line(12f, 7f, 17f); line(18f, 5f, 19f) }
        }
    }
}

/**
 * A picture block shown for real inside a rich-text field (question, option, explanation, stimulus ...). Tap a picture:
 * a toolbar opens right under it (size, alignment, crop, clean, add beside, remove) exactly like the website's
 * `.iqimg-pop`. Size and alignment belong to the whole block; crop / clean / remove act on the tapped picture.
 *
 * [onChange] gets the new block, or null when the last picture was removed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FigureEditStrip(
    fig: FigData,
    selectedPic: Int?,
    onSelect: (Int?) -> Unit,
    onChange: (FigData?) -> Unit,
    onAddBeside: () -> Unit,
    onBarHeight: (Int) -> Unit = {},
) {
    val c = IQ.c
    var cropIdx by remember { mutableStateOf<Int?>(null) }
    var cleanIdx by remember { mutableStateOf<Int?>(null) }
    val sel = selectedPic?.takeIf { it in fig.pics.indices }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FigureBlock(fig, selected = sel, onPicClick = { i -> onSelect(if (sel == i) null else i) })
        if (sel != null) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onBarHeight(it.height) }
                    .clip(IQD.ctl)
                    .background(c.panel)
                    .border(1.dp, c.border, IQD.ctl)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf("S" to "fig.small", "M" to "fig.medium", "L" to "fig.large").forEach { (k, key) ->
                    FigTbBtn(fig.size == k, onClick = { onChange(fig.copy(size = k)) }) {
                        FigTbText(tr(key), if (fig.size == k) c.primary else c.textSecondary)
                    }
                }
                Box(Modifier.height(32.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(1.dp).height(20.dp).background(c.border))
                }
                listOf("l", "c", "r").forEach { a ->
                    FigTbBtn(fig.align == a, onClick = { onChange(fig.copy(align = a)) }) {
                        AlignGlyph(a, if (fig.align == a) c.primary else c.textSecondary)
                    }
                }
                Box(Modifier.height(32.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(1.dp).height(20.dp).background(c.border))
                }
                FigTbBtn(false, onClick = { cropIdx = sel }) { FigTbText(tr("fig.crop"), c.textSecondary) }
                FigTbBtn(false, onClick = { cleanIdx = sel }) { FigTbText(tr("fig.clean"), c.textSecondary) }
                if (fig.pics.size < MAX_FIG_PICS) {
                    FigTbBtn(false, onClick = onAddBeside) { FigTbText(tr("fig.addBeside"), c.textSecondary) }
                }
                FigTbBtn(false, danger = true, onClick = {
                    val rest = fig.pics.filterIndexed { k, _ -> k != sel }
                    onSelect(null)
                    onChange(if (rest.isEmpty()) null else fig.copy(pics = rest))
                }) { FigTbText(tr("fig.remove"), c.danger) }
            }
        }
    }

    cropIdx?.let { idx ->
        val p = fig.pics.getOrNull(idx)
        if (p == null) cropIdx = null
        else CropDialog(p, onDismiss = { cropIdx = null }, onDone = { np ->
            cropIdx = null
            onChange(fig.copy(pics = fig.pics.mapIndexed { k, old -> if (k == idx) np else old }))
        })
    }
    cleanIdx?.let { idx ->
        val p = fig.pics.getOrNull(idx)
        if (p == null) cleanIdx = null
        else CleanDialog(p, onDismiss = { cleanIdx = null }, onDone = { np ->
            cleanIdx = null
            onChange(fig.copy(pics = fig.pics.mapIndexed { k, old -> if (k == idx) np else old }))
        })
    }
}
