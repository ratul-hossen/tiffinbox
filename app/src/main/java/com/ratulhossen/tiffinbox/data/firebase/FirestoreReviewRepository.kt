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
    /**
     * Per-kitchen review averages for the recommender (overall rating and the
     * customers' on-time / delivery rating), from the public `reviews`
     * collection. Kept in memory for a few minutes so Home and the chef list
     * don't re-read every review on each visit.
     */
    suspend fun getChefReviewStats(): Map<String, ChefReviewStats> {
        statsCache?.let { (at, stats) -> if (System.currentTimeMillis() - at < STATS_TTL_MS) return stats }
        return try {
            val stats = FirebaseFirestore.getInstance().collection("reviews").get().await().documents
                .groupBy { it.getString("chefId").orEmpty() }
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, docs) ->
                    val overall = docs.mapNotNull { it.getDouble("overallRating")?.takeIf { r -> r > 0 } }
                    val onTime = docs.mapNotNull { it.getDouble("onTimeRating")?.takeIf { r -> r > 0 } }
                    ChefReviewStats(
                        count = docs.size,
                        avgOverall = overall.average().takeIf { !it.isNaN() } ?: 0.0,
                        avgOnTime = onTime.average().takeIf { !it.isNaN() } ?: 0.0,
                        onTimeCount = onTime.size
                    )
                }
            statsCache = System.currentTimeMillis() to stats
            stats
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private const val STATS_TTL_MS = 5 * 60 * 1000L
    @Volatile private var statsCache: Pair<Long, Map<String, ChefReviewStats>>? = null

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

/** Review averages for one kitchen; see FirestoreReviewRepository.getChefReviewStats(). */
data class ChefReviewStats(
    val count: Int,
    val avgOverall: Double,
    val avgOnTime: Double,
    val onTimeCount: Int
)
