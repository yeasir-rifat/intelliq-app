package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import android.app.Activity
import com.intelliq.rifatplex.ui.theme.PIcons
import com.intelliq.rifatplex.ui.theme.TIcons
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
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
import com.intelliq.rifatplex.data.AiSync
import com.intelliq.rifatplex.data.AnswerKeyStore
import com.intelliq.rifatplex.data.CloudSync
import com.intelliq.rifatplex.data.HistoryStore
import com.intelliq.rifatplex.data.hasAnswerKey
import com.intelliq.rifatplex.data.hasMcq
import com.intelliq.rifatplex.data.hasMcqKey
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
fun IntelliQApp(store: AppStore, ai: AiViewModel, aiEdit: AiEditViewModel) {
    val d = store.data
    val lang = if (d.language == "bn") Lang.BN else Lang.EN
    val pager = rememberPagerState(pageCount = { 2 })
    val tab = pager.targetPage          // 0 = Input, 1 = Preview (follows the swipe)
    val inputScroll = rememberScrollState()
    val previewV = rememberScrollState()
    val previewH = rememberScrollState()
    var qtab by rememberSaveable { mutableStateOf(TAB_MIX) }
    var devOpen by rememberSaveable { mutableStateOf(false) }
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    var previewKind by rememberSaveable { mutableStateOf(DocKind.QUESTION) } // which document the preview shows
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMsg?>(null) }
    val show: (String, Boolean) -> Unit = { text, err -> toast = ToastMsg(text, err) }
    // toast with a tappable action, used by AI Edit ("Question updated" + Undo)
    val showAction: (String, String, () -> Unit) -> Unit = { text, label, act -> toast = ToastMsg(text, false, actionLabel = label, onAction = act) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val history = remember { HistoryStore.get(context) }
    val cloud = remember { CloudSync.get(context) }
    val keys = remember { AnswerKeyStore.get(context) } // answer snapshots for the OMR check (step 4.3)

    // Step 7: Google account + Firestore sync. The Gemini key / model / insert mode follow the account (same fields as the website).
    LaunchedEffect(Unit) {
        cloud.start(
            history,
            keys,
            readAi = { store.data.ai.let { AiSync(it.apiKey, it.model, it.insertMode) } },
            applyAi = { a -> store.updateAi { it.copy(apiKey = a.apiKey, model = a.model, insertMode = a.mode) } },
            ownerChanged = { store.updateAi { it.copy(apiKey = "") } },
        )
    }
    LaunchedEffect(d.ai.apiKey, d.ai.model, d.ai.insertMode, cloud.user?.uid) {
        delay(800)
        cloud.pushAi(AiSync(d.ai.apiKey, d.ai.model, d.ai.insertMode))
    }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    /** Creates the PDF file (or the ZIP of all sets) of [data] and saves it to Downloads. */
    val startDownload: (AppData, DocKind) -> Unit = start@{ data, kind ->
        if (exporting) return@start
        if (data.questions.none { !it.isEmpty() }) {
            show(trKey("download.noQuestions", lang), true)
            return@start
        }
        if (kind == DocKind.OMR && !data.hasMcq()) {
            show(trKey("download.noMcq", lang), true)
            return@start
        }
        if (kind == DocKind.KEY && !data.hasMcqKey()) {
            show(trKey("download.noMcqKey", lang), true)
            return@start
        }
        exporting = true
        scope.launch {
            try {
                // step 4.3: the answers of the sheets being printed are frozen first, so scanning them later knows the right options
                val cap = if (kind == DocKind.OMR || kind == DocKind.KEY) {
                    try {
                        AnswerKeyCapture.capture(activity, data, keys)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null // the PDF must still be made
                    }
                } else null
                val r = PdfExport.export(activity, data, kind)
                val saved = PdfExport.deliver(activity, r)
                val msg = when {
                    !saved -> "toast.pdfReady"
                    r.isZip -> "toast.zipSaved"
                    kind == DocKind.ANSWER -> "toast.aSaved"
                    kind == DocKind.OMR -> "toast.omrSaved"
                    kind == DocKind.KEY -> "toast.keySheetSaved"
                    else -> "toast.qSaved"
                }
                val warn = if (kind == DocKind.OMR && cap != null) keyWarningText(cap, lang) else ""
                show(trKey(msg, lang) + warn, false)
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
        // step 4.3: keep the answer snapshot of the saved exam (quietly; the export is what warns the teacher)
        val saved = store.data
        scope.launch {
            try {
                AnswerKeyCapture.capture(activity, saved, keys)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // a snapshot that could not be made must never disturb saving
            }
        }
    }

    // result of a Gemini generation (also when the dialog was closed while it was running)
    val aiEvent = ai.event
    LaunchedEffect(aiEvent?.key) {
        val ev = ai.event ?: return@LaunchedEffect
        val err = ev.error
        if (err != null) {
            show(aiToastText(err, lang), true)
        } else {
            var msg = trKey("ai.added", lang).replace("{n}", ev.count.toString()) + (if (ev.summary.isNotEmpty()) " " + ev.summary else "")
            if (ev.usedModel != ev.requestedModel) {
                msg += " · " + trKey("toast.modelFallback", lang).replace("{used}", ev.usedModel)
            }
            show(msg, false)
        }
        ai.consumeEvent()
    }

    // the only splash the user sees: the system splash shows just the background colour (its icon is transparent)
    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450)
        splash = false
    }

    IntelliQTheme(d.dark) {
        val c = IQ.c
        SystemBars(d.dark)
        CompositionLocalProvider(LocalLang provides lang, LocalChemAuto provides d.settings.chemAuto) {
            // edge-to-edge: the app fills the whole display. The colour behind the gesture/nav bar follows the page.
            Box(Modifier.fillMaxSize().background(if (pager.currentPage == 0) c.panel else c.panelSoft)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // navbar + tabs are ONE block; its background also fills the status-bar area (no gap on top)
                    Column(Modifier.fillMaxWidth().background(c.panel).statusBarsPadding()) {
                        AppHeader(
                            dark = d.dark,
                            onToggleTheme = { store.setDark(!d.dark) },
                            account = cloud.user,
                            onAccount = { accountOpen = true },
                            onLogo = { devOpen = true },
                            onHistory = { historyOpen = true },
                            onMenu = { drawerOpen = true },
                        )
                        MobileTabs(tab, { pager.currentPage + pager.currentPageOffsetFraction }) { page -> scope.launch { pager.animateScrollToPage(page) } }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize(),
                            // no swipe while text is selected or an overlay is open (website: hasSel() / blocked())
                            userScrollEnabled = !SwipeGate.selecting && !drawerOpen && !historyOpen,
                        ) { page ->
                            Box(Modifier.fillMaxSize().background(if (page == 0) c.panel else c.panelSoft)) {
                                if (page == 0) {
                                    InputPanel(
                                        d, store, history, lang, show,
                                        onAi = { t -> ai.openModal(t.takeIf { it != TAB_MIX }) },
                                        onAiEdit = { qid, num ->
                                            val target = store.data.questions.firstOrNull { it.id == qid }
                                            if (target == null || target.isEmpty()) show(trKey("aie.errEmpty", lang), true) else aiEdit.open(qid, num)
                                        },
                                        scroll = inputScroll, qtab = qtab, onQtab = { qtab = it },
                                    )
                                } else {
                                    PreviewPanel(
                                        d = d, store = store, lang = lang,
                                        kind = previewKind,
                                        onKind = { previewKind = it },
                                        onToast = show,
                                        onSave = saveNow,
                                        onDownload = { kind ->
                                            history.record(store.data)
                                            startDownload(store.data, kind)
                                        },
                                        vScroll = previewV, hScroll = previewH,
                                    )
                                }
                            }
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
                        store.resetDocument(keepBrand = false)
                        show(trKey("toast.documentReset", lang), false)
                    },
                    onGenerated = { n -> show(countText(n, lang, "set.countSingular", "set.countPlural"), false) },
                    onToast = show,
                )
                AiModal(ai, store)
                AiEditDialog(aiEdit, store, lang, onUndoToast = showAction, onToast = show)
                HistoryModal(
                    open = historyOpen,
                    onClose = { historyOpen = false },
                    history = history,
                    lang = lang,
                    onEdit = { e ->
                        store.loadSnapshot(e.state)
                        historyOpen = false
                        scope.launch { pager.animateScrollToPage(0) }
                        show(trKey("history.loadedToast", lang), false)
                    },
                    onDownload = { e, kind -> startDownload(e.state, kind) },
                    onToast = show,
                )
                if (devOpen) DevInfoDialog { devOpen = false }
                if (accountOpen) AccountDialog(cloud, activity, { accountOpen = false }, show)
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

/** Logo + "IntelliQ" while the app starts; is the only splash; the system splash behind it shows just the background colour. */
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
    onLogo: () -> Unit,
    onToggleTheme: () -> Unit,
    account: com.intelliq.rifatplex.data.CloudUser?,
    onAccount: () -> Unit,
    onHistory: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = IQ.c
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(R.drawable.logo), "IntelliQ logo", Modifier.size(40.dp).clip(IQD.ctl).clickable(onClick = onLogo))
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
            SquareBtn(42.dp, onAccount) {
                if (account != null) Avatar(account, 26.dp)
                else Icon(PIcons.AccountCircle, null, Modifier.size(20.dp), tint = c.textSecondary)
            }
            SquareBtn(42.dp, onHistory) { Icon(PIcons.History, null, Modifier.size(18.dp), tint = c.textSecondary) }
            SquareBtn(42.dp, onMenu) { Icon(PIcons.Menu, null, Modifier.size(20.dp), tint = c.textSecondary) }
        }
    }
}

