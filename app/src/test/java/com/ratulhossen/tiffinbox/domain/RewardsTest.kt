package com.ratulhossen.tiffinbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Points are money off a real order, and the balance is derived from the
 * customer's own orders rather than stored, so the maths here decides what a
 * customer actually pays. The caps matter most: without them a big balance
 * could wipe out an order.
 */
class RewardsTest {

    @Test
    fun `one point per ten taka, rounded down`() {
        assertEquals(0, Rewards.pointsFor(9))
        assertEquals(1, Rewards.pointsFor(19))
        assertEquals(25, Rewards.pointsFor(250))
    }

    @Test
    fun `a free order earns nothing and never goes negative`() {
        assertEquals(0, Rewards.pointsFor(0))
        assertEquals(0, Rewards.pointsFor(-50))
    }

    @Test
    fun `rank follows lifetime points`() {
        assertEquals(1, Rewards.rankFor(0).level)
        assertEquals(1, Rewards.rankFor(99).level)
        assertEquals(2, Rewards.rankFor(100).level)
        assertEquals(5, Rewards.rankFor(5000).level)
    }

    @Test
    fun `only the top rank gets free delivery`() {
        assertTrue(Rewards.rankFor(1500).freeDelivery)
        assertTrue(Rewards.RANKS.dropLast(1).none { it.freeDelivery })
    }

    @Test
    fun `balance is what was earned minus what was spent`() {
        val status = Rewards.status(lifetimePoints = 400, redeemedPoints = 150, deliveredOrders = 8)
        assertEquals(250, status.balance)
        assertEquals(3, status.rank.level)
        assertEquals(4, status.nextRank?.level)
    }

    @Test
    fun `redeeming more than was earned never shows a negative balance`() {
        assertEquals(0, Rewards.status(lifetimePoints = 50, redeemedPoints = 90, deliveredOrders = 1).balance)
    }

    @Test
    fun `the top rank has nothing left to climb to`() {
        val status = Rewards.status(lifetimePoints = 2000, redeemedPoints = 0, deliveredOrders = 30)
        assertNull(status.nextRank)
        assertEquals(1f, status.progressToNext, 0.0001f)
        assertEquals(0, status.pointsToNext)
    }

    @Test
    fun `progress to the next rank is measured from the current rank's floor`() {
        // Rank 2 runs from 100 to 300 points, so 200 is halfway.
        val status = Rewards.status(lifetimePoints = 200, redeemedPoints = 0, deliveredOrders = 5)
        assertEquals(0.5f, status.progressToNext, 0.0001f)
        assertEquals(100, status.pointsToNext)
    }

    @Test
    fun `points can cover at most a fifth of the food subtotal`() {
        // 20% of 1000 is 200 taka, which needs 2000 points.
        assertEquals(2000, Rewards.maxRedeemablePoints(balance = 9999, subtotal = 1000))
        // A smaller balance is the limit instead, rounded to whole taka.
        assertEquals(50, Rewards.maxRedeemablePoints(balance = 55, subtotal = 1000))
    }

    @Test
    fun `ten points is one taka off`() {
        assertEquals(5, Rewards.takaForPoints(50))
        assertEquals(5, Rewards.takaForPoints(59))
    }

    @Test
    fun `the rank discount is capped in taka`() {
        val top = Rewards.rankFor(1500)
        assertEquals(Rewards.MAX_RANK_DISCOUNT, Rewards.rankDiscount(top, subtotal = 10_000))
        assertEquals(8, Rewards.rankDiscount(top, subtotal = 100))
    }

    @Test
    fun `the first rank gets no discount`() {
        assertEquals(0, Rewards.rankDiscount(Rewards.rankFor(0), subtotal = 1000))
    }

    @Test
    fun `the delivery estimate grows with distance and is rounded to five minutes`() {
        val near = Rewards.estimatedDeliveryMinutes(1.0)
        val far = Rewards.estimatedDeliveryMinutes(8.0)
        assertTrue("expected $far to be longer than $near", far > near)
        assertEquals(0, near % 5)
        assertEquals(0, far % 5)
    }

    @Test
    fun `an unknown distance still gives an estimate`() {
        assertTrue(Rewards.estimatedDeliveryMinutes(null) > 0)
    }
}
