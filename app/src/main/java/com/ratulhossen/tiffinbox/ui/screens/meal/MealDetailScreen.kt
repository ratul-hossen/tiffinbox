package com.ratulhossen.tiffinbox.ui.screens.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.ui.components.ShimmerBox

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
        topBar = {
            TopAppBar(
                title = { Text("Meal Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                    IconButton(
                        onClick = { if (quantity > 1) quantity-- },
                        enabled = quantity > 1
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                    }
                    Text("$quantity", style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = { if (quantity < meal.availableQuantity) quantity++ },
                        enabled = quantity < meal.availableQuantity
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
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
                                "Add to Cart • ৳${meal.price * quantity}"
                            } else {
                                "আজকের জন্য শেষ"
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(meal.emoji, fontSize = 72.sp)
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Text(meal.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "৳${meal.price}  •  ${meal.portionSize}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(meal.description, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(20.dp))
                Text("Dietary Tags", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(meal.dietaryTags.ifEmpty { listOf("General") }) { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Allergens", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(meal.allergens) { allergen ->
                        AssistChip(onClick = {}, label = { Text(allergen) })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "● ${meal.availableQuantity} plates left today",
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
