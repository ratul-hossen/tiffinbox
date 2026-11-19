/**
 * Orders through an agent. docs/AGENT_PAYMENTS.md is the contract (states,
 * field names, windows); the owner's rules:
 *  - the customer must Confirm every agent order in the app;
 *  - no cash on delivery: the customer pays in advance by bKash/Nagad
 *    Send Money to TiffinBox's number and gives the TrxID, the method and
 *    the last 4 digits of the number they paid from (agent or app);
 *  - an admin checks the payment by hand (admin-web) before the kitchen
 *    ever sees the order; the chef is paid by TiffinBox later, outside the app.
 *
 * Status flow (only "Placed" onwards is visible to the chef):
 *   AwaitingConfirmation --app Confirm (15 min)--> AwaitingPayment
 *     --TrxID + method + sender last 4 (payBy + 30 min grace)--> PaymentSubmitted
 *     --admin Approve--> Placed -> Accepted -> ... (the normal app flow)
 *     --admin Ask again--> AwaitingPayment (paymentNote shown)
 *     --admin Reject / timeout / customer cancel--> Cancelled (plates given back)
 *   Placed (paid) not accepted within 12 h --> Cancelled + refundStatus "due"
 *
 * Prices are always computed here from fresh Firestore data, never taken
 * from the agent. The same formulas as the app (pricing.ts, rewards.ts,
 * coupons.ts). A coupon is checked when quoting and again when placing; its
 * use is counted when an admin approves the payment (the order is real only
 * then). Spending points stays in the app for now.
 */
import { DeliveryContext, haversineKm, quoteKitchen, surchargeAt } from "./pricing";
import { AppConfig, Kitchen, SavedAddress, UserInfo, cleanText, loadKitchens, loadSurcharges } from "./catalog";
import { Coupon, couponProblem, discountFor, isLive, loadActiveCoupons, loadCoupon, describe as describeCoupon } from "./coupons";
import { ConflictError, Filter, Firestore, FsDoc, Write } from "./firestore";
import { LIMITS, TrustLevel } from "./limits";
import { rankDiscount, rewardsFromOrders } from "./rewards";

export const AWAITING_CONFIRMATION = "AwaitingConfirmation";
export const AWAITING_PAYMENT = "AwaitingPayment";
export const PAYMENT_SUBMITTED = "PaymentSubmitted";
/** Agent-order states before an admin has verified the payment (the chef never sees these). */
export const AGENT_PENDING = [AWAITING_CONFIRMATION, AWAITING_PAYMENT, PAYMENT_SUBMITTED];
/** A "real" order with a kitchen: verified and not cancelled. */
export const REAL_ORDER_STATUSES = ["Placed", "Accepted", "Cooking", "OutForDelivery", "Delivered"];
/** TrxIDs are accepted until payBy + this (PAYMENT_GRACE). */
export const PAYMENT_GRACE_MS = LIMITS.paymentGraceMs;

const ORDER_FIELDS = [
  "customerId", "chefId", "items", "subtotal", "deliveryFee", "platformFee", "discount", "pointsRedeemed",
  "rankDiscount", "totalPrice", "status", "placedAt", "cancelReason", "rejectReason", "paymentMethod",
  "paymentTransactionId", "paymentSenderLast4", "paymentVerifiedAt", "paymentNote", "source", "confirmBy", "payBy",
  "mealQuantities", "platesReserved", "platesRestored", "estimatedDeliveryMinutes", "deliveryAddress",
  "agentClientName", "refundStatus", "refundAmount", "refundReason",
];

export class OrderError extends Error {}

const num = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : d);
const str = (v: unknown) => (typeof v === "string" ? v : "");
const shortId = (id: string) => `#${id.slice(-6).toUpperCase()}`;

export function randomId(length = 20): string {
  const abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  return [...crypto.getRandomValues(new Uint8Array(length))].map((b) => abc[b % abc.length]).join("");
}

export function notification(uid: string, title: string, body: string): Write {
  return { op: "create", path: `notifications/${uid}/items/${randomId()}`, data: { title, body, createdAt: Date.now() } };
}

// ---------------------------------------------------------------- reading

/**
 * A customer's order list, reused for LIMITS.ordersCacheMs per isolate. This
 * server drops a customer's entry whenever it writes one of their orders, so
 * its own changes show at once; changes made by the app show within 30 s.
 * Single-order lookups (get_order_status, payment, cancel) always read fresh.
 */
