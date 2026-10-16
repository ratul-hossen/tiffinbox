package com.ratulhossen.tiffinbox.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Why a "use my current location" attempt failed - each maps to its own message/action in the UI. */
enum class LocationFailure { NO_PERMISSION, LOCATION_OFF, NO_FIX }

sealed class LocationResult {
    data class Success(val point: GeoPoint) : LocationResult()
    data class Failure(val reason: LocationFailure) : LocationResult()
}

/**
 * Uses plain Android/Play-Services location (FusedLocationProviderClient)
 * and the device's own free, on-device Geocoder - deliberately NOT the
 * (paid, Google Cloud billing-gated) Google Maps Platform. This gives exact
 * coordinates for distance math without needing a visual map or any
 * billing account; a map UI can be layered on top of the same stored
 * lat/lng later if that's ever turned on.
 */
object LocationRepository {

    /** Both permissions - Android 12+ lets the user grant only "approximate", which is still enough here. */
    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasLocationPermission(context: Context): Boolean {
        return hasFinePermission(context) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** False when the phone's Location / GPS toggle is switched off. */
    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /**
     * Tries, in order: a fresh fix (high accuracy if "precise" was granted,
     * up to 15s), the fused provider's last known location, then any
     * provider's last known location from the platform LocationManager. The
     * old version only asked once for a balanced-power fix and gave up,
     * which often returned null indoors or right after GPS was switched on.
     */
    @SuppressLint("MissingPermission")
    suspend fun detectCurrentLocation(context: Context): LocationResult {
        if (!hasLocationPermission(context)) return LocationResult.Failure(LocationFailure.NO_PERMISSION)
        if (!isLocationEnabled(context)) return LocationResult.Failure(LocationFailure.LOCATION_OFF)

        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (hasFinePermission(context)) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val fresh: Location? = try {
            val cancellation = CancellationTokenSource()
            try {
                withTimeoutOrNull(15_000L) {
                    client.getCurrentLocation(priority, cancellation.token).await()
                }
            } finally {
                cancellation.cancel()
            }
        } catch (e: Exception) {
            null
        }

        val location = fresh
            ?: try { client.lastLocation.await() } catch (e: Exception) { null }
            ?: lastKnownFromPlatform(context)

        return if (location != null) {
            LocationResult.Success(GeoPoint(location.latitude, location.longitude))
        } else {
            LocationResult.Failure(LocationFailure.NO_FIX)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownFromPlatform(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            manager.getProviders(true)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
        } catch (e: Exception) {
            null
        }
    }

    /** Kept for existing callers - same as [detectCurrentLocation] but just the point (or null). */
    suspend fun getCurrentLocation(context: Context): GeoPoint? =
        (detectCurrentLocation(context) as? LocationResult.Success)?.point

    /** Resolves free-text address to coordinates via the device's built-in Geocoder. */
    suspend fun geocodeAddress(context: Context, address: String): GeoPoint? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
            results?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

    /** Coordinates -> a short human-readable address (area, city), or null if the Geocoder can't tell. */
    suspend fun reverseGeocode(context: Context, point: GeoPoint): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            @Suppress("DEPRECATION")
            val result = Geocoder(context, Locale.getDefault()).getFromLocation(point.latitude, point.longitude, 1)
                ?.firstOrNull() ?: return@withContext null
            listOfNotNull(result.subLocality, result.locality ?: result.subAdminArea)
                .distinct()
                .joinToString(", ")
                .ifBlank { result.getAddressLine(0) }
        } catch (e: Exception) {
            null
        }
    }
}
