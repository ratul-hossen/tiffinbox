package com.ratulhossen.tiffinbox.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.DeliveryContext
import com.ratulhossen.tiffinbox.data.DeliveryQuoter
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.location.UserLocationCache
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.domain.ml.MlSearchIntentModel
import com.ratulhossen.tiffinbox.domain.ml.MlSearchMatcher
import com.ratulhossen.tiffinbox.ui.components.ChefCard
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState
import com.ratulhossen.tiffinbox.util.RECOMMEND_RADIUS_KM
import com.ratulhossen.tiffinbox.util.withRealDistances

private enum class SearchMode { FOOD, KITCHENS }

enum class FoodSortOption(val label: String) {
    RELEVANCE("প্রাসঙ্গিক (Relevance)"),
    PRICE_LOW("দাম: কম থেকে বেশি"),
    PRICE_HIGH("দাম: বেশি থেকে কম"),
    RATING("টপ রেটেড (৪.০+ ★)"),
    DISTANCE("নিকটবর্তী কিচেন")
}

enum class PriceFilterOption(val label: String) {
    ALL("সব দাম"),
    UNDER_100("Under ৳১০০"),
    BETWEEN_100_200("৳১০০ - ৳২০০"),
    ABOVE_200("৳২০০+")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMealClick: (String) -> Unit,
    onChefClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uid = FirebaseAuthRepository.currentUser?.uid
    val cachedLocation = remember(uid) { UserLocationCache.read(context, uid) }

