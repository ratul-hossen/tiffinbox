// Mirrors app/src/test/.../domain/RewardsTest.kt (the parts an agent order uses).
import { describe, expect, it } from "vitest";
import { MAX_RANK_DISCOUNT, RANKS, pointsFor, rankDiscount, rankFor, rewardsFromOrders } from "../src/rewards";

describe("Rewards (RewardsTest.kt)", () => {
  it("one point per ten taka, rounded down", () => {
    expect(pointsFor(9)).toBe(0);
    expect(pointsFor(19)).toBe(1);
    expect(pointsFor(250)).toBe(25);
  });
  it("a free order earns nothing and never goes negative", () => {
    expect(pointsFor(0)).toBe(0);
    expect(pointsFor(-50)).toBe(0);
  });
  it("rank follows lifetime points", () => {
    expect(rankFor(0).level).toBe(1);
    expect(rankFor(99).level).toBe(1);
    expect(rankFor(100).level).toBe(2);
    expect(rankFor(5000).level).toBe(5);
  });
  it("only the top rank gets free delivery", () => {
    expect(rankFor(1500).freeDelivery).toBe(true);
    expect(RANKS.slice(0, -1).some((r) => r.freeDelivery)).toBe(false);
  });
  it("the rank discount is capped in taka", () => {
    const top = rankFor(1500);
    expect(rankDiscount(top, 10_000)).toBe(MAX_RANK_DISCOUNT);
    expect(rankDiscount(top, 100)).toBe(8);
  });
  it("balance is earned on delivered orders minus points spent on orders that weren't cancelled", () => {
    const s = rewardsFromOrders([
      { status: "Delivered", totalPrice: 2500, pointsRedeemed: 0 }, // +250
      { status: "Delivered", totalPrice: 1500, pointsRedeemed: 100 }, // +150, -100
      { status: "Cancelled", totalPrice: 900, pointsRedeemed: 50 }, // refunded
      { status: "Placed", totalPrice: 300, pointsRedeemed: 50 }, // -50
    ]);
    expect(s.lifetimePoints).toBe(400);
    expect(s.balance).toBe(250);
    expect(s.rank.level).toBe(3);
    expect(s.nextRank?.level).toBe(4);
    expect(s.deliveredOrders).toBe(2);
  });
});
