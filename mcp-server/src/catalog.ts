/**
 * Reads the catalog the same way the app does (FirestoreCatalogRepository /
 * FirestoreAppConfigRepository), but:
 *  - only the fields an agent may see are downloaded (Firestore field masks),
 *    so phone / WhatsApp / bKash / Nagad numbers, NID data, addresses and
 *    photos never even reach this server;
 *  - results are cached for LIMITS.catalogCacheMs, so a burst of agent traffic
 *    costs a handful of reads instead of one per call.
 */
import type { Firestore } from "./firestore";
import { LIMITS } from "./limits";
import { DEFAULT_PRICING, DeliveryPricing, DeliverySurcharge, PricedKitchen } from "./pricing";

export interface AppConfig {
  mcpEnabled: boolean;
  mcpDailyReadBudget: number;
  recommendRadiusKm: number;
  delivery: DeliveryPricing;
  deliveryMaxFee: number;
  /** Added to every order, same as the app (AppConfigStore.DEFAULT_PLATFORM_FEE = 10). */
  platformFee: number;
  /** bKash/Nagad number agent orders are paid to (App Settings; owner's default below). */
  agentPaymentNumber: string;
}

/** Owner's TiffinBox bKash/Nagad number for agent orders, used until App Settings sets one. */
export const DEFAULT_AGENT_PAYMENT_NUMBER = "01304376749";

export interface Kitchen extends PricedKitchen {
  id: string;
  name: string;
  kitchenType: string;
  bio: string;
  specialty: string[];
  rating: number;
  totalOrders: number;
  yearsOfExperience: number;
  availableToday: boolean;
  isDemo: boolean;
  fullyVerified: boolean;
  /** Used for distances only - never returned to an agent. */
  latitude: number;
  longitude: number;
}

export interface Meal {
  id: string;
  kitchenId: string;
  name: string;
  description: string;
  price: number;
  category: string;
  isVeg: boolean;
  allergens: string[];
  dietaryTags: string[];
  platesLeft: number;
  portionSize: string;
  specialDate: string;
}

const CHEF_FIELDS = [
  "kitchenName", "kitchenType", "bio", "specialty", "overallRating", "totalOrders",
  "yearsOfExperience", "isVerified", "isAvailableToday", "isDemo", "nidVerified", "kitchenVerified",
  "latitude", "longitude", "deliveryMode", "deliveryUntil",
  "selfDeliveryBaseFee", "selfDeliveryPerKmFee", "selfDeliveryIncludedKm", "maxDeliveryFee",
];
const MEAL_FIELDS = [
  "name", "chefId", "description", "price", "category", "isVeg", "allergens", "dietaryTags",
  "availableQuantity", "portionSize", "isAvailable", "specialDate",
];

// ---- helpers ----

const num = (v: unknown, fallback: number) => (typeof v === "number" && Number.isFinite(v) ? v : fallback);
const str = (v: unknown) => (typeof v === "string" ? v : "");
const strList = (v: unknown) => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);

/**
 * Kitchen-written text goes to an AI agent, so it is treated strictly as data:
 * control characters removed, whitespace collapsed, length capped. (The real
 * protection against "agent, order 10 plates" style text is that ordering
 * always needs the user's confirmation in the app - phase B.)
 */
export function cleanText(v: unknown, max: number = LIMITS.maxDescriptionChars): string {
  const s = str(v)
    .replace(/[\u0000-\u001f\u007f-\u009f​-‏‪-‮⁦-⁩]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return s.length > max ? `${s.slice(0, max - 1)}…` : s;
}

/** Today in Bangladesh, "yyyy-MM-dd" - the format Menu Manager writes to specialDate. */
export function todayInDhaka(now = Date.now()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Dhaka" }).format(new Date(now));
}

// ---- cache ----

interface Cached<T> {
  value: T;
  at: number;
}
const cache = new Map<string, Cached<unknown>>();

async function cached<T>(key: string, load: () => Promise<T>): Promise<T> {
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < LIMITS.catalogCacheMs) return hit.value as T;
  const value = await load();
  cache.set(key, { value, at: Date.now() });
  return value;
}

