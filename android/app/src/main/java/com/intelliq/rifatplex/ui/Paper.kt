package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import com.intelliq.rifatplex.data.BRAND_BOARD
import com.intelliq.rifatplex.data.BRAND_LETTER
import com.intelliq.rifatplex.data.BrandSettings
import com.intelliq.rifatplex.data.DIV_DOUBLE
import com.intelliq.rifatplex.data.DIV_NONE
import com.intelliq.rifatplex.data.DIV_THICK
import com.intelliq.rifatplex.data.POS_CENTER
import com.intelliq.rifatplex.data.POS_END
import com.intelliq.rifatplex.data.POS_START
import com.intelliq.rifatplex.data.PicCache
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
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
import com.intelliq.rifatplex.data.ANS_COLOR_BLACK
import com.intelliq.rifatplex.data.ANS_LAYOUT_CORNER
import com.intelliq.rifatplex.data.ANS_LAYOUT_EXPL
import com.intelliq.rifatplex.data.ANS_LAYOUT_FILL
import com.intelliq.rifatplex.data.DocSettings
import com.intelliq.rifatplex.data.Placed
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.arrangeSections
import com.intelliq.rifatplex.data.colsOf
import com.intelliq.rifatplex.data.dividerOn
import com.intelliq.rifatplex.data.formatMarks
import com.intelliq.rifatplex.data.lineOn
import com.intelliq.rifatplex.data.sectionInstruction
import com.intelliq.rifatplex.data.sectionTitle
import com.intelliq.rifatplex.data.subLabel
import com.intelliq.rifatplex.data.totalMarks
import com.intelliq.rifatplex.data.PAGE_SIZES_MM
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.optionLabel
import com.intelliq.rifatplex.data.parseHtml
import com.intelliq.rifatplex.data.plainText
import com.intelliq.rifatplex.data.toHtml
import com.intelliq.rifatplex.ui.theme.IQ
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val GRAY_TEXT = Color(0xFF374151)
private val GRAY_RULE = Color(0xFF9CA3AF)
private val GREEN = Color(0xFF16A34A)

fun parseHex(s: String, fallback: Color): Color =
    try { Color(android.graphics.Color.parseColor(s)) } catch (e: Exception) { fallback }

private fun mmDp(mm: Float): Dp = (mm * 96f / 25.4f).dp

/** Page margin on all four sides: 0.75 inch = 19.05 mm (website --page-mt/mr/mb/ml). */
private const val PAGE_MARGIN_MM = 19.05f

/** Document typography. 1em == basePx dp (font size in pt * 96/72), independent of the system font scale. */
private class PaperStyle(
    val fonts: PaperFonts,
    val basePx: Float,
    val color: Color,
    val bn: Boolean,
    val density: Density,
    val showAns: Boolean = true,
    val showExpl: Boolean = true,
    val ansLayout: String = ANS_LAYOUT_EXPL,
    val ansColor: Color = GREEN,
    val boldQuestions: Boolean = false,
) {
    val family: FontFamily get() = fonts.base

    /** Text with Bangla / English / formula fonts applied per character. */
    fun t(s: String): AnnotatedString = fonts.apply(s)

    fun sp(em: Float): TextUnit = with(density) { (basePx * em).dp.toSp() }
    fun dp(em: Float): Dp = (basePx * em).dp
}

/* ───────────── layout model ───────────── */

private val INK = Color(0xFF111111)
private val DIVIDER = Color(0xFF6B7280)

/**
 * One piece of the paper that is laid out as a unit (website `__iqExpand`). A normal question is one piece. A Creative
 * question with several sub-questions (or with answers shown) is split into one piece per sub-question, followed by one
 * piece per answer, so pages and columns fill without blank gaps.
 */
private class Item(
    val id: Int,
    val q: Question,
    val number: Int,
    val type: String,
    val heading: Boolean, // a section heading is drawn above this piece
    val noHead: Boolean, // continuation piece: no number / stimulus
    val subs: List<Int>?, // Creative: sub-questions shown in this piece (null = all)
    val ansSub: Int?, // Creative answer piece: the sub-question whose answer it shows
    val ansFirst: Boolean,
    val more: Boolean, // more pieces of the same question follow (small bottom gap)
    val gid: Int = 0, // the question (index in the placed list) this piece belongs to
) {
    fun withNumber(n: Int) = Item(id, q, n, type, heading, noHead, subs, ansSub, ansFirst, more, gid)
}

/** Result of Document AutoFix: the new question order (by gid), the new numbers, and the pages of the question paper. */
private class SmartPlan(val order: List<Int>, val numbers: Map<Int, Int>, val pages: List<List<Item>>)

private sealed interface Block
private class HeadBlock(val item: Item) : Block
private class RunBlock(val k: Int, val columns: List<List<Item>>, val divider: Boolean) : Block
private class PageModel(val first: Boolean, val blocks: List<Block>)

/**
 * Heights measured for paper items, kept between layout passes. Before this, every keystroke re-measured every
 * question of the paper; now only a question whose content changed (new key) is measured again. The whole cache is
 * dropped when something that affects every height changes (font, size, language, answers on/off, density...).
 */
private class HeightCache {
    var env: Any? = null
    val items = HashMap<ItemKey, Int>()
    val heads = HashMap<Pair<String, Int>, Int>()

    fun validate(newEnv: Any) {
        if (env != newEnv || items.size > 4000) {
            items.clear(); heads.clear(); env = newEnv
        }
    }
}

/** One cache for the whole app: survives closing the preview page and is reused by every page of a PDF export. */
private val sharedHeightCache = HeightCache()

/** Heights of the question-paper pieces (answers off), used by AutoFix when the answer key is drawn, so the key follows the paper's order. */
private val planHeightCache = HeightCache()

private data class ItemKey(
    val q: Question, val type: String, val number: Int, val noHead: Boolean, val heading: Boolean, val subs: List<Int>?,
    val ansSub: Int?, val ansFirst: Boolean, val more: Boolean, val k: Int, val colW: Int,
)

private fun expandItems(placed: List<Placed>, showAnswers: Boolean): List<Item> {
    val out = ArrayList<Item>()
    var curG = 0
    fun add(p: Placed, heading: Boolean, noHead: Boolean, subs: List<Int>?, ansSub: Int?, ansFirst: Boolean, more: Boolean) {
        out.add(Item(out.size, p.q, p.n, p.type, heading, noHead, subs, ansSub, ansFirst, more, curG))
    }
    placed.forEachIndexed { gi, p ->
        curG = gi
        val q = p.q
        if (q.kind() == QType.CREATIVE) {
            val vis = q.subs.indices.filter { plainText(q.subs[it].text).isNotBlank() }
            val av = vis.filter { plainText(q.subs[it].answer).isNotBlank() }
            val ans = showAnswers && av.isNotEmpty()
            if (vis.isNotEmpty() && (vis.size > 1 || ans)) {
                vis.forEachIndexed { k, si ->
                    add(p, k == 0 && p.firstOfSection, k > 0, listOf(si), null, false, k < vis.size - 1 || ans)
                }
                if (ans) av.forEachIndexed { k, si -> add(p, false, true, emptyList(), si, k == 0, k < av.size - 1) }
                return@forEachIndexed
            }
        }
        add(p, p.firstOfSection, false, null, null, false, false)
    }
    return out
}

