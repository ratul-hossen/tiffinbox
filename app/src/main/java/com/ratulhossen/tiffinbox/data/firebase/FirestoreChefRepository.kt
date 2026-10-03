package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Owns a chef's own kitchen profile at chefs/{uid}. The document id is
 * always the chef's Firebase Auth uid (not an auto-generated id), so every
 * other chef-side screen (dashboard, menu manager, chat) can use the
 * signed-in uid directly as the chefId with no separate lookup, and
 * firestore.rules can check ownership with a plain uid match.
 */
object FirestoreChefRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun createChefProfile(
        uid: String,
        kitchenName: String,
        ownerName: String,
        whatsappNumber: String = "",
        bkashNumber: String = ""
    ) {
        try {
            db.collection("chefs").document(uid).set(
                hashMapOf(
                    "userId" to uid,
                    "kitchenName" to kitchenName,
                    "ownerName" to ownerName,
                    "whatsappNumber" to whatsappNumber,
                    "bkashNumber" to bkashNumber,
                    "emoji" to "🍱",
                    "bio" to "",
                    "specialty" to emptyList<String>(),
                    "distanceKm" to 1.0,
                    "overallRating" to 0.0,
                    "taste" to 0.0,
                    "hygiene" to 0.0,
                    "packaging" to 0.0,
                    "onTime" to 0.0,
                    "totalOrders" to 0,
                    "isVerified" to false,
                    "isAvailableToday" to true
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore; the
            // dashboard will simply show empty state until this succeeds.
        }
    }

    suspend fun chefProfileExists(uid: String): Boolean {
        return try {
            db.collection("chefs").document(uid).get().await().exists()
        } catch (e: Exception) {
            false
        }
    }
}
