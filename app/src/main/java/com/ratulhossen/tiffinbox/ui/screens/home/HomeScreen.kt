package com.ratulhossen.tiffinbox.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.domain.RecommendationEngine
import com.ratulhossen.tiffinbox.domain.ml.MlRecommendModel
import com.ratulhossen.tiffinbox.ui.components.ChefCard
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.util.NEARBY_RADIUS_KM
import com.ratulhossen.tiffinbox.util.withRealDistances

@Composable
fun HomeScreen(
    onChefClick: (String) -> Unit,
    onMealClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSeeAllClick: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var chefs by remember { mutableStateOf(SampleData.chefs) }
    var aiRecommendedMeals by remember { mutableStateOf<List<MealUi>>(emptyList()) }
    // Only true once we've replaced the static/demo distanceKm with a real
    // haversine distance from the customer's own saved location - until
    // then the 2-3km "nearby" radius filter below would be meaningless.
    var hasRealDistances by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // "AI Recommended For You" - ranked by MlRecommendModel.kt, a logistic
        // regression trained offline on a public Swiggy restaurant dataset
        // (see ml/ at the repo root), run entirely on-device from the meal's
        // own price/isVeg/availableQuantity - no server involved.
        val allMeals = FirestoreCatalogRepository.getAllMeals()
        if (allMeals.isNotEmpty()) {
            aiRecommendedMeals = allMeals
                .sortedByDescending { meal ->
                    MlRecommendModel.predict(
                        avgPrice = meal.price.toDouble(),
                        vegFraction = if (meal.isVeg) 1.0 else 0.0,
                        numOffers = 0.0,
                        popularity = meal.availableQuantity.toDouble()
                    )
                }
                .take(10)
        }

        val firestoreChefs = FirestoreCatalogRepository.getChefs()
        if (firestoreChefs.isEmpty()) return@LaunchedEffect
        val uid = FirebaseAuthRepository.currentUser?.uid
        val myLocation = uid?.let { FirestoreUserRepository.getLocation(it) }
        if (myLocation != null) {
            chefs = firestoreChefs.withRealDistances(myLocation)
            hasRealDistances = true
        } else {
            chefs = firestoreChefs
        }
    }

    val recommendedChefs = remember(chefs) { RecommendationEngine.recommend(chefs) }
    val nearbyChefs = remember(chefs, hasRealDistances) {
        val sorted = chefs.sortedBy { it.distanceKm }
        if (hasRealDistances) sorted.filter { it.distanceKm <= NEARBY_RADIUS_KM } else sorted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.home_location), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_greeting),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfileClick)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = stringResource(R.string.home_profile_content_description),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Surface(
            onClick = onSearchClick,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.home_search_content_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(
            title = stringResource(R.string.home_section_recommended),
            onSeeAllClick = { onSeeAllClick("recommended") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recommendedChefs) { chef ->
                ChefCard(
                    chef = chef,
                    modifier = Modifier.width(180.dp),
                    onClick = { onChefClick(chef.id) }
                )
            }
        }

        if (aiRecommendedMeals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.home_section_ai_recommended),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(aiRecommendedMeals, key = { it.id }) { meal ->
                    MealCard(
                        meal = meal,
                        modifier = Modifier.width(220.dp),
                        onClick = { onMealClick(meal.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(
            title = stringResource(R.string.home_section_nearby),
            onSeeAllClick = { onSeeAllClick("nearby") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(nearbyChefs) { chef ->
                ChefCard(
                    chef = chef,
                    modifier = Modifier.width(180.dp),
                    onClick = { onChefClick(chef.id) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAllClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.home_see_all),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onSeeAllClick)
        )
    }
}
