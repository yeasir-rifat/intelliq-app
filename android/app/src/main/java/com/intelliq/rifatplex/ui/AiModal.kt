package com.intelliq.rifatplex.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            vm.setError("ai.err.noCamera")
        } catch (e: Exception) {
            vm.setError("ai.err.generic")
        }
    }

    val c = IQ.c
    val shape = RoundedCornerShape(16.dp)
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
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PIcons.AutoAwesome, null, Modifier.size(19.dp), tint = c.primary)
                    Text(tr("ai.title"), color = c.text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!vm.customPrompt) {
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
                            Text(tr("ai.noFileSelected"), color = c.textSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                            Column(Modifier.fillMaxWidth().widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AiBtn(tr("ai.capture"), PIcons.CameraAlt, BtnKind.Secondary, Modifier.fillMaxWidth()) { capture() }
                                AiBtn(tr("ai.upload"), PIcons.Upload, BtnKind.Secondary, Modifier.fillMaxWidth()) { pick() }
                            }
                            Text(tr("ai.fileHint"), color = c.textFaint, fontSize = 11.5.sp, fontFamily = uiFont())
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.files.forEach { f -> FileChip(f) { vm.removeFile(f.id) } }
                            AiBtn(tr("ai.addMore"), PIcons.Add, BtnKind.Secondary, Modifier.fillMaxWidth()) { pick() }
                        }
                    }
                }

                // custom prompt switch
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(PIcons.AutoFixHigh, null, Modifier.size(18.dp), tint = c.primary)
                        Column {
                            Text(tr("ai.customPrompt"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                            Text(tr("ai.customPromptHint"), color = c.textFaint, fontSize = 11.5.sp, fontFamily = uiFont())
                        }
                    }
                    IQSwitch(vm.customPrompt) { vm.setCustom(it) }
                }

                // instruction
                Column {
                    FieldLabel(tr(if (vm.customPrompt) "ai.instructionLabelTopic" else "ai.instructionLabel"))
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
                            fontSize = 12.5.sp, fontFamily = uiFont(),
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
                AiBtn(tr("ai.generateMcqs"), PIcons.AutoAwesome, BtnKind.Primary, Modifier.weight(1f), enabled = !vm.busy) {
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
    val shape = RoundedCornerShape(4.dp)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1)
    }
}

@Composable
private fun FileChip(f: AiFile, onRemove: () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(4.dp)
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
            Text(f.name, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = when {
                !f.done -> tr("ai.uploading")
                f.kind == AiKind.Text -> tr(if (f.source == "pptx") "ai.pptxExtracted" else "ai.docxExtracted")
                f.kind == AiKind.Pdf -> "PDF"
                else -> tr("ai.imageLabel")
            }
            Text(meta, color = c.textFaint, fontSize = 11.sp, fontFamily = uiFont(), maxLines = 1)
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(PIcons.Close, null, Modifier.size(16.dp), tint = c.textFaint) }
    }
}
