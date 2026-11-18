package com.ratulhossen.tiffinbox.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.DeliveryContext
import com.ratulhossen.tiffinbox.data.DeliveryQuoter
import com.ratulhossen.tiffinbox.data.FavoritesStore
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.ui.components.ChefCard
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState
import com.ratulhossen.tiffinbox.util.withRealDistances

/**
 * Two parts: favourite foods (a specific dish from a kitchen) and favourite
 * kitchens. Hearts on meal cards / Meal Details fill the first, hearts on
 * kitchen cards the second.
 */
@Composable
fun FavoritesScreen(onChefClick: (String) -> Unit, onMealClick: (String) -> Unit = {}) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var allChefs by remember { mutableStateOf<List<ChefUi>>(emptyList()) }
    var allMeals by remember { mutableStateOf(FirestoreCatalogRepository.getCachedMeals()) }
    var hasRealDistances by remember { mutableStateOf(false) }
    var deliveryContext by remember { mutableStateOf<DeliveryContext?>(null) }

    LaunchedEffect(Unit) {
        val firestoreChefs = FirestoreCatalogRepository.getChefs()
        // Distances and the delivery fee, same as Home and the chef list.
        val uid = FirebaseAuthRepository.currentUser?.uid
        val myLocation = uid?.let { FirestoreUserRepository.getLocation(it) }
        allChefs = if (myLocation != null) firestoreChefs.withRealDistances(myLocation) else firestoreChefs
        hasRealDistances = myLocation != null
        deliveryContext = DeliveryQuoter.context()
        allMeals = FirestoreCatalogRepository.getAllMeals()
    }

    val favoriteMeals = allMeals.filter { FavoritesStore.isMealFavorite(it.id) }
    val favoriteChefs = allChefs.filter { FavoritesStore.isFavorite(it.id) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.favorites_tab_food, favoriteMeals.size)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.favorites_tab_kitchen, favoriteChefs.size)) }
            )
        }

        if (selectedTab == 0) {
            FavoriteMealsList(favoriteMeals, onMealClick)
        } else {
            FavoriteKitchensGrid(favoriteChefs, hasRealDistances, deliveryContext, onChefClick)
        }
    }
}

@Composable
private fun FavoriteMealsList(meals: List<MealUi>, onMealClick: (String) -> Unit) {
    if (meals.isEmpty()) {
        EmptyFavorites(
            title = stringResource(R.string.favorites_food_empty_title),
            subtitle = stringResource(R.string.favorites_food_empty_subtitle)
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(meals, key = { it.id }) { meal ->
            MealCard(meal = meal, onClick = { onMealClick(meal.id) })
        }
    }
}

@Composable
private fun FavoriteKitchensGrid(
    chefs: List<ChefUi>,
    hasRealDistances: Boolean,
    deliveryContext: DeliveryContext?,
    onChefClick: (String) -> Unit
) {
    if (chefs.isEmpty()) {
        EmptyFavorites(
            title = stringResource(R.string.favorites_empty_title),
            subtitle = stringResource(R.string.favorites_empty_subtitle)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(chefs, key = { it.id }) { chef ->
            ChefCard(
                chef = chef,
                delivery = deliveryContext?.takeIf { hasRealDistances }
                    ?.let { DeliveryQuoter.quote(chef, chef.distanceKm, it) },
                onClick = { onChefClick(chef.id) }
            )
        }
    }
}

@Composable
private fun EmptyFavorites(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PhotoEmptyState(
            resId = R.drawable.empty_favorites,
            title = title,
            subtitle = subtitle
        )
    }
}