const ordersCache = new Map<string, { at: number; docs: FsDoc[] }>();
const cloneDoc = (d: FsDoc): FsDoc => ({ ...d, data: structuredClone(d.data) });

export function forgetMyOrders(uid: string): void {
  ordersCache.delete(uid);
}

/** Tests only. */
export function clearOrdersCache(): void {
  ordersCache.clear();
}

/** The customer's orders (newest first), with agent orders past their deadline cancelled on the spot. */
export async function loadMyOrders(db: Firestore, uid: string, opts: { fresh?: boolean } = {}): Promise<FsDoc[]> {
  const hit = ordersCache.get(uid);
  if (!opts.fresh && hit && Date.now() - hit.at < LIMITS.ordersCacheMs) return hit.docs.map(cloneDoc);
  const docs = await db.query("orders", {
    filters: [{ field: "customerId", op: "EQUAL", value: uid }],
    select: ORDER_FIELDS,
    limit: 100,
  });
  for (const doc of docs) {
    // No Cloud Functions: whoever reads a late order first cancels it (the app and the cron sweep do the same).
    await expireIfLate(db, doc).catch((e) => console.error("lazy expiry failed", doc.id, e));
  }
  const sorted = docs.sort((a, b) => num(b.data.placedAt) - num(a.data.placedAt));
  ordersCache.set(uid, { at: Date.now(), docs: sorted.map(cloneDoc) });
  return sorted;
}

/** One order by its full id, or null. */
export async function readOrder(db: Firestore, id: string): Promise<FsDoc | null> {
  return db.get(`orders/${id}`, ORDER_FIELDS);
}

/** What is late about an order right now, if anything. */
export function expiryFor(doc: FsDoc, now = Date.now()): "unconfirmed" | "unpaid" | "not_accepted" | null {
  const d = doc.data;
  if (d.source !== "agent") return null;
  const status = str(d.status);
  if (status === AWAITING_CONFIRMATION && num(d.confirmBy) > 0 && now > num(d.confirmBy)) return "unconfirmed";
  if (status === AWAITING_PAYMENT && num(d.payBy) > 0 && now > num(d.payBy) + PAYMENT_GRACE_MS) return "unpaid";
  if (status === "Placed" && num(d.paymentVerifiedAt) > 0 && num(d.placedAt) > 0 && now > num(d.placedAt) + LIMITS.acceptTimeoutMs) {
    return "not_accepted";
  }
  return null;
}

/**
 * Cancels [doc] ("auto_expired", plates back) if it is past a deadline. A
 * paid order the chef never accepted also gets refundStatus "due" and the
 * customer is told. Safe when two readers race: the commit has an
 * update-time precondition, so only one of them wins. Updates [doc] in place.
 */
export async function expireIfLate(db: Firestore, doc: FsDoc, now = Date.now()): Promise<"cancelled" | "refund_due" | null> {
  const why = expiryFor(doc, now);
  if (!why) return null;
  const refund = why === "not_accepted";
  // The whole order goes back, plus any overpayment refund the admin already recorded on approval.
  const alreadyDue = doc.data.refundStatus === "due" ? num(doc.data.refundAmount) : 0;
  const total = num(doc.data.totalPrice) + alreadyDue;
  const extra: Record<string, unknown> = refund ? { refundStatus: "due", refundAmount: total, refundReason: "chef_timeout" } : {};
  const customerId = str(doc.data.customerId);
  const also = refund && customerId
    ? [
        notification(
          customerId,
          "রিফান্ড দেওয়া হবে · Refund on the way",
          `Order ${shortId(doc.id)} wasn't accepted by the kitchen within 12 hours, so it was cancelled. TiffinBox will refund ৳${total} to you.`,
        ),
      ]
    : [];
  try {
    await cancelWithPlates(db, doc, "auto_expired", extra, also);
  } catch (e) {
    if (e instanceof ConflictError) return null; // someone else changed it first - the next read shows the truth
    throw e;
  }
  Object.assign(doc.data, { status: "Cancelled", cancelReason: "auto_expired", platesRestored: doc.data.platesReserved === true }, extra);
  if (customerId) forgetMyOrders(customerId);
  return refund ? "refund_due" : "cancelled";
}

