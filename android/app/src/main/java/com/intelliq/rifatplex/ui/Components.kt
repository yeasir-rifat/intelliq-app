package com.intelliq.rifatplex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.animation.core.animateDpAsState
import com.intelliq.rifatplex.ui.theme.PIcons
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.ui.theme.IQ
import kotlinx.coroutines.delay

@Composable
fun SquareBtn(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(src, 0.92f)
            .size(size)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.border, shape)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 6.dp),
        color = IQ.c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
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
        InputBox(value, onChange, placeholder, minLines = minLines)
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
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = IQ.c
    val family = uiFont()
    var focused by remember { mutableStateOf(false) }
    val shape = IQD.ctl
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(fontSize = 14.sp, color = c.text, fontFamily = family),
        visualTransformation = if (family === com.intelliq.rifatplex.ui.theme.BanglaUi) LatinVisualTransformation else androidx.compose.ui.text.input.VisualTransformation.None,
        cursorBrush = SolidColor(c.primary),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
                    .heightIn(min = if (minLines <= 1) IQD.fieldH else 0.dp)
                    .padding(horizontal = 13.dp - bw, vertical = 11.dp - bw),
                contentAlignment = if (minLines <= 1) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = c.textFaint, fontSize = 14.sp, fontFamily = family)
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
    fullWidth: Boolean = false,
) {
    val c = IQ.c
    val family = uiFont()
    var open by remember { mutableStateOf(false) }
    val shape = IQD.ctl
    val label = options.firstOrNull { it.first == value }?.second ?: value
    Box(modifier) {
        Row(
            Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(min = minWidth))
                .clip(shape)
                .background(c.field)
                .border(1.dp, c.glassBorder, shape)
                .clickable { open = true }
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = c.text, fontSize = 14.sp, fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Icon(PIcons.KeyboardArrowDown, null, Modifier.size(16.dp), tint = c.textFaint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            l,
                            color = if (v == value) c.primaryInk else c.text,
                            fontSize = 14.sp, fontFamily = family,
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

@Composable
fun IQSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = IQ.c
    val offset by animateDpAsState(if (checked) 20.dp else 2.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "thumb")
    val track by animateColorAsState(if (checked) c.primary else c.borderStrong, tween(150), label = "track")
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
                .shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

enum class BtnKind { Primary, Secondary, Ghost }

@Composable
fun IQButton(text: String, kind: BtnKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src)
            .heightIn(min = IQD.fieldH)
            .clip(shape)
            .background(bg)
            .then(if (kind == BtnKind.Ghost) Modifier.border(1.dp, c.border, shape) else Modifier)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** [actionLabel] + [onAction] add a tappable action (for example "Undo") and keep the toast on screen longer. */
data class ToastMsg(
    val text: String,
    val error: Boolean = false,
    val key: Long = System.nanoTime(),
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
fun ToastHost(toast: ToastMsg?, onDone: () -> Unit) {
    val c = IQ.c
    LaunchedEffect(toast?.key) {
        if (toast != null) {
            delay(if (toast.actionLabel != null) 7000L else 1800L)
            onDone()
        }
    }
    if (toast != null) {
        // fillMaxSize + BottomCenter = pinned to the bottom of the screen (just above the system bar)
        Box(
            Modifier.fillMaxSize().navigationBarsPadding().padding(start = 32.dp, end = 32.dp, bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .shadow(6.dp, shape)
                    .clip(shape)
                    .background(if (toast.error) c.danger else c.text)
                    .padding(start = 10.dp, end = 13.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (toast.error) PIcons.ErrorCircle else PIcons.CheckCircle,
                    null, Modifier.size(15.dp),
                    tint = if (toast.error) Color.White else Color(0xFF4ADE80),
                )
                Text(
                    toast.text,
                    color = if (toast.error) Color.White else c.panel,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (toast.actionLabel != null) {
                    Text(
                        toast.actionLabel,
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                toast.onAction?.invoke()
                                onDone()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (c.isDark) c.primary else Color(0xFF8AB4FF),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(),
                    )
                }
            }
        }
    }
}

/** Small square checkbox (website `accent-color: primary`), used by the section bar. */
@Composable
fun IQCheck(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Box(
        Modifier
            .size(18.dp)
            .clip(shape)
            .background(if (checked) c.primary else c.field)
            .border(1.5.dp, if (checked) c.primary else c.borderStrong, shape)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(PIcons.Check, null, Modifier.size(12.dp), tint = Color.White)
    }
}
