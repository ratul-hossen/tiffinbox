package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object FirestoreUserRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun upsertProfile(uid: String, name: String, email: String, role: String) {
        try {
            db.collection("users").document(uid).set(
                hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to role,
                    "createdAt" to System.currentTimeMillis()
                ),
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
