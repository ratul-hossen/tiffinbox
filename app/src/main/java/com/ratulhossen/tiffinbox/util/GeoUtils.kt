package com.ratulhossen.tiffinbox.util

import com.ratulhossen.tiffinbox.data.location.GeoPoint
import com.ratulhossen.tiffinbox.data.model.ChefUi
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

private const val EARTH_RADIUS_KM = 6371.0

/** The "2-3km" nearby radius the user asked for, in kilometers. */
const val NEARBY_RADIUS_KM = 3.0

/**
 * Default for the recommendation distance (admins can change it in admin-web
 * App Settings -> `app_config/settings.recommendRadiusKm`, read by
 * FirestoreAppConfigRepository). Recommendations (Home "Recommended for you",
 * AI picks, Today's Special banner, the chef list's Recommended sort) only
 * include kitchens within it once the customer has a saved location - recommending a kitchen
 * that can't deliver to them breaks trust. Wider than [NEARBY_RADIUS_KM] so
 * there is still something to recommend.
 */
const val RECOMMEND_RADIUS_KM = 10.0

/** Chefs a customer can be recommended: all of them until real distances are known. */
fun List<ChefUi>.recommendable(hasRealDistances: Boolean, radiusKm: Double = RECOMMEND_RADIUS_KM): List<ChefUi> =
    if (hasRealDistances) filter { it.distanceKm <= radiusKm } else this

/** Great-circle distance between two lat/lng points, in kilometers. */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_KM * c
}

/**
 * Replaces each chef's static/demo distanceKm with their real distance from
 * [from], rounded to one decimal place. A chef with no location set yet
 * (latitude/longitude both 0.0, i.e. signed up before this feature or never
 * completed LocationSetupScreen) is left out entirely, since Null Island
 * isn't a real distance.
 */
fun List<ChefUi>.withRealDistances(from: GeoPoint): List<ChefUi> {
    return this
        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
        .map { chef ->
            val km = haversineKm(from.latitude, from.longitude, chef.latitude, chef.longitude)
            chef.copy(distanceKm = (km * 10).roundToInt() / 10.0)
        }
}
