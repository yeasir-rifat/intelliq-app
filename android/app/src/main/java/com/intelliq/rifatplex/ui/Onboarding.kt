package com.intelliq.rifatplex.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.R
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.Lato
import com.intelliq.rifatplex.ui.theme.PIcons
import com.intelliq.rifatplex.ui.theme.Poppins
import com.intelliq.rifatplex.ui.theme.SolaimanLipi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * First-launch intro: brand -> purpose -> features -> privacy. Every page can be skipped (top right) or stepped through (Continue).
 * Full screen overlay drawn by IntelliQApp above the editor; it follows the app theme (light / dark) and the EN / BN switch.
 * Texts live in OnboardingStrings.kt, the "seen" flag in data/OnboardingStore.kt.
 */

private const val OB_PAGES = 4

private val BrandTop = Color(0xFF05B3FC)
private val BrandBottom = Color(0xFF0C83F7)
private val Violet = Color(0xFF7C5CFC)
private val Rose = Color(0xFFE5487A)
private val Teal = Color(0xFF0EA5B7)
private val Green = Color(0xFF10B77F)
private val Amber = Color(0xFFF59E0B)
private val Blue = Color(0xFF146EF5)

@Composable
fun OnboardingScreen(lang: Lang, onLang: (Lang) -> Unit, onFinish: () -> Unit) {
    val c = IQ.c
    val pager = rememberPagerState(pageCount = { OB_PAGES })
    val scope = rememberCoroutineScope()
    val last = pager.targetPage == OB_PAGES - 1

    // back goes one page back first; on the first page it leaves the app like before
    BackHandler(enabled = pager.targetPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.targetPage - 1) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            // the overlay swallows taps so nothing underneath can be pressed
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Backdrop(pager)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // top bar: language on the left, Skip on the right (fades out on the last page)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LangPill(lang, onLang)
                val skipAlpha by animateFloatAsState(if (last) 0f else 1f, tween(200), label = "skip")
                Text(
                    tr("ob.skip"),
                    Modifier
                        .alpha(skipAlpha)
                        .clip(CircleShape)
                        .clickable(enabled = !last, onClick = onFinish)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = obFont(),
                )
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> BrandPage(pager)
                    1 -> PurposePage(pager)
                    2 -> FeaturesPage(pager)
                    else -> PrivacyPage(pager)
                }
            }

            // bottom: page dots + the main button
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Dots(pager)
                Spacer(Modifier.height(18.dp))
                MainButton(last = last, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                    if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
        }
    }
}

/* ───────────── pages ───────────── */

@Composable
private fun BrandPage(pager: PagerState) {
    val c = IQ.c
    PageBox {
        BrandHero(rememberReveal(pager, 0, 0))
        Spacer(Modifier.height(8.dp))
        BasicText(
            "IntelliQ",
            Modifier.reveal(rememberReveal(pager, 0, 1)),
            style = TextStyle(
                fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 42.sp, letterSpacing = (-0.6).sp,
                brush = Brush.verticalGradient(listOf(BrandTop, BrandBottom)),
            ),
        )
        Spacer(Modifier.height(14.dp))
        OText(tr("ob.tag"), 21, FontWeight.SemiBold, c.text, modifier = Modifier.reveal(rememberReveal(pager, 0, 2)), lh = 1.4f)
        Spacer(Modifier.height(10.dp))
        OText(tr("ob.tagSub"), 15, FontWeight.Normal, c.textSecondary, modifier = Modifier.reveal(rememberReveal(pager, 0, 3)), lh = 1.5f)
    }
}

@Composable
private fun PurposePage(pager: PagerState) {
    PageBox {
        PageHeader(pager, 1, tr("ob.p2.title"), tr("ob.p2.sub"))
        Spacer(Modifier.height(30.dp))
        StepRow("ob.s1", Glyph.PEN, Blue, false, rememberReveal(pager, 1, 2))
        StepRow("ob.s2", Glyph.DOC, Rose, false, rememberReveal(pager, 1, 3))
        StepRow("ob.s3", Glyph.CHECK, Green, true, rememberReveal(pager, 1, 4))
    }
}

private class Feature(val key: String, val glyph: Glyph, val accent: Color)

