package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import com.intelliq.rifatplex.ui.theme.PIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.SubQuestion
import com.intelliq.rifatplex.data.localizeDigits
import com.intelliq.rifatplex.data.parseMarks
import com.intelliq.rifatplex.data.subQuestionLabel
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.optionLabel
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQRadius

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(
    number: Int,
    q: Question,
    lang: Lang,
    store: AppStore,
    focused: Boolean,
    onFocus: () -> Unit,
    onToast: (String) -> Unit,
    onMove: (Int) -> Unit,
) {
    val c = IQ.c
    val bn = lang == Lang.BN
    val compact = LocalConfiguration.current.screenWidthDp <= 720
    val shape = RoundedCornerShape(IQRadius.md)
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
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 10.dp)
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${categoryCardLabel(q.category, lang)} ${formatNumber(number, bn)}",
                    color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = uiFont(), modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    QcBtn(PIcons.TextFormat, false, toolbarOpen) { toolbarOpen = !toolbarOpen }
                    if (q.category == "mcq") LayoutPicker(q.layout) { store.setLayout(q.id, it) }
                    Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(20.dp).background(c.border))
                    QcBtn(PIcons.KeyboardArrowUp, false) { onMove(-1) }
                    QcBtn(PIcons.KeyboardArrowDown, false) { onMove(1) }
                    QcBtn(PIcons.ContentCopy, false) {
                        store.duplicateQuestion(q.id)
                        onToast(trKey("toast.questionDuplicated", lang))
                    }
                    QcBtn(PIcons.DeleteOutline, true) {
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
        val onHandle: (RichHandle) -> Unit = { handle = it; onFocus() }
        Column(Modifier.padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 9.dp else 14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (bn) "মান" else "Marks", color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont(), modifier = Modifier.padding(end = 8.dp))
                InputBox(
                    value = q.marks,
                    onChange = { value -> store.setQuestionMarks(q.id, value.filter { it.isDigit() || it == '.' || it in '০'..'৯' }.take(12)) },
                    placeholder = if (bn) "যেমন ৫" else "e.g. 5",
                    modifier = Modifier.width(84.dp),
                    singleLine = true,
                )
            }
            when (q.category) {
                "tf" -> TfBody(q, store, onHandle)
                "fill" -> FillBody(q, store, onHandle)
                "short", "desc" -> AnswerBody(q, store, onHandle)
                "creative" -> CreativeBody(q, store, bn, onHandle)
                else -> McqBody(q, store, bn, onHandle)
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
        LayoutBtn(PIcons.ViewAgenda, current == "vertical") { onPick("vertical") }
        LayoutBtn(PIcons.GridView, current == "grid") { onPick("grid") }
        LayoutBtn(PIcons.ViewColumn, current == "horizontal") { onPick("horizontal") }
    }
}

@Composable
private fun LayoutBtn(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val compact = LocalConfiguration.current.screenWidthDp <= 720
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .size(width = if (compact) 28.dp else 26.dp, height = if (compact) 32.dp else 24.dp)
            .then(if (active) Modifier.shadow(1.dp, shape) else Modifier)
            .clip(shape)
            .background(if (active) c.panel else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(if (compact) 16.dp else 14.dp), tint = if (active) c.primary else c.textFaint)
    }
}

