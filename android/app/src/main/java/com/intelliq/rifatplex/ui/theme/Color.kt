package com.intelliq.rifatplex.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens from the website's style.css (:root and [data-theme=dark]).
 * Parity Step 1 fixed the text colours. Parity Step 2 added the missing tokens:
 * glass-panel-strong, primary-light, primary-ink, success-ink and the header gradient.
 */
@Immutable
data class IQColors(
    val bg: Color,
    val panel: Color,
    val panelStrong: Color,
    val panelSoft: Color,
    val field: Color,
    val fieldFocus: Color,
    val glassBorder: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val primaryHover: Color,
    val primaryLight: Color,
    val primaryInk: Color,
    val primarySoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val successInk: Color,
    val text: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val isDark: Boolean,
)

val LightIQ = IQColors(
    bg = Color(0xFFF5F6F8),
    panel = Color(0xFFFFFFFF),
    panelStrong = Color(0xFFFFFFFF),
    panelSoft = Color(0xFFF5F6F8),
    field = Color(0xFFFFFFFF),
    fieldFocus = Color(0xFFFFFFFF),
    glassBorder = Color(0x1F14151A),
    border = Color(0x1A14151A),
    borderStrong = Color(0x3314151A),
    primary = Color(0xFF146EF5),
    primaryHover = Color(0xFF0F5CD1),
    primaryLight = Color(0xFF6CA5F8),
    primaryInk = Color(0xFF0F5CD1),
    primarySoft = Color(0xFFE8F0FE),
    danger = Color(0xFFCC2456),
    dangerSoft = Color(0xFFFCE9EE),
    success = Color(0xFF10B77F),
    successInk = Color(0xFF0B7A55),
    text = Color(0xFF14151A),
    textSecondary = Color(0xFF404857),
    textFaint = Color(0xFF566070),
    headerStart = Color(0xFF0A9BEB),
    headerEnd = Color(0xFF0B6FEA),
    isDark = false,
)

val DarkIQ = IQColors(
    bg = Color(0xFF14161A),
    panel = Color(0xFF1E2126),
    panelStrong = Color(0xFF262A31),
    panelSoft = Color(0xFF1A1D22),
    field = Color(0xFF1E2126),
    fieldFocus = Color(0xFF262A31),
    glassBorder = Color(0x1AFFFFFF),
    border = Color(0x1AFFFFFF),
    borderStrong = Color(0x2EFFFFFF),
    primary = Color(0xFF146EF5),
    primaryHover = Color(0xFF3D85F7),
    primaryLight = Color(0xFF6CA5F8),
    primaryInk = Color(0xFF6CA5F8),
    primarySoft = Color(0xFF16283F),
    danger = Color(0xFFF0648A),
    dangerSoft = Color(0xFF3A1620),
    success = Color(0xFF3DD9A5),
    successInk = Color(0xFF3DD9A5),
    text = Color(0xFFF2F3F5),
    textSecondary = Color(0xFFB9BFCC),
    textFaint = Color(0xFFA4ABB8),
    headerStart = Color(0xFF05B3FC),
    headerEnd = Color(0xFF0C83F7),
    isDark = true,
)
