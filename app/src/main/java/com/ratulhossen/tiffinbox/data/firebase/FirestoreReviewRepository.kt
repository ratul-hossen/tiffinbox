package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A review as shown on the chef's public page. */
data class ReviewUi(
    val id: String,
    val overallRating: Double,
    val comment: String,
    val photoBase64: String,
    val createdOn: String
)

/**
 * Best-effort persistence, matches the Review model in README.md.
 * The overall rating uses the same weighting documented there:
 * 0.35 taste + 0.35 hygiene + 0.15 packaging + 0.15 on-time.
 */
object FirestoreReviewRepository {

    fun submitReview(
        chefId: String,
        taste: Int,
        hygiene: Int,
        packaging: Int,
        onTime: Int,
        comment: String,
        photoBase64: String = ""
    ) {
        try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return
            val overallRating = (0.35 * taste) + (0.35 * hygiene) + (0.15 * packaging) + (0.15 * onTime)

            val reviewData = hashMapOf(
                "customerId" to customerId,
                "chefId" to chefId,
                "tasteRating" to taste,
                "hygieneRating" to hygiene,
                "packagingRating" to packaging,
                "onTimeRating" to onTime,
                "overallRating" to overallRating,
                "comment" to comment,
                // Optional food photo, compressed base64 (same free-tier
                // pattern as kitchen/meal photos - no Firebase Storage).
                "photoBase64" to photoBase64,
                "createdAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("reviews").add(reviewData)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Most recent reviews for a chef (client-side sorted to avoid a composite index). */
    suspend fun getReviewsForChef(chefId: String, limit: Int = 20): List<ReviewUi> {
        return try {
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
            FirebaseFirestore.getInstance()
                .collection("reviews")
                .whereEqualTo("chefId", chefId)
                .limit(100)
                .get()
                .await()
                .documents
                .sortedByDescending { it.getLong("createdAt") ?: 0L }
                .take(limit)
                .map { doc ->
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    ReviewUi(
                        id = doc.id,
                        overallRating = doc.getDouble("overallRating") ?: 0.0,
                        comment = doc.getString("comment").orEmpty(),
                        photoBase64 = doc.getString("photoBase64").orEmpty(),
                        createdOn = if (createdAt > 0) dateFormat.format(Date(createdAt)) else ""
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
