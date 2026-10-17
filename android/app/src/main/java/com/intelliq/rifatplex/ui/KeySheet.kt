package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intelliq.rifatplex.data.AnswerKey
import com.intelliq.rifatplex.data.BrandSettings
import com.intelliq.rifatplex.data.DocFields
import com.intelliq.rifatplex.data.DocSettings
import com.intelliq.rifatplex.data.SheetStyle
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.optionLabel

/*
 * Answer sheet ("উত্তরমালা", DocKind.KEY): the correct option of every MCQ in a grid, for the examiner.
 *
 * Every cell reads "printed question number -> correct option(s)", e.g. "১ → খ". The numbers are the ones printed on
 * the question paper (see NumberingProbe in Paper.kt), so they stay right when Document AutoFix reorders questions.
 *
 * The geometry is fixed (millimetres), so the page count is known without measuring any text: [keyPaginate] tells
 * [SheetPages] how many entries go on each page, and [KeyPage] draws one page.
 */

/** Cells per row of the grid. */
internal const val KEY_PER_ROW = 5

private const val KEY_MARGIN_MM = SheetStyle.MARGIN_MM // 0.75 inch, fixed
private const val KEY_CELL_H_MM = 9f
private const val KEY_HEADER_FIRST_MM = 40f // institution, exam, title box (+ set code)
private const val KEY_HEADER_NEXT_MM = 14f // slim header of the following pages
private const val KEY_FOOTER_MM = 14f // warning line + page label

/** Shown in a cell when the MCQ has no correct option marked. */
private const val KEY_NONE = "—"

private fun keyMm(mm: Float): Dp = (mm * 96f / 25.4f).dp

/** One cell of the grid: [answer] = null when no correct option is marked. */
internal data class KeyEntry(val number: Int, val answer: String?)

/**
 * The MCQs of [questions] with their printed number, in number order. [numbers] maps question id -> printed number
 * (from `arrangeSections`, or from NumberingProbe when AutoFix is on). Option letters follow the option order of the
 * question as given, so the shuffled options of a set are already right.
 */
internal fun keyEntries(questions: List<Question>, numbers: Map<String, Int>, bn: Boolean): List<KeyEntry> =
    // the same list the OMR rows and the answer snapshot use (AnswerKey.printedMcqs), so they always agree
    AnswerKey.printedMcqs(questions, numbers).map { (n, q) ->
        val letters = AnswerKey.correctPositions(q).sorted()
        KeyEntry(n, letters.takeIf { it.isNotEmpty() }?.joinToString(", ") { optionLabel(it, bn) })
    }

private fun keyRows(pageHmm: Float, first: Boolean): Int {
    val free = pageHmm - 2f * KEY_MARGIN_MM - (if (first) KEY_HEADER_FIRST_MM else KEY_HEADER_NEXT_MM) - KEY_FOOTER_MM
    return (free / KEY_CELL_H_MM).toInt().coerceAtLeast(1)
}

/** How many entries go on each page (at least one page, even with no entries). */
internal fun keyPaginate(count: Int, pageHmm: Float): List<Int> {
    val firstCap = keyRows(pageHmm, true) * KEY_PER_ROW
    val nextCap = keyRows(pageHmm, false) * KEY_PER_ROW
    if (count <= firstCap) return listOf(count)
    val out = ArrayList<Int>()
    out.add(firstCap)
    var left = count - firstCap
    while (left > 0) {
        val c = minOf(left, nextCap)
        out.add(c)
        left -= c
    }
    return out
}

/**
 * One page of the answer sheet. [entries] are the cells of THIS page. [hasMcq] = false draws the "add an MCQ" hint
 * instead of a grid. [unanswered] (MCQs without a marked answer in the whole sheet) adds a warning on the last page.
 */
