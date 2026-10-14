package com.intelliq.rifatplex.ui

import android.app.Activity
import com.intelliq.rifatplex.ui.theme.PIcons
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.intelliq.rifatplex.R
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.Folder as FolderData
import com.intelliq.rifatplex.data.HistoryStore
import com.intelliq.rifatplex.data.hasAnswerKey
import com.intelliq.rifatplex.data.PAGE_SIZES_MM
import com.intelliq.rifatplex.data.previewQuestions
import com.intelliq.rifatplex.data.previewSetCode
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IntelliQTheme
import com.intelliq.rifatplex.ui.theme.NotoSerifBengali
import com.intelliq.rifatplex.ui.theme.Poppins
import com.intelliq.rifatplex.ui.theme.Lato
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun IntelliQApp(store: AppStore, ai: AiViewModel) {
    val d = store.data
    val lang = if (d.language == "bn") Lang.BN else Lang.EN
    var tab by rememberSaveable { mutableStateOf(0) }
    var showAnswers by rememberSaveable { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMsg?>(null) }
    val show: (String, Boolean) -> Unit = { text, err -> toast = ToastMsg(text, err) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val history = remember { HistoryStore.get(context) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    /** Creates the PDF (or the ZIP of all sets) of [data] and saves it to Downloads. */
    val startDownload: (AppData, Boolean) -> Unit = start@{ data, answers ->
        if (exporting) return@start
        if (data.questions.none { !it.isEmpty() }) {
            show(trKey("download.noQuestions", lang), true)
            return@start
        }
        exporting = true
        scope.launch {
            try {
                val r = PdfExport.export(activity, data, answers)
                val saved = PdfExport.deliver(activity, r)
                val msg = when {
                    !saved -> "toast.pdfReady"
                    r.isZip -> "toast.zipSaved"
                    answers -> "toast.aSaved"
                    else -> "toast.qSaved"
                }
                show(trKey(msg, lang), false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(trKey("toast.pdfError", lang), true)
            } finally {
                exporting = false
            }
        }
    }
    val saveNow: () -> Unit = {
        store.flush()
        history.record(store.data)
        show(trKey("toast.savedSuccess", lang), false)
    }

    // result of a Gemini generation (also when the dialog was closed while it was running)
    val aiEvent = ai.event
    LaunchedEffect(aiEvent?.key) {
        val ev = ai.event ?: return@LaunchedEffect
        val err = ev.error
        if (err != null) {
            show(aiToastText(err, lang), true)
        } else {
            var msg = countText(ev.count, lang, "mcq.countSingular", "mcq.countPlural")
            if (ev.usedModel != ev.requestedModel) {
                msg += " · " + trKey("toast.modelFallback", lang).replace("{used}", ev.usedModel)
            }
            show(msg, false)
        }
        ai.consumeEvent()
    }

    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450)
        splash = false
    }

    IntelliQTheme(d.dark) {
        val c = IQ.c
        SystemBars(d.dark)
        CompositionLocalProvider(LocalLang provides lang, LocalChemAuto provides d.settings.chemAuto) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 4.dp)
                ) {
                    AppHeader(
                        dark = d.dark,
                        onToggleTheme = { store.setDark(!d.dark) },
                        onAccount = { show(trKey("todo.next", lang), false) },
                        onHistory = { historyOpen = true },
                        onMenu = { drawerOpen = true },
                    )
                    MobileTabs(tab) { tab = it }
                    val panelShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .shadow(1.dp, panelShape)
                            .clip(panelShape)
                            .background(if (tab == 0) c.panel else c.panelSoft)
                            .border(1.dp, c.glassBorder, panelShape)
                    ) {
                        if (tab == 0) {
                            InputPanel(d, store, history, lang, show, onAi = { ai.openModal() })
                        } else {
                            PreviewPanel(
                                d = d, store = store, lang = lang,
                                showAnswers = showAnswers,
                                onShowAnswers = { showAnswers = it },
                                onToast = show,
                                onSave = saveNow,
                                onDownload = { answers ->
                                    history.record(store.data)
                                    startDownload(store.data, answers)
                                },
                            )
                        }
                    }
                }

                SettingsDrawer(
                    open = drawerOpen,
                    onClose = { drawerOpen = false },
                    d = d,
                    store = store,
                    lang = lang,
                    onSave = saveNow,
                    onReset = {
                        store.resetDocument()
                        show(trKey("toast.documentReset", lang), false)
                    },
                    onGenerated = { n -> show(countText(n, lang, "set.countSingular", "set.countPlural"), false) },
                    onToast = show,
                )
                AiModal(ai, store)
                HistoryModal(
                    open = historyOpen,
                    onClose = { historyOpen = false },
                    history = history,
                    lang = lang,
                    onEdit = { e ->
                        store.loadSnapshot(e.state)
                        historyOpen = false
                        tab = 0
                        show(trKey("history.loadedToast", lang), false)
                    },
                    onDownload = { e, answers -> startDownload(e.state, answers) },
                    onToast = show,
                )
                if (exporting) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                    ) {
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.clip(shape).background(c.panel).border(1.dp, c.glassBorder, shape).padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = c.primary, strokeWidth = 2.5.dp)
                            Text(tr("download.working"), color = c.text, fontSize = 13.5.sp, fontFamily = uiFont())
                        }
                    }
                }
                ToastHost(toast) { toast = null }
                AnimatedVisibility(splash, exit = fadeOut(tween(250))) { SplashScreenView() }
            }
        }
    }
}

