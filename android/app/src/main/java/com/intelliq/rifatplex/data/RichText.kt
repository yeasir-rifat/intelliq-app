package com.intelliq.rifatplex.data

/**
 * Rich text is stored as a small HTML subset (same idea as the website's contenteditable HTML):
 * <b> <i> <u> <sub> <sup> <span style="color:#RRGGBB"> and <br>.
 * Internally every character carries its own [CharStyle], which makes editing trivial and exact.
 */
enum class Fmt { Bold, Italic, Underline, Sub, Sup }

data class CharStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val sub: Boolean = false,
    val sup: Boolean = false,
    val color: Int = 0, // 0 = default colour, otherwise opaque ARGB
    // Stacked fraction payload (HTML-subset strings). Only set on the single FRAC_CHAR "atom" character.
    val fracN: String = "",
    val fracD: String = "",
    // Picture payload. Only set on the single FIG_CHAR "atom" character (see Figure.kt).
    val fig: FigData? = null,
) {
    val isFrac: Boolean get() = fracN.isNotEmpty() || fracD.isNotEmpty()
    val isFig: Boolean get() = fig != null

    /** Same style without the fraction / picture payload (so typed text next to an atom never becomes one). */
    fun noFrac(): CharStyle = if (isFrac || fig != null) copy(fracN = "", fracD = "", fig = null) else this

    fun has(f: Fmt): Boolean = when (f) {
        Fmt.Bold -> bold
        Fmt.Italic -> italic
        Fmt.Underline -> underline
        Fmt.Sub -> sub
        Fmt.Sup -> sup
    }

    fun with(f: Fmt, on: Boolean): CharStyle = when (f) {
        Fmt.Bold -> copy(bold = on)
        Fmt.Italic -> copy(italic = on)
        Fmt.Underline -> copy(underline = on)
        Fmt.Sub -> copy(sub = on, sup = if (on) false else sup)
        Fmt.Sup -> copy(sup = on, sub = if (on) false else sub)
    }
}

/** One stacked fraction (numerator over denominator) occupies exactly one of these characters in the text. */
const val FRAC_CHAR = '\uFFFC'

/** One picture block occupies exactly one of these characters in the text (private-use code point). */
const val FIG_CHAR = '\uE0F1'

class Rich(val text: String, val styles: List<CharStyle>) {
    fun isAtom(i: Int): Boolean = text[i] == FRAC_CHAR && styles.getOrNull(i)?.isFrac == true
    fun atomIndices(): List<Int> = text.indices.filter { isAtom(it) }
    fun isFig(i: Int): Boolean = text[i] == FIG_CHAR && styles.getOrNull(i)?.isFig == true
    fun hasFig(): Boolean = FIG_CHAR in text && text.indices.any { isFig(it) }
}

private fun attrValue(attrs: String, name: String): String {
    val m = Regex("(?:^|\\s)" + name + "\\s*=\\s*\"([^\"]*)\"").find(attrs) ?: return ""
    return m.groupValues[1].replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
}

private fun attrEsc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun figFromAttrs(attrs: String): FigData? {
    val urls = attrValue(attrs, "d").split('|').filter { it.startsWith("data:image/") }
    if (urls.isEmpty()) return null
    val ratios = attrValue(attrs, "r").split(',')
    val pics = urls.take(MAX_FIG_PICS).mapIndexed { i, u ->
        val wh = ratios.getOrNull(i)?.split('/')
        Pic(u, wh?.getOrNull(0)?.toIntOrNull() ?: 0, wh?.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    val size = attrValue(attrs, "s").let { if (it == "S" || it == "L") it else "M" }
    val align = attrValue(attrs, "a").let { if (it == "l" || it == "r") it else "c" }
    return FigData(pics, size, align)
}

private val ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
)

private val TAG_RE = Regex("^(/?)([a-zA-Z][a-zA-Z0-9]*)(\\s[^>]*)?/?$")
private val COLOR_RE = Regex("(?:^|[\\s;\"'])color\\s*[:=]\\s*[\"']?\\s*(#[0-9a-fA-F]{3,8}|rgba?\\([^)]*\\)|[a-zA-Z]+)")

private fun parseColor(token: String): Int {
    val t = token.trim()
    return try {
        if (t.startsWith("rgb")) {
            val n = t.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat().toInt().coerceIn(0, 255) }
            (0xFF shl 24) or (n[0] shl 16) or (n[1] shl 8) or n[2]
        } else {
            val c = android.graphics.Color.parseColor(t)
            (0xFF shl 24) or (c and 0xFFFFFF)
        }
    } catch (e: Exception) {
        0
    }
}

private fun applyTag(name: String, attrs: String, cur: CharStyle): CharStyle {
    var s = cur
    when (name) {
        "b", "strong" -> s = s.copy(bold = true)
        "i", "em" -> s = s.copy(italic = true)
        "u" -> s = s.copy(underline = true)
        "sub" -> s = s.with(Fmt.Sub, true)
        "sup" -> s = s.with(Fmt.Sup, true)
        "span", "font" -> {
            val a = attrs.lowercase()
            COLOR_RE.find(attrs)?.let { m ->
                val col = parseColor(m.groupValues[1])
                if (col != 0) s = s.copy(color = col)
            }
            if ("font-weight" in a && ("bold" in a || "700" in a || "600" in a)) s = s.copy(bold = true)
            if ("font-style" in a && "italic" in a) s = s.copy(italic = true)
            if ("text-decoration" in a && "underline" in a) s = s.copy(underline = true)
            if ("vertical-align" in a && "sub" in a) s = s.with(Fmt.Sub, true)
            if ("vertical-align" in a && "super" in a) s = s.with(Fmt.Sup, true)
        }
    }
    return s
}

