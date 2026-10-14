package com.ratulhossen.tiffinbox.ui.screens.chef

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.ui.components.RatingRow
import com.ratulhossen.tiffinbox.ui.components.ShimmerBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefDetailScreen(
    chefId: String,
    onBack: () -> Unit,
    onMealClick: (String) -> Unit,
    onChatClick: () -> Unit,
    onSubscribeClick: () -> Unit
) {
    var chefState by remember(chefId) { mutableStateOf<ChefUi?>(SampleData.chefById(chefId)) }
    var meals by remember(chefId) { mutableStateOf(SampleData.mealsForChef(chefId)) }
    var isLoading by remember(chefId) { mutableStateOf(chefState == null) }

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let { chefState = it }
        val firestoreMeals = FirestoreCatalogRepository.getMealsForChef(chefId)
        if (firestoreMeals.isNotEmpty()) meals = firestoreMeals
        isLoading = false
    }

    if (chefState == null) {
        if (isLoading) {
            ChefDetailShimmer()
        }
        return
    }
    val chef = chefState!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chef.kitchenName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    IconButton(onClick = onChatClick) {
                        Icon(Icons.Filled.Chat, contentDescription = stringResource(R.string.content_description_chat))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(chef.emoji, fontSize = 56.sp)
                }
            }
            item {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chef.kitchenName, style = MaterialTheme.typography.headlineSmall)
                        if (chef.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.content_description_verified),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.chef_detail_owner_by, chef.ownerName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (chef.deliveryMode == "tiffinbox") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                stringResource(R.string.chef_card_tiffinbox_delivery_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(
                                R.string.chef_rating_summary,
                                chef.overallRating.toString(),
                                chef.totalOrders.toString(),
                                chef.distanceKm.toString()
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(chef.bio, style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onSubscribeClick, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.chef_detail_view_subscription_button))
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(stringResource(R.string.chef_detail_rating_breakdown_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    RatingRow(stringResource(R.string.rating_taste_label), chef.taste)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow(stringResource(R.string.rating_hygiene_label), chef.hygiene)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow(stringResource(R.string.rating_packaging_label), chef.packaging)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow(stringResource(R.string.rating_on_time_label), chef.onTime)

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(stringResource(R.string.chef_detail_menu_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            items(meals) { meal ->
                MealCard(
                    meal = meal,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    onClick = { onMealClick(meal.id) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ChefDetailShimmer() {
    Column(modifier = Modifier.fillMaxSize()) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp), cornerRadius = 0.dp)
        Column(modifier = Modifier.padding(20.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(24.dp))
            Spacer(modifier = Modifier.height(10.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
            Spacer(modifier = Modifier.height(20.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(24.dp))
            repeat(3) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(64.dp))
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}
