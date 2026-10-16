package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * A hairline edge for content cards so they separate from the coloured
 * backdrop (AnimatedGradientBackground): a faint light line in dark mode, a
 * faint dark line in light mode. Shadows alone barely show on a dark red/black
 * background.
 */
@Composable
fun cardEdge(): BorderStroke {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
}
