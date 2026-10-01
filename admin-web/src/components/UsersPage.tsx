import { useCollection } from "../hooks/useCollection";
import type { AppUser } from "../types";

export default function UsersPage() {
  const { data: users, loading, error } = useCollection<AppUser>("users");

  if (loading) return <p className="text-sm text-slate-400">Loading users…</p>;
  if (error) return <p className="text-sm text-red-600">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">Customers / Users ({users.length})</h2>

      <p className="text-xs text-slate-400">
        The <code>users</code> collection only started being populated via the
        new signup flow, so older accounts may not show up here — that's
        expected, not a bug.
      </p>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
              <th className="px-4 py-2">Name</th>
              <th className="px-4 py-2">Email</th>
              <th className="px-4 py-2">Role</th>
              <th className="px-4 py-2">Created</th>
              <th className="px-4 py-2">UID</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-b border-slate-50 last:border-0">
                <td className="px-4 py-2">{u.name ?? "–"}</td>
                <td className="px-4 py-2 text-slate-500">{u.email ?? "–"}</td>
                <td className="px-4 py-2">
                  <span className="text-xs bg-slate-100 rounded-full px-2 py-0.5">
                    {u.role ?? "unknown"}
                  </span>
                </td>
                <td className="px-4 py-2 text-slate-500">
                  {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : "–"}
                </td>
                <td className="px-4 py-2 text-slate-400 text-xs">{u.id}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {users.length === 0 && (
          <p className="text-sm text-slate-400 px-4 py-6">
            No users in Firestore yet — this is expected until the new signup
            flow has run for existing accounts.
          </p>
        )}
      </div>
    </div>
  );
}
