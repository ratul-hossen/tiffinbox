package com.ratulhossen.tiffinbox.data.firebase

import com.ratulhossen.tiffinbox.domain.RewardsStatus
import com.ratulhossen.tiffinbox.domain.Rewards
import com.ratulhossen.tiffinbox.data.model.CUSTOMER_CANCEL_WINDOW_MS
import com.google.firebase.firestore.DocumentSnapshot
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ratulhossen.tiffinbox.data.model.CartItemUi
import com.ratulhossen.tiffinbox.data.model.ChefOrderUi
import com.ratulhossen.tiffinbox.data.model.EarningsSummary
import com.ratulhossen.tiffinbox.data.model.OrderStage
import com.ratulhossen.tiffinbox.data.model.PastOrderUi
import com.ratulhossen.tiffinbox.data.model.toOrderStage
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Best-effort persistence: writes silently do nothing if Firebase isn't
 * configured yet or the device is offline, so they never interrupt the
 * demo flow. This mirrors the Order model in README.md.
 */
/** A "Placed" order the chef hasn't accepted within this window is cancelled automatically. */
const val ORDER_ACCEPT_TIMEOUT_MS = 12L * 60 * 60 * 1000

/** [orderId] on success; [soldOutMeal] names a meal that ran out of plates (blank name = unknown). */
data class PlaceOrderResult(val orderId: String? = null, val soldOutMeal: String? = null)

object FirestoreOrderRepository {

    /**
     * Free-tier stand-in for a scheduled Cloud Function: whenever an order is
     * read (customer history, chef dashboard, admin-web), a still-"Placed"
     * order older than [ORDER_ACCEPT_TIMEOUT_MS] is flipped to "Cancelled"
     * (cancelReason = "auto_expired") on the spot. Returns the status the
     * caller should show.
     */
    private fun effectiveStatus(doc: DocumentSnapshot): String {
        val status = doc.getString("status") ?: "Placed"
        val placedAt = doc.getLong("placedAt") ?: 0L
        // A pre-order isn't "late" before its own slot either.
        val deadline = maxOf(placedAt + ORDER_ACCEPT_TIMEOUT_MS, doc.getLong("scheduledFor") ?: 0L)
        if (status == "Placed" && placedAt > 0 && System.currentTimeMillis() > deadline) {
            // Fire-and-forget: also hands the reserved plates back to the menu.
            cancelInTransaction(doc.reference, "auto_expired") { it.getString("status") == "Placed" }
                .addOnSuccessListener { cancelled ->
                    val customerId = cancelled?.getString("customerId") ?: return@addOnSuccessListener
                    FirestoreNotificationRepository.addNotification(
                        uid = customerId,
                        title = "Order cancelled",
                        body = "The chef didn't accept your order in time, so it was cancelled automatically."
                    )
                }
            return "Cancelled"
        }
        return status
    }

    /**
     * Cancels an order in one transaction and, if it reserved plates when it
     * was placed, gives those plates back to each meal's availableQuantity
     * (firestore.rules checks the amounts against the order's mealQuantities).
     * [guard] sees the fresh order; returning false aborts with no change.
     * The task's result is the order snapshot, or null when nothing changed.
     */
    private fun cancelInTransaction(
        ref: DocumentReference,
        reason: String,
        guard: (DocumentSnapshot) -> Boolean
    ): Task<DocumentSnapshot?> {
        val db = FirebaseFirestore.getInstance()
        return db.runTransaction<DocumentSnapshot?> { tx ->
            val snap = tx.get(ref)
            if (!snap.exists() || !guard(snap)) return@runTransaction null
            val update = mutableMapOf<String, Any>(
                "status" to "Cancelled",
                "cancelReason" to reason,
                "cancelledAt" to System.currentTimeMillis()
            )
            val restore = mutableListOf<Pair<DocumentReference, Long>>()
            if (snap.getBoolean("platesReserved") == true && snap.getBoolean("platesRestored") != true) {
                update["platesRestored"] = true
                (snap.get("mealQuantities") as? Map<*, *>)?.forEach { (key, value) ->
                    val mealRef = db.collection("meals").document(key as? String ?: return@forEach)
                    val qty = (value as? Number)?.toLong() ?: return@forEach
                    // Skip meals the chef has deleted since.
                    if (qty > 0 && tx.get(mealRef).exists()) restore += mealRef to qty
                }
            }
            restore.forEach { (mealRef, qty) ->
                tx.update(mealRef, mapOf("availableQuantity" to FieldValue.increment(qty), "lastRestoreOrderId" to ref.id))
            }
            tx.update(ref, update)
            snap
        }
    }

