package com.intelliq.rifatplex.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A dashed rounded border that is drawn fully inside the bounds (so it is never cut off by a clip) and whose dash
 * pattern is stretched slightly so a whole number of dashes fits around the box. That keeps the four corners
 * looking identical and avoids a half-dash or a gap landing on a corner. [radius] must equal the corner radius of
 * the shape the box is clipped with.
 */
fun Modifier.dashedBorder(
    color: Color,
    radius: Dp,
    width: Dp = 1.dp,
    dash: Dp = 4.dp,
    gap: Dp = 3.dp,
): Modifier = drawBehind {
    val sw = width.toPx()
    val half = sw / 2f
    val r = (radius.toPx() - half).coerceAtLeast(0f)
    val w = size.width - sw
    val h = size.height - sw
    val perimeter = 2f * (w - 2f * r) + 2f * (h - 2f * r) + 2f * PI.toFloat() * r
    val d = dash.toPx()
    val g = gap.toPx()
    val count = max(1, (perimeter / (d + g)).roundToInt())
    val scale = perimeter / count / (d + g)
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
        style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(d * scale, g * scale))),
    )
}
