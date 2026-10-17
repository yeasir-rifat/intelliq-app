package com.intelliq.rifatplex.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.intelliq.rifatplex.ui.theme.PIcons
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.intelliq.rifatplex.data.AlignResult
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.KeyExisting
import com.intelliq.rifatplex.data.KeyQrAssembly
import com.intelliq.rifatplex.data.OmrFlag
import com.intelliq.rifatplex.data.OmrGeometry
import com.intelliq.rifatplex.data.OmrLayout
import com.intelliq.rifatplex.data.OmrReport
import com.intelliq.rifatplex.data.QResult
import com.intelliq.rifatplex.data.QStatus
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Step 4.6 - "OMR Evaluation": photograph (with the phone's own camera app) or pick filled sheets, see the marks, fix what the reader was unsure about.
 *
 * The drawer section holds the camera / gallery buttons, the marking rules, a summary and one card per sheet. A card opens
 * the detail dialog: the photo with the right answers (green), the student's wrong marks (red) and marks to look at (amber)
 * drawn at the printed bubble positions, the roll number (editable) and the rows with their options as tap-to-correct chips.
 * All work happens in [OmrEvalViewModel]; photos stay on the device and only a small preview of each is kept.
 */

private val AMBER = Color(0xFFD97706)
private val GREEN = Color(0xFF16A34A)
private val RED = Color(0xFFDC2626)
private val BLUE = Color(0xFF2563EB)

private tailrec fun Context.findComponentActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> error("no activity")
}

private fun cleanNumber(v: String): String = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)

