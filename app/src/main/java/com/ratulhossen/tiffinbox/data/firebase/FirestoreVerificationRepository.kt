package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** The chef's own view of their verification request. */
data class VerificationUi(
    val hasNidPhoto: Boolean,
    /** "pending" | "verified" | "rejected" | "" (never sent). */
    val status: String,
    val note: String,
    val submittedAt: Long
)

/**
 * chef_verifications/{chefUid} - PRIVATE (firestore.rules: only the chef and
 * admins can read it). Holds the chef's NID photo for the admin's check on
 * admin-web's Chef Verification page. The result (nidVerified /
 * kitchenVerified) is written by the admin on the public chef doc; the photo
 * itself never goes on the chef doc, which anyone can read.
 */
object FirestoreVerificationRepository {
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun get(uid: String): VerificationUi? {
        return try {
            val doc = db.collection("chef_verifications").document(uid).get().await()
            if (!doc.exists()) return null
            VerificationUi(
                hasNidPhoto = !doc.getString("nidPhotoBase64").isNullOrBlank(),
                status = doc.getString("status").orEmpty(),
                note = doc.getString("note").orEmpty(),
                submittedAt = doc.getLong("submittedAt") ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Sends (or replaces) the NID photo; puts the request back to "pending". */
    suspend fun submitNid(uid: String, nidPhotoBase64: String): Boolean {
        return try {
            db.collection("chef_verifications").document(uid).set(
                hashMapOf(
                    "chefId" to uid,
                    "nidPhotoBase64" to nidPhotoBase64,
                    "status" to "pending",
                    "note" to "",
                    "submittedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
