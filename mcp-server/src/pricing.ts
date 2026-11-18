/**
 * Delivery fee + ETA - a line-for-line port of the Android app's
 * domain/DeliveryQuote.kt, data/DeliveryQuoter.kt, Rewards.estimatedDeliveryMinutes
 * and util/GeoUtils.haversineKm. test/pricing.test.ts runs the same cases as the
 * app's DeliveryQuotesTest / DeliveryQuoterTest, so an agent is quoted exactly
 * what the app would show. Change both sides together.
 */

export interface DeliveryPricing {
  baseFee: number;
  perKmFee: number;
  includedKm: number;
  /** Admin's ceiling on this kitchen's own fee; 0 = no cap. Self delivery only. */
  maxFee: number;
}

export const DEFAULT_PRICING: DeliveryPricing = { baseFee: 20, perKmFee: 5, includedKm: 2.0, maxFee: 0 };

export interface DeliverySurcharge {
  id: string;
  areaName: string;
  latitude: number;
  longitude: number;
  radiusKm: number;
  type: "flat" | "multiply";
  amount: number;
  validFrom: number;
  validUntil: number;
  isActive: boolean;
}

export interface DeliveryQuote {
  fee: number;
  minutes: number;
  surcharge: number;
  surchargeArea: string;
}

/** Kitchen fields the pricing needs (see FirestoreCatalogRepository.toChefUi). */
export interface PricedKitchen {
  deliveryMode: string;
  selfDeliveryBaseFee: number;
  selfDeliveryPerKmFee: number;
  selfDeliveryIncludedKm: number;
  maxDeliveryFee: number;
}

export interface DeliveryContext {
  appPricing: DeliveryPricing;
  globalMaxFee: number;
  surcharge: DeliverySurcharge | null;
}

export const MAX_FEE = 1000;
const PREP_MINUTES = 20;
const MINUTES_PER_KM = 4.0;

/** Kotlin's roundToInt(): half rounds up. */
const roundToInt = (x: number) => Math.floor(x + 0.5);
const clamp = (x: number, lo: number, hi: number) => Math.min(Math.max(x, lo), hi);

export function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function estimatedDeliveryMinutes(distanceKm: number | null): number {
  const travel = Math.ceil(clamp(distanceKm ?? 3.0, 0.5, 15.0) * MINUTES_PER_KM);
  const total = PREP_MINUTES + travel + 5;
  return Math.floor((total + 4) / 5) * 5;
}

export function surchargeIsLiveAt(s: DeliverySurcharge, now: number): boolean {
  return s.isActive && (s.validFrom <= 0 || now >= s.validFrom) && (s.validUntil <= 0 || now <= s.validUntil);
}

export function surchargeCovers(s: DeliverySurcharge, latitude: number, longitude: number): boolean {
  if (s.radiusKm <= 0) return false;
  if (s.latitude === 0 && s.longitude === 0) return false;
  return haversineKm(latitude, longitude, s.latitude, s.longitude) <= s.radiusKm;
}

export function surchargeExtraOn(s: DeliverySurcharge, fee: number): number {
  const extra = s.type === "multiply" ? roundToInt(fee * (s.amount - 1.0)) : roundToInt(s.amount);
  return Math.max(extra, 0);
}

/** The smallest live surcharge covering this point (zones may overlap), like the app. */
export function surchargeAt(all: DeliverySurcharge[], latitude: number, longitude: number, now: number) {
  const covering = all.filter((s) => surchargeIsLiveAt(s, now) && surchargeCovers(s, latitude, longitude));
  if (covering.length === 0) return null;
  return covering.reduce((best, s) => (surchargeExtraOn(s, 100) < surchargeExtraOn(best, 100) ? s : best));
}

export function quoteFee(
  distanceKm: number | null,
  pricing: DeliveryPricing,
  surcharge: DeliverySurcharge | null = null,
): DeliveryQuote {
  const extraKm = Math.max((distanceKm ?? 0) - pricing.includedKm, 0);
  const raw = pricing.baseFee + Math.ceil(extraKm) * pricing.perKmFee;
  const capped = Math.min(pricing.maxFee > 0 ? Math.min(raw, pricing.maxFee) : raw, MAX_FEE);
  const extra = surcharge ? surchargeExtraOn(surcharge, capped) : 0;
  return {
    fee: Math.max(capped + extra, 0),
    minutes: estimatedDeliveryMinutes(distanceKm),
    surcharge: extra,
    surchargeArea: extra > 0 ? surcharge?.areaName ?? "" : "",
  };
}

export function pricingFor(kitchen: PricedKitchen, app: DeliveryPricing, globalMaxFee: number): DeliveryPricing {
  if (kitchen.deliveryMode === "tiffinbox") return { ...app, maxFee: 0 };
  return {
    baseFee: kitchen.selfDeliveryBaseFee >= 0 ? kitchen.selfDeliveryBaseFee : app.baseFee,
    perKmFee: kitchen.selfDeliveryPerKmFee >= 0 ? kitchen.selfDeliveryPerKmFee : app.perKmFee,
    includedKm: kitchen.selfDeliveryIncludedKm >= 0 ? kitchen.selfDeliveryIncludedKm : app.includedKm,
    maxFee: kitchen.maxDeliveryFee > 0 ? kitchen.maxDeliveryFee : globalMaxFee,
  };
}

export function quoteKitchen(kitchen: PricedKitchen, distanceKm: number | null, ctx: DeliveryContext): DeliveryQuote {
  return quoteFee(
    distanceKm,
    pricingFor(kitchen, ctx.appPricing, ctx.globalMaxFee),
    kitchen.deliveryMode === "tiffinbox" ? ctx.surcharge : null,
  );
}
