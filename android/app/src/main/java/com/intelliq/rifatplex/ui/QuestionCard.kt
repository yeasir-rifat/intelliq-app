package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
                            Icon(PIcons.DragIndicator, null, Modifier.size(20.dp), tint = if (dragging) c.primary else c.textFaint)
                        }
                    }
                    Text(
                        "${tr(if (kind == QType.MCQ) "label.mcq" else "label.q")} ${formatNumber(number, bn)}",
                        color = c.primaryInk, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = uiFont(), modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // like the website, the type drop-down exists on the Mix tab only
                    if (mix) TypeSelect(kind, { store.setType(q.id, it) }, Modifier.width(128.dp))
                    MarksField(
                        value = q.marks,
                        onChange = { store.setMarks(q.id, it) },
                        placeholder = marksHint,
                        modifier = Modifier.size(width = 56.dp, height = 38.dp),
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QcBtn(PIcons.TextFormat, false, toolbarOpen) { toolbarOpen = !toolbarOpen }
                if (kind == QType.MCQ) LayoutPicker(q.layout) { store.setLayout(q.id, it) }
                QcBtn(PIcons.KeyboardArrowUp, false, enabled = canMoveUp, onClick = onMoveUp)
                QcBtn(PIcons.KeyboardArrowDown, false, enabled = canMoveDown, onClick = onMoveDown)
                QcBtn(PIcons.ContentCopy, false) {
                    store.duplicateQuestion(q.id)
                    onToast(trKey("toast.questionDuplicated", lang))
                }
                QcBtn(PIcons.DeleteOutline, true) {
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
private fun LayoutPicker(current: String, onPick: (String) -> Unit) {
    val c = IQ.c
    val shape = IQD.card
    Row(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.borderStrong, shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayoutBtn(PIcons.ViewAgenda, current == "vertical") { onPick("vertical") }
        LayoutBtn(PIcons.GridView, current == "grid") { onPick("grid") }
        LayoutBtn(PIcons.ViewColumn, current == "horizontal") { onPick("horizontal") }
    }
}

@Composable
private fun LayoutBtn(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Box(
        Modifier
            .size(width = 30.dp, height = 34.dp)
            .clip(shape)
            .background(if (active) c.primarySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (active) c.primary else c.textSecondary)
    }
}

/** Tool button of the card (website `.qc-btn` on a phone): 36 x 40 with a border; disabled = faded. */
@Composable
private fun QcBtn(
    icon: ImageVector,
    danger: Boolean,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = IQ.c
    val shape = IQD.card
    Box(
        Modifier
            .size(width = 36.dp, height = 40.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(if (active) c.primarySoft else c.panel)
            .border(1.dp, if (active) c.primary else c.borderStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null, Modifier.size(18.dp),
            tint = if (danger) c.danger else if (active) c.primary else c.textSecondary,
        )
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
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            )
        }
        Box(Modifier.weight(1f)) {
            RichEditor(text, onText, tr("ph.option"), minHeight = 36.dp, onFocus = onFocus)
        }
    }
}