/** How many items go in each of [k] columns so that the tallest column is as short as possible (earlier columns win ties). */
private fun splitColumns(hs: List<Int>, k: Int): List<Int> {
    val n = hs.size
    if (k <= 1 || n <= 1) return listOf(n)
    val pre = IntArray(n + 1)
    for (i in 0 until n) pre[i + 1] = pre[i] + hs[i]
    val inf = Int.MAX_VALUE
    val dp = Array(k + 1) { IntArray(n + 1) { inf } }
    val par = Array(k + 1) { IntArray(n + 1) }
    for (i in 0..n) dp[1][i] = pre[i]
    for (j in 2..k) {
        for (i in 0..n) {
            var best = inf
            var bm = 0
            for (m in i downTo 0) {
                val prev = dp[j - 1][m]
                if (prev == inf) continue
                val v = max(prev, pre[i] - pre[m])
                if (v < best) { best = v; bm = m }
            }
            dp[j][i] = best
            par[j][i] = bm
        }
    }
    val counts = IntArray(k)
    var i = n
    for (j in k downTo 2) {
        val m = par[j][i]
        counts[j - 1] = i - m
        i = m
    }
    counts[0] = i
    return counts.toList()
}

/**
 * Renders the question paper as real-size pages (mm → dp at 96 dpi), paginated like the website:
 * questions grouped in sections (heading + own numbering), each run of questions in 1 / 2 / 3 balanced columns with an
 * optional divider line, questions never split (a Creative question is split into sub-question pieces instead),
 * usable height = page height − 2 × 19.05 mm (0.75 inch margins). [scale] is applied to the whole stack; [containerWidth] centres the pages.
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
    /** Institution branding of the header (off unless switched on in the settings). */
    brand: BrandSettings = BrandSettings(),
    /** PDF export: draw only this page (0-based) at exactly page size, no shadow/border/gap. -1 = normal preview. */
    printPage: Int = -1,
    /** PDF export: called while laying out with (page that was laid out, total pages). */
    onPrintLayout: ((Int, Int) -> Unit)? = null,
    /** Called while laying out with the printed number of every question (question id -> number), AutoFix included. */
    onNumbering: ((Map<String, Int>) -> Unit)? = null,
    /** Plan only (draws nothing, size 0): used by [NumberingProbe] to learn the printed numbers. */
    numberingOnly: Boolean = false,
) {
    val density = LocalDensity.current
    val bn = lang == Lang.BN
    val st = PaperStyle(
        fonts = paperFonts(settings.fontEn, settings.fontBn, bn),
        basePx = settings.fontSize * 96f / 72f,
        color = parseHex(settings.fontColor, Color(0xFF111111)),
        bn = bn,
        density = density,
        showAns = settings.showAnswer,
        showExpl = settings.showExplanation,
        ansLayout = settings.answerLayout,
        ansColor = if (settings.answerColor == ANS_COLOR_BLACK) Color(0xFF111111) else GREEN,
        boldQuestions = settings.boldQuestions,
    )
    val size = PAGE_SIZES_MM[settings.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
    val wMm = size.first
    val hMm = size.second
    val pageBg = parseHex(settings.pageColor, Color.White)
    val showMarks = settings.showMarks
    // header "Marks" is filled in from the questions when the field is left empty
    val autoMarks = questions.totalMarks().takeIf { it > 0.0 }?.let { formatMarks(it, bn) }
    val heightCache = sharedHeightCache
    val cacheEnv = listOf(settings, bn, density.density, density.fontScale, showAnswers, showMarks, brand, setCode)
    val planEnv = listOf(settings, bn, density.density, density.fontScale, false, showMarks, brand, setCode)

    SubcomposeLayout(modifier) { _ ->
        heightCache.validate(cacheEnv)
        val pageW = mmDp(wMm).roundToPx()
        val pageH = mmDp(hMm).roundToPx()
        val padH = mmDp(PAGE_MARGIN_MM).roundToPx()
        val contentW = pageW - 2 * padH
        val usable = mmDp(hMm - 2f * PAGE_MARGIN_MM).roundToPx()

        fun gapPx(k: Int): Int = when (k) {
            2 -> mmDp(9f).roundToPx()
            3 -> mmDp(6f).roundToPx()
            else -> 0
        }
        fun colWPx(k: Int): Int = (contentW - (k - 1) * gapPx(k)) / k

        val placed = arrangeSections(questions, settings)
        val items0 = expandItems(placed, showAnswers && settings.showAnswer)
        val n = items0.size
        val sectioned = placed.any { it.firstOfSection } // numbering restarts in every section

        val hdrH = subcompose("hdr") { HeaderSection(doc, st, showAnswers, autoMarks, brand, setCode) }
            .sumOf { it.measure(Constraints.fixedWidth(contentW)).height }

        // every (piece, column count) and every heading is measured once
        fun itemH(it: Item, k: Int, cache: HeightCache = heightCache, ans: Boolean = showAnswers): Int {
            val key = ItemKey(it.q, it.type, it.number, it.noHead, it.heading, it.subs, it.ansSub, it.ansFirst, it.more, k, colWPx(k))
            return cache.items.getOrPut(key) {
                subcompose("m${if (cache === heightCache) "" else "p"}${it.id}_${k}_${it.number}") { ItemView(it, k, st, ans, showMarks) }
                    .sumOf { p -> p.measure(Constraints.fixedWidth(colWPx(k))).height }
            }
        }
        fun headH(it: Item): Int = heightCache.heads.getOrPut(it.type to contentW) {
            subcompose("h${it.id}") { SectionHeading(it.type, settings, st, 10.dp) }
                .sumOf { p -> p.measure(Constraints.fixedWidth(contentW)).height }
        }

        /** Blocks (headings + column runs) and their total height for the pieces [list] on one page. */
        fun buildOf(list: List<Item>, cache: HeightCache = heightCache, ans: Boolean = showAnswers): Pair<List<Block>, Int> {
            val blocks = ArrayList<Block>()
            var total = 0
            val buf = ArrayList<Item>()
            var cur = -1
            var dvKey = ""
            fun flush() {
                if (buf.isEmpty()) return
                val k = max(1, min(cur, buf.size))
                val hs = buf.map { itemH(it, k, cache, ans) }
                val counts = splitColumns(hs, k)
                val cols = ArrayList<List<Item>>()
                var from = 0
                var tallest = 0
                counts.forEach { c ->
                    cols.add(buf.subList(from, from + c).toList())
                    tallest = max(tallest, hs.subList(from, from + c).sum())
                    from += c
                }
                blocks.add(RunBlock(k, cols, dvKey.isNotEmpty() && k >= 2))
                total += tallest
                buf.clear()
            }
            for (item in list) {
                if (item.heading) {
                    flush()
                    cur = -1
                    blocks.add(HeadBlock(item))
                    total += headH(item)
                }
                val key = if (settings.dividerOn(item.type)) item.type else ""
                if (key != dvKey) { flush(); dvKey = key }
                val c = settings.colsOf(item.q)
                if (c != cur) { flush(); cur = c }
                buf.add(item)
            }
            flush()
            return blocks to total
        }

        fun availOf(first: Boolean, header: Int = hdrH): Int = if (first) max(usable - header, (usable * 0.3f).toInt()) else usable
        // AutoFix always plans against the QUESTION PAPER's header, so paper and answer key get the same order
        val planHdrH = if (!showAnswers) hdrH else
            subcompose("hdrPlan") { HeaderSection(doc, st, false, autoMarks, brand, setCode) }
                .sumOf { it.measure(Constraints.fixedWidth(contentW)).height }

        /**
         * Document AutoFix. Pages are filled in order as before; then the gap at the bottom of the page is filled by
         * moving up the next question(s) of the SAME section that fit into it (first fit, so the order changes as
         * little as possible). Questions are renumbered in the new order. It is always planned on the question paper
         * (answers off), so the answer key gets exactly the same order and numbers. Null = nothing to move.
         */
        fun smartPlan(): SmartPlan? {
            val cache = if (showAnswers) planHeightCache else heightCache
            if (showAnswers) planHeightCache.validate(planEnv)
            val src = if (showAnswers) expandItems(placed, false) else items0
            val rest = ArrayList(src)
            val out = ArrayList<List<Item>>()
            var moved = false
            var firstPg = true
            while (rest.isNotEmpty()) {
                val avail = availOf(firstPg, planHdrH)
                fun fits(list: List<Item>) = buildOf(list, cache, false).second <= avail
                var best = 1
                if (fits(rest)) {
                    best = rest.size
                } else {
                    var lo = 2
                    var hi = rest.size
                    while (lo <= hi) {
                        val mid = (lo + hi) / 2
                        if (fits(rest.subList(0, mid))) { best = mid; lo = mid + 1 } else hi = mid - 1
                    }
                }
                val page = ArrayList(rest.subList(0, best))
                val tail = ArrayList(rest.subList(best, rest.size))
                // never interleave another question into the middle of a split question
                if (tail.isNotEmpty() && !tail[0].noHead) {
                    val lastType = page.last().type
                    var i = 0
                    while (i < tail.size && tail[i].gid == tail[0].gid) i++ // that question did not fit
                    var tries = 0
                    while (i < tail.size && tries < 60) {
                        val head = tail[i]
                        if (head.type != lastType || head.heading) break // stay inside the section
                        var j = i
                        while (j < tail.size && tail[j].gid == head.gid) j++
                        tries++
                        val cand = ArrayList(tail.subList(i, j))
                        if (fits(page + cand)) {
                            page.addAll(cand)
                            repeat(j - i) { tail.removeAt(i) }
                            moved = true
                        } else i = j
                    }
                }
                out.add(page)
                rest.clear(); rest.addAll(tail)
                firstPg = false
            }
            if (!moved) return null

            val order = out.flatten().map { it.gid }.distinct()
            val typeOf = HashMap<Int, String>()
            src.forEach { typeOf[it.gid] = it.type }
            val counters = HashMap<String, Int>()
            val numbers = HashMap<Int, Int>()
            order.forEach { g ->
                val key = if (sectioned) typeOf[g].orEmpty() else ""
                val c = (counters[key] ?: 0) + 1
                counters[key] = c
                numbers[g] = c
            }
            // the new numbers can be a digit wider: make sure every page still fits, else keep the normal layout
            val pagesOut = out.map { pg -> pg.map { it.withNumber(numbers.getValue(it.gid)) } }
            pagesOut.forEachIndexed { pi, pg ->
                if (pg.size > 1 && buildOf(pg, cache, false).second > availOf(pi == 0, planHdrH)) return null
            }
            return SmartPlan(order, numbers, pagesOut)
        }

        val plan = if (settings.autoFix && n > 1) smartPlan() else null

        if (onNumbering != null) {
            // gid = index in [placed]; with AutoFix the plan holds the new numbers, else the placed numbers are printed
            val printed = HashMap<String, Int>()
            placed.forEachIndexed { gi, p -> printed[p.q.id] = plan?.numbers?.get(gi) ?: p.n }
            onNumbering(printed)
        }
        if (numberingOnly) return@SubcomposeLayout layout(0, 0) {}

        val pages = ArrayList<PageModel>()
        if (n == 0) pages.add(PageModel(true, emptyList()))
        if (plan != null && !showAnswers) {
            plan.pages.forEachIndexed { pi, pg -> pages.add(PageModel(pi == 0, buildOf(pg).first)) }
        } else {
            // normal layout (or the answer key following the paper's AutoFix order)
            val items = if (plan == null) items0 else {
                val byGroup = items0.groupBy { it.gid }
                plan.order.flatMap { g -> byGroup[g].orEmpty().map { it.withNumber(plan.numbers.getValue(g)) } }
            }
            var idx = 0
            var first = true
            while (idx < n) {
                val avail = availOf(first)
                var best = idx + 1
                if (buildOf(items.subList(idx, n)).second <= avail) {
                    best = n
                } else {
                    var lo = idx + 1
                    var hi = n
                    while (lo <= hi) {
                        val mid = (lo + hi) / 2
                        if (buildOf(items.subList(idx, mid)).second <= avail) { best = mid; lo = mid + 1 } else hi = mid - 1
                    }
                }
                pages.add(PageModel(first, buildOf(items.subList(idx, best)).first))
                idx = best
                first = false
            }
        }

        if (printPage >= 0) {
            val pi = printPage.coerceIn(0, pages.size - 1)
            val label = if (settings.showPageNumber) pageNumberText(pi + 1, pages.size, lang, bn) else null
            val printed = subcompose("p$pi") {
                PageView(
                    pages[pi], doc, setCode, st, bn, settings, showAnswers, showMarks, autoMarks, pageBg, brand = brand,
                    print = true, pageLabel = label,
                )
            }.map { it.measure(Constraints.fixed(pageW, pageH)) }
            onPrintLayout?.invoke(pi, pages.size)
            return@SubcomposeLayout layout(pageW, pageH) { printed.forEach { it.place(0, 0) } }
        }

        val pagePlaceables = pages.mapIndexed { pi, pg ->
            subcompose("p$pi") {
                PageView(
                    pg, doc, setCode, st, bn, settings, showAnswers, showMarks, autoMarks, pageBg, brand = brand,
                    pageLabel = if (settings.showPageNumber) pageNumberText(pi + 1, pages.size, lang, bn) else null,
                )
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

/**
 * Draws nothing. Plans the question paper exactly like [PaperPages] (same header, fonts, columns, AutoFix) and reports
 * the printed number of every question through [onNumbers] (question id -> number). The answer sheet uses it so its
 * numbers always match the paper, because AutoFix decides the order while it measures the text.
 * Without AutoFix the numbers come straight from [arrangeSections] and no probe is needed.
 */
@Composable
internal fun NumberingProbe(
    questions: List<Question>,
    setCode: String?,
    doc: DocFields,
    lang: Lang,
    settings: DocSettings,
    brand: BrandSettings,
    onNumbers: (Map<String, Int>) -> Unit,
) {
    PaperPages(
        questions = questions,
        setCode = setCode,
        doc = doc,
        lang = lang,
        settings = settings,
        showAnswers = false,
        scale = 1f,
        containerWidth = 0.dp,
        brand = brand,
        onNumbering = onNumbers,
        numberingOnly = true,
    )
}

/** Bangla: "পৃষ্ঠা ১/৩"; English: "Page 01/03" (two digits). Drawn in the paper font, see [PageView]. */
private fun pageNumberText(n: Int, total: Int, lang: Lang, bn: Boolean): String =
    if (bn) "${trKey("doc.page", lang)} ${formatNumber(n, true)}/${formatNumber(total, true)}"
    else "${trKey("doc.page", lang)} ${"%02d".format(n)}/${"%02d".format(total)}"

@Composable
private fun PageView(
    page: PageModel,
    doc: DocFields,
    setCode: String?,
    st: PaperStyle,
    bn: Boolean,
    settings: DocSettings,
    showAnswers: Boolean,
    showMarks: Boolean,
    autoMarks: String?,
    pageBg: Color,
    print: Boolean = false,
    pageLabel: String? = null,
    brand: BrandSettings = BrandSettings(),
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
        Column(Modifier.fillMaxSize().padding(horizontal = mmDp(PAGE_MARGIN_MM), vertical = mmDp(PAGE_MARGIN_MM))) {
            if (page.first) HeaderSection(doc, st, showAnswers, autoMarks, brand, setCode)
            if (page.blocks.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                    Text(tr("doc.empty"), color = GRAY_RULE, fontSize = 13.sp, fontFamily = uiFont(), textAlign = TextAlign.Center)
                }
            } else {
                page.blocks.forEachIndexed { bi, block ->
                    when (block) {
                        is HeadBlock -> SectionHeading(
                            block.item.type, settings, st,
                            top = if (bi == 0 && !page.first) 2.dp else 10.dp,
                        )
                        is RunBlock -> RunView(block, st, showAnswers, showMarks)
                    }
                }
            }
        }
        if (pageLabel != null) {
            Text(
                st.t(pageLabel),
                Modifier.align(Alignment.BottomEnd).padding(bottom = mmDp(PAGE_MARGIN_MM / 2f - 2f), end = mmDp(PAGE_MARGIN_MM)),
                color = GRAY_RULE, fontFamily = st.family, fontSize = st.sp(0.8f),
            )
        }
        if (page.first && setCode != null && !brand.enabled) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = mmDp(PAGE_MARGIN_MM), end = mmDp(PAGE_MARGIN_MM))
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

/** Default header, or the institution header when branding is switched on in the settings. */
@Composable
private fun HeaderSection(doc: DocFields, st: PaperStyle, answers: Boolean, autoMarks: String?, brand: BrandSettings, setCode: String?) {
    if (brand.enabled) BrandHeader(doc, st, answers, autoMarks, brand, setCode)
    else HeaderBlock(doc, st, answers, autoMarks)
}

@Composable
private fun SetCodeBox(st: PaperStyle, setCode: String) {
    Box(
        Modifier
            .border(1.3.dp, st.color, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Text(
            st.t("${trKey("doc.set", if (st.bn) Lang.BN else Lang.EN)}: $setCode"),
            color = st.color, fontFamily = st.family, fontWeight = FontWeight.Bold, fontSize = st.sp(0.95f),
        )
    }
}

/** Rule under the institution block: thin (default), thick or double; "none" draws nothing. */
@Composable
private fun BrandDivider(style: String, color: Color, modifier: Modifier = Modifier) {
    when (style) {
        DIV_NONE -> {}
        DIV_THICK -> Box(modifier.fillMaxWidth().height(2.6.dp).background(color))
        DIV_DOUBLE -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(color))
            Box(Modifier.fillMaxWidth().height(1.dp).background(color))
        }
        else -> Box(modifier.fillMaxWidth().height(1.3.dp).background(color))
    }
}

@Composable
private fun BrandText(
    st: PaperStyle, text: String, em: Float, align: TextAlign, bold: Boolean = false,
    italic: Boolean = false, gray: Boolean = false, modifier: Modifier = Modifier, color: Color? = null,
) {
    Text(
        st.t(text), modifier.fillMaxWidth(),
        color = color ?: if (gray) GRAY_TEXT else st.color, fontFamily = st.family,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = st.sp(em), lineHeight = st.sp(em * 1.5f), textAlign = align,
    )
}

/**
 * Header with the institution branding. Three layouts (classic / letterhead / board) place the logo, the institution
 * name + slogan, the exam name, "subject • chapter" and the set code box; each piece can be hidden in the settings.
 */
@Composable
private fun BrandHeader(doc: DocFields, st: PaperStyle, answers: Boolean, autoMarks: String?, brand: BrandSettings, setCode: String?) {
    val lang = if (st.bn) Lang.BN else Lang.EN
    val marks = doc.marks.ifBlank { autoMarks ?: trKey("doc.marksDefault", lang) }
    val time = doc.time.ifBlank { trKey("doc.timeDefault", lang) }
    val name = if (brand.showName) brand.name.trim() else ""
    val slogan = if (brand.showSlogan) brand.slogan.trim() else ""
    val exam = if (brand.showExam) doc.title.ifBlank { trKey("doc.titleDefault", lang) } else ""
    val subj = listOf(
        if (brand.showSubject) doc.subtitle.trim() else "",
        if (brand.showChapter) doc.chapter.trim() else "",
    ).filter { it.isNotEmpty() }.joinToString(" • ")
        .ifBlank { if (brand.showSubject && brand.showChapter) trKey("doc.subtitleDefault", lang) else "" }
    val logo = if (brand.hasLogo()) PicCache.get(brand.logo) else null
    val code = if (brand.showSetCode) setCode else null
    val logoPos = brand.effLogoPos()
    val codePos = brand.codePos
    val brandColor = if (brand.color.isNotEmpty()) parseHex(brand.color, st.color) else st.color
    val nameK = brand.nameScale / 100f
    val logoK = brand.logoScale / 100f
    val info = brand.infoLines()
    val div = brand.effDivider()

    // the content of one side slot: logo and/or set code box (stacked when both are on the same side)
    @Composable
    fun Slot(pos: String, modifier: Modifier, fitWidth: Boolean = true) {
        val align = when (pos) { POS_START -> Alignment.Start; POS_END -> Alignment.End; else -> Alignment.CenterHorizontally }
        Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (logo != null && logoPos == pos) {
                val ratio = if (brand.logoW > 0 && brand.logoH > 0) brand.logoW.toFloat() / brand.logoH else logo.width.toFloat() / logo.height
                if (fitWidth) {
                    // inside a weighted slot: never wider than the slot
                    val a = when (pos) { POS_START -> Alignment.CenterStart; POS_END -> Alignment.CenterEnd; else -> Alignment.Center }
                    Image(logo, null, Modifier.fillMaxWidth().heightIn(max = st.dp(4.2f * logoK)), contentScale = ContentScale.Fit, alignment = a)
                } else {
                    Image(logo, null, Modifier.height(st.dp(4.2f * logoK)).aspectRatio(ratio), contentScale = ContentScale.Fit)
                }
            }
            if (code != null && codePos == pos && pos != POS_CENTER) SetCodeBox(st, code)
        }
    }

    val hasStart = (logo != null && logoPos == POS_START) || (code != null && codePos == POS_START)
    val hasEnd = (logo != null && logoPos == POS_END) || (code != null && codePos == POS_END)
    val center = TextAlign.Center

    Column(Modifier.fillMaxWidth()) {
        when (brand.layout) {
            BRAND_LETTER -> {
                // logo beside the name; the name block leans towards the side of the logo
                val logoSide = if (logo != null) logoPos else POS_START
                val textAlign = if (logo == null) center else if (logoSide == POS_END) TextAlign.End else TextAlign.Start
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasStart) Slot(POS_START, Modifier, fitWidth = false)
                    Column(Modifier.weight(1f)) {
                        if (name.isNotEmpty()) BrandText(st, name, 1.6f * nameK, textAlign, bold = true, color = brandColor)
                        if (slogan.isNotEmpty()) BrandText(st, slogan, 0.92f, textAlign, italic = true, gray = true)
                        info.forEach { BrandText(st, it, 0.88f, textAlign, gray = true) }
                    }
                    if (hasEnd) Slot(POS_END, Modifier, fitWidth = false)
                }
                if (name.isNotEmpty() || slogan.isNotEmpty() || info.isNotEmpty() || logo != null || code != null) {
                    BrandDivider(div, brandColor, Modifier.padding(top = 8.dp, bottom = 8.dp))
                }
                if (exam.isNotEmpty()) BrandText(st, exam, 1.2f, center, bold = true)
                if (subj.isNotEmpty()) BrandText(st, subj, 1.0f, center, gray = true, modifier = Modifier.padding(bottom = 4.dp))
            }
            BRAND_BOARD -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Slot(POS_START, Modifier.weight(0.24f))
                    Column(Modifier.weight(0.52f)) {
                        if (name.isNotEmpty()) BrandText(st, name, 1.5f * nameK, center, bold = true, color = brandColor)
                        if (slogan.isNotEmpty()) BrandText(st, slogan, 0.9f, center, italic = true, gray = true)
                        info.forEach { BrandText(st, it, 0.86f, center, gray = true) }
                        if (exam.isNotEmpty()) BrandText(st, exam, 1.15f, center, bold = true, modifier = Modifier.padding(top = 4.dp))
                        if (subj.isNotEmpty()) BrandText(st, subj, 1.0f, center, gray = true)
                    }
                    Slot(POS_END, Modifier.weight(0.24f))
                }
                BrandDivider(div, brandColor, Modifier.padding(top = 6.dp, bottom = 4.dp))
            }
            else -> {
                if (hasStart || hasEnd || (logo != null && logoPos == POS_CENTER)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Slot(POS_START, Modifier.weight(1f))
                        Slot(POS_CENTER, Modifier.weight(1f))
                        Slot(POS_END, Modifier.weight(1f))
                    }
                }
                if (name.isNotEmpty()) BrandText(st, name, 1.6f * nameK, center, bold = true, modifier = Modifier.padding(top = 4.dp), color = brandColor)
                if (slogan.isNotEmpty()) BrandText(st, slogan, 0.92f, center, italic = true, gray = true)
                info.forEach { BrandText(st, it, 0.88f, center, gray = true) }
                if (exam.isNotEmpty()) BrandText(st, exam, 1.2f, center, bold = true, modifier = Modifier.padding(top = 4.dp))
                if (subj.isNotEmpty()) BrandText(st, subj, 1.0f, center, gray = true, modifier = Modifier.padding(bottom = 4.dp))
                BrandDivider(div, brandColor, Modifier.padding(top = 4.dp, bottom = 4.dp))
            }
        }
        if (answers) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Text(
                    st.t(trKey("doc.answerSheet", lang)),
                    Modifier
                        .border(1.3.dp, st.color, RoundedCornerShape(4.dp))
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    color = st.color, fontFamily = st.family, fontWeight = FontWeight.Bold,
                    fontSize = st.sp(1.05f), letterSpacing = st.sp(0.02f),
                )
            }
        }
        if (brand.showMarks || brand.showTime) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = if (brand.showMarks && brand.showTime) Arrangement.SpaceBetween else if (brand.showMarks) Arrangement.Start else Arrangement.End,
            ) {
                if (brand.showMarks) Text(
                    st.t("${trKey("doc.marks", lang)}: $marks"), color = st.color, fontFamily = st.family,
                    fontWeight = FontWeight.SemiBold, fontSize = st.sp(0.98f), lineHeight = st.sp(1.47f),
                )
                if (brand.showTime) Text(
                    st.t("${trKey("doc.time", lang)}: $time"), color = st.color, fontFamily = st.family,
                    fontWeight = FontWeight.SemiBold, fontSize = st.sp(0.98f), lineHeight = st.sp(1.47f),
                )
            }
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

