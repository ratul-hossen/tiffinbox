package com.ratulhossen.tiffinbox.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * TiffinBox Points - a small "level up" game for customers.
 *
 * Earning: [POINTS_PER_TAKA] point per ৳10 of every *delivered* order.
 * The balance is never stored as an editable number: it's recomputed from
 * the customer's own orders (earned on Delivered, minus what they redeemed
 * on non-cancelled orders), so it can't be inflated by writing to a profile
 * field - no Cloud Functions needed. A cancelled order refunds its points
 * automatically because it simply stops counting.
 *
 * Ranks come from lifetime earned points and give a permanent % off the
 * food subtotal at checkout; the top rank also gets free delivery. On top
 * of that, points can be spent: [POINTS_PER_TAKA_OFF] points = ৳1 off, up to
 * [MAX_REDEEM_SHARE] of the subtotal.
 *
 * Rank names are original, anime-flavoured titles (no third-party
 * characters or trademarks).
 */
data class RewardRank(
    val level: Int,
    val emoji: String,
    val title: String,
    val minPoints: Int,
    /** % off the food subtotal at checkout. */
    val discountPercent: Int,
    val freeDelivery: Boolean = false
)

data class RewardsStatus(
    /** Lifetime points earned from delivered orders (decides the rank). */
    val lifetimePoints: Int,
    /** Spendable points = lifetime - redeemed. */
    val balance: Int,
    val deliveredOrders: Int,
    val rank: RewardRank,
    val nextRank: RewardRank?
) {
    /** 0..1 progress from the current rank's floor to the next rank. */
    val progressToNext: Float
        get() = nextRank?.let {
            ((lifetimePoints - rank.minPoints).toFloat() / (it.minPoints - rank.minPoints)).coerceIn(0f, 1f)
        } ?: 1f
    val pointsToNext: Int get() = nextRank?.let { (it.minPoints - lifetimePoints).coerceAtLeast(0) } ?: 0
}

object Rewards {
    /** 1 point per ৳10 spent. */
    const val POINTS_PER_TAKA = 10
    /** 10 points = ৳1 off. */
    const val POINTS_PER_TAKA_OFF = 10
    /** Points can cover at most 20% of the food subtotal. */
    const val MAX_REDEEM_SHARE = 0.20
    /** Rank discount never exceeds this many taka per order. */
    const val MAX_RANK_DISCOUNT = 60

    val RANKS = listOf(
        RewardRank(1, "🍙", "Rookie Eater", 0, 0),
        RewardRank(2, "🥷", "Food Ninja", 100, 2),
        RewardRank(3, "🏴‍☠️", "Snack Pirate", 300, 4),
        RewardRank(4, "⚔️", "Flavor Samurai", 700, 6),
        RewardRank(5, "🐉", "Legendary Glutton", 1500, 8, freeDelivery = true)
    )

    fun pointsFor(orderTotal: Int): Int = (orderTotal / POINTS_PER_TAKA).coerceAtLeast(0)

    fun rankFor(lifetimePoints: Int): RewardRank = RANKS.last { lifetimePoints >= it.minPoints }

    fun status(lifetimePoints: Int, redeemedPoints: Int, deliveredOrders: Int): RewardsStatus {
        val rank = rankFor(lifetimePoints)
        return RewardsStatus(
            lifetimePoints = lifetimePoints,
            balance = (lifetimePoints - redeemedPoints).coerceAtLeast(0),
            deliveredOrders = deliveredOrders,
            rank = rank,
            nextRank = RANKS.firstOrNull { it.minPoints > lifetimePoints }
        )
    }

    fun rankDiscount(rank: RewardRank, subtotal: Int): Int =
        (subtotal * rank.discountPercent / 100.0).roundToInt().coerceIn(0, MAX_RANK_DISCOUNT)

    /** Most points usable on an order with this food subtotal (whole taka only). */
    fun maxRedeemablePoints(balance: Int, subtotal: Int): Int {
        val maxTaka = (subtotal * MAX_REDEEM_SHARE).toInt()
        return minOf(balance / POINTS_PER_TAKA_OFF, maxTaka) * POINTS_PER_TAKA_OFF
    }

    fun takaForPoints(points: Int): Int = points / POINTS_PER_TAKA_OFF

    // --- Delivery-time estimate (no Maps API: plain distance math) ---------

    /** Kitchen prep time before the food leaves. */
    const val PREP_MINUTES = 20
    /** City motorbike pace incl. traffic, minutes per km. */
    const val MINUTES_PER_KM = 4.0

    /** Estimated minutes from placing the order to delivery, rounded up to 5. */
    fun estimatedDeliveryMinutes(distanceKm: Double?): Int {
        val travel = ceil((distanceKm ?: 3.0).coerceIn(0.5, 15.0) * MINUTES_PER_KM).toInt()
        val total = PREP_MINUTES + travel + 5
        return ((total + 4) / 5) * 5
    }
}
