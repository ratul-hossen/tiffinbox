package com.ratulhossen.tiffinbox.ui.screens.meal

import com.ratulhossen.tiffinbox.data.AppConfigStore
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import com.ratulhossen.tiffinbox.util.ImageCompressor
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.components.NewKitchenBadge
import com.ratulhossen.tiffinbox.ui.components.isNewKitchen
import com.ratulhossen.tiffinbox.ui.components.cardEdge
import com.ratulhossen.tiffinbox.data.model.ChefUi
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import com.ratulhossen.tiffinbox.ui.components.LightStatusBarIcons
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.ui.components.QuantityStepper
import com.ratulhossen.tiffinbox.ui.components.ShimmerBox
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.DeliveryQuoteLine
import com.ratulhossen.tiffinbox.data.DeliveryQuoter
import com.ratulhossen.tiffinbox.domain.DeliveryQuote
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.TopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    mealId: String,
    onBack: () -> Unit,
    onAddedToCart: () -> Unit,
    onChefClick: (String) -> Unit = {}
) {
    var mealState by remember(mealId) { mutableStateOf<MealUi?>(SampleData.mealById(mealId)) }
    var isLoading by remember(mealId) { mutableStateOf(mealState == null) }

    LaunchedEffect(mealId) {
        if (mealState == null) {
            mealState = FirestoreCatalogRepository.getMeal(mealId)
        }
        isLoading = false
    }

    if (mealState == null) {
        if (isLoading) {
            MealDetailShimmer()
        }
        return
    }
    val meal = mealState!!
    // Who cooks it and how they're rated - the key thing to know before ordering.
    var chef by remember(meal.chefId) { mutableStateOf<ChefUi?>(null) }
    var deliveryQuote by remember(meal.chefId) { mutableStateOf<DeliveryQuote?>(null) }
    LaunchedEffect(meal.chefId) {
        chef = FirestoreCatalogRepository.getChef(meal.chefId)
        deliveryQuote = DeliveryQuoter.quote(meal.chefId)
    }
    // The header/photo runs behind the status bar - keep its icons white.
    LightStatusBarIcons()
    var quantity by remember(mealId) { mutableIntStateOf(1) }
    // Today's plates cap an order for now; a pre-order (when sold out) is capped at 10.
    val maxQuantity = if (meal.availableQuantity > 0) meal.availableQuantity else AppConfigStore.preOrderMaxPlates

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuantityStepper(
                        quantity = quantity,
                        onDecrease = { if (quantity > 1) quantity-- },
                        onIncrease = { if (quantity < maxQuantity) quantity++ },
                        canDecrease = quantity > 1,
                        canIncrease = quantity < maxQuantity
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TiffinButton(
                        onClick = {
                            CartStore.add(meal, quantity)
                            onAddedToCart()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        // Sold out today: it can still be pre-ordered for a later slot.
                        Text(
                            if (meal.availableQuantity > 0) {
                                stringResource(R.string.meal_add_to_cart_button, meal.price * quantity)
                            } else {
                                stringResource(R.string.meal_preorder_button, meal.price * quantity)
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // No top bar: the photo runs edge to edge up behind the status bar,
        // with the back button floating on it.
        Column(
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // The chef's food photo in the same 4:3 frame it was cropped to on
            // upload; meals without one keep the emoji placeholder.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ImageCompressor.MEAL_ASPECT)
            ) {
                Base64Thumbnail(
                    base64 = meal.photoBase64,
                    emoji = meal.emoji,
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    emojiFontSize = 72.sp
                )
                // Soft dark fade at the top so the status bar and back button stay readable.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                        tint = Color.White
                    )
                }
                if (meal.isTodaySpecial) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.home_hero_badge),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Text(meal.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(6.dp))
                chef?.let { c ->
                    ChefByline(chef = c, delivery = deliveryQuote, onClick = { onChefClick(c.id) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    stringResource(R.string.meal_price_portion, meal.price, meal.portionSize),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(meal.description, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(20.dp))
                Text(stringResource(R.string.meal_dietary_tags_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                val generalTag = stringResource(R.string.meal_general_tag)
                InfoPillRow(meal.dietaryTags.ifEmpty { listOf(generalTag) })

                // Only shown when the meal actually lists allergens.
                val allergens = meal.allergens.filter { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
                if (allergens.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.meal_allergens_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoPillRow(allergens)
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Amber normally; red only when stock is really running out.
                Text(
                    if (meal.availableQuantity > 0) stringResource(R.string.meal_plates_left, meal.availableQuantity)
                    else stringResource(R.string.meal_sold_out_preorder_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (meal.availableQuantity <= 3) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun MealDetailShimmer() {
    Column(modifier = Modifier.fillMaxSize()) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(180.dp), cornerRadius = 0.dp)
        Column(modifier = Modifier.padding(20.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(24.dp))
            Spacer(modifier = Modifier.height(10.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.3f).height(18.dp))
            Spacer(modifier = Modifier.height(16.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
        }
    }
}

/** Read-only info pills (not chips - nothing happens on tap, so they don't look tappable). */
@Composable
private fun InfoPillRow(labels: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels) { label ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** "Cooked by" row: kitchen photo, name, rating (or "New") and delivery quote; opens the kitchen. */
@Composable
private fun ChefByline(chef: ChefUi, delivery: DeliveryQuote? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = cardEdge(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Base64Thumbnail(
                base64 = chef.photoBase64,
                emoji = chef.emoji,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                emojiFontSize = 22.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.meal_by_kitchen),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    chef.kitchenName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                delivery?.let { DeliveryQuoteLine(it) }
            }
            if (isNewKitchen(chef)) {
                NewKitchenBadge()
            } else {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    stringResource(R.string.chef_card_rating_orders, chef.overallRating.toString(), chef.totalOrders.toString()),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