@Composable
internal fun OmrEvalSection(d: AppData) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val bn = lang == Lang.BN
    val c = IQ.c
    val vm = remember { ViewModelProvider(ctx.findComponentActivity())[OmrEvalViewModel::class.java] }
    vm.bind(ctx, OmrLayout(d.settings.omr, if (d.setCode.enabled) d.generatedSets.map { it.code } else emptyList()), d.examId)

    var errKey by remember { mutableStateOf<String?>(null) }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var camFile by remember { mutableStateOf<File?>(null) }
    var openId by rememberSaveable { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var exportErr by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    /**
     * Builds the file off the main thread and saves it to Downloads/IntelliQ (or opens the share sheet on Android 8/9).
     * [zip] = false: one PDF with every marked sheet. [zip] = true: a ZIP with one PDF per marked sheet ("Download all").
     */
    fun export(zip: Boolean) {
        if (exporting || vm.sheets.isEmpty()) return
        val snapshot = vm.sheets.toList()
        if (!OmrResultPdf.hasPages(snapshot)) {
            exportErr = true
            exportMsg = trKey("eval.pdf.none", lang)
            return
        }
        exporting = true
        exportMsg = null
        val layout = OmrLayout(d.settings.omr, if (d.setCode.enabled) d.generatedSets.map { it.code } else emptyList())
        scope.launch {
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val r = withContext(Dispatchers.Default) {
                    if (zip) {
                        ExportResult("OMR_Results_$stamp.zip", "application/zip", OmrResultPdf.buildZip(ctx, snapshot, layout, lang), true)
                    } else {
                        ExportResult("OMR_Results_$stamp.pdf", "application/pdf", OmrResultPdf.build(ctx, snapshot, layout, lang), false)
                    }
                }
                val saved = PdfExport.deliver(ctx.findComponentActivity(), r)
                exportErr = false
                exportMsg = trKey(if (saved) "eval.exported" else "eval.shared", lang).replace("{f}", r.fileName)
            } catch (e: Exception) {
                exportErr = true
                exportMsg = trKey("eval.exportFail", lang)
            } finally {
                exporting = false
            }
        }
    }

    // sign-in is required (no plans / limits)
    val cloud = remember { com.intelliq.rifatplex.data.CloudSync.get(ctx) }
    fun newSession() = vm.sheets.isEmpty() && vm.pending == 0
    /** Opens the sign-in screen and returns false when nobody is signed in. */
    fun allowed(): Boolean = SignInGate.check(cloud)

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        errKey = null
        vm.add(uris, null)
    }
    val camGate = rememberCameraGate()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        val f = camFile
        camUri = null
        camFile = null
        if (ok && u != null) {
            errKey = null
            vm.add(listOf(u), f)
        } else {
            f?.delete()
        }
    }

    fun pickGallery() {
        if (!allowed()) return
        try {
            gallery.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.read"
        }
    }

    fun capture() {
        if (!allowed()) return
        try {
            val dir = File(ctx.cacheDir, "omr").apply { mkdirs() }
            val f = File.createTempFile("scan_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camFile = f
            camUri = uri
            camGate { camera.launch(uri) }
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.noCamera"
        } catch (e: Exception) {
            errKey = "scan.err.read"
        }
    }

    // ---- answer-sheet import (step 4.9): camera / gallery / PDF ----
    var keyOpen by rememberSaveable { mutableStateOf(false) }
    var keyCamUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var keyCamFile by remember { mutableStateOf<File?>(null) }
    val keyGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        errKey = null
        vm.addKeyImages(uris, null)
    }
    val keyPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        errKey = null
        if (uri != null) vm.addKeyPdf(uri)
    }
    val keyCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = keyCamUri
        val f = keyCamFile
        keyCamUri = null
        keyCamFile = null
        if (ok && u != null) vm.addKeyImages(listOf(u), f) else f?.delete()
    }

    fun launchSafely(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.read"
        }
    }

    fun captureKey() {
        try {
            val dir = File(ctx.cacheDir, "omr").apply { mkdirs() }
            val f = File.createTempFile("key_", ".jpg", dir)
            keyCamFile = f
            keyCamUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camGate { keyCamera.launch(keyCamUri!!) }
        } catch (e: ActivityNotFoundException) {
            errKey = "scan.err.noCamera"
        } catch (e: Exception) {
            errKey = "scan.err.read"
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- 1. add sheets: the phone's own camera app, or the gallery ----
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("eval.title"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                InfoTip(tr("eval.intro") + "\n\n" + tr("scan.tip"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScanTile(PIcons.CameraAlt, tr("scan.camera"), true, Modifier.weight(1f)) { capture() }
                ScanTile(PIcons.AddPhotoAlternate, tr("scan.gallery"), false, Modifier.weight(1f)) { pickGallery() }
            }
            val e = errKey
            if (e != null) Text(tr(e), color = c.danger, fontSize = 13.sp, fontFamily = uiFont())
            if (vm.pending > 0) {
                Text(
                    tr("scan.working").replace("{n}", formatNumber(vm.pending, bn)),
                    color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                )
            }
        }

        // ---- 2. results (right under the buttons, so new photos show up where the finger is) ----
        val list = vm.sheets
        if (list.isNotEmpty()) {
            val ok = list.count { it.state == EvalState.OK }
            val review = list.count { it.state == EvalState.REVIEW || it.state == EvalState.UNMATCHED }
            val failed = list.count { it.state == EvalState.FAILED }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("eval.sheets").replace("{n}", formatNumber(list.size, bn)),
                    color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(formatNumber(ok, bn), tr("eval.stat.ok"), c.successInk, Modifier.weight(1f))
                    StatChip(formatNumber(review, bn), tr("eval.stat.review"), AMBER, Modifier.weight(1f))
                    StatChip(formatNumber(failed, bn), tr("eval.stat.failed"), c.danger, Modifier.weight(1f))
                }
                for (s in list) SheetCard(s, bn) { openId = s.id }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IQButton(tr(if (exporting) "eval.exporting" else "eval.exportPdf"), BtnKind.Primary, Modifier.weight(1f).alpha(if (exporting) 0.5f else 1f)) { export(false) }
                    IQButton(tr("eval.downloadAll"), BtnKind.Secondary, Modifier.weight(1f).alpha(if (exporting) 0.5f else 1f)) { export(true) }
                }
                val em = exportMsg
                if (em != null) Text(em, color = if (exportErr) c.danger else c.successInk, fontSize = 13.sp, fontFamily = uiFont())
                Text(
                    tr("eval.clear"), color = c.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(IQD.ctl).clickable { confirmClear = true }.padding(vertical = 10.dp),
                )
            }
        }

        // ---- 3. marking rules ----
        EvalPanel {
            val rulesInfo = rulesNowInfo(vm, bn)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("eval.rules"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                if (!rulesInfo.second) InfoTip(rulesInfo.first) // the "marking with ..." line is a tooltip; only an error stays visible
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleField(tr("eval.marks"), c.success, vm.marksText, tr("eval.asExam"), Modifier.weight(1f)) { v -> vm.marksText = cleanNumber(v); vm.settingsChanged() }
                RuleField(tr("eval.negative"), c.danger, vm.negText, tr("eval.asExam"), Modifier.weight(1f)) { v -> vm.negText = cleanNumber(v); vm.settingsChanged() }
            }
            if (rulesInfo.second) Text(rulesInfo.first, color = c.danger, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            SwitchRow(tr("eval.multi"), tr("eval.multi.hint"), vm.penalizeMulti) { v -> vm.penalizeMulti = v; vm.settingsChanged() }
        }

        // ---- 4. answer sheet import by QR / PDF (step 4.9) ----
        EvalPanel {
            FoldRow(tr("keyimp.title"), keyOpen, bold = true, tip = tr("eval.key.hint") + "\n\n" + tr("keyimp.intro")) { keyOpen = !keyOpen }
            if (keyOpen) KeyImportPanel(
                vm, bn,
                { if (SignInGate.check(cloud)) captureKey() },
                { if (SignInGate.check(cloud)) launchSafely { keyGallery.launch("image/*") } },
                { if (SignInGate.check(cloud)) launchSafely { keyPdf.launch("application/pdf") } },
            )
        }
    }

    val open = vm.sheets.firstOrNull { it.id == openId }
    if (open != null) EvalDetail(vm, open, bn) { openId = -1 }
    if (confirmClear) ConfirmDialog(
        tr("eval.clear.confirm"), tr("eval.clear.yes"),
        onConfirm = { vm.clear(); openId = -1; confirmClear = false },
        onDismiss = { confirmClear = false },
    )
}