/**
 * Settings panel: the real institution header, scaled down to the width of the panel, so every change in the branding
 * section shows at once. [BrandHeader] is reused as is, only the type scale is reduced.
 */
@Composable
fun BrandPreview(brand: BrandSettings, doc: DocFields, settings: DocSettings, lang: Lang, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val size = PAGE_SIZES_MM[settings.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
    val pageBg = parseHex(settings.pageColor, Color.White)
    val shape = RoundedCornerShape(8.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inner = maxWidth - 24.dp // 10 dp padding on both sides + the 1 dp border
        val pageContent = mmDp(size.first - 2f * PAGE_MARGIN_MM)
        val k = (inner.value / pageContent.value).coerceIn(0.2f, 1f)
        val st = PaperStyle(
            fonts = paperFonts(settings.fontEn, settings.fontBn, lang == Lang.BN),
            basePx = settings.fontSize * 96f / 72f * k,
            color = parseHex(settings.fontColor, Color(0xFF111111)),
            bn = lang == Lang.BN,
            density = density,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(pageBg, shape)
                .border(1.dp, Color(0xFFD1D5DB), shape)
                .padding(10.dp)
        ) {
            BrandHeader(doc, st, false, null, brand, "A")
        }
    }
}

@Composable
private fun HeaderBlock(doc: DocFields, st: PaperStyle, answers: Boolean, autoMarks: String?) {
    val lang = if (st.bn) Lang.BN else Lang.EN
    val marks = doc.marks.ifBlank { autoMarks ?: trKey("doc.marksDefault", lang) }
    val time = doc.time.ifBlank { trKey("doc.timeDefault", lang) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            st.t(doc.title.ifBlank { trKey("doc.titleDefault", lang) }),
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            color = st.color, fontFamily = st.family, fontWeight = FontWeight.Bold,
            fontSize = st.sp(1.5f), lineHeight = st.sp(2.25f), textAlign = TextAlign.Center,
        )
        Text(
            st.t(doc.subjectLine().ifBlank { trKey("doc.subtitleDefault", lang) }),
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            color = GRAY_TEXT, fontFamily = st.family, fontWeight = FontWeight.Medium,
            fontSize = st.sp(1.05f), lineHeight = st.sp(1.575f), textAlign = TextAlign.Center,
        )
        if (answers) {
            // website `.doc__anskey`: framed "Answer Sheet" label under the subtitle
            Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    st.t(trKey("doc.answerSheet", lang)),
                    Modifier
                        .border(1.3.dp, st.color, RoundedCornerShape(4.dp))
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    color = st.color, fontFamily = st.family, fontWeight = FontWeight.Bold,
                    fontSize = st.sp(1.05f), letterSpacing = st.sp(0.02f),
                )
            }
        }
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

