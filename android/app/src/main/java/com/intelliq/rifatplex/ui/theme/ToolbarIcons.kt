package com.intelliq.rifatplex.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Preview-toolbar icon set. Every icon is drawn on the same 24x24 grid with the same
 * 1.75 stroke, round caps and round joins, no fill layer. This keeps zoom, download,
 * preview, save and reset visually identical in weight and style.
 */
object TIcons {

    private const val STROKE = 1.75f

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val ZoomOut: ImageVector by lazy { icon("ZoomOut", "M5 12h14") }

    val ZoomIn: ImageVector by lazy { icon("ZoomIn", "M5 12h14", "M12 5v14") }

    val Download: ImageVector by lazy {
        icon("Download", "M12 3v12", "M7 10l5 5 5-5", "M4 20h16")
    }

    val Eye: ImageVector by lazy {
        icon(
            "Eye",
            "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z",
            "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z",
        )
    }

    val Save: ImageVector by lazy {
        icon(
            "Save",
            "M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z",
            "M17 21v-8H7v8",
            "M7 3v5h8",
        )
    }

    val Reset: ImageVector by lazy {
        icon("Reset", "M3 12a9 9 0 1 0 2.64-6.36L3 8", "M3 3v5h5")
    }

    val Word: ImageVector by lazy {
        icon(
            "Word",
            "M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9l-6-6Z",
            "M14 3v6h6",
            "M8 13h8",
            "M8 17h8",
        )
    }
}
