package com.intelliq.rifatplex.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.intelliq.rifatplex.R
import com.intelliq.rifatplex.data.ANS_COLOR_BLACK
import com.intelliq.rifatplex.data.BRAND_BOARD
import com.intelliq.rifatplex.data.DIV_DOUBLE
import com.intelliq.rifatplex.data.DIV_NONE
import com.intelliq.rifatplex.data.DIV_THICK
import com.intelliq.rifatplex.data.BRAND_LETTER
import com.intelliq.rifatplex.data.POS_CENTER
import com.intelliq.rifatplex.data.POS_END
import com.intelliq.rifatplex.data.POS_START
import com.intelliq.rifatplex.data.ANS_LAYOUT_CORNER
import com.intelliq.rifatplex.data.ANS_LAYOUT_EXPL
import com.intelliq.rifatplex.data.ANS_LAYOUT_FILL
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.CharStyle
import com.intelliq.rifatplex.data.FIG_CHAR
import com.intelliq.rifatplex.data.FRAC_CHAR
import com.intelliq.rifatplex.data.FigData
import com.intelliq.rifatplex.data.PAGE_SIZES_MM
import com.intelliq.rifatplex.data.Placed
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.Rich
import com.intelliq.rifatplex.data.arrangeSections
import com.intelliq.rifatplex.data.colsOf
import com.intelliq.rifatplex.data.dividerOn
import com.intelliq.rifatplex.data.figWidthPct
import com.intelliq.rifatplex.data.formatMarks
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.lineOn
import com.intelliq.rifatplex.data.optionLabel
import com.intelliq.rifatplex.data.parseHtml
import com.intelliq.rifatplex.data.plainText
import com.intelliq.rifatplex.data.sectionInstruction
import com.intelliq.rifatplex.data.sectionTitle
import com.intelliq.rifatplex.data.subLabel
import com.intelliq.rifatplex.data.totalMarks
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Word (.docx) export of a question paper / answer key.
 *
 * The file is written by hand (WordprocessingML inside a ZIP), so there is no extra library. Everything stays real,
 * editable Word content: text runs with the paper's fonts, real columns (continuous sections, with the divider line),
 * stacked fractions as Word equations, pictures as inline images, marks badges and Creative sub-questions as
 * borderless tables, page numbers in the footer. Opens in Microsoft Word, Google Docs and LibreOffice.
 */
object DocxExport {
    const val MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    fun build(activity: Activity, d: AppData, questions: List<Question>, setCode: String?, answers: Boolean): ByteArray =
        DocxBuilder(activity, d, questions, setCode, answers).render()
}

private const val GRAY_TEXT = "374151"
private const val GRAY_RULE = "9CA3AF"
private const val GREEN = "16A34A"
private const val INK = "111111"

private class Base(val pt: Double, val bold: Boolean = false, val italic: Boolean = false, val color: String)

private class RunGroup(val k: Int, val div: Boolean) {
    val items = ArrayList<Placed>()
}

private class Segment(val elems: List<String>, val k: Int, val sep: Boolean)

private class EmbedFont(val name: String, val resIds: List<Int>) // regular, (bold)