/** Logo + "IntelliQ" while the app starts; sits right after the system splash, so there is no blank screen. */
@Composable
private fun SplashScreenView() {
    Box(Modifier.fillMaxSize().background(IQ.c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.logo), "IntelliQ logo", Modifier.size(96.dp))
            BasicText(
                "IntelliQ",
                style = TextStyle(
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.45).sp,
                    brush = Brush.verticalGradient(listOf(Color(0xFF05B3FC), Color(0xFF0C83F7))),
                ),
            )
        }
    }
}

@Composable
private fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

/* ───────────── Header ───────────── */

@Composable
private fun AppHeader(
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onAccount: () -> Unit,
    onHistory: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = IQ.c
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(c.panel)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(c.glassBorder, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(R.drawable.logo), "IntelliQ logo", Modifier.size(40.dp))
            BasicText(
                "IntelliQ",
                style = TextStyle(
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.27).sp,
                    brush = Brush.verticalGradient(listOf(Color(0xFF05B3FC), Color(0xFF0C83F7))),
                ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SquareBtn(42.dp, onToggleTheme) {
                Icon(
                    if (dark) PIcons.DarkMode else PIcons.LightMode,
                    null, Modifier.size(18.dp), tint = c.textSecondary,
                )
            }
            SquareBtn(42.dp, onAccount) { Icon(PIcons.AccountCircle, null, Modifier.size(20.dp), tint = c.textSecondary) }
            SquareBtn(42.dp, onHistory) { Icon(PIcons.History, null, Modifier.size(18.dp), tint = c.textSecondary) }
            SquareBtn(42.dp, onMenu) { Icon(PIcons.Menu, null, Modifier.size(20.dp), tint = c.textSecondary) }
        }
    }
}

/* ───────────── Tabs ───────────── */

@Composable
private fun MobileTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
    ) {
        TabBtn(PIcons.Edit, tr("tabs.input"), selected == 0, Modifier.weight(1f)) { onSelect(0) }
        TabBtn(PIcons.Visibility, tr("tabs.preview"), selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun TabBtn(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val color = if (active) c.primary else c.textFaint
    Column(modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) c.primary else Color.Transparent))
    }
}

/* ───────────── Input panel ───────────── */

@Composable
private fun InputPanel(d: AppData, store: AppStore, history: HistoryStore, lang: Lang, onToast: (String, Boolean) -> Unit, onAi: () -> Unit) {
    val c = IQ.c
    var focusedId by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 64.dp)
    ) {
        // top bar: language switch + AI Mode
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LangSwitch(lang) { store.setLanguage(if (it == Lang.BN) "bn" else "en") }
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.primary)
                    .clickable { onAi() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PIcons.AutoAwesome, null, Modifier.size(15.dp), tint = Color.White)
                Text(tr("topbar.aiMode"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
            }
        }

        // header card
        val cardShape = RoundedCornerShape(8.dp)
        Column(
            Modifier
                .padding(bottom = 20.dp)
                .fillMaxWidth()
                .shadow(1.dp, cardShape)
                .clip(cardShape)
                .background(c.panel)
                .border(1.dp, c.glassBorder, cardShape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            FolderField(
                selectedId = d.folderId,
                folders = history.folders,
                onSelect = { store.setFolder(it) },
                onCreate = { name ->
                    val f = history.createFolder(name)
                    store.setFolder(f.id)
                    onToast(trKey("folder.createdToast", lang), false)
                },
            )
            Gap(16)
            Field(tr("field.examTitle"), d.doc.title, { store.setDoc { f -> f.copy(title = it) } }, tr("ph.title"))
            Gap(16)
            Field(tr("field.subtitle"), d.doc.subtitle, { store.setDoc { f -> f.copy(subtitle = it) } }, tr("ph.subtitle"))
            Gap(16)
            Field(tr("field.marks"), d.doc.marks, { store.setDoc { f -> f.copy(marks = it) } }, tr("ph.marks"))
            Gap(12)
            Field(tr("field.time"), d.doc.time, { store.setDoc { f -> f.copy(time = it) } }, tr("ph.time"))
            Gap(16)
            Field(tr("field.note"), d.doc.note, { store.setDoc { f -> f.copy(note = it) } }, tr("ph.note"), minLines = 2)
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp).height(1.dp).background(c.border))

        // questions heading + count
        val count = d.questions.count { !it.isEmpty() }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(tr("questions.heading"), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            Text(
                countText(count, lang, "question.countSingular", "question.countPlural"),
                color = c.textFaint, fontSize = 12.sp, fontFamily = uiFont(),
            )
        }

        // question cards
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            d.questions.forEachIndexed { i, q ->
                key(q.id) {
                    QuestionCard(
                        index = i, q = q, lang = lang, store = store,
                        focused = focusedId == q.id,
                        onFocus = { focusedId = q.id },
                        onToast = { onToast(it, false) },
                    )
                }
            }
        }

        // footer
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
                .drawBehind {
                    drawLine(c.border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }
                .padding(top = 20.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("©IntelliQ • Rifatplex • 2026", color = c.textFaint, fontSize = 12.sp, fontFamily = Lato)
        }
    }
}

