package com.ratulhossen.tiffinbox.data.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ratulhossen.tiffinbox.R

data class ChefUi(
    val id: String,
    val kitchenName: String,
    val ownerName: String,
    val emoji: String,
    val specialty: List<String>,
    val bio: String,
    val distanceKm: Double,
    val overallRating: Double,
    val taste: Double,
    val hygiene: Double,
    val packaging: Double,
    val onTime: Double,
    val totalOrders: Int,
    val isVerified: Boolean,
    val isAvailableToday: Boolean,
    val bkashNumber: String = "",
    val nagadNumber: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** "self" = the chef delivers their own orders; "tiffinbox" = TiffinBox's own delivery system handles it. */
    /** Effective mode: "tiffinbox" only while the delivery agreement ([deliveryUntil]) is still running. */
    val deliveryMode: String = "self",
    /** End of the TiffinBox Delivery agreement (epoch ms), set by admin-web on approval; 0 = none. */
    val deliveryUntil: Long = 0L,
    /** Compressed kitchen photo, base64 (see ImageCompressor.kt); blank falls back to [emoji]. */
    val photoBase64: String = "",
    /** Each entry: {url, caption, postedAt} - an external link (YouTube/Facebook/Drive), not an uploaded file. */
    val videos: List<ChefVideoUi> = emptyList(),
    /** e.g. "Home Kitchen" / "Cloud Kitchen" / "Restaurant"; blank for chefs who signed up before this field existed. */
    val kitchenType: String = "",
    val yearsOfExperience: Int = 0,
    /** True only for chefs created by admin-web's "Seed demo chefs" button; shown as a "Demo" badge so customers aren't misled. */
    val isDemo: Boolean = false,
    /** "pending" / "approved" / "rejected", set by admin-web's Chef Approvals page; blank on older docs. */
    val approvalStatus: String = ""
)

data class ChefVideoUi(
    val url: String,
    val caption: String,
    val postedAt: Long
)

data class NotificationUi(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long
)

data class MealUi(
    val id: String,
    val chefId: String,
    val name: String,
    val emoji: String,
    val description: String,
    val price: Int,
    val category: String,
    val isVeg: Boolean,
    val allergens: List<String>,
    val dietaryTags: List<String>,
    val availableQuantity: Int,
    val portionSize: String,
    /** Compressed food photo, base64 (see ImageCompressor.kt); blank falls back to [emoji]. */
    val photoBase64: String = "",
    /** Chef's on/off switch in Menu Manager; switched-off meals are hidden from customers. */
    val isAvailable: Boolean = true,
    /** "yyyy-MM-dd" the chef marked this as Today's Special (blank = not special). */
    val specialDate: String = ""
) {
    /** True only on the day the chef marked it - it resets by itself the next day. */
    val isTodaySpecial: Boolean get() = specialDate.isNotBlank() && specialDate == todayKey()

    companion object {
        fun todayKey(): String =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
}

data class CartItemUi(
    val meal: MealUi,
    val quantity: Int
)

enum class OrderStage {
    PLACED,
    ACCEPTED,
    COOKING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}

/** Maps the plain status string stored on orders/{orderId} to an [OrderStage], or null if unrecognized. */
fun String.toOrderStage(): OrderStage? = when (this) {
    "Placed" -> OrderStage.PLACED
    "Accepted" -> OrderStage.ACCEPTED
    "Cooking" -> OrderStage.COOKING
    "OutForDelivery" -> OrderStage.OUT_FOR_DELIVERY
    "Delivered" -> OrderStage.DELIVERED
    "Cancelled" -> OrderStage.CANCELLED
    else -> null
}

@Composable
fun OrderStage.label(): String = when (this) {
    OrderStage.PLACED -> stringResource(R.string.order_stage_placed)
    OrderStage.ACCEPTED -> stringResource(R.string.order_stage_accepted)
    OrderStage.COOKING -> stringResource(R.string.order_stage_cooking)
    OrderStage.OUT_FOR_DELIVERY -> stringResource(R.string.order_stage_out_for_delivery)
    OrderStage.DELIVERED -> stringResource(R.string.order_stage_delivered)
    OrderStage.CANCELLED -> stringResource(R.string.order_stage_cancelled)
}

data class PastOrderUi(
    val id: String,
    val chefName: String,
    val chefEmoji: String,
    val itemsSummary: String,
    val total: Int,
    val status: OrderStage,
    val placedOn: String,
    val chefId: String = "",
    /** "customer" / "auto_expired" / "chef" / "admin" when [status] is CANCELLED; blank otherwise. */
    val cancelReason: String = ""
) {
    /** A customer may cancel only while the chef hasn't accepted yet. */
    val canCancel: Boolean get() = status == OrderStage.PLACED
}

/** [days] is the package's total duration, matching the "Package Type: 7/10/15/30 Days" reference screen. */
enum class SubscriptionPlan(val days: Int) {
    WEEKLY(7),
    DAYS_10(10),
    DAYS_15(15),
    MONTHLY(30)
}

@Composable
fun SubscriptionPlan.label(): String = when (this) {
    SubscriptionPlan.WEEKLY -> stringResource(R.string.subscription_plan_weekly)
    SubscriptionPlan.DAYS_10 -> stringResource(R.string.subscription_plan_days_10)
    SubscriptionPlan.DAYS_15 -> stringResource(R.string.subscription_plan_days_15)
    SubscriptionPlan.MONTHLY -> stringResource(R.string.subscription_plan_monthly)
}

/** "Lunch", "Dinner", or "Both" - matches the reference "Meal Type" package field. */
object MealType {
    const val LUNCH = "Lunch"
    const val DINNER = "Dinner"
    const val BOTH = "Both"
}

data class SubscriptionUi(
    val id: String,
    val chefId: String,
    val chefName: String,
    val plan: SubscriptionPlan,
    val mealsPerWeek: Int,
    val mealType: String = MealType.BOTH,
    val pricePerMeal: Int,
    val totalPrice: Int,
    val startedAt: Long
)

data class ChefOrderUi(
    val id: String,
    val customerId: String,
    val itemsSummary: String,
    val totalPrice: Int,
    val status: String,
    val placedAt: Long,
    val paymentMethod: String = "",
    /** A manual mobile-banking proof (bKash or Nagad - same free-tier pattern for both). */
    val paymentTransactionId: String = "",
    val paymentScreenshotBase64: String = ""
)

data class EarningsSummary(
    val todayTotal: Int,
    val weekTotal: Int,
    val recentOrderCount: Int,
    val dailyTotals: List<Pair<String, Int>>
)
