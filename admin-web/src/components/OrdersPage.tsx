import { useEffect, useMemo, useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { Download, Trash2 } from "lucide-react";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Order } from "../types";
import { ORDER_STATUSES } from "../types";
import { logActivity } from "../lib/activityLog";
import { softDelete } from "../lib/recycleBin";
import { formatCurrency, formatDateTime } from "../lib/format";
import { STATUS_BADGE_TONE } from "../lib/orderStatus";
import Badge from "./ui/Badge";
import OrderDetail from "./OrderDetail";

const ORDER_ACCEPT_TIMEOUT_MS = 12 * 60 * 60 * 1000;

interface Props {
  search: string;
}

function downloadCsv(filename: string, rows: string[][]) {
  const csv = rows
    .map((row) =>
      row
        .map((cell) => {
          const s = String(cell ?? "");
          return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
        })
        .join(","),
    )
    .join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export default function OrdersPage({ search }: Props) {
  const { data: orders, loading, error } = useCollection<Order>("orders");
  const { data: chefs } = useCollection<Chef>("chefs");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [chefFilter, setChefFilter] = useState<string>("all");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [detailOrderId, setDetailOrderId] = useState<string | null>(null);

  // Same 12h rule as the Android app (FirestoreOrderRepository.ORDER_ACCEPT_TIMEOUT_MS):
  // a still-"Placed" order the chef never accepted is cancelled automatically.
  // There are no Cloud Functions on the free plan, so whichever client reads
  // it first (app or this page) performs the flip.
  useEffect(() => {
    const now = Date.now();
    orders
      .filter((o) => (o.status ?? "Placed") === "Placed" && o.placedAt && now - o.placedAt > ORDER_ACCEPT_TIMEOUT_MS)
      .forEach((o) => {
        updateDoc(doc(db, "orders", o.id), {
          status: "Cancelled",
          cancelReason: "auto_expired",
          cancelledAt: now,
        }).catch(() => {});
      });
  }, [orders]);

  const chefName = (chefId?: string) =>
    chefs.find((c) => c.id === chefId)?.kitchenName ?? chefId ?? "(unknown chef)";

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return orders.filter((o) => {
      if (statusFilter !== "all" && o.status !== statusFilter) return false;
      if (chefFilter !== "all" && o.chefId !== chefFilter) return false;
      if (q) {
        const haystack = `${o.id} ${o.customerId ?? ""} ${chefName(o.chefId)}`.toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [orders, statusFilter, chefFilter, search, chefs]);

  const totalRevenue = useMemo(
    () => filtered.reduce((sum, o) => sum + (o.totalPrice ?? 0), 0),
    [filtered],
  );

  // Cancelling an order moves it to the Recycle Bin (same as every other
  // "remove" action in admin-web) rather than just flipping its status -
  // it disappears from this list immediately, and can be restored or
  // permanently deleted from the Recycle Bin page later.
  async function changeStatus(order: Order, status: string) {
    setBusyId(order.id);
    try {
      if (status === "Cancelled") {
        await softDelete("orders", order.id, { ...order, status }, `Order ${order.id.slice(0, 8)}…`);
        await logActivity("Cancelled order (moved to recycle bin)", "orders", order.id);
      } else {
        await updateDoc(doc(db, "orders", order.id), { status });
        await logActivity("Changed order status", "orders", order.id, { status });
      }
    } finally {
      setBusyId(null);
    }
  }

  /** Removes one order: snapshot to the Recycle Bin, then delete. */
  async function removeOrder(order: Order) {
    await softDelete("orders", order.id, order, `Order ${order.id.slice(0, 8)}…`);
    await logActivity("Removed order (moved to recycle bin)", "orders", order.id, { status: order.status ?? "" });
  }

  const cancelledOrders = useMemo(() => orders.filter((o) => o.status === "Cancelled"), [orders]);

  /** One click to clear every already-cancelled order into the Recycle Bin. */
  async function removeAllCancelled() {
    if (cancelledOrders.length === 0) return;
    if (!confirm(`Move all ${cancelledOrders.length} cancelled order(s) to the Recycle Bin?`)) return;
    setBusyId("__bulk__");
    try {
      for (const o of cancelledOrders) await removeOrder(o);
    } catch (err) {
      alert(
        (err instanceof Error ? err.message : "Failed to remove orders.") +
          "\n\nIf this says 'Missing or insufficient permissions', publish the latest firestore.rules in Firebase Console.",
      );
    } finally {
      setBusyId(null);
    }
  }

  function exportCsv() {
    const header = ["Order ID", "Chef", "Customer ID", "Items", "Total", "Status", "Placed At"];
    const rows = filtered.map((o) => [
      o.id,
      chefName(o.chefId),
      o.customerId ?? "",
      (o.items ?? []).map((it) => `${it.quantity}x ${it.mealName}`).join("; "),
      String(o.totalPrice ?? 0),
      o.status ?? "Placed",
      o.placedAt ? new Date(o.placedAt).toISOString() : "",
    ]);
    downloadCsv(`tiffinbox-orders-${new Date().toISOString().slice(0, 10)}.csv`, [header, ...rows]);
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading orders…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  const detailOrder = orders.find((o) => o.id === detailOrderId) ?? null;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <p className="text-sm text-apple-secondary">
          {filtered.length} orders · {formatCurrency(totalRevenue)}
        </p>
        <div className="flex gap-2 items-center">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
          >
            <option value="all">All statuses</option>
            {ORDER_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <select
            value={chefFilter}
            onChange={(e) => setChefFilter(e.target.value)}
            className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
          >
            <option value="all">All chefs</option>
            {chefs.map((c) => (
              <option key={c.id} value={c.id}>
                {c.kitchenName ?? c.id}
              </option>
            ))}
          </select>
          {cancelledOrders.length > 0 && (
            <button
              onClick={removeAllCancelled}
              disabled={busyId === "__bulk__"}
              className="flex items-center gap-1.5 text-sm border border-apple-red/40 text-apple-red hover:bg-apple-red/10 rounded-lg px-3 py-1.5 font-medium disabled:opacity-50 transition-colors duration-150"
            >
              <Trash2 size={14} /> {busyId === "__bulk__" ? "Removing…" : `Remove cancelled (${cancelledOrders.length})`}
            </button>
          )}
          <button
            onClick={exportCsv}
            className="flex items-center gap-1.5 text-sm bg-apple-blue hover:bg-apple-blue-dark text-white rounded-lg px-3 py-1.5 font-medium transition-colors duration-150"
          >
            <Download size={14} /> Export CSV
          </button>
        </div>
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-apple-secondary border-b border-apple-border">
              <th className="px-4 py-3">Order</th>
              <th className="px-4 py-3">Chef</th>
              <th className="px-4 py-3">Items</th>
              <th className="px-4 py-3">Total</th>
              <th className="px-4 py-3">Placed</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((order) => (
              <tr key={order.id} className="border-b border-apple-border last:border-0 align-top">
                <td className="px-4 py-3 text-xs text-apple-secondary/70">{order.id.slice(0, 8)}…</td>
                <td className="px-4 py-3 text-apple-secondary">{chefName(order.chefId)}</td>
                <td className="px-4 py-3 text-apple-secondary">
                  {(order.items ?? []).map((it, idx) => (
                    <div key={idx}>
                      {it.quantity}× {it.mealName}
                    </div>
                  ))}
                </td>
                <td className="px-4 py-3 font-medium">{formatCurrency(order.totalPrice ?? 0)}</td>
                <td className="px-4 py-3 text-apple-secondary">{formatDateTime(order.placedAt)}</td>
                <td className="px-4 py-3">
                  <select
                    value={order.status ?? "Placed"}
                    disabled={busyId === order.id}
                    onChange={(e) => changeStatus(order, e.target.value)}
                    className="text-xs border border-apple-border2 rounded-lg px-2 py-1 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
                  >
                    {ORDER_STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                  <div className="mt-1">
                    <Badge
                      tone={STATUS_BADGE_TONE[(order.status as keyof typeof STATUS_BADGE_TONE) ?? "Placed"] ?? "neutral"}
                    >
                      {order.status ?? "Placed"}
                    </Badge>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => setDetailOrderId(order.id)}
                    className="text-xs text-apple-blue hover:text-apple-blue-dark font-medium transition-colors duration-150"
                  >
                    Details
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-apple-secondary px-4 py-6">No orders match these filters.</p>
        )}
      </div>

      <OrderDetail
        order={detailOrder}
        chefName={chefName(detailOrder?.chefId)}
        onClose={() => setDetailOrderId(null)}
        onRemove={removeOrder}
      />
    </div>
  );
}
