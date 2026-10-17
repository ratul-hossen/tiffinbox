package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.domain.DeliveryPricing
import com.ratulhossen.tiffinbox.domain.DeliverySurcharge
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
        val delivery: DeliveryPricing = DeliveryPricing(),
        /** Global ceiling on a self-delivering chef's own fee; 0 = no cap. */
        val deliveryMaxFee: Int = 0,
        val platformFee: Int = AppConfigStore.DEFAULT_PLATFORM_FEE,
        val preOrderMaxPlates: Int = AppConfigStore.DEFAULT_PRE_ORDER_MAX_PLATES,
        val fewPlatesThreshold: Int = AppConfigStore.DEFAULT_FEW_PLATES_THRESHOLD
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
                ),
                deliveryMaxFee = doc.getLong("deliveryMaxFee")?.toInt()?.takeIf { it >= 0 } ?: 0,
                platformFee = doc.getLong("platformFee")?.toInt()?.takeIf { it in 0..200 }
                    ?: AppConfigStore.DEFAULT_PLATFORM_FEE,
                preOrderMaxPlates = doc.getLong("preOrderMaxPlates")?.toInt()?.takeIf { it in 1..50 }
                    ?: AppConfigStore.DEFAULT_PRE_ORDER_MAX_PLATES,
                fewPlatesThreshold = doc.getLong("fewPlatesThreshold")?.toInt()?.takeIf { it in 1..20 }
                    ?: AppConfigStore.DEFAULT_FEW_PLATES_THRESHOLD
            ).also {
                cached = it
                AppConfigStore.platformFee = it.platformFee
                AppConfigStore.preOrderMaxPlates = it.preOrderMaxPlates
                AppConfigStore.fewPlatesThreshold = it.fewPlatesThreshold
            }
        } catch (e: Exception) {
            AppConfig()
        }
    }

    /** Max distance (km) a kitchen may be from the customer and still be recommended. */
    suspend fun recommendRadiusKm(): Double = config().recommendRadiusKm

    /** TiffinBox's own delivery rates (used when a kitchen is on "tiffinbox" delivery). */
    suspend fun deliveryPricing(): DeliveryPricing = config().delivery

    /** Global ceiling on a self-delivering chef's fee when no per-chef cap is set. */
    suspend fun globalMaxDeliveryFee(): Int = config().deliveryMaxFee

    /**
     * Area surcharges that are switched on and inside their time window right
     * now. Read once per app run like the rest of this config - a surge the
     * admin starts mid-session shows up the next time the app is opened,
     * which is fine for a "festival day" style setting and keeps this to a
     * single Firestore read.
     */
    suspend fun activeSurcharges(): List<DeliverySurcharge> {
        cachedSurcharges?.let { return it }
        return try {
            val now = System.currentTimeMillis()
            FirebaseFirestore.getInstance().collection("delivery_surcharges").get().await().documents
                .mapNotNull { doc ->
                    DeliverySurcharge(
                        id = doc.id,
                        areaName = doc.getString("areaName") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        radiusKm = doc.getDouble("radiusKm") ?: 0.0,
                        type = doc.getString("type") ?: DeliverySurcharge.TYPE_FLAT,
                        amount = doc.getDouble("amount") ?: 0.0,
                        validFrom = doc.getLong("validFrom") ?: 0L,
                        validUntil = doc.getLong("validUntil") ?: 0L,
                        isActive = doc.getBoolean("isActive") ?: true
                    ).takeIf { it.isLiveAt(now) }
                }
                .also { cachedSurcharges = it }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Volatile private var cachedSurcharges: List<DeliverySurcharge>? = null
}
