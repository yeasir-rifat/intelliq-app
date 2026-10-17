package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.foundation.background
import com.intelliq.rifatplex.ui.theme.PIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.intelliq.rifatplex.data.Folder as FolderData
import com.intelliq.rifatplex.data.HistoryEntry
import com.intelliq.rifatplex.data.HistoryStore
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.hasMcq
import com.intelliq.rifatplex.data.hasMcqKey
import com.intelliq.rifatplex.ui.theme.IQ
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BN_MONTHS = listOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
)

/** Same wording as the website: "১৪ অক্টোবর ২০২৬ · দুপুর ২:০৫" / "14 Oct 2026 · 02:05 PM". */
fun formatDateTime(millis: Long, lang: Lang): String {
    if (millis <= 0L) return ""
    return try {
        if (lang == Lang.BN) {
            val cal = Calendar.getInstance().apply { time = Date(millis) }
            val h24 = cal.get(Calendar.HOUR_OF_DAY)
            val period = when {
                h24 < 12 -> "সকাল"
                h24 < 17 -> "দুপুর"
                h24 < 20 -> "সন্ধ্যা"
                else -> "রাত"
            }
            val h12 = (h24 % 12).let { if (it == 0) 12 else it }
            "${formatNumber(cal.get(Calendar.DAY_OF_MONTH), true)} ${BN_MONTHS[cal.get(Calendar.MONTH)]} " +
                "${formatNumber(cal.get(Calendar.YEAR), true)} · $period ${formatNumber(h12, true)}:${formatNumber(cal.get(Calendar.MINUTE), true)}"
        } else {
            SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.US).format(Date(millis))
        }
    } catch (e: Exception) {
        ""
    }
}

