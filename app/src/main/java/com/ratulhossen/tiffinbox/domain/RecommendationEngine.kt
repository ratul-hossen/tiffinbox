package com.ratulhossen.tiffinbox.domain

import com.ratulhossen.tiffinbox.data.firebase.ChefReviewStats
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.util.RECOMMEND_RADIUS_KM
import kotlin.math.ln

/**
 * Everything the recommender knows about the customer and the kitchens,
 * gathered once per screen load (all free Firestore reads, no server).
 */
data class RecommendationContext(
    val hasRealDistances: Boolean = false,
    val radiusKm: Double = RECOMMEND_RADIUS_KM,
    /** Per-kitchen review averages (overall + on-time/delivery), by chefId. */
    val reviewStats: Map<String, ChefReviewStats> = emptyMap(),
    /** How many times this customer ordered from each kitchen, by chefId. */
    val myOrderCounts: Map<String, Int> = emptyMap(),
    /** Active coupons per kitchen ("" = platform-wide), by chefId. */
    val offerCounts: Map<String, Int> = emptyMap()
)

/**
 * Kitchen ranking for "Recommended for you" and the chef list's Recommended
 * sort. Distance is a plus, not the whole story (owner, 2026-10-16):
 *
 *   score = 0.25 rating        (customers' overall stars, Bayesian-smoothed)
 *         + 0.20 delivery      (customers' on-time stars, Bayesian-smoothed)
 *         + 0.20 proximity     (closer within the recommend radius = better)
 *         + 0.10 review volume (more reviews = more trustworthy rating)
 *         + 0.10 order volume  (completed orders on the kitchen)
 *         + 0.10 repeat orders (kitchens this customer already liked)
 *         + 0.05 offers        (an active coupon there)
 *
 * then x0.7 when the kitchen isn't taking orders today. Bayesian smoothing
 * (prior 3.5 stars worth 3 reviews) stops a brand-new kitchen with one
 * 5-star review from outranking one with fifty 4.7-star reviews, while a
 * kitchen with no reviews yet still gets a fair, neutral score.
 */
object RecommendationEngine {

    private const val RATING_WEIGHT = 0.25
    private const val DELIVERY_WEIGHT = 0.20
    private const val PROXIMITY_WEIGHT = 0.20
    private const val REVIEW_VOLUME_WEIGHT = 0.10
    private const val ORDER_VOLUME_WEIGHT = 0.10
    private const val REPEAT_WEIGHT = 0.10
    private const val OFFER_WEIGHT = 0.05
    private const val UNAVAILABLE_TODAY_FACTOR = 0.7

    private const val PRIOR_STARS = 3.5
    private const val PRIOR_WEIGHT = 3.0

    /** Stars (0..5) pulled toward [PRIOR_STARS] when there are few reviews, as 0..1. */
    private fun smoothedStars(avg: Double, count: Int): Double {
        val n = if (avg > 0) count.toDouble() else 0.0
        return ((PRIOR_STARS * PRIOR_WEIGHT + avg * n) / (PRIOR_WEIGHT + n) / 5.0).coerceIn(0.0, 1.0)
    }

    private fun logShare(value: Int, max: Int): Double =
        if (max <= 0) 0.0 else (ln(1.0 + value) / ln(1.0 + max)).coerceIn(0.0, 1.0)

    /** 1 at the customer's door, 0 at the edge of the radius; neutral without a location. */
    fun proximity(distanceKm: Double, ctx: RecommendationContext): Double =
        if (!ctx.hasRealDistances || ctx.radiusKm <= 0) 0.5
        else (1.0 - distanceKm / ctx.radiusKm).coerceIn(0.0, 1.0)

    /** Food + delivery quality only (0..1), also used to boost AI meal picks. */
    fun quality(chef: ChefUi, ctx: RecommendationContext): Double {
        val stats = ctx.reviewStats[chef.id]
        // No reviews collected yet: fall back to the kitchen's own stored
        // rating (demo kitchens) counted as a single review.
        val rating = if (stats != null && stats.avgOverall > 0) smoothedStars(stats.avgOverall, stats.count)
        else smoothedStars(chef.overallRating, if (chef.overallRating > 0) 1 else 0)
        val delivery = if (stats != null && stats.avgOnTime > 0) smoothedStars(stats.avgOnTime, stats.onTimeCount)
        else smoothedStars(chef.onTime, if (chef.onTime > 0) 1 else 0)
        return (RATING_WEIGHT * rating + DELIVERY_WEIGHT * delivery) / (RATING_WEIGHT + DELIVERY_WEIGHT)
    }

    fun score(chef: ChefUi, ctx: RecommendationContext, maxReviews: Int, maxOrders: Int): Double {
        val stats = ctx.reviewStats[chef.id]
        val qualityPart = (RATING_WEIGHT + DELIVERY_WEIGHT) * quality(chef, ctx)
        val total = qualityPart +
            PROXIMITY_WEIGHT * proximity(chef.distanceKm, ctx) +
            REVIEW_VOLUME_WEIGHT * logShare(stats?.count ?: 0, maxReviews) +
            ORDER_VOLUME_WEIGHT * logShare(chef.totalOrders, maxOrders) +
            REPEAT_WEIGHT * ((ctx.myOrderCounts[chef.id] ?: 0).coerceAtMost(3) / 3.0) +
            OFFER_WEIGHT * (if ((ctx.offerCounts[chef.id] ?: 0) > 0) 1.0 else 0.0)
        return if (chef.isAvailableToday) total else total * UNAVAILABLE_TODAY_FACTOR
    }

    /** Kitchens ranked best-match-first. */
    fun recommend(chefs: List<ChefUi>, ctx: RecommendationContext = RecommendationContext()): List<ChefUi> {
        val maxReviews = chefs.maxOfOrNull { ctx.reviewStats[it.id]?.count ?: 0 } ?: 0
        val maxOrders = chefs.maxOfOrNull { it.totalOrders } ?: 0
        return chefs.sortedByDescending { score(it, ctx, maxReviews, maxOrders) }
    }
}