/* ───────────── Tabs ───────────── */

@Composable
private fun MobileTabs(selected: Int, progress: () -> Float, onSelect: (Int) -> Unit) {
    val c = IQ.c
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            TabBtn(PIcons.Edit, tr("tabs.input"), selected == 0, Modifier.weight(1f)) { onSelect(0) }
            TabBtn(PIcons.Visibility, tr("tabs.preview"), selected == 1, Modifier.weight(1f)) { onSelect(1) }
        }
        // the indicator slides with the finger while swiping
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.border)) {
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(2.dp)
                    .graphicsLayer { translationX = progress().coerceIn(0f, 1f) * size.width }
                    .background(c.primary)
            )
        }
    }
}

@Composable
private fun TabBtn(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val color by animateColorAsState(if (active) c.primaryInk else c.textFaint, tween(150), label = "tabColor")
    Row(
        modifier.clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
    }
}

/* ───────────── Input panel ───────────── */

@Composable
private fun InputPanel(
    d: AppData, store: AppStore, history: HistoryStore, lang: Lang,
    onToast: (String, Boolean) -> Unit, onAi: (String) -> Unit,
    onAiEdit: (String, Int) -> Unit,
    scroll: ScrollState, qtab: String, onQtab: (String) -> Unit,
) {
    val c = IQ.c
    var focusedId by rememberSaveable { mutableStateOf("") }
    // the empty card at the end follows the active type tab (website syncTrailing)
    LaunchedEffect(qtab) { store.setTrailingTab(qtab) }

    // ── drag to reorder (website `iq-question-drag`) ──
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val items = buildCardItems(d, qtab)
    val arranged = isArranged(d)
    val gapPx = with(density) { 12.dp.toPx() }
    val edgePx = with(density) { 80.dp.toPx() }
    val handlePx = with(density) { 28.dp.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val heights = remember { HashMap<String, Int>() }
    val tops = remember { HashMap<String, Float>() }
    val dragInfo = remember { floatArrayOf(0f, 0f, 0f) }   // card top on screen at start, finger travel, unused
    val viewport = remember { floatArrayOf(0f, 0f) }        // scroll area top / bottom in the window
    val swapped = remember { booleanArrayOf(false) }        // one swap per composition (items list is stale until then)
    SideEffect { swapped[0] = false }
    val step = rememberUpdatedState<() -> Unit> {
        val id = dragId
        if (id != null && !swapped[0]) {
            val pos = items.indexOfFirst { it.q.id == id }
            if (pos >= 0) {
                if (dragDy > 0f) {
                    val nb = items.neighbour(pos, 1, arranged)
                    val h = if (nb != null) heights[nb.q.id] ?: 0 else 0
                    if (nb != null && h > 0 && dragDy > h / 2f) {
                        swapped[0] = true
                        store.swapQuestions(id, nb.q.id)
                        dragDy -= (h + gapPx)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } else if (dragDy < 0f) {
                    val nb = items.neighbour(pos, -1, arranged)
                    val h = if (nb != null) heights[nb.q.id] ?: 0 else 0
                    if (nb != null && h > 0 && -dragDy > h / 2f) {
                        swapped[0] = true
                        store.swapQuestions(id, nb.q.id)
                        dragDy += (h + gapPx)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
        }
    }
    // A long or fast pull crosses several cards at once, but only one swap fits in a composition.
    // After every reorder, check again so the card keeps hopping over each card it has passed and the
    // numbers follow it live (2 -> 3 -> 4 -> 5) instead of the card snapping back when it is released.
    val orderKey = items.map { it.q.id }
    LaunchedEffect(orderKey, dragId) {
        if (dragId != null) step.value()
    }

    // scroll the list while the card is held near the top / bottom edge
    LaunchedEffect(dragId) {
        while (true) {
            if (dragId == null) break
            val pointerY = dragInfo[0] + dragInfo[1] + handlePx
            var move = 0f
            if (viewport[1] > viewport[0]) {
                if (pointerY > viewport[1] - edgePx) move = 18f
                else if (pointerY < viewport[0] + edgePx) move = -18f
            }
            if (move != 0f) {
                val used = scroll.scrollBy(move)
                if (used != 0f) {
                    dragDy += used
                    step.value()
                }
            }
            delay(16)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val b = it.boundsInWindow()
                viewport[0] = b.top; viewport[1] = b.bottom
            }
            .verticalScroll(scroll)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp)
    ) {
        // top bar: language switch + AI Mode
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LangSwitch(lang) { store.setLanguage(if (it == Lang.BN) "bn" else "en") }
            Row(
                Modifier
                    .clip(IQD.ctl)
                    .background(c.primary)
                    .clickable { onAi(qtab) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PIcons.AutoAwesome, null, Modifier.size(17.dp), tint = Color.White)
                Text(tr("topbar.aiMode"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            }
        }

        // paper details: a bordered card with the same surface as the question cards below it
        // (the language row above already leaves 14dp, so no extra gap here)
        Box {
            SectionCard(tr("paper.details"), PIcons.Description, raised = true) {
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
                Field(tr("field.examTitle"), d.doc.title, { store.setDoc { f -> f.copy(title = it) } }, tr("ph.title"))
                Field(tr("field.subtitle"), d.doc.subtitle, { store.setDoc { f -> f.copy(subtitle = it) } }, tr("ph.subtitle"))
                Field(tr("field.chapter"), d.doc.chapter, { store.setDoc { f -> f.copy(chapter = it) } }, tr("ph.chapter"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { Field(tr("field.marks"), d.doc.marks, { store.setDoc { f -> f.copy(marks = it) } }, tr("ph.marks")) }
                    Box(Modifier.weight(1f)) { Field(tr("field.time"), d.doc.time, { store.setDoc { f -> f.copy(time = it) } }, tr("ph.time")) }
                }
                Field(tr("field.note"), d.doc.note, { store.setDoc { f -> f.copy(note = it) } }, tr("ph.note"), minLines = 2)
            }
        }

        Gap(12)

        // type tabs (Mix / MCQ / CQ / T/F / Blanks / SQ / BQ), column bar and (Mix only) section bar
        val counts = remember(d.questions) {
            val m = HashMap<String, Int>()
            d.questions.forEach { q -> if (!q.isEmpty()) m[q.kind()] = (m[q.kind()] ?: 0) + 1 }
            m[TAB_MIX] = m.values.sum()
            m
        }
        TypeTabs(qtab, counts = counts) { onQtab(it) }
        Gap(12)
        ColsBar(qtab, d, store)
        if (qtab == TAB_MIX) {
            Gap(12)
            SectionBar(d, store, lang)
        }
        Gap(20)

        // questions heading + count
        val count = d.questions.count { !it.isEmpty() }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(tr("questions.heading"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
            Text(
                countText(count, lang, "question.countSingular", "question.countPlural"),
                color = c.textFaint, fontSize = 13.sp, fontFamily = uiFont(),
            )
        }

        if (count == 0) EmptyHint()

        // question cards (filtered by the active tab, grouped + numbered by section when the paper has 2+ types)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEachIndexed { pos, item ->
                key(item.q.id) {
                    val up = items.neighbour(pos, -1, arranged)
                    val down = items.neighbour(pos, 1, arranged)
                    val id = item.q.id
                    val isDrag = dragId == id
                    val dragMod = if (up != null || down != null) {
                        Modifier.pointerInput(id) {
                            detectDragGestures(
                                onDragStart = {
                                    dragInfo[0] = tops[id] ?: 0f
                                    dragInfo[1] = 0f
                                    dragDy = 0f
                                    dragId = id
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { dragId = null; dragDy = 0f },
                                onDragCancel = { dragId = null; dragDy = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragInfo[1] += amount.y
                                dragDy += amount.y
                                step.value()
                            }
                        }
                    } else null
                    Box(
                        Modifier
                            .zIndex(if (isDrag) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDrag) dragDy else 0f
                                val k = if (isDrag) 1.02f else 1f
                                scaleX = k; scaleY = k
                            }
                            .onGloballyPositioned {
                                heights[id] = it.size.height
                                tops[id] = it.positionInWindow().y
                            }
                    ) {
                        QuestionCard(
                            number = item.number,
                            q = item.q, lang = lang, store = store,
                            mix = qtab == TAB_MIX,
                            focused = focusedId == id,
                            canMoveUp = up != null,
                            canMoveDown = down != null,
                            onMoveUp = { up?.let { store.swapQuestions(id, it.q.id) } },
                            onMoveDown = { down?.let { store.swapQuestions(id, it.q.id) } },
                            onFocus = { focusedId = id },
                            onToast = { onToast(it, false) },
                            dragModifier = dragMod,
                            dragging = isDrag,
                            onAiEdit = { onAiEdit(id, item.number) },
                        )
                    }
                }
            }
        }

        // footer (no separator line above it, the spacing is enough)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("©IntelliQ • Rifatplex • 2026", color = c.textFaint, fontSize = 13.sp, fontFamily = Lato)
        }
    }
}

/** Shown while the paper has no question yet: a dashed card that points to typing or AI Mode. */
@Composable
private fun EmptyHint() {
    val c = IQ.c
    val line = c.borderStrong
    val dash = with(LocalDensity.current) { 6.dp.toPx() }
    val corner = with(LocalDensity.current) { 12.dp.toPx() }
    Column(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(IQD.card)
            .drawBehind {
                drawRoundRect(
                    line, cornerRadius = CornerRadius(corner),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            }
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp).clip(IQD.card).background(c.primarySoft), contentAlignment = Alignment.Center) {
            Icon(PIcons.AutoAwesome, null, Modifier.size(22.dp), tint = c.primary)
        }
        Text(tr("empty.title"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
        Text(
            tr("empty.sub"), color = c.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp,
            textAlign = TextAlign.Center, fontFamily = uiFont(),
        )
    }
}

@Composable
private fun Gap(dp: Int) {
    Box(Modifier.height(dp.dp))
}

@Composable
private fun LangSwitch(lang: Lang, onLang: (Lang) -> Unit) {
    val c = IQ.c
    val shape = IQD.card
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
    val shape = IQD.ctl
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
            color = if (active) c.primaryInk else c.textSecondary,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = family,
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
    val shape = IQD.ctl
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
                Icon(PIcons.Folder, null, Modifier.size(18.dp), tint = c.primary)
                Text(
                    selected?.name ?: tr("field.ungrouped"),
                    Modifier.weight(1f),
                    color = if (selected != null) c.text else c.textSecondary, fontSize = 14.5.sp, fontFamily = uiFont(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(PIcons.KeyboardArrowDown, null, Modifier.size(17.dp), tint = c.textFaint)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    leadingIcon = { Icon(PIcons.Inbox, null, Modifier.size(19.dp), tint = c.textSecondary) },
                    text = {
                        Text(
                            tr("folder.ungroup"),
                            color = if (selected == null) c.primaryInk else c.text, fontSize = 14.5.sp, fontFamily = uiFont(),
                        )
                    },
                    onClick = { open = false; onSelect(null) },
                )
                folders.forEach { f ->
                    DropdownMenuItem(
                        leadingIcon = { Icon(PIcons.Folder, null, Modifier.size(19.dp), tint = c.textSecondary) },
                        text = {
                            Text(
                                f.name,
                                color = if (f.id == selectedId) c.primaryInk else c.text, fontSize = 14.5.sp, fontFamily = uiFont(),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = { open = false; onSelect(f.id) },
                    )
                }
                DropdownMenuItem(
                    leadingIcon = { Icon(PIcons.CreateNewFolder, null, Modifier.size(19.dp), tint = c.primary) },
                    text = { Text(tr("folder.newFolder"), color = c.primaryInk, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont()) },
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
    kind: DocKind,
    onKind: (DocKind) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDownload: (DocKind) -> Unit,
    vScroll: ScrollState,
    hScroll: ScrollState,
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
            val zs = IQD.card
            Row(
                Modifier
                    .clip(zs)
                    .background(c.panelSoft)
                    .border(1.dp, c.glassBorder, zs)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SquareBtn(36.dp, { store.setZoom(d.zoom - 10) }) { Icon(TIcons.ZoomOut, null, Modifier.size(20.dp), tint = c.textSecondary) }
                Text(
                    "${d.zoom}%",
                    Modifier.widthIn(min = 52.dp).clickable { store.setZoom(100) }.padding(horizontal = 4.dp, vertical = 6.dp),
                    color = c.textSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, fontFamily = Lato,
                )
                SquareBtn(36.dp, { store.setZoom(d.zoom + 10) }) { Icon(TIcons.ZoomIn, null, Modifier.size(20.dp), tint = c.textSecondary) }
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
                    // the menu always opens: question paper / answer paper / OMR sheet / answer grid as PDF
                    SquareBtn(36.dp, { dlMenu = true }) {
                        Icon(TIcons.Download, null, Modifier.size(20.dp), tint = c.textSecondary)
                    }
                    DropdownMenu(expanded = dlMenu, onDismissRequest = { dlMenu = false }) {
                        MenuEntry(TIcons.Download, tr("download.questionsOnly") + " · PDF", tr("download.questionsOnlySub")) {
                            dlMenu = false; onDownload(DocKind.QUESTION)
                        }
                        if (d.hasAnswerKey()) {
                            MenuEntry(TIcons.Download, tr("download.answersOnly") + " · PDF", tr("download.answersOnlySub")) {
                                dlMenu = false; onDownload(DocKind.ANSWER)
                            }
                        }
                        if (d.hasMcq()) {
                            MenuEntry(TIcons.Download, tr("download.omr") + " · PDF", tr("download.omrSub")) {
                                dlMenu = false; onDownload(DocKind.OMR)
                            }
                        }
                        if (d.hasMcqKey()) {
                            MenuEntry(TIcons.Download, tr("download.keySheet") + " · PDF", tr("download.keySheetSub")) {
                                dlMenu = false; onDownload(DocKind.KEY)
                            }
                        }
                    }
                }
                MenuBtn(TIcons.Eye) { close ->
                    MenuEntry(TIcons.Eye, tr("preview.question"), tr("preview.questionSub"), kind == DocKind.QUESTION) {
                        close(); onKind(DocKind.QUESTION)
                    }
                    MenuEntry(TIcons.Eye, tr("preview.answer"), tr("preview.answerSub"), kind == DocKind.ANSWER) {
                        close(); onKind(DocKind.ANSWER)
                    }
                    MenuEntry(TIcons.Eye, tr("preview.omr"), tr("preview.omrSub"), kind == DocKind.OMR) {
                        close(); onKind(DocKind.OMR)
                    }
                    MenuEntry(TIcons.Eye, tr("preview.keySheet"), tr("preview.keySheetSub"), kind == DocKind.KEY) {
                        close(); onKind(DocKind.KEY)
                    }
                }
                SquareBtn(36.dp, onSave) { Icon(TIcons.Save, null, Modifier.size(20.dp), tint = c.textSecondary) }
                SquareBtn(36.dp, {
                    store.resetDocument()
                    onToast(trKey("toast.documentReset", lang), false)
                }) { Icon(TIcons.Reset, null, Modifier.size(20.dp), tint = c.textSecondary) }
            }
        }

        // workspace
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val size = PAGE_SIZES_MM[kind.pageSizeKey(d.settings)] ?: PAGE_SIZES_MM.getValue("A4")
            val pw = size.first * 96f / 25.4f
            val ph = size.second * 96f / 25.4f
            val fit = minOf((maxWidth.value - 16f - 40f) / pw, (maxHeight.value - 16f - 40f) / ph, 1.2f)
                .coerceAtLeast(0.1f)
            val scale = d.zoom / 100f * fit
            val paperContainerWidth = maxWidth - 16.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
                    .padding(8.dp)
            ) {
                if (kind.isPaper) {
                    PaperPages(
                        questions = d.previewQuestions(),
                        setCode = d.previewSetCode(),
                        doc = d.doc,
                        lang = lang,
                        settings = d.settings,
                        brand = d.brand,
                        showAnswers = kind.showsAnswers,
                        scale = scale,
                        containerWidth = paperContainerWidth,
                    )
                } else {
                    SheetPages(
                        kind = kind,
                        questions = d.previewQuestions(),
                        setCode = d.previewSetCode(),
                        doc = d.doc,
                        settings = d.settings,
                        scale = scale,
                        containerWidth = paperContainerWidth,
                        brand = d.brand,
                        setCodes = if (d.setCode.enabled) d.generatedSets.map { it.code } else emptyList(),
                        examId = d.examId,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuBtn(icon: ImageVector, items: @Composable (close: () -> Unit) -> Unit) {
    val c = IQ.c
    var open by remember { mutableStateOf(false) }
    Box {
        SquareBtn(36.dp, { open = true }) { Icon(icon, null, Modifier.size(20.dp), tint = c.textSecondary) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, title: String, sub: String, active: Boolean = false, onClick: () -> Unit) {
    val c = IQ.c
    DropdownMenuItem(
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = if (active) c.primary else c.textSecondary) },
        text = {
            Column {
                Text(title, color = if (active) c.primaryInk else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont())
                if (sub.isNotBlank()) Text(sub, color = c.textFaint, fontSize = 12.5.sp, fontFamily = uiFont())
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
