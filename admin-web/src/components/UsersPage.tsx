import { useMemo, useState } from "react";
import { Trash2 } from "lucide-react";
import { useCollection } from "../hooks/useCollection";
import type { AppUser, Order } from "../types";
import { formatDate } from "../lib/format";
import { logActivity } from "../lib/activityLog";
import { softDelete } from "../lib/recycleBin";
import UserDetail from "./UserDetail";

interface Props {
  search: string;
}

export default function UsersPage({ search }: Props) {
  const { data: users, loading, error } = useCollection<AppUser>("users");
  const { data: orders } = useCollection<Order>("orders");
  const [detailUserId, setDetailUserId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function handleRemove(user: AppUser) {
    if (
      !confirm(`Remove ${user.name ?? user.email ?? user.id}? It'll move to the Recycle Bin, not disappear forever.`)
    ) {
      return;
    }
    setBusyId(user.id);
    try {
      await softDelete("users", user.id, user, user.name ?? user.email ?? user.id);
      await logActivity("Removed user (moved to recycle bin)", "users", user.id, { email: user.email ?? null });
    } finally {
      setBusyId(null);
    }
  }

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return users;
    return users.filter(
      (u) =>
        (u.name ?? "").toLowerCase().includes(q) ||
        (u.email ?? "").toLowerCase().includes(q) ||
        u.id.toLowerCase().includes(q),
    );
  }, [users, search]);

  if (loading) return <p className="text-sm text-apple-secondary">Loading users…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  const detailUser = users.find((u) => u.id === detailUserId) ?? null;

  return (
    <div className="space-y-4">
      <p className="text-sm text-apple-secondary">{filtered.length} users</p>

      <p className="text-xs text-apple-secondary">
        The <code className="bg-apple-hover rounded px-1">users</code> collection only started being
        populated via the new signup flow, so older accounts may not show up here — that's
        expected, not a bug.
      </p>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-apple-secondary border-b border-apple-border">
              <th className="px-4 py-3">Name</th>
              <th className="px-4 py-3">Email</th>
              <th className="px-4 py-3">Phone</th>
              <th className="px-4 py-3">WhatsApp</th>
              <th className="px-4 py-3">Role</th>
              <th className="px-4 py-3">Created</th>
              <th className="px-4 py-3">UID</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((u) => (
              <tr key={u.id} className="border-b border-apple-border last:border-0">
                <td className="px-4 py-3">{u.name ?? "–"}</td>
                <td className="px-4 py-3 text-apple-secondary">{u.email ?? "–"}</td>
                <td className="px-4 py-3 text-apple-secondary">{u.phoneNumber || "–"}</td>
                <td className="px-4 py-3 text-apple-secondary">{u.whatsappNumber || "–"}</td>
                <td className="px-4 py-3">
                  <span className="text-xs bg-apple-hover rounded-full px-2 py-0.5">
                    {u.role ?? "unknown"}
                  </span>
                </td>
                <td className="px-4 py-3 text-apple-secondary">{formatDate(u.createdAt)}</td>
                <td className="px-4 py-3 text-apple-secondary/70 text-xs">{u.id.slice(0, 10)}…</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => setDetailUserId(u.id)}
                      className="text-xs text-apple-blue hover:text-apple-blue-dark font-medium transition-colors duration-150"
                    >
                      View details
                    </button>
                    <button
                      onClick={() => handleRemove(u)}
                      disabled={busyId === u.id}
                      className="inline-flex items-center gap-1 text-apple-red hover:underline disabled:opacity-50 text-xs font-medium"
                    >
                      <Trash2 size={13} /> Remove
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-apple-secondary px-4 py-6">
            No users found — this may be expected until the new signup flow has run for existing
            accounts.
          </p>
        )}
      </div>

      <UserDetail user={detailUser} orders={orders} onClose={() => setDetailUserId(null)} />
    </div>
  );
}
