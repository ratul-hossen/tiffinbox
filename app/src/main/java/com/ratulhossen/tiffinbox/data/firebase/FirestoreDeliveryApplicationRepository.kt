package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * delivery_applications/{chefUid} - a chef's application to use TiffinBox's
 * own delivery service once it launches (the delivery system itself, CLAUDE.md
 * phase 7, isn't built yet; this only collects interest). One doc per chef
 * (doc id = chef uid). [status] is "pending" | "approved" | "rejected", set
 * by admin-web's Delivery Applications page; the chef can edit or withdraw
 * only while it's still pending.
 */
data class DeliveryApplicationUi(
    val contactNumber: String,
    val pickupAddress: String,
    val expectedDailyOrders: Int,
    val deliveryRadiusKm: Int,
    val mealSlots: List<String>,
    val note: String,
    val status: String,
    val adminNote: String,
    val createdAt: Long
)

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
        status = getString("status") ?: "pending",
        adminNote = getString("adminNote").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L
    )

    suspend fun getMyApplication(chefId: String): DeliveryApplicationUi? {
        return try {
            val doc = db.collection("delivery_applications").document(chefId).get().await()
            if (doc.exists()) doc.toApplication() else null
        } catch (e: Exception) {
            null
        }
    }

    /** Creates or updates (while pending) the chef's application. Returns true on success. */
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
