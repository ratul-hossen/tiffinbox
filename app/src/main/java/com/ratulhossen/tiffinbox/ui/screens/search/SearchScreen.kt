package com.ratulhossen.tiffinbox.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.DeliveryContext
import com.ratulhossen.tiffinbox.data.DeliveryQuoter
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.domain.ml.MlSearchIntentModel
import com.ratulhossen.tiffinbox.domain.ml.PriceTier
import com.ratulhossen.tiffinbox.domain.ml.SearchIntent
import com.ratulhossen.tiffinbox.ui.components.ChefCard
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState
import com.ratulhossen.tiffinbox.util.RECOMMEND_RADIUS_KM
import com.ratulhossen.tiffinbox.util.withRealDistances

private enum class SearchMode { FOOD, KITCHENS }

/** BDT price bands matching MlSearchIntentModel's price_tier_intent head. */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMealClick: (String) -> Unit,
    onChefClick: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var vegOnly by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(SearchMode.FOOD) }
    // true = within RECOMMEND_RADIUS_KM (10 km); false = all kitchens
    var nearbyOnly by remember { mutableStateOf(true) }

    var allMeals by remember { mutableStateOf<List<MealUi>>(emptyList()) }
    var allChefs by remember { mutableStateOf<List<ChefUi>>(emptyList()) }
    var hasRealDistances by remember { mutableStateOf(false) }
    var deliveryContext by remember { mutableStateOf<DeliveryContext?>(null) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuthRepository.currentUser?.uid
        val myLocation = uid?.let { FirestoreUserRepository.getLocation(it) }
        val firestoreChefs = FirestoreCatalogRepository.getChefs()
        allChefs = if (myLocation != null) {
            hasRealDistances = true
            firestoreChefs.withRealDistances(myLocation)
        } else {
            firestoreChefs
        }
        allMeals = FirestoreCatalogRepository.getAllMeals()
        deliveryContext = DeliveryQuoter.context()
    }

    // Natural-language intent for food re-ranking (veg + price-tier).
    // Re-ranking only: never hides an exact text match.
    val searchIntent = remember(query) { MlSearchIntentModel.predict(query) }

    val mealResults = remember(query, selectedCategory, vegOnly, allMeals, searchIntent) {
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
        if (intent == null) filtered
        else filtered.sortedByDescending { meal -> intentMatchScore(meal, intent) }
    }

    val chefResults = remember(query, nearbyOnly, hasRealDistances, allChefs) {
        allChefs.filter { chef ->
            val matchesQuery = query.isBlank() ||
                chef.kitchenName.contains(query, ignoreCase = true) ||
                chef.specialty.any { it.contains(query, ignoreCase = true) } ||
                chef.bio.contains(query, ignoreCase = true)
            // Distance filter only applies when we have real coordinates.
            val matchesDistance = !nearbyOnly || !hasRealDistances ||
                chef.distanceKm <= RECOMMEND_RADIUS_KM
            matchesQuery && matchesDistance
        }.sortedBy { it.distanceKm }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        stringResource(
                            if (searchMode == SearchMode.FOOD) R.string.search_placeholder
                            else R.string.search_kitchens_placeholder
                        )
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Mode toggle: Food | Kitchens
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = searchMode == SearchMode.FOOD,
                    onClick = { searchMode = SearchMode.FOOD },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(R.string.search_tab_food)) }
                )
                SegmentedButton(
                    selected = searchMode == SearchMode.KITCHENS,
                    onClick = { searchMode = SearchMode.KITCHENS },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(R.string.search_tab_kitchens)) }
                )
            }
        }

        // Filter chips: category/veg for Food mode, distance for Kitchens mode.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchMode == SearchMode.FOOD) {
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
            } else {
                item {
                    FilterChip(
                        selected = nearbyOnly,
                        onClick = { nearbyOnly = true },
                        label = { Text(stringResource(R.string.search_distance_nearby)) }
                    )
                }
                item {
                    FilterChip(
                        selected = !nearbyOnly,
                        onClick = { nearbyOnly = false },
                        label = { Text(stringResource(R.string.search_distance_all)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (searchMode == SearchMode.FOOD) {
            if (mealResults.isEmpty()) {
                EmptySearchState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mealResults, key = { it.id }) { meal: MealUi ->
                        MealCard(meal = meal, onClick = { onMealClick(meal.id) })
                    }
                }
            }
        } else {
            if (chefResults.isEmpty()) {
                EmptySearchState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chefResults, key = { it.id }) { chef: ChefUi ->
                        ChefCard(
                            chef = chef,
                            delivery = deliveryContext?.takeIf { hasRealDistances }
                                ?.let { DeliveryQuoter.quote(chef, chef.distanceKm, it) },
                            onClick = { onChefClick(chef.id) }
                        )
                    }
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
        PhotoEmptyState(
            resId = R.drawable.empty_search,
            title = stringResource(R.string.search_empty_state_message)
        )
    }
}