/**
 * Cancels an order and gives its reserved plates back, all in one commit
 * (mirrors the app's cancelInTransaction). [extra] fields and [also] writes
 * go into the same commit.
 */
async function cancelWithPlates(
  db: Firestore,
  order: FsDoc,
  reason: string,
  extra: Record<string, unknown> = {},
  also: Write[] = [],
): Promise<void> {
  const writes: Write[] = [];
  const update: Record<string, unknown> = { status: "Cancelled", cancelReason: reason, cancelledAt: Date.now(), ...extra };
  if (order.data.platesReserved === true && order.data.platesRestored !== true) {
    update.platesRestored = true;
    const quantities = (order.data.mealQuantities ?? {}) as Record<string, number>;
    for (const [mealId, qty] of Object.entries(quantities)) {
      if (!(num(qty) > 0)) continue;
      // A meal the chef deleted since can't take plates back.
      if (!(await db.get(`meals/${mealId}`, ["name"]))) continue;
      writes.push({ op: "update", path: `meals/${mealId}`, data: { lastRestoreOrderId: order.id }, increments: { availableQuantity: num(qty) } });
    }
  }
  writes.push({ op: "update", path: `orders/${order.id}`, data: update, ifUpdateTime: order.updateTime });
  writes.push(...also);
  await db.commit(writes);
}

export interface SweepResult {
  looked: number;
  cancelled: number;
  refundsDue: number;
}

/**
 * The Cron Trigger's job (no Cloud Functions on Spark): cancel agent orders
 * past confirmBy, past payBy + grace, and - when [paid] - paid ones still
 * "Placed" 12 h after approval. Equality filters only, so no composite
 * index is needed. Safe to overlap with another run or a lazy reader.
 */
export async function sweepExpiredOrders(db: Firestore, opts: { paid: boolean }, now = Date.now()): Promise<SweepResult> {
  const byStatus = (status: string, more: Filter[] = []) =>
    db.query("orders", {
      filters: [{ field: "status", op: "EQUAL", value: status }, ...more],
      select: ORDER_FIELDS,
      limit: LIMITS.sweepBatch,
    });
  const lists = await Promise.all([
    byStatus(AWAITING_CONFIRMATION),
    byStatus(AWAITING_PAYMENT),
    opts.paid ? byStatus("Placed", [{ field: "source", op: "EQUAL", value: "agent" }]) : Promise.resolve([]),
  ]);
  const result: SweepResult = { looked: 0, cancelled: 0, refundsDue: 0 };
  for (const doc of lists.flat()) {
    result.looked++;
    try {
      const done = await expireIfLate(db, doc, now);
      if (done) result.cancelled++;
      if (done === "refund_due") result.refundsDue++;
    } catch (e) {
      console.error("sweep: could not expire", doc.id, e);
    }
  }
  return result;
}

export function trustLevel(orders: FsDoc[]): TrustLevel {
  const delivered = orders.filter((o) => o.data.status === "Delivered").length;
  if (delivered >= LIMITS.trustedAfterDelivered) return "trusted";
  if (delivered >= 1) return "regular";
  return "new";
}

export function rewardsOf(orders: FsDoc[]) {
  return rewardsFromOrders(
    orders.map((o) => ({
      status: str(o.data.status),
      totalPrice: num(o.data.totalPrice),
      pointsRedeemed: num(o.data.pointsRedeemed),
    })),
  );
}

const STATUS_TEXT: Record<string, string> = {
  [AWAITING_CONFIRMATION]: "Waiting for the customer to tap Confirm in the TiffinBox app (Orders tab).",
  [AWAITING_PAYMENT]:
    "Confirmed. Waiting for the advance payment (bKash/Nagad Send Money), then its TrxID and the last 4 digits of the number it was sent from.",
  [PAYMENT_SUBMITTED]: "TrxID received. TiffinBox is checking the payment; the kitchen gets the order once it's verified.",
  Placed: "Payment verified. The order is with the kitchen, waiting for the chef to accept it.",
  Accepted: "The chef accepted the order.",
  Cooking: "The food is being cooked.",
  OutForDelivery: "The order is on its way.",
  Delivered: "Delivered.",
  Cancelled: "Cancelled.",
};

