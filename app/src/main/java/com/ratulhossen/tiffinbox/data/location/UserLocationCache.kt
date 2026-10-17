package com.ratulhossen.tiffinbox.data.location

import android.content.Context

/**
 * Last known customer lat/lng coordinates per account, kept on the phone so distances and map compute with 0ms delay.
 */
object UserLocationCache {
    private const val PREFS = "home_user_location_cache"

    fun read(context: Context, uid: String?): GeoPoint? {
        if (uid == null) return null
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = sp.getString("${uid}_lat", null)?.toDoubleOrNull() ?: return null
        val lng = sp.getString("${uid}_lng", null)?.toDoubleOrNull() ?: return null
        return if (lat != 0.0 || lng != 0.0) GeoPoint(lat, lng) else null
    }

    fun write(context: Context, uid: String, point: GeoPoint) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${uid}_lat", point.latitude.toString())
            .putString("${uid}_lng", point.longitude.toString())
            .apply()
    }
}
