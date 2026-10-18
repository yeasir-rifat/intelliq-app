package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import android.content.ActivityNotFoundException
import com.intelliq.rifatplex.ui.theme.PIcons
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.intelliq.rifatplex.data.AI_MAX_FILES
import com.intelliq.rifatplex.data.AiFile
import com.intelliq.rifatplex.data.AiKind
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.ui.theme.IQ
import java.io.File

private val PICK_TYPES = arrayOf(
    "image/*",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/msword",
    "application/vnd.ms-powerpoint",
)

/** "Generate MCQs with AI" dialog (the website's AI Mode modal). */
@Composable
fun AiModal(vm: AiViewModel, store: AppStore) {
    val ctx = LocalContext.current
    val lang = LocalLang.current

    val pickDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(ctx.contentResolver, uris)
    }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camGate = rememberCameraGate()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        camUri = null
        if (ok && u != null) vm.addFiles(ctx.contentResolver, listOf(u))
    }

    if (!vm.open) return

    fun pick() {
        if (vm.files.size >= AI_MAX_FILES) {
            vm.setError("ai.err.max10")
            return
        }
        try {
            pickDocs.launch(PICK_TYPES)
        } catch (e: ActivityNotFoundException) {
            vm.setError("ai.err.generic")
        }
    }

    fun capture() {
        if (vm.files.size >= AI_MAX_FILES) {
            vm.setError("ai.err.max10")
            return
        }
        try {
            val dir = File(ctx.cacheDir, "ai").apply { mkdirs() }
            val f = File.createTempFile("capture_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camUri = uri
            camGate { camera.launch(uri) }
        } catch (e: ActivityNotFoundException) {
            vm.setError("ai.err.noCamera")
        } catch (e: Exception) {
            vm.setError("ai.err.generic")
        }
    }

    val c = IQ.c
    val shape = IQD.dialog
    val genTotal = run {
        val ts = store.data.ai.types
        if (vm.onlyType != null) ts.filter { it.type == vm.onlyType }.sumOf { it.count } else ts.filter { it.enabled }.sumOf { it.count }
    }
    Dialog(onDismissRequest = { vm.closeModal() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 10.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.glassBorder, shape),
        ) {
            // head
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(IQD.card).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(PIcons.AutoAwesome, null, Modifier.size(21.dp), tint = c.primary)
                    }
                    Column {
                        Text(tr("ai.title"), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                        if (tr("ai.subtitle").isNotBlank()) {
                            Text(tr("ai.subtitle"), color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
                        }
                    }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { vm.closeModal() },
                    contentAlignment = Alignment.Center,
                ) { Icon(PIcons.Close, null, Modifier.size(20.dp), tint = c.textSecondary) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

            // body
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Segmented(listOf("file" to tr("ai.modeFile"), "topic" to tr("ai.modeTopic")), if (vm.customPrompt) "topic" else "file") { vm.setCustom(it == "topic") }

                if (!vm.customPrompt) SectionCard(tr("ai.sectionSource"), PIcons.Upload, { InfoTip(tr("tip.aiSource")) }) {
                    if (vm.files.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(CircleShape).background(c.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) { Icon(PIcons.AddPhotoAlternate, null, Modifier.size(24.dp), tint = c.primary) }
                            Text(tr("ai.noFileSelected"), color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                            Column(Modifier.fillMaxWidth().widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AiBtn(tr("ai.capture"), PIcons.CameraAlt, BtnKind.Secondary, Modifier.fillMaxWidth()) { capture() }
                                AiBtn(tr("ai.upload"), PIcons.Upload, BtnKind.Secondary, Modifier.fillMaxWidth()) { pick() }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.files.forEach { f -> FileChip(f) { vm.removeFile(f.id) } }
                            AiBtn(tr("ai.addMore"), PIcons.Add, BtnKind.Secondary, Modifier.fillMaxWidth()) { pick() }
                        }
                    }
                }

                AiTypesSection(vm, store)

                SectionCard(tr(if (vm.customPrompt) "ai.instructionLabelTopic" else "ai.instructionLabel"), PIcons.Edit, { InfoTip(tr("tip.aiInstruction")) }) {
                    InputBox(
                        vm.prompt, { vm.prompt = it },
                        tr(if (vm.customPrompt) "ai.promptPlaceholderTopic" else "ai.promptPlaceholderFile"),
                        minLines = 4,
                    )
                }

                // status
                val st = vm.status
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (st != null && st.loading) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = c.primary, strokeWidth = 2.dp)
                    }
                    if (st != null) {
                        Text(
                            aiStatusText(st, lang),
                            color = if (st.error) c.danger else c.textSecondary,
                            fontSize = 13.sp, fontFamily = uiFont(),
                        )
                    }
                }
            }

            // foot
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                Modifier.fillMaxWidth().background(c.panelSoft).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AiBtn(tr("common.cancel"), null, BtnKind.Ghost, Modifier.weight(1f)) { vm.closeModal() }
                AiBtn(tr("ai.generateMcqs") + " · " + genTotal, PIcons.AutoAwesome, BtnKind.Primary, Modifier.weight(1f), enabled = !vm.busy) {
                    vm.generate(store)
                }
            }
        }
    }
}

@Composable
private fun AiBtn(
    text: String,
    icon: ImageVector?,
    kind: BtnKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = IQ.c
    val shape = IQD.ctl
    val bg = when (kind) {
        BtnKind.Primary -> c.primary
        BtnKind.Secondary -> c.primarySoft
        BtnKind.Ghost -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = when (kind) {
        BtnKind.Primary -> androidx.compose.ui.graphics.Color.White
        BtnKind.Secondary -> c.primary
        BtnKind.Ghost -> c.textSecondary
    }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .background(bg)
            .then(if (kind == BtnKind.Ghost) Modifier.border(1.dp, c.border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = IQD.fieldH)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1)
    }
}

