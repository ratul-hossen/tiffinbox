package com.ratulhossen.tiffinbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delivery fee is money a real customer pays, worked out on the phone
 * (there are no Cloud Functions on the free plan), so the arithmetic is worth
 * pinning down: every started km after the included distance is charged, the
 * admin's cap is applied before any surcharge, and a chef charging nothing
 * really does come out free.
 */
class DeliveryQuotesTest {

    private val pricing = DeliveryPricing(baseFee = 20, perKmFee = 5, includedKm = 2.0)

    @Test
    fun `within the included distance costs only the base fee`() {
        assertEquals(20, DeliveryQuotes.quote(1.5, pricing).fee)
        assertEquals(20, DeliveryQuotes.quote(2.0, pricing).fee)
    }

    @Test
    fun `each started km past the included distance adds the per-km fee`() {
        assertEquals(25, DeliveryQuotes.quote(2.4, pricing).fee)
        assertEquals(25, DeliveryQuotes.quote(3.0, pricing).fee)
        assertEquals(35, DeliveryQuotes.quote(5.0, pricing).fee)
    }

    @Test
    fun `an unknown customer location falls back to the base fee`() {
        assertEquals(20, DeliveryQuotes.quote(null, pricing).fee)
    }

    @Test
    fun `a chef charging nothing delivers free`() {
        val free = DeliveryPricing(baseFee = 0, perKmFee = 0, includedKm = 0.0)
        assertEquals(0, DeliveryQuotes.quote(12.0, free).fee)
    }

    @Test
    fun `the admin cap limits what a self-delivering chef can charge`() {
        val greedy = DeliveryPricing(baseFee = 200, perKmFee = 90, includedKm = 0.0, maxFee = 60)
        assertEquals(60, DeliveryQuotes.quote(9.0, greedy).fee)
    }

    @Test
    fun `a cap above the real fee changes nothing`() {
        assertEquals(35, DeliveryQuotes.quote(5.0, pricing.copy(maxFee = 500)).fee)
    }

    @Test
    fun `a surcharge is added on top of the capped fee`() {
        val capped = pricing.copy(maxFee = 25)
        val surge = DeliverySurcharge(amount = 15.0, radiusKm = 3.0, latitude = 23.8, longitude = 90.4)
        val quote = DeliveryQuotes.quote(5.0, capped, surge)
        // 35 clamped to the 25 cap, then +15 of TiffinBox's own surge.
        assertEquals(40, quote.fee)
        assertEquals(15, quote.surcharge)
    }

    @Test
    fun `a multiplier surcharge charges the extra part only`() {
        val surge = DeliverySurcharge(
            type = DeliverySurcharge.TYPE_MULTIPLY,
            amount = 1.5,
            radiusKm = 3.0,
            latitude = 23.8,
            longitude = 90.4
        )
        val quote = DeliveryQuotes.quote(5.0, pricing, surge)
        assertEquals(18, quote.surcharge) // half of 35, rounded
        assertEquals(53, quote.fee)
    }

    @Test
    fun `a longer trip is quoted as a longer wait`() {
        val near = DeliveryQuotes.quote(1.0, pricing).minutes
        val far = DeliveryQuotes.quote(10.0, pricing).minutes
        assertTrue("expected $far to be longer than $near", far > near)
    }

    @Test
    fun `a surcharge only counts inside its own circle`() {
        val surge = DeliverySurcharge(latitude = 23.8103, longitude = 90.4125, radiusKm = 2.0, amount = 10.0)
        assertTrue(surge.covers(23.8120, 90.4140))
        assertFalse(surge.covers(23.9500, 90.4125))
    }

    @Test
    fun `a surcharge with no radius covers nothing`() {
        val surge = DeliverySurcharge(latitude = 23.8103, longitude = 90.4125, radiusKm = 0.0, amount = 10.0)
        assertFalse(surge.covers(23.8103, 90.4125))
    }

    @Test
    fun `a surcharge runs only while switched on and inside its window`() {
        val window = DeliverySurcharge(validFrom = 1_000L, validUntil = 2_000L, amount = 10.0)
        assertFalse(window.isLiveAt(500L))
        assertTrue(window.isLiveAt(1_500L))
        assertFalse(window.isLiveAt(2_500L))

        assertTrue(DeliverySurcharge(amount = 10.0).isLiveAt(1_500L))
        assertFalse(DeliverySurcharge(amount = 10.0, isActive = false).isLiveAt(1_500L))
    }
}
