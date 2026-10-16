package com.ratulhossen.tiffinbox.ui.screens.meal

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
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.ui.components.QuantityStepper
import com.ratulhossen.tiffinbox.ui.components.ShimmerBox
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    mealId: String,
    onBack: () -> Unit,
    onAddedToCart: () -> Unit
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
    var quantity by remember(mealId) { mutableIntStateOf(1) }

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.meal_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        },
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
                        onIncrease = { if (quantity < meal.availableQuantity) quantity++ },
                        canDecrease = quantity > 1,
                        canIncrease = quantity < meal.availableQuantity
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TiffinButton(
                        onClick = {
                            CartStore.add(meal, quantity)
                            onAddedToCart()
                        },
                        enabled = meal.availableQuantity > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            if (meal.availableQuantity > 0) {
                                stringResource(R.string.meal_add_to_cart_button, meal.price * quantity)
                            } else {
                                stringResource(R.string.meal_sold_out_today)
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
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
                if (meal.isTodaySpecial) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(meal.dietaryTags.ifEmpty { listOf(generalTag) }) { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.meal_allergens_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(meal.allergens) { allergen ->
                        AssistChip(onClick = {}, label = { Text(allergen) })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.meal_plates_left, meal.availableQuantity),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
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