@Composable
private fun KeyImportPanel(vm: OmrEvalViewModel, bn: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onPdf: () -> Unit) {
    val c = IQ.c
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IQButton(tr("scan.camera"), BtnKind.Secondary, Modifier.weight(1f)) { onCamera() }
            IQButton(tr("scan.gallery"), BtnKind.Secondary, Modifier.weight(1f)) { onGallery() }
            IQButton("PDF", BtnKind.Secondary, Modifier.weight(1f)) { onPdf() }
        }
        if (vm.keyBusy > 0) {
            Text(tr("keyimp.working"), color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        }
        val imp = vm.keyImport
        val asm = imp.assembly
        val msg = vm.keyMsg
        if (msg != null) {
            Text(tr(msg).replace("{n}", formatNumber(vm.keyMsgArg.toIntOrNull() ?: 0, bn)), color = c.danger, fontSize = 13.sp, fontFamily = uiFont())
        }
        if (vm.keySaved) Text(tr("keyimp.saved"), color = c.successInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        when (asm) {
            is KeyQrAssembly.Missing -> {
                // QR pages are 0-based, people count from 1
                val left = asm.pages.joinToString(", ") { formatNumber(it + 1, bn) }
                Text(
                    tr("keyimp.progress")
                        .replace("{h}", formatNumber(imp.pagesHave, bn))
                        .replace("{t}", formatNumber(imp.pagesTotal, bn))
                        .replace("{p}", left),
                    color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                )
                IQButton(tr("keyimp.reset"), BtnKind.Ghost, Modifier.fillMaxWidth()) { vm.keyReset() }
            }
            is KeyQrAssembly.Mismatch -> {
                if (!imp.isEmpty) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IQButton(tr("keyimp.dropLast"), BtnKind.Secondary, Modifier.weight(1f)) { vm.keyDropLast() }
                        IQButton(tr("keyimp.reset"), BtnKind.Ghost, Modifier.weight(1f)) { vm.keyReset() }
                    }
                }
            }
            is KeyQrAssembly.Done -> {
                val s = asm.snapshot
                val ex = vm.keyResult()?.second ?: KeyExisting.NONE
                val set = s.setIndex?.let { formatNumber(it + 1, bn) } ?: "—"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        tr("keyimp.summary")
                            .replace("{id}", s.examId)
                            .replace("{set}", set)
                            .replace("{q}", formatNumber(s.items.size, bn))
                            .replace("{u}", formatNumber(s.unanswered, bn))
                            .replace("{m}", fmt(s.marksPerQ, bn))
                            .replace("{neg}", fmt(s.negativePerWrong, bn)),
                        Modifier.weight(1f), color = c.text, fontSize = 13.sp, fontFamily = uiFont(),
                    )
                    InfoTip(tr("keyimp.cloudNote"))
                }
                when (ex) {
                    KeyExisting.SAME -> {
                        Text(tr("keyimp.same"), color = c.successInk, fontSize = 13.sp, fontFamily = uiFont())
                        IQButton(tr("keyimp.done"), BtnKind.Ghost, Modifier.fillMaxWidth()) { vm.keyReset() }
                    }
                    KeyExisting.DIFFERENT -> {
                        Text(tr("keyimp.different"), color = AMBER, fontSize = 13.sp, fontFamily = uiFont())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IQButton(tr("keyimp.keepOld"), BtnKind.Secondary, Modifier.weight(1f)) { vm.keyReset() }
                            IQButton(tr("keyimp.replace"), BtnKind.Primary, Modifier.weight(1f)) { vm.keySave(true) }
                        }
                    }
                    KeyExisting.NONE -> IQButton(tr("keyimp.save"), BtnKind.Primary, Modifier.fillMaxWidth()) { vm.keySave(false) }
                }
            }
        }
    }
}

