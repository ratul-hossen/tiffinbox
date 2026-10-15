import { useState } from "react";
import { collection, doc, getDocs, query, serverTimestamp, setDoc, where } from "firebase/firestore";
import { ShieldCheck, Shield, Trash2, UserPlus } from "lucide-react";
import { auth, db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { softDelete } from "../lib/recycleBin";
import type { Admin } from "../types";

interface Props {
  isSuperAdmin: boolean;
}

function formatAddedAt(value: Admin["addedAt"]): string {
  if (!value) return "—";
  if (typeof value === "number") return new Date(value).toLocaleDateString();
  if (typeof value === "object" && "toDate" in value && value.toDate) {
    return value.toDate().toLocaleDateString();
  }
  return "—";
}

export default function AdminsPage({ isSuperAdmin }: Props) {
  const { data: admins, loading, error } = useCollection<Admin>("admins");
  const [email, setEmail] = useState("");
  const [adding, setAdding] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const currentUid = auth.currentUser?.uid;

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = email.trim().toLowerCase();
    if (!trimmed) return;

    setAdding(true);
    setFormError(null);
    try {
      const snap = await getDocs(query(collection(db, "users"), where("email", "==", trimmed)));
      if (snap.empty) {
        setFormError(
          "No account found with that email. The person must sign in to the TiffinBox app at least once first.",
        );
        return;
      }

      const targetUid = snap.docs[0].id;
      if (admins.some((a) => a.id === targetUid)) {
        setFormError("This person is already an admin.");
        return;
      }

      await setDoc(doc(db, "admins", targetUid), {
        email: trimmed,
        role: "admin",
        addedAt: serverTimestamp(),
      });
      await logActivity("add_admin", "admins", targetUid, { email: trimmed });
      setEmail("");
    } catch (e) {
      setFormError(e instanceof Error ? e.message : "Failed to add admin.");
    } finally {
      setAdding(false);
    }
  }

  async function handleRemove(admin: Admin) {
    if (admin.role === "super_admin" || admin.id === currentUid) return;
    if (
      !window.confirm(
        `Remove ${admin.email ?? admin.id} as admin? It'll move to the Recycle Bin, not disappear forever.`,
      )
    ) {
      return;
    }

    setRemovingId(admin.id);
    try {
      await softDelete("admins", admin.id, admin, admin.email ?? admin.id);
      await logActivity("remove_admin (moved to recycle bin)", "admins", admin.id, { email: admin.email ?? null });
    } catch (e) {
      alert(e instanceof Error ? e.message : "Failed to remove admin.");
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <div className="space-y-6">
      {isSuperAdmin && (
        <div className="bg-apple-surface rounded-2xl shadow-card border border-apple-border p-5">
          <h2 className="text-sm font-semibold text-apple-text mb-3 flex items-center gap-2">
            <UserPlus size={16} /> Add an admin
          </h2>
          <form onSubmit={handleAdd} className="flex flex-col sm:flex-row gap-2">
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="person@example.com"
              className="flex-1 rounded-xl border border-apple-border2 bg-apple-surface2 px-3.5 py-2.5 text-sm text-apple-text placeholder:text-apple-secondary/70 focus:outline-none focus:ring-2 focus:ring-apple-blue/40"
            />
            <button
              type="submit"
              disabled={adding}
              className="bg-apple-blue hover:brightness-95 disabled:opacity-60 text-white rounded-xl px-4 py-2.5 text-sm font-medium transition-all duration-150 whitespace-nowrap"
            >
              {adding ? "Adding…" : "Add admin"}
            </button>
          </form>
          <p className="text-xs text-apple-secondary mt-2">
            They must already have a TiffinBox account (signed in at least once).
          </p>
          {formError && (
            <div className="text-sm text-apple-red bg-apple-red/10 border border-apple-red/20 rounded-xl px-3 py-2 mt-3">
              {formError}
            </div>
          )}
        </div>
      )}

      <div className="bg-apple-surface rounded-2xl shadow-card border border-apple-border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-apple-subtle text-apple-secondary text-xs uppercase tracking-wide">
            <tr>
              <th className="text-left px-5 py-3 font-medium">Email</th>
              <th className="text-left px-5 py-3 font-medium">Role</th>
              <th className="text-left px-5 py-3 font-medium">Added</th>
              {isSuperAdmin && <th className="text-right px-5 py-3 font-medium">Actions</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-apple-border">
            {admins.map((admin) => {
              const isSuper = admin.role === "super_admin";
              return (
                <tr key={admin.id}>
                  <td className="px-5 py-3 text-apple-text font-medium">
                    {admin.email ?? admin.id}
                  </td>
                  <td className="px-5 py-3">
                    <span
                      className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${
                        isSuper
                          ? "bg-apple-amber/15 text-apple-amber"
                          : "bg-apple-hover text-apple-secondary"
                      }`}
                    >
                      {isSuper ? <ShieldCheck size={13} /> : <Shield size={13} />}
                      {isSuper ? "Super Admin" : "Admin"}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-apple-secondary">{formatAddedAt(admin.addedAt)}</td>
                  {isSuperAdmin && (
                    <td className="px-5 py-3 text-right">
                      {!isSuper && admin.id !== currentUid && (
                        <button
                          onClick={() => handleRemove(admin)}
                          disabled={removingId === admin.id}
                          className="inline-flex items-center gap-1 text-apple-red hover:underline disabled:opacity-50 text-xs font-medium"
                        >
                          <Trash2 size={13} />
                          {removingId === admin.id ? "Removing…" : "Remove"}
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
        {loading && <p className="text-sm text-apple-secondary p-5">Loading…</p>}
        {!loading && admins.length === 0 && (
          <p className="text-sm text-apple-secondary p-5">No admins found.</p>
        )}
        {error && <p className="text-sm text-apple-red p-5">{error}</p>}
      </div>
    </div>
  );
}
