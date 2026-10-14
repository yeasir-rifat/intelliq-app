package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.CharStyle
import com.intelliq.rifatplex.data.Fmt
import com.intelliq.rifatplex.data.shouldAutoSubscript
import com.intelliq.rifatplex.data.FRAC_CHAR
import com.intelliq.rifatplex.data.plainText
import com.intelliq.rifatplex.data.parseHtml
import com.intelliq.rifatplex.data.toHtml
import com.intelliq.rifatplex.ui.theme.IQ

/* ───────────── styled text helpers (shared with the paper) ───────────── */

fun CharStyle.toSpan(subSupScale: Float): SpanStyle = SpanStyle(
    color = if (color != 0) Color(color) else Color.Unspecified,
    fontSize = if (sub || sup) subSupScale.em else TextUnit.Unspecified,
    fontWeight = if (bold) FontWeight.Bold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    baselineShift = when {
        sub -> BaselineShift.Subscript
        sup -> BaselineShift.Superscript
        else -> null
    },
    textDecoration = if (underline) TextDecoration.Underline else null,
)

fun buildStyled(text: String, styles: List<CharStyle>, subSupScale: Float): AnnotatedString {
    if (styles.size != text.length) return AnnotatedString(text)
    val plain = CharStyle()
    return buildAnnotatedString {
        append(text)
        var i = 0
        while (i < text.length) {
            var j = i + 1
            while (j < text.length && styles[j] == styles[i]) j++
            if (styles[i] != plain) addStyle(styles[i].toSpan(subSupScale), i, j)
            i = j
        }
    }
}

/** HTML-subset string → styled text for read-only display (paper). */
fun htmlToAnnotated(html: String, subSupScale: Float): AnnotatedString {
    val r = parseHtml(html)
    return buildStyled(r.text, r.styles, subSupScale)
}

/** Whether the "Chemistry auto-format" switch is on (provided by IntelliQApp). */
val LocalChemAuto = androidx.compose.runtime.staticCompositionLocalOf { false }

/* ───────────── editor state ───────────── */

interface RichHandle {
    val active: Set<Fmt>
    val color: Int
    fun toggle(f: Fmt)
    fun insert(s: String)
    fun setColor(c: Int)
    fun clear()
    fun requestFraction()
}

/** A pending add/edit-fraction request. [index] = position of the atom being edited, or null for a new fraction. */
data class FracRequest(val index: Int?, val n: String, val d: String)

/** Short text shown for a fraction inside the (linear) editor field, e.g. "[a/b]". */
fun fracChip(s: CharStyle): String {
    fun cut(x: String): String {
        val p = plainText(x).replace('\n', ' ').trim()
        return if (p.isEmpty()) "□" else if (p.length > 10) p.take(9) + "…" else p
    }
    return "[" + cut(s.fracN) + "/" + cut(s.fracD) + "]"
}

/** Editor display: fractions become highlighted "[a/b]" chips (the paper shows them stacked). */
fun editorTransformed(text: String, styles: List<CharStyle>, scale: Float, chipFg: Color, chipBg: Color): TransformedText {
    if (styles.size != text.length || FRAC_CHAR !in text) {
        return TransformedText(buildStyled(text, styles, scale), OffsetMapping.Identity)
    }
    val idx = ArrayList<Int>()
    val lens = ArrayList<Int>()
    val out = buildAnnotatedString {
        var seg = 0
        for (i in text.indices) {
            if (text[i] == FRAC_CHAR && styles[i].isFrac) {
                if (i > seg) append(buildStyled(text.substring(seg, i), styles.subList(seg, i), scale))
                val chip = fracChip(styles[i])
                withStyle(SpanStyle(color = chipFg, background = chipBg)) { append(chip) }
                idx.add(i); lens.add(chip.length)
                seg = i + 1
            }
        }
        if (seg < text.length) append(buildStyled(text.substring(seg), styles.subList(seg, text.length), scale))
    }
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            var d = 0
            for (k in idx.indices) if (idx[k] < offset) d += lens[k] - 1 else break
            return offset + d
        }

        override fun transformedToOriginal(offset: Int): Int {
            var delta = 0
            for (k in idx.indices) {
                val ts = idx[k] + delta
                if (offset <= ts) return offset - delta
                if (offset < ts + lens[k]) return if (offset - ts < lens[k] / 2.0) idx[k] else idx[k] + 1
                delta += lens[k] - 1
            }
            return offset - delta
        }
    }
    return TransformedText(out, mapping)
}

