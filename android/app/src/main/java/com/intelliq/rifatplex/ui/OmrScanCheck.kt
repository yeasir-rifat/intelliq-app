package com.intelliq.rifatplex.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.intelliq.rifatplex.data.AlignResult
import com.intelliq.rifatplex.data.MarkState
import com.intelliq.rifatplex.data.OmrAlign
import com.intelliq.rifatplex.data.OmrReader
import com.intelliq.rifatplex.data.OmrSheetRead
import com.intelliq.rifatplex.data.QrStatus
import com.intelliq.rifatplex.data.OmrGeometry
import com.intelliq.rifatplex.data.OmrImageLoader
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/*
 * Step 4.4 - "take or pick a photo of a filled OMR sheet and see if the app finds the sheet".
 *
 * This is the first, small face of the scanner: it only runs the aligner (data/OmrAlign.kt) and shows the result with
 * the printed bubble positions drawn on top of the photo, so alignment can be checked by eye. Reading the bubbles and
 * grading come in steps 4.5 and 4.6, which will grow this into the full "OMR Evaluation" screen.
 * Photos stay on the device; nothing is uploaded. The last 6 photos are kept in memory (small preview only).
 */

/** One checked photo. [result] is null when the file could not be read at all. */
internal class ScanItem(
    val name: String,
    val imgW: Int,
    val imgH: Int,
    val thumb: ImageBitmap?,
    val result: AlignResult?,
    val ms: Long,
    /** Step 4.5: what the reader saw (QR, roll, marked rows), only when the sheet was aligned. */
    val read: OmrSheetRead? = null,
)

private const val KEEP_ITEMS = 6

/** Reads [uri], aligns it, makes a preview. Runs on the calling (background) thread. */
private fun checkPhoto(ctx: android.content.Context, uri: Uri, name: String): ScanItem {
    val t0 = System.currentTimeMillis()
    val img = OmrImageLoader.load(ctx.contentResolver, uri)
        ?: return ScanItem(name, 0, 0, null, null, System.currentTimeMillis() - t0)
    val result = OmrAlign.align(img)
    val read = (result as? AlignResult.Ok)?.let { OmrReader.read(img, it) }
    val thumb = OmrImageLoader.toBitmap(img, 1400).asImageBitmap()
    return ScanItem(name, img.w, img.h, thumb, result, System.currentTimeMillis() - t0, read)
}

@Composable
internal fun OmrScanSection() {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val c = IQ.c
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(listOf<ScanItem>()) }
    var waiting by remember { mutableStateOf(0) }
    var counter by remember { mutableStateOf(0) }
    var view by remember { mutableStateOf<ScanItem?>(null) }
    var errKey by remember { mutableStateOf<String?>(null) }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var camFile by remember { mutableStateOf<File?>(null) }

    fun startScan(uris: List<Uri>, temp: File?) {
        if (uris.isEmpty()) return
        errKey = null
        waiting += uris.size
        scope.launch {
            for (u in uris) {
                counter += 1
                val name = "#" + formatNumber(counter, lang == Lang.BN)
                val item = withContext(Dispatchers.Default) { checkPhoto(ctx, u, name) }
                items = (listOf(item) + items).take(KEEP_ITEMS)
                waiting -= 1
            }
            temp?.delete()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> startScan(uris, null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        val f = camFile
        camUri = null
        camFile = null
        if (ok && u != null) startScan(listOf(u), f) else f?.delete()
    }

    fun pickGallery() {
        try {
            gallery.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.read"
        }
    }

    fun capture() {
        try {
            val dir = File(ctx.cacheDir, "omr").apply { mkdirs() }
            val f = File.createTempFile("scan_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camFile = f
            camUri = uri
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.noCamera"
        } catch (e: Exception) {
            errKey = "scan.err.read"
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("scan.intro"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IQButton(tr("scan.camera"), BtnKind.Primary, Modifier.weight(1f)) { capture() }
            IQButton(tr("scan.gallery"), BtnKind.Secondary, Modifier.weight(1f)) { pickGallery() }
        }
        Text(tr("scan.tip"), color = c.textFaint, fontSize = 11.sp, fontFamily = uiFont())
        val e = errKey
        if (e != null) Text(tr(e), color = c.danger, fontSize = 12.sp, fontFamily = uiFont())
        if (waiting > 0) {
            Text(
                tr("scan.working").replace("{n}", formatNumber(waiting, lang == Lang.BN)),
                color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            )
        }
        for (item in items) ScanRow(item) { view = item }
    }

    val v = view
    if (v != null) ScanPreview(v) { view = null }
}

/** (text, found) for one checked photo. */
@Composable
private fun scanStatus(item: ScanItem): Pair<String, Boolean> {
    return when (val r = item.result) {
        null -> tr("scan.err.read") to false
        is AlignResult.Ok -> tr("scan.ok")
            .replace("{s}", String.format(Locale.US, "%.1f", r.scalePxPerMm))
            .replace("{d}", r.dashes.toString())
            .replace("{t}", OmrGeometry.TIMING_COUNT.toString())
            .replace("{e}", String.format(Locale.US, "%.2f", r.fitRmsMm))
            .replace("{sec}", String.format(Locale.US, "%.1f", item.ms / 1000.0)) to true
        is AlignResult.Failed -> tr("scan.fail." + r.reason.name) to false
    }
}

/** One line about what the reader saw: QR, roll number, how many rows are marked and how many need a look. */
@Composable
private fun readSummary(r: OmrSheetRead): String {
    val q = when (val qr = r.qr) {
        is QrStatus.Found -> qr.payload.let { p ->
            p.examId + (p.setIndex?.let { " #" + (it + 1) } ?: "") + " " + (p.page + 1) + "/" + p.pages
        }
        is QrStatus.None -> tr("scan.qr.none")
        is QrStatus.OtherVersion -> tr("scan.qr.other")
        is QrStatus.Foreign -> tr("scan.qr.foreign")
    }
    val marked = r.rows.count { it.state == MarkState.FILLED }
    val review = r.rows.count { it.state == MarkState.UNCERTAIN || it.state == MarkState.MULTI }
    return tr("scan.read")
        .replace("{q}", q)
        .replace("{r}", r.roll?.text ?: "-")
        .replace("{m}", marked.toString())
        .replace("{n}", r.rows.size.toString())
        .replace("{u}", review.toString()) + if (r.lowContrast) " · " + tr("scan.lowcontrast") else ""
}

@Composable
private fun ScanRow(item: ScanItem, onView: () -> Unit) {
    val c = IQ.c
    val (text, ok) = scanStatus(item)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IQD.card)
            .background(c.field)
            .border(1.dp, if (ok) c.success else c.border, IQD.card)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            Text(text, color = if (ok) c.successInk else c.danger, fontSize = 12.sp, fontFamily = uiFont())
            val rd = item.read
            if (rd != null) Text(readSummary(rd), color = c.text, fontSize = 12.sp, fontFamily = uiFont())
            val f = item.result as? AlignResult.Failed
            if (f != null) Text(f.detail, color = c.textFaint, fontSize = 11.sp, fontFamily = uiFont())
        }
        if (item.thumb != null) IQButton(tr("scan.view"), BtnKind.Ghost) { onView() }
    }
}

