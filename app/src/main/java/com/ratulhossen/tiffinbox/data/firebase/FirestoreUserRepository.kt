package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.ratulhossen.tiffinbox.data.location.GeoPoint
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val name: String,
    val email: String,
    val role: String,
    val phoneNumber: String,
    val whatsappNumber: String,
    val address: String,
    val hasLocation: Boolean
)

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

    fun saveLocation(uid: String, latitude: Double, longitude: Double, address: String = "") {
        try {
            db.collection("users").document(uid).set(
                hashMapOf("latitude" to latitude, "longitude" to longitude, "address" to address),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Fields shown on the Edit Profile screen; null if the doc can't be read. */
    suspend fun getProfile(uid: String): UserProfile? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            UserProfile(
                name = doc.getString("name").orEmpty(),
                email = doc.getString("email").orEmpty(),
                role = doc.getString("role").orEmpty(),
                phoneNumber = doc.getString("phoneNumber").orEmpty(),
                whatsappNumber = doc.getString("whatsappNumber").orEmpty(),
                address = doc.getString("address").orEmpty(),
                hasLocation = doc.getDouble("latitude") != null && doc.getDouble("longitude") != null
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Edit Profile save - merges only these fields, never touches role/location. */
    suspend fun updateProfile(uid: String, name: String, phoneNumber: String, whatsappNumber: String): Boolean {
        return try {
            db.collection("users").document(uid).set(
                hashMapOf(
                    "name" to name,
                    "phoneNumber" to phoneNumber,
                    "whatsappNumber" to whatsappNumber
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
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