export function orderView(doc: FsDoc, kitchenName: string, cfg: AppConfig) {
  const status = str(doc.data.status) || "Placed";
  const items = (Array.isArray(doc.data.items) ? doc.data.items : []) as Array<Record<string, unknown>>;
  const placedAt = num(doc.data.placedAt);
  const view: Record<string, unknown> = {
    orderId: doc.id,
    shortId: shortId(doc.id),
    kitchen: { id: str(doc.data.chefId), name: kitchenName },
    items: items.map((i) => ({ name: cleanText(i.mealName, 80), quantity: num(i.quantity), unitPriceBdt: num(i.unitPrice) })),
    totalBdt: num(doc.data.totalPrice),
    status,
    statusText: STATUS_TEXT[status] ?? status,
    placedAt: placedAt ? new Date(placedAt).toISOString() : null,
    viaAgent: doc.data.source === "agent" || undefined,
  };
  if (status === "Cancelled") {
    view.cancelReason = str(doc.data.cancelReason) || undefined;
    if (doc.data.rejectReason) view.rejectReason = cleanText(doc.data.rejectReason, 200);
  }
  if (status === AWAITING_CONFIRMATION) view.confirmBy = new Date(num(doc.data.confirmBy)).toISOString();
  if (status === AWAITING_PAYMENT) {
    view.payBy = new Date(num(doc.data.payBy)).toISOString();
    view.howToPay = paymentInstructions(cfg, num(doc.data.totalPrice));
    // Written by an admin on "Ask again", e.g. "TrxID not found, check it".
    if (doc.data.paymentNote) view.paymentNote = cleanText(doc.data.paymentNote, 300);
  }
  if (status === PAYMENT_SUBMITTED) {
    view.payment = {
      method: str(doc.data.paymentMethod),
      transactionId: str(doc.data.paymentTransactionId),
      senderLast4: str(doc.data.paymentSenderLast4) || undefined,
    };
  }
  const refundStatus = str(doc.data.refundStatus);
  if (refundStatus) {
    const amount = num(doc.data.refundAmount);
    view.refund = {
      status: refundStatus,
      amountBdt: amount,
      tellUser:
        refundStatus === "refunded"
          ? `TiffinBox has refunded ৳${amount} for this order.`
          : `TiffinBox will refund ৳${amount} for this order.`,
    };
  }
  return view;
}

export function paymentInstructions(cfg: AppConfig, total: number): string {
  return (
    `Send Money ৳${total} by bKash or Nagad to ${cfg.agentPaymentNumber} (TiffinBox). ` +
    "Then give the TrxID, the method and the last 4 digits of the number you paid from with submit_payment, " +
    "or type them in the app (Orders → this order). Cash on delivery is not available for agent orders."
  );
}

// ---------------------------------------------------------------- quoting

export interface Quote {
  uid: string;
  kitchenId: string;
  kitchenName: string;
  items: Array<{ mealId: string; name: string; quantity: number; unitPrice: number }>;
  subtotal: number;
  deliveryFee: number;
  platformFee: number;
  rankDiscount: number;
  rankTitle: string;
  couponCode: string;
  couponDiscount: number;
  /** The best coupon the customer didn't ask for - suggested, never applied silently. */
  couponSuggestion: { code: string; offer: string; saveBdt: number } | null;
  total: number;
  etaMinutes: number;
  deliveryHandledBy: string;
  address: SavedAddress;
  trust: TrustLevel;
  createdAt: number;
}

export interface QuoteInput {
  items: Array<{ mealId: string; quantity: number }>;
  addressIndex?: number;
  couponCode?: string;
}

/** The coupon that saves most on this order, among those usable at this kitchen right now. */
function bestCoupon(coupons: Coupon[], kitchenId: string, subtotal: number, now: number): Coupon | null {
  const usable = coupons.filter((c) => isLive(c, now) && (!c.chefId || c.chefId === kitchenId) && subtotal >= c.minOrder);
  return usable.reduce<Coupon | null>((best, c) => (!best || discountFor(c, subtotal) > discountFor(best, subtotal) ? c : best), null);
}

/** Fresh meal docs (not the 60-s cache): price and plates must be exact when money is involved. */
async function readMeals(db: Firestore, ids: string[]): Promise<FsDoc[]> {
  const docs = await Promise.all(
    ids.map((id) => db.get(`meals/${id}`, ["name", "chefId", "price", "availableQuantity", "isAvailable"])),
  );
  return docs.map((d, i) => {
    if (!d || d.data.isAvailable === false) throw new OrderError(`Dish ${ids[i]} is not available. Use get_menu to see what is.`);
    return d;
  });
}

