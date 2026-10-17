package com.intelliq.rifatplex.data

/**
 * Pictures inside rich text (website `<div class="iq-fig"><img class="iq-img">`).
 *
 * A figure is ONE atom character ([FIG_CHAR]) in the text whose [CharStyle.fig] holds the pictures. It is stored in the
 * HTML-subset string as a single self-closing tag, so saving, History and undo keep working unchanged:
 *   <fig s="M" a="c" r="800/600,400/300" d="data:image/jpeg;base64,...|data:image/png;base64,..."/>
 */

/** One picture: a data URL (jpeg / png) and its pixel size. */
class Pic(val url: String, val w: Int, val h: Int) {
    val ratio: Float get() = if (w > 0 && h > 0) w.toFloat() / h else 1f
}

/** One figure block: up to [MAX_FIG_PICS] pictures side by side, a size (S / M / L) and an alignment (l / c / r). */
class FigData(val pics: List<Pic>, val size: String = "M", val align: String = "c") {
    fun copy(pics: List<Pic> = this.pics, size: String = this.size, align: String = this.align) = FigData(pics, size, align)

    /** The HTML-subset tag; built once because the editor re-serialises the text on every keystroke. */
    val tag: String by lazy { buildTag() }

    private fun buildTag(): String =
        "<fig s=\"" + size + "\" a=\"" + align + "\" r=\"" + pics.joinToString(",") { "${it.w}/${it.h}" } +
            "\" d=\"" + pics.joinToString("|") { it.url } + "\"/>"
}

const val MAX_FIG_PICS = 3

/** Width of every picture in percent of the column width (same numbers as the website `sizeW`). */
fun figWidthPct(size: String, n: Int): Int {
    if (n <= 1) return when (size) {
        "S" -> 35
        "L" -> 100
        else -> 60
    }
    val total = when (size) {
        "S" -> 60
        "L" -> 100
        else -> 85
    }
    return (total.toDouble() / n - 1).toInt()
}
