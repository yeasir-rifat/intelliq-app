package com.intelliq.rifatplex.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.aiEditNorm
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.optionLabel
import com.intelliq.rifatplex.data.plainText
import com.intelliq.rifatplex.data.subLabel
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD
import com.intelliq.rifatplex.ui.theme.PIcons

/** One field of a question as the result view shows it (website `describe`). */
private class EditBlock(
    val key: String,
    val label: String,
    val html: String,
    val correct: Boolean = false,
    /** Shown as plain text (T/F answer), not as rich text. */
    val plain: Boolean = false,
)

/** A block of the proposed version, the same block of the old version, and whether it changed. */
private class EditDiff(val block: EditBlock, val old: EditBlock?, val changed: Boolean)

private fun describe(q: Question, lang: Lang): List<EditBlock> {
    val bn = lang == Lang.BN
    fun t(k: String) = trKey(k, lang)
    val out = ArrayList<EditBlock>()
    val kind = q.kind()
    out.add(EditBlock("q", t(if (kind == QType.TF) "aie.statement" else if (kind == QType.CREATIVE) "aie.stimulus" else "aie.question"), q.text))
    when (kind) {
        QType.MCQ -> {
            q.options.forEachIndexed { i, o ->
                out.add(EditBlock("o$i", t("aie.option") + " " + optionLabel(i, bn), o.text, correct = o.correct))
            }
            out.add(EditBlock("expl", t("aie.explanation"), q.explanation))
        }
        QType.TF -> {
            val a = when (q.tfAnswer) {
                true -> t("aie.tfTrue")
                false -> t("aie.tfFalse")
                null -> ""
            }
            out.add(EditBlock("ans", t("aie.answer"), a, plain = true))
            out.add(EditBlock("expl", t("aie.explanation"), q.explanation))
        }
        QType.CREATIVE -> {
            q.subs.forEachIndexed { i, s ->
                val marks = if (s.marks.isNotBlank()) " [" + s.marks + "]" else ""
                out.add(EditBlock("s$i", t("aie.sub2") + " " + subLabel(i, bn) + marks, s.text))
                out.add(EditBlock("a$i", t("aie.answer") + " (" + subLabel(i, bn) + ")", s.answer))
            }
        }
        else -> out.add(EditBlock("ans", t("aie.answer"), q.answer))
    }
    return out
}

private fun hasContent(b: EditBlock?): Boolean = b != null && (b.correct || (if (b.plain) b.html.isNotBlank() else plainText(b.html).isNotBlank()))

/** Blocks of the proposed version that are worth showing, each marked changed / unchanged (website result view). */
private fun diff(before: List<EditBlock>, after: List<EditBlock>): List<EditDiff> {
    val map = before.associateBy { it.key }
    val out = ArrayList<EditDiff>()
    for (b in after) {
        val o = map[b.key]
        if (!hasContent(b) && !hasContent(o)) continue
        val changed = o == null || aiEditNorm(o.html, o.plain, o.correct) != aiEditNorm(b.html, b.plain, b.correct)
        out.add(EditDiff(b, o, changed))
    }
    return out
}

private fun actionIcon(k: String): ImageVector = when (k) {
    "fix" -> PIcons.Check
    "polish" -> PIcons.AutoFixHigh
    "change" -> PIcons.RestartAlt
    else -> PIcons.Description
}

/**
 * "AI Edit" dialog (website `iq-aie`). Form: what to do + optional instruction. Result: the AI's version with the
 * changed fields marked, then Apply (with an Undo toast) or Try again.
 */