@Composable
private fun QcBtn(icon: ImageVector, danger: Boolean, active: Boolean = false, onClick: () -> Unit) {
    val c = IQ.c
    val compact = LocalConfiguration.current.screenWidthDp <= 720
    val veryCompact = LocalConfiguration.current.screenWidthDp <= 400
    Box(
        Modifier
            .size(width = if (veryCompact) 32.dp else if (compact) 36.dp else 40.dp, height = if (veryCompact) 34.dp else if (compact) 38.dp else 40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) c.primarySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(if (compact) 17.dp else 20.dp), tint = if (danger) c.danger else if (active) c.primary else c.textFaint)
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

// ── Parity Step 5: one body per question type, following the website's editor ──

@Composable
private fun McqBody(q: Question, store: AppStore, bn: Boolean, onHandle: (RichHandle) -> Unit) {
    Column {
        RichEditor(
            html = q.text,
            onHtml = { store.setQuestionText(q.id, it) },
            placeholder = tr("ph.question"),
            minHeight = 44.dp,
            onFocus = onHandle,
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
                    onFocus = onHandle,
                )
            }
        }
        if (q.hasCorrect()) {
            Box(Modifier.height(4.dp))
            ExplanationBox(q.explanation, { store.setExplanation(q.id, it) }, onHandle)
        }
    }
}

/** T/F: a statement, a True / False choice (tap again to clear), and an explanation once a choice is made. */
@Composable
private fun TfBody(q: Question, store: AppStore, onHandle: (RichHandle) -> Unit) {
    Column {
        RichEditor(
            html = q.text,
            onHtml = { store.setQuestionText(q.id, it) },
            placeholder = tr("ph.tfStatement"),
            minHeight = 44.dp,
            onFocus = onHandle,
        )
        Box(Modifier.height(12.dp))
        TfChoice(q.tfAnswer) { store.setTfAnswer(q.id, it) }
        if (q.tfAnswer != null) {
            Box(Modifier.height(4.dp))
            ExplanationBox(q.explanation, { store.setExplanation(q.id, it) }, onHandle)
        }
    }
}

@Composable
private fun TfChoice(current: Boolean?, onPick: (Boolean?) -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(IQRadius.md)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true, false).forEach { value ->
            val on = current == value
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shape)
                    .background(if (on) c.success else c.field)
                    .border(1.dp, if (on) c.success else c.borderStrong, shape)
                    .clickable { onPick(if (on) null else value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr(if (value) "tf.true" else "tf.false"),
                    color = if (on) Color.White else c.textSecondary,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                )
            }
        }
    }
}

/** Blanks: the question with an "Insert blank" button (puts _____ at the cursor), then the expected answer. */
@Composable
private fun FillBody(q: Question, store: AppStore, onHandle: (RichHandle) -> Unit) {
    val c = IQ.c
    var questionHandle by remember { mutableStateOf<RichHandle?>(null) }
    Column {
        RichEditor(
            html = q.text,
            onHtml = { store.setQuestionText(q.id, it) },
            placeholder = tr("ph.fill"),
            minHeight = 44.dp,
            onFocus = { questionHandle = it; onHandle(it) },
        )
        Box(Modifier.height(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(IQRadius.sm))
                .background(c.primarySoft)
                .clickable { questionHandle?.insert(" _____ ") }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "+ " + tr("btn.insertBlank"),
                color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            )
        }
        Box(Modifier.height(12.dp))
        NoteBox(tr("lbl.blankAnswer"), c.primary, q.answer, tr("ph.blankAnswer"), { store.setAnswer(q.id, it) }, onHandle)
    }
}

/** SQ and BQ: the question and its answer (BQ uses the descriptive placeholder). */
@Composable
private fun AnswerBody(q: Question, store: AppStore, onHandle: (RichHandle) -> Unit) {
    val c = IQ.c
    Column {
        RichEditor(
            html = q.text,
            onHtml = { store.setQuestionText(q.id, it) },
            placeholder = tr("ph.question"),
            minHeight = 44.dp,
            onFocus = onHandle,
        )
        Box(Modifier.height(12.dp))
        NoteBox(
            tr("lbl.answer"), c.primary, q.answer,
            tr(if (q.category == "desc") "ph.descAnswer" else "ph.shortAnswer"),
            { store.setAnswer(q.id, it) }, onHandle,
        )
    }
}

