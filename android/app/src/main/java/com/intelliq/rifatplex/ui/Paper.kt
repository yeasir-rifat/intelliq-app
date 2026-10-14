package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.DocFields
import com.intelliq.rifatplex.data.DocSettings
import com.intelliq.rifatplex.data.PAGE_SIZES_MM
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.optionLabel
import com.intelliq.rifatplex.data.plainText
import com.intelliq.rifatplex.ui.theme.IQ
import kotlin.math.max
import kotlin.math.roundToInt

private val GRAY_TEXT = Color(0xFF374151)
private val GRAY_RULE = Color(0xFF9CA3AF)
private val GREEN = Color(0xFF16A34A)

fun parseHex(s: String, fallback: Color): Color =
    try { Color(android.graphics.Color.parseColor(s)) } catch (e: Exception) { fallback }

private fun mmDp(mm: Float): Dp = (mm * 96f / 25.4f).dp

/** Document typography. 1em == basePx dp (font size in pt * 96/72), independent of the system font scale. */
private class PaperStyle(
    val fonts: PaperFonts,
    val basePx: Float,
    val color: Color,
    val bn: Boolean,
    val density: Density,
) {
    val family: FontFamily get() = fonts.base

    /** Text with Bangla / English / formula fonts applied per character. */
    fun t(s: String): AnnotatedString = fonts.apply(s)

    fun sp(em: Float): TextUnit = with(density) { (basePx * em).dp.toSp() }
    fun dp(em: Float): Dp = (basePx * em).dp
}

private data class PageModel(val first: Boolean, val left: List<Int>, val right: List<Int>)

/**
 * Renders the question paper as real-size pages (mm → dp at 96 dpi), paginated like the website:
 * two balanced columns, questions never split, usable height = page height − 36 mm.
 * [scale] is applied to the whole stack; [containerWidth] centres the pages.
 */
