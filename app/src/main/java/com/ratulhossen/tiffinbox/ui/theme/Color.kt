package com.ratulhossen.tiffinbox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One accent "brand" per palette, on top of shared Apple-style neutrals
 * (iOS grouped-background greys in light mode, true black + elevated greys
 * in dark mode). [gradientStart]/[gradientEnd] drive every gradient in the
 * app (buttons, Home header, hero banner) via LocalBrandGradient.
 */
data class BrandColors(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val secondary: Color,
    val tertiary: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

val BlackRedBrand = BrandColors(
    primary = Color(0xFFE11D2E),
    primaryContainer = Color(0xFFFFE1E4),
    onPrimaryContainer = Color(0xFF7A0A16),
    primaryDark = Color(0xFFFF4D5E),
    onPrimaryDark = Color(0xFF2A0006),
    secondary = Color(0xFF1C1C1E),
    tertiary = Color(0xFFFF9F0A),
    gradientStart = Color(0xFFFF3B4E),
    gradientEnd = Color(0xFF8E0E1C)
)

val SunsetBrand = BrandColors(
    primary = Color(0xFFF2600C),
    primaryContainer = Color(0xFFFFE6D5),
    onPrimaryContainer = Color(0xFF6B2500),
    primaryDark = Color(0xFFFF8A3D),
    onPrimaryDark = Color(0xFF2E0E00),
    secondary = Color(0xFFD9265B),
    tertiary = Color(0xFFFFB800),
    gradientStart = Color(0xFFFF9500),
    gradientEnd = Color(0xFFFF2D55)
)

val OceanBrand = BrandColors(
    primary = Color(0xFF0A6CFF),
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF002A6B),
    primaryDark = Color(0xFF4D9BFF),
    onPrimaryDark = Color(0xFF001233),
    secondary = Color(0xFF5E5CE6),
    tertiary = Color(0xFF30D5C8),
    gradientStart = Color(0xFF32ADE6),
    gradientEnd = Color(0xFF5E5CE6)
)

val GreenBrand = BrandColors(
    primary = Color(0xFF059669),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    primaryDark = Color(0xFF34D399),
    onPrimaryDark = Color(0xFF022C22),
    secondary = Color(0xFF0E7490),
    tertiary = Color(0xFFF59E0B),
    gradientStart = Color(0xFF34C759),
    gradientEnd = Color(0xFF0E7490)
)

// Apple-style neutrals shared by every palette.
val NeutralBackground = Color(0xFFF2F2F7)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralSurfaceVariant = Color(0xFFE9E9EF)
val NeutralOnSurface = Color(0xFF111114)
val NeutralOnSurfaceMuted = Color(0xFF6C6C72)
val NeutralOutline = Color(0xFFD8D8DE)

val NeutralBackgroundDark = Color(0xFF000000)
val NeutralSurfaceDark = Color(0xFF1C1C1E)
val NeutralSurfaceVariantDark = Color(0xFF2C2C2E)
val NeutralOnSurfaceDark = Color(0xFFF5F5F7)
val NeutralOnSurfaceMutedDark = Color(0xFFA1A1A8)
val NeutralOutlineDark = Color(0xFF3A3A3C)

val ErrorRed = Color(0xFFFF3B30)
val ErrorRedContainer = Color(0xFFFFE2E0)

// Rating stars / success states, independent of palette.
val StarAmber = Color(0xFFFFB300)
val SuccessGreen = Color(0xFF34C759)