@Stable
class RichEditorState(html: String) : RichHandle {
    private val initial = parseHtml(html)
    var tfv by mutableStateOf(TextFieldValue(initial.text, TextRange(initial.text.length)))
        private set
    var styles by mutableStateOf(initial.styles)
        private set
    private var typing by mutableStateOf<CharStyle?>(null)
    var lastHtml: String = html
    var onHtml: (String) -> Unit = {}
    var chemAuto: Boolean = false
    private var autoSubEnd = -1 // index right after the last auto-subscripted digit

    fun reset(html: String) {
        val r = parseHtml(html)
        val sel = tfv.selection
        tfv = TextFieldValue(r.text, TextRange(minOf(sel.start, r.text.length), minOf(sel.end, r.text.length)))
        styles = r.styles
        typing = null
        lastHtml = html
    }

    private fun emit() {
        val h = toHtml(tfv.text, styles)
        lastHtml = h
        onHtml(h)
    }

    private fun styleBefore(pos: Int): CharStyle =
        (if (pos > 0 && pos - 1 < styles.size) styles[pos - 1] else styles.firstOrNull() ?: CharStyle()).noFrac()

    private fun current(): CharStyle {
        val sel = tfv.selection
        if (sel.collapsed) return typing ?: styleBefore(sel.start)
        val part = styles.subList(sel.min.coerceAtMost(styles.size), sel.max.coerceAtMost(styles.size))
        if (part.isEmpty()) return CharStyle()
        val first = part.first()
        return CharStyle(
            bold = part.all { it.bold }, italic = part.all { it.italic }, underline = part.all { it.underline },
            sub = part.all { it.sub }, sup = part.all { it.sup },
            color = if (part.all { it.color == first.color }) first.color else 0,
        )
    }

    override val active: Set<Fmt>
        get() {
            val s = current()
            return Fmt.values().filter { s.has(it) }.toSet()
        }

    override val color: Int get() = current().color

    private fun mapSelection(f: (CharStyle) -> CharStyle) {
        val sel = tfv.selection
        styles = styles.mapIndexed { i, s -> if (i >= sel.min && i < sel.max) f(s) else s }
        emit()
    }

    override fun toggle(f: Fmt) {
        val sel = tfv.selection
        if (sel.collapsed) {
            val base = typing ?: styleBefore(sel.start)
            typing = base.with(f, !base.has(f))
        } else {
            val on = !current().has(f)
            mapSelection { it.with(f, on) }
        }
    }

    override fun setColor(c: Int) {
        val sel = tfv.selection
        if (sel.collapsed) {
            val base = typing ?: styleBefore(sel.start)
            typing = base.copy(color = c)
        } else {
            mapSelection { it.copy(color = c) }
        }
    }

    override fun clear() {
        val sel = tfv.selection
        if (sel.collapsed) typing = CharStyle() else mapSelection { CharStyle(fracN = it.fracN, fracD = it.fracD) }
    }

    override fun insert(s: String) {
        val sel = tfv.selection
        val st = typing ?: if (!sel.collapsed) (styles.getOrNull(sel.min) ?: CharStyle()).noFrac() else styleBefore(sel.start)
        val text = tfv.text
        val newText = text.replaceRange(sel.min, sel.max, s)
        styles = styles.subList(0, sel.min) + List(s.length) { st } + styles.subList(sel.max, styles.size)
        tfv = TextFieldValue(newText, TextRange(sel.min + s.length))
        emit()
    }

