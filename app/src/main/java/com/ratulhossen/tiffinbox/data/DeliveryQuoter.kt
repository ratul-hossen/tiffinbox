package com.ratulhossen.tiffinbox.data

import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreAppConfigRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.domain.DeliveryQuote
import com.ratulhossen.tiffinbox.domain.DeliveryQuotes
import com.ratulhossen.tiffinbox.util.haversineKm

/**
 * Delivery fee + time for one kitchen, from the customer's saved location
 * (straight-line haversine, no Maps) and the admin's pricing. The same
 * numbers are shown on Chef Detail, Meal Details, Cart and Checkout.
 */
object DeliveryQuoter {

    /** Kitchen -> customer km, or null when either location is unknown. */
    suspend fun distanceKm(chef: ChefUi): Double? {
        if (chef.latitude == 0.0 && chef.longitude == 0.0) return null
        val me = FirebaseAuthRepository.currentUser?.uid?.let { FirestoreUserRepository.getLocation(it) } ?: return null
        return haversineKm(me.latitude, me.longitude, chef.latitude, chef.longitude)
    }

    suspend fun quote(chef: ChefUi): DeliveryQuote =
        DeliveryQuotes.quote(distanceKm(chef), FirestoreAppConfigRepository.deliveryPricing())

    suspend fun quote(chefId: String): DeliveryQuote? =
        FirestoreCatalogRepository.getChef(chefId)?.let { quote(it) }
}