/**
 * The stored sheet photo, halved (power-of-two sample size) until it is only just wider than [MAX_PHOTO_W] px.
 * The marks are drawn from the full-size geometry (size.width / imgW), so a smaller bitmap lines up the same.
 */
private const val MAX_PHOTO_W = 1600

private fun decodeEvalPhoto(jpeg: ByteArray, srcW: Int): ImageBitmap? {
    var sample = 1
    while (srcW / (sample * 2) >= MAX_PHOTO_W) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)?.asImageBitmap()
}

@Composable
private fun stateColor(s: EvalState): Color = when (s) {
    EvalState.OK -> IQ.c.successInk
    EvalState.REVIEW -> AMBER
    EvalState.UNMATCHED -> AMBER
    EvalState.FAILED -> IQ.c.danger
}

/** "Roll 123456 · set 2" for a graded sheet (the score is shown on the right of the card), else the reason. */
@Composable
private fun sheetLine(s: EvalSheet, bn: Boolean): String {
    val res = s.result
    val roll = (s.roll ?: "-").let { if (bn) it.map { ch -> if (ch in '0'..'9') ('০' + (ch - '0')) else ch }.joinToString("") else it }
    if (res == null) {
        return when {
            s.state == EvalState.FAILED -> tr(s.failKey ?: "scan.err.read")
            else -> tr("eval.roll") + " " + roll + " · " + tr("eval.state.UNMATCHED")
        }
    }
    val set = res.setIndex?.let { " · " + tr("eval.set") + " " + formatNumber(it + 1, bn) } ?: ""
    return tr("eval.roll") + " " + roll + set
}

private fun fmt(v: Double, bn: Boolean): String {
    val t = if (v == Math.rint(v)) String.format(Locale.US, "%d", v.toLong()) else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    return if (bn) t.map { if (it in '0'..'9') '০' + (it - '0') else if (it == '.') '.' else it }.joinToString("") else t
}

