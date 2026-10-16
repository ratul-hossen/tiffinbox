package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import com.ratulhossen.tiffinbox.R
import androidx.annotation.RawRes
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bundled photo (res/drawable-nodpi) cropped to fill its bounds, with an
 * optional [scrim] painted on top so overlaid text stays readable.
 */
@Composable
fun FullBleedPhoto(
    @DrawableRes resId: Int,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    scrim: Brush? = null,
    /** A single gentle "settle-in" zoom when the screen opens (1.08x -> 1x), then it stays still. */
    animated: Boolean = true
) {
    val zoom = remember { Animatable(if (animated) 1.08f else 1f) }
    LaunchedEffect(animated) {
        if (animated) zoom.animateTo(1f, tween(2400, easing = FastOutSlowInEasing))
    }
    Box(modifier = modifier.clipToBounds()) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                }
        )
        if (scrim != null) {
            Box(modifier = Modifier.fillMaxSize().background(scrim))
        }
    }
}

/** Bottom-heavy dark gradient for white text laid over a photo. */
fun bottomScrim(strength: Float = 0.85f): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Transparent,
    1f to Color.Black.copy(alpha = strength)
)

/** Round photo with a soft shadow - category tiles and empty-state illustrations. */
@Composable
fun CirclePhoto(
    @DrawableRes resId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 10.dp
) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .shadow(elevation, CircleShape)
            .clip(CircleShape)
    )
}

/** Photo + title + subtitle, centered - shared look for every "nothing here yet" screen. */
@Composable
fun PhotoEmptyState(
    @DrawableRes resId: Int,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    /** Gently bobbing bowl motion graphic; null shows the [resId] photo instead. */
    @RawRes animation: Int? = R.raw.lottie_empty_bowl
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (animation != null) {
            TiffinLottie(res = animation, modifier = Modifier.size(180.dp))
        } else {
            CirclePhoto(resId = resId, size = 168.dp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
