package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.CartItemUi
import com.ratulhossen.tiffinbox.data.model.ChefOrderUi
import com.ratulhossen.tiffinbox.data.model.EarningsSummary
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
        total: Int,
        deliveryAddress: String,
        paymentMethod: String
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
                "deliveryAddress" to deliveryAddress,
                "paymentMethod" to paymentMethod,
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

    /**
     * Shared fetch backing both the earnings summary and the incoming-orders
     * list, so the dashboard only needs one Firestore query. Capped at 200
     * most-recent-write-order docs (Firestore has no server-side orderBy
     * here to dodge composite-index requirements — see the whereEqualTo-only
     * filter above), which is a reasonable approximation for a chef who
     * hasn't done thousands of orders yet.
     */
    private suspend fun fetchRecentOrders(chefId: String): List<ChefOrderUi> {
        return try {
            FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("chefId", chefId)
                .limit(200)
                .get()
                .await()
                .documents
                .map { doc ->
                    val itemNames = (doc.get("items") as? List<*>)
                        ?.mapNotNull { (it as? Map<*, *>)?.get("mealName") as? String }
                        .orEmpty()
                    ChefOrderUi(
                        id = doc.id,
                        customerId = doc.getString("customerId").orEmpty(),
                        itemsSummary = itemNames.joinToString(", ").ifBlank { "—" },
                        totalPrice = (doc.getLong("totalPrice") ?: 0L).toInt(),
                        status = doc.getString("status") ?: "Placed",
                        placedAt = doc.getLong("placedAt") ?: 0L
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getIncomingOrders(chefId: String): List<ChefOrderUi> {
        return fetchRecentOrders(chefId)
            .filter { it.status == "Placed" }
            .sortedByDescending { it.placedAt }
    }

    suspend fun getEarningsSummary(chefId: String, chartDays: Int = 14): EarningsSummary {
        val orders = fetchRecentOrders(chefId)
        val dayFormat = SimpleDateFormat("MM/dd", Locale.US)
        val now = System.currentTimeMillis()
        val dayMillis = 24L * 60 * 60 * 1000
        val todayKey = dayFormat.format(Date(now))
        val weekCutoff = now - 7 * dayMillis
        val chartCutoff = now - chartDays * dayMillis

        val todayTotal = orders.filter { dayFormat.format(Date(it.placedAt)) == todayKey }
            .sumOf { it.totalPrice }
        val weekTotal = orders.filter { it.placedAt >= weekCutoff }.sumOf { it.totalPrice }

        val dailyTotals = orders
            .filter { it.placedAt >= chartCutoff }
            .groupBy { dayFormat.format(Date(it.placedAt)) }
            .toSortedMap()
            .map { (day, dayOrders) -> day to dayOrders.sumOf { it.totalPrice } }

        return EarningsSummary(
            todayTotal = todayTotal,
            weekTotal = weekTotal,
            recentOrderCount = orders.size,
            dailyTotals = dailyTotals
        )
    }

    fun updateOrderStatus(orderId: String, status: String) {
        try {
            FirebaseFirestore.getInstance().collection("orders").document(orderId).update("status", status)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
