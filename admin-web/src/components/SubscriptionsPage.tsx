import { useMemo, useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Subscription } from "../types";
import { logActivity } from "../lib/activityLog";
import { formatCurrency, formatDate } from "../lib/format";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

export default function SubscriptionsPage({ search }: Props) {
  const { data: subscriptions, loading, error } = useCollection<Subscription>("subscriptions");
  const [activeFilter, setActiveFilter] = useState<"all" | "active" | "inactive">("all");
  const [busyId, setBusyId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return subscriptions.filter((s) => {
      if (activeFilter === "active" && !s.isActive) return false;
      if (activeFilter === "inactive" && s.isActive) return false;
      if (q) {
        const haystack = `${s.chefName ?? ""} ${s.chefId ?? ""} ${s.customerName ?? ""} ${s.customerPhone ?? ""} ${s.deliveryAddress ?? ""} ${s.paymentMethod ?? ""} ${s.paymentTransactionId ?? ""} ${s.plan ?? ""} ${s.scheduleSummary ?? ""} ${s.id}`.toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [subscriptions, activeFilter, search]);

  async function toggleStatus(subId: string, makeActive: boolean) {
    const actionLabel = makeActive ? "Reactivate" : "Force-cancel";
    if (!confirm(`${actionLabel} this subscription?`)) return;
    setBusyId(subId);
    try {
      await updateDoc(doc(db, "subscriptions", subId), { isActive: makeActive });
      await logActivity(`${actionLabel}led subscription`, "subscriptions", subId);
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading subscriptions…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <p className="text-sm text-apple-secondary">{filtered.length} subscriptions</p>
        <select
          value={activeFilter}
          onChange={(e) => setActiveFilter(e.target.value as "all" | "active" | "inactive")}
          className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
        >
          <option value="all">All</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </select>
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-apple-secondary border-b border-apple-border">
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3">Chef</th>
              <th className="px-4 py-3">Plan</th>
              <th className="px-4 py-3">Meal / Schedule</th>
              <th className="px-4 py-3">Meals/wk</th>
              <th className="px-4 py-3">Total</th>
              <th className="px-4 py-3">Payment</th>
              <th className="px-4 py-3">Started</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((sub) => (
              <tr key={sub.id} className="border-b border-apple-border last:border-0">
                <td className="px-4 py-3">
                  <div className="font-medium text-apple-text">{sub.customerName || sub.customerId || "–"}</div>
                  {sub.customerPhone && <div className="text-xs text-apple-secondary">{sub.customerPhone}</div>}
                  {sub.deliveryAddress && <div className="text-xs text-apple-secondary truncate max-w-xs">{sub.deliveryAddress}</div>}
                </td>
                <td className="px-4 py-3">{sub.chefName ?? sub.chefId}</td>
                <td className="px-4 py-3 text-apple-secondary">{sub.plan}</td>
                <td className="px-4 py-3 text-apple-secondary">
                  <div>{sub.mealType ?? "–"}</div>
                  {sub.scheduleSummary && (
                    <div className="text-[11px] text-apple-secondary font-mono mt-0.5 leading-tight max-w-[200px]" title={sub.scheduleSummary}>
                      🗓️ {sub.scheduleSummary}
                    </div>
                  )}
                </td>
                <td className="px-4 py-3">{sub.mealsPerWeek ?? "–"}</td>
                <td className="px-4 py-3">{formatCurrency(sub.totalPrice ?? 0)}</td>
                <td className="px-4 py-3">
                  <span className="text-xs font-medium text-apple-text">{sub.paymentMethod ?? "COD"}</span>
                  {sub.paymentTransactionId && (
                    <div className="text-xs text-apple-secondary font-mono">{sub.paymentTransactionId}</div>
                  )}
                </td>
                <td className="px-4 py-3 text-apple-secondary">{formatDate(sub.startedAt)}</td>
                <td className="px-4 py-3">
                  <Badge tone={sub.isActive ? "green" : "neutral"}>
                    {sub.isActive ? "Active" : "Cancelled"}
                  </Badge>
                </td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => toggleStatus(sub.id, !sub.isActive)}
                    disabled={busyId === sub.id}
                    className={`text-xs hover:opacity-80 transition-opacity duration-150 ${sub.isActive ? "text-apple-red" : "text-apple-blue font-medium"}`}
                  >
                    {sub.isActive ? "Cancel" : "Reactivate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-apple-secondary px-4 py-6">No subscriptions match this filter.</p>
        )}
      </div>
    </div>
  );
}
