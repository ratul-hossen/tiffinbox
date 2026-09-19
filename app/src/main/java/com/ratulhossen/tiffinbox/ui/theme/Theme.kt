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
    secondary = TiffinAmber,
    background = TiffinBackground,
    onBackground = TiffinOnSurface,
    surface = TiffinSurface,
    onSurface = TiffinOnSurface,
    surfaceVariant = TiffinOrangeLight,
    onSurfaceVariant = TiffinOnSurfaceMuted,
    outline = TiffinOutline,
    error = TiffinRed
)

private val DarkColors = darkColorScheme(
    primary = TiffinOrange,
    onPrimary = Color.White,
    primaryContainer = TiffinOrangeDark,
    onPrimaryContainer = TiffinOrangeLight,
    secondary = TiffinAmber,
    background = TiffinBackgroundDark,
    onBackground = TiffinOnSurfaceDark,
    surface = TiffinSurfaceDark,
    onSurface = TiffinOnSurfaceDark,
    surfaceVariant = TiffinSurfaceDark,
    onSurfaceVariant = TiffinOnSurfaceMutedDark,
    outline = TiffinOutlineDark,
    error = TiffinRed
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