fun parseHtml(html: String): Rich {
    if (html.isEmpty()) return Rich("", emptyList())
    val sb = StringBuilder()
    val styles = ArrayList<CharStyle>()
    var cur = CharStyle()
    val stack = ArrayList<Pair<String, CharStyle>>() // tag name + style before it was opened

    fun emit(ch: Char) {
        sb.append(ch)
        styles.add(cur)
    }

    var i = 0
    while (i < html.length) {
        val ch = html[i]
        when {
            ch == '<' -> {
                val end = html.indexOf('>', i)
                val m = if (end > i) TAG_RE.matchEntire(html.substring(i + 1, end).trim()) else null
                if (m == null) {
                    emit('<'); i++
                } else {
                    val closing = m.groupValues[1] == "/"
                    val name = m.groupValues[2].lowercase()
                    val attrs = m.groupValues[3]
                    if (closing) {
                        val idx = stack.indexOfLast { it.first == name }
                        if (idx >= 0) {
                            cur = stack[idx].second
                            while (stack.size > idx) stack.removeAt(stack.size - 1)
                        }
                    } else when (name) {
                        "frac" -> {
                            sb.append(FRAC_CHAR)
                            styles.add(CharStyle(fracN = attrValue(attrs, "n"), fracD = attrValue(attrs, "d")))
                        }
                        "fig" -> {
                            val fd = figFromAttrs(attrs)
                            if (fd != null) {
                                sb.append(FIG_CHAR)
                                styles.add(CharStyle(fig = fd))
                            }
                        }
                        "br" -> emit('\n')
                        "div", "p" -> if (sb.isNotEmpty() && sb.last() != '\n') emit('\n')
                        "b", "strong", "i", "em", "u", "sub", "sup", "span", "font" -> {
                            stack.add(name to cur)
                            cur = applyTag(name, attrs, cur)
                        }
                        else -> {}
                    }
                    i = end + 1
                }
            }
            ch == '&' -> {
                val semi = html.indexOf(';', i)
                val ent = if (semi in (i + 2)..(i + 8)) html.substring(i + 1, semi) else null
                val rep = when {
                    ent == null -> null
                    ent.startsWith("#x") || ent.startsWith("#X") -> ent.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                    ent.startsWith("#") -> ent.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                    else -> ENTITIES[ent]
                }
                if (rep != null) {
                    rep.forEach { emit(it) }
                    i = semi + 1
                } else {
                    emit('&'); i++
                }
            }
            ch == '\r' -> i++
            else -> {
                emit(ch); i++
            }
        }
    }
    return Rich(sb.toString(), styles)
}

private fun esc(s: String): String = buildString {
    for (ch in s) when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '\n' -> append("<br>")
        else -> append(ch)
    }
}

fun toHtml(text: String, styles: List<CharStyle>): String {
    if (text.isEmpty()) return ""
    if (styles.size != text.length) return esc(text)
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        if (text[i] == FRAC_CHAR && styles[i].isFrac) {
            out.append("<frac n=\"").append(attrEsc(styles[i].fracN)).append("\" d=\"")
                .append(attrEsc(styles[i].fracD)).append("\"/>")
            i++
            continue
        }
        if (text[i] == FIG_CHAR && styles[i].isFig) {
            out.append(styles[i].fig!!.tag)
            i++
            continue
        }
        var j = i + 1
        while (j < text.length && styles[j] == styles[i]) j++
        val s = styles[i]
        val chunk = esc(text.substring(i, j))
        if (s.color != 0) out.append("<span style=\"color:").append(String.format("#%06X", s.color and 0xFFFFFF)).append("\">")
        if (s.bold) out.append("<b>")
        if (s.italic) out.append("<i>")
        if (s.underline) out.append("<u>")
        if (s.sub) out.append("<sub>")
        if (s.sup) out.append("<sup>")
        out.append(chunk)
        if (s.sup) out.append("</sup>")
        if (s.sub) out.append("</sub>")
        if (s.underline) out.append("</u>")
        if (s.italic) out.append("</i>")
        if (s.bold) out.append("</b>")
        if (s.color != 0) out.append("</span>")
        i = j
    }
    return out.toString()
}

/** Plain text of an HTML-subset string (tags removed, entities decoded). A picture shows as "▣" (it counts as content). */
fun plainText(html: String): String {
    if (html.indexOf('<') < 0 && html.indexOf('&') < 0) return html
    val r = parseHtml(html)
    if (FRAC_CHAR !in r.text && FIG_CHAR !in r.text) return r.text
    return buildString {
        r.text.forEachIndexed { i, ch ->
            if (r.isAtom(i)) append('(').append(plainText(r.styles[i].fracN)).append(")/(").append(plainText(r.styles[i].fracD)).append(')')
            else if (r.isFig(i)) append('\u25A3')
            else append(ch)
        }
    }
}
