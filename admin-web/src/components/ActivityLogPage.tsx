import { useMemo } from "react";
import { History } from "lucide-react";
import { useCollection } from "../hooks/useCollection";
import type { AdminActivityLogEntry } from "../types";
import { formatDateTime } from "../lib/format";

interface Props {
  search: string;
}

/**
 * Newest-first feed of every mutating admin action (see src/lib/activityLog.ts
 * for the write side). Read-only by design — the append-only audit trail is
 * meant to be looked at, not edited, which firestore.rules enforces too.
 */
export default function ActivityLogPage({ search }: Props) {
  const { data: entries, loading, error } = useCollection<AdminActivityLogEntry>(
    "admin_activity_log",
    { orderByField: "timestamp", direction: "desc", limit: 200 },
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return entries;
    return entries.filter((e) => {
      const haystack = `${e.action ?? ""} ${e.adminEmail ?? ""} ${e.targetCollection ?? ""} ${e.targetId ?? ""}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [entries, search]);

  if (loading) return <p className="text-sm text-apple-secondary">Loading activity…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <h2 className="text-2xl font-bold text-apple-text tracking-tight">
        Activity Log ({filtered.length})
      </h2>
      <p className="text-xs text-apple-secondary">
        Every verify/edit/delete/status-change action taken from this admin panel is recorded
        here, newest first. Append-only — entries can't be edited or deleted from the UI.
      </p>

      <div className="bg-apple-surface rounded-2xl border border-black/5 shadow-card overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-apple-secondary border-b border-black/5">
              <th className="px-4 py-3">When</th>
              <th className="px-4 py-3">Admin</th>
              <th className="px-4 py-3">Action</th>
              <th className="px-4 py-3">Target</th>
              <th className="px-4 py-3">Details</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((entry) => (
              <tr key={entry.id} className="border-b border-black/5 last:border-0 align-top">
                <td className="px-4 py-3 text-apple-secondary whitespace-nowrap">
                  {formatDateTime(entry.timestamp)}
                </td>
                <td className="px-4 py-3 text-apple-secondary">{entry.adminEmail ?? "–"}</td>
                <td className="px-4 py-3 font-medium text-apple-text">{entry.action ?? "–"}</td>
                <td className="px-4 py-3 text-apple-secondary">
                  {entry.targetCollection}
                  {entry.targetId ? ` / ${entry.targetId.slice(0, 10)}…` : ""}
                </td>
                <td className="px-4 py-3 text-apple-secondary text-xs max-w-xs truncate">
                  {entry.details ? JSON.stringify(entry.details) : ""}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <div className="px-4 py-8 text-center">
            <History className="mx-auto mb-2 text-apple-secondary" size={24} />
            <p className="text-sm text-apple-secondary">No admin activity recorded yet.</p>
          </div>
        )}
      </div>
    </div>
  );
}
