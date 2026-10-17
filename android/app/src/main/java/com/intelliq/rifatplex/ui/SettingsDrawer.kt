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
import com.intelliq.rifatplex.data.OMR_AUTO
import com.intelliq.rifatplex.data.OMR_CUSTOM
import androidx.activity.compose.BackHandler
import com.intelliq.rifatplex.data.OmrSettings
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
    // the phone's back button / gesture closes the panel instead of leaving the app
    BackHandler(enabled = open) { onClose() }
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Column(
                    Modifier
                        .fillMaxSize() // whole display, edge to edge
                        .background(c.panel)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                ) {
                    DrawerHead(onClose)
                    // one group open at a time (all folded by default), so the panel stays short and easy to scan
                    var openGroup by remember { mutableStateOf("") } // every group starts folded
                    val toggle: (String) -> Unit = { id -> openGroup = if (openGroup == id) "" else id }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsGroup(tr("settings.grp.doc"), tr("settings.grp.doc.hint"), PIcons.Description, GroupAccent(Color(0xFF4C9AFF), Color(0xFF146EF5)), openGroup == "doc", { toggle("doc") }) {
                            PageSection(d, store)
                            GroupDivider()
                            FontSection(d, store)
                            GroupDivider()
                            PageNumberSection(d, store)
                            GroupDivider()
                            AutoFixSection(d, store)
                        }
                        SettingsGroup(tr("settings.grp.answer"), tr("settings.grp.answer.hint"), PIcons.CheckCircle, GroupAccent(Color(0xFF34D399), Color(0xFF0E9F6E)), openGroup == "answer", { toggle("answer") }) {
                            AnswerFormatSection(d, store)
                        }
                        SettingsGroup(tr("settings.grp.omr"), tr("settings.grp.omr.hint"), PIcons.ViewColumn, GroupAccent(Color(0xFFA78BFA), Color(0xFF7C3AED)), openGroup == "omr", { toggle("omr") }) {
                            OmrSection(d, store)
                        }
                        SettingsGroup(tr("settings.grp.omrEval"), tr("settings.grp.omrEval.hint"), PIcons.CameraAlt, GroupAccent(Color(0xFFFBBF24), Color(0xFFF97316)), openGroup == "omrEval", { toggle("omrEval") }) {
                            OmrEvalSection(d)
                        }
                        SettingsGroup(tr("settings.grp.brand"), tr("settings.grp.brand.hint"), PIcons.Palette, GroupAccent(Color(0xFFF472B6), Color(0xFFDB2777)), openGroup == "brand", { toggle("brand") }) {
                            BrandingSection(d, store, onToast)
                        }
                        SettingsGroup(tr("settings.grp.sets"), tr("settings.grp.sets.hint"), PIcons.GridView, GroupAccent(Color(0xFF22D3EE), Color(0xFF0891B2)), openGroup == "sets", { toggle("sets") }) {
                            SetCodeSection(d, store, lang, onGenerated, onToast)
                        }
                        SettingsGroup(tr("settings.grp.ai"), tr("settings.grp.ai.hint"), PIcons.AutoAwesome, GroupAccent(Color(0xFF818CF8), Color(0xFFC026D3)), openGroup == "ai", { toggle("ai") }) {
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
            Text(tr("settings.title"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
        }
        SquareBtn(36.dp, onClose) { Icon(PIcons.Close, null, Modifier.size(17.dp), tint = c.textSecondary) }
    }
}

/** Light and deep shade of one settings group's own colour (gradient tile, open border, glow). */
@androidx.compose.runtime.Immutable
private class GroupAccent(val light: Color, val deep: Color)

/** One titled, collapsible group of settings (icon + title + hint + chevron); its sections show when open. */
@Composable
private fun SettingsGroup(
    title: String,
    hint: String,
    icon: ImageVector,
    accent: GroupAccent,
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
            .border(1.dp, if (open) accent.deep else c.border, IQD.card)
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
            // a coloured gradient tile per group, with a soft glow in the group's own colour
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(if (open) 6.dp else 3.dp, RoundedCornerShape(13.dp), ambientColor = accent.deep, spotColor = accent.deep)
                    .clip(RoundedCornerShape(13.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.light, accent.deep))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
            }
            // title only (no sub text), centred vertically against the icon tile
            Box(Modifier.weight(1f).heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
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
private fun SubHead(text: String, tip: String? = null) {
    Row(Modifier.padding(bottom = if (tip != null) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, color = IQ.c.textFaint,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
        )
        if (tip != null) InfoTip(tip)
    }
}

@Composable
private fun Section(bottomPad: Dp = 2.dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = bottomPad)) { content() }
}

