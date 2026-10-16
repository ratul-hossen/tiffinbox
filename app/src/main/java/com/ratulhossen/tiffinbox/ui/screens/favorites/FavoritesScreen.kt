package com.ratulhossen.tiffinbox.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ratulhossen.tiffinbox.data.FavoritesStore
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.ui.components.ChefCard
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState

@Composable
fun FavoritesScreen(onChefClick: (String) -> Unit) {
    var allChefs by remember { mutableStateOf<List<ChefUi>>(emptyList()) }

    LaunchedEffect(Unit) {
        val firestoreChefs = FirestoreCatalogRepository.getChefs()
        allChefs = firestoreChefs
    }

    val favoriteChefs = allChefs.filter { FavoritesStore.isFavorite(it.id) }

    if (favoriteChefs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            PhotoEmptyState(
                resId = R.drawable.empty_favorites,
                title = stringResource(R.string.favorites_empty_title),
                subtitle = stringResource(R.string.favorites_empty_subtitle)
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(favoriteChefs, key = { it.id }) { chef ->
                ChefCard(chef = chef, onClick = { onChefClick(chef.id) })
            }
        }
    }
}