private val FEATURES = listOf(
    Feature("ob.f1", Glyph.SPARK, Violet),
    Feature("ob.f2", Glyph.TYPES, Blue),
    Feature("ob.f3", Glyph.DOC, Rose),
    Feature("ob.f4", Glyph.OMR, Green),
    Feature("ob.f5", Glyph.INST, Amber),
    Feature("ob.f6", Glyph.AA, Teal),
)

@Composable
private fun FeaturesPage(pager: PagerState) {
    PageBox {
        PageHeader(pager, 2, tr("ob.p3.title"), tr("ob.p3.sub"))
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FEATURES.chunked(2).forEachIndexed { row, pair ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEachIndexed { i, f ->
                        FeatureCard(f, rememberReveal(pager, 2, 2 + row * 2 + i), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPage(pager: PagerState) {
    val c = IQ.c
    val shape = RoundedCornerShape(16.dp)
    PageBox {
        PrivacyHero(rememberReveal(pager, 3, 0))
        Spacer(Modifier.height(10.dp))
        PageHeader(pager, 3, tr("ob.p4.title"), tr("ob.p4.sub"), firstIndex = 1)
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .reveal(rememberReveal(pager, 3, 3))
                .shadow(1.dp, shape)
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.glassBorder, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf("ob.p4.a", "ob.p4.b", "ob.p4.c", "ob.p4.d").forEach { k ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    GlyphTile(Glyph.CHECK, Green, 24.dp)
                    OText(tr(k), 14, FontWeight.Normal, c.text, TextAlign.Start, Modifier.weight(1f), lh = 1.5f)
                }
            }
        }
    }
}

/* ───────────── building blocks ───────────── */

/** The intro always uses SolaimanLipi for Bangla and Lato for English. */
@Composable
private fun obFont(): FontFamily = if (LocalLang.current == Lang.BN) SolaimanLipi else Lato

/** Centres a page vertically; scrolls when the screen is too small (large font sizes, split screen). */
@Composable
private fun PageBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val minH = maxHeight
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minH)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun PageHeader(pager: PagerState, page: Int, title: String, sub: String, firstIndex: Int = 0) {
    val c = IQ.c
    OText(title, 26, FontWeight.Bold, c.text, modifier = Modifier.reveal(rememberReveal(pager, page, firstIndex)), lh = 1.3f)
    Spacer(Modifier.height(8.dp))
    OText(sub, 15, FontWeight.Normal, c.textSecondary, modifier = Modifier.reveal(rememberReveal(pager, page, firstIndex + 1)), lh = 1.5f)
}

@Composable
private fun OText(
    text: String,
    size: Int,
    weight: FontWeight,
    color: Color,
    align: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
    lh: Float = 1.45f,
) {
    Text(
        text, modifier, color = color, fontSize = size.sp, fontWeight = weight, textAlign = align,
        lineHeight = (size * lh).sp, fontFamily = obFont(),
    )
}

/** Step of the "Create - Print - Check" timeline. Keys: <prefix>.n / .t / .d */
@Composable
private fun StepRow(prefix: String, glyph: Glyph, accent: Color, last: Boolean, p: State<Float>) {
    val c = IQ.c
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).reveal(p)) {
        Column(Modifier.width(52.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphTile(glyph, accent, 52.dp)
            if (!last) {
                Box(
                    Modifier
                        .padding(vertical = 6.dp)
                        .width(2.dp)
                        .weight(1f)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), c.border)))
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 26.dp)) {
            OText(tr("$prefix.t"), 19, FontWeight.Bold, c.text, TextAlign.Start, lh = 1.35f)
            Spacer(Modifier.height(4.dp))
            OText(tr("$prefix.d"), 14, FontWeight.Normal, c.textSecondary, TextAlign.Start, lh = 1.5f)
        }
    }
}

