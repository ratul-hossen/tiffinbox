package com.ratulhossen.tiffinbox.ui.screens.chefside

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreMealRepository
import com.ratulhossen.tiffinbox.data.model.MealUi

private const val DEMO_CHEF_ID = "chef1"

private data class ManagedMeal(val meal: MealUi, val isAvailable: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagerScreen(onBack: () -> Unit) {
    var meals by remember {
        mutableStateOf(SampleData.mealsForChef(DEMO_CHEF_ID).map { ManagedMeal(it, true) })
    }

    LaunchedEffect(Unit) {
        val firestoreMeals = FirestoreCatalogRepository.getMealsForChef(DEMO_CHEF_ID)
        if (firestoreMeals.isNotEmpty()) {
            meals = firestoreMeals.map { ManagedMeal(it, true) }
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.menu_manager_add_meal_content_description))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(meals, key = { it.meal.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.meal.emoji, fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.meal.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.menu_manager_item_price_quantity, item.meal.price, item.meal.availableQuantity),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = item.isAvailable,
                            onCheckedChange = { checked ->
                                meals = meals.map {
                                    if (it.meal.id == item.meal.id) it.copy(isAvailable = checked) else it
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMealDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, price ->
                val newMeal = MealUi(
                    id = "meal_${System.currentTimeMillis()}",
                    chefId = DEMO_CHEF_ID,
                    name = name,
                    emoji = "🍽️",
                    description = "নতুন যোগ করা মিল",
                    price = price,
                    category = "Lunch",
                    isVeg = true,
                    allergens = listOf("None"),
                    dietaryTags = emptyList(),
                    availableQuantity = 5,
                    portionSize = "1 plate"
                )
                meals = meals + ManagedMeal(newMeal, true)
                FirestoreMealRepository.addMeal(newMeal)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddMealDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, price: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_manager_add_meal_content_description)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.menu_manager_add_meal_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.menu_manager_add_meal_price_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val priceValue = price.toIntOrNull() ?: 0
                    if (name.isNotBlank() && priceValue > 0) {
                        onAdd(name, priceValue)
                    }
                }
            ) {
                Text(stringResource(R.string.menu_manager_add_button), fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_manager_cancel_button)) }
        }
    )
}
