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
import androidx.compose.foundation.shape.RoundedCornerShape
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
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
                    ) {
                        PageSection(d, store)
                        FontSection(d, store)
                        SetCodeSection(d, store, lang, onGenerated, onToast)
                        AiSection(d, store, lang)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind { drawLine(c.border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IQButton(trKey("common.reset", lang), BtnKind.Ghost, Modifier.weight(1f)) { onReset() }
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

@Composable
private fun Section(content: @Composable () -> Unit) {
    val c = IQ.c
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(vertical = 18.dp)
    ) { content() }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = IQ.c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        Box(Modifier.padding(start = 12.dp)) { content() }
    }
}

@Composable
private fun PageSection(d: AppData, store: AppStore) {
    val c = IQ.c
    Section {
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
        SettingRow(tr("settings.chemAuto")) {
            IQSwitch(d.settings.chemAuto) { on -> store.setSettings { s -> s.copy(chemAuto = on) } }
        }
        if (tr("settings.chemAutoHint").isNotBlank()) {
            Text(
                tr("settings.chemAutoHint"), color = IQ.c.textSecondary, fontSize = 12.sp, fontFamily = uiFont(),
                modifier = Modifier.padding(bottom = 4.dp),
            )
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
            SettingRow(tr("settings.randomization")) {
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
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
        Text(tr("settings.geminiKey"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(), modifier = Modifier.padding(bottom = 12.dp))
        InputBox(d.ai.apiKey, { store.setAi { a -> a.copy(apiKey = it.trim()) } }, tr("ph.geminiKey"), singleLine = true)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("settings.geminiKeyHint"), color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont())
            Text(
                "aistudio.google.com/apikey",
                color = c.primaryInk, fontSize = 12.sp, fontFamily = Lato,
                modifier = Modifier.clickable { uri.openUri("https://aistudio.google.com/apikey") },
            )
        }
        Box(Modifier.height(10.dp))
        SettingRow(tr("settings.model")) {
            Select(
                d.ai.model,
                listOf(
                    "gemini-3-flash-preview" to tr("settings.modelRecommended"),
                    "gemini-3.1-flash-lite" to tr("settings.modelFastest"),
                    "gemini-2.5-flash-lite" to tr("model.25lite"),
                    "gemini-2.0-flash" to tr("model.20flash"),
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