    var query by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.FOOD) }

    // Filter states
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var vegOnly by remember { mutableStateOf(false) }
    var inStockOnly by remember { mutableStateOf(false) }
    var todaySpecialOnly by remember { mutableStateOf(false) }
    var lessOilOnly by remember { mutableStateOf(false) }
    var topRatedOnly by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(FoodSortOption.RELEVANCE) }
    var priceFilter by remember { mutableStateOf(PriceFilterOption.ALL) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Kitchen mode filter. Starts on "All": the map/location features aren't
    // complete yet, so a kitchen search covers every kitchen by default and
    // "Nearby" is an opt-in narrowing.
    var nearbyOnly by remember { mutableStateOf(false) }

    var allMeals by remember { mutableStateOf(FirestoreCatalogRepository.getCachedMeals()) }
    var allChefs by remember {
        mutableStateOf(
            FirestoreCatalogRepository.getCachedChefs().let { list ->
                if (cachedLocation != null) list.withRealDistances(cachedLocation) else list
            }
        )
    }
    var hasRealDistances by remember { mutableStateOf(cachedLocation != null) }
    var deliveryContext by remember { mutableStateOf<DeliveryContext?>(null) }

    LaunchedEffect(Unit) {
        val myLocation = uid?.let { FirestoreUserRepository.getLocation(it) } ?: cachedLocation
        if (uid != null && myLocation != null) {
            UserLocationCache.write(context, uid, myLocation)
        }
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

    // Active filters count for Food mode
    val activeFilterCount = (if (sortBy != FoodSortOption.RELEVANCE) 1 else 0) +
        (if (priceFilter != PriceFilterOption.ALL) 1 else 0) +
        (if (vegOnly) 1 else 0) +
        (if (inStockOnly) 1 else 0) +
        (if (todaySpecialOnly) 1 else 0) +
        (if (lessOilOnly) 1 else 0) +
        (if (topRatedOnly) 1 else 0) +
        (if (selectedCategory != null) 1 else 0)

    // Natural-language intent for food re-ranking (veg + price-tier)
    val searchIntent = remember(query) { MlSearchIntentModel.predict(query) }

    val scoredMealResults = remember(query, selectedCategory, vegOnly, allMeals, searchIntent) {
        MlSearchMatcher.searchMeals(
            query = query,
            allMeals = allMeals,
            selectedCategory = selectedCategory,
            vegOnly = vegOnly,
            searchIntent = searchIntent
        )
    }

    // Apply advanced filters & sorting
    val filteredAndSortedMeals = remember(
        scoredMealResults,
        sortBy,
        priceFilter,
        inStockOnly,
        todaySpecialOnly,
        lessOilOnly,
        topRatedOnly,
        allChefs
    ) {
        val chefById = allChefs.associateBy { it.id }
        var list = scoredMealResults.map { it.item }

        if (priceFilter != PriceFilterOption.ALL) {
            list = list.filter { meal ->
                when (priceFilter) {
                    PriceFilterOption.UNDER_100 -> meal.price <= 100
                    PriceFilterOption.BETWEEN_100_200 -> meal.price in 101..200
                    PriceFilterOption.ABOVE_200 -> meal.price > 200
                    PriceFilterOption.ALL -> true
                }
            }
        }
        if (inStockOnly) {
            list = list.filter { it.availableQuantity > 0 }
        }
        if (todaySpecialOnly) {
            list = list.filter { it.isTodaySpecial }
        }
        if (lessOilOnly) {
            list = list.filter { meal ->
                meal.dietaryTags.any {
                    it.contains("oil", ignoreCase = true) || it.contains("health", ignoreCase = true) || it.contains("কম তেল", ignoreCase = true)
                } || meal.description.contains("less oil", ignoreCase = true) || meal.description.contains("কম তেল", ignoreCase = true)
            }
        }
        if (topRatedOnly) {
            list = list.filter { meal ->
                val chef = chefById[meal.chefId]
                (chef?.overallRating ?: 0.0) >= 4.0
            }
        }

        when (sortBy) {
            FoodSortOption.RELEVANCE -> list
            FoodSortOption.PRICE_LOW -> list.sortedBy { it.price }
            FoodSortOption.PRICE_HIGH -> list.sortedByDescending { it.price }
            FoodSortOption.RATING -> list.sortedByDescending { chefById[it.chefId]?.overallRating ?: 0.0 }
            FoodSortOption.DISTANCE -> list.sortedBy { chefById[it.chefId]?.distanceKm ?: Double.MAX_VALUE }
        }
    }

    val hasCloseMatchesOnly = remember(scoredMealResults, query) {
        query.isNotBlank() && scoredMealResults.isNotEmpty() && scoredMealResults.none { it.isExactOrPrefix }
    }

    val scoredChefResults = remember(query, nearbyOnly, hasRealDistances, allChefs) {
        MlSearchMatcher.searchChefs(
            query = query,
            allChefs = allChefs,
            nearbyOnly = nearbyOnly,
            hasRealDistances = hasRealDistances,
            recommendRadiusKm = RECOMMEND_RADIUS_KM
        )
    }

    val chefResults = remember(scoredChefResults) {
        scoredChefResults.map { it.item }
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
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
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

        // Quick Filter chips: Modern food delivery style
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchMode == SearchMode.FOOD) {
                // Main Filter Button with Badge
                item {
                    FilterChip(
                        selected = activeFilterCount > 0,
                        onClick = { showFilterSheet = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters")
                        }
                    )
                }

                // Clear All Button (appears when any filter is active)
                if (activeFilterCount > 0) {
                    item {
                        AssistChip(
                            onClick = {
                                sortBy = FoodSortOption.RELEVANCE
                                priceFilter = PriceFilterOption.ALL
                                vegOnly = false
                                inStockOnly = false
                                todaySpecialOnly = false
                                lessOilOnly = false
                                topRatedOnly = false
                                selectedCategory = null
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Clear All") }
                        )
                    }
                }

                // 1-tap chip: Under ৳100
                item {
                    FilterChip(
                        selected = priceFilter == PriceFilterOption.UNDER_100,
                        onClick = {
                            priceFilter = if (priceFilter == PriceFilterOption.UNDER_100) PriceFilterOption.ALL else PriceFilterOption.UNDER_100
                        },
                        label = { Text("Under ৳১০০") }
                    )
                }

                // 1-tap chip: Available Now
                item {
                    FilterChip(
                        selected = inStockOnly,
                        onClick = { inStockOnly = !inStockOnly },
                        label = { Text("⚡ Available Now") }
                    )
                }

                // 1-tap chip: Top Rated
                item {
                    FilterChip(
                        selected = topRatedOnly,
                        onClick = { topRatedOnly = !topRatedOnly },
                        label = { Text("⭐ ৪.০+ রেটিং") }
                    )
                }

                // 1-tap chip: Pure Veg
                item {
                    FilterChip(
                        selected = vegOnly,
                        onClick = { vegOnly = !vegOnly },
                        label = { Text(stringResource(R.string.search_veg_only_chip)) }
                    )
                }

                // 1-tap chip: Today's Special
                item {
                    FilterChip(
                        selected = todaySpecialOnly,
                        onClick = { todaySpecialOnly = !todaySpecialOnly },
                        label = { Text("🌟 Today's Special") }
                    )
                }

                // 1-tap chip: Less Oil
                item {
                    FilterChip(
                        selected = lessOilOnly,
                        onClick = { lessOilOnly = !lessOilOnly },
                        label = { Text("🌿 কম তেল") }
                    )
                }

                // Category chips
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
                // Kitchens filter chips
                item {
                    FilterChip(
                        selected = !nearbyOnly,
                        onClick = { nearbyOnly = false },
                        label = { Text(stringResource(R.string.search_distance_all)) }
                    )
                }
                item {
                    FilterChip(
                        selected = nearbyOnly,
                        onClick = { nearbyOnly = true },
                        label = { Text(stringResource(R.string.search_distance_nearby)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (searchMode == SearchMode.FOOD) {
            if (hasCloseMatchesOnly) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ হুবহু মেলেনি, কাছাকাছি ফলাফল দেখানো হচ্ছে:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (filteredAndSortedMeals.isEmpty()) {
                EmptySearchState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAndSortedMeals, key = { it.id }) { meal: MealUi ->
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

    // Filter & Sort Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter & Sort",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeFilterCount > 0) {
                        TextButton(
                            onClick = {
                                sortBy = FoodSortOption.RELEVANCE
                                priceFilter = PriceFilterOption.ALL
                                vegOnly = false
                                inStockOnly = false
                                todaySpecialOnly = false
                                lessOilOnly = false
                                topRatedOnly = false
                                selectedCategory = null
                            }
                        ) {
                            Text("Reset All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Sort By
                    Text("Sort By", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FoodSortOption.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sortBy = option }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sortBy == option,
                                onClick = { sortBy = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Price Range", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriceFilterOption.entries.forEach { opt ->
                            FilterChip(
                                selected = priceFilter == opt,
                                onClick = { priceFilter = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Dietary & Quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = inStockOnly,
                            onClick = { inStockOnly = !inStockOnly },
                            label = { Text("⚡ Available Now") }
                        )
                        FilterChip(
                            selected = vegOnly,
                            onClick = { vegOnly = !vegOnly },
                            label = { Text("🥦 Pure Veg") }
                        )
                        FilterChip(
                            selected = topRatedOnly,
                            onClick = { topRatedOnly = !topRatedOnly },
                            label = { Text("⭐ ৪.০+ রেটিং") }
                        )
                        FilterChip(
                            selected = todaySpecialOnly,
                            onClick = { todaySpecialOnly = !todaySpecialOnly },
                            label = { Text("🌟 Today's Special") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Meal Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SampleData.categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Apply Filters (${filteredAndSortedMeals.size} items)",
                        fontWeight = FontWeight.Bold
                    )
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
