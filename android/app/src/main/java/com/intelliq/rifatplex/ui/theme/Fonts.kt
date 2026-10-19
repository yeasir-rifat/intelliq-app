package com.intelliq.rifatplex.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.intelliq.rifatplex.R

// The .ttf files are fetched by scripts/fetch_fonts.sh at build time (Kalpurush and SolaimanLipi are committed in res/font).

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
 * Bangla UI font: SolaimanLipi. Only a Regular file exists, so Normal and Medium use it as it is and SemiBold / Bold
 * get the engine's synthesised bold (headings, buttons). This is a separate FontFamily instance from [SolaimanLipi]
 * (the paper font) on purpose: the UI text wrapper recognises Bangla UI text by identity and puts Lato under the
 * English letters and digits, which must never happen to paper text.
 */
val BanglaUi = FontFamily(
    Font(R.font.solaiman_lipi, FontWeight.Normal),
    Font(R.font.solaiman_lipi, FontWeight.Medium),
)

/** Noto Serif Bengali, kept only as a paper font choice (Settings > Font Family). One variable file, real weights. */
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

// Times New Roman, Calibri and Cambria cannot be shipped (Microsoft licence). Tinos, Carlito and Caladea are the
// metric-compatible open fonts made for exactly this: same widths, same line breaks, same page count.
val Tinos = FontFamily(
    Font(R.font.tinos_regular, FontWeight.Normal),
    Font(R.font.tinos_bold, FontWeight.Bold),
    Font(R.font.tinos_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.tinos_bolditalic, FontWeight.Bold, FontStyle.Italic),
)
val Carlito = FontFamily(
    Font(R.font.carlito_regular, FontWeight.Normal),
    Font(R.font.carlito_bold, FontWeight.Bold),
    Font(R.font.carlito_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.carlito_bolditalic, FontWeight.Bold, FontStyle.Italic),
)
val Caladea = FontFamily(
    Font(R.font.caladea_regular, FontWeight.Normal),
    Font(R.font.caladea_bold, FontWeight.Bold),
    Font(R.font.caladea_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.caladea_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

/** Settings → Font Family names (identical to the website). */
val DOC_FONT_NAMES = listOf(
    "Times New Roman", "Kalpurush", "Cambria Math", "Calibri",
    "Noto Serif Bengali", "Hind Siliguri", "SolaimanLipi",
)
