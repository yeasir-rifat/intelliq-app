package com.intelliq.rifatplex.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.intelliq.rifatplex.R

// The .ttf files are fetched by scripts/fetch_fonts.sh at build time (Kalpurush, SolaimanLipi, Nikosh, AdorshoLipi, Times New Roman, Georgia and Cambria are committed in res/font).

/* ───────────── App UI fonts ───────────── */

/** English UI font. Lato ships Regular and Bold, so Medium uses Regular and SemiBold uses Bold. */
val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_regular, FontWeight.Medium),
    Font(R.font.lato_bold, FontWeight.SemiBold),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lato_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

/**
 * Bangla UI font: Noto Serif Bengali (one variable file, real Regular / Medium / SemiBold / Bold). This is a separate
 * FontFamily instance from [NotoSerifBengali] (the paper font choice) on purpose: the UI text wrapper recognises Bangla
 * UI text by identity and puts Lato under the English letters and digits, which must never happen to paper text.
 */
@OptIn(ExperimentalTextApi::class)
val BanglaUi = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.noto_serif_bengali, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

/** Noto Serif Bengali as a paper font choice (Settings > Bangla Font). One variable file, real weights. */
@OptIn(ExperimentalTextApi::class)
val NotoSerifBengali = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.noto_serif_bengali, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

/** Paper font "SolaimanLipi" (Bangla + Latin, committed in res/font). Only Regular exists, bold is synthesised. */
val SolaimanLipi = FontFamily(Font(R.font.solaiman_lipi, FontWeight.Normal))

/* ───────────── Other bundled fonts ───────────── */

// Brand wordmark ("IntelliQ" in the header) only.
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold),
)

/* ───────────── PDF / paper fonts ───────────── */

/** Bangla paper font. Only Regular exists, so bold and italic are synthesised by the text engine. */
val Kalpurush = FontFamily(Font(R.font.kalpurush, FontWeight.Normal))

// Times New Roman, Georgia and Cambria (res/font, supplied by the app owner) are defined further down.
val Carlito = FontFamily(
    Font(R.font.carlito_regular, FontWeight.Normal),
    Font(R.font.carlito_bold, FontWeight.Bold),
    Font(R.font.carlito_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.carlito_bolditalic, FontWeight.Bold, FontStyle.Italic),
)
/**
 * The real Microsoft fonts (supplied by the app owner). Times New Roman and Cambria came as Regular only, so their bold
 * and italic are synthesised by the text engine. Georgia has all four styles.
 */
val TimesNewRoman = FontFamily(Font(R.font.times_new_roman, FontWeight.Normal))
val Georgia = FontFamily(
    Font(R.font.georgia_regular, FontWeight.Normal),
    Font(R.font.georgia_bold, FontWeight.Bold),
    Font(R.font.georgia_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.georgia_bolditalic, FontWeight.Bold, FontStyle.Italic),
)
val Cambria = FontFamily(Font(R.font.cambria, FontWeight.Normal))

/** Bangla paper fonts from Omicron Lab / Nikosh (fetched by scripts/fetch_fonts.sh). Regular only, bold is synthesised. */
val Nikosh = FontFamily(Font(R.font.nikosh, FontWeight.Normal))
val AdorshoLipi = FontFamily(Font(R.font.adorsho_lipi, FontWeight.Normal))

/** Settings: English font choices (letters and digits written in English; formulas always use Cambria). */
val DOC_FONTS_EN = listOf("Times New Roman", "Georgia", "Cambria")

/** Settings: Bangla font choices. */
val DOC_FONTS_BN = listOf("Noto Serif Bengali", "Nikosh", "Kalpurush", "SolaimanLipi", "AdorshoLipi")
