package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.CartItemUi
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Best-effort persistence: writes silently do nothing if Firebase isn't
 * configured yet or the device is offline, so they never interrupt the
 * demo flow. This mirrors the Order model in README.md.
 */
object FirestoreOrderRepository {

    fun placeOrder(
        items: List<CartItemUi>,
        subtotal: Int,
        deliveryFee: Int,
        platformFee: Int,
        total: Int
    ) {
        try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return
            val chefId = items.firstOrNull()?.meal?.chefId ?: return

            val orderData = hashMapOf(
                "customerId" to customerId,
                "chefId" to chefId,
                "items" to items.map { item ->
                    mapOf(
                        "mealId" to item.meal.id,
                        "mealName" to item.meal.name,
                        "quantity" to item.quantity,
                        "unitPrice" to item.meal.price
                    )
                },
                "subtotal" to subtotal,
                "deliveryFee" to deliveryFee,
                "platformFee" to platformFee,
                "totalPrice" to total,
                "status" to "Placed",
                "placedAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("orders").add(orderData)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /**
     * One order count per distinct day in the lookback window, oldest
     * first, for DemandForecastEngine. Grouped client-side (rather than a
     * server-side orderBy/range query) so this never needs a Firestore
     * composite index.
     */
    suspend fun getDailyOrderCountsForChef(chefId: String, lookbackDays: Int = 30): List<Int> {
        return try {
            val cutoff = System.currentTimeMillis() - lookbackDays * 24L * 60 * 60 * 1000
            val snapshot = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("chefId", chefId)
                .limit(200)
                .get()
                .await()

            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            snapshot.documents
                .mapNotNull { it.getLong("placedAt") }
                .filter { it >= cutoff }
                .groupBy { dayFormat.format(Date(it)) }
                .toSortedMap()
                .map { it.value.size }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
