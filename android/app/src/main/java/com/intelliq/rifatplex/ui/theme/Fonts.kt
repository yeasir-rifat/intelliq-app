package com.intelliq.rifatplex.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.intelliq.rifatplex.R

// The .ttf files are fetched by scripts/fetch_fonts.sh at build time (Kalpurush is committed in res/font).

/* ───────────── App UI fonts ───────────── */

// English UI = Poppins (website --font-ui-en). Lato was removed in Parity Step 2.

/** Bangla UI font. One variable font file, every weight is a real weight (not a faked bold). */
@OptIn(ExperimentalTextApi::class)
val NotoSerifBengali = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.noto_serif_bengali, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

/* ───────────── Other bundled fonts ───────────── */

/** English UI font (website --font-ui-en) and the brand wordmark. */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Bangla body font (website --font-ui-bn). Used by Paper fonts; UI chrome uses NotoSerifBengali. */
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
    "Noto Serif Bengali", "Hind Siliguri", "Shonar Bangla", "Nikosh BAN",
)
