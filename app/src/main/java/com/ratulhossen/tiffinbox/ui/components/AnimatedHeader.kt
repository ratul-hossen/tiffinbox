package com.ratulhossen.tiffinbox.ui.components

import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
 * A big plate in the middle with smaller food photos around it, like the
 * reference video: the ring turns one slot with an ease-in-out, holds, then
 * turns again (a choreographed "rotate - settle - rotate", not a constant
 * screensaver spin). The plate in front grows slightly as it arrives.
 */
@Composable
fun FoodOrbit(
    @DrawableRes center: Int,
    satellites: List<Int>,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val step = 360f / satellites.size.coerceAtLeast(1)
    val angle = remember { Animatable(-90f) }
    LaunchedEffect(satellites.size) {
        while (true) {
            delay(2200)
            angle.animateTo(angle.value + step, tween(900, easing = FastOutSlowInEasing))
        }
    }
    val satelliteSize = size * 0.28f
    val radiusPx = with(LocalDensity.current) { ((size - satelliteSize) / 2).toPx() }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Central plate turns a little the other way with each step.
        Image(
            painter = painterResource(center),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size * 0.5f)
                .graphicsLayer { rotationZ = -angle.value / 3 }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
        )
        satellites.forEachIndexed { index, res ->
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
                    .graphicsLayer {
                        // The slot at the bottom-front is the "featured" one.
                        val rad = Math.toRadians((angle.value + index * step - 90.0))
                        val front = cos(rad).coerceAtLeast(0.0)
                        val grow = 1f + 0.28f * (front * front * front * front).toFloat()
                        scaleX = grow
                        scaleY = grow
                    }
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}
