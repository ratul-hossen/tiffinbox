package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.ratulhossen.tiffinbox.data.location.GeoPoint
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val name: String,
    val email: String,
    val role: String,
    val phoneNumber: String,
    val whatsappNumber: String,
    val address: String,
    val hasLocation: Boolean
)

/** One of a user's (max [MAX_SAVED_ADDRESSES]) saved delivery addresses. */
data class SavedAddress(
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

const val MAX_SAVED_ADDRESSES = 2

object FirestoreUserRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun upsertProfile(uid: String, name: String, email: String, role: String, phoneNumber: String = "", whatsappNumber: String = "") {
        try {
            db.collection("users").document(uid).set(
                hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to role,
                    "phoneNumber" to phoneNumber,
                    "whatsappNumber" to whatsappNumber,
                    "createdAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Fetches the current FCM token and saves it, so a push backend (once one exists) can target this device. */
    suspend fun registerFcmToken(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            saveFcmToken(uid, token)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    fun saveFcmToken(uid: String, token: String) {
        try {
            db.collection("users").document(uid).set(
                hashMapOf("fcmToken" to token),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    suspend fun getRole(uid: String): String? {
        return try {
            db.collection("users").document(uid).get().await().getString("role")
        } catch (e: Exception) {
            null
        }
    }

    private val profileCache = java.util.concurrent.ConcurrentHashMap<String, UserProfile>()
    private val locationCache = java.util.concurrent.ConcurrentHashMap<String, GeoPoint>()

    fun saveLocation(uid: String, latitude: Double, longitude: Double, address: String = "") {
        try {
            locationCache[uid] = GeoPoint(latitude, longitude)
            profileCache[uid]?.let {
                profileCache[uid] = it.copy(address = address.ifBlank { it.address }, hasLocation = true)
            }
            db.collection("users").document(uid).set(
                hashMapOf("latitude" to latitude, "longitude" to longitude, "address" to address),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** Fields shown on the Edit Profile screen; cached in memory to save database reads. */
    suspend fun getProfile(uid: String, forceRefresh: Boolean = false): UserProfile? {
        if (!forceRefresh) {
            profileCache[uid]?.let { return it }
        }
        return try {
            val doc = db.collection("users").document(uid).get().await()
            val profile = UserProfile(
                name = doc.getString("name").orEmpty(),
                email = doc.getString("email").orEmpty(),
                role = doc.getString("role").orEmpty(),
                phoneNumber = doc.getString("phoneNumber").orEmpty(),
                whatsappNumber = doc.getString("whatsappNumber").orEmpty(),
                address = doc.getString("address").orEmpty(),
                hasLocation = doc.getDouble("latitude") != null && doc.getDouble("longitude") != null
            )
            profileCache[uid] = profile
            val lat = doc.getDouble("latitude")
            val lng = doc.getDouble("longitude")
            if (lat != null && lng != null) {
                locationCache[uid] = GeoPoint(lat, lng)
            }
            profile
        } catch (e: Exception) {
            profileCache[uid]
        }
    }

    /** Edit Profile save - merges only these fields, never touches role/location. */
    suspend fun updateProfile(uid: String, name: String, phoneNumber: String, whatsappNumber: String): Boolean {
        return try {
            db.collection("users").document(uid).set(
                hashMapOf(
                    "name" to name,
                    "phoneNumber" to phoneNumber,
                    "whatsappNumber" to whatsappNumber
                ),
                SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Saved addresses (users/{uid}.savedAddresses) + which one is the
     * default. Accounts from before this feature only have the top-level
     * address/latitude/longitude - that's surfaced as a single "Home" entry
     * so nothing is lost.
     */
    suspend fun getSavedAddresses(uid: String): Pair<List<SavedAddress>, Int> {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            val list = (doc.get("savedAddresses") as? List<*>)?.mapNotNull { raw ->
                val m = raw as? Map<*, *> ?: return@mapNotNull null
                SavedAddress(
                    label = m["label"] as? String ?: "",
                    address = m["address"] as? String ?: "",
                    latitude = (m["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (m["longitude"] as? Number)?.toDouble() ?: 0.0
                )
            }.orEmpty()
            if (list.isNotEmpty()) {
                list.take(MAX_SAVED_ADDRESSES) to ((doc.getLong("defaultAddressIndex") ?: 0L).toInt().coerceIn(0, list.size - 1))
            } else {
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                if (lat != null && lng != null) {
                    listOf(SavedAddress("Home", doc.getString("address").orEmpty(), lat, lng)) to 0
                } else {
                    emptyList<SavedAddress>() to 0
                }
            }
        } catch (e: Exception) {
            emptyList<SavedAddress>() to 0
        }
    }

    /**
     * Writes the whole list plus the default index, and mirrors the default
     * address into the top-level address/latitude/longitude that the
     * nearby-chef distance math (and chef profile) already reads.
     */
    suspend fun saveSavedAddresses(uid: String, addresses: List<SavedAddress>, defaultIndex: Int): Boolean {
        return try {
            val trimmed = addresses.take(MAX_SAVED_ADDRESSES)
            val data = hashMapOf<String, Any>(
                "savedAddresses" to trimmed.map {
                    mapOf("label" to it.label, "address" to it.address, "latitude" to it.latitude, "longitude" to it.longitude)
                },
                "defaultAddressIndex" to defaultIndex.coerceIn(0, (trimmed.size - 1).coerceAtLeast(0))
            )
            val def = trimmed.getOrNull(defaultIndex.coerceIn(0, (trimmed.size - 1).coerceAtLeast(0)))
            if (def != null) {
                data["address"] = def.address
                data["latitude"] = def.latitude
                data["longitude"] = def.longitude
            } else {
                // Last address removed - clear the mirror so Home doesn't keep
                // measuring distances from a location the user deleted.
                data["address"] = FieldValue.delete()
                data["latitude"] = FieldValue.delete()
                data["longitude"] = FieldValue.delete()
            }
            db.collection("users").document(uid).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Null if the user hasn't set a location yet; cached in memory to avoid repeated queries. */
    suspend fun getLocation(uid: String, forceRefresh: Boolean = false): GeoPoint? {
        if (!forceRefresh) {
            locationCache[uid]?.let { return it }
        }
        return try {
            val doc = db.collection("users").document(uid).get().await()
            val lat = doc.getDouble("latitude")
            val lng = doc.getDouble("longitude")
            if (lat != null && lng != null) {
                val point = GeoPoint(lat, lng)
                locationCache[uid] = point
                point
            } else null
        } catch (e: Exception) {
            locationCache[uid]
        }
    }
}
