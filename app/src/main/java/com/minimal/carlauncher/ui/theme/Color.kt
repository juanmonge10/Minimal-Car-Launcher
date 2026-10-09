package com.minimal.carlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CarColors(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentCyan: Color,
    val accentAmber: Color,
    val accentGreen: Color,
    val accentZLink: Color,
    val accentBlue: Color,
    val accentRed: Color,
    val isDark: Boolean
)

// Neutral, CarPlay-inspired night palette: true black backdrop, graphite surfaces
val DarkCarColors = CarColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    border = Color(0xFF38383A),
    textPrimary = Color(0xFFF2F2F7),
    textSecondary = Color(0xFFAEAEB2),
    textMuted = Color(0xFF8E8E93),
    accentCyan = Color(0xFF0A84FF),
    accentAmber = Color(0xFFFF9F0A),
    accentGreen = Color(0xFF30D158),
    accentZLink = Color(0xFF30D158),
    accentBlue = Color(0xFF0A84FF),
    accentRed = Color(0xFFFF453A),
    isDark = true
)

// Neutral, CarPlay-inspired day palette: soft grey backdrop, white surfaces, high-contrast text
val LightCarColors = CarColors(
    bg = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE5E5EA),
    border = Color(0xFFD1D1D6),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF3C3C43),
    textMuted = Color(0xFF6C6C70),
    accentCyan = Color(0xFF007AFF),
    accentAmber = Color(0xFFC93400),
    accentGreen = Color(0xFF248A3D),
    accentZLink = Color(0xFF248A3D),
    accentBlue = Color(0xFF007AFF),
    accentRed = Color(0xFFD70015),
    isDark = false
)

// Fixed home-screen tile colors (like app icons, identical in both themes; icons drawn in white)
val TileGreen = Color(0xFF34C759)
val TileBlue = Color(0xFF0A84FF)
val TilePink = Color(0xFFFF2D55)
val TileGraphite = Color(0xFF48484A)
val TileOrange = Color(0xFFFF9500)

val LocalCarColors = staticCompositionLocalOf { DarkCarColors }

// Dynamic Composable Color Accessors:
// Seamlessly delegates to LocalCarColors.current so all existing composables adapt dynamically
val CarBg: Color
    @Composable
    get() = LocalCarColors.current.bg

val CarSurface: Color
    @Composable
    get() = LocalCarColors.current.surface

val CarSurfaceVariant: Color
    @Composable
    get() = LocalCarColors.current.surfaceVariant

val CarBorder: Color
    @Composable
    get() = LocalCarColors.current.border

val AccentCyan: Color
    @Composable
    get() = LocalCarColors.current.accentCyan

val AccentAmber: Color
    @Composable
    get() = LocalCarColors.current.accentAmber

val AccentGreen: Color
    @Composable
    get() = LocalCarColors.current.accentGreen

val AccentZLink: Color
    @Composable
    get() = LocalCarColors.current.accentZLink

val AccentBlue: Color
    @Composable
    get() = LocalCarColors.current.accentBlue

val AccentRed: Color
    @Composable
    get() = LocalCarColors.current.accentRed

val TextPrimary: Color
    @Composable
    get() = LocalCarColors.current.textPrimary

val TextSecondary: Color
    @Composable
    get() = LocalCarColors.current.textSecondary

val TextMuted: Color
    @Composable
    get() = LocalCarColors.current.textMuted
