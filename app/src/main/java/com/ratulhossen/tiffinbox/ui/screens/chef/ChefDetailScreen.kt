package com.ratulhossen.tiffinbox.ui.screens.chef

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.ui.components.RatingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefDetailScreen(
    chefId: String,
    onBack: () -> Unit,
    onMealClick: (String) -> Unit
) {
    val chef = SampleData.chefById(chefId) ?: return
    val meals = SampleData.mealsForChef(chefId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chef.kitchenName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        "by ${chef.ownerName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${chef.overallRating}  (${chef.totalOrders} orders)  •  ${chef.distanceKm} km away")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(chef.bio, style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Rating Breakdown", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    RatingRow("🍽️ স্বাদ", chef.taste)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow("🧼 Hygiene", chef.hygiene)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow("📦 Packaging", chef.packaging)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow("⏱️ On-Time", chef.onTime)

                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Menu", style = MaterialTheme.typography.titleMedium)
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
