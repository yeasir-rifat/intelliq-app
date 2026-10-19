package com.intelliq.rifatplex.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.intelliq.rifatplex.ui.theme.AdorshoLipi
import com.intelliq.rifatplex.ui.theme.Cambria
import com.intelliq.rifatplex.ui.theme.Georgia
import com.intelliq.rifatplex.ui.theme.TimesNewRoman
import com.intelliq.rifatplex.ui.theme.Kalpurush
import com.intelliq.rifatplex.ui.theme.Nikosh
import com.intelliq.rifatplex.ui.theme.SolaimanLipi
import com.intelliq.rifatplex.ui.theme.NotoSerifBengali

/**
 * Fonts of the question paper / PDF, chosen per character:
 *  - Bangla letters, digits, danda  -> [bangla]  (Settings: Noto Serif Bengali / Nikosh / Kalpurush / SolaimanLipi / AdorshoLipi)
 *  - English letters and digits     -> [latin]   (Settings: Times New Roman / Georgia / Cambria)
 *  - formulas -> [math] (always Cambria). Detected automatically: fractions, sub/superscripts, math symbols
 *    and written-out equations such as "3x + 5 = 20" (see [findMathRuns]).
 */
data class PaperFonts(
    val latin: FontFamily,
    val bangla: FontFamily,
    val math: FontFamily,
    /** Family for text that has no script of its own (empty text, only spaces). */
    val base: FontFamily,
) {
    private enum class K { BN, LATIN, MATH, NEUTRAL }

    private fun kindOf(c: Char): K {
        val x = c.code
        return when {
            x in 0x0980..0x09FF || x == 0x0964 || x == 0x0965 -> K.BN
            x < 0x80 -> if (c.isLetterOrDigit()) K.LATIN else K.NEUTRAL
            c == '\u200C' || c == '\u200D' || c == '\u00A0' -> K.NEUTRAL
            isMathChar(x) -> K.MATH
            c.isLetterOrDigit() -> K.LATIN
            else -> K.NEUTRAL
        }
    }

    /**
     * Adds a font to every run of [a] (existing styles such as bold, colour or baseline shift are kept).
     * [mathDefault] = inside a fraction: Latin letters and digits are formula text too.
     */
    fun apply(a: AnnotatedString, mathDefault: Boolean = false): AnnotatedString {
        val t = a.text
        if (t.isEmpty()) return a
        val kind = Array(t.length) { kindOf(t[it]) }
        // sub / superscript runs are formula text (except Bangla letters)
        a.spanStyles.forEach { r ->
            if (r.item.baselineShift != null) for (i in r.start until minOf(r.end, t.length)) {
                if (kind[i] != K.BN) kind[i] = K.MATH
            }
        }
        if (mathDefault) for (i in t.indices) if (kind[i] == K.LATIN) kind[i] = K.MATH
        // written-out equations / expressions ("x^2 + 3x = 5") are formula text too (never Bangla letters)
        for (r in findMathRuns(t)) for (i in r) if (kind[i] != K.BN) kind[i] = K.MATH
        // spaces / punctuation follow the letter before them (or after them at the very start)
        var last = K.NEUTRAL
        for (i in t.indices) if (kind[i] == K.NEUTRAL) kind[i] = last else last = kind[i]
        var next = K.NEUTRAL
        for (i in t.indices.reversed()) if (kind[i] == K.NEUTRAL) kind[i] = next else next = kind[i]
        fun fam(k: K): FontFamily = when (k) {
            K.BN -> bangla
            K.LATIN -> latin
            K.MATH -> math
            K.NEUTRAL -> base
        }
        return buildAnnotatedString {
            append(a)
            var i = 0
            while (i < t.length) {
                var j = i + 1
                while (j < t.length && kind[j] == kind[i]) j++
                addStyle(SpanStyle(fontFamily = fam(kind[i])), i, j)
                i = j
            }
        }
    }

    fun apply(text: String): AnnotatedString = apply(AnnotatedString(text))

    /** Family for a short label such as an option letter (A / ক). */
    fun familyFor(label: String): FontFamily =
        if (label.any { kindOf(it) == K.BN }) bangla else latin
}

/**
 * Fixed fonts of the OMR sheet and the answer sheet ("উত্তরমালা"): Bangla = Kalpurush, English = Times New Roman look
 * (TimesNewRoman), formulas = Cambria. Never chosen from Settings (see [com.intelliq.rifatplex.data.SheetStyle]). The few
 * symbols these sheets use (the arrow of "১ → খ") come from Times New Roman, which has that glyph.
 */
val sheetFonts: PaperFonts = PaperFonts(latin = TimesNewRoman, bangla = Kalpurush, math = Cambria, base = TimesNewRoman)

