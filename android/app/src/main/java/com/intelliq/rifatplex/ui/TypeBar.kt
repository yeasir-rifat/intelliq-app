package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.distinctTypes
import com.intelliq.rifatplex.data.orderedTypes
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.Lato

/** The "all types" tab. The other tabs use the question type ids (QType.MCQ ...). */
const val TAB_MIX = "mix"

// Same labels as the website (qTypeTabs): they are not translated there either.
private val TYPE_TABS = listOf(
    QType.MCQ to "MCQ",
    QType.CREATIVE to "CQ",
    QType.TF to "T/F",
    QType.FILL to "Blanks",
    QType.SHORT to "SQ",
    QType.DESC to "BQ",
)

/**
 * Website `.qtabs`: Mix / MCQ / CQ / T/F / Blanks / SQ / BQ. On a phone it is three rows:
 * "Mixed Questions" over the full width, then three tabs, then three tabs.
 */
@Composable
fun TypeTabs(active: String, counts: Map<String, Int> = emptyMap(), onSelect: (String) -> Unit) {
    val c = IQ.c
    val shape = IQD.card
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.glassBorder, shape)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TabButton(tr("tab.mixFull"), active == TAB_MIX, counts[TAB_MIX] ?: 0, Modifier.fillMaxWidth()) { onSelect(TAB_MIX) }
        TYPE_TABS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (id, label) ->
                    TabButton(label, active == id, counts[id] ?: 0, Modifier.weight(1f)) { onSelect(id) }
                }
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    val bg by animateColorAsState(if (selected) c.primary else Color.Transparent, tween(120), label = "tabBg")
    val line by animateColorAsState(if (selected) c.primary else c.borderStrong, tween(120), label = "tabLine")
    val fg by animateColorAsState(if (selected) Color.White else c.textSecondary, tween(120), label = "tabFg")
    Box(
        modifier
            .height(40.dp)
            .then(if (selected) Modifier.shadow(1.dp, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .border(1.5.dp, line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Lato,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (count > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White.copy(alpha = 0.25f) else c.primarySoft)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        count.toString(),
                        color = if (selected) Color.White else c.primaryInk,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = Lato,
                    )
                }
            }
        }
    }
}

/* ───────────── which cards are shown, in which order, with which number ───────────── */

/** One card of the editor: the question, the number shown on it and whether it is the empty card at the end. */
data class CardItem(val q: Question, val number: Int, val isTrailing: Boolean)

/** "Arrange by section" is on and the paper has at least two types (website `QSec`). */
fun isArranged(d: AppData): Boolean = d.settings.sectionMode && d.questions.distinctTypes().size >= 2

/**
 * The cards of the editor like the website shows them:
 * - arranged by section (2+ types): cards are grouped in the section order and numbered inside their section,
 *   the empty card always stays last;
 * - otherwise: data order, numbered 1..n;
 * - on a type tab only the questions of that type are listed.
 */
fun buildCardItems(d: AppData, tab: String): List<CardItem> {
    val qs = d.questions
    val last = qs.lastOrNull()
    val items: List<CardItem> = if (isArranged(d)) {
        val ord = d.settings.orderedTypes()
        val counts = HashMap<String, Int>()
        val numbered = qs.map { q ->
            val t = q.kind()
            val n = (counts[t] ?: 0) + 1
            if (!q.isEmpty()) counts[t] = n
            CardItem(q, n, q === last && q.isEmpty())
        }
        numbered.withIndex()
            .sortedWith(
                compareBy<IndexedValue<CardItem>>(
                    { if (it.value.isTrailing) 99 else ord.indexOf(it.value.q.kind()) + 1 },
                    { it.index },
                )
            )
            .map { it.value }
    } else {
        qs.mapIndexed { i, q -> CardItem(q, i + 1, q === last && q.isEmpty()) }
    }
    return if (tab == TAB_MIX) items else items.filter { it.q.kind() == tab }
}

/**
 * The card above (-1) / below (+1) that the up / down button swaps with. Null = button disabled.
 * In an arranged paper only cards of the same section can swap (otherwise nothing would visibly move), and the empty
 * card at the end never moves.
 */
fun List<CardItem>.neighbour(pos: Int, delta: Int, sameTypeOnly: Boolean): CardItem? {
    val me = getOrNull(pos) ?: return null
    val other = getOrNull(pos + delta) ?: return null
    if (me.isTrailing || other.isTrailing) return null
    if (sameTypeOnly && other.q.kind() != me.q.kind()) return null
    return other
}
