package com.intelliq.rifatplex.ui

import com.intelliq.rifatplex.ui.theme.IQD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.data.DEFAULT_COLS
import com.intelliq.rifatplex.data.Question
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.Lato

/**
 * Website `#qColsBar`. On a type tab it sets the columns of that category (1 / 2 / 3). On the Mix tab it sets the
 * columns of every question at once (and "Auto" removes all per-question overrides).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColsBar(tab: String, d: AppData, store: AppStore) {
    val c = IQ.c
    val shape = IQD.card
    val mix = tab == TAB_MIX
    val current: Int? = if (mix) null else (d.settings.typeCols[tab]?.takeIf { it in 1..3 } ?: DEFAULT_COLS)
    val label = if (mix) tr("cols.mix") else tr("cols.cat") + " " + tr("cols.type.$tab")
    FlowRow(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.glassBorder, shape)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.height(38.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                label,
                color = c.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..3).forEach { n ->
                ColsButton(n.toString(), current == n) {
                    if (mix) store.setAllCols(n) else store.setCategoryCols(tab, n)
                }
            }
            if (mix) ColsButton(tr("cols.auto"), false) { store.setAllCols(null) }
        }
    }
}

/**
 * Website `.qcol-row`: the small "Columns: Auto 1 2 3" row inside a card. Only shown on the Mix tab, and (like the
 * website) only for the non-MCQ types.
 */
@Composable
fun QColRow(q: Question, store: AppStore, modifier: Modifier = Modifier) {
    val c = IQ.c
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr("cols.row") + ":",
            color = c.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = uiFont(),
        )
        ColsButton(tr("cols.auto"), q.cols == null) { store.setCols(q.id, null) }
        (1..3).forEach { n -> ColsButton(n.toString(), q.cols == n) { store.setCols(q.id, n) } }
    }
}

@Composable
fun ColsButton(text: String, active: Boolean, onClick: () -> Unit) {
    val c = IQ.c
    val shape = IQD.ctl
    Box(
        Modifier
            .widthIn(min = 40.dp)
            .height(38.dp)
            .clip(shape)
            .background(if (active) c.primary else Color.Transparent)
            .border(1.5.dp, if (active) c.primary else c.borderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (active) Color.White else c.textSecondary,
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = Lato,
        )
    }
}
