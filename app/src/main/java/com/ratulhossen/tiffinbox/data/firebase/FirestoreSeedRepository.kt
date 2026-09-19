package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.SampleData

/**
 * Dev-only helper: writes the same dummy chefs/meals the UI already shows
 * (SampleData) into Firestore, using their existing ids as document ids so
 * running it again just overwrites the same documents instead of
 * duplicating them. Useful for trying out the Firestore repositories
 * against real data before building real onboarding/menu-entry flows.
 */
object FirestoreSeedRepository {

    sealed class SeedResult {
        object Success : SeedResult()
        data class Failure(val message: String) : SeedResult()
    }

    fun seedSampleData(onResult: (SeedResult) -> Unit) {
        try {
            val db = FirebaseFirestore.getInstance()
            val batch = db.batch()

            SampleData.chefs.forEach { chef ->
                val ref = db.collection("chefs").document(chef.id)
                batch.set(
                    ref,
                    hashMapOf(
                        "kitchenName" to chef.kitchenName,
                        "ownerName" to chef.ownerName,
                        "bio" to chef.bio,
                        "specialty" to chef.specialty,
                        "overallRating" to chef.overallRating,
                        "taste" to chef.taste,
                        "hygiene" to chef.hygiene,
                        "packaging" to chef.packaging,
                        "onTime" to chef.onTime,
                        "totalOrders" to chef.totalOrders,
                        "isVerified" to chef.isVerified,
                        "isAvailableToday" to chef.isAvailableToday
                    )
                )
            }

            SampleData.meals.forEach { meal ->
                val ref = db.collection("meals").document(meal.id)
                batch.set(
                    ref,
                    hashMapOf(
                        "chefId" to meal.chefId,
                        "name" to meal.name,
                        "description" to meal.description,
                        "price" to meal.price,
                        "category" to meal.category,
                        "isVeg" to meal.isVeg,
                        "allergens" to meal.allergens,
                        "dietaryTags" to meal.dietaryTags,
                        "availableQuantity" to meal.availableQuantity,
                        "portionSize" to meal.portionSize,
                        "isAvailable" to true
                    )
                )
            }

            batch.commit()
                .addOnSuccessListener { onResult(SeedResult.Success) }
                .addOnFailureListener { e -> onResult(SeedResult.Failure(e.message ?: "Seed ব্যর্থ হয়েছে")) }
        } catch (e: Exception) {
            onResult(SeedResult.Failure(e.message ?: "Firebase এখনো সেটআপ করা হয়নি"))
        }
    }
}
