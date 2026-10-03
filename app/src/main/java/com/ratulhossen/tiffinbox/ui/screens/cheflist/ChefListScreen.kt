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
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.domain.RecommendationEngine
import com.ratulhossen.tiffinbox.ui.components.ChefCard

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
    onChefClick: (String) -> Unit
) {
    var chefs by remember { mutableStateOf(SampleData.chefs) }
    var sort by remember {
        mutableStateOf(
            when (initialSort) {
                "nearby" -> ChefSort.NEARBY
                "rating" -> ChefSort.RATING
                else -> ChefSort.RECOMMENDED
            }
        )
    }

    LaunchedEffect(Unit) {
        val firestoreChefs = FirestoreCatalogRepository.getChefs()
        if (firestoreChefs.isNotEmpty()) chefs = firestoreChefs
    }

    val sortedChefs = remember(chefs, sort) {
        when (sort) {
            ChefSort.RECOMMENDED -> RecommendationEngine.recommend(chefs)
            ChefSort.NEARBY -> chefs.sortedBy { it.distanceKm }
            ChefSort.RATING -> chefs.sortedByDescending { it.overallRating }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chef_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
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
                            onClick = { onChefClick(chef.id) }
                        )
                    }
                }
            }
        }
    }
}
