package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.intelliq.rifatplex.data.FigData
import com.intelliq.rifatplex.data.Rich
import com.intelliq.rifatplex.data.parseHtml
import com.intelliq.rifatplex.data.toHtml
import com.intelliq.rifatplex.ui.theme.IQ

private const val SUB_SUP_SCALE = 0.72f
private const val FRAC_INNER_SCALE = 0.92f

/** Numerator / denominator text. On the paper, formula text uses the Cambria-style math font. */
private fun fracText(html: String, fonts: PaperFonts?): AnnotatedString {
    val a = htmlToAnnotated(html, SUB_SUP_SCALE)
    return if (fonts == null) a else fonts.apply(a, mathDefault = true)
}

/** A numerator drawn over a denominator with a bar between them. */
@Composable
fun FracColumn(
    nHtml: String,
    dHtml: String,
    style: TextStyle,
    barColor: Color,
    barThickness: Dp,
    modifier: Modifier = Modifier,
    fonts: PaperFonts? = null,
) {
    val n = remember(nHtml, fonts) { fracText(nHtml, fonts) }
    val d = remember(dHtml, fonts) { fracText(dHtml, fonts) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(n, style = style, softWrap = false, maxLines = 1)
        Box(Modifier.fillMaxWidth().padding(horizontal = 1.dp).height(barThickness).background(barColor))
        Text(d, style = style, softWrap = false, maxLines = 1)
    }
}