function mergeItems(items: QuoteInput["items"]): Map<string, number> {
  const merged = new Map<string, number>();
  for (const it of items) merged.set(it.mealId, (merged.get(it.mealId) ?? 0) + it.quantity);
  if (merged.size === 0) throw new OrderError("Add at least one dish.");
  if (merged.size > LIMITS.maxItemsPerOrder) throw new OrderError(`At most ${LIMITS.maxItemsPerOrder} different dishes per order.`);
  for (const q of merged.values()) {
    if (!Number.isInteger(q) || q < 1 || q > LIMITS.maxPlatesPerItem) {
      throw new OrderError(`Each dish can be 1 to ${LIMITS.maxPlatesPerItem} plates.`);
    }
  }
  return merged;
}

export function checkTrust(orders: FsDoc[], total: number): TrustLevel {
  const trust = trustLevel(orders);
  const tier = LIMITS.trust[trust];
  const cap = Math.min(tier.maxTotal, LIMITS.hardMaxTotal);
  if (total > cap) {
    throw new OrderError(
      `Agent orders for this account can be at most ৳${cap} (${tier.label}). Order a smaller amount, or use the app.`,
    );
  }
  const pending = orders.filter((o) => AGENT_PENDING.includes(str(o.data.status))).length;
  if (pending >= tier.pendingAtOnce) {
    throw new OrderError(
      "There is already an agent order waiting for confirmation or payment. Finish or cancel it first (list_my_orders).",
    );
  }
  return trust;
}

export async function prepareQuote(
  db: Firestore,
  uid: string,
  user: UserInfo,
  cfg: AppConfig,
  input: QuoteInput,
): Promise<Quote> {
  const index = input.addressIndex ?? user.defaultAddressIndex;
  const address = user.addresses[index];
  if (!address || (address.latitude === 0 && address.longitude === 0)) {
    throw new OrderError("No delivery address with a location. The customer can add one in the app: Profile → Edit Profile.");
  }
  const merged = mergeItems(input.items);
  const meals = await readMeals(db, [...merged.keys()]);
  const kitchenIds = new Set(meals.map((m) => str(m.data.chefId)));
  if (kitchenIds.size !== 1) throw new OrderError("All dishes in one order must come from the same kitchen.");
  const kitchenId = [...kitchenIds][0]!;
  const kitchen: Kitchen | undefined = (await loadKitchens(db)).find((k) => k.id === kitchenId);
  if (!kitchen) throw new OrderError("That kitchen isn't taking orders on TiffinBox.");
  if (!kitchen.availableToday) throw new OrderError(`${kitchen.name} is not taking orders today.`);

  const items = meals.map((m) => {
    const quantity = merged.get(m.id)!;
    const left = num(m.data.availableQuantity);
    if (left < quantity) {
      throw new OrderError(`Only ${left} plate(s) of ${cleanText(m.data.name, 80)} left today.`);
    }
    return { mealId: m.id, name: cleanText(m.data.name, 80), quantity, unitPrice: num(m.data.price) };
  });
  const subtotal = items.reduce((s, i) => s + i.unitPrice * i.quantity, 0);

  const distance =
    kitchen.latitude !== 0 || kitchen.longitude !== 0
      ? haversineKm(address.latitude, address.longitude, kitchen.latitude, kitchen.longitude)
      : null;
  const ctx: DeliveryContext = {
    appPricing: cfg.delivery,
    globalMaxFee: cfg.deliveryMaxFee,
    surcharge: surchargeAt(await loadSurcharges(db), address.latitude, address.longitude, Date.now()),
  };
  const delivery = quoteKitchen(kitchen, distance, ctx);

  const now = Date.now();
  let coupon: Coupon | null = null;
  if (input.couponCode?.trim()) {
    coupon = await loadCoupon(db, input.couponCode);
    const problem = couponProblem(coupon, kitchenId, subtotal, now);
    if (problem) throw new OrderError(`${problem} Use find_coupons to see what works here, or prepare the order without a coupon.`);
  }
  const couponDiscount = coupon ? discountFor(coupon, subtotal) : 0;
  const best = coupon ? null : bestCoupon(await loadActiveCoupons(db), kitchenId, subtotal, now);

  const myOrders = await loadMyOrders(db, uid);
  const rewards = rewardsOf(myOrders);
  // Same as Checkout: the rank discount is on the food subtotal, the coupon comes off too.
  const discount = rankDiscount(rewards.rank, subtotal);
  const deliveryFee = rewards.rank.freeDelivery ? 0 : delivery.fee;
  const total = Math.max(subtotal + deliveryFee + cfg.platformFee - couponDiscount - discount, 0);
  const trust = checkTrust(myOrders, total);

  return {
    uid,
    kitchenId,
    kitchenName: kitchen.name,
    items,
    subtotal,
    deliveryFee,
    platformFee: cfg.platformFee,
    rankDiscount: discount,
    rankTitle: `${rewards.rank.emoji} ${rewards.rank.title}`,
    couponCode: coupon?.code ?? "",
    couponDiscount,
    couponSuggestion: best && discountFor(best, subtotal) > 0
      ? { code: best.code, offer: describeCoupon(best), saveBdt: discountFor(best, subtotal) }
      : null,
    total,
    etaMinutes: delivery.minutes,
    deliveryHandledBy: kitchen.deliveryMode,
    address,
    trust,
    createdAt: Date.now(),
  };
}

