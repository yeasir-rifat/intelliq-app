package com.intelliq.rifatplex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Design tokens of the app UI (the paper preview / PDF do not use them).
 * Radius scale: controls (fields, buttons, chips) 8, cards 12, dialogs 20. Spacing is on a 4dp grid. Field height 44.
 */
object IQD {
    val ctl = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(20.dp)
    val fieldH = 44.dp
}