/** The website's History modal: folders (collapsible) + ungrouped papers, each with Download / Edit / Delete. */
@Composable
fun HistoryModal(
    open: Boolean,
    onClose: () -> Unit,
    history: HistoryStore,
    lang: Lang,
    onEdit: (HistoryEntry) -> Unit,
    onDownload: (HistoryEntry, DocKind) -> Unit,
    onToast: (String, Boolean) -> Unit,
) {
    if (!open) return
    val c = IQ.c
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }
    var renaming by remember { mutableStateOf<FolderData?>(null) }
    var openFolders by remember { mutableStateOf(setOf<String>()) }
    val shape = IQD.dialog
    val maxH = LocalConfiguration.current.screenHeightDp.dp - 48.dp

    val entries = history.entries
    val folders = history.folders
    val ungrouped = entries.filter { e -> e.folderId == null || folders.none { it.id == e.folderId } }
    val nonEmptyFolders = folders.filter { f -> entries.any { it.folderId == f.id } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 10.dp, vertical = 24.dp)
                .fillMaxWidth()
                .heightIn(max = maxH)
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.glassBorder, shape),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(IQD.ctl).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(PIcons.History, null, Modifier.size(16.dp), tint = c.primary)
                    }
                    Text(tr("history.title"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                }
                SquareBtn(40.dp, onClose) { Icon(PIcons.Close, null, Modifier.size(16.dp), tint = c.textSecondary) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

            if (entries.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(52.dp).clip(IQD.card).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(PIcons.Inbox, null, Modifier.size(24.dp), tint = c.primary)
                    }
                    Text(tr("history.emptyTitle"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                    Text(tr("history.emptySub"), color = c.textFaint, fontSize = 13.sp, fontFamily = uiFont())
                }
            } else {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (nonEmptyFolders.isNotEmpty()) {
                        SectionLabel(tr("history.foldersSection"))
                        nonEmptyFolders.forEach { folder ->
                            val items = entries.filter { it.folderId == folder.id }
                            val isOpen = folder.id in openFolders
                            FolderHead(
                                folder, items.size, isOpen,
                                onToggle = { openFolders = if (isOpen) openFolders - folder.id else openFolders + folder.id },
                                onRename = { renaming = folder },
                            )
                            if (isOpen) {
                                Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items.forEach { e ->
                                        HistoryItem(e, lang, { onEdit(e) }, { kind -> onDownload(e, kind) }, { deleting = e })
                                    }
                                }
                            }
                        }
                    }
                    if (ungrouped.isNotEmpty()) {
                        if (nonEmptyFolders.isNotEmpty()) SectionLabel(tr("history.ungroupedSection"))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ungrouped.forEach { e ->
                                HistoryItem(e, lang, { onEdit(e) }, { kind -> onDownload(e, kind) }, { deleting = e })
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { e ->
        ConfirmDialog(
            message = tr("history.deleteConfirm"),
            confirmLabel = tr("history.delete"),
            onConfirm = { history.delete(e.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    renaming?.let { f ->
        NameDialog(
            title = tr("folder.rename"),
            initial = f.name,
            confirmLabel = tr("common.save"),
            onConfirm = { name ->
                history.renameFolder(f.id, name)
                renaming = null
                onToast(trKey("folder.renamedToast", lang), false)
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = IQ.c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, fontFamily = uiFont(),
    )
}

@Composable
private fun FolderHead(folder: FolderData, count: Int, open: Boolean, onToggle: () -> Unit, onRename: () -> Unit) {
    val c = IQ.c
    val shape = IQD.card
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.border, shape)
            .clickable { onToggle() }
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(28.dp).clip(IQD.ctl).background(c.primarySoft), contentAlignment = Alignment.Center) {
            Icon(PIcons.Folder, null, Modifier.size(16.dp), tint = c.primary)
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                folder.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val bn = LocalLang.current == Lang.BN
            Text(
                "${formatNumber(count, bn)} ${tr(if (count == 1) "history.entrySingular" else "history.entryPlural")}",
                color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont(),
            )
        }
        IconAction(PIcons.Edit, false, onRename)
        Icon(if (open) PIcons.ExpandLess else PIcons.ExpandMore, null, Modifier.padding(end = 8.dp).size(20.dp), tint = c.textFaint)
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val c = IQ.c
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(18.dp), tint = if (danger) c.danger else c.textSecondary) }
}

@Composable
private fun Badge(text: String) {
    val c = IQ.c
    Text(
        text,
        Modifier.clip(IQD.ctl).background(c.primarySoft).padding(horizontal = 8.dp, vertical = 3.dp),
        color = c.primaryInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
    )
}

@Composable
private fun HistoryItem(
    e: HistoryEntry,
    lang: Lang,
    onEdit: () -> Unit,
    onDownload: (DocKind) -> Unit,
    onDelete: () -> Unit,
) {
    val c = IQ.c
    val shape = IQD.card
    val bn = lang == Lang.BN
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.glassBorder, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("${formatNumber(e.questionCount, bn)} ${trKey(if (e.questionCount == 1) "history.itemsSingular" else "history.itemsPlural", lang)}")
            Badge(formatDateTime(e.updatedAt, lang))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(IQD.ctl).background(c.primarySoft), contentAlignment = Alignment.Center) {
                Icon(PIcons.Description, null, Modifier.size(18.dp), tint = c.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    e.title.ifEmpty { tr("history.untitled") },
                    color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (e.subtitle.isNotEmpty()) {
                    Text(
                        e.subtitle, color = c.textFaint, fontSize = 13.sp, fontFamily = uiFont(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconAction(PIcons.Download, false) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(PIcons.Description, null, Modifier.size(17.dp), tint = c.textSecondary) },
                        text = { Text(tr("history.downloadQuestion"), color = c.text, fontSize = 14.sp, fontFamily = uiFont()) },
                        onClick = { menu = false; onDownload(DocKind.QUESTION) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(PIcons.CheckCircle, null, Modifier.size(17.dp), tint = c.textSecondary) },
                        text = { Text(tr("history.downloadAnswer"), color = c.text, fontSize = 14.sp, fontFamily = uiFont()) },
                        onClick = { menu = false; onDownload(DocKind.ANSWER) },
                    )
                    if (e.state.hasMcq()) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(PIcons.GridView, null, Modifier.size(17.dp), tint = c.textSecondary) },
                            text = { Text(tr("history.downloadOmr"), color = c.text, fontSize = 14.sp, fontFamily = uiFont()) },
                            onClick = { menu = false; onDownload(DocKind.OMR) },
                        )
                    }
                    if (e.state.hasMcqKey()) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(PIcons.CheckCircle, null, Modifier.size(17.dp), tint = c.textSecondary) },
                            text = { Text(tr("history.downloadKeySheet"), color = c.text, fontSize = 14.sp, fontFamily = uiFont()) },
                            onClick = { menu = false; onDownload(DocKind.KEY) },
                        )
                    }
                }
            }
            IconAction(PIcons.Edit, false, onEdit)
            IconAction(PIcons.DeleteOutline, true, onDelete)
        }
    }
}

/** Small centered dialog with one text field (new folder / rename folder). */
@Composable
fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val c = IQ.c
    var text by remember { mutableStateOf(initial) }
    val shape = IQD.dialog
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.panel).border(1.dp, c.glassBorder, shape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            InputBox(text, { if (it.length <= 60) text = it }, tr("folder.newFolderPlaceholder"), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                IQButton(tr("common.cancel"), BtnKind.Ghost) { onDismiss() }
                IQButton(confirmLabel, BtnKind.Primary) { if (text.isNotBlank()) onConfirm(text.trim()) }
            }
        }
    }
}

@Composable
fun ConfirmDialog(message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = IQ.c
    val shape = IQD.dialog
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.panel).border(1.dp, c.glassBorder, shape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, color = c.text, fontSize = 15.sp, fontFamily = uiFont())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                IQButton(tr("common.cancel"), BtnKind.Ghost) { onDismiss() }
                IQButton(confirmLabel, BtnKind.Primary) { onConfirm() }
            }
        }
    }
}
