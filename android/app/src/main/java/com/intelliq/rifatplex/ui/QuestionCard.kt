package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.formatMarks
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.optionLabel
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.PIcons

/**
 * One question card of the editor. The header shows the number, the type drop-down (Mix tab only) and the marks box,
 * and below it the tools row. The body depends on the type (MCQ, CQ, T/F, Blanks, SQ, BQ).
 *
 * [number] is the number to show (section aware, see buildCardItems). [mix] = the Mix tab is active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(
    number: Int,
    q: Question,
    lang: Lang,
    store: AppStore,
    mix: Boolean,
    focused: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onFocus: () -> Unit,
    onToast: (String) -> Unit,
    /** Modifier of the drag handle (long drag = reorder). Null = no handle. */
    dragModifier: Modifier? = null,
    dragging: Boolean = false,
    /** Opens the AI Edit dialog of this question (website: AI Edit button in the first row of every card). */
    onAiEdit: () -> Unit = {},
) {
    val c = IQ.c
    val bn = lang == Lang.BN
    val kind = q.kind()
    val shape = IQD.card
    var toolbarOpen by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf<RichHandle?>(null) }
    val focusEditor: (RichHandle) -> Unit = {
        handle = it
        onFocus()
    }
    // A Creative question without its own marks shows the sum of its sub-question marks as the hint.
    val marksHint = if (kind == QType.CREATIVE && q.marks.isBlank()) {
        val sum = q.totalMarks()
        if (sum > 0.0) formatMarks(sum, false) else tr("t.marks")
    } else tr("t.marks")

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (dragging) 10.dp else 1.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, if (focused || dragging) c.primary else c.glassBorder, shape)
    ) {
        // ── head ──
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.panelSoft)
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // First row: number (left); type drop-down (Mix tab), marks and the AI Edit button (right corner).
            // On a narrow phone the type drop-down gives up some width (website: 96px under 400px) so nothing is pushed out.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val leftW = if (dragModifier != null) 104.dp else 70.dp
                val selectW = maxOf(84.dp, minOf(128.dp, maxWidth - leftW - 56.dp - 40.dp - 18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (dragModifier != null) {
                            Box(
                                dragModifier.size(width = 32.dp, height = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(PIcons.DragIndicator, null, Modifier.size(22.dp), tint = if (dragging) c.primary else c.textFaint)
                            }
                        }
                        Text(
                            "${tr(if (kind == QType.MCQ) "label.mcq" else "label.q")} ${formatNumber(number, bn)}",
                            color = c.primaryInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            fontFamily = uiFont(), modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // like the website, the type drop-down exists on the Mix tab only
                        if (mix) TypeSelect(kind, { store.setType(q.id, it) }, Modifier.width(selectW))
                        MarksField(
                            value = q.marks,
                            onChange = { store.setMarks(q.id, it) },
                            placeholder = marksHint,
                            modifier = Modifier.size(width = 56.dp, height = 38.dp),
                        )
                        AiEditBtn(onAiEdit)
                    }
                }
            }
            // Second row. MCQ: the tools share the full width (the layout picker makes the row nearly full anyway).
            // Other types: fixed-width tools packed to the right edge (right aligned).
            val wide = kind == QType.MCQ
            val toolW: (Float) -> Modifier = { w -> if (wide) Modifier.weight(w) else Modifier.width(40.dp) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (wide) Arrangement.spacedBy(6.dp) else Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                QcBtn(PIcons.TextFormat, false, toolbarOpen, modifier = toolW(1f)) { toolbarOpen = !toolbarOpen }
                if (wide) LayoutPicker(q.layout, Modifier.weight(2.6f)) { store.setLayout(q.id, it) }
                QcBtn(PIcons.KeyboardArrowUp, false, enabled = canMoveUp, modifier = toolW(1f), onClick = onMoveUp)
                QcBtn(PIcons.KeyboardArrowDown, false, enabled = canMoveDown, modifier = toolW(1f), onClick = onMoveDown)
                QcBtn(PIcons.ContentCopy, false, modifier = toolW(1f)) {
                    store.duplicateQuestion(q.id)
                    onToast(trKey("toast.questionDuplicated", lang))
                }
                QcBtn(PIcons.DeleteOutline, true, modifier = toolW(1f)) {
                    val cleared = store.deleteQuestion(q.id)
                    onToast(trKey(if (cleared) "toast.questionCleared" else "toast.questionDeleted", lang))
                }
            }
            if (toolbarOpen) {
                FormatToolbar(handle) { toolbarOpen = false }
            }
        }

        // ── body ──
        val creative = kind == QType.CREATIVE
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = if (creative) Arrangement.spacedBy(14.dp) else Arrangement.Top,
        ) {
            // per-question columns: Mix tab, non-MCQ only (website QCols)
            if (mix && kind != QType.MCQ) {
                QColRow(q, store, Modifier.padding(bottom = if (creative) 0.dp else 10.dp))
            }
            when (kind) {
                QType.TF -> TfBody(q, store, focusEditor)
                QType.FILL -> FillBody(q, store, focusEditor)
                QType.SHORT -> ShortBody(q, store, false, focusEditor)
                QType.DESC -> ShortBody(q, store, true, focusEditor)
                QType.CREATIVE -> CreativeBody(q, store, bn, focusEditor)
                else -> McqBody(q, store, bn, focusEditor)
            }
        }
    }
}

