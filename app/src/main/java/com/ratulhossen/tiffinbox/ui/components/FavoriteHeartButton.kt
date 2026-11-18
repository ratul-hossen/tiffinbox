package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R

private val FavoriteHeartRed = Color(0xFFE0303D)

/** Round heart toggle used on food photos (same look as the kitchen card's heart). */
@Composable
fun FavoriteHeartButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp
) {
    val heartScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favoriteHeartScale"
    )
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(onClick = onToggle)
            .padding(4.dp)
    ) {
        Icon(
            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.content_description_favorite),
            tint = if (isFavorite) FavoriteHeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(iconSize)
                .scale(heartScale)
        )
    }
}
