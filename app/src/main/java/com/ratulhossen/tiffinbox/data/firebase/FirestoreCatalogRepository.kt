package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import kotlinx.coroutines.tasks.await

/**
 * Reads the chefs/meals collections written by FirestoreSeedRepository /
 * MenuManagerScreen. Every function returns an empty list instead of
 * throwing on any failure (Firebase not configured, offline, empty
 * collection) so callers can simply fall back to SampleData.
 */
object FirestoreCatalogRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Only approved (isVerified) chefs are customer-visible - an admin must
     * explicitly verify a chef (admin-web's Chefs page) before their kitchen
     * shows up anywhere a customer browses, even though the chef's own
     * dashboard and profile already exist right after signup.
     */
    suspend fun getChefs(): List<ChefUi> {
        return try {
            db.collection("chefs").get().await().documents
                .mapNotNull { it.toChefUi() }
                .filter { it.isVerified }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChef(chefId: String): ChefUi? {
        return try {
            db.collection("chefs").document(chefId).get().await().toChefUi()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllMeals(): List<MealUi> {
        return try {
            db.collection("meals").get().await().documents.mapNotNull { it.toMealUi() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMeal(mealId: String): MealUi? {
        return try {
            db.collection("meals").document(mealId).get().await().toMealUi()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMealsForChef(chefId: String): List<MealUi> {
        return try {
            db.collection("meals")
                .whereEqualTo("chefId", chefId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toMealUi() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun DocumentSnapshot.toChefUi(): ChefUi? {
        val kitchenName = getString("kitchenName") ?: return null
        @Suppress("UNCHECKED_CAST")
        val specialty = (get("specialty") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return ChefUi(
            id = id,
            kitchenName = kitchenName,
            ownerName = getString("ownerName") ?: "",
            emoji = getString("emoji") ?: "🍱",
            specialty = specialty,
            bio = getString("bio") ?: "",
            distanceKm = getDouble("distanceKm") ?: 1.0,
            overallRating = getDouble("overallRating") ?: 0.0,
            taste = getDouble("taste") ?: 0.0,
            hygiene = getDouble("hygiene") ?: 0.0,
            packaging = getDouble("packaging") ?: 0.0,
            onTime = getDouble("onTime") ?: 0.0,
            totalOrders = (getLong("totalOrders") ?: 0L).toInt(),
            isVerified = getBoolean("isVerified") ?: false,
            isAvailableToday = getBoolean("isAvailableToday") ?: true,
            bkashNumber = getString("bkashNumber") ?: "",
            latitude = getDouble("latitude") ?: 0.0,
            longitude = getDouble("longitude") ?: 0.0,
            deliveryMode = getString("deliveryMode") ?: "self"
        )
    }

    private fun DocumentSnapshot.toMealUi(): MealUi? {
        val name = getString("name") ?: return null
        val chefId = getString("chefId") ?: return null
        val allergens = (get("allergens") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val dietaryTags = (get("dietaryTags") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return MealUi(
            id = id,
            chefId = chefId,
            name = name,
            emoji = getString("emoji") ?: "🍽️",
            description = getString("description") ?: "",
            price = (getLong("price") ?: 0L).toInt(),
            category = getString("category") ?: "Lunch",
            isVeg = getBoolean("isVeg") ?: true,
            allergens = allergens,
            dietaryTags = dietaryTags,
            availableQuantity = (getLong("availableQuantity") ?: 0L).toInt(),
            portionSize = getString("portionSize") ?: "1 plate"
        )
    }
}