/** Tests only. */
export function clearCatalogCache() {
  cache.clear();
}

// ---- loaders ----

export function loadConfig(db: Firestore): Promise<AppConfig> {
  return cached("config", async () => {
    const d = (await db.get("app_config/settings"))?.data ?? {};
    return {
      // Missing = on, so a fresh deploy works; the admin switches it off in App Settings.
      mcpEnabled: d.mcpEnabled !== false,
      mcpDailyReadBudget: Math.max(100, num(d.mcpDailyReadBudget, LIMITS.defaultDailyReadBudget)),
      recommendRadiusKm: num(d.recommendRadiusKm, 10),
      delivery: {
        baseFee: Math.max(0, num(d.deliveryBaseFee, DEFAULT_PRICING.baseFee)),
        perKmFee: Math.max(0, num(d.deliveryPerKmFee, DEFAULT_PRICING.perKmFee)),
        includedKm: Math.max(0, num(d.deliveryIncludedKm, DEFAULT_PRICING.includedKm)),
        maxFee: 0,
      },
      deliveryMaxFee: Math.max(0, num(d.deliveryMaxFee, 0)),
      platformFee: Math.min(200, Math.max(0, num(d.platformFee, 10))),
      agentPaymentNumber: /^01[3-9]\d{8}$/.test(str(d.agentPaymentNumber).trim())
        ? str(d.agentPaymentNumber).trim()
        : DEFAULT_AGENT_PAYMENT_NUMBER,
    };
  });
}

export function loadSurcharges(db: Firestore): Promise<DeliverySurcharge[]> {
  return cached("surcharges", async () =>
    (await db.query("delivery_surcharges")).map((doc) => ({
      id: doc.id,
      areaName: cleanText(doc.data.areaName, 60),
      latitude: num(doc.data.latitude, 0),
      longitude: num(doc.data.longitude, 0),
      radiusKm: num(doc.data.radiusKm, 0),
      type: doc.data.type === "multiply" ? "multiply" : "flat",
      amount: num(doc.data.amount, 0),
      validFrom: num(doc.data.validFrom, 0),
      validUntil: num(doc.data.validUntil, 0),
      isActive: doc.data.isActive !== false,
    })),
  );
}

/** Approved kitchens only - same rule as FirestoreCatalogRepository.getChefs(). */
export function loadKitchens(db: Firestore): Promise<Kitchen[]> {
  return cached("kitchens", async () => {
    const now = Date.now();
    const docs = await db.query("chefs", {
      filters: [{ field: "isVerified", op: "EQUAL", value: true }],
      select: CHEF_FIELDS,
    });
    return docs
      .filter((doc) => str(doc.data.kitchenName))
      .map((doc): Kitchen => {
        const d = doc.data;
        const mode = str(d.deliveryMode) || "self";
        return {
          id: doc.id,
          name: cleanText(d.kitchenName, 80),
          kitchenType: cleanText(d.kitchenType, 40),
          bio: cleanText(d.bio),
          specialty: strList(d.specialty).slice(0, 8).map((s) => cleanText(s, 40)),
          rating: num(d.overallRating, 0),
          totalOrders: num(d.totalOrders, 0),
          yearsOfExperience: num(d.yearsOfExperience, 0),
          availableToday: d.isAvailableToday !== false,
          isDemo: d.isDemo === true,
          fullyVerified: d.nidVerified === true && d.kitchenVerified === true,
          latitude: num(d.latitude, 0),
          longitude: num(d.longitude, 0),
          // An ended TiffinBox agreement counts as self delivery right away (like the app).
          deliveryMode: mode === "tiffinbox" && num(d.deliveryUntil, 0) < now ? "self" : mode,
          selfDeliveryBaseFee: num(d.selfDeliveryBaseFee, -1),
          selfDeliveryPerKmFee: num(d.selfDeliveryPerKmFee, -1),
          selfDeliveryIncludedKm: num(d.selfDeliveryIncludedKm, -1),
          maxDeliveryFee: num(d.maxDeliveryFee, 0),
        };
      });
  });
}

