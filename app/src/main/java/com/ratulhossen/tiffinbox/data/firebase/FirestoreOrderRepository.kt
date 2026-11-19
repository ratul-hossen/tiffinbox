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
/** Statuses of an order the chef has accepted but not yet delivered, in order. */
val ACTIVE_ORDER_STATUSES = listOf("Accepted", "Cooking", "OutForDelivery")

/**
 * AI agent orders (mcp-server) before TiffinBox has verified their advance
 * payment. They live in the Orders tab's "AI agent orders" section, not in
 * the history, and never count as a chef's order or earnings.
 */
const val AGENT_AWAITING_CONFIRMATION = "AwaitingConfirmation"
const val AGENT_AWAITING_PAYMENT = "AwaitingPayment"
const val AGENT_PAYMENT_SUBMITTED = "PaymentSubmitted"
val AGENT_PENDING_STATUSES = listOf(AGENT_AWAITING_CONFIRMATION, AGENT_AWAITING_PAYMENT, AGENT_PAYMENT_SUBMITTED)

/** After confirming, the customer has this long to pay (matches mcp-server LIMITS.paymentWindowMs). */
const val AGENT_PAYMENT_WINDOW_MS = 60L * 60 * 1000

/**
 * A payment is still accepted this long after `payBy` (docs/AGENT_PAYMENTS.md
 * PAYMENT_GRACE), so someone who paid at the last minute can still type the TrxID.
 */
const val AGENT_PAYMENT_GRACE_MS = 30L * 60 * 1000

/** One agent order waiting on the customer or on TiffinBox. */
data class AgentOrderUi(
    val id: String,
    val chefId: String = "",
    val kitchenName: String,
    val itemsSummary: String,
    val total: Int,
    val status: String,
    val agentName: String,
    val confirmBy: Long,
    val payBy: Long,
    val paymentMethod: String,
    val paymentTransactionId: String,
    val deliveryAddress: String = "",
    val subtotal: Int = 0,
    val deliveryFee: Int = 0,
    val platformFee: Int = 0,
    /** Coupon discount and its code (blank = no coupon). */
    val discount: Int = 0,
    val couponCode: String = "",
    val rankDiscount: Int = 0,
    val estimatedDeliveryMinutes: Int = 0,
    /** The admin's "Ask again" note, shown above the payment form. */
    val paymentNote: String = ""
) {
    val shortId: String get() = id.takeLast(6).uppercase()

    /** Past the confirm deadline, or past payBy + grace: nothing can be done any more. */
    fun isExpired(now: Long): Boolean = when (status) {
        AGENT_AWAITING_CONFIRMATION -> confirmBy in 1 until now
        AGENT_AWAITING_PAYMENT -> payBy > 0 && now > payBy + AGENT_PAYMENT_GRACE_MS
        else -> false
    }
}

/** A paid agent order (TiffinBox verified the advance payment) - cancelling it means a refund. */
private fun DocumentSnapshot.isPaidAgentOrder(): Boolean =
    getString("source") == "agent" && (getLong("paymentVerifiedAt") ?: 0L) > 0

