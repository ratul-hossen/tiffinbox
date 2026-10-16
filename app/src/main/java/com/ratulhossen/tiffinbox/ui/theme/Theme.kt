package com.ratulhossen.tiffinbox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** The current palette's two gradient colors - read via [brandGradient]. */
data class BrandGradient(val start: Color, val end: Color)

val LocalBrandGradient = staticCompositionLocalOf { BrandGradient(BlackRedBrand.gradientStart, BlackRedBrand.gradientEnd) }

/** Diagonal brand gradient for buttons, headers and banners. */
@Composable
fun brandGradient(): Brush {
    val g = LocalBrandGradient.current
    return Brush.linearGradient(listOf(g.start, g.end))
}

private fun lightScheme(b: BrandColors): ColorScheme = lightColorScheme(
    primary = b.primary,
    onPrimary = Color.White,
    primaryContainer = b.primaryContainer,
    onPrimaryContainer = b.onPrimaryContainer,
    secondary = b.secondary,
    onSecondary = Color.White,
    secondaryContainer = NeutralSurfaceVariant,
    onSecondaryContainer = NeutralOnSurface,
    tertiary = b.tertiary,
    onTertiary = Color.White,
    tertiaryContainer = b.primaryContainer,
    onTertiaryContainer = b.onPrimaryContainer,
    background = NeutralBackground,
    onBackground = NeutralOnSurface,
    surface = NeutralSurface,
    onSurface = NeutralOnSurface,
    surfaceVariant = NeutralSurfaceVariant,
    onSurfaceVariant = NeutralOnSurfaceMuted,
    surfaceContainer = NeutralSurface,
    surfaceContainerLow = NeutralSurface,
    surfaceContainerHigh = NeutralSurface,
    outline = NeutralOutline,
    outlineVariant = NeutralOutline,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFF7A0B05)
)

private fun darkScheme(b: BrandColors): ColorScheme = darkColorScheme(
    primary = b.primaryDark,
    onPrimary = b.onPrimaryDark,
    primaryContainer = b.onPrimaryContainer,
    onPrimaryContainer = b.primaryContainer,
    secondary = b.secondary,
    onSecondary = Color.White,
    secondaryContainer = NeutralSurfaceVariantDark,
    onSecondaryContainer = NeutralOnSurfaceDark,
    tertiary = b.tertiary,
    onTertiary = Color.Black,
    tertiaryContainer = b.onPrimaryContainer,
    onTertiaryContainer = b.primaryContainer,
    background = NeutralBackgroundDark,
    onBackground = NeutralOnSurfaceDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceVariant = NeutralSurfaceVariantDark,
    onSurfaceVariant = NeutralOnSurfaceMutedDark,
    surfaceContainer = NeutralSurfaceDark,
    surfaceContainerLow = NeutralSurfaceDark,
    surfaceContainerHigh = NeutralSurfaceVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineDark,
    error = Color(0xFFFF6961),
    onError = Color.Black,
    errorContainer = Color(0xFF5C0A05),
    onErrorContainer = ErrorRedContainer
)

private fun brandFor(palette: ThemePalette): BrandColors = when (palette) {
    ThemePalette.BLACK_RED -> BlackRedBrand
    ThemePalette.SUNSET_ORANGE -> SunsetBrand
    ThemePalette.OCEAN_BLUE -> OceanBrand
    ThemePalette.CLASSIC_GREEN -> GreenBrand
    ThemePalette.DYNAMIC -> BlackRedBrand
}

@Composable
fun TiffinBoxTheme(
    palette: ThemePalette = ThemePreferences.current,
    darkTheme: Boolean = when (ThemePreferences.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val useDynamic = palette == ThemePalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors: ColorScheme
    val gradient: BrandGradient
    if (useDynamic) {
        val context = LocalContext.current
        colors = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        gradient = BrandGradient(colors.primary, colors.tertiary)
    } else {
        val brand = brandFor(palette)
        colors = if (darkTheme) darkScheme(brand) else lightScheme(brand)
        gradient = BrandGradient(brand.gradientStart, brand.gradientEnd)
    }
    CompositionLocalProvider(LocalBrandGradient provides gradient) {
        MaterialTheme(
            colorScheme = colors,
            typography = TiffinTypography,
            shapes = TiffinShapes,
            content = content
        )
    }
}
