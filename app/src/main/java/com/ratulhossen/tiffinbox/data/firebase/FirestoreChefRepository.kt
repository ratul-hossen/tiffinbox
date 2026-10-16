package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FieldValue
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
        phoneNumber: String = "",
        whatsappNumber: String = "",
        bkashNumber: String = "",
        nagadNumber: String = "",
        kitchenType: String = "",
        yearsOfExperience: Int = 0
    ) {
        try {
            db.collection("chefs").document(uid).set(
                hashMapOf(
                    "userId" to uid,
                    "kitchenName" to kitchenName,
                    "ownerName" to ownerName,
                    "phoneNumber" to phoneNumber,
                    "whatsappNumber" to whatsappNumber,
                    "bkashNumber" to bkashNumber,
                    "nagadNumber" to nagadNumber,
                    "kitchenType" to kitchenType,
                    "yearsOfExperience" to yearsOfExperience,
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
                    "isAvailableToday" to true,
                    "deliveryMode" to "self"
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

    /** A verified location is mandatory for every chef (required right after signup). */
    fun updateLocation(uid: String, latitude: Double, longitude: Double, address: String = "") {
        try {
            db.collection("chefs").document(uid).set(
                hashMapOf("latitude" to latitude, "longitude" to longitude, "address" to address),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Edit Profile save for chefs - contact/payment numbers and kitchen name on chefs/{uid}. */
    suspend fun updateContactInfo(
        uid: String,
        ownerName: String,
        kitchenName: String,
        phoneNumber: String,
        whatsappNumber: String,
        bkashNumber: String,
        nagadNumber: String
    ): Boolean {
        return try {
            db.collection("chefs").document(uid).set(
                hashMapOf(
                    "ownerName" to ownerName,
                    "kitchenName" to kitchenName,
                    "phoneNumber" to phoneNumber,
                    "whatsappNumber" to whatsappNumber,
                    "bkashNumber" to bkashNumber,
                    "nagadNumber" to nagadNumber
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** mode is "self" (chef delivers their own orders) or "tiffinbox" (TiffinBox's own delivery system). */
    fun updateDeliveryMode(uid: String, mode: String) {
        try {
            db.collection("chefs").document(uid).set(
                hashMapOf("deliveryMode" to mode),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** [base64] is a pre-compressed photo (see ImageCompressor.kt) - no Firebase Storage involved. */
    fun updatePhoto(uid: String, base64: String) {
        try {
            db.collection("chefs").document(uid).set(
                hashMapOf("photoBase64" to base64),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /**
     * Chef video "posts" are external links (YouTube/Facebook/Drive share
     * URLs), not uploaded files - true video upload needs Firebase Storage
     * (Blaze plan), which this project deliberately avoids. See CLAUDE.md.
     */
    fun addVideo(uid: String, url: String, caption: String) {
        try {
            db.collection("chefs").document(uid).update(
                "videos",
                FieldValue.arrayUnion(
                    hashMapOf(
                        "url" to url,
                        "caption" to caption,
                        "postedAt" to System.currentTimeMillis()
                    )
                )
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