    var fracRequest by mutableStateOf<FracRequest?>(null)

    override fun requestFraction() {
        val sel = tfv.selection
        val i = sel.min
        val onAtom = sel.max - sel.min == 1 && i < styles.size && tfv.text[i] == FRAC_CHAR && styles[i].isFrac
        fracRequest = if (onAtom) FracRequest(i, styles[i].fracN, styles[i].fracD) else FracRequest(null, "", "")
    }

    fun editFractionAt(i: Int) {
        if (i in styles.indices && styles[i].isFrac) fracRequest = FracRequest(i, styles[i].fracN, styles[i].fracD)
    }

    /** Index of the fraction chip under [p] (editor-field coordinates), if any. */
    fun hitFraction(p: Offset, layout: TextLayoutResult): Int? {
        var delta = 0
        val len = layout.layoutInput.text.length
        for (i in tfv.text.indices) {
            if (tfv.text[i] == FRAC_CHAR && styles.getOrNull(i)?.isFrac == true) {
                val l = fracChip(styles[i]).length
                val ts = i + delta
                for (j in ts until minOf(ts + l, len)) if (layout.getBoundingBox(j).contains(p)) return i
                delta += l - 1
            }
        }
        return null
    }

    fun applyFraction(req: FracRequest, n: String, d: String) {
        fracRequest = null
        if (n.isEmpty() && d.isEmpty()) return
        val atom = CharStyle(fracN = n, fracD = d)
        val at = req.index
        if (at != null && at in styles.indices && tfv.text[at] == FRAC_CHAR) {
            styles = styles.mapIndexed { i, s -> if (i == at) atom else s }
        } else {
            val sel = tfv.selection
            val a = minOf(sel.min, tfv.text.length)
            val b = minOf(sel.max, tfv.text.length)
            val newText = tfv.text.replaceRange(a, b, FRAC_CHAR.toString())
            styles = styles.subList(0, a) + atom + styles.subList(b, styles.size)
            tfv = TextFieldValue(newText, TextRange(a + 1))
        }
        typing = null
        emit()
    }

    fun onChange(new: TextFieldValue) {
        val old = tfv
        if (new.text != old.text) {
            val o = old.text
            val n = new.text
            var p = 0
            val maxP = minOf(o.length, n.length)
            while (p < maxP && o[p] == n[p]) p++
            var s = 0
            val maxS = minOf(o.length, n.length) - p
            while (s < maxS && o[o.length - 1 - s] == n[n.length - 1 - s]) s++
            val removedEnd = o.length - s
            val insertedEnd = n.length - s
            val insLen = insertedEnd - p
            var insStyle = typing ?: if (removedEnd > p && p < styles.size) styles[p].noFrac() else styleBefore(p)
            var newAutoEnd = -1
            if (chemAuto && typing == null && removedEnd == p && insLen == 1) {
                val ch = n[p]
                if (shouldAutoSubscript(n, p, styles)) {
                    insStyle = insStyle.copy(sub = true, sup = false)
                    newAutoEnd = p + 1
                } else if (p == autoSubEnd && insStyle.sub && ch !in '0'..'9') {
                    insStyle = insStyle.copy(sub = false) // H2| then "O": back to normal size
                }
            }
            autoSubEnd = newAutoEnd
            styles = styles.subList(0, p) + List(insLen) { insStyle } + styles.subList(removedEnd, styles.size)
            tfv = new
            emit()
        } else {
            if (new.selection != old.selection) { typing = null; autoSubEnd = -1 }
            tfv = new
        }
    }
}

