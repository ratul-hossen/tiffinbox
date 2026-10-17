import { useMemo, useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { Check, Ban, RotateCcw, Trash2 } from "lucide-react";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Subscription } from "../types";
import { logActivity } from "../lib/activityLog";
import { softDelete } from "../lib/recycleBin";
import { formatCurrency, formatDate } from "../lib/format";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

export default function SubscriptionsPage({ search }: Props) {
  const { data: subscriptions, loading, error } = useCollection<Subscription>("subscriptions");
  const [activeFilter, setActiveFilter] = useState<"all" | "active" | "pending" | "inactive">("all");
  const [busyId, setBusyId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return subscriptions.filter((s) => {
      const isPending = s.status === "Pending Chef Approval" || s.isApprovedByChef === false;
      if (activeFilter === "active" && (!s.isActive || isPending)) return false;
      if (activeFilter === "pending" && !isPending) return false;
      if (activeFilter === "inactive" && (s.isActive || isPending)) return false;
      if (q) {
        const haystack = `${s.chefName ?? ""} ${s.chefId ?? ""} ${s.customerName ?? ""} ${s.customerPhone ?? ""} ${s.deliveryAddress ?? ""} ${s.paymentMethod ?? ""} ${s.paymentTransactionId ?? ""} ${s.plan ?? ""} ${s.scheduleSummary ?? ""} ${s.status ?? ""} ${s.id}`.toLowerCase();
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
      await updateDoc(doc(db, "subscriptions", subId), {
        isActive: makeActive,
        isApprovedByChef: makeActive,
        status: makeActive ? "Active" : "Cancelled"
      });
      await logActivity(`${actionLabel}led subscription`, "subscriptions", subId);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to update subscription status.");
    } finally {
      setBusyId(null);
    }
  }

  async function rejectSubscription(subId: string) {
    if (!confirm("Reject and cancel this subscription request?")) return;
    setBusyId(subId);
    try {
      await updateDoc(doc(db, "subscriptions", subId), {
        isActive: false,
        isApprovedByChef: false,
        status: "Cancelled"
      });
      await logActivity("Rejected subscription request", "subscriptions", subId);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to reject subscription.");
    } finally {
      setBusyId(null);
    }
  }

  async function approveSubscription(subId: string) {
    if (!confirm("Approve and activate this subscription?")) return;
    setBusyId(subId);
    try {
      await updateDoc(doc(db, "subscriptions", subId), {
        isActive: true,
        isApprovedByChef: true,
        status: "Active"
      });
      await logActivity("Approved custom subscription", "subscriptions", subId);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to approve subscription.");
    } finally {
      setBusyId(null);
    }
  }

  /** Auto-cancels the subscription and moves it to the Recycle Bin */
  async function removeSubscription(sub: Subscription) {
    const customer = sub.customerName || sub.customerId || "customer";
    if (!confirm(`Delete subscription for ${customer}? It will be auto-cancelled and moved to the Recycle Bin.`)) return;
    setBusyId(sub.id);
    try {
      const cancelledSub = {
        ...sub,
        isActive: false,
        isApprovedByChef: false,
        status: "Cancelled"
      };
      await softDelete(
        "subscriptions",
        sub.id,
        cancelledSub,
        `Subscription: ${sub.customerName || sub.customerId || sub.id} (${sub.plan ?? "Plan"})`
      );
      await logActivity("Deleted subscription (auto-cancelled and moved to recycle bin)", "subscriptions", sub.id);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to delete subscription.");
    } finally {
      setBusyId(null);
    }
  }

  const cancelledSubscriptions = useMemo(
    () => subscriptions.filter((s) => s.status === "Cancelled" || (!s.isActive && s.isApprovedByChef !== false && s.status !== "Pending Chef Approval")),
    [subscriptions]
  );

  async function removeAllCancelled() {
    if (cancelledSubscriptions.length === 0) return;
    if (!confirm(`Move all ${cancelledSubscriptions.length} cancelled subscription(s) to the Recycle Bin?`)) return;
    setBusyId("__bulk__");
    try {
      for (const s of cancelledSubscriptions) {
        await softDelete(
          "subscriptions",
          s.id,
          { ...s, isActive: false, isApprovedByChef: false, status: "Cancelled" },
          `Subscription: ${s.customerName || s.customerId || s.id} (${s.plan ?? "Plan"})`
        );
      }
      await logActivity(`Moved ${cancelledSubscriptions.length} cancelled subscriptions to recycle bin`, "subscriptions", "__bulk__");
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to clear cancelled subscriptions.");
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
        <div className="flex items-center gap-2">
          {cancelledSubscriptions.length > 0 && (
            <button
              onClick={removeAllCancelled}
              disabled={busyId === "__bulk__"}
              className="text-xs text-apple-red border border-apple-red/30 hover:bg-apple-red/10 rounded-lg px-2.5 py-1.5 transition-colors duration-150 inline-flex items-center gap-1 disabled:opacity-50"
            >
              <Trash2 size={13} /> Clear cancelled ({cancelledSubscriptions.length})
            </button>
          )}
          <select
            value={activeFilter}
            onChange={(e) => setActiveFilter(e.target.value as "all" | "active" | "pending" | "inactive")}
            className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
          >
            <option value="all">All</option>
            <option value="active">Active</option>
            <option value="pending">Pending Approval</option>
            <option value="inactive">Inactive / Cancelled</option>
          </select>
        </div>
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
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((sub) => {
              const isPending = sub.status === "Pending Chef Approval" || sub.isApprovedByChef === false;
              return (
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
                    {isPending ? (
                      <Badge tone="amber">Pending Approval</Badge>
                    ) : (
                      <Badge tone={sub.isActive ? "green" : "neutral"}>
                        {sub.isActive ? "Active" : "Cancelled"}
                      </Badge>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-3 text-xs font-medium">
                      {isPending ? (
                        <>
                          <button
                            onClick={() => approveSubscription(sub.id)}
                            disabled={busyId === sub.id}
                            className="inline-flex items-center gap-1 text-apple-green hover:underline disabled:opacity-50"
                          >
                            <Check size={13} /> Approve
                          </button>
                          <button
                            onClick={() => rejectSubscription(sub.id)}
                            disabled={busyId === sub.id}
                            className="inline-flex items-center gap-1 text-apple-amber hover:underline disabled:opacity-50"
                          >
                            <Ban size={13} /> Reject
                          </button>
                        </>
                      ) : sub.isActive ? (
                        <button
                          onClick={() => toggleStatus(sub.id, false)}
                          disabled={busyId === sub.id}
                          className="inline-flex items-center gap-1 text-apple-amber hover:underline disabled:opacity-50"
                        >
                          <Ban size={13} /> Cancel
                        </button>
                      ) : (
                        <button
                          onClick={() => toggleStatus(sub.id, true)}
                          disabled={busyId === sub.id}
                          className="inline-flex items-center gap-1 text-apple-blue hover:underline disabled:opacity-50"
                        >
                          <RotateCcw size={13} /> Reactivate
                        </button>
                      )}

                      <button
                        onClick={() => removeSubscription(sub)}
                        disabled={busyId === sub.id}
                        className="inline-flex items-center gap-1 text-apple-red hover:underline disabled:opacity-50"
                        title="Delete and move to Recycle Bin"
                      >
                        <Trash2 size={13} /> Delete
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-apple-secondary px-4 py-6">No subscriptions match this filter.</p>
        )}
      </div>
    </div>
  );
}
