package com.ratulhossen.tiffinbox.data.firebase

import androidx.annotation.StringRes
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ratulhossen.tiffinbox.R
import kotlinx.coroutines.tasks.await

/**
 * coupons/{CODE} - the doc id is the uppercase code, so codes are unique
 * platform-wide. chefId "" = valid at every kitchen (only admin-web can
 * create those); otherwise only for that chef's orders. createdBy is
 * "admin" (admin-web) or "chef" (a chef's own, from the app). Admins can
 * cancel any coupon (isActive = false, cancelledByAdmin = true).
 */
data class CouponUi(
    val code: String,
    val discountType: String, // "percent" | "flat"
    val value: Int,
    val minOrder: Int,
    val maxDiscount: Int,
    val chefId: String,
    val createdBy: String,
    val validFrom: Long,
    val validUntil: Long,
    val isActive: Boolean,
    val usedCount: Int,
    val maxUses: Int,
    val cancelledByAdmin: Boolean
) {
    val isExpired: Boolean get() = validUntil in 1 until System.currentTimeMillis()
    val isUsedUp: Boolean get() = maxUses > 0 && usedCount >= maxUses

    /** Taka off a [subtotal] (never more than the subtotal itself). */
    fun discountFor(subtotal: Int): Int {
        val raw = if (discountType == "flat") value else subtotal * value / 100
        val capped = if (discountType == "percent" && maxDiscount > 0) minOf(raw, maxDiscount) else raw
        return capped.coerceIn(0, subtotal)
    }
}

sealed class CouponCheck {
    data class Valid(val coupon: CouponUi, val discount: Int) : CouponCheck()
    data class Invalid(@StringRes val messageRes: Int) : CouponCheck()
}

object FirestoreCouponRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private fun DocumentSnapshot.toCoupon(): CouponUi = CouponUi(
        code = getString("code") ?: id,
        discountType = getString("discountType") ?: "percent",
        value = (getLong("value") ?: 0L).toInt(),
        minOrder = (getLong("minOrder") ?: 0L).toInt(),
        maxDiscount = (getLong("maxDiscount") ?: 0L).toInt(),
        chefId = getString("chefId").orEmpty(),
        createdBy = getString("createdBy") ?: "admin",
        validFrom = getLong("validFrom") ?: 0L,
        validUntil = getLong("validUntil") ?: 0L,
        isActive = getBoolean("isActive") ?: true,
        usedCount = (getLong("usedCount") ?: 0L).toInt(),
        maxUses = (getLong("maxUses") ?: 0L).toInt(),
        cancelledByAdmin = getBoolean("cancelledByAdmin") ?: false
    )

    fun normalize(code: String): String = code.trim().uppercase().replace(Regex("\\s+"), "")

    /** Checkout: is [code] usable for an order from [chefId] with this [subtotal]? */
    suspend fun validate(code: String, chefId: String, subtotal: Int): CouponCheck {
        val normalized = normalize(code)
        if (normalized.isBlank()) return CouponCheck.Invalid(R.string.coupon_error_not_found)
        return try {
            val doc = db.collection("coupons").document(normalized).get().await()
            if (!doc.exists()) return CouponCheck.Invalid(R.string.coupon_error_not_found)
            val coupon = doc.toCoupon()
            val now = System.currentTimeMillis()
            when {
                !coupon.isActive -> CouponCheck.Invalid(R.string.coupon_error_inactive)
                coupon.validFrom > now -> CouponCheck.Invalid(R.string.coupon_error_not_started)
                coupon.isExpired -> CouponCheck.Invalid(R.string.coupon_error_expired)
                coupon.isUsedUp -> CouponCheck.Invalid(R.string.coupon_error_used_up)
                coupon.chefId.isNotBlank() && coupon.chefId != chefId -> CouponCheck.Invalid(R.string.coupon_error_wrong_chef)
                subtotal < coupon.minOrder -> CouponCheck.Invalid(R.string.coupon_error_min_order)
                else -> CouponCheck.Valid(coupon, coupon.discountFor(subtotal))
            }
        } catch (e: Exception) {
            CouponCheck.Invalid(R.string.coupon_error_network)
        }
    }

    /** Called once an order using [code] has been placed. Best-effort. */
    fun recordUse(code: String) {
        try {
            db.collection("coupons").document(normalize(code)).update("usedCount", FieldValue.increment(1))
        } catch (e: Exception) {
            // Offline - not critical.
        }
    }

    /**
     * Number of currently usable offers per chefId (platform-wide coupons,
     * chefId "", are returned under the "" key). Feeds the AI recommendation
     * model's numOffers input, which it was trained with but the app had no
     * real data for until coupons existed.
     */
    suspend fun getActiveOfferCounts(): Map<String, Int> {
        return try {
            val now = System.currentTimeMillis()
            db.collection("coupons").whereEqualTo("isActive", true).get().await()
                .documents.map { it.toCoupon() }
                .filter { it.validFrom <= now && !it.isExpired && !it.isUsedUp }
                .groupingBy { it.chefId }
                .eachCount()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getChefCoupons(chefId: String): List<CouponUi> {
        return try {
            db.collection("coupons").whereEqualTo("chefId", chefId).get().await()
                .documents.map { it.toCoupon() }
                .sortedByDescending { it.validUntil }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns null on success, or an error string resource. */
    suspend fun createChefCoupon(
        chefId: String,
        code: String,
        discountType: String,
        value: Int,
        minOrder: Int,
        maxDiscount: Int,
        validFrom: Long,
        validUntil: Long,
        maxUses: Int
    ): Int? {
        val normalized = normalize(code)
        if (!Regex("^[A-Z0-9_-]{3,20}$").matches(normalized)) return R.string.coupon_error_bad_code
        if (value <= 0 || (discountType == "percent" && value > 100)) return R.string.coupon_error_bad_value
        if (validUntil <= validFrom) return R.string.coupon_error_bad_time
        return try {
            val ref = db.collection("coupons").document(normalized)
            if (ref.get().await().exists()) return R.string.coupon_error_code_taken
            ref.set(
                hashMapOf(
                    "code" to normalized,
                    "discountType" to discountType,
                    "value" to value,
                    "minOrder" to minOrder,
                    "maxDiscount" to if (discountType == "percent") maxDiscount else 0,
                    "chefId" to chefId,
                    "createdBy" to "chef",
                    "validFrom" to validFrom,
                    "validUntil" to validUntil,
                    "isActive" to true,
                    "usedCount" to 0,
                    "maxUses" to maxUses,
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
            null
        } catch (e: Exception) {
            R.string.coupon_error_network
        }
    }

    suspend fun setChefCouponActive(code: String, active: Boolean): Boolean {
        return try {
            db.collection("coupons").document(normalize(code)).update("isActive", active).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