export function quoteView(id: string, q: Quote, cfg: AppConfig) {
  return {
    quoteId: id,
    validForMinutes: LIMITS.quoteTtlMs / 60000,
    kitchen: { id: q.kitchenId, name: q.kitchenName },
    items: q.items.map((i) => ({ name: i.name, quantity: i.quantity, unitPriceBdt: i.unitPrice, lineBdt: i.unitPrice * i.quantity })),
    subtotalBdt: q.subtotal,
    deliveryFeeBdt: q.deliveryFee,
    platformFeeBdt: q.platformFee,
    rankDiscountBdt: q.rankDiscount || undefined,
    rank: q.rankTitle,
    coupon: q.couponCode ? { code: q.couponCode, discountBdt: q.couponDiscount } : undefined,
    couponSuggestion: q.couponSuggestion
      ? {
          ...q.couponSuggestion,
          note: "Tell the customer about this coupon. If they want it, call prepare_order again with this couponCode.",
        }
      : undefined,
    totalBdt: q.total,
    deliverTo: `${q.address.label || "Home"}: ${q.address.address}`,
    estimatedMinutes: q.etaMinutes,
    payment: `Advance only. After the customer confirms in the app: ${paymentInstructions(cfg, q.total)}`,
    nextStep:
      "Show this summary to the customer. Only if they clearly say yes, call place_order with this quoteId and a new idempotencyKey. " +
      "A quote can be used once; prepare a new one if placing fails.",
  };
}

// ---------------------------------------------------------------- placing

/**
 * Creates order [orderId] (derived from the idempotency key) from a quote.
 * The create has an exists=false precondition, so a retry with the same key
 * can never order twice: on a conflict, if that order is already this
 * customer's, it is reported as already placed.
 */
