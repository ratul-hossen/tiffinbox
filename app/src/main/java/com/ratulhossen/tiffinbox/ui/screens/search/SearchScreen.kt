package com.ratulhossen.tiffinbox.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.domain.ml.MlSearchIntentModel
import com.ratulhossen.tiffinbox.domain.ml.PriceTier
import com.ratulhossen.tiffinbox.domain.ml.SearchIntent
import com.ratulhossen.tiffinbox.ui.components.MealCard

/** BDT price bands matching the tiers MlSearchIntentModel.kt's price_tier_intent head was trained on. */
private fun priceTierOf(price: Int): PriceTier = when {
    price < 150 -> PriceTier.BUDGET
    price <= 300 -> PriceTier.MID
    else -> PriceTier.PREMIUM
}

private fun intentMatchScore(meal: MealUi, intent: SearchIntent): Double {
    var score = 0.0
    if (meal.isVeg) score += intent.vegProbability else score += (1.0 - intent.vegProbability)
    if (intent.priceTier != null && priceTierOf(meal.price) == intent.priceTier) {
        score += intent.priceTierConfidence
    }
    return score
}

@Composable
fun SearchScreen(onMealClick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var vegOnly by remember { mutableStateOf(false) }
    var allMeals by remember { mutableStateOf<List<MealUi>>(emptyList()) }

    LaunchedEffect(Unit) {
        val firestoreMeals = FirestoreCatalogRepository.getAllMeals()
        allMeals = firestoreMeals
    }

    // Natural-language search intent (veg + price-tier), predicted on-device
    // by MlSearchIntentModel.kt - a text classifier trained offline on a
    // public Swiggy restaurant-name dataset (see ml/ at the repo root). This
    // doesn't replace the substring match below (so it never hides results
    // the user typed exactly) - it only re-ranks them, so e.g. "cheap veg
    // food" floats cheap vegetarian dishes to the top even when none of
    // those words appear in a dish's name/description.
    val searchIntent = remember(query) { MlSearchIntentModel.predict(query) }

    val results = remember(query, selectedCategory, vegOnly, allMeals, searchIntent) {
        val filtered = allMeals.filter { meal ->
            val matchesQuery = query.isBlank() ||
                meal.name.contains(query, ignoreCase = true) ||
                meal.description.contains(query, ignoreCase = true) ||
                meal.dietaryTags.any { it.contains(query, ignoreCase = true) }
            val matchesCategory = selectedCategory == null || meal.category == selectedCategory
            val matchesVeg = !vegOnly || meal.isVeg
            matchesQuery && matchesCategory && matchesVeg
        }
        val intent = searchIntent
        if (intent == null) {
            filtered
        } else {
            filtered.sortedByDescending { meal -> intentMatchScore(meal, intent) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = vegOnly,
                    onClick = { vegOnly = !vegOnly },
                    label = { Text(stringResource(R.string.search_veg_only_chip)) }
                )
            }
            items(SampleData.categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = if (selectedCategory == category) null else category
                    },
                    label = { Text(category) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (results.isEmpty()) {
            EmptySearchState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.id }) { meal: MealUi ->
                    MealCard(meal = meal, onClick = { onMealClick(meal.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🍽️", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.search_empty_state_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
