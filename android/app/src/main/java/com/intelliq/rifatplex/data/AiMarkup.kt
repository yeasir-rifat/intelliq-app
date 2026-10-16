package com.intelliq.rifatplex.data

/**
 * Turns the text Gemini writes (see [GeminiPrompts]) into the app's rich-text HTML subset
 * (<b> <i> <u> <sub> <sup> <br> and <frac n=".." d=".."/>), the same format the editor tools produce.
 *
 * Gemini is told to use <sub>/<sup>/{{frac|n|d}} directly. As a safety net this also understands the things
 * models sometimes write anyway: LaTeX ($...$, \frac, \sqrt, ^, _), x^2 / H_2O, **bold**.
 */

private val TOKEN_RE = Regex(
    """\{\{\s*frac\s*\|([^|]*)\|([^}]*)\}\}|</?(?:b|i|u|sub|sup)\s*>|<br\s*/?>""",
    RegexOption.IGNORE_CASE,
)
private val LATEX_SEG_RE = Regex(
    """[$][$](.+?)[$][$]|[$](.+?)[$]|\\\((.+?)\\\)|\\\[(.+?)\\\]""",
    RegexOption.DOT_MATCHES_ALL,
)
private val NESTED_FRAC_RE = Regex(
    """(\{\{\s*frac\s*\|[^{}]*?)\{\{\s*frac\s*\|([^|{}]*)\|([^{}]*)\}\}""",
    RegexOption.IGNORE_CASE,
)
private val BARE_CMD_RE = Regex("""\\[A-Za-z]""")
private val CARET_RE = Regex("""(?<=[A-Za-z0-9)\]])\^(\{[^}]*\}|[-+\u2212]?[0-9]+|[A-Za-z])""")
private val UNDER_RE = Regex("""(?<=[A-Za-z)])_(\{[^}]*\}|[0-9]+)""")
private val MD_BOLD_RE = Regex("""\*\*(.+?)\*\*""")
private val WORD_RE = Regex("[A-Za-z0-9()]+")

private fun attrEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun escapeText(s: String): String = buildString {
    for (ch in s) when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '\n' -> append("<br>")
        '\r' -> {}
        else -> append(ch)
    }
}

private fun formulaText(t: String): String {
    val sb = StringBuilder()
    var last = 0
    for (m in WORD_RE.findAll(t)) {
        sb.append(escapeText(t.substring(last, m.range.first)))
        sb.append(subscriptFormulaWord(m.value) ?: escapeText(m.value))
        last = m.range.last + 1
    }
    sb.append(escapeText(t.substring(last)))
    return sb.toString()
}

private fun buildInline(s: String, chemAuto: Boolean, allowFrac: Boolean): String {
    val out = StringBuilder()
    val open = ArrayList<String>()

    fun text(t: String) {
        if (t.isEmpty()) return
        out.append(if (chemAuto && "sub" !in open) formulaText(t) else escapeText(t))
    }

    var last = 0
    for (m in TOKEN_RE.findAll(s)) {
        text(s.substring(last, m.range.first))
        last = m.range.last + 1
        val tok = m.value
        when {
            tok.startsWith("{{") -> {
                val n = m.groupValues[1].trim()
                val d = m.groupValues[2].trim()
                if (allowFrac) {
                    out.append("<frac n=\"").append(attrEscape(buildInline(n, chemAuto, false)))
                        .append("\" d=\"").append(attrEscape(buildInline(d, chemAuto, false))).append("\"/>")
                } else {
                    out.append(escapeText("$n/$d"))
                }
            }
            tok.startsWith("<br", ignoreCase = true) -> out.append("<br>")
            tok.startsWith("</") -> {
                val name = tok.removePrefix("</").removeSuffix(">").trim().lowercase()
                val idx = open.lastIndexOf(name)
                if (idx >= 0) {
                    while (open.size > idx) {
                        out.append("</").append(open.removeAt(open.size - 1)).append(">")
                    }
                }
            }
            else -> {
                val name = tok.removePrefix("<").removeSuffix(">").trim().lowercase()
                open.add(name)
                out.append("<").append(name).append(">")
            }
        }
    }
    text(s.substring(last))
    for (n in open.reversed()) out.append("</").append(n).append(">")
    return out.toString()
}

