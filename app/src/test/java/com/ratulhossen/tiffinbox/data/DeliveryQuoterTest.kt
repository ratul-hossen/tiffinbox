package com.ratulhossen.tiffinbox.data

import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.domain.DeliveryPricing
import com.ratulhossen.tiffinbox.domain.DeliverySurcharge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fee on a chef card has to be the fee at checkout. Home, the chef list
 * and Favorites quote from a pre-loaded [DeliveryContext] rather than awaiting
 * a read per card, so these pin that path to the same answers the suspending
 * one gives: the kitchen's own rates, the admin's cap, and a surcharge only
 * where TiffinBox is the one delivering.
 */
class DeliveryQuoterTest {

    private val app = DeliveryPricing(baseFee = 20, perKmFee = 5, includedKm = 2.0)

    private fun chef(
        mode: String = "self",
        base: Int = -1,
        perKm: Int = -1,
        included: Double = -1.0,
        cap: Int = 0
    ) = ChefUi(
        id = "c1", kitchenName = "K", ownerName = "O", emoji = "🍲",
        specialty = emptyList(), bio = "", distanceKm = 0.0, overallRating = 0.0,
        taste = 0.0, hygiene = 0.0, packaging = 0.0, onTime = 0.0, totalOrders = 0,
        isVerified = true, isAvailableToday = true,
        deliveryMode = mode,
        selfDeliveryBaseFee = base, selfDeliveryPerKmFee = perKm,
        selfDeliveryIncludedKm = included, maxDeliveryFee = cap
    )

    private fun context(globalMaxFee: Int = 0, surcharge: DeliverySurcharge? = null) =
        DeliveryContext(appPricing = app, globalMaxFee = globalMaxFee, surcharge = surcharge)

    @Test
    fun `a chef who set nothing charges what the app would`() {
        assertEquals(35, DeliveryQuoter.quote(chef(), 5.0, context()).fee)
    }

    @Test
    fun `a chef delivering free shows free, not the app's rate`() {
        val free = chef(base = 0, perKm = 0, included = 0.0)
        assertEquals(0, DeliveryQuoter.quote(free, 5.0, context()).fee)
    }

    @Test
    fun `a chef's own rates are used instead of the app's`() {
        val own = chef(base = 40, perKm = 10, included = 1.0)
        assertEquals(80, DeliveryQuoter.quote(own, 5.0, context()).fee)
    }

    @Test
    fun `the per-kitchen cap beats the global one`() {
        val own = chef(base = 200, perKm = 50, included = 0.0, cap = 60)
        assertEquals(60, DeliveryQuoter.quote(own, 5.0, context(globalMaxFee = 300)).fee)
    }

    @Test
    fun `without a per-kitchen cap the global one applies`() {
        val own = chef(base = 200, perKm = 50, included = 0.0)
        assertEquals(80, DeliveryQuoter.quote(own, 5.0, context(globalMaxFee = 80)).fee)
    }

    private val surge = DeliverySurcharge(
        areaName = "Dhanmondi", latitude = 23.8, longitude = 90.4,
        radiusKm = 3.0, amount = 15.0
    )

    @Test
    fun `TiffinBox delivery takes the surcharge and ignores the kitchen cap`() {
        val quote = DeliveryQuoter.quote(chef(mode = "tiffinbox", cap = 25), 5.0, context(surcharge = surge))
        assertEquals(15, quote.surcharge)
        assertEquals(50, quote.fee)
    }

    @Test
    fun `a self-delivering kitchen is never surcharged`() {
        val quote = DeliveryQuoter.quote(chef(), 5.0, context(surcharge = surge))
        assertEquals(0, quote.surcharge)
        assertEquals(35, quote.fee)
    }

    @Test
    fun `an unknown distance still quotes the base fee`() {
        assertEquals(20, DeliveryQuoter.quote(chef(), null, context()).fee)
    }
}
