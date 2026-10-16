import { useMemo, useState } from "react";
import { RotateCcw, Trash2 } from "lucide-react";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { permanentlyDelete, restoreFromRecycleBin } from "../lib/recycleBin";
import type { RecycleBinEntry } from "../types";
import { formatDateTime } from "../lib/format";
import Badge from "./ui/Badge";

interface Props {
  search: string;
  isSuperAdmin: boolean;
}

const COLLECTION_LABEL: Record<string, string> = {
  chefs: "Chef",
  meals: "Meal",
  reviews: "Review",
  delivery_partners: "Delivery Partner",
  admins: "Admin",
  users: "User",
  orders: "Order (Cancelled)",
  coupons: "Coupon",
};

export default function RecycleBinPage({ search, isSuperAdmin }: Props) {
  const { data: entries, loading, error } = useCollection<RecycleBinEntry>("recycle_bin", {
    orderByField: "deletedAt",
    direction: "desc",
  });
  const [busyId, setBusyId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return entries;
    return entries.filter((e) =>
      `${e.label ?? ""} ${e.sourceCollection} ${e.deletedByEmail ?? ""}`.toLowerCase().includes(q),
    );
  }, [entries, search]);

  async function handleRestore(entry: RecycleBinEntry) {
    setBusyId(entry.id);
    try {
      await restoreFromRecycleBin(entry);
      await logActivity("Restored from recycle bin", entry.sourceCollection, entry.sourceId, {
        label: entry.label ?? null,
      });
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to restore.");
    } finally {
      setBusyId(null);
    }
  }

  async function handlePermanentDelete(entry: RecycleBinEntry) {
    if (!confirm(`Permanently delete "${entry.label ?? entry.sourceId}"? This cannot be undone.`)) return;
    setBusyId(entry.id);
    try {
      await permanentlyDelete(entry.id);
      await logActivity("Permanently deleted", entry.sourceCollection, entry.sourceId, {
        label: entry.label ?? null,
      });
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to delete.");
    } finally {
      setBusyId(null);
    }
  }

  // Restoring an admins/{id} entry needs isSuperAdmin() per firestore.rules
  // (admins `create` is super-admin-only) - hide the button for a regular
  // admin rather than let them hit a confusing permission-denied error.
  function canRestore(entry: RecycleBinEntry) {
    return entry.sourceCollection !== "admins" || isSuperAdmin;
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading recycle bin…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <p className="text-sm text-apple-secondary">{filtered.length} deleted items</p>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-apple-subtle text-apple-secondary text-xs uppercase tracking-wide">
            <tr>
              <th className="text-left px-5 py-3 font-medium">Type</th>
              <th className="text-left px-5 py-3 font-medium">Name</th>
              <th className="text-left px-5 py-3 font-medium">Deleted</th>
              <th className="text-left px-5 py-3 font-medium">Deleted by</th>
              <th className="text-right px-5 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-apple-border">
            {filtered.map((entry) => (
              <tr key={entry.id}>
                <td className="px-5 py-3">
                  <Badge tone="neutral">
                    {COLLECTION_LABEL[entry.sourceCollection] ?? entry.sourceCollection}
                  </Badge>
                </td>
                <td className="px-5 py-3 text-apple-text font-medium">
                  {entry.label || entry.sourceId}
                </td>
                <td className="px-5 py-3 text-apple-secondary">
                  {entry.deletedAt ? formatDateTime(entry.deletedAt) : "—"}
                </td>
                <td className="px-5 py-3 text-apple-secondary">{entry.deletedByEmail ?? "—"}</td>
                <td className="px-5 py-3">
                  <div className="flex justify-end gap-3">
                    {canRestore(entry) && (
                      <button
                        onClick={() => handleRestore(entry)}
                        disabled={busyId === entry.id}
                        className="inline-flex items-center gap-1 text-apple-blue hover:underline disabled:opacity-50 text-xs font-medium"
                      >
                        <RotateCcw size={13} /> Restore
                      </button>
                    )}
                    <button
                      onClick={() => handlePermanentDelete(entry)}
                      disabled={busyId === entry.id}
                      className="inline-flex items-center gap-1 text-apple-red hover:underline disabled:opacity-50 text-xs font-medium"
                    >
                      <Trash2 size={13} /> Delete forever
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-apple-secondary p-5">Recycle bin is empty.</p>
        )}
      </div>
    </div>
  );
}
