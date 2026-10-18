package com.intelliq.rifatplex.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.intelliq.rifatplex.ui.theme.Lato
import com.intelliq.rifatplex.ui.theme.PIcons
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD

/**
 * Press feedback: the control shrinks a little while a finger is on it and springs back.
 * Put it FIRST in the modifier chain (before clip / background) so the whole control scales, and give the same
 * [src] to the clickable.
 */
fun Modifier.pressScale(src: InteractionSource, amount: Float = 0.96f): Modifier = composed {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) amount else 1f, tween(100), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * Titled card: soft icon badge + title (+ trailing slot), then the content. Used to group dialog sections.
 * [raised] = the same surface as a question card (panel colour, glass border, 1dp shadow), for a form card that sits
 * among question cards on the page; the default soft look is for cards inside dialogs and control strips.
 */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    raised: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = IQ.c
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(tween(180))
            .then(if (raised) Modifier.shadow(1.dp, IQD.card) else Modifier)
            .clip(IQD.card)
            .background(if (raised) c.panel else c.panelSoft)
            .border(1.dp, c.glassBorder, IQD.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(IQD.ctl).background(c.primarySoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = c.primary)
            }
            Text(title, Modifier.weight(1f), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            if (trailing != null) trailing()
        }
        content()
    }
}

/** Puts the tip box just under the (i) icon, right edges aligned, flipped above it when there is no room below. */
private class TipPositioner(private val gapPx: Int, private val edgePx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - edgePx).coerceAtLeast(edgePx)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(edgePx, maxX)
        var y = anchorBounds.bottom + gapPx
        if (y + popupContentSize.height > windowSize.height - edgePx) y = anchorBounds.top - gapPx - popupContentSize.height
        return IntOffset(x, y.coerceAtLeast(edgePx))
    }
}

/**
 * Small (i) icon. Tap it and a short suggestion box floats next to it; tap anywhere else (or back) to close.
 * Use it instead of permanent helper text, so the screen stays clean. [linkLabel]/[linkUrl] add an optional link line.
 */
@Composable
fun InfoTip(text: String, modifier: Modifier = Modifier, linkLabel: String? = null, linkUrl: String? = null) {
    val c = IQ.c
    val uri = LocalUriHandler.current
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    val positioner = remember(density) { TipPositioner(with(density) { 6.dp.roundToPx() }, with(density) { 12.dp.roundToPx() }) }
    Box(modifier) {
        Box(Modifier.size(24.dp).clip(CircleShape).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(PIcons.Info, null, Modifier.size(16.dp), tint = if (open) c.primary else c.textFaint)
        }
        if (open) {
            Popup(
                popupPositionProvider = positioner,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 260.dp)
                        .clip(IQD.card)
                        .background(c.panelSoft)
                        .border(1.dp, c.borderStrong, IQD.card)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text, color = c.text, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = uiFont())
                    if (linkLabel != null && linkUrl != null) {
                        Text(
                            linkLabel, Modifier.clickable { uri.openUri(linkUrl) },
                            color = c.primaryInk, fontSize = 14.sp, fontFamily = Lato,
                        )
                    }
                }
            }
        }
    }
}

/** Segmented control: one rounded track, the selected segment is filled with the primary colour. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val c = IQ.c
    Row(
        modifier
            .fillMaxWidth()
            .clip(IQD.ctl)
            .background(c.panelSoft)
            .border(1.dp, c.border, IQD.ctl)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (id, label) ->
            val sel = id == selected
            val bg by animateColorAsState(if (sel) c.primary else Color.Transparent, tween(150), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) Color.White else c.textSecondary,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(), maxLines = 1,
                )
            }
        }
    }
}

/** Title + hint on the left, switch on the right. */
@Composable
fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = IQ.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // the helper text is a tooltip: an (i) icon to the right of the title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                if (hint.isNotBlank()) InfoTip(hint)
            }
        }
        IQSwitch(checked, onChange)
    }
}

/** Small numeric field with the same border, focus ring and height rules as [InputBox]. */
@Composable
fun NumberField(value: Int, min: Int, max: Int, enabled: Boolean = true, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val c = IQ.c
    var txt by remember(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = txt,
        onValueChange = { v ->
            val f = v.filter { it.isDigit() }.take(3)
            txt = f
            f.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontFamily = uiFont()),
        cursorBrush = SolidColor(c.primary),
        modifier = modifier.width(60.dp).onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) txt = value.toString()
        },
        decorationBox = { inner ->
            val bw = if (focused) 2.dp else 1.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = IQD.fieldH)
                    .background(if (focused) c.fieldFocus else c.field, IQD.ctl)
                    .border(bw, if (focused) c.primary else c.glassBorder, IQD.ctl)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

/** Short code of a question type in a soft square (MCQ, CQ, T/F ...). */
@Composable
fun TypeBadge(code: String) {
    val c = IQ.c
    Box(Modifier.size(width = 44.dp, height = 32.dp).clip(IQD.ctl).background(c.primarySoft), contentAlignment = Alignment.Center) {
        Text(code, color = c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(), maxLines = 1)
    }
}
