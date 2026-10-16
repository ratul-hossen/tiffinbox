package com.ratulhossen.tiffinbox.domain

import kotlin.math.ceil

/**
 * Delivery fee rules, set by the admin in admin-web App Settings
 * (`app_config/settings`): [baseFee] covers the first [includedKm], then
 * [perKmFee] for every started km beyond it. Defaults keep the old flat
 * ৳20 for short trips.
 */
data class DeliveryPricing(
    val baseFee: Int = 20,
    val perKmFee: Int = 5,
    val includedKm: Double = 2.0
)

/** What a customer pays and roughly waits for one kitchen. */
data class DeliveryQuote(val fee: Int, val minutes: Int)

object DeliveryQuotes {
    /** [distanceKm] null = unknown location: base fee and the default estimate. */
    fun quote(distanceKm: Double?, pricing: DeliveryPricing): DeliveryQuote {
        val extraKm = ((distanceKm ?: 0.0) - pricing.includedKm).coerceAtLeast(0.0)
        val fee = pricing.baseFee + ceil(extraKm).toInt() * pricing.perKmFee
        return DeliveryQuote(fee = fee, minutes = Rewards.estimatedDeliveryMinutes(distanceKm))
    }
}
