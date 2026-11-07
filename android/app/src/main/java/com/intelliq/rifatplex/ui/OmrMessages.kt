package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.data.OmrFlag
import com.intelliq.rifatplex.data.OmrSheetKind
import com.intelliq.rifatplex.data.OmrSizeMismatch
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.omrSheetCount

/*
 * Texts of the OMR sheet size feature that are built from numbers. Plain Kotlin (no Compose state), so the unit tests can call them.
 */

/**
 * The text of one flag under a sheet. [OmrFlag.KEY_SIZE_MISMATCH] names both sizes ("{sheet}" = the photographed sheet,
 * "{key}" = the saved answer key) in the digits of the language; every other flag is its plain text.
 */
internal fun evalFlagText(flag: OmrFlag, sizes: OmrSizeMismatch?, lang: Lang): String {
    val text = trKey("eval.flag." + flag.name, lang)
    if (flag != OmrFlag.KEY_SIZE_MISMATCH) return text
    val bn = lang == Lang.BN
    return text
        .replace("{sheet}", sizes?.let { formatNumber(it.sheetRows, bn) } ?: "?")
        .replace("{key}", sizes?.let { formatNumber(it.keyRows, bn) } ?: "?")
}

/**
 * The size hint for [mcqCount] MCQs, e.g. "45 MCQs: a 50-question sheet is enough"; null for no MCQ. Only a hint: the size is never changed
 * by it. More than 200 MCQs name the number of 200-answer sheets.
 */
internal fun omrSizeHint(mcqCount: Int, lang: Lang): String? {
    val kind = OmrSheetKind.suggest(mcqCount) ?: return null
    val bn = lang == Lang.BN
    val n = formatNumber(mcqCount, bn)
    return if (mcqCount <= kind.rows) {
        trKey("preview.omrSizeHint", lang).replace("{n}", n).replace("{k}", formatNumber(kind.rows, bn))
    } else {
        trKey("preview.omrSizeHintMany", lang).replace("{n}", n)
            .replace("{k}", formatNumber(kind.rows, bn)).replace("{s}", formatNumber(omrSheetCount(mcqCount, kind.rows), bn))
    }
}
