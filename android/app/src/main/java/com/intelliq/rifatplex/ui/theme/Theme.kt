package com.intelliq.rifatplex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalIQ = staticCompositionLocalOf { LightIQ }

object IQ {
    val c: IQColors
        @Composable @ReadOnlyComposable get() = LocalIQ.current
}

/** Corner radii from style.css (--radius-sm, --radius-md, --radius-lg). */
object IQRadius {
    val sm: Dp = 4.dp
    val md: Dp = 8.dp
    val lg: Dp = 10.dp
}

/** Shadow elevations that approximate style.css --shadow-sm, --shadow-md and --shadow-lg. */
object IQElevation {
    val sm: Dp = 1.dp
    val md: Dp = 4.dp
    val lg: Dp = 12.dp
}

@Composable
fun IntelliQTheme(dark: Boolean, content: @Composable () -> Unit) {
    val iq = if (dark) DarkIQ else LightIQ
    // Every Material colour role points at a website token, so no default Material purple leaks in.
    // surfaceTint is transparent: M3 would otherwise tint elevated surfaces (menus, dialogs) blue.
    val scheme = if (dark) {
        darkColorScheme(
            primary = iq.primary, onPrimary = Color.White,
            primaryContainer = iq.primarySoft, onPrimaryContainer = iq.primaryInk,
            background = iq.bg, onBackground = iq.text,
            surface = iq.panel, onSurface = iq.text,
            surfaceVariant = iq.panelSoft, onSurfaceVariant = iq.textSecondary,
            surfaceTint = Color.Transparent,
            outline = iq.borderStrong, outlineVariant = iq.border,
            error = iq.danger, onError = Color.White,
            errorContainer = iq.dangerSoft, onErrorContainer = iq.danger,
        )
    } else {
        lightColorScheme(
            primary = iq.primary, onPrimary = Color.White,
            primaryContainer = iq.primarySoft, onPrimaryContainer = iq.primaryInk,
            background = iq.bg, onBackground = iq.text,
            surface = iq.panel, onSurface = iq.text,
            surfaceVariant = iq.panelSoft, onSurfaceVariant = iq.textSecondary,
            surfaceTint = Color.Transparent,
            outline = iq.borderStrong, outlineVariant = iq.border,
            error = iq.danger, onError = Color.White,
            errorContainer = iq.dangerSoft, onErrorContainer = iq.danger,
        )
    }
    CompositionLocalProvider(LocalIQ provides iq) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
