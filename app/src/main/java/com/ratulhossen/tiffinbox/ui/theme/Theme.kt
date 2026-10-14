package com.ratulhossen.tiffinbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenLightColors = lightColorScheme(
    primary = TiffinEmerald,
    onPrimary = Color.White,
    primaryContainer = TiffinEmeraldLight,
    onPrimaryContainer = TiffinEmeraldDark,
    secondary = TiffinGraphite,
    onSecondary = Color.White,
    secondaryContainer = TiffinGraphiteLight,
    onSecondaryContainer = TiffinGraphiteDark,
    tertiary = TiffinAmber,
    onTertiary = Color.White,
    tertiaryContainer = TiffinAmberLight,
    onTertiaryContainer = TiffinAmberDark,
    background = TiffinBackground,
    onBackground = TiffinOnSurface,
    surface = TiffinSurface,
    onSurface = TiffinOnSurface,
    surfaceVariant = TiffinSurfaceDim,
    onSurfaceVariant = TiffinOnSurfaceMuted,
    outline = TiffinOutline,
    outlineVariant = TiffinOutline,
    error = TiffinRed,
    onError = Color.White,
    errorContainer = TiffinRedLight,
    onErrorContainer = TiffinRed
)

private val GreenDarkColors = darkColorScheme(
    primary = TiffinEmeraldBright,
    onPrimary = TiffinEmeraldOnBright,
    primaryContainer = TiffinEmeraldDark,
    onPrimaryContainer = TiffinEmeraldLight,
    secondary = TiffinGraphiteLight,
    onSecondary = TiffinGraphiteDark,
    secondaryContainer = TiffinGraphiteDark,
    onSecondaryContainer = TiffinGraphiteLight,
    tertiary = TiffinAmberLight,
    onTertiary = TiffinAmberDark,
    tertiaryContainer = TiffinAmberDark,
    onTertiaryContainer = TiffinAmberLight,
    background = TiffinBackgroundDark,
    onBackground = TiffinOnSurfaceDark,
    surface = TiffinSurfaceDark,
    onSurface = TiffinOnSurfaceDark,
    surfaceVariant = TiffinSurfaceDimDark,
    onSurfaceVariant = TiffinOnSurfaceMutedDark,
    outline = TiffinOutlineDark,
    outlineVariant = TiffinOutlineDark,
    error = TiffinRedLight,
    onError = TiffinRed,
    errorContainer = TiffinRed,
    onErrorContainer = TiffinRedLight
)

private val BlackRedLightColors = lightColorScheme(
    primary = BlackRedPrimary,
    onPrimary = Color.White,
    primaryContainer = BlackRedPrimaryLight,
    onPrimaryContainer = BlackRedPrimaryDark,
    secondary = BlackRedSecondary,
    onSecondary = Color.White,
    secondaryContainer = BlackRedSecondaryLight,
    onSecondaryContainer = BlackRedSecondaryDark,
    tertiary = BlackRedSecondary,
    onTertiary = Color.White,
    tertiaryContainer = BlackRedSecondaryLight,
    onTertiaryContainer = BlackRedSecondaryDark,
    background = BlackRedBackground,
    onBackground = BlackRedOnSurface,
    surface = BlackRedSurface,
    onSurface = BlackRedOnSurface,
    surfaceVariant = BlackRedSurfaceDim,
    onSurfaceVariant = BlackRedOnSurfaceMuted,
    outline = BlackRedOutline,
    outlineVariant = BlackRedOutline,
    error = BlackRedPrimary,
    onError = Color.White,
    errorContainer = BlackRedPrimaryLight,
    onErrorContainer = BlackRedPrimaryDark
)

private val BlackRedDarkColors = darkColorScheme(
    primary = BlackRedPrimaryBright,
    onPrimary = BlackRedOnPrimaryBright,
    primaryContainer = BlackRedPrimaryDark,
    onPrimaryContainer = BlackRedPrimaryLight,
    secondary = BlackRedSecondaryLight,
    onSecondary = BlackRedSecondaryDark,
    secondaryContainer = BlackRedSecondaryDark,
    onSecondaryContainer = BlackRedSecondaryLight,
    tertiary = BlackRedSecondaryLight,
    onTertiary = BlackRedSecondaryDark,
    tertiaryContainer = BlackRedSecondaryDark,
    onTertiaryContainer = BlackRedSecondaryLight,
    background = BlackRedBackgroundDark,
    onBackground = BlackRedOnSurfaceDark,
    surface = BlackRedSurfaceDark,
    onSurface = BlackRedOnSurfaceDark,
    surfaceVariant = BlackRedSurfaceDimDark,
    onSurfaceVariant = BlackRedOnSurfaceMutedDark,
    outline = BlackRedOutlineDark,
    outlineVariant = BlackRedOutlineDark,
    error = BlackRedPrimaryBright,
    onError = BlackRedOnPrimaryBright,
    errorContainer = BlackRedPrimaryDark,
    onErrorContainer = BlackRedPrimaryLight
)

@Composable
fun TiffinBoxTheme(
    palette: ThemePalette = ThemePreferences.current,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = when (palette) {
        ThemePalette.CLASSIC_GREEN -> if (darkTheme) GreenDarkColors else GreenLightColors
        ThemePalette.BLACK_RED -> if (darkTheme) BlackRedDarkColors else BlackRedLightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = TiffinTypography,
        shapes = TiffinShapes,
        content = content
    )
}
