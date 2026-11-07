package com.intelliq.rifatplex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intelliq.rifatplex.data.AppData
import com.intelliq.rifatplex.data.OmrSheetKind
import com.intelliq.rifatplex.data.QType
import com.intelliq.rifatplex.data.formatNumber
import com.intelliq.rifatplex.data.previewQuestions
import com.intelliq.rifatplex.ui.theme.IQ
import com.intelliq.rifatplex.ui.theme.IQD

/*
 * The quick sheet-size selector of the OMR preview (50 / 100 / 200 chips) and the one-line size hint under the toolbar.
 * The chips write settings.omr.sheetKind, the same value as the selector in Settings, so the preview and its page count follow at once.
 */

/** Number of MCQs of the paper on screen (what the OMR sheet has to hold). */
internal fun omrMcqCount(d: AppData): Int = d.previewQuestions().count { !it.isEmpty() && it.kind() == QType.MCQ }

/** 50 / 100 / 200 chips in the look of the zoom control; the chip of [current] is highlighted. */
@Composable
internal fun OmrSizeChips(current: Int, onPick: (Int) -> Unit) {
    val c = IQ.c
    val bn = LocalLang.current == Lang.BN
    val shape = IQD.card
    val selected = OmrSheetKind.ofRows(current)
    Row(
        Modifier
            .clip(shape)
            .background(c.panelSoft)
            .border(1.dp, c.glassBorder, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmrSheetKind.values().sortedBy { it.rows }.forEach { k ->
            val on = k == selected
            Text(
                formatNumber(k.rows, bn),
                Modifier
                    .clip(IQD.ctl)
                    .background(if (on) c.primarySoft else Color.Transparent)
                    .clickable { onPick(k.rows) }
                    .widthIn(min = 44.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = if (on) c.primaryInk else c.textSecondary,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontFamily = uiFont(),
            )
        }
    }
}

/** The size hint as one small centred line under the toolbar; shows nothing when there is no MCQ. */
@Composable
internal fun OmrSizeHint(mcqCount: Int, lang: Lang) {
    val text = omrSizeHint(mcqCount, lang) ?: return
    Text(
        text,
        Modifier.fillMaxWidth().background(IQ.c.panel).padding(horizontal = 12.dp, vertical = 6.dp),
        color = IQ.c.textFaint, fontSize = 12.sp, fontFamily = uiFont(), textAlign = TextAlign.Center,
    )
}
