#!/usr/bin/env bash
# Downloads the Google Fonts the website uses (+ metric-compatible stand-ins for
# Times New Roman / Calibri / Cambria) and bundles them into the app.
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

# Bengali serif (variable font) - fall back to Hind Siliguri if the file name ever changes
get "$B/notoserifbengali/NotoSerifBengali%5Bwdth%2Cwght%5D.ttf" noto_serif_bengali.ttf
fallback noto_serif_bengali.ttf hind_siliguri_regular.ttf

# Paper fonts: Tinos ~ Times New Roman, Carlito ~ Calibri, Caladea ~ Cambria
get "$B/tinos/Tinos-Regular.ttf"     tinos_regular.ttf
get "$B/tinos/Tinos-Bold.ttf"        tinos_bold.ttf
get "$B/tinos/Tinos-Italic.ttf"      tinos_italic.ttf
get "$B/tinos/Tinos-BoldItalic.ttf"  tinos_bolditalic.ttf
get "$B/carlito/Carlito-Regular.ttf"     carlito_regular.ttf
get "$B/carlito/Carlito-Bold.ttf"        carlito_bold.ttf
get "$B/carlito/Carlito-Italic.ttf"      carlito_italic.ttf
get "$B/carlito/Carlito-BoldItalic.ttf"  carlito_bolditalic.ttf
get "$B/caladea/Caladea-Regular.ttf"     caladea_regular.ttf
get "$B/caladea/Caladea-Bold.ttf"        caladea_bold.ttf
get "$B/caladea/Caladea-Italic.ttf"      caladea_italic.ttf
get "$B/caladea/Caladea-BoldItalic.ttf"  caladea_bolditalic.ttf
for f in tinos carlito caladea; do
  fallback "${f}_regular.ttf" hind_siliguri_regular.ttf
  fallback "${f}_bold.ttf" hind_siliguri_bold.ttf
  fallback "${f}_italic.ttf" "${f}_regular.ttf"
  fallback "${f}_bolditalic.ttf" "${f}_bold.ttf"
done
ls -la "$D"
