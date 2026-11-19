import { doc, increment, runTransaction } from "firebase/firestore";
import { db } from "../firebase";
import type { Order } from "../types";

/** Same 12h rule as the Android app (FirestoreOrderRepository.ORDER_ACCEPT_TIMEOUT_MS). */
export const ORDER_ACCEPT_TIMEOUT_MS = 12 * 60 * 60 * 1000;

/** When a still-"Placed" order counts as expired: 12h after placing, but never before a pre-order's slot. */
export function expiresAt(order: Order): number {
  return Math.max((order.placedAt ?? 0) + ORDER_ACCEPT_TIMEOUT_MS, order.scheduledFor ?? 0);
}

/** A paid agent order (the admin verified the money): cancelling it means a refund is owed. */
export function isPaidAgentOrder(order: Pick<Order, "source" | "paymentVerifiedAt">): boolean {
  return order.source === "agent" && !!order.paymentVerifiedAt;
}

export interface CancelOptions {
  /** Only cancel if the order's current status (re-read in the transaction) is one of these. */
  expectStatus?: string[];
  /** More fields written in the same update (e.g. rejectReason). Applied last. */
  extra?: Record<string, unknown>;
}

/**
 * Cancels an order and, if it reserved plates when it was placed (an "order
 * for now" in the app), gives those plates back to each meal's
 * availableQuantity - the same bookkeeping the app does, so plates-left stays
 * right when the admin (or a page's auto-expiry) cancels.
 *
 * Runs in a transaction on the order's current data, so plates are never given
 * back twice and `expectStatus` can refuse an order someone else already moved.
 * A paid agent order (`source "agent"` + `paymentVerifiedAt`) also gets
 * `refundStatus "due"`, `refundAmount` = totalPrice and `refundReason` = the
 * cancel reason (docs/AGENT_PAYMENTS.md).
 *
 * Returns false (and writes nothing) when the order is gone, already
 * cancelled, or not in `expectStatus`.
 */
export async function cancelOrderReleasingPlates(
  order: Order,
  cancelReason: string,
  options: CancelOptions = {},
): Promise<boolean> {
  const orderRef = doc(db, "orders", order.id);
  return runTransaction(db, async (tx) => {
    const snap = await tx.get(orderRef);
    if (!snap.exists()) return false;
    const current = { id: snap.id, ...snap.data() } as Order;
    const status = String(current.status ?? "Placed");
    if (status === "Cancelled") return false;
    if (options.expectStatus && !options.expectStatus.includes(status)) return false;

    const update: Record<string, unknown> = { status: "Cancelled", cancelReason, cancelledAt: Date.now() };
    const mealUpdates: { ref: ReturnType<typeof doc>; qty: number }[] = [];
    if (current.platesReserved && !current.platesRestored) {
      update.platesRestored = true;
      for (const [mealId, qty] of Object.entries(current.mealQuantities ?? {})) {
        if (!qty || qty <= 0) continue;
        const mealRef = doc(db, "meals", mealId);
        // A meal the chef deleted since can't take plates back.
        if (!(await tx.get(mealRef)).exists()) continue;
        mealUpdates.push({ ref: mealRef, qty });
      }
    }
    if (isPaidAgentOrder(current)) {
      update.refundStatus = "due";
      // An overpayment still owed is part of what goes back (one already
      // refunded is not - "refunded" before a cancel can only be that).
      const alreadyOwed = current.refundStatus === "due" ? current.refundAmount ?? 0 : 0;
      update.refundAmount = (current.totalPrice ?? 0) + alreadyOwed;
      update.refundReason = cancelReason;
    }
    for (const m of mealUpdates) {
      tx.update(m.ref, { availableQuantity: increment(m.qty), lastRestoreOrderId: order.id });
    }
    tx.update(orderRef, { ...update, ...(options.extra ?? {}) });
    return true;
  });
}