/** The line that says which rules the sheets are marked with right now, and whether it is an error (shown in red). */
@Composable
private fun rulesNowInfo(vm: OmrEvalViewModel, bn: Boolean): Pair<String, Boolean> {
    val m = parseRuleNumber(vm.marksText)
    val n = parseRuleNumber(vm.negText)
    val badM = vm.marksText.isNotBlank() && (m == null || m <= 0.0)
    val badN = vm.negText.isNotBlank() && (n == null || n < 0.0)
    return when {
        badM || badN -> tr("eval.rules.bad") to true
        m == null && n == null -> tr("eval.rules.exam") to false
        else -> {
            val ms = if (m != null) fmt(m, bn) else tr("eval.asExam")
            val ns = if (n != null) fmt(n, bn) else tr("eval.asExam")
            tr("eval.rules.now").replace("{m}", ms).replace("{n}", ns) to false
        }
    }
}

/** A big tappable tile: icon on top, label. [filled] = the main action. */
@Composable
private fun ScanTile(icon: ImageVector, label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val bg = if (filled) c.primary else c.primarySoft
    val fg = if (filled) Color.White else c.primaryInk
    Column(
        modifier
            .heightIn(min = 84.dp)
            .clip(IQD.card)
            .background(bg)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (filled) Color.White.copy(alpha = 0.2f) else c.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = fg) }
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(), textAlign = TextAlign.Center)
    }
}