/** The website's contenteditable .editor-area with rich formatting. Stores an HTML-subset string. */
@Composable
fun RichEditor(
    html: String,
    onHtml: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    onFocus: (RichHandle) -> Unit = {},
) {
    val c = IQ.c
    val family = uiFont()
    val state = remember { RichEditorState(html) }
    state.onHtml = onHtml
    state.chemAuto = LocalChemAuto.current
    SideEffect { if (html != state.lastHtml) state.reset(html) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val styles = state.styles
    val chipFg = c.primary
    val chipBg = c.primarySoft
    val vt = VisualTransformation { t -> editorTransformed(t.text, styles, 0.75f, chipFg, chipBg) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val padX = with(LocalDensity.current) { 12.dp.toPx() }
    val padY = with(LocalDensity.current) { 9.dp.toPx() }
    state.fracRequest?.let { req ->
        FractionDialog(
            initialN = req.n, initialD = req.d, editing = req.index != null,
            onDismiss = { state.fracRequest = null },
            onApply = { n, d -> state.applyFraction(req, n, d) },
        )
    }
    BasicTextField(
        value = state.tfv,
        onValueChange = state::onChange,
        textStyle = TextStyle(fontSize = 13.5.sp, lineHeight = 21.6.sp, color = c.text, fontFamily = family),
        cursorBrush = SolidColor(c.primary),
        visualTransformation = vt,
        onTextLayout = { layout = it },
        modifier = modifier.fillMaxWidth().pointerInput(Unit) {
            detectTapGestures { pos ->
                val l = layout ?: return@detectTapGestures
                state.hitFraction(Offset(pos.x - padX, pos.y - padY), l)?.let { state.editFractionAt(it) }
            }
        }.onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus(state)
        },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(if (focused) c.fieldFocus else c.field, shape)
                    .border(1.dp, if (focused) c.primary else c.glassBorder, shape)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                if (state.tfv.text.isEmpty()) {
                    Text(placeholder, color = c.textFaint, fontSize = 13.5.sp, lineHeight = 21.6.sp, fontFamily = family)
                }
                inner()
            }
        },
    )
}

/* ───────────── formatting toolbar (.inline-toolbar) ───────────── */

private val WEB_SYMBOLS = listOf(
    "²", "³", "⁴", "½", "⅓", "¼", "√", "∛", "∑", "∫", "∞", "≈", "≠", "≤", "≥", "±", "×", "÷",
    "α", "β", "γ", "δ", "θ", "π", "λ", "μ", "Ω", "Δ", "∂", "∇", "→", "⇌", "°", "‰", "·", "…",
    "⅔", "⅕", "⅖", "⅗", "⅘", "⅙", "⅚", "⅛", "⅜", "⅝", "⅞", "∝",
)

// extra symbols for maths & chemistry
private val EXTRA_SYMBOLS = listOf(
    "⁰", "¹", "⁵", "⁶", "⁷", "⁸", "⁹", "⁺", "⁻", "ⁿ",
    "₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉", "₊", "₋",
    "∜", "∈", "∉", "∪", "∩", "⊂", "⊃", "⊆", "≡", "≅", "∠", "⊥", "∥", "∴", "∵",
    "←", "↑", "↓", "↔", "⇄", "⇋", "⟶", "⇒", "⇔", "∀", "∃", "∅",
    "ε", "ζ", "η", "ι", "κ", "ν", "ξ", "ρ", "σ", "τ", "φ", "χ", "ψ", "ω",
    "Σ", "Π", "Φ", "Ψ", "Γ", "Θ", "Λ", "Ξ", "ℏ", "Å", "℃", "′", "″", "∮", "∬",
    "⊕", "⊗", "⋅", "∘", "¬", "∧", "∨", "ℝ", "ℕ", "ℤ", "ℚ", "ℂ",
)

private val TEXT_COLORS = listOf(
    "#000000", "#374151", "#6B7280", "#DC2626", "#EA580C", "#D97706",
    "#16A34A", "#0D9488", "#2563EB", "#4F46E5", "#7C3AED", "#DB2777",
)

private fun hexToArgb(h: String): Int? {
    val t = h.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return (0xFF shl 24) or t.toInt(16)
}

