package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore

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
        comment: String
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
                "createdAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("reviews").add(reviewData)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
