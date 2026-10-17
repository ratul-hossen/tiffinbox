package com.ratulhossen.tiffinbox.ui.screens.subscription

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreSubscriptionRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealType
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.data.model.SubscriptionPlan
import com.ratulhossen.tiffinbox.data.model.label
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribeScreen(
    chefId: String,
    onBack: () -> Unit,
    onSubscribed: () -> Unit,
    onOpenChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isSubmitting by remember { mutableStateOf(false) }
    var chef by remember(chefId) { mutableStateOf<ChefUi?>(SampleData.chefById(chefId)) }
    var meals by remember(chefId) { mutableStateOf<List<MealUi>>(SampleData.mealsForChef(chefId)) }
    var deliveryAddress by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash on Delivery") }
    var trxId by remember { mutableStateOf("") }
    var showPendingApprovalDialog by remember { mutableStateOf(false) }

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

    // Available package durations configured by chef
    val availablePlans = remember(chef) {
        val allowed = chef?.mealPlanPackages.orEmpty()
        if (allowed.isEmpty()) SubscriptionPlan.entries
        else SubscriptionPlan.entries.filter { allowed.contains(it.name) }.ifEmpty { SubscriptionPlan.entries }
    }
    var plan by remember { mutableStateOf(SubscriptionPlan.WEEKLY) }
    LaunchedEffect(availablePlans) {
        if (!availablePlans.contains(plan) && availablePlans.isNotEmpty()) {
            plan = availablePlans.first()
        }
    }

    // Available meal slots configured by chef
    val availableMealSlots = remember(chef) {
        val list = chef?.mealPlanMealTypes.orEmpty()
        if (list.isEmpty()) listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        else list
    }

    // Price per meal tier / range
    val minPrice = chef?.mealPlanPricePerMealMin ?: 0
    val maxPrice = chef?.mealPlanPricePerMealMax ?: 0
    val hasPriceRange = minPrice > 0 && maxPrice >= minPrice
    val defaultFallbackPrice = remember(meals) {
        if (meals.isEmpty()) 100 else meals.map { it.price }.average().toInt().coerceAtLeast(60)
    }

    var selectedPricePerMeal by remember(minPrice, maxPrice, defaultFallbackPrice) {
        mutableIntStateOf(
            if (hasPriceRange) minPrice else defaultFallbackPrice
        )
    }

    // Category price averages from menu dishes as smart fallbacks
    val categoryAverages = remember(meals) {
        val groups = meals.groupBy { it.category.lowercase() }
        val bAvg = groups.filterKeys { it.contains("breakfast") }.values.flatten().map { it.price }.average()
        val lAvg = groups.filterKeys { it.contains("lunch") || it.contains("rice") }.values.flatten().map { it.price }.average()
        val dAvg = groups.filterKeys { it.contains("dinner") || it.contains("curry") }.values.flatten().map { it.price }.average()
        val overallAvg = if (meals.isNotEmpty()) meals.map { it.price }.average() else 100.0
        mapOf(
            MealType.BREAKFAST to (if (!bAvg.isNaN() && bAvg > 0) bAvg.roundToInt() else (overallAvg * 0.7).roundToInt().coerceAtLeast(50)),
            MealType.LUNCH to (if (!lAvg.isNaN() && lAvg > 0) lAvg.roundToInt() else overallAvg.roundToInt().coerceAtLeast(80)),
            MealType.DINNER to (if (!dAvg.isNaN() && dAvg > 0) dAvg.roundToInt() else (overallAvg * 0.95).roundToInt().coerceAtLeast(80))
        )
    }

    // Resolves the exact price for a given day and meal slot
    fun getMealSlotPrice(day: String, slot: String): Int {
        // 1. Day-specific override price from chef
        val dayPrice = chef?.mealPlanDayPrices?.get(day)?.get(slot) ?: 0
        if (dayPrice > 0) return dayPrice

        // 2. Base slot price set by chef
        val baseSlotPrice = when (slot) {
            MealType.BREAKFAST -> chef?.mealPlanBaseBreakfastPrice ?: 0
            MealType.LUNCH -> chef?.mealPlanBaseLunchPrice ?: 0
            MealType.DINNER -> chef?.mealPlanBaseDinnerPrice ?: 0
            else -> 0
        }
        if (baseSlotPrice > 0) return baseSlotPrice

        // 3. Fallback: Mean/average price of menu dishes in that category
        val catAvg = categoryAverages[slot] ?: 0
        if (catAvg > 0) return catAvg

        // 4. Default fallback
        return when (slot) {
            MealType.BREAKFAST -> 60
            MealType.LUNCH -> 130
            MealType.DINNER -> 120
            else -> 100
        }
    }

    // Day-wise weekly custom schedule
    val daysOfWeek = remember {
        listOf("Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
    }

    // Default schedule: Sat-Thu Lunch + Dinner, Friday off
    var weeklySchedule by remember(availableMealSlots) {
        val defaultSlots = availableMealSlots.filter { it == MealType.LUNCH || it == MealType.DINNER }.toSet()
            .ifEmpty { availableMealSlots.toSet() }
        mutableStateOf(
            daysOfWeek.associateWith { day ->
                if (day == "Friday") emptySet<String>()
                else defaultSlots
            }
        )
    }

    // Total meals per week is the exact count from the custom schedule
    val mealsPerWeek = weeklySchedule.values.sumOf { it.size }

    // Summary of total meals across the package duration
    val totalMeals = remember(plan, mealsPerWeek) {
        if (plan == SubscriptionPlan.WEEKLY) mealsPerWeek
        else (mealsPerWeek * (plan.days / 7.0)).roundToInt()
    }

    // Exact weekly cost calculated by summing price of each chosen slot for each day
    val weeklyCost = remember(weeklySchedule, chef, categoryAverages) {
        weeklySchedule.entries.sumOf { (day, slots) ->
            slots.sumOf { slot -> getMealSlotPrice(day, slot) }
        }
    }

    // Total price across the package duration
    val totalPrice = remember(weeklyCost, plan, mealsPerWeek) {
        if (plan == SubscriptionPlan.WEEKLY) weeklyCost
        else ((weeklyCost / 7.0) * plan.days).roundToInt()
    }

    val effectivePricePerMeal = remember(totalPrice, totalMeals) {
        if (totalMeals > 0) (totalPrice.toDouble() / totalMeals).roundToInt() else 100
    }

    // Schedule summary string for database & display
    val scheduleSummary = remember(weeklySchedule) {
        weeklySchedule.entries.mapNotNull { (day, slots) ->
            if (slots.isEmpty()) null
            else {
                val shortDay = day.take(3)
                val codes = slots.map {
                    when (it) {
                        MealType.BREAKFAST -> "B"
                        MealType.LUNCH -> "L"
                        MealType.DINNER -> "D"
                        else -> it.take(1)
                    }
                }.joinToString(",")
                "$shortDay: $codes"
            }
        }.joinToString("; ")
    }

    // Resolved meal type summary (e.g. Lunch, Dinner, Both, Custom)
    val resolvedMealType = remember(weeklySchedule) {
        val allSlots = weeklySchedule.values.flatten().toSet()
        when {
            allSlots.isEmpty() -> "None"
            allSlots == setOf(MealType.LUNCH) -> MealType.LUNCH
            allSlots == setOf(MealType.DINNER) -> MealType.DINNER
            allSlots == setOf(MealType.BREAKFAST) -> MealType.BREAKFAST
            allSlots == setOf(MealType.LUNCH, MealType.DINNER) -> MealType.BOTH
            else -> allSlots.joinToString("+")
        }
    }

    Scaffold(
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
            // Header
            Text(
                stringResource(R.string.subscribe_header, chef?.kitchenName.orEmpty()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.subscribe_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Chef's Meal Plan Description Card (if configured by chef)
            if (chef?.mealPlanDescription?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.RestaurantMenu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.subscribe_chef_note),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                chef!!.mealPlanDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Package Duration (7 Days, 10 Days, 15 Days, 30 Days)
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.subscribe_choose_plan_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availablePlans.forEach { option ->
                    FilterChip(
                        selected = plan == option,
                        onClick = { plan = option },
                        label = { Text(option.label()) }
                    )
                }
            }

            // Price Per Meal (Chef Range or Default)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.subscribe_tier_price_label, selectedPricePerMeal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (hasPriceRange && minPrice < maxPrice) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Chef's price range: ৳$minPrice — ৳$maxPrice / meal. Move the slider to choose your portion tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Slider(
                    value = selectedPricePerMeal.toFloat(),
                    onValueChange = { selectedPricePerMeal = it.roundToInt() },
                    valueRange = minPrice.toFloat()..maxPrice.toFloat(),
                    steps = ((maxPrice - minPrice) / 10).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Standard meal rate from this kitchen: ৳$selectedPricePerMeal per meal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Day-wise Schedule Customization
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.subscribe_schedule_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.subscribe_schedule_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        stringResource(R.string.subscribe_schedule_summary, mealsPerWeek),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Quick preset chips
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val lunchDinnerSlots = availableMealSlots.filter { it == MealType.LUNCH || it == MealType.DINNER }.toSet()
                FilterChip(
                    selected = false,
                    onClick = {
                        weeklySchedule = daysOfWeek.associateWith { day ->
                            if (day == "Friday") emptySet() else lunchDinnerSlots
                        }
                    },
                    label = { Text("Sat–Thu (L+D)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        weeklySchedule = daysOfWeek.associateWith { lunchDinnerSlots }
                    },
                    label = { Text("All 7 Days (L+D)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        val allAvailable = availableMealSlots.toSet()
                        weeklySchedule = daysOfWeek.associateWith { day ->
                            if (day == "Friday") emptySet() else allAvailable
                        }
                    },
                    label = { Text("Sat–Thu All", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Day-by-Day schedule list
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    daysOfWeek.forEachIndexed { index, day ->
                        val daySlots = weeklySchedule[day] ?: emptySet()
                        val isDayOff = daySlots.isEmpty()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        day,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (daySlots.isNotEmpty()) {
                                        val dayTotal = daySlots.sumOf { getMealSlotPrice(day, it) }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                "৳$dayTotal",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (day == "Friday") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                "Weekend",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        weeklySchedule = if (isDayOff) {
                                            weeklySchedule + (day to availableMealSlots.toSet())
                                        } else {
                                            weeklySchedule + (day to emptySet())
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (isDayOff) "Take meals" else "Take off",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableMealSlots.forEach { slot ->
                                    val isSelected = daySlots.contains(slot)
                                    val slotPrice = getMealSlotPrice(day, slot)
                                    val label = when (slot) {
                                        MealType.BREAKFAST -> stringResource(R.string.subscribe_meal_type_breakfast)
                                        MealType.LUNCH -> stringResource(R.string.subscribe_meal_type_lunch)
                                        MealType.DINNER -> stringResource(R.string.subscribe_meal_type_dinner)
                                        else -> slot
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val newSlots = if (isSelected) daySlots - slot else daySlots + slot
                                            weeklySchedule = weeklySchedule + (day to newSlots)
                                        },
                                        label = { Text("$label · ৳$slotPrice", fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        if (index < daysOfWeek.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (mealsPerWeek == 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "⚠️ Please select at least one meal in your schedule.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Delivery Details
            Spacer(modifier = Modifier.height(24.dp))
            Text("Delivery Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

            // Payment Method
            Spacer(modifier = Modifier.height(24.dp))
            Text("Payment Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

            // bKash details card with Chef's bKash number & copy action
            if (paymentMethod == "bKash") {
                val chefBkash = chef?.bkashNumber.orEmpty().ifBlank {
                    chef?.nagadNumber.orEmpty()
                }

                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Payment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.subscribe_bkash_number_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (chefBkash.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    chefBkash,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(chefBkash))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.subscribe_bkash_number_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.subscribe_bkash_copy_number), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Text(
                                "Chef has not set a bKash number. Please contact the chef or use Cash on Delivery.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Send ৳$totalPrice to the bKash number above via Send Money, then enter your Transaction ID below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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

            // Notice for custom meal plan
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.subscribe_custom_pending_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Summary Breakdown Card
            Spacer(modifier = Modifier.height(20.dp))
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SummaryRow(stringResource(R.string.subscribe_summary_price_per_meal), "Avg ৳$effectivePricePerMeal/meal")
                    SummaryRow(stringResource(R.string.subscribe_summary_meals_per_week), "$mealsPerWeek meals/wk")
                    SummaryRow("Weekly plan cost", "৳$weeklyCost/week")
                    SummaryRow(stringResource(R.string.subscribe_summary_duration), stringResource(R.string.subscribe_summary_duration_value, plan.days))
                    SummaryRow("Total meals in package", "$totalMeals meals")
                    if (scheduleSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "🗓️ $scheduleSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        stringResource(R.string.subscribe_summary_total, plan.label()),
                        stringResource(R.string.price_tag, totalPrice),
                        bold = true
                    )
                }
            }

            // Subscribe Button
            Spacer(modifier = Modifier.height(24.dp))
            TiffinButton(
                enabled = !isSubmitting &&
                    deliveryAddress.isNotBlank() &&
                    mealsPerWeek > 0 &&
                    (paymentMethod != "bKash" || trxId.isNotBlank()),
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        val subId = FirestoreSubscriptionRepository.subscribe(
                            chefId = chefId,
                            chefName = chef?.kitchenName.orEmpty(),
                            plan = plan,
                            mealsPerWeek = mealsPerWeek,
                            mealType = resolvedMealType,
                            pricePerMeal = effectivePricePerMeal,
                            deliveryAddress = deliveryAddress,
                            customerPhone = customerPhone,
                            paymentMethod = paymentMethod,
                            paymentTransactionId = trxId,
                            scheduleSummary = scheduleSummary,
                            isCustomSchedule = true,
                            customTotalPrice = totalPrice
                        )
                        isSubmitting = false
                        if (subId != null) {
                            showPendingApprovalDialog = true
                        } else {
                            Toast.makeText(context, "Failed to subscribe. Please try again.", Toast.LENGTH_SHORT).show()
                        }
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

    if (showPendingApprovalDialog) {
        AlertDialog(
            onDismissRequest = {
                showPendingApprovalDialog = false
                onSubscribed()
            },
            title = { Text("মিল প্ল্যান রিকোয়েস্ট সফল!") },
            text = {
                Text("আপনার কাস্টম মিল প্ল্যানটি শেফের কাছে পৌঁছেছে এবং শেফের অনুমোদনের অপেক্ষায় আছে। আপনি চ্যাটে শেফের সাথে কথা বলে পেমেন্ট এবং খাবারের বিবরণ নিশ্চিত করতে পারেন।")
            },
            confirmButton = {
                if (onOpenChat != null) {
                    Button(onClick = {
                        showPendingApprovalDialog = false
                        onOpenChat(chefId)
                    }) {
                        Text("শেফের সাথে চ্যাট করুন")
                    }
                } else {
                    Button(onClick = {
                        showPendingApprovalDialog = false
                        onSubscribed()
                    }) {
                        Text("ঠিক আছে")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPendingApprovalDialog = false
                    onSubscribed()
                }) {
                    Text("হোমে যান")
                }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
