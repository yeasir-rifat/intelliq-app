package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.animation.AnimatedVisibility
import com.intelliq.rifatplex.ui.theme.PIcons
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.intelliq.rifatplex.data.BRAND_BOARD
import com.intelliq.rifatplex.data.BRAND_CLASSIC
import com.intelliq.rifatplex.data.BRAND_LETTER
import com.intelliq.rifatplex.data.ImageTools
import com.intelliq.rifatplex.data.POS_CENTER
import com.intelliq.rifatplex.data.POS_END
import com.intelliq.rifatplex.data.POS_START
import com.intelliq.rifatplex.data.Pic
import com.intelliq.rifatplex.data.PicCache
import com.intelliq.rifatplex.data.DIV_DOUBLE
import com.intelliq.rifatplex.data.DIV_NONE
import com.intelliq.rifatplex.data.DIV_THICK
import com.intelliq.rifatplex.data.DIV_THIN
import androidx.compose.material3.Slider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.material3.SliderDefaults
import kotlin.math.roundToInt
import com.intelliq.rifatplex.data.ANS_COLOR_BLACK
import com.intelliq.rifatplex.data.ANS_COLOR_GREEN
import com.intelliq.rifatplex.data.ANS_LAYOUT_CORNER
import com.intelliq.rifatplex.data.ANS_LAYOUT_EXPL
import com.intelliq.rifatplex.data.ANS_LAYOUT_FILL
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.ui.theme.DOC_FONT_NAMES
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.Lato

private val COLOR_PRESETS = listOf(
    "White" to "#FFFFFF",
    "Ivory" to "#FFFFF0",
    "Light Gray" to "#F3F4F6",
    "Light Blue" to "#EFF6FF",
)

private fun hexOrNull(s: String): String? {
    val t = s.trim().removePrefix("#")
    return if (t.length == 6 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) "#" + t.uppercase() else null
}

@Composable
fun SettingsDrawer(
    open: Boolean,
    onClose: () -> Unit,
    d: AppData,
    store: AppStore,
    lang: Lang,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onGenerated: (Int) -> Unit,
    onToast: (String, Boolean) -> Unit,
) {
    val c = IQ.c
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        ConfirmDialog(
            message = trKey("settings.resetConfirm", lang),
            confirmLabel = trKey("common.reset", lang),
            onConfirm = { confirmReset = false; onReset() },
            onDismiss = { confirmReset = false },
        )
    }
    // scrim
    AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x660F1219))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
    }
    // panel
    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelW = minOf(360.dp, maxWidth * 0.92f)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Column(
                    Modifier
                        .width(panelW)
                        .fillMaxHeight()
                        .shadow(12.dp)
                        .background(c.panel)
                        .drawBehind { drawLine(c.border, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                ) {
                    DrawerHead(onClose)
                    // one group open at a time, so the panel stays short and easy to scan
                    var openGroup by remember { mutableStateOf("doc") }
                    val toggle: (String) -> Unit = { id -> openGroup = if (openGroup == id) "" else id }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsGroup(tr("settings.grp.doc"), tr("settings.grp.doc.hint"), PIcons.Description, openGroup == "doc", { toggle("doc") }) {
                            PageSection(d, store)
                            GroupDivider()
                            FontSection(d, store)
                        }
                        SettingsGroup(tr("settings.grp.answer"), tr("settings.grp.answer.hint"), PIcons.CheckCircle, openGroup == "answer", { toggle("answer") }) {
                            AnswerFormatSection(d, store)
                        }
                        SettingsGroup(tr("settings.grp.brand"), tr("settings.grp.brand.hint"), PIcons.Palette, openGroup == "brand", { toggle("brand") }) {
                            BrandingSection(d, store, onToast)
                        }
                        SettingsGroup(tr("settings.grp.sets"), tr("settings.grp.sets.hint"), PIcons.GridView, openGroup == "sets", { toggle("sets") }) {
                            SetCodeSection(d, store, lang, onGenerated, onToast)
                        }
                        SettingsGroup(tr("settings.grp.ai"), tr("settings.grp.ai.hint"), PIcons.AutoAwesome, openGroup == "ai", { toggle("ai") }) {
                            AiSection(d, store, lang)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind { drawLine(c.border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IQButton(trKey("common.reset", lang), BtnKind.Ghost, Modifier.weight(1f)) { confirmReset = true }
                        IQButton(trKey("common.save", lang), BtnKind.Primary, Modifier.weight(1f)) { onSave() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHead(onClose: () -> Unit) {
    val c = IQ.c
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PIcons.Settings, null, Modifier.size(18.dp), tint = c.primary)
            Text(tr("settings.title"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
        }
        SquareBtn(36.dp, onClose) { Icon(PIcons.Close, null, Modifier.size(17.dp), tint = c.textSecondary) }
    }
}

/** One titled, collapsible group of settings (icon + title + hint + chevron); its sections show when open. */
@Composable
private fun SettingsGroup(
    title: String,
    hint: String,
    icon: ImageVector,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = IQ.c
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IQD.card)
            .background(c.panelSoft)
            .border(1.dp, if (open) c.primary else c.border, IQD.card)
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(IQD.ctl).background(if (open) c.primary else c.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(17.dp), tint = if (open) Color.White else c.primaryInk)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                if (hint.isNotBlank()) {
                    Text(hint, color = c.textSecondary, fontSize = 11.5.sp, fontFamily = uiFont())
                }
            }
            Icon(if (open) PIcons.ExpandLess else PIcons.ExpandMore, null, Modifier.size(20.dp), tint = c.textSecondary)
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) { content() }
        }
    }
}

@Composable
private fun GroupDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(IQ.c.border))
}