export async function placeFromQuote(
  db: Firestore,
  quote: Quote,
  user: UserInfo,
  agent: { connectionId: string; clientName: string },
  orderId: string,
): Promise<{ orderId: string; alreadyPlaced: boolean }> {
  // Re-read everything that could have changed since the quote.
  if (quote.couponCode) {
    const coupon = await loadCoupon(db, quote.couponCode);
    const problem = couponProblem(coupon, quote.kitchenId, quote.subtotal, Date.now());
    if (problem) throw new OrderError(`${problem} Prepare the order again.`);
    if (discountFor(coupon!, quote.subtotal) !== quote.couponDiscount) {
      throw new OrderError("The coupon's discount has changed since the quote. Call prepare_order again for a new quote.");
    }
  }
  const meals = await readMeals(db, quote.items.map((i) => i.mealId));
  for (const m of meals) {
    const item = quote.items.find((i) => i.mealId === m.id)!;
    if (num(m.data.price) !== item.unitPrice) {
      throw new OrderError(`The price of ${item.name} changed. Call prepare_order again for a new quote.`);
    }
    if (num(m.data.availableQuantity) < item.quantity) {
      throw new OrderError(`${item.name} has only ${num(m.data.availableQuantity)} plate(s) left now. Prepare a new quote.`);
    }
  }
  const now = Date.now();
  const mealQuantities = Object.fromEntries(quote.items.map((i) => [i.mealId, i.quantity]));
  const order: Record<string, unknown> = {
    customerId: quote.uid,
    chefId: quote.kitchenId,
    items: quote.items.map((i) => ({ mealId: i.mealId, mealName: i.name, quantity: i.quantity, unitPrice: i.unitPrice })),
    subtotal: quote.subtotal,
    deliveryFee: quote.deliveryFee,
    platformFee: quote.platformFee,
    couponCode: quote.couponCode,
    discount: quote.couponDiscount,
    pointsRedeemed: 0,
    rankDiscount: quote.rankDiscount,
    estimatedDeliveryMinutes: quote.etaMinutes,
    totalPrice: quote.total,
    deliveryAddress: quote.address.address,
    // The chef needs these to deliver, exactly like an app order. Never shown to the agent.
    customerName: user.name,
    customerPhone: user.phoneNumber,
    deliveryLatitude: quote.address.latitude,
    deliveryLongitude: quote.address.longitude,
    paymentMethod: "",
    paymentTransactionId: "",
    paymentScreenshotBase64: "",
    deliveryHandledBy: quote.deliveryHandledBy,
    deliveryPartnerId: "",
    status: AWAITING_CONFIRMATION,
    placedAt: now,
    confirmBy: now + LIMITS.confirmWindowMs,
    payBy: 0,
    mealIds: quote.items.map((i) => i.mealId),
    mealQuantities,
    platesReserved: true,
    platesRestored: false,
    isPreOrder: false,
    scheduledFor: 0,
    source: "agent",
    agentConnectionId: agent.connectionId,
    agentClientName: agent.clientName,
  };
  const writes: Write[] = [{ op: "create", path: `orders/${orderId}`, data: order }];
  for (const m of meals) {
    const item = quote.items.find((i) => i.mealId === m.id)!;
    // Precondition: the meal hasn't changed since we read it, so plates can't be oversold.
    writes.push({
      op: "update",
      path: `meals/${m.id}`,
      data: { availableQuantity: num(m.data.availableQuantity) - item.quantity, lastOrderId: orderId },
      ifUpdateTime: m.updateTime,
    });
  }
  const summary = quote.items.map((i) => `${i.name} x${i.quantity}`).join(", ");
  writes.push(
    notification(
      quote.uid,
      "🤖 AI এজেন্টের অর্ডার কনফার্ম করুন · Confirm your agent's order",
      `${agent.clientName}: ${summary} from ${quote.kitchenName} - ৳${quote.total}. Open Orders and tap Confirm within 15 minutes.`,
    ),
  );
  try {
    await db.commit(writes);
  } catch (e) {
    if (e instanceof ConflictError) {
      // Either this key's order already exists (a retry racing the first call) or a meal changed.
      const existing = await readOrder(db, orderId);
      if (existing) {
        if (existing.data.customerId === quote.uid) return { orderId, alreadyPlaced: true };
        throw new OrderError("That idempotencyKey can't be used. Use a new one.");
      }
      throw new OrderError("Someone else ordered from this kitchen at the same moment. Call prepare_order again.");
    }
    throw e;
  } finally {
    forgetMyOrders(quote.uid);
  }
  return { orderId, alreadyPlaced: false };
}

// ---------------------------------------------------------------- paying / cancelling

export function normalizeTxnId(raw: string): string | null {
  const t = raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
  return /^[A-Z0-9]{8,12}$/.test(t) ? t : null;
}

/** "5678" from "5678" or "৫৬৭৮" (Bengali digits); null unless exactly 4 digits. */
export function normalizeLast4(raw: string): string | null {
  const t = raw.trim().replace(/[০-৯]/g, (d) => String(d.charCodeAt(0) - 0x09e6));
  return /^[0-9]{4}$/.test(t) ? t : null;
}

/**
 * One of the customer's orders, read fresh. Takes the full id, or the short
 * "#ABC123" form the app and chat show (only that form looks through the
 * customer's order list). Late orders are expired on the spot.
 */
