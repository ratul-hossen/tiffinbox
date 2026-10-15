package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.DeliveryPartnerUi
import com.ratulhossen.tiffinbox.data.model.SelfServiceRequestUi
import kotlinx.coroutines.tasks.await

/**
 * The reference app's "Self Service" concept: a customer-initiated
 * point-to-point delivery request (e.g. Home -> Office), independent of
 * ordering food from a chef. There's no delivery-partner Android app yet
 * (see CLAUDE.md phase 7 - a much larger, separate effort), so a hired
 * partner has nowhere of their own to see this; admin-web gets a page to
 * see/manage these requests in the interim, same as it already does for
 * delivery_partners itself.
 */
object FirestoreSelfServiceRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun createRequest(
        pickupAddress: String,
        dropAddress: String,
        preferredTime: String,
        note: String
    ): String? {
        return try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return null
            val data = hashMapOf(
                "customerId" to customerId,
                "pickupAddress" to pickupAddress,
                "dropAddress" to dropAddress,
                "preferredTime" to preferredTime,
                "note" to note,
                "status" to "pending",
                "deliveryPartnerId" to "",
                "createdAt" to System.currentTimeMillis()
            )
            val requestId = db.collection("self_service_requests").add(data).await().id
            FirestoreNotificationRepository.addNotification(
                uid = customerId,
                title = "Self-service request sent",
                body = "Your pickup/drop request has been submitted. Choose a delivery partner to hire."
            )
            requestId
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRequest(requestId: String): SelfServiceRequestUi? {
        return try {
            db.collection("self_service_requests").document(requestId).get().await().toRequestUi()
        } catch (e: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toRequestUi(): SelfServiceRequestUi? {
        if (!exists()) return null
        return SelfServiceRequestUi(
            id = id,
            pickupAddress = getString("pickupAddress") ?: "",
            dropAddress = getString("dropAddress") ?: "",
            preferredTime = getString("preferredTime") ?: "",
            note = getString("note") ?: "",
            status = getString("status") ?: "pending",
            deliveryPartnerId = getString("deliveryPartnerId") ?: "",
            createdAt = getLong("createdAt") ?: 0L
        )
    }

    suspend fun getActiveDeliveryPartners(): List<DeliveryPartnerUi> {
        return try {
            db.collection("delivery_partners")
                .whereEqualTo("isActive", true)
                .get()
                .await()
                .documents
                .map { doc ->
                    DeliveryPartnerUi(
                        id = doc.id,
                        name = doc.getString("name") ?: "Delivery Partner",
                        phone = doc.getString("phone") ?: ""
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun assignDeliveryPartner(requestId: String, partnerId: String) {
        try {
            db.collection("self_service_requests").document(requestId).update(
                mapOf("deliveryPartnerId" to partnerId, "status" to "assigned")
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