/** Website `.doc__section`: bold title, italic instruction, left / centre, optional line below. */
@Composable
private fun SectionHeading(type: String, settings: DocSettings, st: PaperStyle, top: Dp) {
    val center = settings.sectionLayout == "center"
    val line = settings.lineOn(if (center) "center" else "left")
    val title = settings.sectionTitle(type, st.bn)
    val instr = settings.sectionInstruction(type)
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = top, bottom = 8.dp)
            .drawBehind {
                if (line) {
                    val y = size.height - 0.65.dp.toPx()
                    drawLine(INK, Offset(0f, y), Offset(size.width, y), 1.3.dp.toPx())
                }
            }
            .padding(bottom = if (line) 4.dp else 0.dp),
    ) {
        Text(
            st.t(title),
            Modifier.fillMaxWidth(),
            color = st.color, fontFamily = st.family, fontWeight = FontWeight.Bold,
            fontSize = st.sp(1.06f), lineHeight = st.sp(1.59f), textAlign = align,
        )
        if (instr.isNotEmpty()) {
            Text(
                st.t(instr),
                Modifier.fillMaxWidth().padding(top = 2.dp),
                color = GRAY_TEXT, fontFamily = st.family, fontStyle = FontStyle.Italic,
                fontSize = st.sp(0.92f), lineHeight = st.sp(1.38f), textAlign = align,
            )
        }
    }
}

