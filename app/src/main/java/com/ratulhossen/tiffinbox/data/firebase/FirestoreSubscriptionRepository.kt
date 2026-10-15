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

    fun subscribe(
        chefId: String,
        chefName: String,
        plan: SubscriptionPlan,
        mealsPerWeek: Int,
        mealType: String,
        pricePerMeal: Int
    ) {
        try {
            val customerId = FirebaseAuthRepository.currentUser?.uid ?: return
            // mealsPerWeek stays a weekly cadence the customer picks; plan.days/7
            // scales it to however many days the chosen package actually runs.
            val totalMeals = (mealsPerWeek * plan.days / 7.0).roundToInt()
            val totalPrice = pricePerMeal * totalMeals

            val data = hashMapOf(
                "customerId" to customerId,
                "chefId" to chefId,
                "chefName" to chefName,
                "plan" to plan.name,
                "mealsPerWeek" to mealsPerWeek,
                "mealType" to mealType,
                "pricePerMeal" to pricePerMeal,
                "totalPrice" to totalPrice,
                "startedAt" to System.currentTimeMillis(),
                "isActive" to true
            )
            db.collection("subscriptions").add(data)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
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
                .mapNotNull { doc ->
                    val chefId = doc.getString("chefId") ?: return@mapNotNull null
                    val chefName = doc.getString("chefName") ?: ""
                    val planName = doc.getString("plan") ?: return@mapNotNull null
                    val plan = SubscriptionPlan.entries.find { it.name == planName } ?: return@mapNotNull null
                    SubscriptionUi(
                        id = doc.id,
                        chefId = chefId,
                        chefName = chefName,
                        plan = plan,
                        mealsPerWeek = (doc.getLong("mealsPerWeek") ?: 0L).toInt(),
                        mealType = doc.getString("mealType") ?: MealType.BOTH,
                        pricePerMeal = (doc.getLong("pricePerMeal") ?: 0L).toInt(),
                        totalPrice = (doc.getLong("totalPrice") ?: 0L).toInt(),
                        startedAt = doc.getLong("startedAt") ?: 0L
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cancelSubscription(subscriptionId: String) {
        try {
            db.collection("subscriptions").document(subscriptionId).update("isActive", false)
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }
}
