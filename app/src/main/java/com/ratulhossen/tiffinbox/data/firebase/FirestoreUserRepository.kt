package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.ratulhossen.tiffinbox.data.location.GeoPoint
import kotlinx.coroutines.tasks.await

object FirestoreUserRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun upsertProfile(uid: String, name: String, email: String, role: String, phoneNumber: String = "", whatsappNumber: String = "") {
        try {
            db.collection("users").document(uid).set(
                hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to role,
                    "phoneNumber" to phoneNumber,
                    "whatsappNumber" to whatsappNumber,
                    "createdAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Fetches the current FCM token and saves it, so a push backend (once one exists) can target this device. */
    suspend fun registerFcmToken(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            saveFcmToken(uid, token)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    fun saveFcmToken(uid: String, token: String) {
        try {
            db.collection("users").document(uid).set(
                hashMapOf("fcmToken" to token),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    suspend fun getRole(uid: String): String? {
        return try {
            db.collection("users").document(uid).get().await().getString("role")
        } catch (e: Exception) {
            null
        }
    }

    fun saveLocation(uid: String, latitude: Double, longitude: Double) {
        try {
            db.collection("users").document(uid).set(
                hashMapOf("latitude" to latitude, "longitude" to longitude),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Null if the user hasn't set a location yet (e.g. an account from before this feature). */
    suspend fun getLocation(uid: String): GeoPoint? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            val lat = doc.getDouble("latitude")
            val lng = doc.getDouble("longitude")
            if (lat != null && lng != null) GeoPoint(lat, lng) else null
        } catch (e: Exception) {
            null
        }
    }
}
