/**
 * Coupons - the same rules as the app's FirestoreCouponRepository (validate,
 * discountFor, getUsableCoupons). test/coupons.test.ts mirrors them. Change
 * both sides together.
 *
 * coupons/{CODE}: chefId "" = every kitchen (admin-made), otherwise one
 * kitchen. Coupons aren't per-customer in TiffinBox, so "coupons for this
 * customer" = the ones they can use right now at approved kitchens - exactly
 * what the app itself shows at checkout. Nothing private is exposed.
 */
import { cleanText } from "./catalog";
import type { Firestore } from "./firestore";
import { LIMITS } from "./limits";

export interface Coupon {
  code: string;
  discountType: "percent" | "flat";
  value: number;
  minOrder: number;
  maxDiscount: number;
  chefId: string;
  validFrom: number;
  validUntil: number;
  isActive: boolean;
  usedCount: number;
  maxUses: number;
}

const num = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : d);
const str = (v: unknown) => (typeof v === "string" ? v : "");

export function normalizeCode(code: string): string {
  return code.trim().toUpperCase().replace(/\s+/g, "");
}

export function couponFrom(id: string, d: Record<string, unknown>): Coupon {
  return {
    code: normalizeCode(str(d.code) || id),
    discountType: d.discountType === "flat" ? "flat" : "percent",
    value: num(d.value),
    minOrder: num(d.minOrder),
    maxDiscount: num(d.maxDiscount),
    chefId: str(d.chefId),
    validFrom: num(d.validFrom),
    validUntil: num(d.validUntil),
    isActive: d.isActive !== false,
    usedCount: num(d.usedCount),
    maxUses: num(d.maxUses),
  };
}

/** CouponUi.discountFor(): taka off a subtotal (Kotlin Int maths, never more than the subtotal). */
export function discountFor(c: Coupon, subtotal: number): number {
  const raw = c.discountType === "flat" ? c.value : Math.trunc((subtotal * c.value) / 100);
  const capped = c.discountType === "percent" && c.maxDiscount > 0 ? Math.min(raw, c.maxDiscount) : raw;
  return Math.min(Math.max(capped, 0), subtotal);
}

export const isExpired = (c: Coupon, now: number) => c.validUntil >= 1 && c.validUntil < now;
export const isUsedUp = (c: Coupon) => c.maxUses > 0 && c.usedCount >= c.maxUses;

/** Usable today at all, ignoring kitchen and order size (getUsableCoupons' filter). */
export function isLive(c: Coupon, now: number): boolean {
  return c.isActive && c.validFrom <= now && !isExpired(c, now) && !isUsedUp(c);
}

/** FirestoreCouponRepository.validate(), same checks in the same order. null = valid. */
export function couponProblem(c: Coupon | null, kitchenId: string, subtotal: number, now: number): string | null {
  if (!c) return "There is no coupon with that code.";
  if (!c.isActive) return "That coupon has been switched off.";
  if (c.validFrom > now) return "That coupon hasn't started yet.";
  if (isExpired(c, now)) return "That coupon has expired.";
  if (isUsedUp(c)) return "That coupon has been used up.";
  if (c.chefId && c.chefId !== kitchenId) return "That coupon is for a different kitchen.";
  if (subtotal < c.minOrder) return `That coupon needs a food subtotal of at least ৳${c.minOrder}.`;
  return null;
}

/** "10% off (up to ৳50), min order ৳200" */
export function describe(c: Coupon): string {
  const what =
    c.discountType === "flat" ? `৳${c.value} off` : `${c.value}% off${c.maxDiscount > 0 ? ` (up to ৳${c.maxDiscount})` : ""}`;
  return c.minOrder > 0 ? `${what}, min order ৳${c.minOrder}` : what;
}

let cache: { at: number; coupons: Coupon[] } | null = null;

/** Active coupons (60-s cache, like the rest of the catalog). */
export async function loadActiveCoupons(db: Firestore): Promise<Coupon[]> {
  if (cache && Date.now() - cache.at < LIMITS.catalogCacheMs) return cache.coupons;
  const docs = await db.query("coupons", {
    filters: [{ field: "isActive", op: "EQUAL", value: true }],
    select: ["code", "discountType", "value", "minOrder", "maxDiscount", "chefId", "validFrom", "validUntil", "isActive", "usedCount", "maxUses"],
  });
  cache = { at: Date.now(), coupons: docs.map((d) => couponFrom(d.id, d.data)) };
  return cache.coupons;
}

/** One coupon, read fresh (money is involved). */
export async function loadCoupon(db: Firestore, code: string): Promise<Coupon | null> {
  const id = normalizeCode(code);
  if (!/^[A-Z0-9_-]{1,40}$/.test(id)) return null;
  const doc = await db.get(`coupons/${id}`);
  return doc ? couponFrom(doc.id, doc.data) : null;
}

/** What an agent may see about a coupon (no counters beyond "uses left"). */
export function couponView(c: Coupon, kitchenName: string | null, subtotal?: number) {
  return {
    code: cleanText(c.code, 40),
    offer: describe(c),
    kitchen: c.chefId ? kitchenName ?? "one kitchen" : "every kitchen",
    kitchenId: c.chefId || undefined,
    validUntil: c.validUntil > 0 ? new Date(c.validUntil).toISOString() : "no end date",
    usesLeft: c.maxUses > 0 ? Math.max(c.maxUses - c.usedCount, 0) : undefined,
    discountForThisOrderBdt: subtotal !== undefined && subtotal >= c.minOrder ? discountFor(c, subtotal) : undefined,
  };
}
