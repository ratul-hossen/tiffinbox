package com.ratulhossen.tiffinbox.ui.screens.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreSubscriptionRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.data.model.SubscriptionPlan
import com.ratulhossen.tiffinbox.data.model.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribeScreen(
    chefId: String,
    onBack: () -> Unit,
    onSubscribed: () -> Unit
) {
    var chef by remember(chefId) { mutableStateOf<ChefUi?>(SampleData.chefById(chefId)) }
    var meals by remember(chefId) { mutableStateOf<List<MealUi>>(SampleData.mealsForChef(chefId)) }

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let { chef = it }
        val firestoreMeals = FirestoreCatalogRepository.getMealsForChef(chefId)
        if (firestoreMeals.isNotEmpty()) meals = firestoreMeals
    }

    val pricePerMeal = remember(meals) {
        if (meals.isEmpty()) 100 else meals.map { it.price }.average().toInt()
    }

    var plan by remember { mutableStateOf(SubscriptionPlan.WEEKLY) }
    var mealsPerWeek by remember { mutableIntStateOf(5) }
    val totalPrice = remember(plan, mealsPerWeek, pricePerMeal) {
        pricePerMeal * mealsPerWeek * plan.weeks
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscribe_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
        ) {
            Text(
                stringResource(R.string.subscribe_header, chef?.kitchenName.orEmpty()),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.subscribe_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))
            Text(stringResource(R.string.subscribe_choose_plan_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubscriptionPlan.entries.forEach { option ->
                    FilterChip(
                        selected = plan == option,
                        onClick = { plan = option },
                        label = { Text(option.label()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.subscribe_meals_per_week_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (mealsPerWeek > 1) mealsPerWeek-- },
                    enabled = mealsPerWeek > 1
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.content_description_decrease))
                }
                Text(
                    "$mealsPerWeek",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                IconButton(
                    onClick = { if (mealsPerWeek < 14) mealsPerWeek++ },
                    enabled = mealsPerWeek < 14
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.content_description_increase))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SummaryRow(stringResource(R.string.subscribe_summary_price_per_meal), stringResource(R.string.price_tag, pricePerMeal))
                    SummaryRow(stringResource(R.string.subscribe_summary_meals_per_week), "$mealsPerWeek")
                    SummaryRow(stringResource(R.string.subscribe_summary_duration), stringResource(R.string.subscribe_summary_duration_value, plan.weeks))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        stringResource(R.string.subscribe_summary_total, plan.label()),
                        stringResource(R.string.price_tag, totalPrice),
                        bold = true
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    FirestoreSubscriptionRepository.subscribe(
                        chefId = chefId,
                        chefName = chef?.kitchenName.orEmpty(),
                        plan = plan,
                        mealsPerWeek = mealsPerWeek,
                        pricePerMeal = pricePerMeal
                    )
                    onSubscribed()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.subscribe_button))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            value,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
