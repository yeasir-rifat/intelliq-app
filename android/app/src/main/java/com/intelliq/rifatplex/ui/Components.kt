package com.intelliq.rifatplex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQElevation
import com.intelliq.rifatplex.ui.theme.IQRadius
import com.intelliq.rifatplex.ui.theme.PIcons
import kotlinx.coroutines.delay

/**
 * Press feedback that matches the website's :active rule (the control drops 1 px while pressed).
 * No Material ripple, so the controls keep the website's flat look.
 */
fun Modifier.iqPressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val drop by animateFloatAsState(if (pressed) 1f else 0f, label = "press")
    this
        .graphicsLayer { translationY = 1.dp.toPx() * drop }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun SquareBtn(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(IQRadius.sm)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.border, shape)
            .iqPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 6.dp),
        color = IQ.c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
    )
}

/** Single-line / multi-line input styled like the website's .field-input. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
) {
    Column {
        FieldLabel(label)
        InputBox(value, onChange, placeholder, minLines = minLines, singleLine = minLines == 1)
    }
}

@Composable
fun InputBox(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
) {
    val c = IQ.c
    val family = uiFont()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(IQRadius.sm)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(fontSize = 13.5.sp, color = c.text, fontFamily = family),
        cursorBrush = SolidColor(c.primary),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            // web: 1px border + 1px focus outline (no layout shift), so padding compensates
            val bw = if (focused) 2.dp else 1.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (focused) c.fieldFocus else c.field, shape)
                    .border(bw, if (focused) c.primary else c.glassBorder, shape)
                    .padding(horizontal = 12.dp - bw, vertical = 10.dp - bw)
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = c.textFaint, fontSize = 13.5.sp, fontFamily = family)
                }
                inner()
            }
        },
    )
}

/** The website's contenteditable .editor-area (plain-text version). */
@Composable
fun EditorArea(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    onFocus: () -> Unit = {},
) {
    val c = IQ.c
    val family = uiFont()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(IQRadius.sm)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(fontSize = 13.5.sp, lineHeight = 21.6.sp, color = c.text, fontFamily = family),
        cursorBrush = SolidColor(c.primary),
        modifier = modifier.fillMaxWidth().onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus()
        },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(if (focused) c.fieldFocus else c.field, shape)
                    .border(1.dp, if (focused) c.primary else c.glassBorder, shape)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = c.textFaint, fontSize = 13.5.sp, lineHeight = 21.6.sp, fontFamily = family)
                }
                inner()
            }
        },
    )
}

/** Dropdown styled like .field-select. options = (value, label). */
@Composable
fun Select(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 140.dp,
) {
    val c = IQ.c
    val family = uiFont()
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(IQRadius.sm)
    val label = options.firstOrNull { it.first == value }?.second ?: value
    Box(modifier) {
        Row(
            Modifier
                .widthIn(min = minWidth)
                .clip(shape)
                .background(c.field)
                .border(1.dp, c.glassBorder, shape)
                .iqPressable { open = true }
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = c.text, fontSize = 13.5.sp, fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Icon(PIcons.KeyboardArrowDown, null, Modifier.size(16.dp), tint = c.textFaint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            l,
                            color = if (v == value) c.primary else c.text,
                            fontSize = 13.5.sp, fontFamily = family,
                        )
                    },
                    onClick = {
                        onSelect(v)
                        open = false
                    },
                )
            }
        }
    }
}

/** Toggle switch: 40 x 22 px track, 18 px thumb, the website's colours. The track fades between states. */
@Composable
fun IQSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = IQ.c
    val offset by animateDpAsState(if (checked) 20.dp else 2.dp, label = "thumb")
    val track by animateColorAsState(if (checked) c.primary else c.borderStrong, label = "track")
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(CircleShape)
            .background(track)
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = offset, y = 2.dp)
                .size(18.dp)
                .shadow(IQElevation.sm, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

enum class BtnKind { Primary, Secondary, Ghost }

@Composable
fun IQButton(text: String, kind: BtnKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(IQRadius.sm)
    val bg = when (kind) {
        BtnKind.Primary -> c.primary
        BtnKind.Secondary -> c.primarySoft
        BtnKind.Ghost -> Color.Transparent
    }
    val fg = when (kind) {
        BtnKind.Primary -> Color.White
        BtnKind.Secondary -> c.primaryInk
        BtnKind.Ghost -> c.textSecondary
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (kind == BtnKind.Ghost) Modifier.border(1.dp, c.border, shape) else Modifier)
            .iqPressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
    }
}

data class ToastMsg(val text: String, val error: Boolean = false, val key: Long = System.nanoTime())

@Composable
fun ToastHost(toast: ToastMsg?, onDone: () -> Unit) {
    val c = IQ.c
    LaunchedEffect(toast?.key) {
        if (toast != null) {
            delay(1800)
            onDone()
        }
    }
    if (toast != null) {
        // fillMaxSize + BottomCenter = pinned to the bottom of the screen (just above the system bar)
        Box(
            Modifier.fillMaxSize().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .shadow(IQElevation.lg, shape)
                    .clip(shape)
                    .background(if (toast.error) c.danger else c.text)
                    .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (toast.error) PIcons.ErrorCircle else PIcons.CheckCircle,
                    null, Modifier.size(18.dp),
                    tint = if (toast.error) Color.White else Color(0xFF4ADE80),
                )
                Text(
                    toast.text,
                    color = if (toast.error) Color.White else c.panel,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