/** A label with an (i) tip next to it, for rows that are not a switch/select pair. */
@Composable
private fun LabelTip(label: String, tip: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = IQ.c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        InfoTip(tip)
    }
}

/**
 * [stacked] = label (+ tip) on its own line ABOVE the control, so a wide select/input gets the full width and
 * the label never gets squeezed into a vertical strip. Use it for long selects; short ones stay side by side.
 */
@Composable
private fun SettingRow(label: String, tip: String? = null, stacked: Boolean = false, content: @Composable () -> Unit) {
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = IQ.c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                if (tip != null) InfoTip(tip)
            }
            content()
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), color = IQ.c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            if (tip != null) InfoTip(tip)
        }
        Box(Modifier.padding(start = 12.dp)) { content() }
    }
}

@Composable
private fun PageSection(d: AppData, store: AppStore) {
    val c = IQ.c
    Section {
        SubHead(tr("settings.sub.page"), tr("settings.sheetFixed"))
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
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
        SubHead(tr("settings.sub.text"), tr("settings.sheetFixed"))
        SettingRow(tr("settings.fontFamily"), stacked = true) {
            Select(d.settings.fontFamily, DOC_FONT_NAMES.map { it to it }, { store.setSettings { s -> s.copy(fontFamily = it) } }, fullWidth = true)
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
                Text(tr("settings.setCodeNames"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), modifier = Modifier.padding(bottom = 6.dp))
                InputBox(d.setCode.codeNames, { store.setSetCode { s -> s.copy(codeNames = it) } }, tr("ph.setNames"), singleLine = true)
            }
            SettingRow(tr("settings.randomization"), tip = tr("tip.random"), stacked = true) {
                Select(
                    d.setCode.randomization,
                    listOf(
                        "none" to tr("settings.randomNone"),
                        "questions" to tr("settings.randomQuestions"),
                        "options" to tr("settings.randomOptions"),
                        "both" to tr("settings.randomBoth"),
                    ),
                    { store.setSetCode { s -> s.copy(randomization = it) } },
                    fullWidth = true,
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
            Text(status, Modifier.padding(top = 8.dp).heightIn(min = 16.dp), color = c.textSecondary, fontSize = 13.sp, fontFamily = uiFont())
        }
    }
}

@Composable
private fun AiSection(d: AppData, store: AppStore, lang: Lang) {
    val c = IQ.c
    Section {
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("settings.geminiKey"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            InfoTip(tr("tip.geminiKey"), linkLabel = "aistudio.google.com/apikey", linkUrl = "https://aistudio.google.com/apikey")
        }
        InputBox(d.ai.apiKey, { store.setAi { a -> a.copy(apiKey = it.trim()) } }, tr("ph.geminiKey"), singleLine = true)
        Box(Modifier.height(10.dp))
        SettingRow(tr("settings.model"), stacked = true) {
            Select(
                d.ai.model,
                listOf(
                    "gemini-3.8-flash" to tr("settings.modelRecommended"),
                    "gemini-3.5-flash-lite" to tr("model.35lite"),
                    "gemini-3.1-flash-lite" to tr("settings.modelFastest"),
                ),
                { store.setAi { a -> a.copy(model = it) } },
                fullWidth = true,
            )
        }
        SettingRow(tr("settings.onGenerate"), stacked = true) {
            Select(
                d.ai.insertMode,
                listOf("append" to tr("settings.appendQuestions"), "replace" to tr("settings.replaceQuestions")),
                { store.setAi { a -> a.copy(insertMode = it) } },
                fullWidth = true,
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
                        Text(tr("settings.ansColor"), Modifier.weight(1f), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
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

/** "Add page numbers to the document" checkbox, at the bottom of the Document group. */
@Composable
private fun PageNumberSection(d: AppData, store: AppStore) {
    Section(bottomPad = 14.dp) {
        CheckRow(tr("settings.pageNumber"), d.settings.showPageNumber) { on ->
            store.setSettings { s -> s.copy(showPageNumber = on) }
        }
    }
}

/** "Document AutoFix" checkbox (with an info tip), at the bottom of the Document group. */
@Composable
private fun AutoFixSection(d: AppData, store: AppStore) {
    val on = d.settings.autoFix
    Section(bottomPad = 14.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { store.setSettings { s -> s.copy(autoFix = !on) } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IQCheck(on) { v -> store.setSettings { s -> s.copy(autoFix = v) } }
            Text(tr("settings.autoFix"), color = IQ.c.text, fontSize = 14.sp, fontFamily = uiFont())
            InfoTip(tr("tip.autoFix"))
            Spacer(Modifier.weight(1f))
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
        Text(label, Modifier.weight(1f), color = IQ.c.text, fontSize = 14.sp, fontFamily = uiFont())
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
                    // badge floats at the bottom-right of the options, level with the last one
                    drawCircle(green, 4.5f * u, Offset(size.width - 4.5f * u, y0 + 2 * rowGap))
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
        Text(label, color = if (selected) c.primaryInk else c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), maxLines = 1)
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
        Text(label, color = if (on) c.primaryInk else c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
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
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
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
        color = IQ.c.primaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
    )
}

private val BRAND_COLORS = listOf("#111111", "#1D4ED8", "#047857", "#B91C1C", "#7C3AED", "#B45309", "#0F766E")

/** A labelled slider in whole percent, moving in steps of 10. */
@Composable
private fun BrandSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = IQ.c
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            Text("$value%", color = c.textSecondary, fontSize = 13.sp, fontFamily = uiFont())
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
                Text(tr("brand.preview"), color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
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
                    Text(tr("brand.color"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
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
                    Text(tr("brand.divider"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
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


// ---------------------------------------------------------------------------------------------------------------
// OMR Sheet: automatic or custom
// ---------------------------------------------------------------------------------------------------------------

/** One labelled text input of the custom OMR; an empty value means "use the automatic text" (shown as the hint). */
@Composable
private fun OmrInput(label: String, value: String, placeholder: String, minLines: Int = 1, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        FieldLabel(label)
        InputBox(value, onChange, placeholder, minLines = minLines, singleLine = minLines <= 1)
    }
}

/** A block of the sheet: a switch to show it, and (when shown) the input that renames it. */
@Composable
private fun OmrBlockRow(
    title: String, on: Boolean, label: String, placeholder: String,
    onToggle: (Boolean) -> Unit, onLabel: (String) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        CheckRow(title, on, onToggle)
        if (on) {
            Column(Modifier.padding(top = 8.dp, start = 28.dp)) {
                InputBox(label, onLabel, placeholder, singleLine = true)
                extra?.invoke()
            }
        }
    }
}

@Composable
private fun OmrDigits(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = IQ.c.textSecondary, fontSize = 13.sp, fontFamily = uiFont())
        NumberField(value, 1, max) { onChange(it) }
    }
}

/**
 * "OMR Sheet" group. Auto OMR: the sheet is built from the exam title, subject and institution settings.
 * Custom OMR: every text, block and digit count is chosen here; a text left empty keeps the automatic text.
 */
@Composable
private fun OmrSection(d: AppData, store: AppStore) {
    val o = d.settings.omr
    val bn = LocalLang.current == Lang.BN
    fun set(f: (OmrSettings) -> OmrSettings) = store.setSettings { s -> s.copy(omr = f(s.omr)) }
    Section(bottomPad = 14.dp) {
        // the explanation is a tip beside the switch, not a line of text under it
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Segmented(
                listOf(OMR_AUTO to tr("settings.omr.auto"), OMR_CUSTOM to tr("settings.omr.custom")),
                o.mode,
                Modifier.weight(1f),
            ) { m -> set { it.copy(mode = m) } }
            InfoTip(tr(if (o.isCustom) "settings.omr.customInfo" else "settings.omr.autoInfo"))
        }
        Spacer(Modifier.height(10.dp))
        if (!o.isCustom) return@Section

        // what the automatic sheet would print, shown as the hint of the empty inputs
        val brandName = if (d.brand.enabled && d.brand.showName) d.brand.name.trim() else ""
        val auto1 = brandName.ifEmpty { d.doc.title.trim() }
        val auto2 = (if (brandName.isNotEmpty()) listOf(d.doc.title.trim(), d.doc.subtitle.trim()) else listOf(d.doc.subtitle.trim()))
            .filter { it.isNotEmpty() }.joinToString(" • ")
        fun dflt(key: String) = trKey(key, if (bn) Lang.BN else Lang.EN)

        SubHeadSpaced(tr("settings.omr.header"))
        OmrInput(tr("settings.omr.board"), o.boardName, auto1.ifEmpty { tr("settings.omr.autoText") }) { v -> set { it.copy(boardName = v) } }
        OmrInput(tr("settings.omr.exam"), o.examLine, auto2.ifEmpty { tr("settings.omr.autoText") }) { v -> set { it.copy(examLine = v) } }
        CheckRow(tr("settings.omr.year"), o.showYear) { v -> set { it.copy(showYear = v) } }
        if (o.showYear) {
            Column(Modifier.padding(top = 8.dp, start = 28.dp)) {
                InputBox(o.yearPrefix, { v -> set { it.copy(yearPrefix = v.take(4)) } }, tr("settings.omr.yearPrefix"), singleLine = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        OmrInput(tr("settings.omr.sheetTitle"), o.sheetTitle, dflt("omr.sheetName")) { v -> set { it.copy(sheetTitle = v) } }
        OmrBlockRow(
            tr("settings.omr.banner1"), o.showBanner1, o.banner1, dflt("omr.banner1"),
            { v -> set { it.copy(showBanner1 = v) } }, { v -> set { it.copy(banner1 = v) } },
        )
        OmrBlockRow(
            tr("settings.omr.banner2"), o.showBanner2, o.banner2, dflt("omr.banner"),
            { v -> set { it.copy(showBanner2 = v) } }, { v -> set { it.copy(banner2 = v) } },
        )

        GroupDivider()
        SubHeadSpaced(tr("settings.omr.answers"))
        OmrInput(tr("settings.omr.qHead"), o.qHeader, dflt("omr.qNo")) { v -> set { it.copy(qHeader = v) } }
        OmrInput(tr("settings.omr.aHead"), o.ansHeader, dflt("omr.answer")) { v -> set { it.copy(ansHeader = v) } }
        Box(Modifier.padding(bottom = 12.dp)) {
            CheckRow(tr("settings.omr.shading"), o.shading) { v -> set { it.copy(shading = v) } }
        }

        GroupDivider()
        SubHeadSpaced(tr("settings.omr.blocks"))
        OmrBlockRow(
            tr("settings.omr.roll"), o.showRoll, o.rollLabel, dflt("omr.roll"),
            { v -> set { it.copy(showRoll = v) } }, { v -> set { it.copy(rollLabel = v) } },
        ) { OmrDigits(tr("settings.omr.digits"), o.rollDigits, 8) { v -> set { it.copy(rollDigits = v) } } }
        OmrBlockRow(
            tr("settings.omr.set"), o.showSet, o.setLabel, dflt("omr.set"),
            { v -> set { it.copy(showSet = v) } }, { v -> set { it.copy(setLabel = v) } },
        )
        OmrBlockRow(
            tr("settings.omr.reg"), o.showReg, o.regLabel, dflt("omr.reg"),
            { v -> set { it.copy(showReg = v) } }, { v -> set { it.copy(regLabel = v) } },
        ) { OmrDigits(tr("settings.omr.digits"), o.regDigits, 12) { v -> set { it.copy(regDigits = v) } } }
        OmrBlockRow(
            tr("settings.omr.subject"), o.showSubject, o.subjectLabel, dflt("omr.subject"),
            { v -> set { it.copy(showSubject = v) } }, { v -> set { it.copy(subjectLabel = v) } },
        ) { OmrDigits(tr("settings.omr.digits"), o.subjectDigits, 4) { v -> set { it.copy(subjectDigits = v) } } }
        OmrBlockRow(
            tr("settings.omr.marks"), o.showMarks, o.marksLabel, dflt("omr.marks"),
            { v -> set { it.copy(showMarks = v) } }, { v -> set { it.copy(marksLabel = v) } },
        )
        OmrBlockRow(
            tr("settings.omr.invig"), o.showInvigilator, o.invigilatorLabel, dflt("omr.invig"),
            { v -> set { it.copy(showInvigilator = v) } }, { v -> set { it.copy(invigilatorLabel = v) } },
        )

        GroupDivider()
        SubHeadSpaced(tr("settings.omr.bottom"))
        OmrBlockRow(
            tr("settings.omr.note"), o.showNote, o.note, dflt("omr.note"),
            { v -> set { it.copy(showNote = v) } }, { v -> set { it.copy(note = v) } },
        )
        OmrBlockRow(
            tr("settings.omr.rules"), o.showRules, o.rulesTitle, dflt("omr.rules"),
            { v -> set { it.copy(showRules = v) } }, { v -> set { it.copy(rulesTitle = v) } },
        ) {
            Spacer(Modifier.height(10.dp))
            InputBox(o.rules, { v -> set { it.copy(rules = v) } }, tr("settings.omr.rulesHint"), minLines = 5)
        }
        OmrBlockRow(
            tr("settings.omr.signature"), o.showSignature, o.signatureLabel, dflt("omr.sigStudent"),
            { v -> set { it.copy(showSignature = v) } }, { v -> set { it.copy(signatureLabel = v) } },
        )

        IQButton(tr("settings.omr.reset"), BtnKind.Ghost, Modifier.fillMaxWidth()) {
            set { OmrSettings(mode = OMR_CUSTOM) }
        }
    }
}

@Composable
private fun SubHeadSpaced(text: String) {
    Box(Modifier.padding(top = 6.dp)) { SubHead(text) }
}
