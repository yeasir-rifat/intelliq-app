package com.intelliq.rifatplex.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.intelliq.rifatplex.R

// Same fonts as the website. The .ttf files are fetched by scripts/fetch_fonts.sh at build time.
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

val NotoSerifBengali = FontFamily(Font(R.font.noto_serif_bengali))

// Paper (document) fonts - metric-compatible stand-ins for the website's system fonts.
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

/** Settings → Font Family names (identical to the website) mapped to bundled fonts. */
val DOC_FONT_NAMES = listOf(
    "Calibri", "Times New Roman", "Cambria Math", "Kalpurush",
    "Noto Serif Bengali", "Hind Siliguri", "Shonar Bangla", "Nikosh BAN",
)

fun docFontFamily(name: String): FontFamily = when (name) {
    "Calibri" -> Carlito
    "Cambria Math" -> Caladea
    "Hind Siliguri" -> HindSiliguri
    "Kalpurush", "Noto Serif Bengali", "Shonar Bangla", "Nikosh BAN" -> NotoSerifBengali
    else -> Tinos
}