@Composable
private fun RtBtn(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) c.primarySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatToolbar(handle: RichHandle?, allowFraction: Boolean = true, showClose: Boolean = true, onClose: () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(4.dp)
    val active = handle?.active ?: emptySet()
    FlowRow(
        Modifier
            .shadow(1.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        fun tint(f: Fmt) = if (f in active) c.primary else c.textSecondary
        RtBtn(Fmt.Bold in active, { handle?.toggle(Fmt.Bold) }) {
            Text("B", color = tint(Fmt.Bold), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        RtBtn(Fmt.Italic in active, { handle?.toggle(Fmt.Italic) }) {
            Text("I", color = tint(Fmt.Italic), fontSize = 13.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)
        }
        RtBtn(Fmt.Underline in active, { handle?.toggle(Fmt.Underline) }) {
            Text("U", color = tint(Fmt.Underline), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
        }
        RtBtn(Fmt.Sub in active, { handle?.toggle(Fmt.Sub) }) {
            Text("x₂", color = tint(Fmt.Sub), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        RtBtn(Fmt.Sup in active, { handle?.toggle(Fmt.Sup) }) {
            Text("x²", color = tint(Fmt.Sup), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (allowFraction) {
            RtBtn(false, { handle?.requestFraction() }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("a", color = c.textSecondary, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.size(width = 11.dp, height = 1.dp).background(c.textSecondary))
                    Text("b", color = c.textSecondary, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(20.dp).background(c.border))

        // math & symbols
        var symOpen by remember { mutableStateOf(false) }
        Box {
            RtBtn(false, { symOpen = true }) {
                Text("Σ", color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(expanded = symOpen, onDismissRequest = { symOpen = false }) {
                Column(
                    Modifier
                        .width(220.dp)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp)
                ) {
                    (WEB_SYMBOLS + EXTRA_SYMBOLS).chunked(5).forEach { row ->
                        Row {
                            row.forEach { sym ->
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            handle?.insert(sym)
                                            symOpen = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text(sym, color = c.text, fontSize = 15.sp) }
                            }
                        }
                    }
                }
            }
        }

        // colour
        var colOpen by remember { mutableStateOf(false) }
        Box {
            RtBtn(false, { colOpen = true }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Palette, null, Modifier.size(15.dp), tint = c.textSecondary)
                    val cur = handle?.color ?: 0
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(width = 14.dp, height = 2.dp)
                            .background(if (cur != 0) Color(cur) else Color.Transparent)
                    )
                }
            }
            DropdownMenu(expanded = colOpen, onDismissRequest = { colOpen = false }) {
                var hex by remember { mutableStateOf("") }
                Column(Modifier.width(200.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEXT_COLORS.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { h ->
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(h)))
                                        .border(1.dp, c.borderStrong, CircleShape)
                                        .clickable {
                                            handle?.setColor(hexToArgb(h) ?: 0)
                                            colOpen = false
                                        }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            InputBox(hex, { hex = it.take(7) }, "#RRGGBB", singleLine = true)
                        }
                        IQButton("OK", BtnKind.Secondary) {
                            hexToArgb(hex)?.let {
                                handle?.setColor(it)
                                colOpen = false
                            }
                        }
                    }
                    IQButton(
                        if (LocalLang.current == Lang.BN) "ডিফল্ট রঙ" else "Default colour",
                        BtnKind.Ghost, Modifier.fillMaxWidth(),
                    ) {
                        handle?.setColor(0)
                        colOpen = false
                    }
                }
            }
        }

        RtBtn(false, { handle?.clear() }) {
            Icon(Icons.Outlined.FormatClear, null, Modifier.size(15.dp), tint = c.textSecondary)
        }
        if (showClose) {
            Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(20.dp).background(c.border))
            RtBtn(false, onClose) { Icon(Icons.Outlined.Close, null, Modifier.size(15.dp), tint = c.textFaint) }
        }
    }
}
