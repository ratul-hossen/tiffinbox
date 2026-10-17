package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.ChefVideoUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import kotlinx.coroutines.tasks.await

/**
 * Reads the chefs/meals collections written by the app / admin-web /
 * MenuManagerScreen. Every function returns an empty list instead of
 * throwing on any failure (Firebase not configured, offline, empty
 * collection) so callers can simply fall back to SampleData.
 */
object FirestoreCatalogRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    @Volatile
    private var cachedChefs: List<ChefUi>? = null

    @Volatile
    private var cachedMeals: List<MealUi>? = null

    fun getCachedChefs(): List<ChefUi> = cachedChefs.orEmpty()
    fun getCachedMeals(): List<MealUi> = cachedMeals.orEmpty()

    fun invalidateCache() {
        cachedChefs = null
        cachedMeals = null
    }

    /**
     * Only approved (isVerified) chefs are customer-visible - an admin must
     * explicitly verify a chef (admin-web's Chefs page) before their kitchen
     * shows up anywhere a customer browses, even though the chef's own
     * dashboard and profile already exist right after signup.
     */
    suspend fun getChefs(forceRefresh: Boolean = false): List<ChefUi> {
        val existing = cachedChefs
        if (!forceRefresh && existing != null && existing.isNotEmpty()) {
            return existing
        }
        return try {
            val list = db.collection("chefs").get().await().documents
                .mapNotNull { it.toChefUi() }
                .filter { it.isVerified }
            cachedChefs = list
            list
        } catch (e: Exception) {
            cachedChefs ?: emptyList()
        }
    }

    suspend fun getChef(chefId: String): ChefUi? {
        cachedChefs?.find { it.id == chefId }?.let { return it }
        return try {
            db.collection("chefs").document(chefId).get().await().toChefUi()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllMeals(forceRefresh: Boolean = false): List<MealUi> {
        val existing = cachedMeals
        if (!forceRefresh && existing != null && existing.isNotEmpty()) {
            return existing
        }
        return try {
            // Customer-facing: meals the chef switched off in Menu Manager are hidden.
            val list = db.collection("meals").get().await().documents.mapNotNull { it.toMealUi() }.filter { it.isAvailable }
            cachedMeals = list
            list
        } catch (e: Exception) {
            cachedMeals ?: emptyList()
        }
    }

    suspend fun getMeal(mealId: String): MealUi? {
        return try {
            db.collection("meals").document(mealId).get().await().toMealUi()
        } catch (e: Exception) {
            null
        }
    }

    /** [includeUnavailable] is for the chef's own Menu Manager; customers only see available meals. */
    suspend fun getMealsForChef(chefId: String, includeUnavailable: Boolean = false): List<MealUi> {
        return try {
            db.collection("meals")
                .whereEqualTo("chefId", chefId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toMealUi() }
                .filter { includeUnavailable || it.isAvailable }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun DocumentSnapshot.toChefUi(): ChefUi? {
        val kitchenName = getString("kitchenName") ?: return null
        @Suppress("UNCHECKED_CAST")
        val specialty = (get("specialty") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val videos = (get("videos") as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let {
                val url = it["url"] as? String ?: return@let null
                ChefVideoUi(
                    url = url,
                    caption = it["caption"] as? String ?: "",
                    postedAt = (it["postedAt"] as? Number)?.toLong() ?: 0L
                )
            }
        } ?: emptyList()
        return ChefUi(
            id = id,
            kitchenName = kitchenName,
            ownerName = getString("ownerName") ?: "",
            emoji = getString("emoji") ?: "🍱",
            specialty = specialty,
            bio = getString("bio") ?: "",
            distanceKm = getDouble("distanceKm") ?: 1.0,
            overallRating = getDouble("overallRating") ?: 0.0,
            taste = getDouble("taste") ?: 0.0,
            hygiene = getDouble("hygiene") ?: 0.0,
            packaging = getDouble("packaging") ?: 0.0,
            onTime = getDouble("onTime") ?: 0.0,
            totalOrders = (getLong("totalOrders") ?: 0L).toInt(),
            isVerified = getBoolean("isVerified") ?: false,
            isAvailableToday = getBoolean("isAvailableToday") ?: true,
            bkashNumber = getString("bkashNumber") ?: "",
            nagadNumber = getString("nagadNumber") ?: "",
            latitude = getDouble("latitude") ?: 0.0,
            longitude = getDouble("longitude") ?: 0.0,
            // An ended agreement counts as self-delivery right away (badge
            // gone) even before any client has written the switch back.
            deliveryMode = (getString("deliveryMode") ?: "self").let { mode ->
                val until = getLong("deliveryUntil") ?: 0L
                if (mode == "tiffinbox" && until < System.currentTimeMillis()) "self" else mode
            },
            deliveryUntil = getLong("deliveryUntil") ?: 0L,
            photoBase64 = getString("photoBase64") ?: "",
            videos = videos,
            kitchenType = getString("kitchenType") ?: "",
            yearsOfExperience = (getLong("yearsOfExperience") ?: 0L).toInt(),
            isDemo = getBoolean("isDemo") ?: false,
            approvalStatus = getString("approvalStatus") ?: "",
            story = getString("story") ?: "",
            nidVerified = getBoolean("nidVerified") ?: false,
            kitchenVerified = getBoolean("kitchenVerified") ?: false,
            // -1 = the chef never set their own rate, so the app's rate applies.
            selfDeliveryBaseFee = getLong("selfDeliveryBaseFee")?.toInt() ?: -1,
            selfDeliveryPerKmFee = getLong("selfDeliveryPerKmFee")?.toInt() ?: -1,
            selfDeliveryIncludedKm = getDouble("selfDeliveryIncludedKm") ?: -1.0,
            maxDeliveryFee = getLong("maxDeliveryFee")?.toInt() ?: 0,
            mealPlanEnabled = getBoolean("mealPlanEnabled") ?: true,
            mealPlanDescription = getString("mealPlanDescription") ?: "",
            mealPlanPricePerMealMin = getLong("mealPlanPricePerMealMin")?.toInt() ?: 0,
            mealPlanPricePerMealMax = getLong("mealPlanPricePerMealMax")?.toInt() ?: 0,
            mealPlanPackages = (get("mealPlanPackages") as? List<*>)?.filterIsInstance<String>()
                ?.takeIf { it.isNotEmpty() } ?: listOf("WEEKLY", "DAYS_10", "DAYS_15", "MONTHLY"),
            mealPlanMealTypes = (get("mealPlanMealTypes") as? List<*>)?.filterIsInstance<String>()
                ?.takeIf { it.isNotEmpty() } ?: listOf(com.ratulhossen.tiffinbox.data.model.MealType.BREAKFAST, com.ratulhossen.tiffinbox.data.model.MealType.LUNCH, com.ratulhossen.tiffinbox.data.model.MealType.DINNER)
        )
    }

    private fun DocumentSnapshot.toMealUi(): MealUi? {
        val name = getString("name") ?: return null
        val chefId = getString("chefId") ?: return null
        val allergens = (get("allergens") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val dietaryTags = (get("dietaryTags") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return MealUi(
            id = id,
            chefId = chefId,
            name = name,
            emoji = getString("emoji") ?: "🍽️",
            description = getString("description") ?: "",
            price = (getLong("price") ?: 0L).toInt(),
            category = getString("category") ?: "Lunch",
            isVeg = getBoolean("isVeg") ?: true,
            allergens = allergens,
            dietaryTags = dietaryTags,
            availableQuantity = (getLong("availableQuantity") ?: 0L).toInt(),
            portionSize = getString("portionSize") ?: "1 plate",
            photoBase64 = getString("photoBase64") ?: "",
            isAvailable = getBoolean("isAvailable") ?: true,
            specialDate = getString("specialDate") ?: ""
        )
    }
}
