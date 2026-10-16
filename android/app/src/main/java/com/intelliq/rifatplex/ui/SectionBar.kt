package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.defaultSectionTitle
import com.intelliq.rifatplex.data.dividerOn
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.lineOn
import com.intelliq.rifatplex.data.orderedTypes
import com.intelliq.rifatplex.data.sectionInstruction
import com.intelliq.rifatplex.data.sectionTitle
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.PIcons

/**
 * Website `#qSecBar` (Mix tab only): the "Arrange by section" switch, the heading layout (left / center, with or
 * without a line), and the "Edit sections" list with order, name, instruction and column divider of every section.
 */
@Composable
fun SectionBar(d: AppData, store: AppStore, lang: Lang) {
    val c = IQ.c
    val bn = lang == Lang.BN
    val st = d.settings
    val on = st.sectionMode
    var listOpen by rememberSaveable { mutableStateOf(false) }
    val shape = IQD.card
    val ord = st.orderedTypes()
    val counts = QType.ALL.associateWith { t -> d.questions.count { !it.isEmpty() && it.kind() == t } }
    val presentN = ord.count { (counts[it] ?: 0) > 0 }

    Box(Modifier.animateContentSize(tween(180))) {
    SectionCard(
        title = tr("sec.toggle"),
        icon = PIcons.Description,
        trailing = { IQSwitch(on) { store.setSectionMode(it) } },
    ) {
        if (on) {
            // heading layouts
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("left", "center").forEach { k ->
                    LayoutCard(
                        kind = k,
                        active = st.sectionLayout == k,
                        lineOn = st.lineOn(k),
                        modifier = Modifier.weight(1f),
                        onPick = { store.setSectionLayout(k) },
                        onLine = { store.setSectionLine(k, it) },
                    )
                }
            }

            // "Edit sections (6)" drop-down
            val chevron by animateFloatAsState(if (listOpen) 180f else 0f, tween(180), label = "secChevron")
            val ddShape = IQD.card
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(ddShape)
                    .background(c.panel)
                    .border(1.dp, if (listOpen) c.primary else c.glassBorder, ddShape)
                    .clickable { listOpen = !listOpen }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("sec.dd") + " (" + formatNumber(QType.ALL.size, bn) + ")",
                    color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                )
                Icon(
                    PIcons.KeyboardArrowDown, null,
                    Modifier.size(18.dp).rotate(chevron),
                    tint = if (listOpen) c.primary else c.textSecondary,
                )
            }

            if (listOpen) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ord.forEachIndexed { i, t ->
                        key(t) {
                            SectionItem(
                                t = t, pos = i + 1, count = counts[t] ?: 0,
                                first = i == 0, last = i == ord.lastIndex,
                                d = d, store = store, bn = bn,
                            )
                        }
                    }
                }
            }

            if (presentN < 2 && tr("sec.one").isNotBlank()) {
                Text(
                    tr("sec.one"),
                    Modifier.padding(top = 8.dp),
                    color = c.textFaint, fontSize = 11.5.sp, lineHeight = 16.sp, fontFamily = uiFont(),
                )
            }
        }
    }
    }
}

