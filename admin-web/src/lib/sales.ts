import type { Order } from "../types";
import { AGENT_PENDING_STATUSES } from "../types";

/**
 * Whether an order counts as a sale: not cancelled, and not an AI agent order
 * still waiting for its advance payment to be verified. Dashboard, Orders and
 * Revenue all use this so their totals agree.
 */
export function isRealSale(order: Pick<Order, "status">): boolean {
  const status = String(order.status ?? "Placed");
  return status !== "Cancelled" && !AGENT_PENDING_STATUSES.includes(status);
}
