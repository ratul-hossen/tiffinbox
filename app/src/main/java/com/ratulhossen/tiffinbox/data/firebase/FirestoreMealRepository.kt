package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.MealUi
import kotlinx.coroutines.tasks.await

/**
 * Chef-side Menu Manager persistence (meals/{mealId}). firestore.rules
 * lets only the owning chef (or an admin) update / delete a meal.
 */
object FirestoreMealRepository {

    /** How many meals a chef may mark as Today's Special at once. */
    const val MAX_TODAY_SPECIALS = 2

    private val meals get() = FirebaseFirestore.getInstance().collection("meals")

    private fun MealUi.toData(): HashMap<String, Any> = hashMapOf(
        "chefId" to chefId,
        "name" to name,
        "emoji" to emoji,
        "description" to description,
        "price" to price,
        "category" to category,
        "isVeg" to isVeg,
        "allergens" to allergens,
        "dietaryTags" to dietaryTags,
        "availableQuantity" to availableQuantity,
        "portionSize" to portionSize,
        "photoBase64" to photoBase64,
        "isAvailable" to isAvailable,
        "specialDate" to specialDate
    )

    /**
     * Creates the meal and returns its real Firestore id (null on failure).
     * The id is generated up front so the Menu Manager list uses the same id
     * later edits / deletes are sent to.
     */
    suspend fun addMeal(meal: MealUi): String? {
        return try {
            val ref = meals.document()
            ref.set(meal.copy(id = ref.id).toData()).await()
            ref.id
        } catch (e: Exception) {
            null
        }
    }

    /** Saves every editable field of an existing meal (photo included). */
    suspend fun updateMeal(meal: MealUi): Boolean {
        return try {
            val data = meal.toData()
            data.remove("chefId") // never moves to another chef
            meals.document(meal.id).update(data).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateAvailability(mealId: String, isAvailable: Boolean): Boolean {
        return try {
            meals.document(mealId).update("isAvailable", isAvailable).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Marks / unmarks the meal as today's special (stored as today's date, so it lapses tomorrow). */
    suspend fun setTodaySpecial(mealId: String, special: Boolean): Boolean {
        return try {
            meals.document(mealId).update("specialDate", if (special) MealUi.todayKey() else "").await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteMeal(mealId: String): Boolean {
        return try {
            meals.document(mealId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
