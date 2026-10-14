package com.intelliq.rifatplex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalIQ = staticCompositionLocalOf { LightIQ }

object IQ {
    val c: IQColors
        @Composable @ReadOnlyComposable get() = LocalIQ.current
}

@Composable
fun IntelliQTheme(dark: Boolean, content: @Composable () -> Unit) {
    val iq = if (dark) DarkIQ else LightIQ
    val scheme = if (dark) {
        darkColorScheme(primary = iq.primary, background = iq.bg, surface = iq.panel, onSurface = iq.text)
    } else {
        lightColorScheme(primary = iq.primary, background = iq.bg, surface = iq.panel, onSurface = iq.text)
    }
    CompositionLocalProvider(LocalIQ provides iq) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