@Composable
private fun Gap(dp: Int) {
    Box(Modifier.height(dp.dp))
}

@Composable
private fun LangSwitch(lang: Lang, onLang: (Lang) -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.glassBorder, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LangBtn("English", lang == Lang.EN, Lato) { onLang(Lang.EN) }
        LangBtn("বাংলা", lang == Lang.BN, NotoSerifBengali) { onLang(Lang.BN) }
    }
}

@Composable
private fun LangBtn(label: String, active: Boolean, family: FontFamily, onClick: () -> Unit) {
    val c = IQ.c
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .then(if (active) Modifier.shadow(1.dp, shape) else Modifier)
            .clip(shape)
            .background(if (active) c.panel else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (active) c.primary else c.textSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = family,
        )
    }
}

@Composable
private fun FolderField(
    selectedId: String?,
    folders: List<FolderData>,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
) {
    val c = IQ.c
    val shape = RoundedCornerShape(4.dp)
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val selected = folders.firstOrNull { it.id == selectedId }
    Column {
        FieldLabel(tr("field.folder"))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.field)
                    .border(1.dp, c.glassBorder, shape)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(PIcons.Folder, null, Modifier.size(16.dp), tint = c.primary)
                Text(
                    selected?.name ?: tr("field.ungrouped"),
                    Modifier.weight(1f),
                    color = if (selected != null) c.text else c.textSecondary, fontSize = 13.5.sp, fontFamily = uiFont(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(PIcons.KeyboardArrowDown, null, Modifier.size(15.dp), tint = c.textFaint)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    leadingIcon = { Icon(PIcons.Inbox, null, Modifier.size(17.dp), tint = c.textSecondary) },
                    text = {
                        Text(
                            tr("folder.ungroup"),
                            color = if (selected == null) c.primary else c.text, fontSize = 13.5.sp, fontFamily = uiFont(),
                        )
                    },
                    onClick = { open = false; onSelect(null) },
                )
                folders.forEach { f ->
                    DropdownMenuItem(
                        leadingIcon = { Icon(PIcons.Folder, null, Modifier.size(17.dp), tint = c.textSecondary) },
                        text = {
                            Text(
                                f.name,
                                color = if (f.id == selectedId) c.primary else c.text, fontSize = 13.5.sp, fontFamily = uiFont(),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = { open = false; onSelect(f.id) },
                    )
                }
                DropdownMenuItem(
                    leadingIcon = { Icon(PIcons.CreateNewFolder, null, Modifier.size(17.dp), tint = c.primary) },
                    text = { Text(tr("folder.newFolder"), color = c.primary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont()) },
                    onClick = { open = false; creating = true },
                )
            }
        }
    }
    if (creating) {
        NameDialog(
            title = tr("folder.newFolder"),
            initial = "",
            confirmLabel = tr("folder.create"),
            onConfirm = { creating = false; onCreate(it) },
            onDismiss = { creating = false },
        )
    }
}

