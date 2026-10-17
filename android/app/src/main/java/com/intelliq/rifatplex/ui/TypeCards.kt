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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.SubQuestion
import com.intelliq.rifatplex.data.formatMarks
import com.intelliq.rifatplex.data.parseMarks
import com.intelliq.rifatplex.data.subLabel
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.PIcons

/* ───────────── shared small pieces ───────────── */

/**
 * The website's `.option-explanation` block (green dashed box with a small label and a rich editor). Used for the MCQ
 * explanation and for the answer / explanation boxes of the other types.
 */
@Composable
fun LabeledBlock(
    label: String,
    placeholder: String,
    text: String,
    onText: (String) -> Unit,
    onFocus: (RichHandle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = IQ.c
    val shape = IQD.ctl
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.success.copy(alpha = 0.06f))
            .drawBehind {
                drawRoundRect(
                    color = c.successInk,
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
        Text(
            if (LocalLang.current == Lang.BN) label else label.uppercase(),
            color = c.successInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.33.sp, fontFamily = uiFont(),
        )
        RichEditor(text, onText, placeholder, minHeight = 32.dp, onFocus = onFocus)
    }
}

/** Website `.btn--secondary.qtype-add` ("＋ Insert blank", "＋ Add sub-question"). */
@Composable
fun AddButton(text: String, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(c.primarySoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.primaryInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
        )
    }
}

/** Website `.cq-sec-label`. */
@Composable
private fun SecLabel(text: String) {
    Text(
        text,
        color = IQ.c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.35.sp, fontFamily = uiFont(),
    )
}

/** The little marks box of the card header (56 x 38) and of every CQ sub-question (46 x 30). */
@Composable
fun MarksField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    radius: Dp = 4.dp,
    lineColor: Color? = null,
    bold: Boolean = false,
) {
    val c = IQ.c
    val family = uiFont()
    val shape = RoundedCornerShape(radius)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(
            fontSize = 15.sp, color = c.text, fontFamily = family, textAlign = TextAlign.Center,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        cursorBrush = SolidColor(c.primary),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (focused) c.fieldFocus else c.field, shape)
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) c.primary else (lineColor ?: c.glassBorder),
                        shape,
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = c.textFaint, fontSize = 14.sp, fontFamily = family,
                        textAlign = TextAlign.Center, maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

/** Mix tab only: the question type drop-down of the card header. */
@Composable
fun TypeSelect(kind: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = IQ.c
    val family = uiFont()
    val shape = IQD.ctl
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(shape)
                .background(c.field)
                .border(1.dp, c.glassBorder, shape)
                .clickable { menuOpen = true }
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                tr("t.type.$kind"),
                Modifier.weight(1f),
                color = c.text, fontSize = 14.sp, fontFamily = family,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(PIcons.KeyboardArrowDown, null, Modifier.size(17.dp), tint = c.textFaint)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            QType.ALL.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Text(
                            tr("t.type.$t"),
                            color = if (t == kind) c.primaryInk else c.text, fontSize = 15.sp, fontFamily = family,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onPick(t)
                    },
                )
            }
        }
    }
}

/* ───────────── True / False ───────────── */