export async function findMyOrder(db: Firestore, uid: string, orderId: string): Promise<FsDoc> {
  const id = orderId.trim().replace(/^#/, "");
  if (!/^[A-Za-z0-9_-]{1,40}$/.test(id)) throw new OrderError("Unknown order id.");
  let doc: FsDoc | null = null;
  if (id.length === 6) {
    const short = id.toUpperCase();
    const match = (list: FsDoc[]) => list.find((o) => o.id.slice(-6).toUpperCase() === short);
    const hit = match(await loadMyOrders(db, uid)) ?? match(await loadMyOrders(db, uid, { fresh: true }));
    if (hit) doc = await readOrder(db, hit.id);
  } else {
    doc = await readOrder(db, id);
  }
  // Same answer for "not yours" and "doesn't exist" - no probing other people's orders.
  if (!doc || doc.data.customerId !== uid) throw new OrderError("No order with that id on this account.");
  await expireIfLate(db, doc).catch((e) => console.error("lazy expiry failed", doc.id, e));
  return doc;
}

export async function submitPayment(
  db: Firestore,
  uid: string,
  orderId: string,
  method: "bkash" | "nagad",
  rawTxnId: string,
  rawLast4: string,
): Promise<FsDoc> {
  const order = await findMyOrder(db, uid, orderId);
  const status = str(order.data.status);
  if (status === AWAITING_CONFIRMATION) {
    throw new OrderError("The customer must first tap Confirm on this order in the TiffinBox app (Orders tab).");
  }
  if (status !== AWAITING_PAYMENT) throw new OrderError(`This order isn't waiting for payment (status: ${status}).`);
  if (num(order.data.payBy) > 0 && Date.now() > num(order.data.payBy) + PAYMENT_GRACE_MS) {
    throw new OrderError("The payment time for this order is over, so it will be cancelled. Prepare a new order.");
  }
  const txn = normalizeTxnId(rawTxnId);
  if (!txn) throw new OrderError("That doesn't look like a bKash/Nagad TrxID (8-12 letters and digits).");
  const last4 = normalizeLast4(rawLast4);
  if (!last4) throw new OrderError("Ask the customer for the last 4 digits of the bKash/Nagad number they paid from.");
  const paymentMethod = method === "bkash" ? "BKASH" : "NAGAD";
  const now = Date.now();
  try {
    await db.commit([
      // Each TrxID can pay for one order only (create fails if it already exists).
      {
        op: "create",
        path: `payment_txn_ids/${paymentMethod}_${txn}`,
        data: { orderId: order.id, customerId: uid, method: paymentMethod, senderLast4: last4, createdAt: now },
      },
      {
        op: "update",
        path: `orders/${order.id}`,
        data: {
          status: PAYMENT_SUBMITTED,
          paymentMethod,
          paymentTransactionId: txn,
          paymentSenderLast4: last4,
          paymentSubmittedAt: now,
          paymentSubmittedVia: "agent",
        },
        ifUpdateTime: order.updateTime,
      },
      notification(
        uid,
        "পেমেন্ট জমা হয়েছে · Payment submitted",
        `${paymentMethod} TrxID ${txn} (from a number ending ${last4}) for order ${shortId(order.id)}. TiffinBox will verify it soon.`,
      ),
    ]);
  } catch (e) {
    if (e instanceof ConflictError) {
      if (await db.get(`payment_txn_ids/${paymentMethod}_${txn}`, ["orderId"])) {
        throw new OrderError("This TrxID has already been used for another order.");
      }
      throw new OrderError("The order changed just now. Check it with get_order_status and try again.");
    }
    throw e;
  } finally {
    forgetMyOrders(uid);
  }
  Object.assign(order.data, { status: PAYMENT_SUBMITTED, paymentMethod, paymentTransactionId: txn, paymentSenderLast4: last4 });
  return order;
}

export async function cancelMyOrder(db: Firestore, uid: string, orderId: string): Promise<FsDoc> {
  const order = await findMyOrder(db, uid, orderId);
  const status = str(order.data.status);
  if (status !== AWAITING_CONFIRMATION && status !== AWAITING_PAYMENT) {
    throw new OrderError(
      status === "Cancelled"
        ? "This order is already cancelled."
        : "This order has been paid for, so it can only be cancelled by TiffinBox support (they handle the refund).",
    );
  }
  try {
    await cancelWithPlates(db, order, "customer");
  } catch (e) {
    if (e instanceof ConflictError) throw new OrderError("The order changed just now. Check it with get_order_status.");
    throw e;
  } finally {
    forgetMyOrders(uid);
  }
  order.data.status = "Cancelled";
  order.data.cancelReason = "customer";
  return order;
}
