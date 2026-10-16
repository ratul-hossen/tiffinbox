package com.ratulhossen.tiffinbox.ui.screens.cheflist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Map
import com.ratulhossen.tiffinbox.ui.components.DistanceFilterChips
import com.ratulhossen.tiffinbox.domain.DeliveryQuotes
import com.ratulhossen.tiffinbox.domain.DeliveryPricing
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCouponRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreReviewRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreAppConfigRepository
import com.ratulhossen.tiffinbox.domain.RecommendationContext
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.domain.RecommendationEngine
import com.ratulhossen.tiffinbox.ui.components.ChefCard
import com.ratulhossen.tiffinbox.util.NEARBY_RADIUS_KM
import com.ratulhossen.tiffinbox.util.recommendable
import com.ratulhossen.tiffinbox.util.withRealDistances
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults

private enum class ChefSort {
    RECOMMENDED,
    NEARBY,
    RATING
}

@Composable
private fun ChefSort.label(): String = when (this) {
    ChefSort.RECOMMENDED -> stringResource(R.string.chef_sort_recommended)
    ChefSort.NEARBY -> stringResource(R.string.chef_sort_nearby)
    ChefSort.RATING -> stringResource(R.string.chef_sort_top_rated)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefListScreen(
    initialSort: String,
    onBack: () -> Unit,
    onChefClick: (String) -> Unit,
    onMapClick: () -> Unit = {}
) {
    var chefs by remember { mutableStateOf<List<ChefUi>>(emptyList()) }
    var hasRealDistances by remember { mutableStateOf(false) }
    var recContext by remember { mutableStateOf(RecommendationContext()) }
    var sort by remember {
        mutableStateOf(
            when (initialSort) {
                "nearby" -> ChefSort.NEARBY
                "rating" -> ChefSort.RATING
                else -> ChefSort.RECOMMENDED
            }
        )
    }

    // Distance filter (only once the customer's location is known); "Nearby" starts at 3 km.
    var maxKm by remember { mutableStateOf(if (initialSort == "nearby") NEARBY_RADIUS_KM else null) }
    var deliveryPricing by remember { mutableStateOf<DeliveryPricing?>(null) }

    LaunchedEffect(Unit) {
        deliveryPricing = FirestoreAppConfigRepository.deliveryPricing()
        val firestoreChefs = FirestoreCatalogRepository.getChefs()
        if (firestoreChefs.isEmpty()) return@LaunchedEffect
        val uid = FirebaseAuthRepository.currentUser?.uid
        val myLocation = uid?.let { FirestoreUserRepository.getLocation(it) }
        recContext = RecommendationContext(
            hasRealDistances = myLocation != null,
            radiusKm = FirestoreAppConfigRepository.recommendRadiusKm(),
            reviewStats = FirestoreReviewRepository.getChefReviewStats(),
            myOrderCounts = FirestoreOrderRepository.getMyOrderSignals().first,
            offerCounts = FirestoreCouponRepository.getActiveOfferCounts()
        )
        if (myLocation != null) {
            chefs = firestoreChefs.withRealDistances(myLocation)
            hasRealDistances = true
        } else {
            chefs = firestoreChefs
        }
    }

    val sortedChefs = remember(chefs, sort, hasRealDistances, recContext, maxKm) {
        val inRange = maxKm?.takeIf { hasRealDistances }?.let { km -> chefs.filter { it.distanceKm <= km } } ?: chefs
        when (sort) {
            ChefSort.RECOMMENDED -> RecommendationEngine.recommend(inRange.recommendable(hasRealDistances, recContext.radiusKm), recContext)
            ChefSort.NEARBY -> inRange.sortedBy { it.distanceKm }
            ChefSort.RATING -> inRange.sortedByDescending { it.overallRating }
        }
    }

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.chef_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    IconButton(onClick = onMapClick) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.chef_map_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChefSort.entries.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { sort = option },
                        label = { Text(option.label()) }
                    )
                }
            }
            if (hasRealDistances) {
                DistanceFilterChips(
                    selectedKm = maxKm,
                    onSelect = { maxKm = it },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (sortedChefs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.chef_list_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedChefs, key = { it.id }) { chef ->
                        ChefCard(
                            chef = chef,
                            modifier = Modifier.fillMaxWidth(),
                            delivery = deliveryPricing?.takeIf { hasRealDistances }?.let { DeliveryQuotes.quote(chef.distanceKm, it) },
                            onClick = { onChefClick(chef.id) }
                        )
                    }
                }
            }
        }
    }
}