    /**
     * Customer-side cancel, only allowed while the order is still "Placed"
     * (checked inside a transaction so it can't race the chef accepting).
     * Returns true if it was cancelled.
     */
    suspend fun cancelOrderByCustomer(orderId: String): Boolean {
        return try {
            val ref = FirebaseFirestore.getInstance().collection("orders").document(orderId)
            val snap = cancelInTransaction(ref, "customer") {
                it.getString("status") == "Placed" &&
                    System.currentTimeMillis() - (it.getLong("placedAt") ?: 0L) <= CUSTOMER_CANCEL_WINDOW_MS
            }.await() ?: return false
            val chefId = snap.getString("chefId").orEmpty()
            if (chefId.isNotBlank()) {
                FirestoreNotificationRepository.addNotification(
                    uid = chefId,
                    title = "Order cancelled",
                    body = "A customer cancelled their order before you accepted it."
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Thrown inside the placing transaction when a meal no longer has enough plates. */
    private class SoldOutException(val mealName: String) : Exception()

    /**
     * Places the order. An order for now reserves its plates: in the same
     * transaction each meal's availableQuantity goes down by the quantity
     * ordered (and fails with [PlaceOrderResult.soldOutMeal] if there aren't
     * enough left). A pre-order ([scheduledFor] > 0) reserves nothing - it is
     * the chef's advance demand for that day, so they cook only what's needed.
     */
    suspend fun placeOrder(
        items: List<CartItemUi>,
        subtotal: Int,
        deliveryFee: Int,
        platformFee: Int,
        total: Int,
        deliveryAddress: String,
        paymentMethod: String,
        paymentTransactionId: String? = null,
        paymentScreenshotBase64: String? = null,
        couponCode: String = "",
        discount: Int = 0,
        pointsRedeemed: Int = 0,
        rankDiscount: Int = 0,
        estimatedDeliveryMinutes: Int = 0,
        scheduledFor: Long = 0L
    ): PlaceOrderResult {
        return try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return PlaceOrderResult()
            val chefId = items.firstOrNull()?.meal?.chefId ?: return PlaceOrderResult()
            val isPreOrder = scheduledFor > 0
            val mealQuantities = items.groupBy { it.meal.id }.mapValues { (_, list) -> list.sumOf { it.quantity }.toLong() }
            val deliveryHandledBy = FirestoreCatalogRepository.getChef(chefId)?.deliveryMode ?: "self"

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
                // total already has the coupon discount taken off.
                "couponCode" to couponCode,
                "discount" to discount,
                // TiffinBox Points spent + rank discount (both already off totalPrice).
                "pointsRedeemed" to pointsRedeemed,
                "rankDiscount" to rankDiscount,
                "estimatedDeliveryMinutes" to estimatedDeliveryMinutes,
                "totalPrice" to total,
                "deliveryAddress" to deliveryAddress,
                "paymentMethod" to paymentMethod,
                "paymentTransactionId" to paymentTransactionId.orEmpty(),
                "paymentScreenshotBase64" to paymentScreenshotBase64.orEmpty(),
                // "self" = the chef delivers it; "tiffinbox" = needs a delivery
                // partner assigned (deliveryPartnerId is set later, once the
                // separate delivery-partner app exists to do that assignment).
                "deliveryHandledBy" to deliveryHandledBy,
                "deliveryPartnerId" to "",
                "status" to "Placed",
                "placedAt" to System.currentTimeMillis(),
                // Plates bookkeeping (see firestore.rules meals block) + pre-order slot.
                "mealIds" to mealQuantities.keys.toList(),
                "mealQuantities" to mealQuantities,
                "platesReserved" to !isPreOrder,
                "platesRestored" to false,
                "isPreOrder" to isPreOrder,
                "scheduledFor" to scheduledFor
            )

            val db = FirebaseFirestore.getInstance()
            val orderRef = db.collection("orders").document()
            if (isPreOrder) {
                orderRef.set(orderData).await()
            } else {
                db.runTransaction<Any?> { tx ->
                    val meals = mealQuantities.keys.map { tx.get(db.collection("meals").document(it)) }
                    meals.forEach { meal ->
                        val left = meal.getLong("availableQuantity") ?: 0L
                        if (!meal.exists() || left < (mealQuantities[meal.id] ?: 0L)) {
                            throw SoldOutException(meal.getString("name") ?: "")
                        }
                    }
                    tx.set(orderRef, orderData)
                    meals.forEach { meal ->
                        val left = meal.getLong("availableQuantity") ?: 0L
                        tx.update(
                            meal.reference,
                            mapOf("availableQuantity" to left - (mealQuantities[meal.id] ?: 0L), "lastOrderId" to orderRef.id)
                        )
                    }
                    null
                }.await()
            }
            val orderId = orderRef.id
            FirestoreNotificationRepository.addNotification(
                uid = customerId,
                title = "Order placed",
                body = if (isPreOrder) "Your pre-order has been placed and is waiting for the chef to accept it."
                else "Your order has been placed and is waiting for the chef to accept it."
            )
            PlaceOrderResult(orderId = orderId)
        } catch (e: Exception) {
            // The transaction may wrap our exception.
            val soldOut = (e as? SoldOutException) ?: (e.cause as? SoldOutException)
            PlaceOrderResult(soldOutMeal = soldOut?.mealName)
        }
    }

    /**
     * Live-watches a single order's real status (and chefId, for the
     * post-delivery review step) so the customer sees exactly what the chef
     * does on their dashboard — no more a client-only fake progress timer.
     */
    fun listenOrder(
        orderId: String,
        onUpdate: (status: String?, chefId: String?) -> Unit
    ): ListenerRegistration? {
        return try {
            FirebaseFirestore.getInstance().collection("orders").document(orderId)
                .addSnapshotListener { snapshot, _ ->
                    onUpdate(snapshot?.getString("status"), snapshot?.getString("chefId"))
                }
        } catch (e: Exception) {
            null
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
                        status = effectiveStatus(doc),
                        placedAt = doc.getLong("placedAt") ?: 0L,
                        paymentMethod = doc.getString("paymentMethod").orEmpty(),
                        paymentTransactionId = doc.getString("paymentTransactionId").orEmpty(),
                        paymentScreenshotBase64 = doc.getString("paymentScreenshotBase64").orEmpty(),
                        scheduledFor = doc.getLong("scheduledFor") ?: 0L,
                        itemQuantities = (doc.get("items") as? List<*>).orEmpty()
                            .mapNotNull { it as? Map<*, *> }
                            .groupBy { it["mealName"] as? String ?: "—" }
                            .mapValues { (_, rows) -> rows.sumOf { (it["quantity"] as? Number)?.toInt() ?: 0 } }
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Accepted-or-waiting pre-orders whose slot is still ahead, soonest first. */
    suspend fun getUpcomingPreOrders(chefId: String): List<ChefOrderUi> {
        val now = System.currentTimeMillis()
        return fetchRecentOrders(chefId)
            .filter { it.scheduledFor > now && it.status != "Cancelled" && it.status != "Delivered" }
            .sortedBy { it.scheduledFor }
    }

    suspend fun getIncomingOrders(chefId: String): List<ChefOrderUi> {
        return fetchRecentOrders(chefId)
            .filter { it.status == "Placed" }
            .sortedByDescending { it.placedAt }
    }

    suspend fun getEarningsSummary(chefId: String, chartDays: Int = 14): EarningsSummary {
        val orders = fetchRecentOrders(chefId).filter { it.status != "Cancelled" }
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

    /**
     * A customer's own order history (OrdersScreen) — real Firestore data,
     * not the SampleData placeholder it showed before. Chef name/emoji are
     * looked up per distinct chefId (a handful of orders at demo scale, so
     * one lookup per chef rather than a join).
     */
    suspend fun getMyOrders(): List<PastOrderUi> {
        return try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return emptyList()
            val snapshot = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("customerId", customerId)
                .limit(100)
                .get()
                .await()

            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)
            val chefCache = HashMap<String, Pair<String, String>>()

            snapshot.documents
                .sortedByDescending { it.getLong("placedAt") ?: 0L }
                .mapNotNull { doc ->
                    val chefId = doc.getString("chefId") ?: return@mapNotNull null
                    val (chefName, chefEmoji) = chefCache.getOrPut(chefId) {
                        val chef = FirestoreCatalogRepository.getChef(chefId)
                        (chef?.kitchenName ?: "—") to (chef?.emoji ?: "🍽️")
                    }
                    val itemNames = (doc.get("items") as? List<*>)
                        ?.mapNotNull { (it as? Map<*, *>)?.get("mealName") as? String }
                        .orEmpty()
                    val placedAt = doc.getLong("placedAt") ?: 0L
                    val stage = effectiveStatus(doc).toOrderStage() ?: OrderStage.PLACED
                    val total = (doc.getLong("totalPrice") ?: 0L).toInt()
                    PastOrderUi(
                        id = doc.id,
                        chefName = chefName,
                        chefEmoji = chefEmoji,
                        itemsSummary = itemNames.joinToString(", ").ifBlank { "—" },
                        total = total,
                        status = stage,
                        placedOn = if (placedAt > 0) dateFormat.format(Date(placedAt)) else "",
                        chefId = chefId,
                        cancelReason = doc.getString("cancelReason")
                            ?: if (doc.getString("status") == "Placed") "auto_expired" else "",
                        placedAtMillis = placedAt,
                        estimatedDeliveryMinutes = (doc.getLong("estimatedDeliveryMinutes") ?: 0L).toInt(),
                        pointsEarned = if (stage == OrderStage.DELIVERED) Rewards.pointsFor(total) else 0,
                        scheduledFor = doc.getLong("scheduledFor") ?: 0L,
                        mealQuantities = (doc.get("items") as? List<*>).orEmpty()
                            .mapNotNull { it as? Map<*, *> }
                            .mapNotNull { row ->
                                val id = row["mealId"] as? String ?: return@mapNotNull null
                                id to ((row["quantity"] as? Number)?.toInt() ?: 1)
                            }
                            .groupBy({ it.first }, { it.second })
                            .mapValues { (_, q) -> q.sum() }
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * TiffinBox Points, recomputed from the customer's own orders every
     * time (see domain/Rewards.kt) - earned on Delivered orders, minus points
     * redeemed on orders that weren't cancelled.
     */
    suspend fun getMyRewards(): RewardsStatus {
        return try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return Rewards.status(0, 0, 0)
            val docs = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("customerId", customerId)
                .get()
                .await()
                .documents
            val delivered = docs.filter { it.getString("status") == "Delivered" }
            val earned = delivered.sumOf { Rewards.pointsFor((it.getLong("totalPrice") ?: 0L).toInt()) }
            val redeemed = docs
                .filter { it.getString("status") != "Cancelled" }
                .sumOf { (it.getLong("pointsRedeemed") ?: 0L).toInt() }
            Rewards.status(earned, redeemed, delivered.size)
        } catch (e: Exception) {
            Rewards.status(0, 0, 0)
        }
    }

    /**
     * Personalization signal for the AI recommendations on Home: how many
     * non-cancelled orders this customer has placed per chef, plus every
     * mealId they've ordered. Empty for a new or signed-out user.
     */
    suspend fun getMyOrderSignals(): Pair<Map<String, Int>, Set<String>> {
        return try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return emptyMap<String, Int>() to emptySet()
            val docs = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("customerId", customerId)
                .limit(100)
                .get()
                .await()
                .documents
                .filter { it.getString("status") != "Cancelled" }
            val chefCounts = docs.mapNotNull { it.getString("chefId") }.groupingBy { it }.eachCount()
            val mealIds = docs.flatMap { doc ->
                (doc.get("items") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("mealId") as? String }.orEmpty()
            }.toSet()
            chefCounts to mealIds
        } catch (e: Exception) {
            emptyMap<String, Int>() to emptySet()
        }
    }

    fun updateOrderStatus(orderId: String, status: String, customerId: String? = null) {
        try {
            val ref = FirebaseFirestore.getInstance().collection("orders").document(orderId)
            if (status == "Cancelled") {
                // Chef rejected it: also give the reserved plates back to the menu.
                cancelInTransaction(ref, "chef") { it.getString("status") != "Cancelled" }
            } else {
                ref.update("status", status)
            }
            if (customerId != null) {
                FirestoreNotificationRepository.addNotification(
                    uid = customerId,
                    title = "Order update",
                    body = "Your order is now: $status"
                )
            }
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