/** English font of Settings -> Times New Roman | Georgia | Cambria. */
private fun englishFont(name: String): FontFamily = when (name) {
    "Georgia" -> Georgia
    "Cambria" -> Cambria
    else -> TimesNewRoman
}

/** Bangla font of Settings -> Noto Serif Bengali | Nikosh | Kalpurush | SolaimanLipi | AdorshoLipi. */
private fun banglaFont(name: String): FontFamily = when (name) {
    "Nikosh" -> Nikosh
    "Kalpurush" -> Kalpurush
    "SolaimanLipi" -> SolaimanLipi
    "AdorshoLipi" -> AdorshoLipi
    "Noto Serif Bengali" -> NotoSerifBengali
    else -> Kalpurush
}

/**
 * [en] / [bn] = the two Settings dropdowns. Formulas always use Cambria. [banglaDoc] = the paper is in Bangla,
 * so text without a script of its own (spaces, empty lines) uses the Bangla font; otherwise the English one.
 */
fun paperFonts(en: String, bn: String, banglaDoc: Boolean = false): PaperFonts {
    val latin = englishFont(en)
    val bangla = banglaFont(bn)
    return PaperFonts(latin = latin, bangla = bangla, math = Cambria, base = if (banglaDoc) bangla else latin)
}

/**
 * Index ranges of formula text inside [t]: an expression made of numbers / short variables (x, 3x, ab2) joined by
 * operators, e.g. "3x + 5 = 20", "x^2 - 4", "(a+b)^2", "v = u + at". It needs at least one strong operator
 * (= + - x ÷ ^ < > ...) so that ordinary prose and dates ("well-known", "and/or", "12-05-2024") stay in the text font.
 */
internal fun findMathRuns(t: String): List<IntRange> {
    if (t.length < 3) return emptyList()
    var strong = false
    for (c in t) if (c in STRONG_OPS) { strong = true; break }
    if (!strong) return emptyList()
    val out = ArrayList<IntRange>()
    for (m in MATH_EXPR.findAll(t)) {
        val s = m.value
        if (s.any { it in STRONG_OPS }) out.add(m.range)
    }
    return out
}

private const val STRONG_OPS = "=+\u2212\u00D7\u00F7^<>\u2264\u2265\u2260\u00B1\u2248"
// operand = number | short variable (1-2 letters, optional digits / coefficient) ; joined by operators and spaces
private const val OPERAND = """[(\[]*(?:\d+(?:\.\d+)?[A-Za-z]{0,2}\d*|[A-Za-z]{1,2}\d*)[)\]]*"""
private const val OPERATOR = """\s*[=+\u2212\u00D7\u00F7^<>\u2264\u2265\u2260\u00B1\u2248*/\-]\s*"""
private val MATH_EXPR = Regex("(?<![A-Za-z0-9])$OPERAND(?:$OPERATOR$OPERAND)+(?![A-Za-z0-9])")

/** Math-ish characters that Cambria really has a glyph for (generated from the font file). */
internal fun isMathChar(x: Int): Boolean {
    val inMathBlock = x in 0x00B0..0x00BE || x == 0x00D7 || x == 0x00F7 ||
        x in 0x2070..0x209F || x in 0x2190..0x21FF || x in 0x2200..0x22FF || x in 0x2100..0x218F ||
        x in 0x2030..0x2037 || x in 0x27C0..0x27EF || x in 0x2980..0x2AFF || x in 0x25A0..0x25FF
    if (!inMathBlock) return false
    var k = 0
    while (k < CAMBRIA_RANGES.size) {
        if (x >= CAMBRIA_RANGES[k] && x <= CAMBRIA_RANGES[k + 1]) return true
        k += 2
    }
    return false
}

// Code point ranges (lo, hi, lo, hi, ...) of the math blocks present in cambria.ttf; characters it lacks stay in the Latin font.
private val CAMBRIA_RANGES = intArrayOf(
    0xB0, 0xBE, 0xD7, 0xD7, 0xF7, 0xF7, 0x2030, 0x2030, 0x2070, 0x2070,
    0x2074, 0x208E, 0x2113, 0x2113, 0x2116, 0x2117, 0x2122, 0x2122, 0x2126, 0x2126,
    0x212E, 0x212E, 0x2202, 0x2202, 0x2206, 0x2206, 0x220F, 0x220F, 0x2211, 0x2212,
    0x2215, 0x2215, 0x2219, 0x221A, 0x221E, 0x221E, 0x222B, 0x222B, 0x2248, 0x2248,
    0x2260, 0x2260, 0x2264, 0x2265, 0x25CA, 0x25CA
)