@Composable
private fun McqBody(q: Question, store: AppStore, bn: Boolean, onFocus: (RichHandle) -> Unit) {
    RichEditor(
        html = q.text,
        onHtml = { store.setQuestionText(q.id, it) },
        placeholder = tr("ph.question"),
        minHeight = 44.dp,
        onFocus = onFocus,
        boldBase = LocalBoldQuestions.current,
    )
    Box(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        q.options.forEachIndexed { i, opt ->
            OptionRow(
                label = optionLabel(i, bn),
                text = opt.text,
                correct = opt.correct,
                onText = { store.setOptionText(q.id, opt.id, it) },
                onToggle = { store.toggleCorrect(q.id, opt.id) },
                onFocus = onFocus,
            )
        }
    }
    if (q.hasCorrect()) {
        Box(Modifier.height(4.dp))
        LabeledBlock(
            label = tr("label.explanation"),
            placeholder = tr("ph.explain"),
            text = q.explanation,
            onText = { store.setExplanation(q.id, it) },
            onFocus = onFocus,
        )
    }
}

/** The three option layouts of an MCQ (vertical / rectangular / horizontal). */
@Composable
private fun LayoutPicker(current: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val c = IQ.c
    val shape = IQD.card
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.borderStrong, shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayoutBtn(PIcons.ViewAgenda, current == "vertical", Modifier.weight(1f)) { onPick("vertical") }
        LayoutBtn(PIcons.GridView, current == "grid", Modifier.weight(1f)) { onPick("grid") }
        LayoutBtn(PIcons.ViewColumn, current == "horizontal", Modifier.weight(1f)) { onPick("horizontal") }
    }
}

@Composable
private fun LayoutBtn(icon: ImageVector, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Box(
        modifier
            .height(34.dp)
            .clip(shape)
            .background(if (active) c.primarySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (active) c.primary else c.textSecondary)
    }
}

/** Tool button of the card (website `.qc-btn`): 40 high with a border, as wide as [modifier] makes it; disabled = faded. */
@Composable
private fun QcBtn(
    icon: ImageVector,
    danger: Boolean,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = IQ.c
    val shape = IQD.card
    Box(
        modifier
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(if (active) c.primarySoft else c.panel)
            .border(1.dp, if (active) c.primary else c.borderStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null, Modifier.size(20.dp),
            tint = if (danger) c.danger else if (active) c.primary else c.textSecondary,
        )
    }
}

/** The AI Edit button in the right corner of the first row (website `.iq-aiedit-btn`, icon only on a phone). */
@Composable
private fun AiEditBtn(onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Box(
        Modifier
            .size(width = 40.dp, height = 38.dp)
            .clip(shape)
            .background(c.primarySoft)
            .border(1.dp, c.primary.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PIcons.AutoAwesome, tr("aie.btnTitle"), Modifier.size(20.dp), tint = c.primary)
    }
}

@Composable
private fun OptionRow(
    label: String,
    text: String,
    correct: Boolean,
    onText: (String) -> Unit,
    onToggle: () -> Unit,
    onFocus: (RichHandle) -> Unit,
) {
    val c = IQ.c
    val shape = IQD.ctl
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (correct) c.success.copy(alpha = 0.08f) else Color.Transparent)
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (correct) c.success else c.field)
                .border(1.5.dp, if (correct) c.success else c.borderStrong, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (correct) Color.White else c.textSecondary,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            )
        }
        Box(Modifier.weight(1f)) {
            RichEditor(text, onText, tr("ph.option"), minHeight = 36.dp, onFocus = onFocus)
        }
    }
}
