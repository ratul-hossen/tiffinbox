package com.ratulhossen.tiffinbox.ui.screens.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreAppConfigRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.domain.RecommendationContext
import com.ratulhossen.tiffinbox.domain.RecommendationEngine
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.ui.components.MealPlanPromoCard
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState
import com.ratulhossen.tiffinbox.ui.components.cardEdge
import com.ratulhossen.tiffinbox.ui.components.formatDistanceKm
import com.ratulhossen.tiffinbox.util.recommendable
import com.ratulhossen.tiffinbox.util.withRealDistances

/** One kitchen's plan teaser: about how much 7 meals cost there (same formula as SubscribeScreen). */
private data class PlanOffer(val chef: ChefUi, val weekPrice: Int)

/**
 * Meal plans for students: kitchens within reach that offer a weekly /
 * monthly plan, best match first, each with an approximate week price.
 * Tapping one opens that kitchen's plan (SubscribeScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlansScreen(onBack: () -> Unit, onKitchenClick: (String) -> Unit) {
    var offers by remember { mutableStateOf<List<PlanOffer>?>(null) }
    LaunchedEffect(Unit) {
        val chefs = FirestoreCatalogRepository.getChefs()
        val me = FirebaseAuthRepository.currentUser?.uid?.let { FirestoreUserRepository.getLocation(it) }
        val withKm = if (me != null) chefs.withRealDistances(me) else chefs
        val radius = FirestoreAppConfigRepository.recommendRadiusKm()
        val ranked = RecommendationEngine.recommend(
            withKm.recommendable(me != null, radius),
            RecommendationContext(hasRealDistances = me != null, radiusKm = radius)
        )
        val meals = FirestoreCatalogRepository.getAllMeals().groupBy { it.chefId }
        offers = ranked.mapNotNull { chef ->
            if (!chef.mealPlanEnabled) return@mapNotNull null
            val menu = meals[chef.id].orEmpty()
            val perMeal = if (chef.mealPlanPricePerMealMin > 0) chef.mealPlanPricePerMealMin
            else if (menu.isNotEmpty()) menu.map { it.price }.average().toInt()
            else 100
            PlanOffer(chef, perMeal * 7)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.meal_plans_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        val list = offers
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                PhotoEmptyState(
                    resId = R.drawable.empty_orders,
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle)
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MealPlanPromoCard(onClick = null)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.meal_plans_how),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(list, key = { it.chef.id }) { offer ->
                    Surface(
                        onClick = { onKitchenClick(offer.chef.id) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = cardEdge(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Base64Thumbnail(
                                base64 = offer.chef.photoBase64,
                                emoji = offer.chef.emoji,
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                emojiFontSize = 26.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(offer.chef.kitchenName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (offer.chef.mealPlanDescription.isNotBlank()) {
                                    Text(
                                        offer.chef.mealPlanDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    stringResource(R.string.chef_distance_away, formatDistanceKm(offer.chef.distanceKm)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.price_tag, offer.weekPrice),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.meal_plans_per_week),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
