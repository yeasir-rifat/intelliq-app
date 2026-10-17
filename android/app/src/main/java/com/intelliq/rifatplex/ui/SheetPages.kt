package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AnswerKey
import com.intelliq.rifatplex.data.BrandSettings
import com.intelliq.rifatplex.data.DocFields
import com.intelliq.rifatplex.data.DocSettings
import com.intelliq.rifatplex.data.GradingRules
import com.intelliq.rifatplex.data.KeyItem
import com.intelliq.rifatplex.data.KeyQrPayload
import com.intelliq.rifatplex.data.PAGE_SIZES_MM
import com.intelliq.rifatplex.data.OmrGeometry
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.QrPayload
import com.intelliq.rifatplex.data.SheetStyle
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.omrRows
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.ui.theme.IQ
import kotlin.math.max
import kotlin.math.roundToInt

private fun mmToDp(mm: Float): Dp = (mm * 96f / 25.4f).dp

/**
 * OMR sheet / answer-grid ("উত্তরমালা") pages, for the preview AND the PDF export. Same contract as [PaperPages]:
 *  - normal use: the pages are centred in [containerWidth] and scaled by [scale], like the question paper preview
 *  - PDF export ([printPage] >= 0): exactly one page-sized page, no shadow / border / gap, and [onPrintLayout] is told
 *    (page, total pages) so [PdfExport] knows when the page is ready
 *
 * The OMR sheet ([OmrPage]) holds 50 answers per sheet, so 51+ MCQs give a second sheet and so on.
 * The answer sheet ([KeyPage], "উত্তরমালা") lists "printed number (correct option)" of every MCQ, e.g. "১ (খ)", and has a
 * QR code on every page (step 4.8): the bridge to the OMR sheets, see [KeyQrPayload].
 * Both sheets show the numbers PRINTED on the question paper. With Document AutoFix on, those are learned first by
 * [NumberingProbe] (the PDF waits for them, see [numbersReady]); the answer snapshot of step 4.3 uses the same numbers.
 */
@Composable
fun SheetPages(
    kind: DocKind,
    questions: List<Question>,
    setCode: String?,
    doc: DocFields,
    settings: DocSettings,
    scale: Float,
    containerWidth: Dp,
    modifier: Modifier = Modifier,
    brand: BrandSettings = BrandSettings(),
    /** All set names of the paper (when sets are on); the OMR set-code column offers them. */
    setCodes: List<String> = emptyList(),
    /** Permanent id of the exam ([com.intelliq.rifatplex.data.AppData.examId]); printed in the QR code of every OMR sheet. */
    examId: String = "",
    /** Marking rules of the exam ([com.intelliq.rifatplex.data.AppData.grading]); printed into the QR code of the answer sheet. */
    grading: GradingRules = GradingRules(),
    printPage: Int = -1,
    onPrintLayout: ((Int, Int) -> Unit)? = null,
) {
    val lang = LocalLang.current
    val bn = lang == Lang.BN
    val size = PAGE_SIZES_MM[kind.pageSizeKey(settings)] ?: PAGE_SIZES_MM.getValue("A4")
    val print = printPage >= 0
    val pageBg = Color(SheetStyle.PAGE_COLOR_ARGB) // OMR sheet and answer sheet: always white (SheetStyle)

    // printed number of every question (question id -> number): the same numbers as on the question paper.
    // Without AutoFix they come straight from arrangeSections; with AutoFix the order is only known after the
    // paper's text has been measured, so NumberingProbe reports them (null until then).
    // Step 4.3: the OMR sheet needs them too (its rows are labelled with the printed numbers).
    val needNumbers = kind == DocKind.KEY || kind == DocKind.OMR
    val needProbe = needNumbers && settings.autoFix
    var probed by remember { mutableStateOf<Map<String, Int>?>(null) }
    val plainNumbers: Map<String, Int> = if (needNumbers) {
        remember(questions, settings) { AnswerKey.plainNumbers(questions, settings) }
    } else emptyMap()
    val numbers: Map<String, Int>? = when {
        !needNumbers -> null
        needProbe -> probed
        else -> plainNumbers
    }
    val numbersReady = !needNumbers || numbers != null

    // OMR: the printed numbers of the MCQs, 50 per sheet (see omrRows). While the AutoFix numbers are still being
    // learned the plain numbers are shown, so the preview keeps its page count; the PDF waits for the real ones.
    val chunks: List<List<Int>> = if (kind == DocKind.OMR) {
        val rowNumbers = numbers ?: plainNumbers
        remember(questions, rowNumbers) { omrRows(AnswerKey.printedMcqs(questions, rowNumbers).map { it.first }) }
    } else listOf(emptyList())

    // answer sheet: one cell per MCQ, paginated by fixed geometry
    val keyAll: List<KeyEntry> = if (kind == DocKind.KEY && numbers != null) {
        remember(questions, numbers, bn) { keyEntries(questions, numbers, bn) }
    } else emptyList()
    val keyHasMcq = remember(questions) { questions.any { !it.isEmpty() && it.kind() == QType.MCQ } }
    val keyCounts: List<Int> = if (kind == DocKind.KEY) keyPaginate(keyAll.size, size.second) else emptyList()
    val keyPages: List<List<KeyEntry>> = if (kind == DocKind.KEY) {
        var from = 0
        keyCounts.map { c -> keyAll.subList(from, from + c).also { from += c } }
    } else emptyList()
    val keyUnanswered = keyAll.count { it.answer == null }

    val pageCount = if (kind == DocKind.KEY) keyPages.size else chunks.size
    val shown: List<Int> = if (print) listOf(printPage.coerceIn(0, pageCount - 1)) else (0 until pageCount).toList()
    val showLabels = !print && pageCount > 1

    Layout(
        content = {
            shown.forEach { pi ->
                SheetPageView(
                    kind, chunks.getOrElse(pi) { emptyList() }, keyPages.getOrElse(pi) { emptyList() }, keyHasMcq, keyUnanswered,
                    size.first, settings, pi, pageCount, questions, setCode, setCodes, examId, grading, doc, brand, lang, pageBg, print,
                )
            }
            if (showLabels) {
                (0 until pageCount).forEach { pi ->
                    Text(
                        "${trKey("doc.page", lang)} ${formatNumber(pi + 1, bn)}",
                        color = IQ.c.textFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
                    )
                }
            }
            if (needProbe) {
                NumberingProbe(questions, setCode, doc, lang, settings, brand) { probed = it }
            }
        },
        modifier = modifier,
    ) { measurables, _ ->
        val pageW = mmToDp(size.first).roundToPx()
        val pageH = mmToDp(size.second).roundToPx()
        val pages = shown.indices.map { measurables[it].measure(Constraints.fixed(pageW, pageH)) }
        val labels = if (showLabels) (0 until pageCount).map { measurables[shown.size + it].measure(Constraints()) } else emptyList()
        // the probe draws nothing but must be measured, that is when it plans the paper and reports the numbers
        if (needProbe) measurables.last().measure(Constraints.fixed(0, 0))
        // the PDF must not take the page before the printed numbers are known
        if (numbersReady) onPrintLayout?.invoke(if (print) shown[0] else 0, pageCount)
        if (print) {
            return@Layout layout(pageW, pageH) { pages[0].place(0, 0) }
        }
        val gap = 28.dp.roundToPx()
        val side = 16.dp.roundToPx()
        val padTop = 24.dp.roundToPx()
        val labelOffset = 26.dp.roundToPx()
        val totalW = pageW + 2 * side
        val totalH = padTop * 2 + pageCount * pageH + (pageCount - 1) * gap
        val scaledW = (totalW * scale).roundToInt()
        val scaledH = (totalH * scale).roundToInt()
        val reportW = max(containerWidth.roundToPx(), scaledW)
        val pageX = ((reportW - pageW * scale) / 2f).roundToInt()
        layout(reportW, scaledH) {
            pages.forEachIndexed { pi, p ->
                val y = ((padTop + pi * (pageH + gap)) * scale).roundToInt()
                p.placeWithLayer(pageX, y) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                labels.getOrNull(pi)?.place(pageX, y - (labelOffset * scale).roundToInt())
            }
        }
    }
}