/** A run of questions in [RunBlock.k] columns (gap 9 mm for 2, 6 mm for 3) with the optional divider lines. */
@Composable
private fun RunView(b: RunBlock, st: PaperStyle, showAnswers: Boolean, showMarks: Boolean) {
    val gap = when (b.k) {
        2 -> mmDp(9f)
        3 -> mmDp(6f)
        else -> 0.dp
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (b.divider) {
                    Modifier.drawBehind {
                        val g = gap.toPx()
                        val w = (size.width - (b.k - 1) * g) / b.k
                        for (i in 1 until b.k) {
                            val x = i * w + (i - 0.5f) * g
                            drawLine(DIVIDER, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        }
                    }
                } else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        b.columns.forEach { col ->
            Column(Modifier.weight(1f)) {
                col.forEach { ItemView(it, b.k, st, showAnswers, showMarks) }
            }
        }
    }
}

private fun localDigits(s: String, bn: Boolean): String =
    if (bn) s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("") else s

/** The "[5]" marks badge on the right of a question or sub-question (website `.doc-question__marks`). */
@Composable
private fun MarksBadge(marks: String, st: PaperStyle) {
    Text(
        st.t("[" + localDigits(marks.trim(), st.bn) + "]"),
        Modifier.padding(start = 10.dp),
        color = st.color, fontFamily = st.family, fontWeight = FontWeight.Normal,
        fontSize = st.sp(1f), lineHeight = st.sp(1.5f), maxLines = 1,
    )
}

/** Number + question text, with the marks badge on the right. */
@Composable
private fun QHead(q: Question, number: Int, st: PaperStyle, showMarks: Boolean) {
    Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
        RichTextView(
            q.text,
            Modifier.weight(1f),
            prefix = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${formatNumber(number, st.bn)}.  ") }
            },
            color = st.color, fontFamily = st.family, fontWeight = FontWeight.Medium,
            fontSize = st.sp(1f), lineHeight = st.sp(1.5f), fonts = st.fonts, baseBold = st.boldQuestions,
        )
        if (showMarks && q.marks.isNotBlank()) MarksBadge(q.marks, st)
    }
}