/** A bordered card that groups related controls (no divider lines between the controls). */
@Composable
private fun EvalPanel(content: @Composable () -> Unit) {
    val c = IQ.c
    Column(
        Modifier.fillMaxWidth().clip(IQD.card).background(c.field).border(1.dp, c.border, IQD.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Label with a small colour dot above a numeric field. */
@Composable
private fun RuleField(label: String, dot: Color, value: String, placeholder: String, modifier: Modifier, onChange: (String) -> Unit) {
    val c = IQ.c
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(label, color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
        }
        InputBox(value, onChange, placeholder, singleLine = true, keyboardType = KeyboardType.Decimal)
    }
}

/** A tappable row with a chevron, used to fold / unfold a part of a panel. */
@Composable
private fun FoldRow(title: String, open: Boolean, bold: Boolean = false, tip: String? = null, onClick: () -> Unit) {
    val c = IQ.c
    Row(
        Modifier.fillMaxWidth().clip(IQD.ctl).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the helper text is a tip: an (i) icon to the right of the title
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, Modifier.weight(1f, fill = false),
                color = if (bold) c.text else c.primaryInk,
                fontSize = if (bold) 15.sp else 13.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
                fontFamily = uiFont(),
            )
            if (tip != null) InfoTip(tip)
        }
        Icon(if (open) PIcons.ExpandLess else PIcons.ExpandMore, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
}

/** A number with a caption in a soft tile: how many sheets are fine / to check / failed. */
@Composable
private fun StatChip(value: String, label: String, color: Color, modifier: Modifier) {
    val c = IQ.c
    Column(
        modifier.clip(IQD.card).background(color.copy(alpha = 0.10f)).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont())
        Text(label, color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SheetCard(s: EvalSheet, bn: Boolean, onOpen: () -> Unit) {
    val c = IQ.c
    val col = stateColor(s.state)
    val res = s.result
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IQD.card)
            .background(c.field)
            .border(1.dp, if (s.state == EvalState.OK) c.success else c.border, IQD.card)
            .clickable { onOpen() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(col.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            val ic = when (s.state) {
                EvalState.OK -> PIcons.Check
                EvalState.FAILED -> PIcons.Close
                else -> PIcons.ErrorCircle
            }
            Icon(ic, tr("eval.state." + s.state.name), Modifier.size(18.dp), tint = col)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(sheetLine(s, bn), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            Text(tr("eval.state." + s.state.name), color = col, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        }
        if (res != null) {
            Text(
                fmt(res.score, bn) + " / " + fmt(res.total, bn),
                color = col, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
            )
        }
    }
}

@Composable
private fun EvalDetail(vm: OmrEvalViewModel, s: EvalSheet, bn: Boolean, onClose: () -> Unit) {
    val c = IQ.c
    val res = s.result
    var showAll by rememberSaveable { mutableStateOf(false) }
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
            Text(s.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            Text(tr("eval.state." + s.state.name), color = stateColor(s.state), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())

            if (res != null) {
                Text(
                    tr("eval.score").replace("{s}", fmt(res.score, bn)).replace("{t}", fmt(res.total, bn)),
                    color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
                )
                Text(
                    tr("eval.counts")
                        .replace("{a}", formatNumber(res.correct, bn)).replace("{b}", formatNumber(res.wrong, bn))
                        .replace("{c}", formatNumber(res.blank, bn)).replace("{u}", formatNumber(res.review.size, bn)),
                    color = c.textSecondary, fontSize = 13.sp, fontFamily = uiFont(),
                )
                Text(
                    tr("eval.calc")
                        .replace("{a}", formatNumber(res.correct, bn)).replace("{m}", fmt(res.marksPerQ, bn))
                        .replace("{w}", formatNumber(res.rows.count { it.delta < 0.0 }, bn)).replace("{n}", fmt(res.negativePerWrong, bn))
                        .replace("{s}", fmt(res.score, bn)),
                    color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont(),
                )
            } else if (s.state == EvalState.FAILED) {
                Text(tr(s.failKey ?: "scan.err.read"), color = c.danger, fontSize = 13.sp, fontFamily = uiFont())
                if (s.failDetail.isNotEmpty()) Text(s.failDetail, color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
            }
            val shownFlags = s.flags.filter { it != OmrFlag.KEY_INCOMPLETE || res != null }
            for (f in shownFlags) {
                if (f == OmrFlag.ROLL_INCOMPLETE && s.rollFix != null) continue
                Text("• " + tr("eval.flag." + f.name), color = AMBER, fontSize = 13.sp, fontFamily = uiFont())
            }

            // roll number: what was read ("?" = a column that was not clearly filled), fixable by hand
            if (s.read != null) {
                Text(tr("eval.rollFix"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
                InputBox(
                    s.rollFix ?: (s.read.roll?.text ?: ""),
                    { v -> vm.setRoll(s.id, v.filter { it.isLetterOrDigit() || it == '?' }.take(16)) },
                    tr("eval.roll"),
                    singleLine = true,
                )
            }

            // the photo with the marks drawn on it
            val jpeg = s.jpeg
            if (jpeg != null && s.imgW > 0 && s.imgH > 0) {
                // decoded off the main thread and no larger than the screen needs, so opening a sheet does not stall the UI
                val bmp = produceState<ImageBitmap?>(null, s.id) {
                    value = withContext(Dispatchers.Default) { decodeEvalPhoto(jpeg, s.imgW) }
                }.value
                if (bmp != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(s.imgW.toFloat() / s.imgH.toFloat())
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        Image(bmp, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                        val al = s.align
                        if (al != null) Canvas(Modifier.matchParentSize()) { drawResult(al, res?.rows ?: emptyList(), size.width / s.imgW) }
                    }
                    if (res != null) Text(tr("eval.legend"), color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
                }
            }

            // rows: the ones to look at first, every row on request
            if (res != null) {
                val review = res.review
                val rows = if (showAll) res.rows else review
                Text(
                    tr(if (showAll) "eval.allRows" else "eval.reviewRows").replace("{n}", formatNumber(review.size, bn)),
                    color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                )
                if (rows.isEmpty()) Text(tr("eval.noReview"), color = c.textFaint, fontSize = 13.sp, fontFamily = uiFont())
                for (q in rows) EvalRow(q, bn, s.overrides.containsKey(q.slot), onToggle = { o ->
                    val now = if (o in q.marked) q.marked - o else q.marked + o
                    vm.setOverride(s.id, q.slot, now)
                }, onBlank = { vm.setOverride(s.id, q.slot, emptySet()) }, onUndo = { vm.setOverride(s.id, q.slot, null) })
                IQButton(tr(if (showAll) "eval.showReview" else "eval.showAll"), BtnKind.Ghost, Modifier.fillMaxWidth()) { showAll = !showAll }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IQButton(tr("eval.delete"), BtnKind.Ghost, Modifier.weight(1f)) { vm.remove(s.id); onClose() }
                IQButton(tr("scan.close"), BtnKind.Secondary, Modifier.weight(1f), onClose)
            }
        }
    }
}

@Composable
private fun EvalRow(q: QResult, bn: Boolean, overridden: Boolean, onToggle: (Int) -> Unit, onBlank: () -> Unit, onUndo: () -> Unit) {
    val c = IQ.c
    val labels = if (bn) listOf("ক", "খ", "গ", "ঘ") else listOf("A", "B", "C", "D")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IQD.card)
            .background(c.field)
            .border(1.dp, c.border, IQD.card)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("eval.q") + " " + formatNumber(q.printedNumber, bn),
                color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            )
            Text(tr("eval.qs." + q.status.name), color = rowColor(q.status), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (o in labels.indices) {
                OptChip(labels[o], o in q.marked, o in q.correct) { onToggle(o) }
            }
            Box(
                Modifier
                    .height(44.dp)
                    .width(52.dp)
                    .clip(CircleShape)
                    .background(if (q.marked.isEmpty()) c.primarySoft else c.field)
                    .border(1.dp, c.border, CircleShape)
                    .clickable { onBlank() },
                contentAlignment = Alignment.Center,
            ) { Text("—", color = c.text, fontSize = 14.sp, fontFamily = uiFont()) }
        }
        if (overridden) {
            Text(
                tr("eval.undo"), color = c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                modifier = Modifier.clip(IQD.ctl).clickable { onUndo() }.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun rowColor(s: QStatus): Color = when (s) {
    QStatus.CORRECT -> IQ.c.successInk
    QStatus.WRONG -> IQ.c.danger
    QStatus.UNCERTAIN, QStatus.MULTI -> AMBER
    else -> IQ.c.textFaint
}

/** A round option button: filled = marked by the student (or the teacher), green ring = the right option. */
@Composable
private fun OptChip(label: String, marked: Boolean, correct: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (marked) c.primary else c.field)
            .border(if (correct) 2.dp else 1.dp, if (correct) c.success else c.border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (marked) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
    }
}

/**
 * Draws on the photo, at the printed bubble positions (the aligner's map): the right option green, a wrong mark red, a mark
 * the reader was unsure of amber, plus the four corner squares in blue so a misalignment shows at a glance.
 */
private fun DrawScope.drawResult(al: AlignResult.Ok, rows: List<QResult>, k: Float) {
    val r = (OmrGeometry.BUBBLE_INNER_R * al.scalePxPerMm * k).toFloat().coerceAtLeast(2f)
    fun centre(slot: Int, o: Int): Offset {
        val b = OmrGeometry.answerBubbleOfSlot(slot, o)
        val p = al.toImage.map(b.x.toDouble(), b.y.toDouble())
        return Offset((p.x * k).toFloat(), (p.y * k).toFloat())
    }
    for (q in rows) {
        for (o in q.correct) {
            if (o in 0 until OmrGeometry.OPTIONS) drawCircle(GREEN, r * 1.25f, centre(q.slot, o), style = Stroke(width = 2.6f))
        }
        val wrong = when (q.status) {
            QStatus.WRONG -> q.marked - q.correct
            QStatus.MULTI -> q.marked - q.correct
            else -> emptySet()
        }
        for (o in wrong) if (o in 0 until OmrGeometry.OPTIONS) drawCircle(RED, r * 1.25f, centre(q.slot, o), style = Stroke(width = 2.6f))
        if (q.status == QStatus.UNCERTAIN) {
            for (o in q.marked) if (o in 0 until OmrGeometry.OPTIONS) drawCircle(AMBER, r * 1.45f, centre(q.slot, o), style = Stroke(width = 2.6f))
        }
    }
    for (m in OmrGeometry.cornerMarks()) {
        val p = al.toImage.map((m.x + m.w / 2f).toDouble(), (m.y + m.h / 2f).toDouble())
        drawCircle(BLUE, r * 1.8f, Offset((p.x * k).toFloat(), (p.y * k).toFloat()), style = Stroke(width = 2.2f))
    }
}
