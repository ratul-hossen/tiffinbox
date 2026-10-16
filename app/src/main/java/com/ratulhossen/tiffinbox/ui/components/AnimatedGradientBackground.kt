package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.ratulhossen.tiffinbox.ui.theme.LocalBrandGradient
import kotlin.math.cos
import kotlin.math.sin

/**
 * App-wide backdrop: the theme's background color with three large, soft
 * "light blobs" in the current palette's brand colors only (an Apple-wallpaper-
 * style mesh gradient, now static). Each blob is a radial
 * gradient fading to transparent, so it reads as blurred without needing
 * Modifier.blur (API 31+ only).
 *
 * Mounted once at the root (MainActivity), so every screen that doesn't
 * paint its own opaque background (role select, login, location setup,
 * email verification, chef dashboard...) gets it automatically; screens
 * inside a Scaffold keep their solid container color.
 */
@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gradient = LocalBrandGradient.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val blobAlpha = if (isDark) 0.38f else 0.26f
    val colorA = gradient.start.copy(alpha = blobAlpha)
    val colorB = gradient.end.copy(alpha = blobAlpha)
    // Third blob stays inside the brand pair (it used tertiary, which turned the
    // bottom of every Black & Red screen a muddy amber-brown).
    val colorC = lerp(gradient.start, gradient.end, 0.5f).copy(alpha = blobAlpha * 0.8f)

    // Static on purpose (2026-10-16): a moving backdrop behind every screen
    // competed with the real, purposeful motion (Home header, Lottie), so the
    // blobs now sit still - same look, no constant movement.
    val t = 0.6f

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // propagateMinConstraints so the content fills the screen exactly like
        // the plain root Surface it replaced did.
        Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val r = maxOf(w, h) * 0.55f

                fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
                    val center = Offset(cx, cy)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
                        radius = radius,
                        center = center
                    )
                }

                blob(colorA, w * (0.15f + 0.12f * cos(t)), h * (0.12f + 0.08f * sin(t)), r)
                blob(colorB, w * (0.90f + 0.10f * sin(t * 1.0f + 1.3f)), h * (0.55f + 0.12f * cos(t + 0.7f)), r * 0.9f)
                blob(colorC, w * (0.30f + 0.15f * sin(t + 2.6f)), h * (0.95f + 0.06f * cos(t + 2.1f)), r * 0.8f)
            }
            content()
        }
    }
}