/**
 * Splits an answer / explanation at its line breaks into one html piece per line (empty lines dropped).
 * Null when there is nothing to arrange: a single line, no text, or text that contains pictures.
 */
private fun answerLineParts(html: String): List<String>? {
    if (html.isBlank()) return null
    val rich = parseHtml(html)
    if (rich.hasFig()) return null
    val text = rich.text
    if ('\n' !in text) return null
    val out = ArrayList<String>()
    var a = 0
    while (a <= text.length) {
        var b = text.indexOf('\n', a)
        if (b < 0) b = text.length
        val line = text.substring(a, b)
        if (line.isNotBlank()) out.add(toHtml(line, rich.styles.subList(a, b)))
        a = b + 1
    }
    return if (out.size >= 2) out else null
}

/**
 * Smart line arrangement: every piece keeps its own unbroken line, but a short piece sits NEXT to the previous one
 * when it fits on the same row (with [gap] between them); a piece that does not fit, or is longer than a row,
 * starts a new row. Saves page space without gluing long steps together.
 */
@Composable
private fun SmartLineFlow(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val bounded = constraints.hasBoundedWidth
        val limit = if (bounded) constraints.maxWidth else Int.MAX_VALUE
        val placeables = measurables.map { it.measure(Constraints(maxWidth = if (bounded) limit else Constraints.Infinity)) }

        val rows = ArrayList<List<Placeable>>()
        var cur = ArrayList<Placeable>()
        var used = 0
        for (p in placeables) {
            if (cur.isNotEmpty() && used + gapPx + p.width > limit) {
                rows.add(cur)
                cur = ArrayList()
                used = 0
            }
            used = if (cur.isEmpty()) p.width else used + gapPx + p.width
            cur.add(p)
        }
        if (cur.isNotEmpty()) rows.add(cur)

        val rowH = rows.map { r -> r.maxOf { it.height } }
        val width = if (bounded) constraints.maxWidth else rows.maxOfOrNull { r -> r.sumOf { it.width } + gapPx * (r.size - 1) } ?: 0
        layout(width, rowH.sum()) {
            var y = 0
            rows.forEachIndexed { ri, r ->
                var x = 0
                r.forEach { p ->
                    p.placeRelative(x, y + (rowH[ri] - p.height) / 2)
                    x += p.width + gapPx
                }
                y += rowH[ri]
            }
        }
    }
}

/** Green bold label + text in the small grey "answer / explanation" style (website `.doc-explanation`). */
@Composable
private fun AnswerLine(label: String, html: String, st: PaperStyle, modifier: Modifier = Modifier) {
    val prefix = buildAnnotatedString {
        withStyle(SpanStyle(color = st.ansColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal)) {
            append("$label ")
        }
    }
    val body = SpanStyle(color = GRAY_TEXT, fontStyle = FontStyle.Normal)
    val parts = remember(html) { answerLineParts(html) }
    if (parts == null) {
        RichTextView(
            html, modifier, prefix = prefix, bodySpan = body,
            fontFamily = st.family, fontSize = st.sp(0.88f), lineHeight = st.sp(1.232f), fonts = st.fonts,
        )
        return
    }
    SmartLineFlow(st.dp(1.3f), modifier) {
        parts.forEachIndexed { i, part ->
            RichTextView(
                part, Modifier, prefix = if (i == 0) prefix else null, bodySpan = body,
                fontFamily = st.family, fontSize = st.sp(0.88f), lineHeight = st.sp(1.232f), fonts = st.fonts,
            )
        }
    }
}

