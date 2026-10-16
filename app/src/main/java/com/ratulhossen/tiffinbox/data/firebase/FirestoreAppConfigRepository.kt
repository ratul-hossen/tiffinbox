package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.util.RECOMMEND_RADIUS_KM
import kotlinx.coroutines.tasks.await

/**
 * App-wide settings the admin sets in admin-web's App Settings page
 * (`app_config/settings`). Read once per app run and kept in memory; any
 * failure or missing value falls back to the built-in default.
 */
object FirestoreAppConfigRepository {
    @Volatile private var cachedRadiusKm: Double? = null

    /** Max distance (km) a kitchen may be from the customer and still be recommended. */
    suspend fun recommendRadiusKm(): Double {
        cachedRadiusKm?.let { return it }
        val km = try {
            FirebaseFirestore.getInstance().collection("app_config").document("settings").get().await()
                .getDouble("recommendRadiusKm")
        } catch (e: Exception) {
            null
        }
        val value = km?.takeIf { it > 0 } ?: RECOMMEND_RADIUS_KM
        if (km != null) cachedRadiusKm = value
        return value
    }
}
