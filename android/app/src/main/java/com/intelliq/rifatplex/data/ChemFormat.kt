package com.intelliq.rifatplex.data

/**
 * "Chemistry auto-format": while typing a formula such as H2O, CO2, Ca(OH)2 or Al2(SO4)3,
 * a digit typed right after an element symbol / closing bracket (or after an already-subscripted digit)
 * is turned into a subscript automatically.
 *
 * The check is deliberately strict: the whole word the digit belongs to must be a valid formula made of
 * real element symbols, so ordinary text like "Q1", "Class2" or "x2" is left alone.
 */
private val ELEMENTS = (
    "H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn Ga Ge As Se Br Kr " +
        "Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu " +
        "Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr " +
        "Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl Mc Lv Ts Og"
    ).split(' ').sortedByDescending { it.length }

private val FORMULA_RE = Regex("^[0-9]*(?:(?:" + ELEMENTS.joinToString("|") + "|\\))[0-9]*|\\()+$")

private fun isWordChar(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '(' || c == ')'

/**
 * Should the digit that was just typed at [pos] of [text] become a subscript?
 * [oldStyles] are the styles of the text *before* the digit was inserted (the characters left of [pos] are unchanged).
 */
fun shouldAutoSubscript(text: String, pos: Int, oldStyles: List<CharStyle>): Boolean {
    if (pos <= 0 || pos >= text.length || text[pos] !in '0'..'9') return false
    val prev = text[pos - 1]
    val prevOk = when {
        prev in 'A'..'Z' || prev in 'a'..'z' || prev == ')' -> true
        prev in '0'..'9' -> oldStyles.getOrNull(pos - 1)?.sub == true // continuing a subscript run: C12, H22
        else -> false
    }
    if (!prevOk) return false
    // the word must not continue to the right of the new digit with unrelated letters being typed in the middle
    var s = pos
    while (s > 0 && isWordChar(text[s - 1])) s--
    var e = pos + 1
    while (e < text.length && isWordChar(text[e])) e++
    return FORMULA_RE.matches(text.substring(s, e))
}

/**
 * For AI generated text: "H2SO4" -> "H<sub>2</sub>SO<sub>4</sub>", "Ca(OH)2" -> "Ca(OH)<sub>2</sub>", "2H2O" -> "2H<sub>2</sub>O".
 * Returns null when [word] is not a valid chemical formula or has no digit that needs a subscript.
 * A leading number is a coefficient and is never subscripted.
 */
fun subscriptFormulaWord(word: String): String? {
    if (word.none { it in '0'..'9' } || !FORMULA_RE.matches(word)) return null
    val sb = StringBuilder()
    var changed = false
    var i = 0
    while (i < word.length) {
        val ch = word[i]
        val prevOk = i > 0 && (word[i - 1].isLetter() || word[i - 1] == ')')
        if (ch in '0'..'9' && prevOk) {
            var j = i
            while (j < word.length && word[j] in '0'..'9') j++
            sb.append("<sub>").append(word, i, j).append("</sub>")
            changed = true
            i = j
        } else {
            sb.append(ch)
            i++
        }
    }
    return if (changed) sb.toString() else null
}