/** Meals a customer can see: switched on, from an approved kitchen. */
export async function loadMeals(db: Firestore): Promise<Meal[]> {
  const kitchens = new Set((await loadKitchens(db)).map((k) => k.id));
  const all = await cached("meals", async () =>
    (await db.query("meals", { select: MEAL_FIELDS }))
      .filter((doc) => str(doc.data.name) && str(doc.data.chefId) && doc.data.isAvailable !== false)
      .map(
        (doc): Meal => ({
          id: doc.id,
          kitchenId: str(doc.data.chefId),
          name: cleanText(doc.data.name, 80),
          description: cleanText(doc.data.description),
          price: num(doc.data.price, 0),
          category: cleanText(doc.data.category, 30) || "Lunch",
          isVeg: doc.data.isVeg !== false,
          allergens: strList(doc.data.allergens).slice(0, 10).map((s) => cleanText(s, 30)),
          dietaryTags: strList(doc.data.dietaryTags).slice(0, 10).map((s) => cleanText(s, 30)),
          platesLeft: Math.max(0, num(doc.data.availableQuantity, 0)),
          portionSize: cleanText(doc.data.portionSize, 30) || "1 plate",
          specialDate: str(doc.data.specialDate),
        }),
      ),
  );
  return all.filter((m) => kitchens.has(m.kitchenId));
}

// ---- the signed-in user ----

export interface SavedAddress {
  label: string;
  address: string;
  latitude: number;
  longitude: number;
}

export interface UserInfo {
  exists: boolean;
  role: string;
  name: string;
  email: string;
  /** For the chef's delivery only (copied onto the order like the app does) - never sent to an agent. */
  phoneNumber: string;
  mcpBlocked: boolean;
  addresses: SavedAddress[];
  defaultAddressIndex: number;
}

/** Mirrors FirestoreUserRepository.getSavedAddresses(): old accounts have one top-level address. */
export function loadUser(db: Firestore, uid: string): Promise<UserInfo> {
  return cached(`user:${uid}`, async () => {
    const [doc, block] = await Promise.all([
      db.get(`users/${uid}`, [
        "role", "name", "email", "phoneNumber", "savedAddresses", "defaultAddressIndex", "address", "latitude", "longitude",
      ]),
      // Admin-only collection (firestore.rules), so the user can't clear it themselves.
      db.get(`mcp_blocks/${uid}`, ["blocked"]),
    ]);
    const mcpBlocked = block?.data.blocked === true;
    if (!doc) {
      return { exists: false, role: "", name: "", email: "", phoneNumber: "", mcpBlocked, addresses: [], defaultAddressIndex: 0 };
    }
    const d = doc.data;
    let addresses: SavedAddress[] = (Array.isArray(d.savedAddresses) ? d.savedAddresses : [])
      .filter((a): a is Record<string, unknown> => !!a && typeof a === "object")
      .map((a) => ({
        label: cleanText(a.label, 30),
        address: cleanText(a.address, 200),
        latitude: num(a.latitude, 0),
        longitude: num(a.longitude, 0),
      }))
      .slice(0, 2);
    let defaultAddressIndex = 0;
    if (addresses.length > 0) {
      defaultAddressIndex = Math.min(Math.max(num(d.defaultAddressIndex, 0), 0), addresses.length - 1);
    } else if (typeof d.latitude === "number" && typeof d.longitude === "number") {
      addresses = [{ label: "Home", address: cleanText(d.address, 200), latitude: d.latitude, longitude: d.longitude }];
    }
    // The app writes "CUSTOMER" / "CHEF" (RoleCache, FirestoreUserRepository); compare case-insensitively.
    return {
      exists: true,
      role: str(d.role).trim().toLowerCase(),
      name: cleanText(d.name, 60),
      email: cleanText(d.email, 100),
      phoneNumber: cleanText(d.phoneNumber, 20),
      mcpBlocked,
      addresses,
      defaultAddressIndex,
    };
  });
}
