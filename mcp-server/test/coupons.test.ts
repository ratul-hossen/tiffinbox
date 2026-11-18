// Mirrors the app's FirestoreCouponRepository.validate() and CouponUi.discountFor().
import { describe, expect, it } from "vitest";
import { Coupon, couponProblem, describe as describeCoupon, discountFor, isLive, normalizeCode } from "../src/coupons";

const now = 1_800_000_000_000;
const coupon = (over: Partial<Coupon> = {}): Coupon => ({
  code: "EID50",
  discountType: "percent",
  value: 10,
  minOrder: 0,
  maxDiscount: 0,
  chefId: "",
  validFrom: 0,
  validUntil: 0,
  isActive: true,
  usedCount: 0,
  maxUses: 0,
  ...over,
});

describe("discount maths (CouponUi.discountFor)", () => {
  it("percent uses whole-taka integer maths", () => {
    expect(discountFor(coupon({ value: 15 }), 333)).toBe(49); // 49.95 -> 49, like Kotlin Int
  });
  it("percent is capped by maxDiscount", () => {
    expect(discountFor(coupon({ value: 50, maxDiscount: 60 }), 400)).toBe(60);
  });
  it("flat ignores maxDiscount", () => {
    expect(discountFor(coupon({ discountType: "flat", value: 80, maxDiscount: 10 }), 400)).toBe(80);
  });
  it("never more than the subtotal, never negative", () => {
    expect(discountFor(coupon({ discountType: "flat", value: 500 }), 120)).toBe(120);
    expect(discountFor(coupon({ discountType: "flat", value: -5 }), 120)).toBe(0);
  });
});

describe("validation (FirestoreCouponRepository.validate)", () => {
  it("accepts a live coupon for every kitchen", () => {
    expect(couponProblem(coupon(), "k1", 300, now)).toBeNull();
  });
  it("refuses, in the app's order: missing, off, not started, expired, used up, other kitchen, too small", () => {
    expect(couponProblem(null, "k1", 300, now)).toMatch(/no coupon/);
    expect(couponProblem(coupon({ isActive: false }), "k1", 300, now)).toMatch(/switched off/);
    expect(couponProblem(coupon({ validFrom: now + 1 }), "k1", 300, now)).toMatch(/hasn't started/);
    expect(couponProblem(coupon({ validUntil: now - 1 }), "k1", 300, now)).toMatch(/expired/);
    expect(couponProblem(coupon({ maxUses: 5, usedCount: 5 }), "k1", 300, now)).toMatch(/used up/);
    expect(couponProblem(coupon({ chefId: "k2" }), "k1", 300, now)).toMatch(/different kitchen/);
    expect(couponProblem(coupon({ minOrder: 400 }), "k1", 300, now)).toMatch(/at least ৳400/);
  });
  it("a kitchen's own coupon works at that kitchen", () => {
    expect(couponProblem(coupon({ chefId: "k1" }), "k1", 300, now)).toBeNull();
  });
  it("live = on, started, not expired, not used up", () => {
    expect(isLive(coupon({ validUntil: now + 1000, maxUses: 3, usedCount: 2 }), now)).toBe(true);
    expect(isLive(coupon({ maxUses: 3, usedCount: 3 }), now)).toBe(false);
  });
});

describe("text", () => {
  it("codes are matched like the app (trim, upper case, no spaces)", () => {
    expect(normalizeCode(" eid 50 ")).toBe("EID50");
  });
  it("describes an offer in plain words", () => {
    expect(describeCoupon(coupon({ value: 10, maxDiscount: 50, minOrder: 200 }))).toBe("10% off (up to ৳50), min order ৳200");
    expect(describeCoupon(coupon({ discountType: "flat", value: 30 }))).toBe("৳30 off");
  });
});
