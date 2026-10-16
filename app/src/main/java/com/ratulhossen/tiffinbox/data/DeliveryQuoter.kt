package com.ratulhossen.tiffinbox.data

import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreAppConfigRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.location.GeoPoint
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.domain.DeliveryPricing
import com.ratulhossen.tiffinbox.domain.DeliveryQuote
import com.ratulhossen.tiffinbox.domain.DeliveryQuotes
import com.ratulhossen.tiffinbox.domain.DeliverySurcharge
import com.ratulhossen.tiffinbox.util.haversineKm

/**
 * Delivery fee + time for one kitchen, from the customer's saved location
 * (straight-line haversine, no Maps) and whichever pricing applies. The same
 * numbers are shown on Chef Detail, Meal Details, Cart and Checkout.
 *
 * Which pricing applies depends on the kitchen's delivery mode:
 *  - **"self"** - the chef's own rates (`chefs/{uid}.selfDelivery*`), falling
 *    back to the app's rates when they haven't set any, and capped by the
 *    admin's per-chef `maxDeliveryFee` (or the global one in App Settings).
 *  - **"tiffinbox"** - the app's rates, plus any area surcharge the admin has
 *    running for where this customer is. No cap: it's TiffinBox's own fee.
 *
 * Note this is computed on the client and written onto the order. Without
 * Cloud Functions (free-tier constraint, see CLAUDE.md) the rules can't
 * recompute it, so this is a pricing policy, not a tamper-proof one - the
 * same posture as coupons and points elsewhere in the app.
 */
object DeliveryQuoter {

    /** The customer's saved point, or null when they have no location yet. */
    private suspend fun myLocation(): GeoPoint? =
        FirebaseAuthRepository.currentUser?.uid?.let { FirestoreUserRepository.getLocation(it) }

    /** Kitchen -> customer km, or null when either location is unknown. */
    suspend fun distanceKm(chef: ChefUi): Double? {
        if (chef.latitude == 0.0 && chef.longitude == 0.0) return null
        val me = myLocation() ?: return null
        return haversineKm(me.latitude, me.longitude, chef.latitude, chef.longitude)
    }

    /**
     * The rates this kitchen charges. A self-delivering chef who has set
     * nothing gets the app's own rates, so "default = same as the app's
     * charge" holds without having to copy the numbers onto their doc.
     */
    private fun pricingFor(chef: ChefUi, appPricing: DeliveryPricing, globalMaxFee: Int): DeliveryPricing =
        if (chef.deliveryMode == "tiffinbox") {
            // TiffinBox's own service: the admin's rates, and no per-chef cap.
            appPricing.copy(maxFee = 0)
        } else {
            DeliveryPricing(
                baseFee = chef.selfDeliveryBaseFee.takeIf { it >= 0 } ?: appPricing.baseFee,
                perKmFee = chef.selfDeliveryPerKmFee.takeIf { it >= 0 } ?: appPricing.perKmFee,
                includedKm = chef.selfDeliveryIncludedKm.takeIf { it >= 0 } ?: appPricing.includedKm,
                // Per-chef cap wins; 0 means "use the global one from App Settings".
                maxFee = chef.maxDeliveryFee.takeIf { it > 0 } ?: globalMaxFee
            )
        }

    suspend fun quote(chef: ChefUi): DeliveryQuote {
        val appPricing = FirestoreAppConfigRepository.deliveryPricing()
        val pricing = pricingFor(chef, appPricing, FirestoreAppConfigRepository.globalMaxDeliveryFee())
        return DeliveryQuotes.quote(distanceKm(chef), pricing, surchargeFor(chef))
    }

    /**
     * The area surcharge covering this customer right now, if any.
     *
     * Only for kitchens on TiffinBox delivery (owner's decision): when the
     * chef delivers themselves the fee is their income, so TiffinBox doesn't
     * add a surge on top of it.
     */
    private suspend fun surchargeFor(chef: ChefUi): DeliverySurcharge? {
        if (chef.deliveryMode != "tiffinbox") return null
        val active = FirestoreAppConfigRepository.activeSurcharges()
        if (active.isEmpty()) return null
        val me = myLocation() ?: return null
        // Several zones can overlap; charge the customer the smallest one
        // rather than stacking them or picking the harshest.
        return active.filter { it.covers(me.latitude, me.longitude) }.minByOrNull { it.extraOn(100) }
    }

    suspend fun quote(chefId: String): DeliveryQuote? =
        FirestoreCatalogRepository.getChef(chefId)?.let { quote(it) }
}