/** The page itself: paper colour, and (preview only) the soft shadow and border of a sheet of paper. */
@Composable
private fun SheetPageView(
    kind: DocKind,
    numbers: List<Int>,
    keyEntries: List<KeyEntry>,
    keyHasMcq: Boolean,
    keyUnanswered: Int,
    pageWmm: Float,
    settings: DocSettings,
    pageIndex: Int,
    pageCount: Int,
    questions: List<Question>,
    setCode: String?,
    setCodes: List<String>,
    examId: String,
    grading: GradingRules,
    doc: DocFields,
    brand: BrandSettings,
    lang: Lang,
    pageBg: Color,
    print: Boolean,
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
        when {
            kind == DocKind.KEY -> {
                // the QR code of this page: same exam id / set as the OMR sheets, plus this page's answers
                val keyQr = KeyQrPayload.forPage(
                    examId, OmrGeometry.setIndexOf(setCode, setCodes), pageIndex, pageCount,
                    keyEntries.map { KeyItem(it.number, it.positions) }, grading,
                )
                KeyPage(keyEntries, pageIndex, pageCount, keyHasMcq, keyUnanswered, pageWmm, doc, brand, settings, setCode, lang, Modifier.fillMaxSize(), qr = keyQr)
            }
            kind == DocKind.OMR && numbers.isNotEmpty() ->
                OmrPage(
                    numbers, pageIndex, pageCount, doc, brand, setCodes, settings.omr, lang, Modifier.fillMaxSize(),
                    qr = QrPayload.forSheet(examId, OmrGeometry.setIndexOf(setCode, setCodes), pageIndex, pageCount),
                )
            else -> SheetPageContent(questions, setCode, doc)
        }
    }
}

/** The OMR page when there is no MCQ yet: scanner corner marks, title and a hint (the real sheet has 50 rows from the first MCQ on). */
@Composable
private fun BoxScope.SheetPageContent(questions: List<Question>, setCode: String?, doc: DocFields) {
    val ink = Color(0xFF111111)

    // the four black squares a scanner uses to find the page corners
    val markSize = mmToDp(5f)
    val inset = mmToDp(8f)
    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
        Box(Modifier.align(a).padding(inset).size(markSize).background(ink))
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = mmToDp(24f), vertical = mmToDp(26f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (doc.title.isNotBlank()) {
            Text(doc.title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont(), textAlign = TextAlign.Center)
        }
        if (doc.subtitle.isNotBlank()) {
            Text(doc.subtitle, color = ink, fontSize = 14.sp, fontFamily = uiFont(), textAlign = TextAlign.Center)
        }
        Text(tr("sheet.titleOmr"), color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(), textAlign = TextAlign.Center)
        if (setCode != null) {
            Text("${tr("doc.set")}: $setCode", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = uiFont())
        }
        Text(tr("sheet.needMcq"), color = Color(0xFF6B7280), fontSize = 13.sp, fontFamily = uiFont(), textAlign = TextAlign.Center)
    }
}
