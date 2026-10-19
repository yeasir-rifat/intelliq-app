#!/usr/bin/env bash
# Downloads the Google Fonts the app uses and bundles them into the app.
#   App UI:  English = Lato, Bangla = Noto Serif Bengali
#   PDF:     English = Times New Roman | Georgia | Cambria (committed in res/font)
#            Bangla  = Noto Serif Bengali | Nikosh | Kalpurush | SolaimanLipi | AdorshoLipi
#            formulas = Cambria, chosen automatically
set -uo pipefail
D="$(cd "$(dirname "$0")" && pwd)/../app/src/main/res/font"
mkdir -p "$D"
B="https://github.com/google/fonts/raw/main/ofl"

get() { # url file
  if [ ! -s "$D/$2" ]; then curl -fsSL --retry 3 "$1" -o "$D/$2" || rm -f "$D/$2"; fi
}
need() { # file  (must exist or the build cannot reference it)
  [ -s "$D/$1" ] || { echo "ERROR: could not fetch $1" >&2; exit 1; }
}
fallback() { # file fallbackFile
  [ -s "$D/$1" ] || cp "$D/$2" "$D/$1"
}

# UI fonts (required)
get "$B/lato/Lato-Regular.ttf"       lato_regular.ttf
get "$B/lato/Lato-Bold.ttf"          lato_bold.ttf
get "$B/lato/Lato-Italic.ttf"        lato_italic.ttf
get "$B/lato/Lato-BoldItalic.ttf"    lato_bolditalic.ttf
for f in lato_regular lato_bold lato_italic lato_bolditalic; do need "$f.ttf"; done

get "$B/poppins/Poppins-Regular.ttf"   poppins_regular.ttf
get "$B/poppins/Poppins-Medium.ttf"    poppins_medium.ttf
get "$B/poppins/Poppins-SemiBold.ttf"  poppins_semibold.ttf
get "$B/poppins/Poppins-Bold.ttf"      poppins_bold.ttf
get "$B/hindsiliguri/HindSiliguri-Regular.ttf"  hind_siliguri_regular.ttf
get "$B/hindsiliguri/HindSiliguri-Medium.ttf"   hind_siliguri_medium.ttf
get "$B/hindsiliguri/HindSiliguri-SemiBold.ttf" hind_siliguri_semibold.ttf
get "$B/hindsiliguri/HindSiliguri-Bold.ttf"     hind_siliguri_bold.ttf
for f in poppins_regular poppins_medium poppins_semibold poppins_bold \
         hind_siliguri_regular hind_siliguri_medium hind_siliguri_semibold hind_siliguri_bold; do
  need "$f.ttf"
done

# Bengali serif (variable font: wght 100-900) - fall back to Hind Siliguri if the file name ever changes
get "$B/notoserifbengali/NotoSerifBengali%5Bwdth%2Cwght%5D.ttf" noto_serif_bengali.ttf
fallback noto_serif_bengali.ttf hind_siliguri_regular.ttf

# Kalpurush and SolaimanLipi (Settings -> Bangla Font) are committed in res/font, nothing to fetch.

# Paper font: Carlito ~ Calibri
get "$B/carlito/Carlito-Regular.ttf"     carlito_regular.ttf
get "$B/carlito/Carlito-Bold.ttf"        carlito_bold.ttf
get "$B/carlito/Carlito-Italic.ttf"      carlito_italic.ttf
get "$B/carlito/Carlito-BoldItalic.ttf"  carlito_bolditalic.ttf
for f in carlito; do
  fallback "${f}_regular.ttf" hind_siliguri_regular.ttf
  fallback "${f}_bold.ttf" hind_siliguri_bold.ttf
  fallback "${f}_italic.ttf" "${f}_regular.ttf"
  fallback "${f}_bolditalic.ttf" "${f}_bold.ttf"
done

# Times New Roman, Georgia and Cambria (Settings -> English Font) are committed in res/font (times_new_roman.ttf,
# georgia_*.ttf, cambria.ttf), nothing to fetch. Times New Roman and Cambria have no bold/italic file: it is synthesised.

# Nikosh and AdorshoLipi (Settings -> Bangla Font) are committed in res/font (nikosh.ttf, adorsho_lipi.ttf), so the
# download below is skipped. It only runs if those files are ever removed; falls back to Kalpurush if it fails.
G="https://github.com/hmoazzem/bangla-fonts/raw/master"
get "$G/Nikosh.ttf"                    nikosh.ttf
get "$G/AdorshoLipi_20-07-2007.ttf"    adorsho_lipi.ttf
fallback nikosh.ttf kalpurush.ttf
fallback adorsho_lipi.ttf kalpurush.ttf

ls -la "$D"
