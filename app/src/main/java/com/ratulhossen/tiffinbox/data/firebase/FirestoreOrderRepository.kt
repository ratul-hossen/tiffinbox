package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.CartItemUi

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
}
