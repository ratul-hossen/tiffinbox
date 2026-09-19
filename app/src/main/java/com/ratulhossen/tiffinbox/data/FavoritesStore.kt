package com.ratulhossen.tiffinbox.data

import androidx.compose.runtime.mutableStateListOf

object FavoritesStore {
    val chefIds = mutableStateListOf<String>()

    fun isFavorite(chefId: String): Boolean = chefIds.contains(chefId)

    fun toggle(chefId: String) {
        if (chefIds.contains(chefId)) {
            chefIds.remove(chefId)
        } else {
            chefIds.add(chefId)
        }
    }
}
