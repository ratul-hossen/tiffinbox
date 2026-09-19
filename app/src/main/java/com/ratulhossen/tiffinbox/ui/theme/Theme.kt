package com.ratulhossen.tiffinbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TiffinOrange,
    onPrimary = Color.White,
    primaryContainer = TiffinOrangeLight,
    onPrimaryContainer = TiffinOrangeDark,
    secondary = TiffinGreen,
    onSecondary = Color.White,
    secondaryContainer = TiffinGreenLight,
    onSecondaryContainer = TiffinGreenDark,
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

private val DarkColors = darkColorScheme(
    primary = TiffinOrange,
    onPrimary = Color.White,
    primaryContainer = TiffinOrangeDark,
    onPrimaryContainer = TiffinOrangeLight,
    secondary = TiffinGreenLight,
    onSecondary = TiffinGreenDark,
    secondaryContainer = TiffinGreenDark,
    onSecondaryContainer = TiffinGreenLight,
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

@Composable
fun TiffinBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = TiffinTypography,
        content = content
    )
}