@Composable
internal fun KeyPage(
    entries: List<KeyEntry>,
    pageIndex: Int,
    pageCount: Int,
    hasMcq: Boolean,
    unanswered: Int,
    pageWmm: Float,
    doc: DocFields,
    brand: BrandSettings,
    settings: DocSettings,
    setCode: String?,
    lang: Lang,
    modifier: Modifier = Modifier,
) {
    val bn = lang == Lang.BN
    // fixed look (SheetStyle): fonts, text colour, sizes and margin never come from Settings
    val fonts = sheetFonts
    val density = LocalDensity.current
    val ink = Color(SheetStyle.INK_ARGB)
    val line = ink.copy(alpha = 0.55f)
    val warn = Color(0xFFB91C1C)
    fun sp(pt: Float) = with(density) { (pt * 96f / 72f).dp.toSp() }
    fun t(key: String) = trKey(key, lang)
    @Composable
    fun Txt(
        s: String, pt: Float, bold: Boolean = false, color: Color = ink, modifier: Modifier = Modifier,
        align: TextAlign = TextAlign.Center,
    ) = Text(
        fonts.apply(s), modifier, color = color, fontFamily = fonts.base, fontSize = sp(pt),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )

    val brandName = if (brand.enabled && brand.showName) brand.name.trim() else ""
    val line1 = brandName.ifEmpty { doc.title.trim() }
    val line2 = (if (brandName.isNotEmpty()) listOf(doc.title.trim(), doc.subtitle.trim()) else listOf(doc.subtitle.trim()))
        .filter { it.isNotEmpty() }.joinToString(" • ")
    val first = pageIndex == 0
    val last = pageIndex == pageCount - 1
    val contentW = pageWmm - 2f * KEY_MARGIN_MM
    val cellW = contentW / KEY_PER_ROW
    val titleText = t("sheet.titleKey")
    val setText = if (setCode != null) "${t("doc.set")}: $setCode" else null

    Box(modifier.fillMaxSize().padding(keyMm(KEY_MARGIN_MM))) {
        Column(Modifier.fillMaxSize()) {
            // ---- header
            if (first) {
                Column(
                    Modifier.fillMaxWidth().height(keyMm(KEY_HEADER_FIRST_MM)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (line1.isNotEmpty()) Txt(line1, SheetStyle.HEADING_PT, bold = true, modifier = Modifier.fillMaxWidth())
                    if (line2.isNotEmpty()) Txt(line2, SheetStyle.SUBHEADING_PT, bold = true, modifier = Modifier.fillMaxWidth().padding(top = keyMm(1f)))
                    Box(Modifier.fillMaxWidth().padding(top = keyMm(4f)), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .border(1.2.dp, ink, RoundedCornerShape(2.dp))
                                .padding(horizontal = keyMm(10f), vertical = keyMm(1.8f)),
                        ) { Txt(titleText, SheetStyle.HEADING_PT, bold = true) }
                        if (setText != null) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .border(1.2.dp, ink, RoundedCornerShape(3.dp))
                                    .padding(horizontal = keyMm(3f), vertical = keyMm(1.2f)),
                            ) { Txt(setText, SheetStyle.SUBHEADING_PT, bold = true) }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().height(keyMm(KEY_HEADER_NEXT_MM))) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Txt(line1, SheetStyle.BODY_PT, modifier = Modifier.weight(1f), align = TextAlign.Start)
                        Txt(listOfNotNull(titleText, setText).joinToString(" • "), SheetStyle.SUBHEADING_PT, bold = true, modifier = Modifier.padding(start = keyMm(4f)))
                    }
                    Box(Modifier.padding(top = keyMm(2f)).fillMaxWidth().height(1.dp).background(line))
                }
            }

            // ---- grid (or the hint when there is no MCQ)
            if (!hasMcq) {
                Txt(t("key.needMcq"), SheetStyle.BODY_PT, color = Color(0xFF6B7280), modifier = Modifier.fillMaxWidth().padding(top = keyMm(8f)))
            } else {
                entries.chunked(KEY_PER_ROW).forEach { rowEntries ->
                    Row(Modifier.height(keyMm(KEY_CELL_H_MM))) {
                        rowEntries.forEach { e ->
                            Box(
                                Modifier.width(keyMm(cellW)).height(keyMm(KEY_CELL_H_MM)).border(0.6.dp, line),
                                contentAlignment = Alignment.Center,
                            ) {
                                val missing = e.answer == null
                                Txt(
                                    "${formatNumber(e.number, bn)} → ${e.answer ?: KEY_NONE}", SheetStyle.BODY_PT,
                                    color = if (missing) warn else ink,
                                    modifier = Modifier.padding(horizontal = keyMm(1f)),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- footer: warning (last page) and page label
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(keyMm(KEY_FOOTER_MM - 2f)),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (last && hasMcq && unanswered > 0) {
                Txt(
                    t("key.warn").replace("{n}", formatNumber(unanswered, bn)), SheetStyle.BODY_PT, color = warn,
                    modifier = Modifier.fillMaxWidth().padding(bottom = keyMm(1.5f)),
                )
            }
            if (pageCount > 1) {
                Txt("${t("doc.page")} ${formatNumber(pageIndex + 1, bn)}/${formatNumber(pageCount, bn)}", SheetStyle.BODY_PT, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
