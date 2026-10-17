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
import com.ratulhossen.tiffinbox.data.model.MealType
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.data.model.SubscriptionPlan
import com.ratulhossen.tiffinbox.data.model.label
import kotlin.math.roundToInt
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribeScreen(
    chefId: String,
    onBack: () -> Unit,
    onSubscribed: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }
    var chef by remember(chefId) { mutableStateOf<ChefUi?>(SampleData.chefById(chefId)) }
    var meals by remember(chefId) { mutableStateOf<List<MealUi>>(SampleData.mealsForChef(chefId)) }
    var deliveryAddress by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash on Delivery") }
    var trxId by remember { mutableStateOf("") }

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let { chef = it }
        val firestoreMeals = FirestoreCatalogRepository.getMealsForChef(chefId)
        if (firestoreMeals.isNotEmpty()) meals = firestoreMeals
        val uid = FirebaseAuthRepository.currentUser?.uid
        if (uid != null) {
            FirestoreUserRepository.getProfile(uid)?.let { profile ->
                if (deliveryAddress.isBlank()) deliveryAddress = profile.address
                if (customerPhone.isBlank()) customerPhone = profile.phoneNumber
            }
        }
    }

    val pricePerMeal = remember(meals) {
        if (meals.isEmpty()) 100 else meals.map { it.price }.average().toInt()
    }

    var plan by remember { mutableStateOf(SubscriptionPlan.WEEKLY) }
    var mealsPerWeek by remember { mutableIntStateOf(5) }
    var mealType by remember { mutableStateOf(MealType.BOTH) }
    val totalMeals = remember(plan, mealsPerWeek) {
        (mealsPerWeek * plan.days / 7.0).roundToInt()
    }
    val totalPrice = remember(totalMeals, pricePerMeal) {
        pricePerMeal * totalMeals
    }

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
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

            Spacer(modifier = Modifier.height(24.dp))
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

            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.subscribe_meal_type_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    MealType.LUNCH to stringResource(R.string.subscribe_meal_type_lunch),
                    MealType.DINNER to stringResource(R.string.subscribe_meal_type_dinner),
                    MealType.BOTH to stringResource(R.string.subscribe_meal_type_both)
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = mealType == value,
                        onClick = { mealType = value },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
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

            Spacer(modifier = Modifier.height(20.dp))
            Text("Delivery Details", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = deliveryAddress,
                onValueChange = { deliveryAddress = it },
                label = { Text("Delivery Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("Contact Phone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Payment Method", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Cash on Delivery", "bKash").forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method) }
                    )
                }
            }
            if (paymentMethod == "bKash") {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = trxId,
                    onValueChange = { trxId = it },
                    label = { Text("bKash Transaction ID") },
                    placeholder = { Text("e.g. 9K20ABX8") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SummaryRow(stringResource(R.string.subscribe_summary_price_per_meal), stringResource(R.string.price_tag, pricePerMeal))
                    SummaryRow(stringResource(R.string.subscribe_summary_meals_per_week), "$mealsPerWeek")
                    SummaryRow(stringResource(R.string.subscribe_summary_duration), stringResource(R.string.subscribe_summary_duration_value, plan.days))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        stringResource(R.string.subscribe_summary_total, plan.label()),
                        stringResource(R.string.price_tag, totalPrice),
                        bold = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TiffinButton(
                enabled = !isSubmitting && deliveryAddress.isNotBlank() && (paymentMethod != "bKash" || trxId.isNotBlank()),
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        FirestoreSubscriptionRepository.subscribe(
                            chefId = chefId,
                            chefName = chef?.kitchenName.orEmpty(),
                            plan = plan,
                            mealsPerWeek = mealsPerWeek,
                            mealType = mealType,
                            pricePerMeal = pricePerMeal,
                            deliveryAddress = deliveryAddress,
                            customerPhone = customerPhone,
                            paymentMethod = paymentMethod,
                            paymentTransactionId = trxId
                        )
                        isSubmitting = false
                        onSubscribed()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (isSubmitting) "Subscribing…" else stringResource(R.string.subscribe_button))
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
