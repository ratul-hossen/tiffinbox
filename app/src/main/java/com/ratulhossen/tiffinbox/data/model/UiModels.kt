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
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** "self" = the chef delivers their own orders; "tiffinbox" = TiffinBox's own delivery system handles it. */
    val deliveryMode: String = "self",
    /** Compressed kitchen photo, base64 (see ImageCompressor.kt); blank falls back to [emoji]. */
    val photoBase64: String = "",
    /** Each entry: {url, caption, postedAt} - an external link (YouTube/Facebook/Drive), not an uploaded file. */
    val videos: List<ChefVideoUi> = emptyList()
)

data class ChefVideoUi(
    val url: String,
    val caption: String,
    val postedAt: Long
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
    val photoBase64: String = ""
)

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
    val placedOn: String
)

enum class SubscriptionPlan(val weeks: Int) {
    WEEKLY(1),
    MONTHLY(4)
}

@Composable
fun SubscriptionPlan.label(): String = when (this) {
    SubscriptionPlan.WEEKLY -> stringResource(R.string.subscription_plan_weekly)
    SubscriptionPlan.MONTHLY -> stringResource(R.string.subscription_plan_monthly)
}

data class SubscriptionUi(
    val id: String,
    val chefId: String,
    val chefName: String,
    val plan: SubscriptionPlan,
    val mealsPerWeek: Int,
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
    val bkashTransactionId: String = "",
    val bkashScreenshotBase64: String = ""
)

data class EarningsSummary(
    val todayTotal: Int,
    val weekTotal: Int,
    val recentOrderCount: Int,
    val dailyTotals: List<Pair<String, Int>>
)