/** One laid-out piece: a question of any type, a Creative sub-question piece, or a Creative answer piece. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemView(item: Item, k: Int, st: PaperStyle, showAnswers: Boolean, showMarks: Boolean) {
    val lang = if (st.bn) Lang.BN else Lang.EN
    val q = item.q
    // tighter gap between MCQs so more of them fit on a page; other types keep the roomier 12dp
    val bottom = if (item.more) 3.dp else if (q.kind() == QType.MCQ) 6.dp else 12.dp
    val answerWord = trKey("t.answerLbl", lang)
    val explWord = trKey("label.explanation", lang)

    // Creative: answer of one sub-question
    if (item.ansSub != null) {
        val s = q.subs[item.ansSub]
        Column(Modifier.fillMaxWidth().padding(top = if (item.ansFirst) 4.dp else 0.dp, bottom = bottom)) {
            AnswerLine("$answerWord (${subLabel(item.ansSub, st.bn)}):", s.answer, st, Modifier.padding(vertical = 3.dp))
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(bottom = bottom)) {
        when (q.kind()) {
            QType.CREATIVE -> {
                if (!item.noHead) QHead(q, item.number, st, showMarks)
                val shown = (item.subs ?: q.subs.indices.toList()).filter { plainText(q.subs[it].text).isNotBlank() }
                Column(
                    Modifier.padding(top = if (item.noHead) 0.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    shown.forEach { si ->
                        val s = q.subs[si]
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                st.t(subLabel(si, st.bn) + "."),
                                Modifier.widthIn(min = st.dp(1.5f)).padding(end = 6.dp),
                                color = st.color, fontFamily = st.family, fontWeight = FontWeight.Normal,
                                fontSize = st.sp(1f), lineHeight = st.sp(1.5f),
                            )
                            RichTextView(
                                s.text,
                                Modifier.weight(1f),
                                color = st.color, fontFamily = st.family, fontWeight = FontWeight.Normal,
                                fontSize = st.sp(1f), lineHeight = st.sp(1.5f), fonts = st.fonts,
                            )
                            if (showMarks && s.marks.isNotBlank()) MarksBadge(s.marks, st)
                        }
                    }
                }
            }
            QType.TF -> {
                QHead(q, item.number, st, showMarks)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(OPT_ROW_GAP),
                ) {
                    OptionItem(0, trKey("t.tfTrue", lang), false, st, false, label = "")
                    OptionItem(1, trKey("t.tfFalse", lang), false, st, false, label = "")
                }
                if (showAnswers) {
                    val key = if (!st.showAns) "" else when (q.tfAnswer) {
                        true -> trKey("t.tfTrue", lang)
                        false -> trKey("t.tfFalse", lang)
                        null -> ""
                    }
                    val hasEx = st.showExpl && plainText(q.explanation).isNotBlank()
                    if (key.isNotEmpty() || hasEx) {
                        val label = (if (key.isNotEmpty()) "($key)" else "") +
                            (if (key.isNotEmpty() && hasEx) " " else "") +
                            (if (hasEx) "$explWord:" else "")
                        AnswerLine(label, if (hasEx) q.explanation else "", st, Modifier.padding(top = 4.dp))
                    }
                }
            }
            QType.FILL, QType.DESC -> {
                QHead(q, item.number, st, showMarks)
                if (showAnswers && st.showAns && plainText(q.answer).isNotBlank()) {
                    AnswerLine("$answerWord:", q.answer, st, Modifier.padding(top = 4.dp))
                }
            }
            QType.SHORT -> {
                QHead(q, item.number, st, showMarks)
                // the answer, or the marking guide when there is no answer
                val src = if (st.showAns && plainText(q.answer).isNotBlank()) q.answer else if (st.showExpl) q.explanation else ""
                if (showAnswers && plainText(src).isNotBlank()) {
                    AnswerLine("$answerWord:", src, st, Modifier.padding(top = 4.dp))
                }
            }
            else -> {
                QHead(q, item.number, st, showMarks)
                // answer key: the three layouts of "Answer sheet formatting" (fill | expl | corner)
                val correct = q.options.indices.filter { q.options[it].correct }
                val keys = correct.map { optionLabel(it, st.bn) }
                val showKey = showAnswers && st.showAns && keys.isNotEmpty()
                val hasEx = showAnswers && st.showExpl && plainText(q.explanation).isNotBlank()
                val fill = showKey && st.ansLayout == ANS_LAYOUT_FILL
                val corner = showKey && st.ansLayout == ANS_LAYOUT_CORNER
                // corner layout: the round badge floats over the bottom-right corner of the options block, so the
                // options (vertical / horizontal / rectangular) keep exactly their own layout. It shares the right edge
                // with the [marks] badge above, and the column gap keeps it a little away from the column divider.
                Box(Modifier.fillMaxWidth()) {
                    OptionsBlock(q, st, if (fill) correct.toSet() else emptySet(), k)
                    if (corner) {
                        Row(
                            Modifier.align(Alignment.BottomEnd),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            correct.forEach { i ->
                                val label = optionLabel(i, st.bn)
                                OptionBadge(
                                    label = label, textColor = Color.White, family = st.fonts.familyFor(label),
                                    textSize = st.sp(0.84f), diameter = st.dp(1.35f), fill = st.ansColor, stroke = st.ansColor,
                                )
                            }
                        }
                    }
                }
                if (st.ansLayout == ANS_LAYOUT_EXPL) {
                    val key = if (showKey) "(" + keys.joinToString(", ") + ")" else ""
                    if (key.isNotEmpty() || hasEx) {
                        val label = key + (if (key.isNotEmpty() && hasEx) " " else "") + (if (hasEx) "$explWord:" else "")
                        AnswerLine(label, if (hasEx) q.explanation else "", st, Modifier.padding(top = 4.dp))
                    }
                } else if (hasEx) {
                    AnswerLine("$explWord:", q.explanation, st, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Gap between two option rows (kept small so MCQs stay compact on the page). */
private val OPT_ROW_GAP = 2.dp
private val OPT_H_GAP = 16.dp   // between options of one horizontal row
private val OPT_G_GAP = 14.dp   // between the two columns of the rectangular layout

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsBlock(q: Question, st: PaperStyle, hi: Set<Int>, k: Int) {
    // "auto" (default): all four in one row if they fit, else 2 x 2, else one under another (measured, not guessed)
    if (q.layout == "auto") {
        AutoOptions(q, st, hi)
        return
    }
    // in 3 columns the rectangular layout becomes a single column (website `data-cols="3" .doc-options--grid`)
    val layout = if (k == 3 && q.layout == "grid") "vertical" else q.layout
    when (layout) {
        "horizontal" -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(OPT_H_GAP),
            verticalArrangement = Arrangement.spacedBy(OPT_ROW_GAP),
        ) {
            q.options.forEachIndexed { i, o -> OptionItem(i, o.text, i in hi, st, false) }
        }
        "grid" -> Column(verticalArrangement = Arrangement.spacedBy(OPT_ROW_GAP)) {
            q.options.chunked(2).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OPT_G_GAP)) {
                    row.forEachIndexed { j, o ->
                        Box(Modifier.weight(1f)) { OptionItem(r * 2 + j, o.text, (r * 2 + j) in hi, st, true) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(OPT_ROW_GAP)) {
            q.options.forEachIndexed { i, o -> OptionItem(i, o.text, i in hi, st, true) }
        }
    }
}

