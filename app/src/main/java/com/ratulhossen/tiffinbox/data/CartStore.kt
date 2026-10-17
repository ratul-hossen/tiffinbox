package com.ratulhossen.tiffinbox.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.ratulhossen.tiffinbox.data.model.CartItemUi
import com.ratulhossen.tiffinbox.data.model.MealUi

object CartStore {
    val items = mutableStateListOf<CartItemUi>()

    fun add(meal: MealUi, quantity: Int = 1) {
        val index = items.indexOfFirst { it.meal.id == meal.id }
        if (index >= 0) {
            val existing = items[index]
            items[index] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            items.add(CartItemUi(meal, quantity))
        }
    }

    fun updateQuantity(mealId: String, quantity: Int) {
        val index = items.indexOfFirst { it.meal.id == mealId }
        if (index < 0) return
        if (quantity <= 0) {
            items.removeAt(index)
        } else {
            items[index] = items[index].copy(quantity = quantity)
        }
    }

    fun clear() = items.clear()

    val subtotal: Int
        get() = items.sumOf { it.meal.price * it.quantity }

    /** Used until the distance-based quote (DeliveryQuoter) is known. */
    const val DELIVERY_FEE = 20

    /** Distance-based delivery fee for the kitchen in the cart; Cart/Checkout refresh it. */
    var deliveryFee by mutableIntStateOf(DELIVERY_FEE)

    val total: Int
        get() = if (items.isEmpty()) 0 else subtotal + deliveryFee + AppConfigStore.platformFee
}