@Composable
fun AiEditDialog(
    vm: AiEditViewModel,
    store: AppStore,
    lang: Lang,
    onUndoToast: (text: String, action: String, onAction: () -> Unit) -> Unit,
    onToast: (String, Boolean) -> Unit,
) {
    val id = vm.qid ?: return
    val q = store.data.questions.firstOrNull { it.id == id }?.ensured()
    if (q == null) {
        LaunchedEffect(Unit) { vm.close() }
        return
    }
    val c = IQ.c
    val shape = IQD.dialog
    val bn = lang == Lang.BN

    val diffs = remember(vm.prop, q) { vm.prop?.let { diff(describe(q, lang), describe(it, lang)) } ?: emptyList() }
    val changedCount = diffs.count { it.changed }

    Dialog(onDismissRequest = { vm.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
                        Text(tr("aie.title"), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                        Text(
                            tr("aie.sub") + " " + formatNumber(vm.number, bn) + " \u00B7 " + tr("t.type." + q.kind()),
                            color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { vm.close() },
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
                when (vm.view) {
                    "loading" -> LoadingView()
                    "result" -> {
                        Text(tr("aie.resTitle"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                        val note = vm.summary.ifBlank { if (changedCount == 0) tr("aie.noChange") else "" }
                        if (note.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth().clip(IQD.card).background(c.primarySoft).padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(PIcons.AutoAwesome, null, Modifier.size(16.dp), tint = c.primary)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(tr("aie.changes"), color = c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                                    Text(note, color = c.text, fontSize = 14.sp, fontFamily = uiFont())
                                }
                            }
                        }
                        Text(tr("aie.resHint"), color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
                        diffs.forEach { ResultBlock(it) }
                    }
                    else -> {
                        // current question
                        val snippet = run {
                            val own = plainText(q.text).replace(Regex("\\s+"), " ").trim()
                            if (own.isNotEmpty()) own else q.subs.firstOrNull()?.let { plainText(it.text).replace(Regex("\\s+"), " ").trim() }.orEmpty()
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(IQD.card).background(c.panelSoft).border(1.dp, c.border, IQD.card).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(tr("aie.current"), color = c.textFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                            Text(
                                if (snippet.length > 160) snippet.take(160) + "\u2026" else snippet,
                                color = c.textSecondary, fontSize = 14.sp, fontFamily = uiFont(), maxLines = 3, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FieldLabel(tr("aie.whatTodo"))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("fix", "polish", "change", "explain").forEach { k ->
                                EditActionChip(actionIcon(k), tr("aie.act.$k"), tr("aie.act.$k.hint"), k in vm.sel) { vm.toggle(k) }
                            }
                        }
                        FieldLabel(tr("aie.instrLabel"))
                        InputBox(vm.instr, { vm.instr = it }, tr("aie.instrPh"), minLines = 3)
                        val err = if (vm.errKey.isNotEmpty()) tr(vm.errKey) else vm.errRaw
                        if (err.isNotBlank()) {
                            Text(
                                err,
                                Modifier.fillMaxWidth().clip(IQD.ctl).background(c.dangerSoft).padding(horizontal = 12.dp, vertical = 9.dp),
                                color = c.danger, fontSize = 13.sp, fontFamily = uiFont(),
                            )
                        }
                    }
                }
            }

            // foot
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                Modifier.fillMaxWidth().background(c.panelSoft).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (vm.view) {
                    "loading" -> DlgBtn(tr("common.cancel"), null, BtnKind.Ghost, Modifier.weight(1f)) { vm.close() }
                    "result" -> {
                        DlgBtn(tr("common.cancel"), null, BtnKind.Ghost, Modifier.weight(0.8f)) { vm.close() }
                        DlgBtn(tr("aie.retry"), PIcons.RestartAlt, BtnKind.Secondary, Modifier.weight(1.1f)) { vm.again() }
                        if (changedCount > 0) {
                            DlgBtn(tr("aie.apply"), PIcons.Check, BtnKind.Primary, Modifier.weight(1.1f)) {
                                val backup = vm.apply(store)
                                if (backup != null) {
                                    onUndoToast(trKey("aie.applied", lang), trKey("aie.undo", lang)) {
                                        vm.restore(store, backup)
                                        onToast(trKey("aie.undone", lang), false)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        DlgBtn(tr("common.cancel"), null, BtnKind.Ghost, Modifier.weight(0.8f)) { vm.close() }
                        DlgBtn(tr("aie.run"), PIcons.AutoAwesome, BtnKind.Primary, Modifier.weight(1.6f)) { vm.run(store) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    val c = IQ.c
    Column(
        Modifier.fillMaxWidth().padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(34.dp), color = c.primary, strokeWidth = 3.dp)
        Text(tr("aie.loading"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
        Text(tr("aie.loadingSub"), color = c.textFaint, fontSize = 13.sp, fontFamily = uiFont())
    }
}

@Composable
private fun EditActionChip(icon: ImageVector, title: String, hint: String, on: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.card
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (on) c.primarySoft else c.field)
            .border(if (on) 1.5.dp else 1.dp, if (on) c.primary else c.glassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(IQD.ctl).background(if (on) c.primary else c.primarySoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(18.dp), tint = if (on) Color.White else c.primary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            Text(hint, color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont())
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (on) c.primary else Color.Transparent)
                .border(1.5.dp, if (on) c.primary else c.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(PIcons.Check, null, Modifier.size(14.dp), tint = Color.White) }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(Modifier.clip(IQD.ctl).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(), maxLines = 1)
    }
}

@Composable
private fun ResultBlock(d: EditDiff) {
    val c = IQ.c
    val shape = IQD.card
    val b = d.block
    var showOld by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(if (d.changed) c.primarySoft else c.field)
            .border(if (d.changed) 1.5.dp else 1.dp, if (d.changed) c.primary else c.border, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                b.label, Modifier.weight(1f),
                color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (b.correct) Tag("\u2713 " + tr("aie.correct"), c.successInk)
            if (d.changed) Tag(tr("aie.changed"), c.primaryInk)
        }
        BlockText(b)
        val old = d.old
        if (d.changed && hasContent(old)) {
            Text(
                tr("aie.before") + (if (showOld) "  \u25B4" else "  \u25BE"),
                Modifier.clip(IQD.ctl).clickable { showOld = !showOld }.padding(vertical = 3.dp),
                color = c.primaryInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            )
            if (showOld && old != null) {
                Box(Modifier.fillMaxWidth().clip(IQD.ctl).background(c.panel).border(1.dp, c.border, IQD.ctl).padding(10.dp).alpha(0.9f)) {
                    BlockText(old)
                }
            }
        }
    }
}

@Composable
private fun BlockText(b: EditBlock) {
    val c = IQ.c
    if (b.plain) {
        Text(b.html.ifBlank { "\u2014" }, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
    } else if (plainText(b.html).isBlank()) {
        Text("\u2014", color = c.textFaint, fontSize = 14.sp, fontFamily = uiFont())
    } else {
        RichTextView(b.html, color = c.text, fontFamily = uiFont(), fontSize = 14.sp, lineHeight = 22.sp)
    }
}

/** Compact dialog button (three of them must fit in a row on a 360dp phone). */
@Composable
private fun DlgBtn(text: String, icon: ImageVector?, kind: BtnKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    val bg = when (kind) {
        BtnKind.Primary -> c.primary
        BtnKind.Secondary -> c.primarySoft
        BtnKind.Ghost -> Color.Transparent
    }
    val fg = when (kind) {
        BtnKind.Primary -> Color.White
        BtnKind.Secondary -> c.primary
        BtnKind.Ghost -> c.textSecondary
    }
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (kind == BtnKind.Ghost) Modifier.border(1.dp, c.border, shape) else Modifier)
            .clickable(onClick = onClick)
            .heightIn(min = IQD.fieldH)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
