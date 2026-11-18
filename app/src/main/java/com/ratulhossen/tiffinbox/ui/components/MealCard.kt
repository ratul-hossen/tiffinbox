package com.ratulhossen.tiffinbox.ui.components

import com.ratulhossen.tiffinbox.data.AppConfigStore
import com.ratulhossen.tiffinbox.data.FavoritesStore
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.util.ImageCompressor

@Composable
fun MealCard(
    meal: MealUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "mealCardPressScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardEdge(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .aspectRatio(ImageCompressor.MEAL_ASPECT)
            ) {
                Base64Thumbnail(
                    base64 = meal.photoBase64,
                    emoji = meal.emoji,
                    // Same 4:3 frame food photos are cropped to on upload.
                    modifier = Modifier.fillMaxSize()
                )
                // Save this dish to Favorites -> Food.
                FavoriteHeartButton(
                    isFavorite = FavoritesStore.isMealFavorite(meal.id),
                    onToggle = { FavoritesStore.toggleMeal(meal.id) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (meal.isTodaySpecial) {
                    Text(
                        stringResource(R.string.home_hero_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    meal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    meal.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.price_tag, meal.price),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PlatesLeftBadge(meal.availableQuantity)
                }
            }
        }
    }
}

/**
 * "3 plates left" when today's stock is low (so a customer knows the chef can
 * feed only a few more people), "Sold out" at 0, nothing when plenty remain.
 */
@Composable
fun PlatesLeftBadge(platesLeft: Int, modifier: Modifier = Modifier) {
    if (platesLeft > AppConfigStore.fewPlatesThreshold) return
    val soldOut = platesLeft <= 0
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (soldOut) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
        modifier = modifier
    ) {
        Text(
            if (soldOut) stringResource(R.string.meal_badge_sold_out) else stringResource(R.string.meal_badge_plates_left, platesLeft),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (soldOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
