package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.MealUi

/**
 * Best-effort persistence for the chef-side Menu Manager, matches the
 * Meal model in README.md.
 */
object FirestoreMealRepository {

    fun addMeal(meal: MealUi) {
        try {
            val data = hashMapOf(
                "chefId" to meal.chefId,
                "name" to meal.name,
                "emoji" to meal.emoji,
                "description" to meal.description,
                "price" to meal.price,
                "category" to meal.category,
                "isVeg" to meal.isVeg,
                "allergens" to meal.allergens,
                "dietaryTags" to meal.dietaryTags,
                "availableQuantity" to meal.availableQuantity,
                "portionSize" to meal.portionSize,
                "photoBase64" to meal.photoBase64,
                "isAvailable" to true
            )
            FirebaseFirestore.getInstance().collection("meals").add(data)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    fun updateAvailability(mealId: String, isAvailable: Boolean) {
        try {
            FirebaseFirestore.getInstance().collection("meals")
                .document(mealId)
                .update("isAvailable", isAvailable)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