/** 1 = horizontal, 2 = rectangular, 3 = vertical (0 = no options). Estimated from the text widths. */
private fun autoOptionMode(q: Question, st: PaperStyle, measurer: TextMeasurer, maxW: Int): Int {
    val opts = q.options
    if (opts.isEmpty()) return 0
    if (opts.any { "<fig" in it.text }) return 3 // pictures in options need room
    val d = with(st.density) { st.dp(1.152f).roundToPx() }
    val pad = with(st.density) { (8.dp).roundToPx() }
    val safety = with(st.density) { (4.dp).roundToPx() } // text-estimate vs real layout differences
    val style = TextStyle(fontFamily = st.family, fontSize = st.sp(0.96f))
    val widths = opts.map { o ->
        val t = plainText(o.text).trim()
        val tw = if (t.isEmpty()) 0 else measurer.measure(
            st.fonts.apply(AnnotatedString(t)), style, softWrap = false, maxLines = 1,
        ).size.width
        d + pad + tw + safety
    }
    val hGap = with(st.density) { OPT_H_GAP.roundToPx() }
    val gGap = with(st.density) { OPT_G_GAP.roundToPx() }
    val total = widths.sum() + hGap * (widths.size - 1)
    val colW = (maxW - gGap) / 2
    return when {
        total <= maxW -> 1
        opts.size > 1 && widths.all { it <= colW } -> 2
        else -> 3
    }
}

private fun modeName(m: Int) = when (m) { 1 -> "horizontal"; 2 -> "grid"; else -> "vertical" }

/**
 * The layout the "auto" arrangement picks for [q] on the paper (page size, margins, column count and fonts of
 * [settings]); the question card uses it to highlight the layout that is actually in use. Same rule as [AutoOptions].
 */
fun resolveOptionLayout(q: Question, settings: DocSettings, bn: Boolean, density: Density, measurer: TextMeasurer): String {
    if (q.layout != "auto") return q.layout
    val st = PaperStyle(
        fonts = paperFonts(settings.fontEn, settings.fontBn, bn),
        basePx = settings.fontSize * 96f / 72f,
        color = Color.Black, bn = bn, density = density,
    )
    val size = PAGE_SIZES_MM[settings.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
    val contentW = mmDp(size.first).let { with(density) { it.roundToPx() } } - 2 * with(density) { mmDp(PAGE_MARGIN_MM).roundToPx() }
    val k = settings.colsOf(q)
    val gap = when (k) { 2 -> with(density) { mmDp(9f).roundToPx() }; 3 -> with(density) { mmDp(6f).roundToPx() }; else -> 0 }
    val colW = (contentW - (k - 1) * gap) / k
    return modeName(autoOptionMode(q, st, measurer, colW))
}

/**
 * Smart option arrangement. The options are first measured at their natural (unwrapped) width, then:
 *  1. every option fits in ONE row  -> horizontal,
 *  2. else every option fits in half the width -> rectangular (2 per row, aligned columns),
 *  3. else -> vertical (long text wraps inside its own line).
 * Real measured widths are used, so it adapts to the font, font size, column count (1 / 2 / 3) and the content
 * (fractions, formulas, Bangla / English) instead of a character-count guess.
 */
@Composable
private fun AutoOptions(q: Question, st: PaperStyle, hi: Set<Int>) {
    val opts = q.options
    val measurer = rememberTextMeasurer()
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val hGap = OPT_H_GAP.roundToPx()
        val gGap = OPT_G_GAP.roundToPx()
        val rowGap = OPT_ROW_GAP.roundToPx()

        // 1) probe: natural widths
        val probe = subcompose("probe") {
            opts.forEachIndexed { i, o -> OptionItem(i, o.text, i in hi, st, false) }
        }.map { it.measure(Constraints(maxWidth = Constraints.Infinity)) }
        val widths = probe.map { it.width }
        val total = widths.sum() + hGap * (widths.size - 1).coerceAtLeast(0)
        val colW = (maxW - gGap) / 2

        // same decision as the question card's highlighted layout; the real widths can only make it roomier
        val planned = if (maxW == Int.MAX_VALUE) 1 else autoOptionMode(q, st, measurer, maxW)
        val mode = when {
            opts.isEmpty() -> 0
            planned == 1 && (maxW == Int.MAX_VALUE || total <= maxW) -> 1
            planned <= 2 && opts.size > 1 && widths.all { it <= colW } -> 2
            else -> 3
        }

        when (mode) {
            0 -> layout(constraints.minWidth, 0) {}
            1 -> {
                // reuse the probe placeables: they are already measured at their natural size
                val h = probe.maxOf { it.height }
                val w = if (maxW == Int.MAX_VALUE) total else maxW
                layout(w, h) {
                    var x = 0
                    probe.forEach { p ->
                        p.placeRelative(x, (h - p.height) / 2)
                        x += p.width + hGap
                    }
                }
            }
            2 -> {
                val cells = subcompose("grid") {
                    opts.forEachIndexed { i, o -> OptionItem(i, o.text, i in hi, st, true) }
                }.map { it.measure(Constraints(minWidth = colW, maxWidth = colW)) }
                val rows = cells.chunked(2)
                val rowH = rows.map { r -> r.maxOf { it.height } }
                layout(maxW, rowH.sum() + rowGap * (rows.size - 1)) {
                    var y = 0
                    rows.forEachIndexed { ri, r ->
                        r.forEachIndexed { ci, p -> p.placeRelative(ci * (colW + gGap), y) }
                        y += rowH[ri] + rowGap
                    }
                }
            }
            else -> {
                val cells = subcompose("vertical") {
                    opts.forEachIndexed { i, o -> OptionItem(i, o.text, i in hi, st, true) }
                }.map { it.measure(Constraints(minWidth = maxW, maxWidth = maxW)) }
                layout(maxW, cells.sumOf { it.height } + rowGap * (cells.size - 1)) {
                    var y = 0
                    cells.forEach { p ->
                        p.placeRelative(0, y)
                        y += p.height + rowGap
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    index: Int,
    text: String,
    correct: Boolean,
    st: PaperStyle,
    fillText: Boolean,
    label: String = optionLabel(index, st.bn),
) {
    val badge: @Composable (Modifier) -> Unit = { m ->
        OptionBadge(
            label = label,
            textColor = if (correct) Color.White else st.color,
            family = st.fonts.familyFor(label),
            textSize = st.sp(0.768f),
            diameter = st.dp(1.152f),
            fill = if (correct) st.ansColor else Color.Transparent,
            stroke = if (correct) st.ansColor else st.color,
            modifier = m,
        )
    }
    val weight = if (correct) FontWeight.Bold else FontWeight.Normal
    if (fillText && "<fig" !in text) {
        // full-width option: the badge sits at the start of the FIRST line and a long text continues on the next
        // line from the left edge (under the badge) instead of staying in a narrow column beside it.
        val density = LocalDensity.current
        val d = st.dp(1.152f)
        val indent = with(density) { (d + 8.dp).toSp() }
        val top = ((st.dp(1.344f) - d) / 2).coerceAtLeast(0.dp)
        Box(Modifier.fillMaxWidth()) {
            badge(Modifier.padding(top = top))
            RichTextView(
                text, Modifier.fillMaxWidth(),
                color = st.color, fontFamily = st.family, fontWeight = weight,
                fontSize = st.sp(0.96f), lineHeight = st.sp(1.344f), fonts = st.fonts,
                firstLineIndent = indent,
            )
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        badge(Modifier)
        RichTextView(
            text,
            if (fillText) Modifier.weight(1f) else Modifier,
            color = st.color, fontFamily = st.family, fontWeight = weight,
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
    modifier: Modifier = Modifier,
) {
    val resolver = LocalFontFamilyResolver.current
    val typeface = remember(family) {
        resolver.resolve(family, FontWeight.Bold, FontStyle.Normal, FontSynthesis.All).value as? android.graphics.Typeface
    }
    Canvas(modifier.size(diameter)) {
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