/**
 * Read-only rich text (paper). Works like `Text(htmlToAnnotated(..))`, but stacked fractions are drawn as real
 * "a over b" inline content. [prefix] is plain text put before the body; [bodySpan] styles only the body.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun RichTextView(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    prefix: AnnotatedString? = null,
    bodySpan: SpanStyle? = null,
    /** Paper only: Bangla / English / formula fonts per character. */
    fonts: PaperFonts? = null,
    /** Bold is the default weight of this text ("Bold question text"); characters marked normal stay normal. */
    baseBold: Boolean = false,
) {
    val rich = remember(html) { parseHtml(html) }
    if (rich.hasFig()) {
        FigureText(rich, modifier, color, fontFamily, fontWeight, fontSize, lineHeight, prefix, bodySpan, fonts, baseBold)
        return
    }
    val atoms = remember(rich) { rich.atomIndices() }

    if (atoms.isEmpty()) {
        val text = remember(html, prefix, bodySpan, fonts, baseBold) {
            val built = buildAnnotatedString {
                prefix?.let { append(it) }
                val body = buildStyled(rich.text, rich.styles, SUB_SUP_SCALE, baseBold)
                if (bodySpan != null) withStyle(bodySpan) { append(body) } else append(body)
            }
            if (fonts == null) built else fonts.apply(built)
        }
        Text(
            text, modifier, color = color, fontFamily = fontFamily, fontWeight = fontWeight,
            fontSize = fontSize, lineHeight = lineHeight,
        )
        return
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseSize = if (fontSize == TextUnit.Unspecified) 14.sp else fontSize
    val innerSize = baseSize * FRAC_INNER_SCALE
    val ink = (bodySpan?.color ?: color).takeIf { it != Color.Unspecified } ?: Color.Black
    val innerStyle = TextStyle(
        color = ink, fontFamily = fonts?.math ?: fontFamily, fontWeight = fontWeight,
        fontStyle = bodySpan?.fontStyle, fontSize = innerSize,
    )
    val barDp = with(density) { (innerSize.toPx() * 0.07f).coerceAtLeast(1f).toDp() }

    val fracContent = remember(html, fontFamily, fonts, fontWeight, fontSize, ink, density.density, density.fontScale) {
        val map = LinkedHashMap<String, InlineTextContent>()
        var tallestPx = 0f
        atoms.forEachIndexed { k, i ->
            val nH = rich.styles[i].fracN
            val dH = rich.styles[i].fracD
            val mn = measurer.measure(fracText(nH, fonts), innerStyle, softWrap = false, maxLines = 1)
            val md = measurer.measure(fracText(dH, fonts), innerStyle, softWrap = false, maxLines = 1)
            val barPx = with(density) { barDp.toPx() }
            val wPx = maxOf(mn.size.width, md.size.width) + with(density) { innerSize.toPx() } * 0.45f
            val hPx = mn.size.height + md.size.height + barPx
            if (hPx > tallestPx) tallestPx = hPx
            val ph = Placeholder(
                width = with(density) { wPx.toSp() },
                height = with(density) { hPx.toSp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            )
            map["f$k"] = InlineTextContent(ph) {
                FracColumn(nH, dH, innerStyle, ink, barDp, Modifier.fillMaxSize(), fonts)
            }
        }
        map to tallestPx
    }
    val content = fracContent.first
    // A stacked fraction (numerator + bar + denominator) is taller than one fixed-height text line. With a fixed
    // lineHeight Compose does not grow the line, so the fraction spilled into the lines above and below (the
    // overlapping answer text). Give the lines of this paragraph room for the tallest fraction plus a small gap.
    val fitLineHeight = remember(lineHeight, fracContent.second, density.density, density.fontScale) {
        if (lineHeight == TextUnit.Unspecified || !lineHeight.isSp) lineHeight
        else {
            val need = with(density) { (fracContent.second + innerSize.toPx() * 0.3f).toSp() }
            if (need.value > lineHeight.value) need else lineHeight
        }
    }

    val text = remember(html, prefix, bodySpan, fonts, baseBold) {
        val built = buildAnnotatedString {
            prefix?.let { append(it) }
            fun body() {
                var seg = 0
                var k = 0
                for (i in rich.text.indices) {
                    if (rich.isAtom(i)) {
                        if (i > seg) append(buildStyled(rich.text.substring(seg, i), rich.styles.subList(seg, i), SUB_SUP_SCALE, baseBold))
                        appendInlineContent("f$k", "�")
                        k++
                        seg = i + 1
                    }
                }
                if (seg < rich.text.length) {
                    append(buildStyled(rich.text.substring(seg), rich.styles.subList(seg, rich.text.length), SUB_SUP_SCALE, baseBold))
                }
            }
            if (bodySpan != null) withStyle(bodySpan) { body() } else body()
        }
        if (fonts == null) built else fonts.apply(built)
    }
    Text(
        text, modifier, color = color, fontFamily = fontFamily, fontWeight = fontWeight,
        fontSize = fontSize, lineHeight = fitLineHeight, inlineContent = content,
    )
}

/**
 * Text that contains pictures: the pictures are blocks between the text pieces (website `.iq-fig`), so the text is cut
 * at every picture and each piece is drawn by [RichTextView] again (fractions, sub / superscripts keep working).
 */
@Composable
private fun FigureText(
    rich: Rich,
    modifier: Modifier,
    color: Color,
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    prefix: AnnotatedString?,
    bodySpan: SpanStyle?,
    fonts: PaperFonts?,
    baseBold: Boolean = false,
) {
    val parts = remember(rich) {
        val out = ArrayList<Any>()
        val text = rich.text
        var seg = 0
        fun addText(from: Int, to: Int, figBefore: Boolean, figAfter: Boolean) {
            var a = from
            var b = to
            if (figBefore) while (a < b && text[a] == '\n') a++
            if (figAfter) while (b > a && text[b - 1] == '\n') b--
            if (a < b) out.add(toHtml(text.substring(a, b), rich.styles.subList(a, b)))
        }
        for (i in text.indices) {
            if (rich.isFig(i)) {
                addText(seg, i, seg > 0, true)
                out.add(rich.styles[i].fig as FigData)
                seg = i + 1
            }
        }
        addText(seg, text.length, seg > 0, false)
        out
    }
    Column(modifier) {
        var prefixPending = prefix
        parts.forEach { part ->
            if (part is FigData) {
                prefixPending?.let { pre ->
                    Text(pre, color = color, fontFamily = fontFamily, fontWeight = fontWeight, fontSize = fontSize, lineHeight = lineHeight)
                    prefixPending = null
                }
                FigureBlock(part)
            } else {
                RichTextView(
                    part as String, Modifier.fillMaxWidth(),
                    color = color, fontFamily = fontFamily, fontWeight = fontWeight, fontSize = fontSize, lineHeight = lineHeight,
                    prefix = prefixPending, bodySpan = bodySpan, fonts = fonts, baseBold = baseBold,
                )
                prefixPending = null
            }
        }
    }
}

/** Add / edit a fraction: live preview + numerator and denominator editors (rich text, so x², H₂O etc. work). */
@Composable
fun FractionDialog(
    initialN: String,
    initialD: String,
    editing: Boolean,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
) {
    val c = IQ.c
    val shape = RoundedCornerShape(8.dp)
    var n by remember { mutableStateOf(initialN) }
    var d by remember { mutableStateOf(initialD) }
    var handle by remember { mutableStateOf<RichHandle?>(null) }
    val family = uiFont()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.border, shape)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                tr(if (editing) "frac.edit" else "frac.title"),
                color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = family,
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(c.field).padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                FracColumn(
                    n, d,
                    TextStyle(color = c.text, fontFamily = family, fontSize = 20.sp),
                    c.text, 1.5.dp,
                    Modifier.width(IntrinsicSize.Max),
                )
            }
            Text(tr("frac.numerator"), color = c.textSecondary, fontSize = 12.sp, fontFamily = family)
            RichEditor(n, { n = it }, tr("frac.numerator"), minHeight = 36.dp, onFocus = { handle = it })
            Text(tr("frac.denominator"), color = c.textSecondary, fontSize = 12.sp, fontFamily = family)
            RichEditor(d, { d = it }, tr("frac.denominator"), minHeight = 36.dp, onFocus = { handle = it })
            FormatToolbar(handle, allowFraction = false, allowImage = false, showClose = false) {}
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IQButton(tr("common.cancel"), BtnKind.Ghost, Modifier.weight(1f)) { onDismiss() }
                IQButton(tr(if (editing) "frac.update" else "frac.insert"), BtnKind.Primary, Modifier.weight(1f)) {
                    if (plainTextOf(n).isNotBlank() || plainTextOf(d).isNotBlank()) onApply(n, d)
                }
            }
        }
    }
}

private fun plainTextOf(html: String): String = com.intelliq.rifatplex.data.plainText(html)