private class DocxBuilder(
    private val activity: Activity,
    private val d: AppData,
    private val questions: List<Question>,
    private val setCode: String?,
    private val answers: Boolean,
) {
    private val bn = d.language == "bn"
    private val lang = if (bn) Lang.BN else Lang.EN
    private val st = d.settings
    private val doc = d.doc
    private val sizePt = st.fontSize.toDouble()
    private val ink = hex6(st.fontColor, INK)
    private val showMarks = st.showMarks
    private val ansHex = if (st.answerColor == ANS_COLOR_BLACK) INK else GREEN // colour of the correct answer
    private val ansOn = answers && st.showAnswer // the correct answer is kept in the answer key
    private val exOn = answers && st.showExplanation // the explanation is kept in the answer key

    // ----- fonts: the same families as the PDF (Times New Roman / Noto Serif Bengali / Cambria for formulas) -----
    private val latinName: String
    private val bnName: String

    init {
        when (st.fontFamily) {
            "Noto Serif Bengali" -> { latinName = "Noto Serif Bengali"; bnName = "Noto Serif Bengali" }
            "SolaimanLipi" -> { latinName = "SolaimanLipi"; bnName = "SolaimanLipi" }
            "Kalpurush" -> { latinName = "Kalpurush"; bnName = "Kalpurush" }
            "Hind Siliguri" -> { latinName = "Hind Siliguri"; bnName = "Hind Siliguri" }
            "Calibri" -> { latinName = "Calibri"; bnName = "Noto Serif Bengali" }
            "Cambria Math" -> { latinName = "Cambria"; bnName = "Noto Serif Bengali" }
            else -> { latinName = "Times New Roman"; bnName = "Noto Serif Bengali" }
        }
    }

    // ----- page geometry (twips) -----
    private val pageMm = PAGE_SIZES_MM[st.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
    private val pageW = (pageMm.first / 25.4 * 1440.0).roundToInt()
    private val pageH = (pageMm.second / 25.4 * 1440.0).roundToInt()
    private val margin = 1080 // 0.75 inch, same as the PDF
    private val contentW = pageW - 2 * margin

    private fun gapOf(k: Int): Int = when (k) { 2 -> 510; 3 -> 340; else -> 0 }
    private fun colWOf(k: Int): Int = (contentW - (k - 1) * gapOf(k)) / k

    private fun tw(pt: Double): Int = (pt * 20.0).roundToInt()

    // ----- package parts collected while building -----
    private class Rel(val id: String, val type: String, val target: String)

    private val rels = ArrayList<Rel>()
    private val mediaParts = ArrayList<Pair<String, ByteArray>>() // "media/x.png" -> bytes
    private val urlRel = HashMap<String, Triple<String, Int, Int>>() // data url -> (rId, w, h)
    private var nextRel = 10
    private var nextDocPr = 1
    private var mediaCount = 0
    private val usedExt = HashSet<String>()

    private val resolver by lazy { createFontFamilyResolver(activity) }
    private val paperFont by lazy { paperFonts(st.fontFamily) }

    private val autoMarks: String? = questions.totalMarks().takeIf { it > 0.0 }?.let { formatMarks(it, bn) }

    // ======================================================================================================
    // small XML helpers
    // ======================================================================================================

    private fun hex6(s: String, fallback: String): String {
        var t = s.trim().removePrefix("#")
        if (t.length == 3) t = "" + t[0] + t[0] + t[1] + t[1] + t[2] + t[2]
        val ok = t.length == 6 && t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        return if (ok) t.uppercase() else fallback
    }

    private fun hexInt(c: Int): String = String.format("%06X", c and 0xFFFFFF)

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> {}
                ch == '\uFFFE' || ch == '\uFFFF' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun localDigits(s: String): String =
        if (bn) s.map { if (it in '0'..'9') '\u09E6' + (it - '0') else it }.joinToString("") else s

    // ----- character kinds, same idea as PaperFonts: 0 neutral, 1 Bangla, 2 Latin, 3 formula -----
    private fun kindOf(c: Char): Int {
        val x = c.code
        return when {
            x in 0x0980..0x09FF || x == 0x0964 || x == 0x0965 -> 1
            x < 0x80 -> if (c.isLetterOrDigit()) 2 else 0
            c == '\u200C' || c == '\u200D' || c == '\u00A0' -> 0
            isMathChar(x) -> 3
            c.isLetterOrDigit() -> 2
            else -> 0
        }
    }

    private fun rPr(kind: Int, bold: Boolean, italic: Boolean, underline: Boolean, vert: Int, color: String, pt: Double, pos: Int = 0, shade: String = ""): String {
        val sz = (pt * 2.0).roundToInt().coerceAtLeast(2)
        val f = when (kind) {
            1 -> "<w:rFonts w:ascii='$bnName' w:hAnsi='$bnName' w:eastAsia='$bnName' w:cs='$bnName'/>"
            3 -> "<w:rFonts w:ascii='Cambria' w:hAnsi='Cambria' w:cs='Cambria'/>"
            else -> "<w:rFonts w:ascii='$latinName' w:hAnsi='$latinName' w:cs='$latinName'/>"
        }
        val sb = StringBuilder(f)
        if (bold) sb.append("<w:b/><w:bCs/>")
        if (italic) sb.append("<w:i/><w:iCs/>")
        sb.append("<w:color w:val='$color'/>")
        if (pos != 0) sb.append("<w:position w:val='$pos'/>")
        sb.append("<w:sz w:val='$sz'/><w:szCs w:val='$sz'/>")
        if (underline) sb.append("<w:u w:val='single'/>")
        if (shade.isNotEmpty()) sb.append("<w:shd w:val='clear' w:color='auto' w:fill='$shade'/>")
        if (vert > 0) sb.append("<w:vertAlign w:val='superscript'/>")
        if (vert < 0) sb.append("<w:vertAlign w:val='subscript'/>")
        if (kind == 1) sb.append("<w:cs/><w:lang w:val='en-US' w:bidi='bn-BD'/>")
        return "<w:rPr>$sb</w:rPr>"
    }

    private fun run(text: String, rpr: String): String {
        val sb = StringBuilder()
        val parts = text.split('\n')
        parts.forEachIndexed { i, part ->
            if (i > 0) sb.append("<w:r>$rpr<w:br/></w:r>")
            if (part.isNotEmpty()) sb.append("<w:r>$rpr<w:t xml:space='preserve'>${esc(part)}</w:t></w:r>")
        }
        return sb.toString()
    }

    private fun sameFmt(a: CharStyle, b: CharStyle): Boolean =
        a.bold == b.bold && a.italic == b.italic && a.underline == b.underline && a.sub == b.sub && a.sup == b.sup && a.color == b.color

    /** Text with Bangla / English / formula fonts applied per character. */
    private fun styledText(t: String, s: CharStyle, base: Base): String {
        if (t.isEmpty()) return ""
        val kind = IntArray(t.length) { kindOf(t[it]) }
        if (s.sub || s.sup) for (i in kind.indices) if (kind[i] != 1) kind[i] = 3
        var last = 0
        for (i in kind.indices) { if (kind[i] == 0) kind[i] = last else last = kind[i] }
        var next = 0
        for (i in kind.indices.reversed()) { if (kind[i] == 0) kind[i] = next else next = kind[i] }
        val color = if (s.color != 0) hexInt(s.color) else base.color
        val vert = if (s.sup) 1 else if (s.sub) -1 else 0
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            var j = i + 1
            while (j < t.length && kind[j] == kind[i]) j++
            val k = if (kind[i] == 0) 2 else kind[i]
            sb.append(run(t.substring(i, j), rPr(k, base.bold || s.bold, base.italic || s.italic, s.underline, vert, color, base.pt)))
            i = j
        }
        return sb.toString()
    }

    private fun plainRuns(t: String, base: Base, bold: Boolean = false): String =
        styledText(t, CharStyle(bold = bold), base)

    // ----- paragraphs and tables -----
    private fun para(
        runs: String,
        jc: String = "left",
        before: Int = 0,
        after: Int = 0,
        line: Int = 0,
        keepNext: Boolean = false,
        border: String = "",
        tabs: String = "",
        ind: String = "",
        markPt: Double = sizePt,
    ): String {
        val sb = StringBuilder("<w:p><w:pPr>")
        if (keepNext) sb.append("<w:keepNext/>")
        sb.append("<w:keepLines/>")
        if (border.isNotEmpty()) sb.append("<w:pBdr>").append(border).append("</w:pBdr>")
        if (tabs.isNotEmpty()) sb.append("<w:tabs>").append(tabs).append("</w:tabs>")
        if (line > 0) sb.append("<w:spacing w:before='$before' w:after='$after' w:line='$line' w:lineRule='atLeast'/>")
        else sb.append("<w:spacing w:before='$before' w:after='$after' w:line='240' w:lineRule='auto'/>")
        if (ind.isNotEmpty()) sb.append("<w:ind ").append(ind).append("/>")
        sb.append("<w:jc w:val='$jc'/>")
        val sz = (markPt * 2.0).roundToInt().coerceAtLeast(2)
        sb.append("<w:rPr><w:sz w:val='$sz'/><w:szCs w:val='$sz'/></w:rPr></w:pPr>")
        sb.append(runs).append("</w:p>")
        return sb.toString()
    }

    private fun spacer(twips: Int): String =
        "<w:p><w:pPr><w:keepLines/><w:spacing w:before='0' w:after='0' w:line='${max(twips, 20)}' w:lineRule='exact'/>" +
            "<w:rPr><w:sz w:val='2'/><w:szCs w:val='2'/></w:rPr></w:pPr></w:p>"

    private fun cell(w: Int, content: List<String>, span: Int = 1, props: String = ""): String {
        val body = if (content.isEmpty()) spacer(20) else content.joinToString("")
        val spanXml = if (span > 1) "<w:gridSpan w:val='$span'/>" else ""
        return "<w:tc><w:tcPr><w:tcW w:w='$w' w:type='dxa'/>$spanXml$props</w:tcPr>$body</w:tc>"
    }

    private fun row(cells: List<String>): String = "<w:tr><w:trPr><w:cantSplit/></w:trPr>${cells.joinToString("")}</w:tr>"

    private fun table(grid: List<Int>, rows: List<String>, centered: Boolean = false): String {
        val total = grid.sum()
        val jc = if (centered) "<w:jc w:val='center'/>" else ""
        return "<w:tbl><w:tblPr><w:tblW w:w='$total' w:type='dxa'/>$jc<w:tblLayout w:type='fixed'/>" +
            "<w:tblCellMar><w:top w:w='0' w:type='dxa'/><w:left w:w='0' w:type='dxa'/><w:bottom w:w='0' w:type='dxa'/><w:right w:w='0' w:type='dxa'/></w:tblCellMar>" +
            "<w:tblLook w:val='0000'/></w:tblPr><w:tblGrid>${grid.joinToString("") { "<w:gridCol w:w='$it'/>" }}</w:tblGrid>${rows.joinToString("")}</w:tbl>"
    }

    private fun isPara(x: String) = x.startsWith("<w:p>")

    private fun setAfter(p: String, v: Int): String {
        if (!isPara(p)) return p
        return Regex("w:after='\\d+'").replaceFirst(p, "w:after='$v'")
    }

    private fun setBefore(p: String, v: Int): String {
        if (!isPara(p)) return p
        return Regex("w:before='\\d+'").replaceFirst(p, "w:before='$v'")
    }

    private fun addKeep(x: String): String = x.replace("<w:pPr>", "<w:pPr><w:keepNext/>")

    /** Every paragraph keeps with the next one, except the very last one: a question is never split across columns / pages. */
    private fun keepTogether(items: List<String>): List<String> {
        if (items.isEmpty()) return items
        val out = ArrayList<String>()
        for (i in 0 until items.size - 1) out.add(addKeep(items[i]))
        val last = items.last()
        if (last.startsWith("<w:tbl>")) {
            val cut = last.lastIndexOf("<w:tr>")
            out.add(if (cut > 0) addKeep(last.substring(0, cut)) + last.substring(cut) else last)
        } else out.add(last)
        return out
    }

    private fun withBottom(items: List<String>, twips: Int): List<String> {
        if (items.isEmpty()) return items
        val out = ArrayList<String>(items)
        val last = out.last()
        if (isPara(last)) out[out.lastIndex] = setAfter(last, twips) else out.add(spacer(twips))
        return out
    }

    // ----- measuring (for the small framed boxes) -----
    private fun typefaceFor(label: String, bold: Boolean): Typeface? = try {
        resolver.resolve(
            paperFont.familyFor(label),
            if (bold) FontWeight.Bold else FontWeight.Normal,
            FontStyle.Normal,
            FontSynthesis.All,
        ).value as? Typeface
    } catch (e: Exception) {
        null
    }

    private fun measureTw(text: String, pt: Double, bold: Boolean): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = pt.toFloat()
        typefaceFor(text, bold)?.let { paint.typeface = it }
        return (paint.measureText(text) * 20f * 1.08f).roundToInt()
    }

    // ======================================================================================================
    // pictures
    // ======================================================================================================

    private fun addRel(type: String, target: String): String {
        val id = "rId${nextRel++}"
        rels.add(Rel(id, type, target))
        return id
    }

    private fun addMedia(bytes: ByteArray, ext: String): String {
        mediaCount++
        usedExt.add(ext)
        val name = "media/image$mediaCount.$ext"
        mediaParts.add(name to bytes)
        return addRel("http://schemas.openxmlformats.org/officeDocument/2006/relationships/image", name)
    }

    /** data URL -> relationship id + pixel size (null when it cannot be read). */
    private fun imageFromUrl(url: String, w: Int, h: Int): Triple<String, Int, Int>? {
        urlRel[url]?.let { return it }
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val header = url.substring(0, comma).lowercase()
        val raw = try { Base64.decode(url.substring(comma + 1), Base64.DEFAULT) } catch (e: Exception) { return null }
        var bytes = raw
        var ext = when {
            "jpeg" in header || "jpg" in header -> "jpeg"
            "png" in header -> "png"
            "gif" in header -> "gif"
            else -> ""
        }
        var pw = w
        var ph = h
        if (ext.isEmpty() || pw <= 0 || ph <= 0) {
            val bmp = try { BitmapFactory.decodeByteArray(raw, 0, raw.size) } catch (e: Exception) { null } ?: return null
            pw = bmp.width
            ph = bmp.height
            if (ext.isEmpty()) {
                val bo = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bo)
                bytes = bo.toByteArray()
                ext = "png"
            }
            bmp.recycle()
        }
        val res = Triple(addMedia(bytes, ext), pw, ph)
        urlRel[url] = res
        return res
    }

    private fun drawing(rId: String, cx: Long, cy: Long, name: String, pos: Int = 0): String {
        val id = nextDocPr++
        val rp = if (pos != 0) "<w:rPr><w:position w:val='$pos'/></w:rPr>" else ""
        return "<w:r>$rp<w:drawing><wp:inline distT='0' distB='0' distL='0' distR='0'><wp:extent cx='$cx' cy='$cy'/>" +
            "<wp:effectExtent l='0' t='0' r='0' b='0'/><wp:docPr id='$id' name='Picture $id' descr='${esc(name)}'/>" +
            "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect='1'/></wp:cNvGraphicFramePr>" +
            "<a:graphic><a:graphicData uri='http://schemas.openxmlformats.org/drawingml/2006/picture'><pic:pic>" +
            "<pic:nvPicPr><pic:cNvPr id='$id' name='$name'/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed='$rId'/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x='0' y='0'/><a:ext cx='$cx' cy='$cy'/></a:xfrm><a:prstGeom prst='rect'><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"
    }

    /** The pictures of one figure side by side (sizes follow the paper: S / M / L as a share of the width). */
    private fun figRuns(fig: FigData, availTw: Int): String {
        val n = fig.pics.size
        if (n == 0) return ""
        val pct = figWidthPct(fig.size, n) / 100.0
        val wTw = max((availTw * pct).toInt(), 200)
        val gapTw = if (n > 1) (availTw * 0.02).toInt() else 0
        val sb = StringBuilder()
        var shown = 0
        fig.pics.forEach { p ->
            val img = imageFromUrl(p.url, p.w, p.h) ?: return@forEach
            val ratio = if (img.third > 0) img.second.toDouble() / img.third else 1.0
            val cx = wTw.toLong() * 635L
            val cy = (cx / ratio).toLong().coerceAtLeast(1L)
            if (shown > 0 && gapTw > 0) {
                val half = ((gapTw / 20.0) / 0.25 * 2.0).roundToInt().coerceIn(2, 200)
                sb.append("<w:r><w:rPr><w:sz w:val='$half'/><w:szCs w:val='$half'/></w:rPr><w:t xml:space='preserve'> </w:t></w:r>")
            }
            sb.append(drawing(img.first, cx, cy, "figure"))
            shown++
        }
        return sb.toString()
    }

    private fun figParagraph(fig: FigData, availTw: Int): String {
        val jc = when (fig.align) { "l" -> "left"; "r" -> "right"; else -> "center" }
        return para(figRuns(fig, availTw), jc = jc, before = 60, after = 60)
    }

    // ======================================================================================================
    // rich text -> runs / paragraphs
    // ======================================================================================================

    private fun mRun(text: String, pt: Double, color: String): String {
        val sz = (pt * 2.0).roundToInt().coerceAtLeast(2)
        val font = if (text.any { kindOf(it) == 1 }) bnName else "Cambria Math"
        return "<m:r><m:rPr><m:sty m:val='p'/></m:rPr><w:rPr><w:rFonts w:ascii='$font' w:hAnsi='$font' w:cs='$font'/>" +
            "<w:color w:val='$color'/><w:sz w:val='$sz'/><w:szCs w:val='$sz'/></w:rPr><m:t xml:space='preserve'>${esc(text)}</m:t></m:r>"
    }

    private fun mathContent(html: String, pt: Double, color: String): String {
        val r = parseHtml(html)
        val pieces = ArrayList<Pair<Int, String>>() // 0 plain, 1 sub, 2 sup
        var i = 0
        while (i < r.text.length) {
            val s = r.styles[i]
            val kd = if (s.sup) 2 else if (s.sub) 1 else 0
            var j = i + 1
            while (j < r.text.length) {
                val sj = r.styles[j]
                val kj = if (sj.sup) 2 else if (sj.sub) 1 else 0
                if (kj != kd) break
                j++
            }
            pieces.add(kd to r.text.substring(i, j).replace('\n', ' ').replace(FRAC_CHAR, ' ').replace(FIG_CHAR, ' '))
            i = j
        }
        val sb = StringBuilder()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                sb.append(mRun(plain.toString(), pt, color))
                plain.setLength(0)
            }
        }
        for ((kd, text) in pieces) {
            if (kd == 0) {
                plain.append(text)
            } else {
                var baseStr = "\u200B"
                if (plain.isNotEmpty()) {
                    baseStr = plain.substring(plain.length - 1)
                    plain.setLength(plain.length - 1)
                }
                flush()
                val tag = if (kd == 2) "sSup" else "sSub"
                val part = if (kd == 2) "sup" else "sub"
                sb.append("<m:$tag><m:e>${mRun(baseStr, pt, color)}</m:e><m:$part>${mRun(text, pt, color)}</m:$part></m:$tag>")
            }
        }
        flush()
        if (sb.isEmpty()) sb.append(mRun(" ", pt, color))
        return sb.toString()
    }

    private fun fractionXml(s: CharStyle, base: Base): String {
        val pt = base.pt * 0.92
        return "<m:oMath><m:f><m:num>${mathContent(s.fracN, pt, base.color)}</m:num><m:den>${mathContent(s.fracD, pt, base.color)}</m:den></m:f></m:oMath>"
    }

    private fun hasContent(s: String): Boolean = s.any { !it.isWhitespace() }

    /** Runs of r[a, b): styled text, stacked fractions (Word equations) and, inside option text, inline pictures. */
    private fun richRuns(r: Rich, a: Int, b: Int, base: Base, figW: Int): String {
        val sb = StringBuilder()
        var i = a
        while (i < b) {
            if (r.isAtom(i)) {
                sb.append(fractionXml(r.styles[i], base))
                i++
                continue
            }
            if (r.isFig(i)) {
                val fig = r.styles[i].fig
                if (fig != null) {
                    if (hasContent(r.text.substring(a, i))) sb.append("<w:r><w:br/></w:r>")
                    sb.append(figRuns(fig, figW))
                    if (hasContent(r.text.substring(i + 1, b))) sb.append("<w:r><w:br/></w:r>")
                }
                i++
                continue
            }
            val s0 = r.styles[i]
            var j = i + 1
            while (j < b && !r.isAtom(j) && !r.isFig(j) && sameFmt(r.styles[j], s0)) j++
            sb.append(styledText(r.text.substring(i, j), s0, base))
            i = j
        }
        return sb.toString()
    }

    private class Part(val a: Int, val b: Int, val fig: FigData?)

    /**
     * Rich text -> paragraphs. A picture is a block between the text pieces (like on the paper); the first text piece
     * starts with [prefix] (question number, answer label...). [lineEm] is the paper's line height in em.
     */
    private fun richParas(html: String, availW: Int, base: Base, lineEm: Double, prefix: String = "", before: Int = 0, after: Int = 0): List<String> {
        val r = parseHtml(html)
        val line = tw(lineEm * sizePt)
        val out = ArrayList<String>()
        if (!r.hasFig()) {
            out.add(para(prefix + richRuns(r, 0, r.text.length, base, availW), line = line, before = before, after = after, markPt = base.pt))
            return out
        }
        val parts = ArrayList<Part>()
        val text = r.text
        var seg = 0
        fun addText(from: Int, to: Int, figBefore: Boolean, figAfter: Boolean) {
            var a = from
            var b = to
            if (figBefore) while (a < b && text[a] == '\n') a++
            if (figAfter) while (b > a && text[b - 1] == '\n') b--
            if (a < b) parts.add(Part(a, b, null))
        }
        for (i in text.indices) {
            if (r.isFig(i)) {
                addText(seg, i, seg > 0, true)
                parts.add(Part(i, i + 1, r.styles[i].fig))
                seg = i + 1
            }
        }
        addText(seg, text.length, seg > 0, false)
        var pending = prefix
        parts.forEach { p ->
            val fig = p.fig
            if (fig != null) {
                if (pending.isNotEmpty()) {
                    out.add(para(pending, line = line, markPt = base.pt))
                    pending = ""
                }
                out.add(figParagraph(fig, availW))
            } else {
                out.add(para(pending + richRuns(r, p.a, p.b, base, availW), line = line, markPt = base.pt))
                pending = ""
            }
        }
        if (out.isEmpty()) out.add(para(pending, line = line, markPt = base.pt))
        out[0] = setBefore(out[0], before)
        out[out.lastIndex] = setAfter(out.last(), after)
        return out
    }

    // ======================================================================================================
    // header, headings, questions
    // ======================================================================================================

    private fun headerElems(): List<String> {
        if (d.brand.enabled) return brandHeaderElems()
        val out = ArrayList<String>()
        val titleTxt = doc.title.ifBlank { trKey("doc.titleDefault", lang) }
        val subTxt = doc.subjectLine().ifBlank { trKey("doc.subtitleDefault", lang) }
        val tBase = Base(sizePt * 1.5, bold = true, color = ink)
        val sBase = Base(sizePt * 1.05, color = GRAY_TEXT)
        val titleP = para(plainRuns(titleTxt, tBase), jc = "center", after = 30, line = tw(2.25 * sizePt), markPt = tBase.pt)
        val subP = para(plainRuns(subTxt, sBase), jc = "center", after = 150, line = tw(1.575 * sizePt), markPt = sBase.pt)

        if (setCode == null) {
            out.add(titleP)
            out.add(subP)
        } else {
            // title block centred between two equal cells; the framed set code sits in the right one
            val boxTxt = "${trKey("doc.set", lang)}: $setCode"
            val boxBase = Base(sizePt * 0.95, bold = true, color = ink)
            val side = min(measureTw(boxTxt, boxBase.pt, true) + 2 * 160 + 80, contentW / 3)
            val border = "<w:top w:val='single' w:sz='8' w:space='2' w:color='$ink'/><w:left w:val='single' w:sz='8' w:space='6' w:color='$ink'/>" +
                "<w:bottom w:val='single' w:sz='8' w:space='2' w:color='$ink'/><w:right w:val='single' w:sz='8' w:space='6' w:color='$ink'/>"
            val boxP = para(
                plainRuns(boxTxt, boxBase, bold = true), jc = "center", before = 40, after = 40, border = border,
                ind = "w:left='160' w:right='160'", markPt = boxBase.pt,
            )
            val grid = listOf(side, contentW - 2 * side, side)
            out.add(table(grid, listOf(row(listOf(cell(side, emptyList()), cell(grid[1], listOf(titleP, subP)), cell(side, listOf(boxP)))))))
        }

        headerTail(out, true, true)
        return out
    }

    /** Answer-sheet label, marks / time row, rule and note: shared by the default and the branded header. */
    private fun headerTail(out: ArrayList<String>, showMarksRow: Boolean, showTimeRow: Boolean) {
        if (answers) {
            val lbl = trKey("doc.answerSheet", lang)
            val lb = Base(sizePt * 1.05, bold = true, color = ink)
            val w = min(measureTw(lbl, lb.pt, true) + 2 * 240 + 40, contentW)
            val bd = "<w:tcBorders><w:top w:val='single' w:sz='8' w:space='0' w:color='$ink'/><w:left w:val='single' w:sz='8' w:space='0' w:color='$ink'/>" +
                "<w:bottom w:val='single' w:sz='8' w:space='0' w:color='$ink'/><w:right w:val='single' w:sz='8' w:space='0' w:color='$ink'/></w:tcBorders>" +
                "<w:tcMar><w:top w:w='30' w:type='dxa'/><w:left w:w='240' w:type='dxa'/><w:bottom w:w='30' w:type='dxa'/><w:right w:w='240' w:type='dxa'/></w:tcMar>"
            val p = para(plainRuns(lbl, lb, bold = true), jc = "center", line = tw(1.575 * sizePt), markPt = lb.pt)
            out.add(table(listOf(w), listOf(row(listOf(cell(w, listOf(p), props = bd)))), centered = true))
        }

        val marks = doc.marks.ifBlank { autoMarks ?: trKey("doc.marksDefault", lang) }
        val time = doc.time.ifBlank { trKey("doc.timeDefault", lang) }
        val mBase = Base(sizePt * 0.98, bold = true, color = ink)
        val mTxt = "${trKey("doc.marks", lang)}: $marks"
        val tTxt = "${trKey("doc.time", lang)}: $time"
        val mt = when {
            showMarksRow && showTimeRow -> plainRuns(mTxt, mBase) + "<w:r><w:tab/></w:r>" + plainRuns(tTxt, mBase)
            showMarksRow -> plainRuns(mTxt, mBase)
            else -> "<w:r><w:tab/></w:r>" + plainRuns(tTxt, mBase)
        }
        if (showMarksRow || showTimeRow) out.add(
            para(
                mt, before = if (answers) 210 else 90, after = 120, line = tw(1.47 * sizePt),
                tabs = "<w:tab w:val='right' w:pos='$contentW'/>", markPt = mBase.pt,
            )
        )
        out.add(para("", before = 120, after = 180, border = "<w:bottom w:val='single' w:sz='6' w:space='1' w:color='$GRAY_RULE'/>", markPt = 1.0))

        val hasNote = doc.note.isNotBlank()
        val nBase = Base(sizePt * 0.92, italic = true, color = if (hasNote) GRAY_TEXT else GRAY_RULE)
        val noteTxt = if (hasNote) "${trKey("doc.note", lang)}: ${doc.note}" else trKey("doc.noteDefault", lang)
        out.add(para(plainRuns(noteTxt, nBase), after = 210, line = tw(1.38 * sizePt), markPt = nBase.pt))
    }

    // ----- institution branding (settings panel) -----

    private fun brandHeaderElems(): List<String> {
        val b = d.brand
        val out = ArrayList<String>()
        val name = if (b.showName) b.name.trim() else ""
        val slogan = if (b.showSlogan) b.slogan.trim() else ""
        val exam = if (b.showExam) doc.title.ifBlank { trKey("doc.titleDefault", lang) } else ""
        val subj = listOf(if (b.showSubject) doc.subtitle.trim() else "", if (b.showChapter) doc.chapter.trim() else "")
            .filter { it.isNotEmpty() }.joinToString(" • ")
            .ifBlank { if (b.showSubject && b.showChapter) trKey("doc.subtitleDefault", lang) else "" }
        val code = if (b.showSetCode) setCode else null
        val logoPos = b.effLogoPos()
        val codePos = b.codePos
        val bc = if (b.color.isNotEmpty()) hex6(b.color, ink) else ink // institution name + divider colour
        val nameK = b.nameScale / 100.0
        val info = b.infoLines()
        val div = b.effDivider()

        // logo: about 4.2 lines tall (times the logo size setting), never wider than a third of the page
        var logoRun = ""
        var logoW = 0
        if (b.hasLogo()) imageFromUrl(b.logo, b.logoW, b.logoH)?.let { img ->
            val ratio = if (img.third > 0) img.second.toDouble() / img.third else 1.0
            var hTw = tw(4.2 * sizePt * b.logoScale / 100.0)
            var wTw = (hTw * ratio).toInt()
            val maxW = contentW / 3
            if (wTw > maxW) { wTw = maxW; hTw = (wTw / ratio).toInt() }
            logoRun = drawing(img.first, wTw.toLong() * 635L, hTw.toLong() * 635L, "logo")
            logoW = wTw
        }
        val boxBase = Base(sizePt * 0.95, bold = true, color = ink)
        val boxTxt = code?.let { "${trKey("doc.set", lang)}: $it" } ?: ""
        val codeW = if (code != null) min(measureTw(boxTxt, boxBase.pt, true) + 2 * 160 + 80, contentW / 3) else 0
        val border = "<w:top w:val='single' w:sz='8' w:space='2' w:color='$ink'/><w:left w:val='single' w:sz='8' w:space='6' w:color='$ink'/>" +
            "<w:bottom w:val='single' w:sz='8' w:space='2' w:color='$ink'/><w:right w:val='single' w:sz='8' w:space='6' w:color='$ink'/>"

        fun slotW(pos: String): Int = max(
            if (logoRun.isNotEmpty() && logoPos == pos) logoW else 0,
            if (code != null && codePos == pos) codeW else 0,
        )
        fun jcOf(pos: String) = when (pos) { POS_START -> "left"; POS_END -> "right"; else -> "center" }
        fun slotParas(pos: String, w: Int): List<String> {
            val ps = ArrayList<String>()
            if (logoRun.isNotEmpty() && logoPos == pos) ps.add(para(logoRun, jc = jcOf(pos), after = 60))
            if (code != null && codePos == pos) {
                val gap = max(0, w - codeW) + 160
                val ind = if (pos == POS_START) "w:left='160' w:right='$gap'" else "w:left='$gap' w:right='160'"
                ps.add(para(plainRuns(boxTxt, boxBase, bold = true), jc = "center", before = 40, after = 40, border = border, ind = ind, markPt = boxBase.pt))
            }
            return ps
        }
        fun tp(text: String, em: Double, jc: String, bold: Boolean = false, italic: Boolean = false, gray: Boolean = false, before: Int = 0, after: Int = 0, color: String? = null): String {
            val base = Base(sizePt * em, bold = bold, italic = italic, color = color ?: if (gray) GRAY_TEXT else ink)
            return para(plainRuns(text, base), jc = jc, before = before, after = after, line = tw(1.5 * em * sizePt), markPt = base.pt)
        }
        fun nameBlock(jc: String, nameEm: Double, sloganEm: Double): List<String> {
            val ps = ArrayList<String>()
            if (name.isNotEmpty()) ps.add(tp(name, nameEm * nameK, jc, bold = true, color = bc))
            if (slogan.isNotEmpty()) ps.add(tp(slogan, sloganEm, jc, italic = true, gray = true))
            info.forEach { ps.add(tp(it, 0.88, jc, gray = true)) }
            return ps
        }
        // rule under the institution block (thin / thick / double); the layout decides the default
        fun dividerPara(): String? {
            val bd = when (div) {
                DIV_THICK -> "<w:bottom w:val='single' w:sz='24' w:space='1' w:color='$bc'/>"
                DIV_DOUBLE -> "<w:bottom w:val='double' w:sz='6' w:space='1' w:color='$bc'/>"
                DIV_NONE -> return null
                else -> "<w:bottom w:val='single' w:sz='10' w:space='1' w:color='$bc'/>"
            }
            return para("", before = 60, after = 100, border = bd, markPt = 1.0)
        }
        fun examBlock(examEm: Double): List<String> {
            val ps = ArrayList<String>()
            if (exam.isNotEmpty()) ps.add(tp(exam, examEm, "center", bold = true, before = 60))
            if (subj.isNotEmpty()) ps.add(tp(subj, 1.0, "center", gray = true, after = 60))
            return ps
        }

        when (b.layout) {
            BRAND_LETTER -> {
                val sw = slotW(POS_START).let { if (it > 0) it + 140 else 0 }
                val ew = slotW(POS_END).let { if (it > 0) it + 140 else 0 }
                val jc = if (logoRun.isEmpty()) "center" else if (logoPos == POS_END) "right" else "left"
                val mid = nameBlock(jc, 1.6, 0.92)
                if (sw > 0 || ew > 0) {
                    val grid = ArrayList<Int>()
                    val cells = ArrayList<String>()
                    if (sw > 0) { grid.add(sw); cells.add(cell(sw, slotParas(POS_START, sw - 140))) }
                    val mw = contentW - sw - ew
                    grid.add(mw); cells.add(cell(mw, mid.ifEmpty { listOf(para("", markPt = 1.0)) }, props = "<w:vAlign w:val='center'/>"))
                    if (ew > 0) { grid.add(ew); cells.add(cell(ew, slotParas(POS_END, ew - 140))) }
                    out.add(table(grid, listOf(row(cells))))
                } else out.addAll(mid)
                if (mid.isNotEmpty() || sw > 0 || ew > 0) dividerPara()?.let { out.add(it) }
                out.addAll(examBlock(1.2))
            }
            BRAND_BOARD -> {
                val side = max(slotW(POS_START), slotW(POS_END))
                val mid = nameBlock("center", 1.5, 0.9) + examBlock(1.15)
                if (side > 0) {
                    val mw = contentW - 2 * side
                    out.add(table(listOf(side, mw, side), listOf(row(listOf(
                        cell(side, slotParas(POS_START, side)),
                        cell(mw, mid.ifEmpty { listOf(para("", markPt = 1.0)) }),
                        cell(side, slotParas(POS_END, side)),
                    )))))
                } else out.addAll(mid)
                dividerPara()?.let { out.add(it) }
            }
            else -> {
                val side = max(slotW(POS_START), slotW(POS_END))
                val centerLogo = logoRun.isNotEmpty() && logoPos == POS_CENTER
                if (side > 0) {
                    val mw = contentW - 2 * side
                    out.add(table(listOf(side, mw, side), listOf(row(listOf(
                        cell(side, slotParas(POS_START, side)),
                        cell(mw, if (centerLogo) listOf(para(logoRun, jc = "center", after = 60)) else emptyList()),
                        cell(side, slotParas(POS_END, side)),
                    )))))
                } else if (centerLogo) out.add(para(logoRun, jc = "center", after = 60))
                out.addAll(nameBlock("center", 1.6, 0.92))
                out.addAll(examBlock(1.2))
                dividerPara()?.let { out.add(it) }
            }
        }
        headerTail(out, b.showMarks, b.showTime)
        return out
    }

    private fun headingElems(type: String): List<String> {
        val center = st.sectionLayout == "center"
        val line = st.lineOn(if (center) "center" else "left")
        val title = st.sectionTitle(type, bn)
        val instr = st.sectionInstruction(type)
        val jc = if (center) "center" else "left"
        val border = if (line) "<w:bottom w:val='single' w:sz='8' w:space='3' w:color='$INK'/>" else ""
        val tBase = Base(sizePt * 1.06, bold = true, color = ink)
        val out = ArrayList<String>()
        out.add(
            para(
                plainRuns(title, tBase, bold = true), jc = jc, before = 150, after = if (instr.isEmpty()) 120 else 0,
                line = tw(1.59 * sizePt), keepNext = true, border = if (instr.isEmpty()) border else "", markPt = tBase.pt,
            )
        )
        if (instr.isNotEmpty()) {
            val iBase = Base(sizePt * 0.92, italic = true, color = GRAY_TEXT)
            out.add(para(plainRuns(instr, iBase), jc = jc, before = 30, after = 120, line = tw(1.38 * sizePt), keepNext = true, border = border, markPt = iBase.pt))
        }
        return out
    }

    private val qBase get() = Base(sizePt, color = ink)
    private val ansBase get() = Base(sizePt * 0.88, color = GRAY_TEXT)

    private fun badgeWidth(txt: String): Int = tw(txt.length * sizePt * 0.62) + 200

    private fun marksText(m: String): String = "[" + localDigits(m.trim()) + "]"

    /** Number + question text; the [marks] badge sits in a narrow right-hand cell, like on the paper. */
    private fun qHead(q: Question, number: Int, colW: Int): List<String> {
        val prefix = styledText("${formatNumber(number, bn)}.  ", CharStyle(bold = true), qBase)
        if (showMarks && q.marks.isNotBlank()) {
            val bt = marksText(q.marks)
            val bw = badgeWidth(bt)
            val left = richParas(q.text, colW - bw, qBase, 1.5, prefix, after = 75)
            val right = para(plainRuns(bt, qBase), jc = "right", line = tw(1.5 * sizePt))
            return listOf(table(listOf(colW - bw, bw), listOf(row(listOf(cell(colW - bw, left), cell(bw, listOf(right)))))))
        }
        return richParas(q.text, colW, qBase, 1.5, prefix, after = 75)
    }

    private fun answerLine(label: String, html: String, availW: Int, before: Int = 45): List<String> {
        val prefix = styledText("$label ", CharStyle(bold = true), Base(sizePt * 0.88, color = ansHex))
        return richParas(html, availW, ansBase, 1.232, prefix, before = before, after = 45)
    }

    private val hang: Int get() = tw(1.9 * sizePt)

    private fun optBase(correct: Boolean) = Base(sizePt * 0.96, bold = correct, color = if (correct) ansHex else ink)

    private fun labelRuns(label: String, hi: Boolean = false): String = plainRuns(label, optBase(hi))

    /** "(ক) text" / "(A) text": real text, wrapped lines line up after the label. */
    private fun optionPara(label: String, html: String, availW: Int, after: Int, hi: Boolean = false): String {
        val r = parseHtml(html)
        val runs = labelRuns(label, hi) + "<w:r><w:tab/></w:r>" + richRuns(r, 0, r.text.length, optBase(hi), availW - hang)
        return para(runs, line = tw(1.344 * sizePt), after = after, ind = "w:left='$hang' w:hanging='$hang'", markPt = sizePt * 0.96)
    }

    private fun inlineOptions(items: List<Triple<String, String, Boolean>>, availW: Int): String {
        val sb = StringBuilder()
        items.forEachIndexed { i, (label, html, hi) ->
            if (i > 0) sb.append(run("    ", rPr(2, false, false, false, 0, ink, sizePt * 0.96)))
            sb.append(labelRuns(label, hi))
            sb.append(run("\u00A0", rPr(2, false, false, false, 0, ink, sizePt * 0.96)))
            val r = parseHtml(html)
            sb.append(richRuns(r, 0, r.text.length, optBase(hi), availW))
        }
        return para(sb.toString(), line = tw(1.344 * sizePt), after = 60, markPt = sizePt * 0.96)
    }

    private fun optionsBlock(q: Question, k: Int, colW: Int, hi: Set<Int> = emptySet()): List<String> {
        val layout = if (k == 3 && q.layout == "grid") "vertical" else q.layout
        val opts = q.options.mapIndexed { i, o -> Triple("(${optionLabel(i, bn)})", o.text, i in hi) }
        return when (layout) {
            "horizontal" -> listOf(inlineOptions(opts, colW))
            "grid" -> {
                val half = colW / 2
                val rows = opts.chunked(2).map { rowOpts ->
                    val cells = ArrayList<String>()
                    rowOpts.forEach { (l, t, h) ->
                        cells.add(cell(half, listOf(optionPara(l, t, half - 105, 60, h)), props = "<w:tcMar><w:right w:w='105' w:type='dxa'/></w:tcMar>"))
                    }
                    if (rowOpts.size == 1) cells.add(cell(colW - half, emptyList()))
                    row(cells)
                }
                listOf(table(listOf(half, colW - half), rows))
            }
            else -> opts.map { (l, t, h) -> optionPara(l, t, colW, 60, h) }
        }
    }

    private fun creativeBlock(q: Question, number: Int, colW: Int): List<String> {
        val vis = q.subs.indices.filter { plainText(q.subs[it].text).isNotBlank() }
        val out = ArrayList<String>()
        val headMarks = showMarks && q.marks.isNotBlank()
        val subMarks = showMarks && vis.any { q.subs[it].marks.isNotBlank() }
        val badge = headMarks || subMarks
        val allMarks = (vis.filter { q.subs[it].marks.isNotBlank() }.map { marksText(q.subs[it].marks) } +
            (if (headMarks) listOf(marksText(q.marks)) else emptyList()))
        val bw = if (badge) badgeWidth(allMarks.maxByOrNull { it.length } ?: "[0]") else 0
        val labelW = tw(1.5 * sizePt) + 90
        val textW = colW - labelW - bw
        val grid = if (badge) listOf(labelW, textW, bw) else listOf(labelW, textW)
        val rows = ArrayList<String>()

        val prefix = styledText("${formatNumber(number, bn)}.  ", CharStyle(bold = true), qBase)
        val headParas = richParas(q.text, labelW + textW, qBase, 1.5, prefix, after = 135)
        val headCells = ArrayList<String>()
        headCells.add(cell(labelW + textW, headParas, span = 2))
        if (badge) headCells.add(cell(bw, if (headMarks) listOf(para(plainRuns(marksText(q.marks), qBase), jc = "right", line = tw(1.5 * sizePt))) else emptyList()))
        rows.add(row(headCells))

        vis.forEachIndexed { n, si ->
            val s = q.subs[si]
            val lab = para(plainRuns(subLabel(si, bn) + ".", qBase), line = tw(1.5 * sizePt), after = if (n == vis.size - 1) 0 else 75)
            val txt = richParas(s.text, textW, qBase, 1.5, after = if (n == vis.size - 1) 0 else 75)
            val cells = arrayListOf(cell(labelW, listOf(lab)), cell(textW, txt))
            if (badge) {
                val bp = if (showMarks && s.marks.isNotBlank()) listOf(para(plainRuns(marksText(s.marks), qBase), jc = "right", line = tw(1.5 * sizePt))) else emptyList()
                cells.add(cell(bw, bp))
            }
            rows.add(row(cells))
        }
        out.add(table(grid, rows))
        if (ansOn) {
            var first = true
            vis.forEach { si ->
                val s = q.subs[si]
                if (plainText(s.answer).isNotBlank()) {
                    out.addAll(answerLine("${trKey("t.answerLbl", lang)} (${subLabel(si, bn)}):", s.answer, colW, if (first) 90 else 45))
                    first = false
                }
            }
        }
        return out
    }

    private fun questionElems(p: Placed, k: Int): List<String> {
        val q = p.q
        val colW = colWOf(k)
        val ansWord = trKey("t.answerLbl", lang)
        val explWord = trKey("label.explanation", lang)
        val out = ArrayList<String>()
        when (q.kind()) {
            QType.CREATIVE -> out.addAll(creativeBlock(q, p.n, colW))
            QType.TF -> {
                out.addAll(qHead(q, p.n, colW))
                out.add(inlineOptions(listOf(Triple("( )", trKey("t.tfTrue", lang), false), Triple("( )", trKey("t.tfFalse", lang), false)), colW))
                if (answers) {
                    val key = if (!ansOn) "" else when (q.tfAnswer) {
                        true -> trKey("t.tfTrue", lang)
                        false -> trKey("t.tfFalse", lang)
                        null -> ""
                    }
                    val hasEx = exOn && plainText(q.explanation).isNotBlank()
                    if (key.isNotEmpty() || hasEx) {
                        val label = (if (key.isNotEmpty()) "($key)" else "") + (if (key.isNotEmpty() && hasEx) " " else "") + (if (hasEx) "$explWord:" else "")
                        out.addAll(answerLine(label, if (hasEx) q.explanation else "", colW))
                    }
                }
            }
            QType.FILL, QType.DESC -> {
                out.addAll(qHead(q, p.n, colW))
                if (ansOn && plainText(q.answer).isNotBlank()) out.addAll(answerLine("$ansWord:", q.answer, colW))
            }
            QType.SHORT -> {
                out.addAll(qHead(q, p.n, colW))
                val src = if (ansOn && plainText(q.answer).isNotBlank()) q.answer else if (exOn) q.explanation else ""
                if (answers && plainText(src).isNotBlank()) out.addAll(answerLine("$ansWord:", src, colW))
            }
            else -> {
                out.addAll(qHead(q, p.n, colW))
                val correct = q.options.indices.filter { q.options[it].correct }
                val keys = correct.map { optionLabel(it, bn) }
                val showKey = ansOn && keys.isNotEmpty()
                val hasEx = exOn && plainText(q.explanation).isNotBlank()
                out.addAll(optionsBlock(q, k, colW, if (showKey && st.answerLayout == ANS_LAYOUT_FILL) correct.toSet() else emptySet()))
                if (st.answerLayout == ANS_LAYOUT_EXPL) {
                    // answer key: "(খ) ব্যাখ্যা:" line
                    val key = if (showKey) "(" + keys.joinToString(", ") + ")" else ""
                    if (key.isNotEmpty() || hasEx) {
                        val label = key + (if (key.isNotEmpty() && hasEx) " " else "") + (if (hasEx) "$explWord:" else "")
                        out.addAll(answerLine(label, if (hasEx) q.explanation else "", colW))
                    }
                } else {
                    if (hasEx) out.addAll(answerLine("$explWord:", q.explanation, colW))
                    if (showKey && st.answerLayout == ANS_LAYOUT_CORNER) {
                        // bottom-right corner: the correct option(s) in brackets, e.g. "(খ)"; the options above stay untouched
                        val tag = styledText("(" + keys.joinToString(", ") + ")", CharStyle(bold = true), Base(sizePt * 0.92, color = ansHex))
                        out.add(para(tag, jc = "right", before = 30, line = tw(1.232 * sizePt), markPt = sizePt * 0.92))
                    }
                }
            }
        }
        // two tables never touch (Word would merge them into one)
        val spaced = ArrayList<String>()
        out.forEach { e ->
            if (e.startsWith("<w:tbl>") && spaced.isNotEmpty() && spaced.last().startsWith("<w:tbl>")) spaced.add(spacer(20))
            spaced.add(e)
        }
        return withBottom(keepTogether(spaced), 180)
    }

    /** How many laid-out pieces a question gives (a Creative question is one piece per sub-question, like the PDF). */
    private fun pieces(q: Question): Int {
        if (q.kind() != QType.CREATIVE) return 1
        val vis = q.subs.indices.filter { plainText(q.subs[it].text).isNotBlank() }
        val av = vis.filter { plainText(q.subs[it].answer).isNotBlank() }
        val ans = ansOn && av.isNotEmpty()
        return if (vis.isNotEmpty() && (vis.size > 1 || ans)) vis.size + (if (ans) av.size else 0) else 1
    }

    // ======================================================================================================
    // sections / columns
    // ======================================================================================================

    private fun sectPr(k: Int, sep: Boolean): String {
        val cols = if (k >= 2) "<w:cols w:num='$k' w:space='${gapOf(k)}'${if (sep) " w:sep='1'" else ""}/>" else "<w:cols w:space='720'/>"
        return "<w:sectPr><w:footerReference w:type='default' r:id='rId3'/><w:type w:val='continuous'/>" +
            "<w:pgSz w:w='$pageW' w:h='$pageH'/>" +
            "<w:pgMar w:top='$margin' w:right='$margin' w:bottom='$margin' w:left='$margin' w:header='720' w:footer='425' w:gutter='0'/>" +
            "$cols</w:sectPr>"
    }

    private fun buildSegments(): List<Segment> {
        val segs = ArrayList<Segment>()
        segs.add(Segment(headerElems(), 1, false))
        val placed = arrangeSections(questions, st)
        // group into runs of equal column count / divider
        val blocks = ArrayList<Any>() // String = heading of that type, RunGroup = questions
        var cur: RunGroup? = null
        var curKey = ""
        for (p in placed) {
            if (p.firstOfSection) {
                blocks.add(p.type)
                cur = null
                curKey = ""
            }
            val k = st.colsOf(p.q)
            val dv = st.dividerOn(p.type)
            val key = "$k|${if (dv) p.type else ""}"
            if (cur == null || key != curKey) {
                cur = RunGroup(k, dv && k >= 2)
                blocks.add(cur)
                curKey = key
            }
            cur.items.add(p)
        }
        for (b in blocks) {
            if (b is String) {
                segs.add(Segment(headingElems(b), 1, false))
            } else if (b is RunGroup) {
                val total = b.items.sumOf { pieces(it.q) }
                val k = max(1, min(b.k, total))
                val elems = ArrayList<String>()
                b.items.forEach { elems.addAll(questionElems(it, k)) }
                segs.add(Segment(elems, k, b.div && k >= 2))
            }
        }
        return segs
    }

    // ======================================================================================================
    // package
    // ======================================================================================================

    private val ns = "xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' " +
        "xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' " +
        "xmlns:wp='http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing' " +
        "xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' " +
        "xmlns:pic='http://schemas.openxmlformats.org/drawingml/2006/picture' " +
        "xmlns:m='http://schemas.openxmlformats.org/officeDocument/2006/math'"

    private val xmlHead = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>\n"

    private fun documentXml(): String {
        val segs = buildSegments()
        val body = StringBuilder()
        segs.forEachIndexed { i, sg ->
            val elems = ArrayList<String>(sg.elems)
            if (!isPara(elems.last())) elems.add(spacer(20))
            if (i < segs.size - 1) {
                val lastP = elems.last()
                elems[elems.lastIndex] = lastP.replaceFirst("</w:pPr>", sectPr(sg.k, sg.sep) + "</w:pPr>")
            }
            body.append(elems.joinToString(""))
        }
        val lastSeg = segs.last()
        body.append(sectPr(lastSeg.k, lastSeg.sep))
        val bg = hex6(st.pageColor, "FFFFFF")
        val bgXml = if (bg != "FFFFFF") "<w:background w:color='$bg'/>" else ""
        return "${xmlHead}<w:document $ns>$bgXml<w:body>$body</w:body></w:document>"
    }

    private fun stylesXml(): String =
        "${xmlHead}<w:styles xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>" +
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii='$latinName' w:hAnsi='$latinName' w:eastAsia='$latinName' w:cs='$bnName'/>" +
            "<w:color w:val='$ink'/><w:sz w:val='${(sizePt * 2).roundToInt()}'/><w:szCs w:val='${(sizePt * 2).roundToInt()}'/>" +
            "<w:lang w:val='en-US' w:eastAsia='en-US' w:bidi='bn-BD'/></w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr><w:spacing w:after='0' w:line='240' w:lineRule='auto'/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type='paragraph' w:default='1' w:styleId='Normal'><w:name w:val='Normal'/><w:qFormat/></w:style>" +
            "<w:style w:type='character' w:default='1' w:styleId='DefaultParagraphFont'><w:name w:val='Default Paragraph Font'/><w:uiPriority w:val='1'/><w:semiHidden/></w:style>" +
            "<w:style w:type='table' w:default='1' w:styleId='TableNormal'><w:name w:val='Normal Table'/><w:uiPriority w:val='99'/><w:semiHidden/>" +
            "<w:tblPr><w:tblInd w:w='0' w:type='dxa'/><w:tblCellMar><w:top w:w='0' w:type='dxa'/><w:left w:w='0' w:type='dxa'/>" +
            "<w:bottom w:w='0' w:type='dxa'/><w:right w:w='0' w:type='dxa'/></w:tblCellMar></w:tblPr></w:style>" +
            "</w:styles>"

    private fun footerXml(): String {
        val base = Base(sizePt * 0.8, color = GRAY_RULE)
        val rp = rPr(2, false, false, false, 0, GRAY_RULE, base.pt)
        val label = plainRuns("${trKey("doc.page", lang)} ", base)
        return "${xmlHead}<w:ftr $ns><w:p><w:pPr><w:jc w:val='right'/></w:pPr>$label" +
            "<w:fldSimple w:instr=' PAGE '><w:r>$rp<w:t>1</w:t></w:r></w:fldSimple>" +
            "<w:r>$rp<w:t>/</w:t></w:r>" +
            "<w:fldSimple w:instr=' NUMPAGES '><w:r>$rp<w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>"
    }

    // ----- font embedding (so Bangla looks the same in Word on a PC that has no Noto Serif Bengali) -----
    private fun embedCandidates(): List<EmbedFont> {
        val names = linkedSetOf(latinName, bnName)
        val out = ArrayList<EmbedFont>()
        names.forEach { n ->
            when (n) {
                "Noto Serif Bengali" -> out.add(EmbedFont(n, listOf(R.font.noto_serif_bengali)))
                "Kalpurush" -> out.add(EmbedFont(n, listOf(R.font.kalpurush)))
                "SolaimanLipi" -> out.add(EmbedFont(n, listOf(R.font.solaiman_lipi)))
                "Hind Siliguri" -> out.add(EmbedFont(n, listOf(R.font.hind_siliguri_regular, R.font.hind_siliguri_bold)))
            }
        }
        return out
    }

    private class Embedded(val name: String, val parts: List<Pair<String, String>>) // (rId, fontKey)

    private val fontParts = ArrayList<Pair<String, ByteArray>>() // "fonts/font1.odttf" -> obfuscated bytes
    private val fontRels = ArrayList<Rel>()
    private val embedded = ArrayList<Embedded>()

    private fun obfuscate(font: ByteArray, guid: UUID): ByteArray {
        val hex = guid.toString().replace("-", "")
        val key = ByteArray(16)
        for (i in 0 until 16) {
            val pair = hex.substring((15 - i) * 2, (15 - i) * 2 + 2)
            key[i] = pair.toInt(16).toByte()
        }
        val out = font.copyOf()
        val n = min(32, out.size)
        for (i in 0 until n) out[i] = (out[i].toInt() xor key[i % 16].toInt()).toByte()
        return out
    }

    private fun prepareFonts() {
        var idx = 0
        embedCandidates().forEach { ef ->
            try {
                val parts = ArrayList<Pair<String, String>>()
                ef.resIds.forEach { resId ->
                    val bytes = activity.resources.openRawResource(resId).use { it.readBytes() }
                    if (bytes.size < 1024) return@forEach
                    idx++
                    val guid = UUID.randomUUID()
                    val name = "fonts/font$idx.odttf"
                    fontParts.add(name to obfuscate(bytes, guid))
                    val rid = "rId$idx"
                    fontRels.add(Rel(rid, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/font", name))
                    parts.add(rid to "{${guid.toString().uppercase()}}")
                }
                if (parts.isNotEmpty()) embedded.add(Embedded(ef.name, parts))
            } catch (e: Exception) {
                // no embedding for this font: Word falls back to an installed font, nothing else is lost
            }
        }
    }

    private fun fontTableXml(): String {
        val sb = StringBuilder("${xmlHead}<w:fonts $ns>")
        embedded.forEach { e ->
            sb.append("<w:font w:name='${e.name}'><w:charset w:val='00'/><w:family w:val='roman'/><w:pitch w:val='variable'/>")
            e.parts.forEachIndexed { i, (rid, key) ->
                val tag = if (i == 0) "embedRegular" else "embedBold"
                sb.append("<w:$tag r:id='$rid' w:fontKey='$key'/>")
            }
            sb.append("</w:font>")
        }
        sb.append("</w:fonts>")
        return sb.toString()
    }

    private fun settingsXml(): String {
        val bg = hex6(st.pageColor, "FFFFFF") != "FFFFFF"
        return "${xmlHead}<w:settings xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>" +
            (if (bg) "<w:displayBackgroundShape/>" else "") +
            (if (embedded.isNotEmpty()) "<w:embedTrueTypeFonts/>" else "") +
            "<w:defaultTabStop w:val='720'/><w:characterSpacingControl w:val='doNotCompress'/>" +
            "<w:compat><w:compatSetting w:name='compatibilityMode' w:uri='http://schemas.microsoft.com/office/word' w:val='15'/></w:compat>" +
            "</w:settings>"
    }

    private fun relsXml(list: List<Rel>): String {
        val sb = StringBuilder("${xmlHead}<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>")
        list.forEach { sb.append("<Relationship Id='${it.id}' Type='${it.type}' Target='${it.target}'/>") }
        sb.append("</Relationships>")
        return sb.toString()
    }

    fun render(): ByteArray {
        prepareFonts()
        val docXml = documentXml() // fills the image relationships
        val wr = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val docRels = ArrayList<Rel>()
        docRels.add(Rel("rId1", "$wr/styles", "styles.xml"))
        docRels.add(Rel("rId2", "$wr/settings", "settings.xml"))
        docRels.add(Rel("rId3", "$wr/footer", "footer1.xml"))
        docRels.add(Rel("rId4", "$wr/fontTable", "fontTable.xml"))
        docRels.addAll(rels)

        val types = StringBuilder("${xmlHead}<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>")
        types.append("<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>")
        types.append("<Default Extension='xml' ContentType='application/xml'/>")
        usedExt.forEach { ext ->
            val ct = when (ext) { "png" -> "image/png"; "gif" -> "image/gif"; else -> "image/jpeg" }
            types.append("<Default Extension='$ext' ContentType='$ct'/>")
        }
        if (fontParts.isNotEmpty()) types.append("<Default Extension='odttf' ContentType='application/vnd.openxmlformats-officedocument.obfuscatedFont'/>")
        val wm = "application/vnd.openxmlformats-officedocument.wordprocessingml"
        types.append("<Override PartName='/word/document.xml' ContentType='$wm.document.main+xml'/>")
        types.append("<Override PartName='/word/styles.xml' ContentType='$wm.styles+xml'/>")
        types.append("<Override PartName='/word/settings.xml' ContentType='$wm.settings+xml'/>")
        types.append("<Override PartName='/word/fontTable.xml' ContentType='$wm.fontTable+xml'/>")
        types.append("<Override PartName='/word/footer1.xml' ContentType='$wm.footer+xml'/>")
        types.append("</Types>")

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, data: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
            put("[Content_Types].xml", types.toString().toByteArray(Charsets.UTF_8))
            put("_rels/.rels", relsXml(listOf(Rel("rId1", "$wr/officeDocument", "word/document.xml"))).toByteArray(Charsets.UTF_8))
            put("word/document.xml", docXml.toByteArray(Charsets.UTF_8))
            put("word/styles.xml", stylesXml().toByteArray(Charsets.UTF_8))
            put("word/settings.xml", settingsXml().toByteArray(Charsets.UTF_8))
            put("word/footer1.xml", footerXml().toByteArray(Charsets.UTF_8))
            put("word/fontTable.xml", fontTableXml().toByteArray(Charsets.UTF_8))
            put("word/_rels/document.xml.rels", relsXml(docRels).toByteArray(Charsets.UTF_8))
            if (fontRels.isNotEmpty()) put("word/_rels/fontTable.xml.rels", relsXml(fontRels).toByteArray(Charsets.UTF_8))
            mediaParts.forEach { (n, b) -> put("word/$n", b) }
            fontParts.forEach { (n, b) -> put("word/$n", b) }
        }
        return out.toByteArray()
    }
}
