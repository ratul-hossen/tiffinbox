package com.ratulhossen.tiffinbox.data.model

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
    val isAvailableToday: Boolean
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
    val portionSize: String
)

data class CartItemUi(
    val meal: MealUi,
    val quantity: Int
)

enum class OrderStage(val label: String) {
    PLACED("Placed"),
    ACCEPTED("Accepted"),
    COOKING("Cooking"),
    OUT_FOR_DELIVERY("Out for Delivery"),
    DELIVERED("Delivered")
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