@Composable
private fun FileChip(f: AiFile, onRemove: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (f.done) 1f else 0.85f)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(shape).background(c.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !f.done -> CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.dp)
                f.thumb != null -> Image(f.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> {
                    val icon = when {
                        f.kind == AiKind.Pdf -> PIcons.PictureAsPdf
                        f.kind == AiKind.Text && f.source == "pptx" -> PIcons.Slideshow
                        f.kind == AiKind.Text -> PIcons.Description
                        else -> PIcons.Image
                    }
                    Icon(icon, null, Modifier.size(17.dp), tint = c.primary)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(f.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = when {
                !f.done -> tr("ai.uploading")
                f.kind == AiKind.Text -> tr(if (f.source == "pptx") "ai.pptxExtracted" else "ai.docxExtracted")
                f.kind == AiKind.Pdf -> "PDF"
                else -> tr("ai.imageLabel")
            }
            Text(meta, color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont(), maxLines = 1)
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(PIcons.Close, null, Modifier.size(16.dp), tint = c.textFaint) }
    }
}

private fun <T> moveItem(l: List<T>, from: Int, to: Int): List<T> {
    val m = l.toMutableList()
    val x = m.removeAt(from)
    m.add(to, x)
    return m
}

private val TYPE_CODES = mapOf("mcq" to "MCQ", "creative" to "CQ", "tf" to "T/F", "fill" to "Gap", "short" to "SQ", "desc" to "BQ")

/** "Question types" card: drag handle, type badge, name, count, switch; then difficulty, answers and explanations. */
@Composable
private fun AiTypesSection(vm: AiViewModel, store: AppStore) {
    val c = IQ.c
    val ai = store.data.ai
    val only = vm.onlyType
    val rowH = 56.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val list = if (only != null) ai.types.filter { it.type == only } else ai.types
    val total = (if (only != null) list else list.filter { it.enabled }).sumOf { it.count }

    SectionCard(
        tr("ai.typesTitle"), PIcons.GridView,
        trailing = {
            Box(Modifier.clip(IQD.ctl).background(if (total > 120) c.dangerSoft else c.primarySoft).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(
                    tr("ai.total").replace("{n}", total.toString()),
                    color = if (total > 120) c.danger else c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                )
            }
        },
    ) {
        if (only != null) {
            Text(tr("ai.onlyHint").replace("{type}", tr("ai.type.$only")), color = c.textSecondary, fontSize = 13.sp, fontFamily = uiFont())
        }
        Column(Modifier.fillMaxWidth().clip(IQD.ctl).background(c.field).border(1.dp, c.border, IQD.ctl)) {
            list.forEachIndexed { i, cfg ->
                key(cfg.type) {
                    val on = only != null || cfg.enabled
                    Row(
                        Modifier.fillMaxWidth().height(rowH).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (only == null) {
                            Box(
                                Modifier
                                    .size(40.dp, rowH)
                                    .pointerInput(cfg.type) {
                                        var acc = 0f
                                        detectDragGestures(
                                            onDragStart = { acc = 0f },
                                            onDragEnd = { acc = 0f },
                                            onDragCancel = { acc = 0f },
                                        ) { change, amount ->
                                            change.consume()
                                            acc += amount.y
                                            val cur = store.data.ai.types
                                            val idx = cur.indexOfFirst { it.type == cfg.type }
                                            if (acc > rowPx / 2 && idx in 0 until cur.lastIndex) {
                                                store.updateAi { a -> a.copy(types = moveItem(a.types, idx, idx + 1)) }
                                                acc -= rowPx
                                            } else if (acc < -rowPx / 2 && idx > 0) {
                                                store.updateAi { a -> a.copy(types = moveItem(a.types, idx, idx - 1)) }
                                                acc += rowPx
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(PIcons.DragIndicator, null, Modifier.size(18.dp), tint = c.textFaint) }
                        } else {
                            Spacer(Modifier.width(14.dp))
                        }
                        Row(
                            Modifier.weight(1f).alpha(if (on) 1f else 0.45f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TypeBadge(TYPE_CODES[cfg.type] ?: cfg.type)
                            Text(tr("ai.type." + cfg.type), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.alpha(if (on) 1f else 0.45f)) {
                            NumberField(cfg.count, 1, 100, on) { n ->
                                store.updateAi { a -> a.copy(types = a.types.map { if (it.type == cfg.type) it.copy(count = n) else it }) }
                            }
                        }
                        if (only == null) {
                            Spacer(Modifier.width(12.dp))
                            IQSwitch(cfg.enabled) { v -> store.updateAi { a -> a.copy(types = a.types.map { if (it.type == cfg.type) it.copy(enabled = v) else it }) } }
                        }
                    }
                    if (i < list.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                }
            }
        }

        Column {
            FieldLabel(tr("ai.difficulty"))
            Segmented(listOf("mixed", "easy", "medium", "hard").map { it to tr("ai.diff.$it") }, ai.difficulty) { dv ->
                store.updateAi { a -> a.copy(difficulty = dv) }
            }
        }
        SwitchRow(tr("ai.answers"), tr("ai.answersHint"), ai.answers) { v -> store.updateAi { a -> a.copy(answers = v) } }
        SwitchRow(tr("ai.explanations"), tr("ai.explanationsHint"), ai.explanations) { v -> store.updateAi { a -> a.copy(explanations = v) } }
    }
}