/** CQ: a stimulus, then sub-questions (label, level, marks, text, answer), then add button and total marks. */
@Composable
private fun CreativeBody(q: Question, store: AppStore, bn: Boolean, onHandle: (RichHandle) -> Unit) {
    val c = IQ.c
    Column {
        CqSectionLabel(tr("lbl.stimulus"))
        RichEditor(
            html = q.text,
            onHtml = { store.setQuestionText(q.id, it) },
            placeholder = tr("ph.stimulus"),
            minHeight = 44.dp,
            onFocus = onHandle,
        )
        Box(Modifier.height(12.dp))
        q.subQuestions.forEachIndexed { i, sub ->
            SubQuestionRow(store, q.id, i, sub, bn, onHandle)
            Box(Modifier.height(10.dp))
        }
        val total = q.subQuestions.sumOf { parseMarks(it.marks) }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(IQRadius.sm))
                    .background(c.primarySoft)
                    .clickable { store.addSubQuestion(q.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "+ " + tr("btn.addSub"),
                    color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                )
            }
            Text(
                tr("lbl.totalMarks") + ": " + localizeDigits(formatMarks(total), bn),
                color = c.textFaint, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            )
        }
    }
}

@Composable
private fun SubQuestionRow(
    store: AppStore,
    qid: String,
    index: Int,
    sub: SubQuestion,
    bn: Boolean,
    onHandle: (RichHandle) -> Unit,
) {
    val c = IQ.c
    val shape = RoundedCornerShape(IQRadius.md)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.glassBorder, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                subQuestionLabel(index, bn),
                color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
            )
            Text(
                tr("level.${index % 4}"),
                modifier = Modifier.weight(1f),
                color = c.textFaint, fontSize = 11.5.sp, fontFamily = uiFont(),
            )
            Text(tr("lbl.marks"), color = c.textFaint, fontSize = 11.5.sp, fontFamily = uiFont())
            MarksField(sub.marks) { m -> store.setSubQuestion(qid, sub.id) { it.copy(marks = m) } }
            QcBtn(PIcons.DeleteOutline, true) { store.removeSubQuestion(qid, sub.id) }
        }
        RichEditor(
            html = sub.text,
            onHtml = { t -> store.setSubQuestion(qid, sub.id) { it.copy(text = t) } },
            placeholder = tr("ph.subQuestion"),
            minHeight = 36.dp,
            onFocus = onHandle,
        )
        NoteBox(
            tr("lbl.answer"), c.primary, sub.answer, tr("ph.subAnswer"),
            { a -> store.setSubQuestion(qid, sub.id) { it.copy(answer = a) } }, onHandle,
        )
    }
}

@Composable
private fun MarksField(value: String, onChange: (String) -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(IQRadius.sm)
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(8)) },
        singleLine = true,
        textStyle = TextStyle(color = c.text, fontSize = 13.sp, fontFamily = uiFont(), textAlign = TextAlign.Center),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .width(54.dp)
            .height(28.dp)
            .clip(shape)
            .background(c.field)
            .border(1.dp, c.borderStrong, shape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    )
}

/** A labelled answer box in the same style as the explanation box, in [color]. */
@Composable
private fun NoteBox(
    label: String,
    color: Color,
    text: String,
    placeholder: String,
    onText: (String) -> Unit,
    onFocus: (RichHandle) -> Unit,
) {
    val shape = RoundedCornerShape(IQRadius.sm)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.06f))
            .drawBehind {
                drawRoundRect(
                    color = color,
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
        val lbl = label.let { if (LocalLang.current == Lang.BN) it else it.uppercase() }
        Text(
            lbl,
            color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.33.sp, fontFamily = uiFont(),
        )
        RichEditor(text, onText, placeholder, minHeight = 32.dp, onFocus = onFocus)
    }
}

@Composable
private fun CqSectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(bottom = 4.dp),
        color = IQ.c.textFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.33.sp, fontFamily = uiFont(),
    )
}

/** Total marks with at most two decimals: 10, 2.5, 0.75. */
private fun formatMarks(v: Double): String {
    val r = kotlin.math.round(v * 100) / 100
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}