/** Small label that names a block inside a group (e.g. "Page", "Text"). */
@Composable
private fun SubHead(text: String) {
    Text(
        text, Modifier.padding(bottom = 10.dp), color = IQ.c.textFaint,
        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
    )
}

@Composable
private fun Section(bottomPad: Dp = 2.dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = bottomPad)) { content() }
}

/** A label with an (i) tip next to it, for rows that are not a switch/select pair. */
@Composable
private fun LabelTip(label: String, tip: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = IQ.c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        InfoTip(tip)
    }
}

@Composable
private fun SettingRow(label: String, tip: String? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), color = IQ.c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            if (tip != null) InfoTip(tip)
        }
        Box(Modifier.padding(start = 12.dp)) { content() }
    }
}

@Composable
private fun PageSection(d: AppData, store: AppStore) {
    val c = IQ.c
    Section {
        SubHead(tr("settings.sub.page"))
        SettingRow(tr("settings.pageSize")) {
            Select(d.settings.pageSize, listOf("A4" to "A4", "Letter" to "Letter", "Legal" to "Legal"), {
                store.setSettings { s -> s.copy(pageSize = it) }
            })
        }
        var hex by remember(d.settings.pageColor) { mutableStateOf(d.settings.pageColor) }
        SettingRow(tr("settings.pageColor")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val sw = IQD.ctl
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(sw)
                        .background(runCatching { Color(android.graphics.Color.parseColor(d.settings.pageColor)) }.getOrDefault(Color.White))
                        .border(1.dp, c.border, sw)
                )
                Box(Modifier.width(100.dp)) {
                    InputBox(
                        value = hex,
                        onChange = {
                            hex = it.take(7)
                            hexOrNull(hex)?.let { ok -> store.setSettings { s -> s.copy(pageColor = ok) } }
                        },
                        placeholder = "#FFFFFF",
                        singleLine = true,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COLOR_PRESETS.forEach { (_, h) ->
                val active = h.equals(d.settings.pageColor, ignoreCase = true)
                Box(
                    Modifier
                        .then(if (active) Modifier.border(2.dp, c.primary, CircleShape) else Modifier)
                        .padding(2.dp)
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(h)))
                            .border(1.dp, c.borderStrong, CircleShape)
                            .clickable { store.setSettings { s -> s.copy(pageColor = h) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun FontSection(d: AppData, store: AppStore) {
    Section {
        SubHead(tr("settings.sub.text"))
        SettingRow(tr("settings.fontFamily")) {
            Select(d.settings.fontFamily, DOC_FONT_NAMES.map { it to it }, { store.setSettings { s -> s.copy(fontFamily = it) } })
        }
        SettingRow(tr("settings.fontSize")) {
            val sizes = listOf(10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24)
            Select(
                d.settings.fontSize.toString(), sizes.map { it.toString() to it.toString() },
                { store.setSettings { s -> s.copy(fontSize = it.toInt()) } },
            )
        }
        SettingRow(tr("settings.showMarks")) {
            IQSwitch(d.settings.showMarks) { on -> store.setSettings { s -> s.copy(showMarks = on) } }
        }
        SettingRow(tr("settings.chemAuto"), tip = tr("tip.chem")) {
            IQSwitch(d.settings.chemAuto) { on -> store.setSettings { s -> s.copy(chemAuto = on) } }
        }
    }
}

@Composable
private fun SetCodeSection(
    d: AppData,
    store: AppStore,
    lang: Lang,
    onGenerated: (Int) -> Unit,
    onToast: (String, Boolean) -> Unit,
) {
    val c = IQ.c
    var status by remember { mutableStateOf("") }
    Section {
        SettingRow(tr("settings.setCode")) {
            IQSwitch(d.setCode.enabled) { on ->
                store.setSetCode { it.copy(enabled = on) }
                if (!on) status = ""
            }
        }
        if (d.setCode.enabled) {
            SettingRow(tr("settings.numSets")) {
                Select(
                    d.setCode.numberOfSets.toString(), (1..10).map { it.toString() to it.toString() },
                    { store.setSetCode { s -> s.copy(numberOfSets = it.toInt()) } },
                )
            }
            Column(Modifier.padding(bottom = 12.dp)) {
                Text(tr("settings.setCodeNames"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), modifier = Modifier.padding(bottom = 6.dp))
                InputBox(d.setCode.codeNames, { store.setSetCode { s -> s.copy(codeNames = it) } }, tr("ph.setNames"), singleLine = true)
            }
            SettingRow(tr("settings.randomization"), tip = tr("tip.random")) {
                Select(
                    d.setCode.randomization,
                    listOf(
                        "none" to tr("settings.randomNone"),
                        "questions" to tr("settings.randomQuestions"),
                        "options" to tr("settings.randomOptions"),
                        "both" to tr("settings.randomBoth"),
                    ),
                    { store.setSetCode { s -> s.copy(randomization = it) } },
                )
            }
            IQButton(tr("settings.generateSets"), BtnKind.Secondary, Modifier.fillMaxWidth()) {
                if (d.questions.none { !it.isEmpty() }) {
                    status = trKey("settings.needQuestionForSets", lang)
                } else {
                    val n = store.generateSets()
                    status = "${countText(n, lang, "set.countSingular", "set.countPlural")}."
                    onGenerated(n)
                }
            }
            Text(status, Modifier.padding(top = 8.dp).heightIn(min = 16.dp), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
        }
    }
}

@Composable
private fun AiSection(d: AppData, store: AppStore, lang: Lang) {
    val c = IQ.c
    Section {
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("settings.geminiKey"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            InfoTip(tr("tip.geminiKey"), linkLabel = "aistudio.google.com/apikey", linkUrl = "https://aistudio.google.com/apikey")
        }
        InputBox(d.ai.apiKey, { store.setAi { a -> a.copy(apiKey = it.trim()) } }, tr("ph.geminiKey"), singleLine = true)
        Box(Modifier.height(10.dp))
        SettingRow(tr("settings.model")) {
            Select(
                d.ai.model,
                listOf(
                    "gemini-3.8-flash" to tr("settings.modelRecommended"),
                    "gemini-3.5-flash-lite" to tr("model.35lite"),
                    "gemini-3.1-flash-lite" to tr("settings.modelFastest"),
                ),
                { store.setAi { a -> a.copy(model = it) } },
            )
        }
        SettingRow(tr("settings.onGenerate")) {
            Select(
                d.ai.insertMode,
                listOf("append" to tr("settings.appendQuestions"), "replace" to tr("settings.replaceQuestions")),
                { store.setAi { a -> a.copy(insertMode = it) } },
            )
        }
    }
}


/** Collapsible "Answer sheet formatting" dropdown: two checkboxes + three icon cards for the MCQ answer layout. */
@Composable
private fun AnswerFormatSection(d: AppData, store: AppStore) {
    val c = IQ.c
    val st = d.settings
    Section(bottomPad = 14.dp) {
        Column {
            run {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CheckRow(tr("settings.keepAnswer"), st.showAnswer) { on -> store.setSettings { s -> s.copy(showAnswer = on) } }
                    CheckRow(tr("settings.keepExpl"), st.showExplanation) { on -> store.setSettings { s -> s.copy(showExplanation = on) } }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    LabelTip(tr("settings.ansLayout"), tr("tip.ansLayout"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ANS_LAYOUT_FILL, ANS_LAYOUT_EXPL, ANS_LAYOUT_CORNER).forEach { kind ->
                            // "fill" and "corner" mark the correct answer itself, so they need the answer to be kept
                            val enabled = kind == ANS_LAYOUT_EXPL || st.showAnswer
                            LayoutCard(
                                kind, st.answerLayout == kind, enabled, Modifier.weight(1f),
                            ) { store.setSettings { s -> s.copy(answerLayout = kind) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("settings.ansColor"), Modifier.weight(1f), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ANS_COLOR_GREEN to Color(0xFF16A34A), ANS_COLOR_BLACK to Color(0xFF111111)).forEach { (key, col) ->
                                Box(
                                    Modifier
                                        .then(if (st.answerColor == key) Modifier.border(2.dp, c.primary, CircleShape) else Modifier)
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .border(1.dp, c.borderStrong, CircleShape)
                                            .clickable { store.setSettings { s -> s.copy(answerColor = key) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IQCheck(checked, onChange)
        Text(label, Modifier.weight(1f), color = IQ.c.text, fontSize = 13.sp, fontFamily = uiFont())
    }
}

/** One layout option: a tiny MCQ drawn with the theme colours, so it reads the same in light and dark. */
@Composable
private fun LayoutCard(kind: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(10.dp)
    val ink = c.textFaint
    val green = c.success
    Box(
        modifier
            .height(72.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (selected) c.primarySoft else c.field)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.glassBorder, shape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 64.dp, height = 52.dp)) {
            val u = 1.dp.toPx()
            val r = 4.5f * u
            val rowGap = 12f * u
            val x0 = 4f * u
            val y0 = 8f * u
            val bar = CornerRadius(1.5f * u)
            fun option(i: Int, hi: Boolean) {
                val cy = y0 + i * rowGap
                val col = if (hi) green else ink
                drawCircle(col, r, Offset(x0 + r, cy), style = if (hi) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.2f * u))
                drawRoundRect(col, Offset(x0 + 2 * r + 5 * u, cy - 1.5f * u), Size(if (hi) 30f * u else 26f * u, 3f * u), bar)
            }
            when (kind) {
                ANS_LAYOUT_FILL -> { option(0, false); option(1, true); option(2, false) }
                ANS_LAYOUT_EXPL -> {
                    option(0, false); option(1, false); option(2, false)
                    val y = y0 + 3 * rowGap - 3f * u
                    drawRoundRect(green, Offset(x0, y), Size(9f * u, 3f * u), bar)
                    drawRoundRect(ink, Offset(x0 + 12f * u, y), Size(34f * u, 3f * u), bar)
                }
                else -> {
                    option(0, false); option(1, false); option(2, false)
                    drawCircle(green, 5f * u, Offset(size.width - 5f * u, size.height - 5f * u))
                }
            }
        }
    }
}


/* ───────────── institution branding ───────────── */

/** A small icon card (a drawn miniature) used by the layout and position pickers. */
@Composable
private fun PickCard(
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    draw: DrawScope.(ink: Color, accent: Color) -> Unit,
) {
    val c = IQ.c
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (selected) c.primarySoft else c.field)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.glassBorder, shape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val ink = c.textFaint
        val accent = if (selected) c.primary else c.textSecondary
        Canvas(Modifier.size(width = 64.dp, height = height - 16.dp)) { this.draw(ink, accent) }
    }
}

private fun DrawScope.bar(c: Color, x: Float, y: Float, w: Float, h: Float) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(h / 2f))

private fun DrawScope.logoBox(c: Color, x: Float, y: Float, side: Float) =
    drawRoundRect(c, Offset(x, y), Size(side, side), CornerRadius(side * 0.22f))

private fun DrawScope.codeBox(c: Color, x: Float, y: Float, w: Float, h: Float) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(2.dp.toPx()), style = Stroke(1.2.dp.toPx()))

@Composable
private fun BrandLayoutCard(kind: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = IQ.c
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PickCard(selected, true, Modifier.fillMaxWidth(), 76.dp, onClick) { ink, accent ->
            val u = 1.dp.toPx()
            val w = size.width
            when (kind) {
                BRAND_LETTER -> {
                    logoBox(accent, 2 * u, 2 * u, 14 * u)
                    bar(ink, 21 * u, 4 * u, 32 * u, 4 * u)
                    bar(ink, 21 * u, 11 * u, 22 * u, 2.5f * u)
                    drawLine(ink, Offset(0f, 21 * u), Offset(w, 21 * u), 1.2f * u)
                    bar(ink, w / 2 - 18 * u, 28 * u, 36 * u, 4 * u)
                    bar(ink, w / 2 - 12 * u, 36 * u, 24 * u, 2.5f * u)
                }
                BRAND_BOARD -> {
                    logoBox(accent, 0f, 3 * u, 13 * u)
                    codeBox(accent, w - 14 * u, 3 * u, 14 * u, 9 * u)
                    bar(ink, w / 2 - 14 * u, 3 * u, 28 * u, 4 * u)
                    bar(ink, w / 2 - 10 * u, 10 * u, 20 * u, 2.5f * u)
                    bar(ink, w / 2 - 16 * u, 18 * u, 32 * u, 4 * u)
                    bar(ink, w / 2 - 11 * u, 25 * u, 22 * u, 2.5f * u)
                }
                else -> {
                    logoBox(accent, w / 2 - 7 * u, 0f, 14 * u)
                    bar(ink, w / 2 - 18 * u, 19 * u, 36 * u, 4 * u)
                    bar(ink, w / 2 - 12 * u, 26 * u, 24 * u, 2.5f * u)
                    bar(ink, w / 2 - 15 * u, 33 * u, 30 * u, 3.5f * u)
                    bar(ink, w / 2 - 10 * u, 40 * u, 20 * u, 2.5f * u)
                }
            }
        }
        Text(label, color = if (selected) c.primaryInk else c.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1)
    }
}

/** Position of the logo (start / centre / end) or of the set code box (start / end) as a tiny header sketch. */
@Composable
private fun BrandPosCard(pos: String, label: String, isLogo: Boolean, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = IQ.c
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PickCard(selected, enabled, Modifier.fillMaxWidth(), 48.dp, onClick) { ink, accent ->
            val u = 1.dp.toPx()
            val w = size.width
            val side = 11 * u
            val x = when (pos) { POS_START -> 2 * u; POS_END -> w - side - 2 * u; else -> w / 2 - side / 2 }
            if (isLogo) logoBox(accent, x, 1 * u, side) else codeBox(accent, x - (if (pos == POS_END) 4 * u else 0f), 1 * u, 15 * u, 10 * u)
            bar(ink, w / 2 - 14 * u, 18 * u, 28 * u, 3 * u)
            bar(ink, w / 2 - 9 * u, 24 * u, 18 * u, 2.5f * u)
        }
        Text(
            label, color = (if (selected) c.primaryInk else c.textSecondary).copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 11.5.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1,
        )
    }
}

@Composable
private fun BrandChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (on) c.primarySoft else c.field)
            .border(1.dp, if (on) c.primary else c.glassBorder, shape)
            .clickable { onClick() }
            .padding(start = if (on) 8.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (on) Icon(PIcons.Check, null, Modifier.size(13.dp), tint = c.primaryInk)
        Text(label, color = if (on) c.primaryInk else c.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
    }
}

/** A collapsible group of the branding settings (only one is open at a time, so the live preview stays in view). */
@Composable
private fun BrandGroup(title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, if (open) c.primary else c.glassBorder, shape)) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }
                .background(if (open) c.primarySoft else c.field)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, Modifier.weight(1f), color = if (open) c.primaryInk else c.text,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            )
            Icon(if (open) PIcons.ExpandLess else PIcons.ExpandMore, null, Modifier.size(18.dp), tint = c.textSecondary)
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

@Composable
private fun BrandLink(label: String, onClick: () -> Unit) {
    Text(
        label, Modifier.clickable { onClick() }.padding(vertical = 4.dp),
        color = IQ.c.primaryInk, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
    )
}

@Composable
private fun brandPosLabel(pos: String): String = when (pos) {
    POS_START -> tr("brand.posStart")
    POS_END -> tr("brand.posEnd")
    else -> tr("brand.posCenter")
}

private val BRAND_COLORS = listOf("#111111", "#1D4ED8", "#047857", "#B91C1C", "#7C3AED", "#B45309", "#0F766E")

/** A labelled slider in whole percent, moving in steps of 10. */
@Composable
private fun BrandSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = IQ.c
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            Text("$value%", color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
        }
        Slider(
            value = v,
            onValueChange = { v = it; onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 10 - 1,
            colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.border),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrandingSection(d: AppData, store: AppStore, onToast: (String, Boolean) -> Unit) {
    val c = IQ.c
    val b = d.brand
    val ctx = LocalContext.current
    val errMsg = tr("brand.logoErr")
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val pic = ImageTools.logoFromUri(ctx.contentResolver, uri)
            if (pic != null) store.setBrand { it.copy(logo = pic.url, logoW = pic.w, logoH = pic.h, showLogo = true) }
            else onToast(errMsg, true)
        }
    }
    var cropOpen by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf("identity") }
    val toggle: (String) -> Unit = { id -> open = if (open == id) "" else id }
    Section(bottomPad = 14.dp) {
        SettingRow(tr("settings.brandOn")) {
            IQSwitch(b.enabled) { on -> store.setBrand { it.copy(enabled = on) } }
        }
        if (b.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // live preview: the real header, scaled down
                Text(tr("brand.preview"), color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                BrandPreview(b, d.doc, d.settings, LocalLang.current)

                BrandGroup(tr("brand.grp.identity"), open == "identity", { toggle("identity") }) {
                    // logo, name, slogan
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.field)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val bmp = if (b.logo.isNotEmpty()) PicCache.get(b.logo) else null
                            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                            else Icon(PIcons.Description, null, Modifier.size(20.dp), tint = c.textFaint)
                        }
                        IQButton(
                            if (b.logo.isEmpty()) tr("settings.brandLogoAdd") else tr("settings.brandLogoChange"),
                            BtnKind.Secondary, Modifier.weight(1f),
                        ) { pick.launch("image/*") }
                        if (b.logo.isNotEmpty()) {
                            IQButton(tr("settings.brandLogoRemove"), BtnKind.Ghost) { store.setBrand { it.copy(logo = "", logoW = 0, logoH = 0) } }
                        }
                    }
                    if (b.logo.isNotEmpty()) {
                        IQButton(tr("brand.logoCrop"), BtnKind.Secondary, Modifier.fillMaxWidth()) { cropOpen = true }
                        BrandSlider(tr("brand.logoSize"), b.logoScale, 60..200) { v -> store.setBrand { it.copy(logoScale = v) } }
                    }
                    Field(tr("settings.brandName"), b.name, { v -> store.setBrand { it.copy(name = v) } }, tr("ph.brandName"))
                    Field(tr("settings.brandSlogan"), b.slogan, { v -> store.setBrand { it.copy(slogan = v) } }, tr("ph.brandSlogan"))
                }

                BrandGroup(tr("brand.grp.extra"), open == "extra", { toggle("extra") }) {
                    Field(tr("brand.address"), b.address, { v -> store.setBrand { it.copy(address = v) } }, tr("ph.brandAddress"))
                    Field(tr("brand.contact"), b.contact, { v -> store.setBrand { it.copy(contact = v) } }, tr("ph.brandContact"))
                    Field(tr("brand.eiin"), b.eiin, { v -> store.setBrand { it.copy(eiin = v) } }, tr("ph.brandEiin"))
                }

                BrandGroup(tr("settings.brandLayout"), open == "layout", { toggle("layout") }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            BRAND_CLASSIC to tr("brand.layoutClassic"),
                            BRAND_LETTER to tr("brand.layoutLetter"),
                            BRAND_BOARD to tr("brand.layoutBoard"),
                        ).forEach { (k, label) ->
                            BrandLayoutCard(k, label, b.layout == k, Modifier.weight(1f)) { store.setBrand { it.copy(layout = k) } }
                        }
                    }
                }

                BrandGroup(tr("brand.grp.style"), open == "style", { toggle("style") }) {
                    BrandSlider(tr("brand.nameSize"), b.nameScale, 70..150) { v -> store.setBrand { it.copy(nameScale = v) } }
                    Text(tr("brand.color"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrandChip(tr("brand.colorAuto"), b.color.isEmpty()) { store.setBrand { it.copy(color = "") } }
                        BRAND_COLORS.forEach { h ->
                            val active = h.equals(b.color, ignoreCase = true)
                            Box(
                                Modifier
                                    .then(if (active) Modifier.border(2.dp, c.primary, CircleShape) else Modifier)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(h)))
                                        .border(1.dp, c.borderStrong, CircleShape)
                                        .clickable { store.setBrand { it.copy(color = h) } }
                                )
                            }
                        }
                    }
                    var hex by remember(b.color) { mutableStateOf(b.color) }
                    Box(Modifier.width(120.dp)) {
                        InputBox(
                            value = hex,
                            onChange = { v ->
                                hex = v.take(7)
                                hexOrNull(hex)?.let { ok -> store.setBrand { it.copy(color = ok) } }
                            },
                            placeholder = "#1D4ED8",
                            singleLine = true,
                        )
                    }
                    Text(tr("brand.divider"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            DIV_NONE to tr("brand.divNone"), DIV_THIN to tr("brand.divThin"),
                            DIV_THICK to tr("brand.divThick"), DIV_DOUBLE to tr("brand.divDouble"),
                        ).forEach { (k, label) ->
                            BrandChip(label, b.effDivider() == k) { store.setBrand { it.copy(divider = k) } }
                        }
                    }
                }

                BrandGroup(tr("settings.brandShow"), open == "show", { toggle("show") }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)) {
                        BrandLink(tr("brand.selAll")) {
                            store.setBrand {
                                it.copy(showLogo = true, showName = true, showSlogan = true, showExam = true, showSubject = true,
                                    showChapter = true, showTime = true, showMarks = true, showSetCode = true,
                                    showAddress = true, showContact = true, showEiin = true)
                            }
                        }
                        BrandLink(tr("brand.selNone")) {
                            store.setBrand {
                                it.copy(showLogo = false, showName = false, showSlogan = false, showExam = false, showSubject = false,
                                    showChapter = false, showTime = false, showMarks = false, showSetCode = false,
                                    showAddress = false, showContact = false, showEiin = false)
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrandChip(tr("brand.logo"), b.showLogo) { store.setBrand { it.copy(showLogo = !it.showLogo) } }
                        BrandChip(tr("brand.name"), b.showName) { store.setBrand { it.copy(showName = !it.showName) } }
                        BrandChip(tr("brand.slogan"), b.showSlogan) { store.setBrand { it.copy(showSlogan = !it.showSlogan) } }
                        BrandChip(tr("brand.address"), b.showAddress) { store.setBrand { it.copy(showAddress = !it.showAddress) } }
                        BrandChip(tr("brand.contact"), b.showContact) { store.setBrand { it.copy(showContact = !it.showContact) } }
                        BrandChip(tr("brand.eiin"), b.showEiin) { store.setBrand { it.copy(showEiin = !it.showEiin) } }
                        BrandChip(tr("brand.exam"), b.showExam) { store.setBrand { it.copy(showExam = !it.showExam) } }
                        BrandChip(tr("brand.subject"), b.showSubject) { store.setBrand { it.copy(showSubject = !it.showSubject) } }
                        BrandChip(tr("brand.chapter"), b.showChapter) { store.setBrand { it.copy(showChapter = !it.showChapter) } }
                        BrandChip(tr("brand.time"), b.showTime) { store.setBrand { it.copy(showTime = !it.showTime) } }
                        BrandChip(tr("brand.marks"), b.showMarks) { store.setBrand { it.copy(showMarks = !it.showMarks) } }
                        BrandChip(tr("brand.code"), b.showSetCode) { store.setBrand { it.copy(showSetCode = !it.showSetCode) } }
                    }
                }

                BrandGroup(tr("brand.grp.pos"), open == "pos", { toggle("pos") }) {
                    LabelTip(tr("settings.brandLogoPos"), tr("tip.logoPos"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(POS_START, POS_CENTER, POS_END).forEach { p ->
                            // only the classic layout has a centre slot for the logo
                            val ok = p != POS_CENTER || b.layout == BRAND_CLASSIC
                            BrandPosCard(p, brandPosLabel(p), true, b.effLogoPos() == p, ok, Modifier.weight(1f)) { store.setBrand { it.copy(logoPos = p) } }
                        }
                    }
                    LabelTip(tr("settings.brandCodePos"), tr("tip.codePos"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(POS_START, POS_END).forEach { p ->
                            BrandPosCard(p, brandPosLabel(p), false, b.codePos == p, true, Modifier.weight(1f)) { store.setBrand { it.copy(codePos = p) } }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (cropOpen && b.logo.isNotEmpty()) {
        CropDialog(
            pic = Pic(b.logo, b.logoW, b.logoH),
            onDismiss = { cropOpen = false },
            onDone = { out ->
                store.setBrand { it.copy(logo = out.url, logoW = out.w, logoH = out.h) }
                cropOpen = false
            },
            logo = true,
        )
    }
}
