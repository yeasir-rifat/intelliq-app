package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.QUESTION_CATEGORIES
import com.intelliq.rifatplex.data.categorySettings
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQRadius
import com.intelliq.rifatplex.ui.theme.PIcons
import com.intelliq.rifatplex.ui.theme.Poppins

/** Tab id of the "Mix" tab: every question in document order. Other tabs use a category key. */
const val MIX_TAB = "mix"

/** Category keys in the order their tabs are shown (the order is set by dragging the tabs). */
fun AppData.categoryTabOrder(): List<String> =
    QUESTION_CATEGORIES.sortedBy { categorySettings(it).order }

/** Tab text. The website keeps the tab names in English in both languages, so the tabs do too. */
fun categoryTabLabel(key: String): String = trKey(categoryLabelKey(key), Lang.EN)

/** Question card label: MCQ for mcq, CQ for creative, and so on. Bangla uses the website's names. */
fun categoryCardLabel(key: String, lang: Lang): String = trKey(categoryLabelKey(key), lang)

private fun categoryLabelKey(key: String): String = when (key) {
    "creative" -> "label.cq"
    "tf" -> "label.tf"
    "fill" -> "label.blanks"
    "short" -> "label.sq"
    "desc" -> "label.bq"
    else -> "label.mcq"
}

/**
 * Category tabs: Mix first, then the categories in their saved order. Each tab shows how many
 * non-empty questions it holds. A category tab can be dragged (press and hold, then move) to a new
 * position; the new order is saved when the finger is lifted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryTabs(
    order: List<String>,
    active: String,
    counts: Map<String, Int>,
    lang: Lang,
    onSelect: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val c = IQ.c
    val bn = lang == Lang.BN
    // Last measured position of each category tab in window coordinates (read when a drag ends).
    val bounds = remember { HashMap<String, Rect>() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    val latestOrder by rememberUpdatedState(order)
    val latestReorder by rememberUpdatedState(onReorder)

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TabChip(
            label = "Mix",
            count = counts[MIX_TAB] ?: 0,
            active = active == MIX_TAB,
            bn = bn,
            modifier = Modifier,
            onClick = { onSelect(MIX_TAB) },
        )
        order.forEach { catKey ->
            key(catKey) {
                val dragging = dragKey == catKey
                TabChip(
                    label = categoryTabLabel(catKey),
                    count = counts[catKey] ?: 0,
                    active = active == catKey,
                    bn = bn,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationX = dragDelta.x
                                translationY = dragDelta.y
                            }
                        }
                        .onGloballyPositioned { bounds[catKey] = it.boundsInRoot() }
                        .pointerInput(catKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragKey = catKey
                                    dragDelta = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDelta = dragDelta + amount
                                },
                                onDragEnd = {
                                    val start = bounds[catKey]
                                    val point = start?.center?.plus(dragDelta)
                                    val target = point?.let { p ->
                                        latestOrder.firstOrNull { k ->
                                            k != catKey && bounds[k]?.contains(p) == true
                                        }
                                    }
                                    if (target != null) {
                                        val list = latestOrder.toMutableList()
                                        list.remove(catKey)
                                        list.add(list.indexOf(target).coerceAtLeast(0), catKey)
                                        latestReorder(list)
                                    }
                                    dragKey = null
                                    dragDelta = Offset.Zero
                                },
                                onDragCancel = {
                                    dragKey = null
                                    dragDelta = Offset.Zero
                                },
                            )
                        },
                    onClick = { onSelect(catKey) },
                )
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    count: Int,
    active: Boolean,
    bn: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = IQ.c
    val shape = RoundedCornerShape(IQRadius.sm)
    Row(
        modifier
            .clip(shape)
            .background(if (active) c.primary else c.panel)
            .border(1.dp, if (active) c.primary else c.glassBorder, shape)
            .iqPressable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (active) Color.White else c.text,
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = Poppins,
        )
        Text(
            formatNumber(count, bn),
            color = if (active) Color.White.copy(alpha = 0.85f) else c.textFaint,
            fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = uiFont(),
        )
    }
}

/**
 * The bar under the tabs for a category tab: a count stepper and the "Add" button, which adds that
 * many blank questions of the category. The count is saved per category (CategorySettings.count).
 */
@Composable
fun CategoryAddBar(
    count: Int,
    lang: Lang,
    onCount: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val c = IQ.c
    val bn = lang == Lang.BN
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            trKey("cat.count", lang),
            color = c.textSecondary, fontSize = 12.sp, fontFamily = uiFont(),
        )
        SquareBtn(28.dp, onClick = { onCount(count - 1) }) {
            Icon(PIcons.Remove, null, Modifier.size(14.dp), tint = c.textSecondary)
        }
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            Text(
                formatNumber(count, bn),
                color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            )
        }
        SquareBtn(28.dp, onClick = { onCount(count + 1) }) {
            Icon(PIcons.Add, null, Modifier.size(14.dp), tint = c.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        IQButton(trKey("cat.add", lang), BtnKind.Primary) { onAdd() }
    }
}
