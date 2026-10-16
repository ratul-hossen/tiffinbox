package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * delivery_applications/{chefUid} - a chef's application to use TiffinBox's
 * delivery service (doc id = chef uid). [status]:
 *  - "pending"   - waiting for admin review (chef may edit / withdraw);
 *  - "approved"  - agreement running until [validUntil]; admin-web set the
 *                  chef's deliveryMode to "tiffinbox" (customers see the badge);
 *  - "rejected" / "cancelled" (admin ended it) / "expired" (time ran out) -
 *                  chef is back on self-delivery and may apply again.
 * No Cloud Functions on the free plan, so expiry is applied by whichever
 * client reads it first ([syncExpiry] here, admin-web's page too).
 */
data class DeliveryApplicationUi(
    val contactNumber: String,
    val pickupAddress: String,
    val expectedDailyOrders: Int,
    val deliveryRadiusKm: Int,
    val mealSlots: List<String>,
    val note: String,
    /** Effective status - an "approved" agreement past [validUntil] reads as "expired". */
    val status: String,
    val adminNote: String,
    val createdAt: Long,
    val validUntil: Long
) {
    val isActive: Boolean get() = status == "approved"
}

object FirestoreDeliveryApplicationRepository {

    /** Values stored in mealSlots - the UI shows translated labels. */
    val MEAL_SLOTS = listOf("Breakfast", "Lunch", "Dinner")

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private fun DocumentSnapshot.toApplication(): DeliveryApplicationUi = DeliveryApplicationUi(
        contactNumber = getString("contactNumber").orEmpty(),
        pickupAddress = getString("pickupAddress").orEmpty(),
        expectedDailyOrders = (getLong("expectedDailyOrders") ?: 0L).toInt(),
        deliveryRadiusKm = (getLong("deliveryRadiusKm") ?: 0L).toInt(),
        mealSlots = (get("mealSlots") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        note = getString("note").orEmpty(),
        status = (getString("status") ?: "pending").let { raw ->
            if (raw == "approved" && (getLong("validUntil") ?: 0L) < System.currentTimeMillis()) "expired" else raw
        },
        adminNote = getString("adminNote").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        validUntil = getLong("validUntil") ?: 0L
    )

    suspend fun getMyApplication(chefId: String): DeliveryApplicationUi? {
        return try {
            val doc = db.collection("delivery_applications").document(chefId).get().await()
            if (doc.exists()) doc.toApplication() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * If the chef's agreement has run out but is still stored as "approved",
     * records it as "expired", switches the chef back to self-delivery and
     * notifies them. Safe to call on every screen open. Returns the
     * (effective) application, or null if the chef never applied.
     */
    suspend fun syncExpiry(chefId: String): DeliveryApplicationUi? {
        return try {
            val ref = db.collection("delivery_applications").document(chefId)
            val doc = ref.get().await()
            if (!doc.exists()) return null
            val app = doc.toApplication()
            if (doc.getString("status") == "approved" && app.status == "expired") {
                ref.update(mapOf("status" to "expired", "expiredAt" to System.currentTimeMillis())).await()
                FirestoreChefRepository.updateDeliveryMode(chefId, "self")
                FirestoreNotificationRepository.addNotification(
                    uid = chefId,
                    title = "TiffinBox Delivery agreement ended",
                    body = "Your kitchen is back on self-delivery. Apply again any time from Delivery Option."
                )
            }
            app
        } catch (e: Exception) {
            null
        }
    }

    /** Creates the application, or re-submits it (while pending, or after it was rejected / ended). */
    suspend fun submit(
        chefId: String,
        kitchenName: String,
        ownerName: String,
        contactNumber: String,
        pickupAddress: String,
        latitude: Double,
        longitude: Double,
        expectedDailyOrders: Int,
        deliveryRadiusKm: Int,
        mealSlots: List<String>,
        note: String,
        createdAt: Long?
    ): Boolean {
        return try {
            val now = System.currentTimeMillis()
            db.collection("delivery_applications").document(chefId).set(
                hashMapOf(
                    "chefId" to chefId,
                    "kitchenName" to kitchenName,
                    "ownerName" to ownerName,
                    "contactNumber" to contactNumber,
                    "pickupAddress" to pickupAddress,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "expectedDailyOrders" to expectedDailyOrders,
                    "deliveryRadiusKm" to deliveryRadiusKm,
                    "mealSlots" to mealSlots,
                    "note" to note,
                    "status" to "pending",
                    "adminNote" to "",
                    "createdAt" to (createdAt ?: now),
                    "updatedAt" to now
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun withdraw(chefId: String): Boolean {
        return try {
            db.collection("delivery_applications").document(chefId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
