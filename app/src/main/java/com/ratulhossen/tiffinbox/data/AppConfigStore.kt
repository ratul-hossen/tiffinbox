package com.ratulhossen.tiffinbox.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Admin-tunable numbers that screens read synchronously. Starts at the built-in
 * defaults and is overwritten once `app_config/settings` loads
 * (FirestoreAppConfigRepository.config()). Compose state, so anything already
 * showing these values updates on its own.
 */
object AppConfigStore {
    const val DEFAULT_PLATFORM_FEE = 10
    const val DEFAULT_PRE_ORDER_MAX_PLATES = 10
    const val DEFAULT_FEW_PLATES_THRESHOLD = 5

    var platformFee by mutableIntStateOf(DEFAULT_PLATFORM_FEE)
    var preOrderMaxPlates by mutableIntStateOf(DEFAULT_PRE_ORDER_MAX_PLATES)
    var fewPlatesThreshold by mutableIntStateOf(DEFAULT_FEW_PLATES_THRESHOLD)
}
