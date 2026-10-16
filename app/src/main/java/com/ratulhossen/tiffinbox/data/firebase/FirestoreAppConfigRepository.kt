package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.domain.DeliveryPricing
import com.ratulhossen.tiffinbox.util.RECOMMEND_RADIUS_KM
import kotlinx.coroutines.tasks.await

/**
 * App-wide settings the admin sets in admin-web's App Settings page
 * (`app_config/settings`). Read once per app run and kept in memory; any
 * failure or missing value falls back to the built-in default.
 */
object FirestoreAppConfigRepository {

    data class AppConfig(
        val recommendRadiusKm: Double = RECOMMEND_RADIUS_KM,
        val delivery: DeliveryPricing = DeliveryPricing()
    )

    @Volatile private var cached: AppConfig? = null

    suspend fun config(): AppConfig {
        cached?.let { return it }
        return try {
            val doc = FirebaseFirestore.getInstance().collection("app_config").document("settings").get().await()
            val defaults = DeliveryPricing()
            AppConfig(
                recommendRadiusKm = doc.getDouble("recommendRadiusKm")?.takeIf { it > 0 } ?: RECOMMEND_RADIUS_KM,
                delivery = DeliveryPricing(
                    baseFee = doc.getLong("deliveryBaseFee")?.toInt()?.takeIf { it >= 0 } ?: defaults.baseFee,
                    perKmFee = doc.getLong("deliveryPerKmFee")?.toInt()?.takeIf { it >= 0 } ?: defaults.perKmFee,
                    includedKm = doc.getDouble("deliveryIncludedKm")?.takeIf { it >= 0 } ?: defaults.includedKm
                )
            ).also { cached = it }
        } catch (e: Exception) {
            AppConfig()
        }
    }

    /** Max distance (km) a kitchen may be from the customer and still be recommended. */
    suspend fun recommendRadiusKm(): Double = config().recommendRadiusKm

    suspend fun deliveryPricing(): DeliveryPricing = config().delivery
}
