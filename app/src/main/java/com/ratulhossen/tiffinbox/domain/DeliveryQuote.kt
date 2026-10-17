package com.ratulhossen.tiffinbox.domain

import com.ratulhossen.tiffinbox.util.haversineKm
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Delivery fee rules: [baseFee] covers the first [includedKm], then
 * [perKmFee] for every started km beyond it. Defaults keep the old flat
 * ৳20 for short trips.
 *
 * Two different sets of these are in play, picked by the kitchen's
 * `deliveryMode` (see [DeliveryQuotes.quote]):
 *  - **"tiffinbox"** - the admin's own rates from admin-web App Settings
 *    (`app_config/settings`). This is TiffinBox's delivery service, so the
 *    money is TiffinBox's: no per-kitchen cap, but an area surcharge may
 *    apply (see [DeliverySurcharge]).
 *  - **"self"** - the chef delivers themselves and sets their own rates on
 *    `chefs/{uid}` (selfDeliveryBaseFee/PerKmFee/IncludedKm). A chef who has
 *    set nothing falls back to the admin's rates, so the default is always
 *    "same as the app's charge". [maxFee] is the ceiling an admin fixed for
 *    that kitchen - the chef can never charge above it.
 */
data class DeliveryPricing(
    val baseFee: Int = 20,
    val perKmFee: Int = 5,
    val includedKm: Double = 2.0,
    /** Admin's ceiling on this kitchen's own fee; 0 = no cap. Self delivery only. */
    val maxFee: Int = 0
)

/**
 * An extra delivery charge the admin applies to one area for a window of
 * time - e.g. a festival day in Dhanmondi (admin-web Delivery Surcharges ->
 * `delivery_surcharges/{id}`).
 *
 * Deliberately **TiffinBox delivery only** (owner's decision): when a chef
 * delivers themselves the fee is their income, so TiffinBox doesn't add a
 * cut on top of it.
 *
 * The area is a plain centre + radius tested with haversine - no Maps
 * billing, same as the rest of this project's location work.
 */
data class DeliverySurcharge(
    val id: String = "",
    val areaName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusKm: Double = 0.0,
    /** [TYPE_FLAT] = [amount] taka added; [TYPE_MULTIPLY] = fee x [amount]. */
    val type: String = TYPE_FLAT,
    val amount: Double = 0.0,
    val validFrom: Long = 0L,
    val validUntil: Long = 0L,
    val isActive: Boolean = true
) {
    /** True when this surcharge is switched on and [now] is inside its window. */
    fun isLiveAt(now: Long): Boolean =
        isActive && (validFrom <= 0L || now >= validFrom) && (validUntil <= 0L || now <= validUntil)

    /** True when the customer's location falls inside the zone. */
    fun covers(latitude: Double, longitude: Double): Boolean {
        if (radiusKm <= 0.0) return false
        if (this.latitude == 0.0 && this.longitude == 0.0) return false
        return haversineKm(latitude, longitude, this.latitude, this.longitude) <= radiusKm
    }

    /** Extra taka on top of a [fee], never negative. */
    fun extraOn(fee: Int): Int = when (type) {
        TYPE_MULTIPLY -> (fee * (amount - 1.0)).roundToInt()
        else -> amount.roundToInt()
    }.coerceAtLeast(0)

    companion object {
        const val TYPE_FLAT = "flat"
        const val TYPE_MULTIPLY = "multiply"
    }
}

/**
 * What a customer pays and roughly waits for one kitchen. [surcharge] is the
 * part of [fee] that comes from an area surcharge, broken out so Checkout can
 * show it on its own line - a bill that quietly grew on a festival day reads
 * as a bug to the customer.
 */
data class DeliveryQuote(
    val fee: Int,
    val minutes: Int,
    val surcharge: Int = 0,
    val surchargeArea: String = ""
)

object DeliveryQuotes {
    /**
     * Hard ceiling on one kitchen's delivery fee, whatever the admin or the
     * chef has configured. App Settings allows a ৳500 base and ৳100/km, and a
     * recommend radius up to 50 km, which multiplies out to ৳5500 - a number
     * no home-kitchen order should ever show, and above the sanity bound
     * `orderMoneySane()` in firestore.rules allows, so the order would be
     * refused rather than merely look wrong. Applied before the surcharge,
     * like the per-kitchen cap.
     */
    const val MAX_FEE = 1000

    /**
     * [distanceKm] null = unknown location: base fee and the default estimate.
     * [surcharge] should already have been matched to the customer's location
     * and the current time by the caller (see DeliveryQuoter).
     */
    fun quote(
        distanceKm: Double?,
        pricing: DeliveryPricing,
        surcharge: DeliverySurcharge? = null
    ): DeliveryQuote {
        val extraKm = ((distanceKm ?: 0.0) - pricing.includedKm).coerceAtLeast(0.0)
        val raw = pricing.baseFee + ceil(extraKm).toInt() * pricing.perKmFee
        // The cap is on what the kitchen charges, so it is applied before the
        // surcharge - TiffinBox's own surge isn't something a chef's ceiling
        // should swallow (and the two never combine anyway: a cap only exists
        // in self delivery, a surcharge only in TiffinBox delivery).
        val capped = (if (pricing.maxFee > 0) raw.coerceAtMost(pricing.maxFee) else raw)
            .coerceAtMost(MAX_FEE)
        val extra = surcharge?.extraOn(capped) ?: 0
        return DeliveryQuote(
            fee = (capped + extra).coerceAtLeast(0),
            minutes = Rewards.estimatedDeliveryMinutes(distanceKm),
            surcharge = extra,
            surchargeArea = if (extra > 0) surcharge?.areaName.orEmpty() else ""
        )
    }
}
