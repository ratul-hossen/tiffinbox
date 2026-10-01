package com.ratulhossen.tiffinbox.domain

import kotlin.math.ceil

/**
 * Deliberately simple moving-average forecast, matching the spirit of the
 * "গত ৩ সপ্তাহের বৃহস্পতিবারের trend অনুযায়ী..." example in README.md.
 * Returns null instead of a fabricated number when there isn't enough
 * order history yet to forecast responsibly.
 */
object DemandForecastEngine {

    private const val MIN_DAYS_OF_HISTORY = 3
    private const val LOOKBACK_DAYS = 14
    private const val SAFETY_BUFFER = 1.1

    fun forecastNextDayPlates(dailyOrderCounts: List<Int>): Int? {
        if (dailyOrderCounts.size < MIN_DAYS_OF_HISTORY) return null
        val recent = dailyOrderCounts.takeLast(LOOKBACK_DAYS)
        val average = recent.average()
        return ceil(average * SAFETY_BUFFER).toInt().coerceAtLeast(1)
    }
}
