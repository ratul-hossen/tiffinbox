package com.ratulhossen.tiffinbox.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.ui.components.MealCard

@Composable
fun SearchScreen(onMealClick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var vegOnly by remember { mutableStateOf(false) }
    var allMeals by remember { mutableStateOf(SampleData.meals) }

    LaunchedEffect(Unit) {
        val firestoreMeals = FirestoreCatalogRepository.getAllMeals()
        if (firestoreMeals.isNotEmpty()) allMeals = firestoreMeals
    }

    val results = remember(query, selectedCategory, vegOnly, allMeals) {
        allMeals.filter { meal ->
            val matchesQuery = query.isBlank() ||
                meal.name.contains(query, ignoreCase = true) ||
                meal.description.contains(query, ignoreCase = true) ||
                meal.dietaryTags.any { it.contains(query, ignoreCase = true) }
            val matchesCategory = selectedCategory == null || meal.category == selectedCategory
            val matchesVeg = !vegOnly || meal.isVeg
            matchesQuery && matchesCategory && matchesVeg
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("কম তেলে ১২০ টাকার দুপুরের খাবার...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = vegOnly,
                    onClick = { vegOnly = !vegOnly },
                    label = { Text("শুধু Veg") }
                )
            }
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

        Spacer(modifier = Modifier.height(12.dp))

        if (results.isEmpty()) {
            EmptySearchState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.id }) { meal: MealUi ->
                    MealCard(meal = meal, onClick = { onMealClick(meal.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🍽️", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "কিছু পাওয়া যায়নি, অন্য কিছু খুঁজে দেখুন",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