/** One of the two heading layouts: a small mock heading + the "Line below" checkbox. */
@Composable
private fun LayoutCard(
    kind: String,
    active: Boolean,
    lineOn: Boolean,
    modifier: Modifier,
    onPick: () -> Unit,
    onLine: (Boolean) -> Unit,
) {
    val c = IQ.c
    val shape = IQD.card
    val mockShape = IQD.ctl
    val center = kind == "center"
    val ink = Color(0xFF111111)
    Column(
        modifier
            .clip(shape)
            .background(if (active) c.primarySoft else c.panel)
            .border(1.5.dp, if (active) c.primary else c.glassBorder, shape)
            .clickable(onClick = onPick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(mockShape)
                .background(Color.White)
                .border(1.dp, c.border, mockShape)
                .drawBehind {
                    if (lineOn) {
                        val y = size.height - 1.dp.toPx()
                        drawLine(ink, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                    }
                }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                tr("sec.mName"),
                Modifier.fillMaxWidth(),
                color = ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                tr("sec.mInstr"),
                Modifier.fillMaxWidth(),
                color = Color(0xFF4B5563), fontSize = 11.sp, fontStyle = FontStyle.Italic, fontFamily = uiFont(),
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.clickable { onLine(!lineOn) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IQCheck(lineOn, onLine)
            Text(tr("sec.line"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
        }
    }
}

/** The editor of one section: position, name, count, up / down, name field, instruction, column divider switch. */
@Composable
private fun SectionItem(
    t: String,
    pos: Int,
    count: Int,
    first: Boolean,
    last: Boolean,
    d: AppData,
    store: AppStore,
    bn: Boolean,
) {
    val c = IQ.c
    val shape = IQD.card
    val st = d.settings
    val defTitle = defaultSectionTitle(t, bn)
    // The text fields keep their own text while typing; the store receives every change.
    var name by remember(t, bn) { mutableStateOf(st.sectionTitle(t, bn)) }
    var instr by remember(t) { mutableStateOf(st.sectionInstruction(t)) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.glassBorder, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(c.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatNumber(pos, bn),
                    color = c.primaryInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
                )
            }
            Text(
                defTitle,
                Modifier.weight(1f),
                color = c.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatNumber(count, bn) + tr("sec.countSuffix"),
                color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MoveBtn(PIcons.KeyboardArrowUp, !first) { store.moveSection(t, -1) }
                MoveBtn(PIcons.KeyboardArrowDown, !last) { store.moveSection(t, 1) }
            }
        }

        SecField(tr("sec.nm")) {
            SecInput(
                value = name,
                onChange = { v ->
                    name = v
                    store.setSectionMeta(t) { m -> m.copy(title = if (v.isNotBlank() && v != defTitle) v else "") }
                },
                placeholder = "",
                bold = true,
                minLines = 1,
                onBlur = { if (name.isBlank()) name = defTitle },
            )
        }

        SecField(tr("sec.insLbl")) {
            SecInput(
                value = instr,
                onChange = { v ->
                    instr = v
                    store.setSectionMeta(t) { m -> m.copy(instruction = if (v.isNotBlank()) v else "") }
                },
                placeholder = tr("sec.ins"),
                bold = false,
                minLines = 2,
            )
        }

        val divOn = st.dividerOn(t)
        Row(
            Modifier.clickable { store.setSectionDivider(t, !divOn) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IQCheck(divOn) { store.setSectionDivider(t, it) }
            Text(tr("sec.divLbl"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
        }
    }
}

@Composable
private fun SecField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color = IQ.c.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
        )
        content()
    }
}

@Composable
private fun MoveBtn(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.card
    Box(
        Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.borderStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = c.textSecondary)
    }
}

/** Website `.qsec__nameinput` / `.qsec__instr`: 8dp corners, 13sp text. */
@Composable
private fun SecInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    bold: Boolean,
    minLines: Int,
    onBlur: () -> Unit = {},
) {
    val c = IQ.c
    val family = uiFont()
    var focused by remember { mutableStateOf(false) }
    val shape = IQD.card
    val single = minLines <= 1
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = single,
        minLines = minLines,
        textStyle = TextStyle(
            fontSize = 13.sp, lineHeight = 19.5.sp, color = c.text, fontFamily = family,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        cursorBrush = SolidColor(c.primary),
        modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (focused && !it.isFocused) onBlur()
            focused = it.isFocused
        },
        decorationBox = { inner ->
            val bw = if (focused) 2.dp else 1.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (single) 36.dp else 56.dp)
                    .background(if (focused) c.fieldFocus else c.field, shape)
                    .border(bw, if (focused) c.primary else c.glassBorder, shape)
                    .padding(horizontal = 11.dp - bw, vertical = 9.dp - bw),
                contentAlignment = if (single) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = c.textFaint, fontSize = 13.sp, fontFamily = family)
                }
                inner()
            }
        },
    )
}