private fun convertLatex(s: String): String {
    if (s.none { it == '$' || it == '\\' }) return s
    val out = StringBuilder()
    var last = 0
    fun bare(gap: String): String = if (BARE_CMD_RE.containsMatchIn(gap)) LatexConverter(gap).all() else gap
    for (m in LATEX_SEG_RE.findAll(s)) {
        out.append(bare(s.substring(last, m.range.first)))
        last = m.range.last + 1
        val inner = (1..4).map { m.groupValues[it] }.firstOrNull { it.isNotEmpty() }
        val dollar = m.value.startsWith('$')
        if (inner == null || (dollar && inner.none { it == '\\' || it == '^' || it == '_' || it == '{' })) {
            out.append(m.value) // e.g. "$5 and $10"
        } else {
            out.append(LatexConverter(inner.trim()).all())
        }
    }
    out.append(bare(s.substring(last)))
    return out.toString()
}

/** The editor has no fraction inside a fraction; an inner one is written as (a/b) instead. */
private fun flattenNestedFractions(s: String): String {
    var r = s
    repeat(4) {
        val n = NESTED_FRAC_RE.replace(r) { it.groupValues[1] + "(" + it.groupValues[2] + "/" + it.groupValues[3] + ")" }
        if (n == r) return r
        r = n
    }
    return r
}

private fun normalizePlainMath(s: String): String {
    var r = s
    if ('^' in r) r = CARET_RE.replace(r) { "<sup>" + it.groupValues[1].removeSurrounding("{", "}") + "</sup>" }
    if ('_' in r) r = UNDER_RE.replace(r) { "<sub>" + it.groupValues[1].removeSurrounding("{", "}") + "</sub>" }
    if ("**" in r) r = MD_BOLD_RE.replace(r) { "<b>" + it.groupValues[1] + "</b>" }
    return r
}

/** Gemini text -> app rich-text HTML subset. [chemAuto] additionally subscripts plain formulas such as H2O. */
fun aiMarkupToHtml(raw: String, chemAuto: Boolean): String {
    val s = flattenNestedFractions(normalizePlainMath(convertLatex(raw.trim())))
    return buildInline(s, chemAuto, true)
}

/** Same rule as the website's computeAutoLayout. */
fun autoLayout(q: Question): String {
    if (q.options.any { "<fig" in it.text }) return "vertical" // pictures in options need room
    val lens = q.options.map { plainText(it.text).trim().length }
    val max = lens.maxOrNull() ?: 0
    val sum = lens.sum()
    return when {
        sum <= 0 -> "vertical"
        max <= 6 && sum <= 20 -> "horizontal"
        max <= 18 && sum <= 56 -> "grid"
        else -> "vertical"
    }
}

/** "bn" when the generated text contains Bangla, "en" otherwise, null when there is too little text to tell. */
fun detectLanguage(questions: List<Question>): String? {
    val all = questions.joinToString(" ") { q ->
        plainText(q.text) + " " + q.options.joinToString(" ") { plainText(it.text) }
    }
    val bn = all.count { it in '\u0980'..'\u09FF' }
    val en = all.count { it in 'A'..'Z' || it in 'a'..'z' }
    if (bn + en < 3) return null
    return if (bn > 0) "bn" else "en"
}

/* ───────────── LaTeX safety net ───────────── */