@Composable
private fun ScanPreview(item: ScanItem, onClose: () -> Unit) {
    val c = IQ.c
    val (text, ok) = scanStatus(item)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 10.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(IQD.dialog)
                .background(c.panel)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            Text(text, color = if (ok) c.successInk else c.danger, fontSize = 12.sp, fontFamily = uiFont())
            val thumb = item.thumb
            if (thumb != null && item.imgW > 0 && item.imgH > 0) {
                val res = item.result as? AlignResult.Ok
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(item.imgW.toFloat() / item.imgH.toFloat())
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    Image(thumb, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                    if (res != null) {
                        Canvas(Modifier.matchParentSize()) { drawAlignment(res, size.width / item.imgW) }
                    }
                }
                if (res != null) Text(tr("scan.legend"), color = c.textFaint, fontSize = 11.sp, fontFamily = uiFont())
            }
            IQButton(tr("scan.close"), BtnKind.Secondary, Modifier.fillMaxWidth(), onClose)
        }
    }
}

/** Draws where the aligner believes the printed bubbles (green), corner squares and timing dashes (blue) are. */
private fun DrawScope.drawAlignment(ok: AlignResult.Ok, k: Float) {
    val green = Color(0xFF16A34A)
    val blue = Color(0xFF2563EB)
    val r = (OmrGeometry.BUBBLE_INNER_R * ok.scalePxPerMm * k).toFloat().coerceAtLeast(1.5f)
    val thin = Stroke(width = 1.3f)
    for (slot in 0 until OmrGeometry.ROWS_PER_PAGE) for (o in 0 until OmrGeometry.OPTIONS) {
        val b = OmrGeometry.answerBubbleOfSlot(slot, o)
        val p = ok.toImage.map(b.x.toDouble(), b.y.toDouble())
        drawCircle(green, r, Offset((p.x * k).toFloat(), (p.y * k).toFloat()), style = thin)
    }
    for (m in OmrGeometry.cornerMarks()) {
        val p = ok.toImage.map((m.x + m.w / 2f).toDouble(), (m.y + m.h / 2f).toDouble())
        drawCircle(blue, r * 1.8f, Offset((p.x * k).toFloat(), (p.y * k).toFloat()), style = Stroke(width = 2.2f))
    }
    for (d in OmrGeometry.timingDashes()) {
        val p = ok.toImage.map((d.x + d.w / 2f).toDouble(), (d.y + d.h / 2f).toDouble())
        drawCircle(blue, 2f, Offset((p.x * k).toFloat(), (p.y * k).toFloat()))
    }
}