@Composable
private fun FeatureCard(f: Feature, p: State<Float>, modifier: Modifier) {
    val c = IQ.c
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .reveal(p)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.glassBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphTile(f.glyph, f.accent, 44.dp)
        OText(tr(f.key + ".t"), 15, FontWeight.SemiBold, c.text, TextAlign.Start, lh = 1.3f)
        Text(
            tr(f.key + ".d"), color = c.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Start,
            lineHeight = 17.sp, fontFamily = obFont(), minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Logo with soft rings and four floating feature badges around it. */
@Composable
private fun BrandHero(p: State<Float>) {
    val inf = rememberInfiniteTransition(label = "hero")
    val breathe = inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val logoShape = RoundedCornerShape(26.dp)
    Box(Modifier.size(300.dp, 250.dp).reveal(p), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension / 2f
            val b = breathe.value
            drawCircle(
                Brush.radialGradient(listOf(BrandTop.copy(alpha = 0.26f), Color.Transparent), center = center, radius = base * 0.95f),
                radius = base * 0.95f, center = center,
            )
            listOf(0.52f to 0.30f, 0.74f to 0.17f, 0.97f to 0.09f).forEach { (r, a) ->
                drawCircle(
                    BrandBottom.copy(alpha = a), radius = base * r * (1f + 0.025f * b), center = center,
                    style = Stroke(1.2.dp.toPx()),
                )
            }
        }
        Image(
            painterResource(R.drawable.logo), "IntelliQ logo",
            Modifier
                .size(108.dp)
                .graphicsLayer { val s = 1f + 0.03f * breathe.value; scaleX = s; scaleY = s }
                .shadow(20.dp, logoShape, ambientColor = Color(0x660C83F7), spotColor = Color(0x990C83F7))
                .clip(logoShape),
        )
        FloatChip(Glyph.SPARK, Violet, breathe, 1f, Modifier.align(Alignment.TopStart).offset(x = 38.dp, y = 22.dp))
        FloatChip(Glyph.DOC, Rose, breathe, -1f, Modifier.align(Alignment.TopEnd).offset(x = (-34).dp, y = 44.dp))
        FloatChip(Glyph.OMR, Teal, breathe, -1f, Modifier.align(Alignment.BottomStart).offset(x = 30.dp, y = (-34).dp))
        FloatChip(Glyph.CHECK, Green, breathe, 1f, Modifier.align(Alignment.BottomEnd).offset(x = (-42).dp, y = (-18).dp))
    }
}

@Composable
private fun FloatChip(glyph: Glyph, tint: Color, breathe: State<Float>, dir: Float, modifier: Modifier) {
    GlyphTile(
        glyph, tint, 50.dp,
        modifier.graphicsLayer { translationY = (breathe.value - 0.5f) * 12.dp.toPx() * dir },
    )
}

/** Padlock badge on a glowing disc (drawn, so no extra icon is needed). */
@Composable
private fun PrivacyHero(p: State<Float>) {
    val inf = rememberInfiniteTransition(label = "lock")
    val pulse = inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    Box(Modifier.size(150.dp).reveal(p), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension / 2f
            val b = pulse.value
            drawCircle(
                Brush.radialGradient(listOf(BrandTop.copy(alpha = 0.28f), Color.Transparent), center = center, radius = base),
                radius = base, center = center,
            )
            drawCircle(BrandBottom.copy(alpha = 0.16f - 0.06f * b), radius = base * (0.74f + 0.08f * b), center = center, style = Stroke(1.2.dp.toPx()))
        }
        Box(
            Modifier
                .size(88.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0x660C83F7), spotColor = Color(0x990C83F7))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(BrandTop, Color(0xFF146EF5)))),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(38.dp)) {
                val w = size.width
                val h = size.height
                val sw = w * 0.11f
                // shackle: arc + two legs
                drawArc(
                    Color.White, 180f, 180f, false,
                    topLeft = Offset(w * 0.27f, h * 0.08f), size = Size(w * 0.46f, w * 0.46f),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawLine(Color.White, Offset(w * 0.27f, h * 0.31f), Offset(w * 0.27f, h * 0.48f), sw, StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.73f, h * 0.31f), Offset(w * 0.73f, h * 0.48f), sw, StrokeCap.Round)
                // body
                drawRoundRect(Color.White, Offset(w * 0.14f, h * 0.44f), Size(w * 0.72f, h * 0.50f), CornerRadius(w * 0.13f))
                // keyhole
                drawCircle(BrandBottom, radius = w * 0.075f, center = Offset(w * 0.5f, h * 0.64f))
                drawLine(BrandBottom, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.79f), w * 0.075f, StrokeCap.Round)
            }
        }
    }
}

