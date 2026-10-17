package com.intelliq.rifatplex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val LocalIQ = staticCompositionLocalOf { LightIQ }

object IQ {
    val c: IQColors
        @Composable @ReadOnlyComposable get() = LocalIQ.current
}

/** The phone's "font size" setting may not make the app text bigger than this (1.0 = normal): the layouts are built for it. */
const val MAX_FONT_SCALE = 1.0f

@Composable
fun IntelliQTheme(dark: Boolean, content: @Composable () -> Unit) {
    val iq = if (dark) DarkIQ else LightIQ
    val scheme = if (dark) {
        darkColorScheme(primary = iq.primary, background = iq.bg, surface = iq.panel, onSurface = iq.text)
    } else {
        lightColorScheme(primary = iq.primary, background = iq.bg, surface = iq.panel, onSurface = iq.text)
    }
    val dens = LocalDensity.current
    CompositionLocalProvider(
        LocalIQ provides iq,
        LocalDensity provides Density(dens.density, dens.fontScale.coerceAtMost(MAX_FONT_SCALE)),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