/** What happened when the customer typed a TrxID in the app. */
enum class AgentPaymentResult { OK, INVALID, ALREADY_USED_OR_FAILED }

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
        // An agent order the customer didn't confirm / pay in time (no Cloud Functions:
        // whoever reads it first cancels it; the MCP server does the same).
        val agentDeadline = when (status) {
            AGENT_AWAITING_CONFIRMATION -> doc.getLong("confirmBy") ?: 0L
            AGENT_AWAITING_PAYMENT -> (doc.getLong("payBy") ?: 0L).let { if (it > 0) it + AGENT_PAYMENT_GRACE_MS else 0L }
            else -> 0L
        }
        if (agentDeadline > 0 && System.currentTimeMillis() > agentDeadline) {
            cancelInTransaction(doc.reference, "auto_expired") { it.getString("status") == status }
            return "Cancelled"
        }
        // A pre-order isn't "late" before its own slot either.
        val deadline = maxOf(placedAt + ORDER_ACCEPT_TIMEOUT_MS, doc.getLong("scheduledFor") ?: 0L)
        if (status == "Placed" && placedAt > 0 && System.currentTimeMillis() > deadline) {
            // Fire-and-forget: also hands the reserved plates back to the menu.
            cancelInTransaction(doc.reference, "auto_expired", refundReason = "chef_timeout") { it.getString("status") == "Placed" }
                .addOnSuccessListener { cancelled ->
                    val customerId = cancelled?.getString("customerId") ?: return@addOnSuccessListener
                    // A paid agent order gets the refund notice from cancelInTransaction instead.
                    if (cancelled.isPaidAgentOrder()) return@addOnSuccessListener
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
     *
     * A paid agent order (docs/AGENT_PAYMENTS.md: the chef rejects it, or it
     * sits 12 h unaccepted) also records the money owed back - refundStatus
     * "due", refundAmount, refundReason ([refundReason], default [reason]) -
     * and the customer is told a refund is coming.
     */
    private fun cancelInTransaction(
        ref: DocumentReference,
        reason: String,
        refundReason: String = reason,
        guard: (DocumentSnapshot) -> Boolean
    ): Task<DocumentSnapshot?> {
        val db = FirebaseFirestore.getInstance()
        var refundAmount = 0L
        val task = db.runTransaction<DocumentSnapshot?> { tx ->
            val snap = tx.get(ref)
            if (!snap.exists() || !guard(snap)) return@runTransaction null
            val update = mutableMapOf<String, Any>(
                "status" to "Cancelled",
                "cancelReason" to reason,
                "cancelledAt" to System.currentTimeMillis()
            )
            refundAmount = 0L
            if (snap.isPaidAgentOrder()) {
                // An overpayment still owed is part of what goes back (same as admin-web's orderPlates.ts).
                val alreadyOwed = if (snap.getString("refundStatus") == "due") snap.getLong("refundAmount") ?: 0L else 0L
                refundAmount = (snap.getLong("totalPrice") ?: 0L) + alreadyOwed
                update["refundStatus"] = "due"
                update["refundAmount"] = refundAmount
                update["refundReason"] = refundReason
            }
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
        task.addOnSuccessListener { snap ->
            if (snap == null || !snap.isPaidAgentOrder()) return@addOnSuccessListener
            val customerId = snap.getString("customerId").orEmpty()
            if (customerId.isBlank()) return@addOnSuccessListener
            FirestoreNotificationRepository.addNotification(
                uid = customerId,
                title = "রিফান্ড আসছে · Refund on the way",
                body = (if (refundReason == "chef_timeout") "The kitchen didn't accept order #${ref.id.takeLast(6).uppercase()} in time, so it was cancelled. "
                else "Order #${ref.id.takeLast(6).uppercase()} was cancelled. ") +
                    "৳$refundAmount will be refunded to the number you paid from."
            )
        }
        return task
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
            // Copied onto the order so the chef can see who and where to
            // deliver to: firestore.rules deliberately doesn't let a chef read
            // a customer's users/{uid} doc, and shouldn't - this snapshot is
            // exactly the part of it that this one order needs.
            val profile = FirestoreUserRepository.getProfile(customerId)
            val deliveryPoint = FirestoreUserRepository.getLocation(customerId)

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
                "customerName" to profile?.name.orEmpty(),
                "customerPhone" to profile?.phoneNumber.orEmpty(),
                "deliveryLatitude" to (deliveryPoint?.latitude ?: 0.0),
                "deliveryLongitude" to (deliveryPoint?.longitude ?: 0.0),
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
            val itemsSummary = items.joinToString(", ") { "${it.meal.name} x${it.quantity}" }
            val chefObj = FirestoreCatalogRepository.getChef(chefId)
            FirestoreChatRepository.sendOrderPlacedMessage(
                customerId = customerId,
                chefId = chefId,
                chefName = chefObj?.kitchenName ?: "Chef",
                orderId = orderId,
                itemsSummary = itemsSummary,
                totalPrice = total,
                isPreOrder = isPreOrder,
                paymentMethod = paymentMethod
            )
            FirestoreNotificationRepository.addNotification(
                uid = chefId,
                title = if (isPreOrder) "New pre-order" else "New order",
                body = "$itemsSummary - ৳$total. Open your dashboard to accept it."
            )
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
                // Cancelled orders and agent orders TiffinBox hasn't verified yet aren't real demand.
                .filter { val st = it.getString("status") ?: "Placed"; st != "Cancelled" && st !in AGENT_PENDING_STATUSES }
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
                        customerName = doc.getString("customerName").orEmpty(),
                        customerPhone = doc.getString("customerPhone").orEmpty(),
                        deliveryAddress = doc.getString("deliveryAddress").orEmpty(),
                        deliveryLatitude = doc.getDouble("deliveryLatitude") ?: 0.0,
                        deliveryLongitude = doc.getDouble("deliveryLongitude") ?: 0.0,
                        itemQuantities = (doc.get("items") as? List<*>).orEmpty()
                            .mapNotNull { it as? Map<*, *> }
                            .groupBy { it["mealName"] as? String ?: "—" }
                            .mapValues { (_, rows) -> rows.sumOf { (it["quantity"] as? Number)?.toInt() ?: 0 } },
                        source = doc.getString("source").orEmpty(),
                        paymentVerifiedAt = doc.getLong("paymentVerifiedAt") ?: 0L
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * True when [customerId] has a paid agent order with this kitchen that is
     * still running (Placed -> OutForDelivery). The chef's chat warns before
     * a payment request then: that order is already paid to TiffinBox.
     */
    suspend fun hasActivePaidAgentOrder(chefId: String, customerId: String): Boolean = try {
        FirebaseFirestore.getInstance().collection("orders")
            .whereEqualTo("chefId", chefId)
            .whereEqualTo("customerId", customerId)
            .limit(50)
            .get()
            .await()
            .documents
            .any { it.isPaidAgentOrder() && (it.getString("status") ?: "") in listOf("Placed") + ACTIVE_ORDER_STATUSES }
    } catch (e: Exception) {
        false
    }

    /** Accepted-or-waiting pre-orders whose slot is still ahead, soonest first. */
    suspend fun getUpcomingPreOrders(chefId: String): List<ChefOrderUi> {
        val now = System.currentTimeMillis()
        return fetchRecentOrders(chefId)
            .filter { it.scheduledFor > now && it.status != "Cancelled" && it.status != "Delivered" && it.status !in AGENT_PENDING_STATUSES }
            .sortedBy { it.scheduledFor }
    }

    suspend fun getIncomingOrders(chefId: String): List<ChefOrderUi> {
        return fetchRecentOrders(chefId)
            .filter { it.status == "Placed" }
            .sortedByDescending { it.placedAt }
    }

    /**
     * Orders the chef accepted but hasn't finished yet (Accepted -> Cooking ->
     * OutForDelivery). Before, an order vanished from the dashboard once
     * accepted and nobody in the app could move it to Delivered, so with no
     * delivery rider it stayed "in progress" forever (cash-on-delivery orders
     * were never closed). The chef now walks it through to Delivered.
     */
    suspend fun getActiveOrders(chefId: String): List<ChefOrderUi> {
        return fetchRecentOrders(chefId)
            .filter { it.status in ACTIVE_ORDER_STATUSES }
            .sortedByDescending { it.placedAt }
    }

    suspend fun getEarningsSummary(chefId: String, chartDays: Int = 14): EarningsSummary {
        val orders = fetchRecentOrders(chefId).filter { it.status != "Cancelled" && it.status !in AGENT_PENDING_STATUSES }
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
                // Agent orders still waiting for confirm/payment are shown in their own section.
                .filter { (it.getString("status") ?: "") !in AGENT_PENDING_STATUSES }
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
                    // Cancelled just now by the 12 h expiry: this snapshot predates the refund fields.
                    val justExpiredPaid = stage == OrderStage.CANCELLED && doc.getString("status") == "Placed" && doc.isPaidAgentOrder()
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
                            .mapValues { (_, q) -> q.sum() },
                        viaAgent = doc.getString("source") == "agent",
                        paid = (doc.getLong("paymentVerifiedAt") ?: 0L) > 0,
                        rejectReason = doc.getString("rejectReason").orEmpty(),
                        refundStatus = if (justExpiredPaid) "due" else doc.getString("refundStatus").orEmpty(),
                        refundAmount = if (justExpiredPaid) total else (doc.getLong("refundAmount") ?: 0L).toInt(),
                        refundTrxId = doc.getString("refundTrxId").orEmpty()
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

    // ---------------------------------------------------------- AI agent orders

    /** Live list of this customer's agent orders that are waiting on them or on TiffinBox. */
    fun listenMyAgentOrders(onUpdate: (List<AgentOrderUi>) -> Unit): ListenerRegistration? {
        val uid = FirebaseAuthRepository.currentUser?.uid ?: return null
        return try {
            FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("customerId", uid)
                .whereEqualTo("source", "agent")
                .addSnapshotListener { snapshot, _ ->
                    val list = snapshot?.documents.orEmpty()
                        .filter { (it.getString("status") ?: "") in AGENT_PENDING_STATUSES }
                        .filter { effectiveStatus(it) != "Cancelled" }
                        .sortedByDescending { it.getLong("placedAt") ?: 0L }
                        .map { doc ->
                            val items = (doc.get("items") as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                            AgentOrderUi(
                                id = doc.id,
                                chefId = doc.getString("chefId").orEmpty(),
                                // Filled in by the card with getChef() when the cache doesn't have it.
                                kitchenName = FirestoreCatalogRepository.getCachedChefs()
                                    .find { it.id == doc.getString("chefId") }?.kitchenName ?: "",
                                itemsSummary = items.joinToString(", ") {
                                    "${it["mealName"] as? String ?: "-"} ×${(it["quantity"] as? Number)?.toInt() ?: 1}"
                                },
                                total = (doc.getLong("totalPrice") ?: 0L).toInt(),
                                status = doc.getString("status").orEmpty(),
                                agentName = doc.getString("agentClientName").orEmpty().ifBlank { "AI agent" },
                                confirmBy = doc.getLong("confirmBy") ?: 0L,
                                payBy = doc.getLong("payBy") ?: 0L,
                                paymentMethod = doc.getString("paymentMethod").orEmpty(),
                                paymentTransactionId = doc.getString("paymentTransactionId").orEmpty(),
                                deliveryAddress = doc.getString("deliveryAddress").orEmpty(),
                                subtotal = (doc.getLong("subtotal") ?: 0L).toInt(),
                                deliveryFee = (doc.getLong("deliveryFee") ?: 0L).toInt(),
                                platformFee = (doc.getLong("platformFee") ?: 0L).toInt(),
                                discount = (doc.getLong("discount") ?: 0L).toInt(),
                                couponCode = doc.getString("couponCode").orEmpty(),
                                rankDiscount = (doc.getLong("rankDiscount") ?: 0L).toInt(),
                                estimatedDeliveryMinutes = (doc.getLong("estimatedDeliveryMinutes") ?: 0L).toInt(),
                                paymentNote = doc.getString("paymentNote").orEmpty().trim()
                            )
                        }
                    onUpdate(list)
                }
        } catch (e: Exception) {
            null
        }
    }

    /** The customer approves their agent's order; the payment clock starts now. */
    suspend fun confirmAgentOrder(orderId: String): Boolean = try {
        val now = System.currentTimeMillis()
        FirebaseFirestore.getInstance().collection("orders").document(orderId)
            .update(mapOf("status" to AGENT_AWAITING_PAYMENT, "confirmedAt" to now, "payBy" to now + AGENT_PAYMENT_WINDOW_MS))
            .await()
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Records the bKash/Nagad TrxID and the last 4 digits of the number the
     * customer paid from (the admin matches both against the received-money
     * SMS). The TrxID is claimed in payment_txn_ids in the same batch, so the
     * rules refuse one that already paid for another order.
     */
    suspend fun submitAgentPayment(orderId: String, method: String, rawTxnId: String, senderLast4: String): AgentPaymentResult {
        val txn = rawTxnId.uppercase().filter { it.isLetterOrDigit() }
        val last4 = senderLast4.trim()
        if (method !in listOf("BKASH", "NAGAD") || !Regex("^[A-Z0-9]{8,12}$").matches(txn) ||
            !Regex("^[0-9]{4}$").matches(last4)
        ) return AgentPaymentResult.INVALID
        val uid = FirebaseAuthRepository.currentUser?.uid ?: return AgentPaymentResult.ALREADY_USED_OR_FAILED
        return try {
            val db = FirebaseFirestore.getInstance()
            val now = System.currentTimeMillis()
            db.batch()
                .set(
                    db.collection("payment_txn_ids").document("${method}_$txn"),
                    mapOf("orderId" to orderId, "customerId" to uid, "method" to method, "senderLast4" to last4, "createdAt" to now)
                )
                .update(
                    db.collection("orders").document(orderId),
                    mapOf(
                        "status" to AGENT_PAYMENT_SUBMITTED,
                        "paymentMethod" to method,
                        "paymentTransactionId" to txn,
                        "paymentSenderLast4" to last4,
                        "paymentSubmittedAt" to now,
                        "paymentSubmittedVia" to "app"
                    )
                )
                .commit()
                .await()
            AgentPaymentResult.OK
        } catch (e: Exception) {
            AgentPaymentResult.ALREADY_USED_OR_FAILED
        }
    }

    /** Cancels an agent order that isn't paid yet; its plates go back to the menu. */
    suspend fun cancelAgentOrder(orderId: String): Boolean = try {
        val ref = FirebaseFirestore.getInstance().collection("orders").document(orderId)
        cancelInTransaction(ref, "customer") {
            it.getString("status") == AGENT_AWAITING_CONFIRMATION || it.getString("status") == AGENT_AWAITING_PAYMENT
        }.await() != null
    } catch (e: Exception) {
        false
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
                val (title, body) = customerStatusMessage(status)
                FirestoreNotificationRepository.addNotification(
                    uid = customerId,
                    title = title,
                    body = body
                )
            }
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    /** What the customer is told (in-app list + phone notification) when the chef moves their order on. */
    private fun customerStatusMessage(status: String): Pair<String, String> = when (status) {
        "Accepted" -> "Order accepted" to "The chef accepted your order and will start cooking soon."
        "Cooking" -> "Your food is cooking" to "The chef has started preparing your order."
        "OutForDelivery" -> "Order on the way" to "Your order has left the kitchen and is on its way to you."
        "Delivered" -> "Order delivered" to "Your order was delivered. Enjoy your meal!"
        "Cancelled" -> "Order cancelled" to "The chef couldn't take your order, so it was cancelled."
        else -> "Order update" to "Your order is now: $status"
    }
}
