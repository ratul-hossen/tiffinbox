package com.ratulhossen.tiffinbox.domain

import com.ratulhossen.tiffinbox.data.model.ChefUi

/**
 * Hybrid scoring engine matching the formula documented in README.md:
 *
 *   score = 0.30 * proximity_score
 *         + 0.25 * chef_rating
 *         + 0.20 * budget_match
 *         + 0.15 * dietary_match
 *         + 0.10 * repeat_order_bonus
 *
 * budget_match, dietary_match and repeat_order_bonus need per-user context
 * (past orders, stated preferences) that the app doesn't collect yet, so
 * they default to a neutral 0.5 / 0.0 rather than faking personalization.
 * Once order history and dietary filters are wired to a real user profile,
 * swap those defaults for the real signals.
 */
object RecommendationEngine {

    private const val PROXIMITY_WEIGHT = 0.30
    private const val RATING_WEIGHT = 0.25
    private const val BUDGET_WEIGHT = 0.20
    private const val DIETARY_WEIGHT = 0.15
    private const val REPEAT_ORDER_WEIGHT = 0.10

    private const val NEUTRAL_BUDGET_MATCH = 0.5
    private const val NEUTRAL_DIETARY_MATCH = 0.5
    private const val DEFAULT_REPEAT_ORDER_BONUS = 0.0

    fun score(chef: ChefUi, maxDistanceKm: Double): Double {
        val proximityScore = if (maxDistanceKm > 0) {
            (1.0 - (chef.distanceKm / maxDistanceKm)).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        val ratingScore = (chef.overallRating / 5.0).coerceIn(0.0, 1.0)

        return (PROXIMITY_WEIGHT * proximityScore) +
            (RATING_WEIGHT * ratingScore) +
            (BUDGET_WEIGHT * NEUTRAL_BUDGET_MATCH) +
            (DIETARY_WEIGHT * NEUTRAL_DIETARY_MATCH) +
            (REPEAT_ORDER_WEIGHT * DEFAULT_REPEAT_ORDER_BONUS)
    }

    /** Chefs ranked best-match-first for a "Recommended for you" style section. */
    fun recommend(chefs: List<ChefUi>): List<ChefUi> {
        val maxDistance = chefs.maxOfOrNull { it.distanceKm } ?: 0.0
        return chefs.sortedByDescending { score(it, maxDistance) }
    }
}
