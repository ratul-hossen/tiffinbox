import { useMemo, useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Subscription } from "../types";

export default function SubscriptionsPage() {
  const { data: subscriptions, loading, error } = useCollection<Subscription>("subscriptions");
  const [activeFilter, setActiveFilter] = useState<"all" | "active" | "inactive">("all");
  const [busyId, setBusyId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    if (activeFilter === "active") return subscriptions.filter((s) => s.isActive);
    if (activeFilter === "inactive") return subscriptions.filter((s) => !s.isActive);
    return subscriptions;
  }, [subscriptions, activeFilter]);

  async function forceCancel(subId: string) {
    if (!confirm("Force-cancel this subscription?")) return;
    setBusyId(subId);
    try {
      await updateDoc(doc(db, "subscriptions", subId), { isActive: false });
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-slate-400">Loading subscriptions…</p>;
  if (error) return <p className="text-sm text-red-600">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="text-lg font-semibold text-slate-900">Subscriptions ({filtered.length})</h2>
        <select
          value={activeFilter}
          onChange={(e) => setActiveFilter(e.target.value as "all" | "active" | "inactive")}
          className="text-sm border border-slate-300 rounded px-2 py-1"
        >
          <option value="all">All</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </select>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
              <th className="px-4 py-2">Chef</th>
              <th className="px-4 py-2">Plan</th>
              <th className="px-4 py-2">Meals/wk</th>
              <th className="px-4 py-2">Total</th>
              <th className="px-4 py-2">Started</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((sub) => (
              <tr key={sub.id} className="border-b border-slate-50 last:border-0">
                <td className="px-4 py-2">{sub.chefName ?? sub.chefId}</td>
                <td className="px-4 py-2 text-slate-500">{sub.plan}</td>
                <td className="px-4 py-2">{sub.mealsPerWeek ?? "–"}</td>
                <td className="px-4 py-2">৳{sub.totalPrice ?? 0}</td>
                <td className="px-4 py-2 text-slate-500">
                  {sub.startedAt ? new Date(sub.startedAt).toLocaleDateString() : "–"}
                </td>
                <td className="px-4 py-2">
                  <span
                    className={`text-xs rounded-full px-2 py-1 font-medium ${
                      sub.isActive ? "bg-green-100 text-green-700" : "bg-slate-100 text-slate-600"
                    }`}
                  >
                    {sub.isActive ? "Active" : "Cancelled"}
                  </span>
                </td>
                <td className="px-4 py-2">
                  {sub.isActive && (
                    <button
                      onClick={() => forceCancel(sub.id)}
                      disabled={busyId === sub.id}
                      className="text-xs text-red-600 hover:text-red-800 underline"
                    >
                      Force-cancel
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-slate-400 px-4 py-6">No subscriptions match this filter.</p>
        )}
      </div>
    </div>
  );
}
