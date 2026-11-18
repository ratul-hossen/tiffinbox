/**
 * TiffinBox Points and ranks - a port of the app's domain/Rewards.kt (the
 * parts an agent order needs). test/rewards.test.ts mirrors RewardsTest.kt.
 * Change both sides together.
 */

export interface RewardRank {
  level: number;
  emoji: string;
  title: string;
  minPoints: number;
  discountPercent: number;
  freeDelivery: boolean;
}

export const RANKS: RewardRank[] = [
  { level: 1, emoji: "🍙", title: "Rookie Eater", minPoints: 0, discountPercent: 0, freeDelivery: false },
  { level: 2, emoji: "🥷", title: "Food Ninja", minPoints: 100, discountPercent: 2, freeDelivery: false },
  { level: 3, emoji: "🏴‍☠️", title: "Snack Pirate", minPoints: 300, discountPercent: 4, freeDelivery: false },
  { level: 4, emoji: "⚔️", title: "Flavor Samurai", minPoints: 700, discountPercent: 6, freeDelivery: false },
  { level: 5, emoji: "🐉", title: "Legendary Glutton", minPoints: 1500, discountPercent: 8, freeDelivery: true },
];

export const MAX_RANK_DISCOUNT = 60;

/** 1 point per ৳10 of a delivered order (integer division, never negative). */
export function pointsFor(orderTotal: number): number {
  return Math.max(Math.trunc(orderTotal / 10), 0);
}

export function rankFor(lifetimePoints: number): RewardRank {
  return [...RANKS].reverse().find((r) => lifetimePoints >= r.minPoints) ?? RANKS[0]!;
}

/** Kotlin's roundToInt(): half rounds up. */
const roundToInt = (x: number) => Math.floor(x + 0.5);

export function rankDiscount(rank: RewardRank, subtotal: number): number {
  return Math.min(Math.max(roundToInt((subtotal * rank.discountPercent) / 100), 0), MAX_RANK_DISCOUNT);
}

export interface RewardsStatus {
  lifetimePoints: number;
  balance: number;
  deliveredOrders: number;
  rank: RewardRank;
  nextRank: RewardRank | null;
  pointsToNext: number;
}

/** Same as FirestoreOrderRepository.getMyRewards(): earned on Delivered, minus redeemed on non-cancelled. */
export function rewardsFromOrders(
  orders: Array<{ status: string; totalPrice: number; pointsRedeemed: number }>,
): RewardsStatus {
  const delivered = orders.filter((o) => o.status === "Delivered");
  const lifetimePoints = delivered.reduce((s, o) => s + pointsFor(o.totalPrice), 0);
  const redeemed = orders.filter((o) => o.status !== "Cancelled").reduce((s, o) => s + o.pointsRedeemed, 0);
  const rank = rankFor(lifetimePoints);
  const nextRank = RANKS.find((r) => r.minPoints > lifetimePoints) ?? null;
  return {
    lifetimePoints,
    balance: Math.max(lifetimePoints - redeemed, 0),
    deliveredOrders: delivered.length,
    rank,
    nextRank,
    pointsToNext: nextRank ? Math.max(nextRank.minPoints - lifetimePoints, 0) : 0,
  };
}
