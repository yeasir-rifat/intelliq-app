package com.intelliq.rifatplex.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.intelliq.rifatplex.ui.theme.Caladea
import com.intelliq.rifatplex.ui.theme.Carlito
import com.intelliq.rifatplex.ui.theme.HindSiliguri
import com.intelliq.rifatplex.ui.theme.Kalpurush
import com.intelliq.rifatplex.ui.theme.SolaimanLipi
import com.intelliq.rifatplex.ui.theme.NotoSerifBengali
import com.intelliq.rifatplex.ui.theme.Tinos

/**
 * Fonts of the question paper / PDF, chosen per character:
 *  - Bangla letters, digits, danda  -> [bangla]  (Noto Serif Bengali: real Regular and Bold)
 *  - English letters and digits     -> [latin]   (Times New Roman look: Tinos)
 *  - formulas: fractions, subscripts, superscripts and math symbols -> [math] (Cambria look: Caladea)
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
 * Fixed fonts of the OMR sheet and the answer sheet ("উত্তরমালা"): Bangla = SolaimanLipi, English = Times New Roman look
 * (Tinos). Never chosen from Settings (see [com.intelliq.rifatplex.data.SheetStyle]). The few symbols these sheets use
 * (the arrow of "১ → খ") come from Tinos too, so no third font is involved.
 */
val sheetFonts: PaperFonts = PaperFonts(latin = Tinos, bangla = SolaimanLipi, math = Tinos, base = Tinos)

/** [name] = Settings → Font Family. Default: Times New Roman look for English, Noto Serif Bengali for Bangla, Cambria look for formulas. */
fun paperFonts(name: String): PaperFonts = when (name) {
    // every entry of Settings -> Font Family is its own font (Bangla + English + formulas)
    "Kalpurush" -> PaperFonts(latin = Kalpurush, bangla = Kalpurush, math = Caladea, base = Kalpurush)
    "Noto Serif Bengali" ->
        PaperFonts(latin = NotoSerifBengali, bangla = NotoSerifBengali, math = Caladea, base = NotoSerifBengali)
    "SolaimanLipi" -> PaperFonts(latin = SolaimanLipi, bangla = SolaimanLipi, math = Caladea, base = SolaimanLipi)
    "Hind Siliguri" -> PaperFonts(latin = HindSiliguri, bangla = HindSiliguri, math = Caladea, base = HindSiliguri)
    "Calibri" -> PaperFonts(latin = Carlito, bangla = NotoSerifBengali, math = Caladea, base = Carlito)
    "Cambria Math" -> PaperFonts(latin = Caladea, bangla = NotoSerifBengali, math = Caladea, base = Caladea)
    else -> PaperFonts(latin = Tinos, bangla = NotoSerifBengali, math = Caladea, base = Tinos)
}

/** Math-ish characters that the Cambria stand-in (Caladea) really has a glyph for (generated from the font file). */
internal fun isMathChar(x: Int): Boolean {
    val inMathBlock = x in 0x00B0..0x00BE || x == 0x00D7 || x == 0x00F7 ||
        x in 0x2070..0x209F || x in 0x2190..0x21FF || x in 0x2200..0x22FF || x in 0x2100..0x218F ||
        x in 0x2030..0x2037 || x in 0x27C0..0x27EF || x in 0x2980..0x2AFF || x in 0x25A0..0x25FF
    if (!inMathBlock) return false
    var k = 0
    while (k < CALADEA_RANGES.size) {
        if (x >= CALADEA_RANGES[k] && x <= CALADEA_RANGES[k + 1]) return true
        k += 2
    }
    return false
}

// Code point ranges (lo, hi, lo, hi, ...) present in Caladea-Regular.ttf; characters it lacks stay in the Latin font.
private val CALADEA_RANGES = intArrayOf(
    0xA0, 0x161, 0x164, 0x17F, 0x192, 0x192, 0x1FA, 0x1FF, 0x218, 0x21B, 0x237, 0x237, 0x2C6, 0x2C7, 0x2C9, 0x2C9,
    0x2D8, 0x2DD, 0x300, 0x304, 0x306, 0x308, 0x30A, 0x30C, 0x326, 0x328, 0x1E80, 0x1E85, 0x1EBC, 0x1EBD, 0x1EF2,
    0x1EF3, 0x1EF8, 0x1EF9, 0x2013, 0x2015, 0x2017, 0x201E, 0x2020, 0x2022, 0x2026, 0x2026, 0x2030, 0x2030,
    0x2032, 0x2033, 0x2039, 0x203A, 0x203C, 0x203C, 0x203E, 0x203E, 0x2044, 0x2044, 0x2074, 0x2074, 0x207F,
    0x207F, 0x20A3, 0x20A4, 0x20A7, 0x20A7, 0x20AC, 0x20AC, 0x2105, 0x2105, 0x2113, 0x2113, 0x2116, 0x2116,
    0x2122, 0x2122, 0x2126, 0x2126, 0x212E, 0x212E, 0x215B, 0x215E, 0x2190, 0x2195, 0x21A8, 0x21A8, 0x2202,
    0x2202, 0x2206, 0x2206, 0x220F, 0x220F, 0x2211, 0x2212, 0x2215, 0x2215, 0x2219, 0x221A, 0x221E, 0x221F,
    0x2229, 0x2229, 0x222B, 0x222B, 0x2248, 0x2248, 0x2260, 0x2261, 0x2264, 0x2265, 0x2302, 0x2302, 0x2310,
    0x2310, 0x2320, 0x2321, 0xF6C3, 0xF6C3, 0xFB01, 0xFB02
)