/** Two big soft colour glows behind everything; they drift sideways while the pages are swiped. */
@Composable
private fun Backdrop(pager: PagerState) {
    val dark = IQ.c.isDark
    Box(
        Modifier.fillMaxSize().drawBehind {
            val prog = pager.currentPage + pager.currentPageOffsetFraction
            val w = size.width
            val h = size.height
            val r1 = w * 0.95f
            val c1 = Offset(w * (0.10f + 0.12f * prog), h * 0.12f)
            drawCircle(
                Brush.radialGradient(listOf(BrandTop.copy(alpha = if (dark) 0.20f else 0.15f), Color.Transparent), center = c1, radius = r1),
                radius = r1, center = c1,
            )
            val r2 = w * 0.85f
            val c2 = Offset(w * (1.0f - 0.10f * prog), h * 0.94f)
            drawCircle(
                Brush.radialGradient(listOf(Violet.copy(alpha = if (dark) 0.16f else 0.10f), Color.Transparent), center = c2, radius = r2),
                radius = r2, center = c2,
            )
        }
    )
}

@Composable
private fun LangPill(lang: Lang, onLang: (Lang) -> Unit) {
    val c = IQ.c
    Row(
        Modifier.height(36.dp).clip(CircleShape).background(c.panel).border(1.dp, c.glassBorder, CircleShape).padding(3.dp),
    ) {
        LangSeg("EN", Lato, lang == Lang.EN) { onLang(Lang.EN) }
        LangSeg("বাংলা", SolaimanLipi, lang == Lang.BN) { onLang(Lang.BN) }
    }
}

@Composable
private fun LangSeg(label: String, font: FontFamily, selected: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    Box(
        Modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(if (selected) c.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) Color.White else c.textSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = font,
        )
    }
}

@Composable
private fun Dots(pager: PagerState) {
    val c = IQ.c
    val prog = pager.currentPage + pager.currentPageOffsetFraction
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(OB_PAGES) { i ->
            val f = (1f - kotlin.math.abs(prog - i)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .height(8.dp)
                    .width((8f + 18f * f).dp)
                    .clip(CircleShape)
                    .background(lerp(c.borderStrong, c.primary, f))
            )
        }
    }
}

@Composable
private fun MainButton(last: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src)
            .height(54.dp)
            .shadow(10.dp, shape, ambientColor = Color(0x55146EF5), spotColor = Color(0x99146EF5))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF2B86F8), Color(0xFF0F5CD1))))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(last, label = "cta") { isLast ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr(if (isLast) "ob.start" else "ob.next"),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = obFont(),
                )
                if (!isLast) {
                    // the "down" chevron turned to point right
                    Icon(PIcons.KeyboardArrowDown, null, Modifier.size(18.dp).rotate(-90f), tint = Color.White)
                }
            }
        }
    }
}

/* ───────────── reveal animation ───────────── */

/**
 * 0 -> 1 when [page] becomes the target page (delayed by [index] so items rise one after another); back to 0 once the
 * pager has settled on another page, so going back replays it.
 */
@Composable
private fun rememberReveal(pager: PagerState, page: Int, index: Int): State<Float> {
    val a = remember { Animatable(0f) }
    val here = pager.targetPage == page
    LaunchedEffect(here) {
        if (here) {
            delay(index * 70L)
            a.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        } else {
            snapshotFlow { pager.settledPage }.first { it != page }
            a.snapTo(0f)
        }
    }
    return a.asState()
}

private fun Modifier.reveal(p: State<Float>, dy: Dp = 24.dp): Modifier = graphicsLayer {
    val v = p.value
    alpha = 0.12f + 0.88f * v
    translationY = (1f - v) * dy.toPx()
}

/* ───────────── premium glyph tiles ───────────── */

private enum class Glyph { SPARK, DOC, OMR, TYPES, INST, PEN, CHECK, AA }

/** Gradient squircle with a white, hand-drawn glyph (app-icon look). Colours follow [accent]. */
@Composable
private fun GlyphTile(glyph: Glyph, accent: Color, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.3f)
    val ink = lerp(accent, Color.Black, 0.28f)
    Box(
        modifier
            .size(size)
            .shadow(size * 0.2f, shape, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.30f), accent, lerp(accent, Color.Black, 0.16f))))
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph == Glyph.AA) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val fs = (size.value * 0.34f).sp
                Text("অ", color = Color.White, fontSize = fs, fontFamily = SolaimanLipi)
                Text("A", color = Color.White, fontSize = fs, fontWeight = FontWeight.Bold, fontFamily = Lato)
            }
        } else {
            Canvas(Modifier.size(size * 0.54f)) { drawGlyph(glyph, ink) }
        }
    }
}

