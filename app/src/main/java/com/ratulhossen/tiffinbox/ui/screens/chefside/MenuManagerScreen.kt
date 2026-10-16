package com.ratulhossen.tiffinbox.ui.screens.chefside

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.ratulhossen.tiffinbox.data.SampleData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreMealRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.util.ImageCompressor
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults

private data class ManagedMeal(val meal: MealUi, val isAvailable: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagerScreen(chefId: String, onBack: () -> Unit) {
    var meals by remember(chefId) {
        mutableStateOf<List<ManagedMeal>>(emptyList())
    }

    LaunchedEffect(chefId) {
        val firestoreMeals = FirestoreCatalogRepository.getMealsForChef(chefId)
        meals = firestoreMeals.map { ManagedMeal(it, true) }
    }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                        Base64Thumbnail(
                            base64 = item.meal.photoBase64,
                            emoji = item.meal.emoji,
                            modifier = Modifier.size(48.dp)
                        )
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
            onAdd = { name, price, photoBase64, category ->
                val newMeal = MealUi(
                    id = "meal_${System.currentTimeMillis()}",
                    chefId = chefId,
                    name = name,
                    emoji = "🍽️",
                    description = "নতুন যোগ করা মিল",
                    price = price,
                    category = category,
                    isVeg = true,
                    allergens = listOf("None"),
                    dietaryTags = emptyList(),
                    availableQuantity = 5,
                    portionSize = "1 plate",
                    photoBase64 = photoBase64
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
    onAdd: (name: String, price: Int, photoBase64: String, category: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    // Same categories as the Home category filter (SampleData.categories).
    var category by remember { mutableStateOf("Lunch") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isCompressing by remember { mutableStateOf(false) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }

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
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.menu_manager_category_label), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SampleData.categories.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        pickPhotoLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (photoUri != null) Icons.Filled.CheckCircle else Icons.Filled.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (photoUri != null) {
                            stringResource(R.string.menu_manager_photo_selected)
                        } else {
                            stringResource(R.string.menu_manager_select_photo_button)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isCompressing,
                onClick = {
                    val priceValue = price.toIntOrNull() ?: 0
                    if (name.isNotBlank() && priceValue > 0) {
                        val uri = photoUri
                        if (uri == null) {
                            onAdd(name, priceValue, "", category)
                        } else {
                            isCompressing = true
                            scope.launch {
                                val base64 = ImageCompressor.compressToBase64(context, uri)
                                isCompressing = false
                                onAdd(name, priceValue, base64.orEmpty(), category)
                            }
                        }
                    }
                }
            ) {
                if (isCompressing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.menu_manager_add_button), fontWeight = FontWeight.Medium)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_manager_cancel_button)) }
        }
    )
}
