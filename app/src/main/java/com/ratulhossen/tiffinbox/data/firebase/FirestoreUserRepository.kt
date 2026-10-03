package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FirestoreUserRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun upsertProfile(uid: String, name: String, email: String, role: String, whatsappNumber: String = "") {
        try {
            db.collection("users").document(uid).set(
                hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to role,
                    "whatsappNumber" to whatsappNumber,
                    "phoneVerified" to false,
                    "createdAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Flips phoneVerified to true once PhoneVerificationRepository confirms the OTP. */
    fun markPhoneVerified(uid: String) {
        try {
            db.collection("users").document(uid).set(
                hashMapOf("phoneVerified" to true),
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
}