@Composable
fun TfBody(q: Question, store: AppStore, onFocus: (RichHandle) -> Unit) {
    RichEditor(
        q.text, { store.setQuestionText(q.id, it) }, tr("t.tfStatement"),
        minHeight = 44.dp, onFocus = onFocus,
    )
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TfButton(tr("t.tfTrue"), q.tfAnswer == true) {
            store.setTfAnswer(q.id, if (q.tfAnswer == true) null else true) // tap again to un-select
        }
        TfButton(tr("t.tfFalse"), q.tfAnswer == false) {
            store.setTfAnswer(q.id, if (q.tfAnswer == false) null else false)
        }
    }
    if (q.tfAnswer != null) {
        LabeledBlock(
            tr("t.explLbl"), tr("t.explPh"), q.explanation,
            { store.setExplanation(q.id, it) }, onFocus,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun TfButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Box(
        Modifier
            .widthIn(min = 96.dp)
            .height(40.dp)
            .clip(shape)
            .background(if (selected) c.success else c.field)
            .border(1.5.dp, if (selected) c.success else c.borderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else c.textSecondary,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
        )
    }
}

/* ───────────── Blanks (fill in the blanks) ───────────── */

@Composable
fun FillBody(q: Question, store: AppStore, onFocus: (RichHandle) -> Unit) {
    var questionHandle by remember { mutableStateOf<RichHandle?>(null) }
    RichEditor(
        q.text, { store.setQuestionText(q.id, it) }, tr("t.fillPh"),
        minHeight = 44.dp,
        onFocus = {
            questionHandle = it
            onFocus(it)
        },
    )
    Box(Modifier.padding(top = 8.dp)) {
        AddButton("＋ " + tr("t.addBlank")) {
            val h = questionHandle
            // Insert at the cursor of the question editor; if it was never focused, add the blank at the end.
            if (h != null) h.insert(" _____ ") else store.setQuestionText(q.id, q.text + " _____ ")
        }
    }
    LabeledBlock(
        tr("t.fillAnsLbl"), tr("t.fillAnsPh"), q.answer,
        { store.setAnswer(q.id, it) }, onFocus,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/* ───────────── Short question (SQ) and Broad question (BQ) ───────────── */

@Composable
fun ShortBody(q: Question, store: AppStore, isDesc: Boolean, onFocus: (RichHandle) -> Unit) {
    RichEditor(
        q.text, { store.setQuestionText(q.id, it) }, tr("ph.question"),
        minHeight = 44.dp, onFocus = onFocus,
    )
    LabeledBlock(
        tr(if (isDesc) "t.descAnsLbl" else "t.shortAnsLbl"),
        tr(if (isDesc) "t.descAnsPh" else "t.shortAnsPh"),
        q.answer,
        { store.setAnswer(q.id, it) }, onFocus,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/* ───────────── Creative question (CQ) ───────────── */

// The website defines one colour for every level (--lv); the four levels differ by their name only.
private val LEVEL_COLOR = Color(0xFF146EF5)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreativeBody(q: Question, store: AppStore, bn: Boolean, onFocus: (RichHandle) -> Unit) {
    val c = IQ.c
    val shape = IQD.card

    // stimulus
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.glassBorder, shape)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SecLabel(tr("t.stimulusLbl"))
        RichEditor(
            q.text, { store.setQuestionText(q.id, it) }, tr("t.stimulusPh"),
            minHeight = 44.dp, onFocus = onFocus,
        )
    }

    // sub-questions
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        q.subs.forEachIndexed { i, s ->
            key(s.id) { SubCard(q, s, i, store, bn, onFocus) }
        }
    }

    // footer: add button + total marks
    val total = q.subs.sumOf { parseMarks(it.marks) }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AddButton("＋ " + tr("t.addSub")) { store.addSub(q.id) } // max 8, enforced by the store
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(c.primarySoft)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tr("t.totalLbl") + ": " + formatMarks(total, bn),
                color = c.primaryInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
            )
        }
    }
}

/** One sub-question: label chip + level + marks + delete, the question, and its dotted-line answer box. */
@Composable
private fun SubCard(
    q: Question,
    s: SubQuestion,
    i: Int,
    store: AppStore,
    bn: Boolean,
    onFocus: (RichHandle) -> Unit,
) {
    val c = IQ.c
    val lv = i % 4
    val shape = IQD.card
    val narrow = LocalConfiguration.current.screenWidthDp <= 380 // website: marks word hidden below 380px
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.glassBorder, shape)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(LEVEL_COLOR),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    subLabel(i, bn),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
                )
            }
            Box(Modifier.weight(1f)) {
                Text(
                    tr("t.level$lv"),
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LEVEL_COLOR.copy(alpha = 0.13f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    color = LEVEL_COLOR, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!narrow) {
                Text(tr("t.marks"), color = c.textSecondary, fontSize = 13.sp, fontFamily = uiFont())
            }
            MarksField(
                value = s.marks,
                onChange = { store.setSubMarks(q.id, s.id, it) },
                placeholder = "",
                modifier = Modifier.size(width = 46.dp, height = 30.dp),
                radius = 8.dp,
                lineColor = c.borderStrong,
                bold = true,
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(IQD.card)
                    .border(1.dp, c.border, IQD.card)
                    .clickable { store.deleteSub(q.id, s.id) }, // the last sub-question is cleared instead
                contentAlignment = Alignment.Center,
            ) {
                Icon(PIcons.Close, null, Modifier.size(16.dp), tint = c.textFaint)
            }
        }

        RichEditor(
            s.text, { store.setSubText(q.id, s.id, it) }, tr("t.subPh$lv"),
            minHeight = 40.dp, onFocus = onFocus,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = c.borderStrong,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                }
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SecLabel(tr("t.answerLbl"))
            RichEditor(
                s.answer, { store.setSubAnswer(q.id, s.id, it) }, tr("t.subAnsPh"),
                minHeight = 40.dp, onFocus = onFocus, fieldBg = c.panelSoft,
            )
        }
    }
}