/* ───────────── Preview panel ───────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewPanel(
    d: AppData,
    store: AppStore,
    lang: Lang,
    showAnswers: Boolean,
    onShowAnswers: (Boolean) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDownload: (Boolean) -> Unit,
) {
    val c = IQ.c
    var dlMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        // toolbar
        FlowRow(
            Modifier
                .fillMaxWidth()
                .background(c.panel)
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val zs = RoundedCornerShape(8.dp)
            Row(
                Modifier
                    .clip(zs)
                    .background(c.panelSoft)
                    .border(1.dp, c.border, zs)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SquareBtn(36.dp, { store.setZoom(d.zoom - 10) }) { Icon(PIcons.Remove, null, Modifier.size(17.dp), tint = c.textSecondary) }
                Text(
                    "${d.zoom}%",
                    Modifier.widthIn(min = 52.dp).clickable { store.setZoom(100) }.padding(horizontal = 4.dp, vertical = 6.dp),
                    color = c.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, fontFamily = Lato,
                )
                SquareBtn(36.dp, { store.setZoom(d.zoom + 10) }) { Icon(PIcons.Add, null, Modifier.size(17.dp), tint = c.textSecondary) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (d.setCode.enabled && d.generatedSets.isNotEmpty()) {
                    Select(
                        value = d.activeSetIndex.toString(),
                        options = d.generatedSets.mapIndexed { i, s -> i.toString() to "${trKey("doc.set", lang)} ${s.code}" },
                        onSelect = { store.setActiveSet(it.toInt()) },
                        minWidth = 90.dp,
                    )
                }
                Box {
                    // like the website: with an answer key a menu opens, otherwise the question paper downloads directly
                    SquareBtn(36.dp, { if (d.hasAnswerKey()) dlMenu = true else onDownload(false) }) {
                        Icon(PIcons.Download, null, Modifier.size(17.dp), tint = c.textSecondary)
                    }
                    DropdownMenu(expanded = dlMenu, onDismissRequest = { dlMenu = false }) {
                        MenuEntry(PIcons.Download, tr("download.questionsOnly"), tr("download.questionsOnlySub")) {
                            dlMenu = false; onDownload(false)
                        }
                        MenuEntry(PIcons.Download, tr("download.answersOnly"), tr("download.answersOnlySub")) {
                            dlMenu = false; onDownload(true)
                        }
                    }
                }
                MenuBtn(PIcons.Visibility) { close ->
                    MenuEntry(PIcons.Visibility, tr("preview.question"), tr("preview.questionSub"), !showAnswers) {
                        close(); onShowAnswers(false)
                    }
                    MenuEntry(PIcons.Visibility, tr("preview.answer"), tr("preview.answerSub"), showAnswers) {
                        close(); onShowAnswers(true)
                    }
                }
                SquareBtn(36.dp, onSave) { Icon(PIcons.Save, null, Modifier.size(17.dp), tint = c.textSecondary) }
                SquareBtn(36.dp, {
                    store.resetDocument()
                    onToast(trKey("toast.documentReset", lang), false)
                }) { Icon(PIcons.RestartAlt, null, Modifier.size(17.dp), tint = c.textSecondary) }
            }
        }

        // workspace
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val size = PAGE_SIZES_MM[d.settings.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
            val pw = size.first * 96f / 25.4f
            val ph = size.second * 96f / 25.4f
            val fit = minOf((maxWidth.value - 16f - 40f) / pw, (maxHeight.value - 16f - 40f) / ph, 1.2f)
                .coerceAtLeast(0.1f)
            val scale = d.zoom / 100f * fit
            val paperContainerWidth = maxWidth - 16.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                PaperPages(
                    questions = d.previewQuestions(),
                    setCode = d.previewSetCode(),
                    doc = d.doc,
                    lang = lang,
                    settings = d.settings,
                    showAnswers = showAnswers,
                    scale = scale,
                    containerWidth = paperContainerWidth,
                )
            }
        }
    }
}

@Composable
private fun MenuBtn(icon: ImageVector, items: @Composable (close: () -> Unit) -> Unit) {
    val c = IQ.c
    var open by remember { mutableStateOf(false) }
    Box {
        SquareBtn(36.dp, { open = true }) { Icon(icon, null, Modifier.size(17.dp), tint = c.textSecondary) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, title: String, sub: String, active: Boolean = false, onClick: () -> Unit) {
    val c = IQ.c
    DropdownMenuItem(
        leadingIcon = { Icon(icon, null, Modifier.size(17.dp), tint = if (active) c.primary else c.textSecondary) },
        text = {
            Column {
                Text(title, color = if (active) c.primary else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont())
                Text(sub, color = c.textFaint, fontSize = 11.5.sp, fontFamily = uiFont())
            }
        },
        onClick = onClick,
    )
}

private fun Context.findActivity(): Activity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    error("IntelliQ needs an Activity context")
}
