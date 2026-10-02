import type { BadgeTone } from "../components/ui/Badge";
import type { OrderStatus } from "../types";

/** Badge tone per order status — used anywhere a status badge is shown. */
export const STATUS_BADGE_TONE: Record<OrderStatus, BadgeTone> = {
  Placed: "blue",
  Accepted: "blue",
  Cooking: "amber",
  OutForDelivery: "amber",
  Delivered: "green",
  Cancelled: "red",
};

/**
 * Distinct chart colors per order status, for the Dashboard's order-status
 * breakdown chart (drawn from the validated categorical palette; chosen so
 * each stays visually distinct and reads intuitively — blue for "just in",
 * green for a completed delivery, red for cancelled).
 */
export const STATUS_CHART_COLOR: Record<OrderStatus, string> = {
  Placed: "#2a78d6",
  Accepted: "#eb6834",
  Cooking: "#eda100",
  OutForDelivery: "#1baf7a",
  Delivered: "#008300",
  Cancelled: "#e34948",
};

/** The normal (non-cancelled) happy-path sequence, used for the order detail stepper. */
export const ORDER_STATUS_STEPS: OrderStatus[] = [
  "Placed",
  "Accepted",
  "Cooking",
  "OutForDelivery",
  "Delivered",
];
