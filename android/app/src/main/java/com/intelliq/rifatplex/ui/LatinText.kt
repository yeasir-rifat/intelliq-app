package com.intelliq.rifatplex.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.intelliq.rifatplex.ui.theme.Lato
import com.intelliq.rifatplex.ui.theme.BanglaUi

/*
 * In Bangla mode the whole UI uses SolaimanLipi (BanglaUi), which also contains Latin glyphs, so English words and
 * numbers inside Bangla text would be drawn in SolaimanLipi instead of Lato. This file fixes that in one place:
 * every Latin run (A-Z, a-z, 0-9 and the spaces/punctuation between two such characters) gets Lato,
 * Bangla characters keep SolaimanLipi. Only the app UI font is affected, paper fonts are untouched.
 */

private fun isLatinChar(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9'
private fun isBanglaChar(ch: Char): Boolean = ch in '\u0980'..'\u09FF'

/** Returns the [start, end) ranges of Latin runs. A neutral character (space, punctuation) joins two Latin chars. */
internal fun latinRuns(s: CharSequence): List<IntRange> {
    val out = ArrayList<IntRange>()
    var i = 0
    val n = s.length
    while (i < n) {
        if (!isLatinChar(s[i])) { i++; continue }
        var end = i + 1
        var j = i + 1
        while (j < n) {
            val ch = s[j]
            if (isLatinChar(ch)) { end = j + 1; j++ }
            else if (!isBanglaChar(ch) && !ch.isLetter()) { j++ }
            else break
        }
        out.add(i until end)
        i = end
    }
    return out
}

internal fun withLatinFont(text: String): AnnotatedString {
    val runs = latinRuns(text)
    if (runs.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (r in runs) addStyle(SpanStyle(fontFamily = Lato), r.first, r.last + 1)
    }
}

/** For BasicTextField: typed English stays in Lato while the app language is Bangla. */
object LatinVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val runs = latinRuns(text.text)
        if (runs.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            for (r in runs) addStyle(SpanStyle(fontFamily = Lato), r.first, r.last + 1)
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val bangla = fontFamily === BanglaUi || (fontFamily == null && style.fontFamily === BanglaUi)
    val shown = remember(text, bangla) { if (bangla) withLatinFont(text) else AnnotatedString(text) }
    androidx.compose.material3.Text(
        text = shown, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        inlineContent = emptyMap<String, InlineTextContent>(),
        onTextLayout = onTextLayout ?: {}, style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = inlineContent,
        onTextLayout = onTextLayout ?: {}, style = style,
    )
}
