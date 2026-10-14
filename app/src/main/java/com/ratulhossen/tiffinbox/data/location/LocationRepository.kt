package com.ratulhossen.tiffinbox.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Uses plain Android/Play-Services location (FusedLocationProviderClient)
 * and the device's own free, on-device Geocoder - deliberately NOT the
 * (paid, Google Cloud billing-gated) Google Maps Platform. This gives exact
 * coordinates for distance math without needing a visual map or any
 * billing account; a map UI can be layered on top of the same stored
 * lat/lng later if that's ever turned on.
 */
object LocationRepository {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): GeoPoint? {
        if (!hasLocationPermission(context)) return null
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            location?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

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
}
