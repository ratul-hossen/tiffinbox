import { Check, X } from "lucide-react";
import DetailDrawer from "./ui/DetailDrawer";
import Badge from "./ui/Badge";
import type { Order } from "../types";
import { formatCurrency, formatDateTime } from "../lib/format";
import { ORDER_STATUS_STEPS, STATUS_BADGE_TONE } from "../lib/orderStatus";

interface Props {
  order: Order | null;
  chefName: string;
  onClose: () => void;
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
                  done ? "bg-apple-blue text-white" : "bg-black/5 text-apple-secondary"
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
                className={`h-0.5 flex-1 mx-1 rounded ${idx < currentIndex ? "bg-apple-blue" : "bg-black/5"}`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

export default function OrderDetail({ order, chefName, onClose }: Props) {
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
          {order.bkashTransactionId && (
            <div className="bg-apple-bg rounded-xl px-3 py-2">
              <div className="text-xs text-apple-secondary">bKash Transaction ID</div>
              <div className="font-medium truncate">{order.bkashTransactionId}</div>
            </div>
          )}
          {order.bkashScreenshotUrl && (
            <div className="bg-apple-bg rounded-xl px-3 py-2">
              <div className="text-xs text-apple-secondary">bKash screenshot</div>
              <a
                href={order.bkashScreenshotUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="font-medium text-apple-blue hover:text-apple-blue-dark transition-colors duration-150"
              >
                View screenshot
              </a>
            </div>
          )}
          <div className="bg-apple-bg rounded-xl px-3 py-2 col-span-2">
            <div className="text-xs text-apple-secondary">Placed at</div>
            <div className="font-medium">{formatDateTime(order.placedAt)}</div>
          </div>
        </div>

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
                    <tr key={idx} className="border-t border-black/5">
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
          <div className="flex justify-between text-base font-semibold border-t border-black/5 pt-2 mt-1">
            <span>Total</span>
            <span>{formatCurrency(order.totalPrice ?? 0)}</span>
          </div>
        </section>

        <div>
          <Badge tone={STATUS_BADGE_TONE[(order.status as keyof typeof STATUS_BADGE_TONE) ?? "Placed"] ?? "neutral"}>
            {order.status ?? "Placed"}
          </Badge>
        </div>
      </div>
    </DetailDrawer>
  );
}
