package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.ui.theme.LocalBrandGradient
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Home header background that slowly drifts through the current theme's
 * colours (brand gradient -> tertiary -> secondary -> back), like the
 * colour-changing header in the reference video. The animated value is only
 * read while drawing, so this redraws without recomposing the header.
 */
@Composable
fun Modifier.shiftingBrandBackground(): Modifier {
    val g = LocalBrandGradient.current
    val scheme = MaterialTheme.colorScheme
    val stops = listOf(g.start, g.end, scheme.tertiary, scheme.secondary, g.start)
    val transition = rememberInfiniteTransition(label = "headerColors")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (stops.size - 1).toFloat(),
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart),
        label = "headerPhase"
    )
    return this.drawBehind {
        val p = phase.value
        val i = p.toInt().coerceAtMost(stops.size - 2)
        val f = p - i
        val a = lerp(stops[i], stops[i + 1], f)
        val b = lerp(stops[(i + 1) % (stops.size - 1)], stops[(i + 2) % (stops.size - 1)], f)
        drawRect(Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset(size.width, size.height)))
    }
}

/**
 * A big plate in the middle with smaller food photos orbiting it - the
 * rotating-plates header from the reference video. Rotation is applied in
 * the layout/draw phase only.
 */
@Composable
fun FoodOrbit(
    @DrawableRes center: Int,
    satellites: List<Int>,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orbit")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
        label = "orbitAngle"
    )
    val pulse = transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "orbitPulse"
    )
    val satelliteSize = size * 0.28f
    val radiusPx = with(LocalDensity.current) { ((size - satelliteSize) / 2).toPx() }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Central plate, gently "breathing", spinning slowly the other way.
        Image(
            painter = painterResource(center),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size * 0.5f)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                    rotationZ = -angle.value / 2
                }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
        )
        satellites.forEachIndexed { index, res ->
            val step = 360f / satellites.size
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset {
                        val rad = Math.toRadians((angle.value + index * step).toDouble())
                        IntOffset((cos(rad) * radiusPx).roundToInt(), (sin(rad) * radiusPx).roundToInt())
                    }
                    .size(satelliteSize)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

/**
 * Spices, leaves and sparkles drifting slowly upward over a screen - the
 * "everything moves a little" feel of the reference video. One shared
 * animation clock; each particle only reads it in the layout/draw phase.
 */
@Composable
fun FloatingFoodParticles(
    modifier: Modifier = Modifier,
    emojis: List<String> = listOf("🌶️", "🍃", "✨", "🧄", "🍋", "✨", "🌿", "🍛", "✨", "🌶️"),
    durationMillis: Int = 14_000
) {
    val transition = rememberInfiniteTransition(label = "particles")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "particleClock"
    )
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        emojis.forEachIndexed { i, emoji ->
            // Fixed pseudo-random lane, size and phase per particle.
            val lane = ((i * 37) % 100) / 100f
            val phase = ((i * 53) % 100) / 100f
            val fontSize = (14 + (i * 7) % 12).sp
            Text(
                emoji,
                fontSize = fontSize,
                modifier = Modifier.graphicsLayer {
                    val p = (clock.value + phase) % 1f
                    translationX = lane * widthPx * 0.92f + sin(p * 6.283f * 2 + i) * 18f
                    translationY = heightPx * (1f - p) - 40f
                    rotationZ = (p * 360f * if (i % 2 == 0) 1 else -1) / 3
                    // Fade in at the bottom, out at the top.
                    alpha = 0.75f * minOf(1f, p * 5f, (1f - p) * 5f)
                }
            )
        }
    }
}
