package com.intelliq.rifatplex.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.optionLabel
import com.intelliq.rifatplex.ui.theme.IQ

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(
    index: Int,
    q: Question,
    lang: Lang,
    store: AppStore,
    focused: Boolean,
    onFocus: () -> Unit,
    onToast: (String) -> Unit,
) {
    val c = IQ.c
    val bn = lang == Lang.BN
    val shape = RoundedCornerShape(8.dp)
    var toolbarOpen by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf<RichHandle?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, if (focused) c.primary else c.glassBorder, shape)
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
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${tr("label.mcq")} ${formatNumber(index + 1, bn)}",
                    color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = uiFont(), modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    QcBtn(Icons.Outlined.TextFormat, false, toolbarOpen) { toolbarOpen = !toolbarOpen }
                    LayoutPicker(q.layout) { store.setLayout(q.id, it) }
                    Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(20.dp).background(c.border))
                    QcBtn(Icons.Outlined.KeyboardArrowUp, false) { store.moveQuestion(q.id, -1) }
                    QcBtn(Icons.Outlined.KeyboardArrowDown, false) { store.moveQuestion(q.id, 1) }
                    QcBtn(Icons.Outlined.ContentCopy, false) {
                        store.duplicateQuestion(q.id)
                        onToast(trKey("toast.questionDuplicated", lang))
                    }
                    QcBtn(Icons.Outlined.DeleteOutline, true) {
                        val cleared = store.deleteQuestion(q.id)
                        onToast(trKey(if (cleared) "toast.questionCleared" else "toast.questionDeleted", lang))
                    }
                }
            }
            if (toolbarOpen) {
                Box(Modifier.padding(top = 8.dp)) { FormatToolbar(handle) { toolbarOpen = false } }
            }
        }

        // ── body ──
        Column(Modifier.padding(14.dp)) {
            RichEditor(
                html = q.text,
                onHtml = { store.setQuestionText(q.id, it) },
                placeholder = tr("ph.question"),
                minHeight = 44.dp,
                onFocus = { handle = it; onFocus() },
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
                        onFocus = { handle = it; onFocus() },
                    )
                }
            }
            if (q.hasCorrect()) {
                Box(Modifier.height(4.dp))
                ExplanationBox(q.explanation, { store.setExplanation(q.id, it) }) { handle = it; onFocus() }
            }
        }
    }
}

@Composable
private fun LayoutPicker(current: String, onPick: (String) -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(7.dp)
    Row(
        Modifier
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.glassBorder, shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayoutBtn(Icons.Outlined.ViewAgenda, current == "vertical") { onPick("vertical") }
        LayoutBtn(Icons.Outlined.GridView, current == "grid") { onPick("grid") }
        LayoutBtn(Icons.Outlined.ViewColumn, current == "horizontal") { onPick("horizontal") }
    }
}

@Composable
private fun LayoutBtn(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .size(width = 26.dp, height = 24.dp)
            .then(if (active) Modifier.shadow(1.dp, shape) else Modifier)
            .clip(shape)
            .background(if (active) c.panel else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = if (active) c.primary else c.textFaint)
    }
}

@Composable
private fun QcBtn(icon: ImageVector, danger: Boolean, active: Boolean = false, onClick: () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) c.primarySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = if (danger) c.danger else if (active) c.primary else c.textFaint)
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
    val shape = RoundedCornerShape(4.dp)
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

@Composable
private fun ExplanationBox(text: String, onText: (String) -> Unit, onFocus: (RichHandle) -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.success.copy(alpha = 0.06f))
            .drawBehind {
                drawRoundRect(
                    color = c.success,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val lbl = tr("label.explanation")
        Text(
            if (LocalLang.current == Lang.BN) lbl else lbl.uppercase(),
            color = c.success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.33.sp, fontFamily = uiFont(),
        )
        RichEditor(text, onText, tr("ph.explain"), minHeight = 32.dp, onFocus = onFocus)
    }
}
