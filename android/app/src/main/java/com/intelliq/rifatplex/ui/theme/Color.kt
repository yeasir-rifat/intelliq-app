package com.intelliq.rifatplex.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Exact design tokens from the website's styles.css (:root and [data-theme=dark]). */
@Immutable
data class IQColors(
    val bg: Color,
    val panel: Color,
    val panelSoft: Color,
    val field: Color,
    val fieldFocus: Color,
    val glassBorder: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val primaryHover: Color,
    val primarySoft: Color,
    /** Primary as TEXT colour on panels (website --primary-ink): AA contrast in light and dark. */
    val primaryInk: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val successInk: Color,
    val text: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val isDark: Boolean,
)

val LightIQ = IQColors(
    bg = Color(0xFFF5F6F8),
    panel = Color(0xFFFFFFFF),
    panelSoft = Color(0xFFF5F6F8),
    field = Color(0xFFFFFFFF),
    fieldFocus = Color(0xFFFFFFFF),
    glassBorder = Color(0x1F14151A),
    border = Color(0x1A14151A),
    borderStrong = Color(0x3314151A),
    primary = Color(0xFF146EF5),
    primaryHover = Color(0xFF0F5CD1),
    primarySoft = Color(0xFFE8F0FE),
    primaryInk = Color(0xFF0F5CD1),
    danger = Color(0xFFCC2456),
    dangerSoft = Color(0xFFFCE9EE),
    success = Color(0xFF10B77F),
    successInk = Color(0xFF0B7A55),
    text = Color(0xFF14151A),
    textSecondary = Color(0xFF404857),
    textFaint = Color(0xFF566070),
    isDark = false,
)

val DarkIQ = IQColors(
    bg = Color(0xFF14161A),
    panel = Color(0xFF1E2126),
    panelSoft = Color(0xFF1A1D22),
    field = Color(0xFF1E2126),
    fieldFocus = Color(0xFF262A31),
    glassBorder = Color(0x1AFFFFFF),
    border = Color(0x1AFFFFFF),
    borderStrong = Color(0x2EFFFFFF),
    primary = Color(0xFF146EF5),
    primaryHover = Color(0xFF3D85F7),
    primarySoft = Color(0xFF16283F),
    primaryInk = Color(0xFF6CA5F8),
    danger = Color(0xFFF0648A),
    dangerSoft = Color(0xFF3A1620),
    success = Color(0xFF3DD9A5),
    successInk = Color(0xFF3DD9A5),
    text = Color(0xFFF2F3F5),
    textSecondary = Color(0xFFB9BFCC),
    textFaint = Color(0xFFA4ABB8),
    isDark = true,
)