private fun DrawScope.star(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(cx, cy - r)
        quadraticBezierTo(cx, cy, cx + r, cy)
        quadraticBezierTo(cx, cy, cx, cy + r)
        quadraticBezierTo(cx, cy, cx - r, cy)
        quadraticBezierTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawGlyph(g: Glyph, ink: Color) {
    val w = size.width
    val h = size.height
    val white = Color.White
    when (g) {
        Glyph.SPARK -> {
            star(w * 0.42f, w * 0.58f, w * 0.40f, white)
            star(w * 0.80f, w * 0.20f, w * 0.16f, white.copy(alpha = 0.85f))
            star(w * 0.82f, w * 0.80f, w * 0.10f, white.copy(alpha = 0.7f))
        }
        Glyph.DOC -> {
            drawRoundRect(white, Offset(w * 0.16f, 0f), Size(w * 0.68f, h), CornerRadius(w * 0.12f))
            val sw = w * 0.07f
            drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.30f, h * 0.30f), Offset(w * 0.70f, h * 0.30f), sw, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.30f, h * 0.50f), Offset(w * 0.70f, h * 0.50f), sw, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.30f, h * 0.70f), Offset(w * 0.56f, h * 0.70f), sw, StrokeCap.Round)
        }
        Glyph.OMR -> {
            drawRoundRect(white, Offset(w * 0.08f, 0f), Size(w * 0.84f, h), CornerRadius(w * 0.14f))
            val filled = listOf(0, 2, 1)
            for (r in 0 until 3) for (cIdx in 0 until 3) {
                val c = Offset(w * (0.30f + 0.20f * cIdx), h * (0.22f + 0.28f * r))
                if (filled[r] == cIdx) drawCircle(ink, w * 0.075f, c)
                else drawCircle(ink.copy(alpha = 0.55f), w * 0.075f, c, style = Stroke(w * 0.04f))
            }
        }
        Glyph.TYPES -> {
            val s = w * 0.42f
            val rr = CornerRadius(w * 0.1f)
            drawRoundRect(white, Offset(0f, 0f), Size(s, s), rr)
            drawRoundRect(white.copy(alpha = 0.6f), Offset(w - s, 0f), Size(s, s), rr)
            drawRoundRect(white.copy(alpha = 0.6f), Offset(0f, h - s), Size(s, s), rr)
            drawRoundRect(white, Offset(w - s, h - s), Size(s, s), rr)
        }
        Glyph.INST -> {
            val roof = Path().apply {
                moveTo(w * 0.5f, 0f); lineTo(w, h * 0.30f); lineTo(0f, h * 0.30f); close()
            }
            drawPath(roof, white)
            for (i in 0 until 3) drawRoundRect(white, Offset(w * (0.10f + 0.33f * i), h * 0.40f), Size(w * 0.14f, h * 0.38f), CornerRadius(w * 0.03f))
            drawRoundRect(white, Offset(0f, h * 0.86f), Size(w, h * 0.14f), CornerRadius(w * 0.04f))
        }
        Glyph.PEN -> withTransform({ rotate(45f, Offset(w / 2f, h / 2f)) }) {
            drawRoundRect(white, Offset(w * 0.38f, -h * 0.04f), Size(w * 0.24f, h * 0.66f), CornerRadius(w * 0.08f))
            val tip = Path().apply {
                moveTo(w * 0.38f, h * 0.64f); lineTo(w * 0.62f, h * 0.64f); lineTo(w * 0.5f, h * 1.0f); close()
            }
            drawPath(tip, white)
            drawLine(ink.copy(alpha = 0.5f), Offset(w * 0.38f, h * 0.18f), Offset(w * 0.62f, h * 0.18f), w * 0.05f)
        }
        Glyph.CHECK -> {
            drawCircle(white, w * 0.44f, center, style = Stroke(w * 0.10f))
            val p = Path().apply {
                moveTo(w * 0.28f, h * 0.52f); lineTo(w * 0.44f, h * 0.68f); lineTo(w * 0.72f, h * 0.36f)
            }
            drawPath(p, white, style = Stroke(w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Glyph.AA -> {}
    }
}
