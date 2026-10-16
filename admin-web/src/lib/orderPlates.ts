import { doc, getDoc, increment, writeBatch } from "firebase/firestore";
import { db } from "../firebase";
import type { Order } from "../types";

/** Same 12h rule as the Android app (FirestoreOrderRepository.ORDER_ACCEPT_TIMEOUT_MS). */
export const ORDER_ACCEPT_TIMEOUT_MS = 12 * 60 * 60 * 1000;

/** When a still-"Placed" order counts as expired: 12h after placing, but never before a pre-order's slot. */
export function expiresAt(order: Order): number {
  return Math.max((order.placedAt ?? 0) + ORDER_ACCEPT_TIMEOUT_MS, order.scheduledFor ?? 0);
}

/**
 * Cancels an order and, if it reserved plates when it was placed (an "order
 * for now" in the app), gives those plates back to each meal's
 * availableQuantity - the same bookkeeping the app does, so plates-left stays
 * right when the admin (or this page's auto-expiry) cancels.
 */
export async function cancelOrderReleasingPlates(order: Order, cancelReason: string): Promise<void> {
  const batch = writeBatch(db);
  const update: Record<string, unknown> = { status: "Cancelled", cancelReason, cancelledAt: Date.now() };
  if (order.platesReserved && !order.platesRestored) {
    update.platesRestored = true;
    for (const [mealId, qty] of Object.entries(order.mealQuantities ?? {})) {
      if (!qty || qty <= 0) continue;
      const mealRef = doc(db, "meals", mealId);
      // A meal the chef deleted since can't take plates back.
      if (!(await getDoc(mealRef)).exists()) continue;
      batch.update(mealRef, { availableQuantity: increment(qty), lastRestoreOrderId: order.id });
    }
  }
  batch.update(doc(db, "orders", order.id), update);
  await batch.commit();
}
