// Mirrors app/src/test/.../domain/DeliveryQuotesTest.kt and data/DeliveryQuoterTest.kt.
// If one of these changes, change the other: an agent must be quoted what the app shows.
import { describe, expect, it } from "vitest";
import {
  DeliveryContext,
  DeliveryPricing,
  DeliverySurcharge,
  MAX_FEE,
  PricedKitchen,
  quoteFee,
  quoteKitchen,
  surchargeAt,
  surchargeCovers,
  surchargeIsLiveAt,
} from "../src/pricing";

const pricing: DeliveryPricing = { baseFee: 20, perKmFee: 5, includedKm: 2.0, maxFee: 0 };
const surge = (over: Partial<DeliverySurcharge> = {}): DeliverySurcharge => ({
  id: "s",
  areaName: "",
  latitude: 23.8,
  longitude: 90.4,
  radiusKm: 3.0,
  type: "flat",
  amount: 15,
  validFrom: 0,
  validUntil: 0,
  isActive: true,
  ...over,
});

describe("DeliveryQuotes (DeliveryQuotesTest.kt)", () => {
  it("within the included distance costs only the base fee", () => {
    expect(quoteFee(1.5, pricing).fee).toBe(20);
    expect(quoteFee(2.0, pricing).fee).toBe(20);
  });
  it("each started km past the included distance adds the per-km fee", () => {
    expect(quoteFee(2.4, pricing).fee).toBe(25);
    expect(quoteFee(3.0, pricing).fee).toBe(25);
    expect(quoteFee(5.0, pricing).fee).toBe(35);
  });
  it("an unknown customer location falls back to the base fee", () => {
    expect(quoteFee(null, pricing).fee).toBe(20);
  });
  it("a chef charging nothing delivers free", () => {
    expect(quoteFee(12.0, { baseFee: 0, perKmFee: 0, includedKm: 0, maxFee: 0 }).fee).toBe(0);
  });
  it("the admin cap limits what a self-delivering chef can charge", () => {
    expect(quoteFee(9.0, { baseFee: 200, perKmFee: 90, includedKm: 0, maxFee: 60 }).fee).toBe(60);
  });
  it("a cap above the real fee changes nothing", () => {
    expect(quoteFee(5.0, { ...pricing, maxFee: 500 }).fee).toBe(35);
  });
  it("no configuration can charge more than the hard ceiling", () => {
    expect(quoteFee(50.0, { baseFee: 500, perKmFee: 100, includedKm: 0, maxFee: 0 }).fee).toBe(MAX_FEE);
  });
  it("a surcharge is added on top of the capped fee", () => {
    const q = quoteFee(5.0, { ...pricing, maxFee: 25 }, surge());
    expect(q.fee).toBe(40);
    expect(q.surcharge).toBe(15);
  });
  it("a multiplier surcharge charges the extra part only", () => {
    const q = quoteFee(5.0, pricing, surge({ type: "multiply", amount: 1.5 }));
    expect(q.surcharge).toBe(18);
    expect(q.fee).toBe(53);
  });
  it("a longer trip is quoted as a longer wait", () => {
    expect(quoteFee(10.0, pricing).minutes).toBeGreaterThan(quoteFee(1.0, pricing).minutes);
  });
  it("a surcharge only counts inside its own circle", () => {
    const s = surge({ latitude: 23.8103, longitude: 90.4125, radiusKm: 2.0, amount: 10 });
    expect(surchargeCovers(s, 23.812, 90.414)).toBe(true);
    expect(surchargeCovers(s, 23.95, 90.4125)).toBe(false);
  });
  it("a surcharge with no radius covers nothing", () => {
    expect(surchargeCovers(surge({ latitude: 23.8103, longitude: 90.4125, radiusKm: 0 }), 23.8103, 90.4125)).toBe(
      false,
    );
  });
  it("a surcharge runs only while switched on and inside its window", () => {
    const w = surge({ validFrom: 1000, validUntil: 2000 });
    expect(surchargeIsLiveAt(w, 500)).toBe(false);
    expect(surchargeIsLiveAt(w, 1500)).toBe(true);
    expect(surchargeIsLiveAt(w, 2500)).toBe(false);
    expect(surchargeIsLiveAt(surge(), 1500)).toBe(true);
    expect(surchargeIsLiveAt(surge({ isActive: false }), 1500)).toBe(false);
  });
  it("overlapping zones charge the smallest one", () => {
    const picked = surchargeAt([surge({ id: "big", amount: 40 }), surge({ id: "small", amount: 10 })], 23.8, 90.4, 1);
    expect(picked?.id).toBe("small");
  });
});

describe("DeliveryQuoter (DeliveryQuoterTest.kt)", () => {
  const chef = (over: Partial<PricedKitchen> = {}): PricedKitchen => ({
    deliveryMode: "self",
    selfDeliveryBaseFee: -1,
    selfDeliveryPerKmFee: -1,
    selfDeliveryIncludedKm: -1,
    maxDeliveryFee: 0,
    ...over,
  });
  const ctx = (globalMaxFee = 0, surcharge: DeliverySurcharge | null = null): DeliveryContext => ({
    appPricing: pricing,
    globalMaxFee,
    surcharge,
  });
  const dhanmondi = surge({ areaName: "Dhanmondi" });

  it("a chef who set nothing charges what the app would", () => {
    expect(quoteKitchen(chef(), 5.0, ctx()).fee).toBe(35);
  });
  it("a chef delivering free shows free, not the app's rate", () => {
    const free = chef({ selfDeliveryBaseFee: 0, selfDeliveryPerKmFee: 0, selfDeliveryIncludedKm: 0 });
    expect(quoteKitchen(free, 5.0, ctx()).fee).toBe(0);
  });
  it("a chef's own rates are used instead of the app's", () => {
    const own = chef({ selfDeliveryBaseFee: 40, selfDeliveryPerKmFee: 10, selfDeliveryIncludedKm: 1 });
    expect(quoteKitchen(own, 5.0, ctx()).fee).toBe(80);
  });
  it("the per-kitchen cap beats the global one", () => {
    const own = chef({ selfDeliveryBaseFee: 200, selfDeliveryPerKmFee: 50, selfDeliveryIncludedKm: 0, maxDeliveryFee: 60 });
    expect(quoteKitchen(own, 5.0, ctx(300)).fee).toBe(60);
  });
  it("without a per-kitchen cap the global one applies", () => {
    const own = chef({ selfDeliveryBaseFee: 200, selfDeliveryPerKmFee: 50, selfDeliveryIncludedKm: 0 });
    expect(quoteKitchen(own, 5.0, ctx(80)).fee).toBe(80);
  });
  it("TiffinBox delivery takes the surcharge and ignores the kitchen cap", () => {
    const q = quoteKitchen(chef({ deliveryMode: "tiffinbox", maxDeliveryFee: 25 }), 5.0, ctx(0, dhanmondi));
    expect(q.surcharge).toBe(15);
    expect(q.fee).toBe(50);
  });
  it("a self-delivering kitchen is never surcharged", () => {
    const q = quoteKitchen(chef(), 5.0, ctx(0, dhanmondi));
    expect(q.surcharge).toBe(0);
    expect(q.fee).toBe(35);
  });
  it("an unknown distance still quotes the base fee", () => {
    expect(quoteKitchen(chef(), null, ctx()).fee).toBe(20);
  });
});