@Composable
fun PaperPages(
    questions: List<Question>,
    setCode: String?,
    doc: DocFields,
    lang: Lang,
    settings: DocSettings,
    showAnswers: Boolean,
    scale: Float,
    containerWidth: Dp,
    modifier: Modifier = Modifier,
    /** PDF export: draw only this page (0-based) at exactly page size, no shadow/border/gap. -1 = normal preview. */
    printPage: Int = -1,
    /** PDF export: called while laying out with (page that was laid out, total pages). */
    onPrintLayout: ((Int, Int) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val bn = lang == Lang.BN
    val st = PaperStyle(
        fonts = paperFonts(settings.fontFamily),
        basePx = settings.fontSize * 96f / 72f,
        color = parseHex(settings.fontColor, Color(0xFF111111)),
        bn = bn,
        density = density,
    )
    val size = PAGE_SIZES_MM[settings.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
    val wMm = size.first
    val hMm = size.second
    val pageBg = parseHex(settings.pageColor, Color.White)

    SubcomposeLayout(modifier) { _ ->
        val pageW = mmDp(wMm).roundToPx()
        val pageH = mmDp(hMm).roundToPx()
        val padH = mmDp(10f).roundToPx()
        val colGap = mmDp(9f).roundToPx()
        val contentW = pageW - 2 * padH
        val colW = (contentW - colGap) / 2
        val usable = mmDp(hMm - 36f).roundToPx()

        val hdrH = subcompose("hdr") { HeaderBlock(doc, st) }
            .sumOf { it.measure(Constraints.fixedWidth(contentW)).height }
        val n = questions.size
        val hs = IntArray(n) { i ->
            subcompose("m$i") { QuestionBlock(questions[i], i + 1, st, showAnswers) }
                .sumOf { it.measure(Constraints.fixedWidth(colW)).height }
        }

        fun total(start: Int, k: Int): Int {
            var t = 0
            for (i in start until start + k) t += hs[i]
            return t
        }

        fun balanced(start: Int, k: Int): Int {
            val t = total(start, k)
            var acc = 0
            var best = t
            for (i in start until start + k) {
                acc += hs[i]
                best = minOf(best, max(acc, t - acc))
            }
            return best
        }

        val pages = ArrayList<PageModel>()
        if (n == 0) pages.add(PageModel(true, emptyList(), emptyList()))
        var idx = 0
        var first = true
        while (idx < n) {
            val avail = if (first) max(usable - hdrH, (usable * 0.3f).toInt()) else usable
            var k = 1
            while (idx + k < n && balanced(idx, k + 1) <= avail) k++
            val t = total(idx, k)
            var bestS = k
            var bestV = Int.MAX_VALUE
            var acc = 0
            for (s in 1..k) {
                acc += hs[idx + s - 1]
                val v = max(acc, t - acc)
                if (v < bestV) { bestV = v; bestS = s }
            }
            pages.add(
                PageModel(
                    first,
                    (idx until idx + bestS).toList(),
                    (idx + bestS until idx + k).toList(),
                ),
            )
            idx += k
            first = false
        }

        if (printPage >= 0) {
            val pi = printPage.coerceIn(0, pages.size - 1)
            val label = if (pages.size > 1) {
                "${trKey("doc.page", lang)} ${formatNumber(pi + 1, bn)}/${formatNumber(pages.size, bn)}"
            } else null
            val printed = subcompose("p$pi") {
                PageView(pages[pi], questions, doc, setCode, st, bn, showAnswers, pageBg, print = true, pageLabel = label)
            }.map { it.measure(Constraints.fixed(pageW, pageH)) }
            onPrintLayout?.invoke(pi, pages.size)
            return@SubcomposeLayout layout(pageW, pageH) { printed.forEach { it.place(0, 0) } }
        }

        val pagePlaceables = pages.mapIndexed { pi, pg ->
            subcompose("p$pi") {
                PageView(pg, questions, doc, setCode, st, bn, showAnswers, pageBg)
            }.map { it.measure(Constraints.fixed(pageW, pageH)) }
        }
        val labels = if (pages.size > 1) {
            pages.indices.map { pi ->
                subcompose("l$pi") {
                    Text(
                        "${trKey("doc.page", lang)} ${formatNumber(pi + 1, bn)}",
                        color = IQ.c.textFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                    )
                }.map { it.measure(Constraints()) }
            }
        } else emptyList()

        val gap = 28.dp.roundToPx()
        val padTop = 24.dp.roundToPx()
        val side = 16.dp.roundToPx()
        val totalW = pageW + 2 * side
        val totalH = padTop * 2 + pages.size * pageH + (pages.size - 1) * gap
        val scaledW = (totalW * scale).roundToInt()
        val scaledH = (totalH * scale).roundToInt()
        val reportW = max(containerWidth.roundToPx(), scaledW)
        val pageX = ((reportW - pageW * scale) / 2f).roundToInt()
        val labelOffset = 26.dp.roundToPx()

        layout(reportW, scaledH) {
            pagePlaceables.forEachIndexed { pi, list ->
                val y = ((padTop + pi * (pageH + gap)) * scale).roundToInt()
                list.forEach {
                    it.placeWithLayer(pageX, y) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
                labels.getOrNull(pi)?.forEach {
                    it.place(pageX, y - (labelOffset * scale).roundToInt())
                }
            }
        }
    }
}

@Composable
private fun PageView(
    page: PageModel,
    questions: List<Question>,
    doc: DocFields,
    setCode: String?,
    st: PaperStyle,
    bn: Boolean,
    showAnswers: Boolean,
    pageBg: Color,
    print: Boolean = false,
    pageLabel: String? = null,
) {
    val c = IQ.c
    val shape = RoundedCornerShape(2.dp)
    val frame = if (print) {
        Modifier.fillMaxSize().background(pageBg)
    } else {
        Modifier
            .fillMaxSize()
            .shadow(3.dp, shape)
            .background(pageBg, shape)
            .border(1.dp, c.glassBorder, shape)
            .clipToBounds()
    }
    Box(frame) {
        Column(Modifier.fillMaxSize().padding(horizontal = mmDp(10f), vertical = mmDp(12f))) {
            if (page.first) HeaderBlock(doc, st)
            if (questions.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                    Text(tr("doc.empty"), color = GRAY_RULE, fontSize = 13.sp, fontFamily = uiFont(), textAlign = TextAlign.Center)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(mmDp(9f))) {
                    Column(Modifier.weight(1f)) {
                        page.left.forEach { QuestionBlock(questions[it], it + 1, st, showAnswers) }
                    }
                    Column(Modifier.weight(1f)) {
                        page.right.forEach { QuestionBlock(questions[it], it + 1, st, showAnswers) }
                    }
                }
            }
        }
        if (print && pageLabel != null) {
            Text(
                st.t(pageLabel),
                Modifier.align(Alignment.BottomCenter).padding(bottom = mmDp(5f)),
                color = GRAY_RULE, fontFamily = st.family, fontSize = st.sp(0.8f),
            )
        }
        if (page.first && setCode != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = mmDp(18f), end = mmDp(16f))
                    .border(1.3.dp, st.color, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    st.t("${trKey("doc.set", if (bn) Lang.BN else Lang.EN)}: $setCode"),
                    color = st.color, fontFamily = st.family, fontWeight = FontWeight.Bold, fontSize = st.sp(0.95f),
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock(doc: DocFields, st: PaperStyle) {
    val lang = if (st.bn) Lang.BN else Lang.EN
    val marks = doc.marks.ifBlank { trKey("doc.marksDefault", lang) }
    val time = doc.time.ifBlank { trKey("doc.timeDefault", lang) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            st.t(doc.title.ifBlank { trKey("doc.titleDefault", lang) }),
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            color = st.color, fontFamily = st.family, fontWeight = FontWeight.Bold,
            fontSize = st.sp(1.5f), lineHeight = st.sp(2.25f), textAlign = TextAlign.Center,
        )
        Text(
            st.t(doc.subtitle.ifBlank { trKey("doc.subtitleDefault", lang) }),
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            color = GRAY_TEXT, fontFamily = st.family, fontWeight = FontWeight.Medium,
            fontSize = st.sp(1.05f), lineHeight = st.sp(1.575f), textAlign = TextAlign.Center,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                st.t("${trKey("doc.marks", lang)}: $marks"), color = st.color, fontFamily = st.family,
                fontWeight = FontWeight.SemiBold, fontSize = st.sp(0.98f), lineHeight = st.sp(1.47f),
            )
            Text(
                st.t("${trKey("doc.time", lang)}: $time"), color = st.color, fontFamily = st.family,
                fontWeight = FontWeight.SemiBold, fontSize = st.sp(0.98f), lineHeight = st.sp(1.47f),
            )
        }
        Box(Modifier.padding(top = 8.dp, bottom = 12.dp).fillMaxWidth().height(1.dp).background(GRAY_RULE))
        val hasNote = doc.note.isNotBlank()
        Text(
            st.t(if (hasNote) "${trKey("doc.note", lang)}: ${doc.note}" else trKey("doc.noteDefault", lang)),
            Modifier.padding(bottom = 14.dp),
            color = if (hasNote) GRAY_TEXT else GRAY_RULE, fontFamily = st.family, fontStyle = FontStyle.Italic,
            fontSize = st.sp(0.92f), lineHeight = st.sp(1.38f),
        )
    }
}

@Composable
private fun QuestionBlock(q: Question, number: Int, st: PaperStyle, showAnswers: Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        RichTextView(
            q.text,
            Modifier.fillMaxWidth().padding(bottom = 5.dp),
            prefix = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${formatNumber(number, st.bn)}.  ") }
            },
            color = st.color, fontFamily = st.family, fontWeight = FontWeight.Medium,
            fontSize = st.sp(1f), lineHeight = st.sp(1.5f), fonts = st.fonts,
        )
        OptionsBlock(q, st, showAnswers)
        if (showAnswers && plainText(q.explanation).isNotBlank()) {
            val label = trKey("label.explanation", if (st.bn) Lang.BN else Lang.EN)
            RichTextView(
                q.explanation,
                Modifier.padding(top = 8.dp),
                prefix = buildAnnotatedString {
                    withStyle(SpanStyle(color = GREEN, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal)) {
                        append("$label: ")
                    }
                },
                bodySpan = SpanStyle(color = GRAY_TEXT, fontStyle = FontStyle.Italic),
                fontFamily = st.family, fontSize = st.sp(0.88f), lineHeight = st.sp(1.232f), fonts = st.fonts,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsBlock(q: Question, st: PaperStyle, showAnswers: Boolean) {
    when (q.layout) {
        "horizontal" -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            q.options.forEachIndexed { i, o -> OptionItem(i, o.text, showAnswers && o.correct, st, false) }
        }
        "grid" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            q.options.chunked(2).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEachIndexed { j, o ->
                        Box(Modifier.weight(1f)) { OptionItem(r * 2 + j, o.text, showAnswers && o.correct, st, true) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            q.options.forEachIndexed { i, o -> OptionItem(i, o.text, showAnswers && o.correct, st, true) }
        }
    }
}

@Composable
private fun OptionItem(index: Int, text: String, correct: Boolean, st: PaperStyle, fillText: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionBadge(
            label = optionLabel(index, st.bn),
            textColor = if (correct) Color.White else st.color,
            family = st.fonts.familyFor(optionLabel(index, st.bn)),
            textSize = st.sp(0.768f),
            diameter = st.dp(1.152f),
            fill = if (correct) GREEN else Color.Transparent,
            stroke = if (correct) GREEN else st.color,
        )
        RichTextView(
            text,
            if (fillText) Modifier.weight(1f) else Modifier,
            color = st.color, fontFamily = st.family,
            fontWeight = if (correct) FontWeight.Bold else FontWeight.Normal,
            fontSize = st.sp(0.96f), lineHeight = st.sp(1.344f), fonts = st.fonts,
        )
    }
}

/** Circle with the option letter exactly in the middle (uses the real glyph bounds, so Bangla letters are centred too). */
@Composable
private fun OptionBadge(
    label: String,
    textColor: Color,
    family: FontFamily,
    textSize: TextUnit,
    diameter: Dp,
    fill: Color,
    stroke: Color,
) {
    val resolver = LocalFontFamilyResolver.current
    val typeface = remember(family) {
        resolver.resolve(family, FontWeight.Bold, FontStyle.Normal, FontSynthesis.All).value as? android.graphics.Typeface
    }
    Canvas(Modifier.size(diameter)) {
        val strokeW = 1.dp.toPx()
        val r = size.minDimension / 2f
        drawCircle(fill, radius = r)
        drawCircle(stroke, radius = r - strokeW / 2f, style = Stroke(strokeW))
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = textColor.toArgb()
            paint.textSize = textSize.toPx()
            if (typeface != null) paint.typeface = typeface
            val bounds = android.graphics.Rect()
            paint.getTextBounds(label, 0, label.length, bounds)
            val x = size.width / 2f - (bounds.left + bounds.right) / 2f
            val y = size.height / 2f - (bounds.top + bounds.bottom) / 2f
            canvas.nativeCanvas.drawText(label, x, y, paint)
        }
    }
}
