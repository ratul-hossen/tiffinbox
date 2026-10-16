import { useState } from "react";
import { Check, Trash2, X } from "lucide-react";
import DetailDrawer from "./ui/DetailDrawer";
import Badge from "./ui/Badge";
import type { Order } from "../types";
import { formatCurrency, formatDateTime } from "../lib/format";
import { ORDER_STATUS_STEPS, STATUS_BADGE_TONE } from "../lib/orderStatus";

interface Props {
  order: Order | null;
  chefName: string;
  onClose: () => void;
  /** Moves the order to the Recycle Bin (OrdersPage). */
  onRemove?: (order: Order) => Promise<void>;
}

function StatusTimeline({ status }: { status?: string }) {
  if (status === "Cancelled") {
    return (
      <div className="flex items-center gap-2 text-sm text-apple-red font-medium">
        <span className="rounded-full bg-apple-red/10 p-1.5">
          <X size={14} />
        </span>
        Cancelled
      </div>
    );
  }

  const currentIndex = Math.max(0, ORDER_STATUS_STEPS.indexOf((status as never) ?? "Placed"));

  return (
    <div className="flex items-center">
      {ORDER_STATUS_STEPS.map((step, idx) => {
        const done = idx <= currentIndex;
        const isLast = idx === ORDER_STATUS_STEPS.length - 1;
        return (
          <div key={step} className="flex items-center flex-1 last:flex-initial">
            <div className="flex flex-col items-center gap-1">
              <div
                className={`w-6 h-6 rounded-full flex items-center justify-center transition-colors duration-200 ${
                  done ? "bg-apple-blue text-white" : "bg-apple-hover text-apple-secondary"
                }`}
              >
                {done ? <Check size={13} /> : <span className="text-[10px]">{idx + 1}</span>}
              </div>
              <span
                className={`text-[11px] text-center whitespace-nowrap ${
                  done ? "text-apple-text font-medium" : "text-apple-secondary"
                }`}
              >
                {step}
              </span>
            </div>
            {!isLast && (
              <div
                className={`h-0.5 flex-1 mx-1 rounded ${idx < currentIndex ? "bg-apple-blue" : "bg-apple-hover"}`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

export default function OrderDetail({ order, chefName, onClose, onRemove }: Props) {
  const [removing, setRemoving] = useState(false);
  if (!order) return null;
  const items = order.items ?? [];

  return (
    <DetailDrawer open={!!order} onClose={onClose} title={`Order ${order.id.slice(0, 8)}…`} subtitle={chefName}>
      <div className="space-y-6">
        <div>
          <StatusTimeline status={order.status} />
        </div>

        <div className="grid grid-cols-2 gap-3 text-sm">
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">Customer ID</div>
            <div className="truncate font-medium">{order.customerId ?? "–"}</div>
          </div>
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">Chef ID</div>
            <div className="truncate font-medium">{order.chefId ?? "–"}</div>
          </div>
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">Delivery address</div>
            <div className="font-medium">{order.deliveryAddress || "–"}</div>
          </div>
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">Payment method</div>
            <div className="font-medium">{order.paymentMethod || "–"}</div>
          </div>
          {order.paymentTransactionId && (
            <div className="bg-apple-bg rounded-xl px-3 py-2 col-span-2">
              <div className="text-xs text-apple-secondary">{order.paymentMethod ?? "Mobile banking"} Transaction ID</div>
              <div className="font-medium truncate">{order.paymentTransactionId}</div>
            </div>
          )}
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">Placed at</div>
            <div className="font-medium">{formatDateTime(order.placedAt)}</div>
          </div>
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">{order.scheduledFor ? "Pre-order for" : "Estimated delivery"}</div>
            <div className="font-medium">
              {order.scheduledFor
                ? new Date(order.scheduledFor).toLocaleString([], { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" })
                : order.estimatedDeliveryMinutes && order.placedAt
                ? `~${order.estimatedDeliveryMinutes} min (by ${new Date(order.placedAt + order.estimatedDeliveryMinutes * 60000).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })})`
                : "–"}
            </div>
          </div>
        </div>

        {order.paymentScreenshotBase64 && (
          <section>
            <h4 className="text-sm font-semibold text-apple-text mb-2">{order.paymentMethod ?? "Mobile banking"} payment screenshot</h4>
            <a
              href={`data:image/jpeg;base64,${order.paymentScreenshotBase64}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              <img
                src={`data:image/jpeg;base64,${order.paymentScreenshotBase64}`}
                alt="Payment screenshot"
                className="max-h-60 rounded-xl border border-apple-border2"
              />
            </a>
          </section>
        )}

        <section>
          <h4 className="text-sm font-semibold text-apple-text mb-2">Items</h4>
          <div className="bg-apple-bg rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-apple-secondary">
                  <th className="px-3 py-2">Meal</th>
                  <th className="px-3 py-2 text-right">Qty</th>
                  <th className="px-3 py-2 text-right">Unit</th>
                  <th className="px-3 py-2 text-right">Line total</th>
                </tr>
              </thead>
              <tbody>
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-3 py-3 text-apple-secondary text-xs">
                      No item breakdown recorded for this order.
                    </td>
                  </tr>
                ) : (
                  items.map((it, idx) => (
                    <tr key={idx} className="border-t border-apple-border">
                      <td className="px-3 py-2">{it.mealName ?? it.mealId ?? "–"}</td>
                      <td className="px-3 py-2 text-right">{it.quantity ?? 0}</td>
                      <td className="px-3 py-2 text-right">{formatCurrency(it.unitPrice ?? 0)}</td>
                      <td className="px-3 py-2 text-right font-medium">
                        {formatCurrency((it.unitPrice ?? 0) * (it.quantity ?? 0))}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="space-y-1 text-sm">
          <div className="flex justify-between">
            <span className="text-apple-secondary">Subtotal</span>
            <span>{formatCurrency(order.subtotal ?? 0)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-apple-secondary">Delivery fee</span>
            <span>{formatCurrency(order.deliveryFee ?? 0)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-apple-secondary">Platform fee</span>
            <span>{formatCurrency(order.platformFee ?? 0)}</span>
          </div>
          {(order.discount ?? 0) > 0 && (
            <div className="flex justify-between">
              <span className="text-apple-secondary">Coupon {order.couponCode}</span>
              <span>-{formatCurrency(order.discount ?? 0)}</span>
            </div>
          )}
          {(order.rankDiscount ?? 0) > 0 && (
            <div className="flex justify-between">
              <span className="text-apple-secondary">Rewards rank discount</span>
              <span>-{formatCurrency(order.rankDiscount ?? 0)}</span>
            </div>
          )}
          {(order.pointsRedeemed ?? 0) > 0 && (
            <div className="flex justify-between">
              <span className="text-apple-secondary">Points used ({order.pointsRedeemed})</span>
              <span>-{formatCurrency(Math.floor((order.pointsRedeemed ?? 0) / 10))}</span>
            </div>
          )}
          <div className="flex justify-between text-base font-semibold border-t border-apple-border pt-2 mt-1">
            <span>Total</span>
            <span>{formatCurrency(order.totalPrice ?? 0)}</span>
          </div>
        </section>

        <div>
          <Badge tone={STATUS_BADGE_TONE[(order.status as keyof typeof STATUS_BADGE_TONE) ?? "Placed"] ?? "neutral"}>
            {order.status ?? "Placed"}
          </Badge>
        </div>

        {onRemove && (
          <button
            disabled={removing}
            onClick={async () => {
              if (!confirm("Remove this order? It'll move to the Recycle Bin (restorable from there) and stop counting in revenue.")) return;
              setRemoving(true);
              try {
                await onRemove(order);
                onClose();
              } catch (err) {
                alert(
                  (err instanceof Error ? err.message : "Failed to remove order.") +
                    "\n\nIf this says 'Missing or insufficient permissions', publish the latest firestore.rules in Firebase Console.",
                );
              } finally {
                setRemoving(false);
              }
            }}
            className="w-full flex items-center justify-center gap-2 rounded-xl border border-apple-red/40 text-apple-red hover:bg-apple-red/10 py-2.5 text-sm font-medium disabled:opacity-50 transition-colors duration-150"
          >
            <Trash2 size={15} /> {removing ? "Removing…" : "Remove order"}
          </button>
        )}
      </div>
    </DetailDrawer>
  );
}
