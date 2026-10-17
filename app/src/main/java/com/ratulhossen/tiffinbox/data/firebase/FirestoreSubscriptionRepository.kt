package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.MealType
import com.ratulhossen.tiffinbox.data.model.SubscriptionPlan
import com.ratulhossen.tiffinbox.data.model.SubscriptionUi
import kotlin.math.roundToInt
import kotlinx.coroutines.tasks.await

object FirestoreSubscriptionRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun subscribe(
        chefId: String,
        chefName: String,
        plan: SubscriptionPlan,
        mealsPerWeek: Int,
        mealType: String,
        pricePerMeal: Int,
        deliveryAddress: String = "",
        customerPhone: String = "",
        paymentMethod: String = "Cash on Delivery",
        paymentTransactionId: String = "",
        scheduleSummary: String = ""
    ): String? {
        return try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return null
            val profile = FirestoreUserRepository.getProfile(customerId)
            val finalAddress = deliveryAddress.ifBlank { profile?.address.orEmpty() }
            val finalPhone = customerPhone.ifBlank { profile?.phoneNumber.orEmpty() }
            val customerName = profile?.name.orEmpty()

            // mealsPerWeek stays a weekly cadence the customer picks; plan.days/7
            // scales it to however many days the chosen package actually runs.
            val totalMeals = (mealsPerWeek * plan.days / 7.0).roundToInt()
            val totalPrice = pricePerMeal * totalMeals

            val data = hashMapOf(
                "customerId" to customerId,
                "customerName" to customerName,
                "customerPhone" to finalPhone,
                "deliveryAddress" to finalAddress,
                "chefId" to chefId,
                "chefName" to chefName,
                "plan" to plan.name,
                "mealsPerWeek" to mealsPerWeek,
                "mealType" to mealType,
                "pricePerMeal" to pricePerMeal,
                "totalPrice" to totalPrice,
                "paymentMethod" to paymentMethod,
                "paymentTransactionId" to paymentTransactionId,
                "scheduleSummary" to scheduleSummary,
                "startedAt" to System.currentTimeMillis(),
                "isActive" to true
            )
            val subRef = db.collection("subscriptions").add(data).await()

            // Also place an active order so the chef's orders screen immediately
            // sees this customer's plan and can prepare and deliver every day.
            val deliveryPoint = FirestoreUserRepository.getLocation(customerId)
            val chef = FirestoreCatalogRepository.getChef(chefId)
            val orderData = hashMapOf(
                "customerId" to customerId,
                "chefId" to chefId,
                "items" to listOf(
                    mapOf(
                        "mealId" to "sub_${subRef.id}",
                        "mealName" to "${plan.name.lowercase().replaceFirstChar { it.uppercase() }} Meal Plan ($mealType · $mealsPerWeek meals/wk${if (scheduleSummary.isNotBlank()) " · $scheduleSummary" else ""})",
                        "quantity" to 1,
                        "unitPrice" to totalPrice
                    )
                ),
                "subtotal" to totalPrice,
                "deliveryFee" to 0,
                "platformFee" to 0,
                "couponCode" to "",
                "discount" to 0,
                "pointsRedeemed" to 0,
                "rankDiscount" to 0,
                "estimatedDeliveryMinutes" to 45,
                "totalPrice" to totalPrice,
                "deliveryAddress" to finalAddress,
                "customerName" to customerName,
                "customerPhone" to finalPhone,
                "deliveryLatitude" to (deliveryPoint?.latitude ?: 0.0),
                "deliveryLongitude" to (deliveryPoint?.longitude ?: 0.0),
                "paymentMethod" to paymentMethod,
                "paymentTransactionId" to paymentTransactionId,
                "paymentScreenshotBase64" to "",
                "deliveryHandledBy" to (chef?.deliveryMode ?: "self"),
                "deliveryPartnerId" to "",
                "status" to "Placed",
                "placedAt" to System.currentTimeMillis(),
                "mealIds" to listOf("sub_${subRef.id}"),
                "mealQuantities" to mapOf("sub_${subRef.id}" to 1L),
                "platesReserved" to false,
                "platesRestored" to false,
                "isPreOrder" to false,
                "scheduledFor" to 0L,
                "isSubscription" to true,
                "subscriptionId" to subRef.id
            )
            db.collection("orders").add(orderData).await()

            subRef.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMySubscriptions(): List<SubscriptionUi> {
        return try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return emptyList()
            db.collection("subscriptions")
                .whereEqualTo("customerId", customerId)
                .whereEqualTo("isActive", true)
                .get()
                .await()
                .documents
                .mapNotNull { it.toSubscriptionUi() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChefSubscriptions(chefId: String): List<SubscriptionUi> {
        return try {
            db.collection("subscriptions")
                .whereEqualTo("chefId", chefId)
                .whereEqualTo("isActive", true)
                .get()
                .await()
                .documents
                .mapNotNull { it.toSubscriptionUi() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toSubscriptionUi(): SubscriptionUi? {
        val chefId = getString("chefId") ?: return null
        val chefName = getString("chefName") ?: ""
        val planName = getString("plan") ?: return null
        val plan = SubscriptionPlan.entries.find { it.name == planName } ?: return null
        return SubscriptionUi(
            id = id,
            chefId = chefId,
            chefName = chefName,
            customerId = getString("customerId").orEmpty(),
            customerName = getString("customerName").orEmpty(),
            customerPhone = getString("customerPhone").orEmpty(),
            deliveryAddress = getString("deliveryAddress").orEmpty(),
            plan = plan,
            mealsPerWeek = (getLong("mealsPerWeek") ?: 0L).toInt(),
            mealType = getString("mealType") ?: MealType.BOTH,
            pricePerMeal = (getLong("pricePerMeal") ?: 0L).toInt(),
            totalPrice = (getLong("totalPrice") ?: 0L).toInt(),
            startedAt = getLong("startedAt") ?: 0L,
            paymentMethod = getString("paymentMethod") ?: "Cash on Delivery",
            paymentTransactionId = getString("paymentTransactionId").orEmpty(),
            isActive = getBoolean("isActive") ?: true,
            scheduleSummary = getString("scheduleSummary").orEmpty()
        )
    }

    fun cancelSubscription(subscriptionId: String) {
        try {
            db.collection("subscriptions").document(subscriptionId).update("isActive", false)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
