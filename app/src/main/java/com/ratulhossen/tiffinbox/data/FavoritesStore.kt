package com.ratulhossen.tiffinbox.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * The customer's favourite kitchens and favourite foods.
 *
 * It used to hold kitchens only, in memory, so favourites vanished whenever
 * the app was closed. Now both lists are kept on the phone (instant on the
 * next launch) and on users/{uid} (favoriteChefIds / favoriteMealIds), so
 * they follow the account to another phone. Free: plain Firestore fields.
 */
object FavoritesStore {
    private const val PREFS = "favorites_store"

    val chefIds = mutableStateListOf<String>()
    val mealIds = mutableStateListOf<String>()

    private var appContext: Context? = null
    private var uid: String? = null

    fun isFavorite(chefId: String): Boolean = chefIds.contains(chefId)

    fun isMealFavorite(mealId: String): Boolean = mealIds.contains(mealId)

    fun toggle(chefId: String) {
        if (chefIds.contains(chefId)) chefIds.remove(chefId) else chefIds.add(chefId)
        persist()
    }

    fun toggleMeal(mealId: String) {
        if (mealIds.contains(mealId)) mealIds.remove(mealId) else mealIds.add(mealId)
        persist()
    }

    /** Loads [userId]'s favourites: the phone copy right away, then the account copy merged in. */
    fun load(context: Context, userId: String) {
        appContext = context.applicationContext
        if (uid == userId) return
        uid = userId
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        replace(chefIds, prefs.getStringSet("${userId}_chefs", emptySet()).orEmpty())
        replace(mealIds, prefs.getStringSet("${userId}_meals", emptySet()).orEmpty())
        try {
            FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (uid != userId) return@addOnSuccessListener
                    val remoteChefs = (doc.get("favoriteChefIds") as? List<*>).orEmpty().filterIsInstance<String>()
                    val remoteMeals = (doc.get("favoriteMealIds") as? List<*>).orEmpty().filterIsInstance<String>()
                    val newChefs = remoteChefs.filterNot { chefIds.contains(it) }
                    val newMeals = remoteMeals.filterNot { mealIds.contains(it) }
                    chefIds.addAll(newChefs)
                    mealIds.addAll(newMeals)
                    // Push local-only ones up too, so both copies end up the same.
                    if (chefIds.size != remoteChefs.size || mealIds.size != remoteMeals.size) persist()
                    else saveLocal()
                }
        } catch (e: Exception) {
            // Firebase not configured / offline - the phone copy is used.
        }
    }

    /** Signed out: forget the in-memory lists (each account keeps its own saved copy). */
    fun clear() {
        uid = null
        chefIds.clear()
        mealIds.clear()
    }

    private fun replace(target: MutableList<String>, values: Collection<String>) {
        target.clear()
        target.addAll(values)
    }

    private fun saveLocal() {
        val userId = uid ?: return
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putStringSet("${userId}_chefs", chefIds.toSet())
            ?.putStringSet("${userId}_meals", mealIds.toSet())
            ?.apply()
    }

    private fun persist() {
        saveLocal()
        val userId = uid ?: return
        try {
            FirebaseFirestore.getInstance().collection("users").document(userId).set(
                hashMapOf(
                    "favoriteChefIds" to chefIds.toList(),
                    "favoriteMealIds" to mealIds.toList()
                ),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Offline: Firestore queues the write; the phone copy is already saved.
        }
    }
}