private val LATEX_SYMBOLS = mapOf(
    "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "cdot" to "·", "le" to "≤", "leq" to "≤",
    "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠", "approx" to "≈", "propto" to "∝", "infty" to "∞",
    "to" to "→", "rightarrow" to "→", "Rightarrow" to "⇒", "leftarrow" to "←", "leftrightarrow" to "↔",
    "rightleftharpoons" to "⇌", "uparrow" to "↑", "downarrow" to "↓", "circ" to "°", "degree" to "°",
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "theta" to "θ",
    "lambda" to "λ", "mu" to "μ", "pi" to "π", "sigma" to "σ", "omega" to "ω", "phi" to "φ", "rho" to "ρ",
    "tau" to "τ", "eta" to "η", "nu" to "ν", "Delta" to "Δ", "Omega" to "Ω", "Sigma" to "Σ", "Pi" to "Π",
    "Phi" to "Φ", "Gamma" to "Γ", "Theta" to "Θ", "Lambda" to "Λ", "partial" to "∂", "nabla" to "∇",
    "sum" to "∑", "int" to "∫", "ldots" to "…", "dots" to "…", "cdots" to "…", "angle" to "∠", "perp" to "⊥",
    "parallel" to "∥", "in" to "∈", "therefore" to "∴", "because" to "∵",
)
private val LATEX_FUNCS = setOf("sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lim", "exp", "min", "max")

private class LatexConverter(private val s: String) {
    private var i = 0

    fun all(): String {
        val sb = StringBuilder()
        while (i < s.length) sb.append(atom())
        return sb.toString()
    }

    private fun braceInner(): String {
        var depth = 0
        var j = i
        while (j < s.length) {
            val ch = s[j]
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == '{') depth++
            else if (ch == '}') {
                depth--
                if (depth == 0) break
            }
            j++
        }
        val start = minOf(i + 1, s.length)
        val end = maxOf(start, minOf(j, s.length))
        i = minOf(j + 1, s.length)
        return s.substring(start, end)
    }

    private fun skipSpaces() {
        while (i < s.length && s[i] == ' ') i++
    }

    private fun group(): String = LatexConverter(braceInner()).all()

    private fun rawGroup(): String {
        skipSpaces()
        return if (i < s.length && s[i] == '{') braceInner() else ""
    }

    private fun arg(): String {
        skipSpaces()
        if (i >= s.length) return ""
        return if (s[i] == '{') group() else atom()
    }

    private fun atom(): String {
        val ch = s[i]
        return when (ch) {
            '{' -> group()
            '^' -> {
                i++
                "<sup>" + arg() + "</sup>"
            }
            '_' -> {
                i++
                "<sub>" + arg() + "</sub>"
            }
            '\\' -> command()
            else -> {
                i++
                ch.toString()
            }
        }
    }

    private fun command(): String {
        i++ // the backslash
        if (i >= s.length) return ""
        val c = s[i]
        if (!c.isLetter()) {
            i++
            return when (c) {
                ',', ';', ':', ' ', '\\' -> " "
                '!' -> ""
                else -> c.toString() // \% \& \_ \{ \} \# \$
            }
        }
        val st = i
        while (i < s.length && s[i].isLetter()) i++
        val name = s.substring(st, i)
        return when (name) {
            "frac", "dfrac", "tfrac" -> {
                val a = arg()
                val b = arg()
                "{{frac|$a|$b}}"
            }
            "sqrt" -> {
                skipSpaces()
                var index = ""
                if (i < s.length && s[i] == '[') {
                    val e = s.indexOf(']', i)
                    if (e > 0) {
                        index = s.substring(i + 1, e)
                        i = e + 1
                    }
                }
                val inner = arg()
                val body = if (inner.length == 1 && inner[0].isLetterOrDigit()) inner else "($inner)"
                val sign = when (index) {
                    "", "2" -> "√"
                    "3" -> "∛"
                    else -> "<sup>$index</sup>√"
                }
                sign + body
            }
            "text", "mathrm", "textrm", "mathit", "textit", "mathsf", "operatorname", "ce" -> rawGroup()
            "mathbf", "textbf", "boldsymbol" -> "<b>" + arg() + "</b>"
            "left", "right" -> {
                if (i < s.length && s[i] == '.') i++
                ""
            }
            "displaystyle", "limits", "big", "Big", "bigl", "bigr" -> ""
            "quad", "qquad" -> " "
            "overline", "vec", "hat", "bar" -> arg()
            else -> {
                val out = LATEX_SYMBOLS[name] ?: name
                if (name !in LATEX_FUNCS && i < s.length && s[i] == ' ') i++
                out
            }
        }
    }
}
