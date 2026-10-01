import { useMemo, useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Order } from "../types";
import { ORDER_STATUSES } from "../types";

function formatCurrency(n: number) {
  return `৳${n.toLocaleString(undefined, { maximumFractionDigits: 0 })}`;
}

export default function OrdersPage() {
  const { data: orders, loading, error } = useCollection<Order>("orders");
  const { data: chefs } = useCollection<Chef>("chefs");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [chefFilter, setChefFilter] = useState<string>("all");
  const [busyId, setBusyId] = useState<string | null>(null);

  const chefName = (chefId?: string) =>
    chefs.find((c) => c.id === chefId)?.kitchenName ?? chefId ?? "(unknown chef)";

  const filtered = useMemo(() => {
    return orders.filter((o) => {
      if (statusFilter !== "all" && o.status !== statusFilter) return false;
      if (chefFilter !== "all" && o.chefId !== chefFilter) return false;
      return true;
    });
  }, [orders, statusFilter, chefFilter]);

  const totalRevenue = useMemo(
    () => filtered.reduce((sum, o) => sum + (o.totalPrice ?? 0), 0),
    [filtered],
  );

  async function changeStatus(orderId: string, status: string) {
    setBusyId(orderId);
    try {
      await updateDoc(doc(db, "orders", orderId), { status });
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-slate-400">Loading orders…</p>;
  if (error) return <p className="text-sm text-red-600">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="text-lg font-semibold text-slate-900">
          Orders ({filtered.length}) · {formatCurrency(totalRevenue)}
        </h2>
        <div className="flex gap-2">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="text-sm border border-slate-300 rounded px-2 py-1"
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
            className="text-sm border border-slate-300 rounded px-2 py-1"
          >
            <option value="all">All chefs</option>
            {chefs.map((c) => (
              <option key={c.id} value={c.id}>
                {c.kitchenName ?? c.id}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
              <th className="px-4 py-2">Order</th>
              <th className="px-4 py-2">Chef</th>
              <th className="px-4 py-2">Items</th>
              <th className="px-4 py-2">Total</th>
              <th className="px-4 py-2">Placed</th>
              <th className="px-4 py-2">Status</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((order) => (
              <tr key={order.id} className="border-b border-slate-50 last:border-0 align-top">
                <td className="px-4 py-2 text-xs text-slate-400">{order.id}</td>
                <td className="px-4 py-2 text-slate-600">{chefName(order.chefId)}</td>
                <td className="px-4 py-2 text-slate-500">
                  {(order.items ?? []).map((it, idx) => (
                    <div key={idx}>
                      {it.quantity}× {it.mealName}
                    </div>
                  ))}
                </td>
                <td className="px-4 py-2 font-medium">{formatCurrency(order.totalPrice ?? 0)}</td>
                <td className="px-4 py-2 text-slate-500">
                  {order.placedAt ? new Date(order.placedAt).toLocaleString() : "–"}
                </td>
                <td className="px-4 py-2">
                  <select
                    value={order.status ?? "Placed"}
                    disabled={busyId === order.id}
                    onChange={(e) => changeStatus(order.id, e.target.value)}
                    className="text-xs border border-slate-300 rounded px-2 py-1"
                  >
                    {ORDER_STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-slate-400 px-4 py-6">No orders match these filters.</p>
        )}
      </div>
    </div>
  );
}
