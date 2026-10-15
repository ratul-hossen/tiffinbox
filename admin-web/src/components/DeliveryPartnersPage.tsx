import { useState } from "react";
import { createUserWithEmailAndPassword, signOut as signOutSecondary } from "firebase/auth";
import { doc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { Bike, Trash2, UserPlus } from "lucide-react";
import { db, secondaryAuth } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { softDelete } from "../lib/recycleBin";
import type { DeliveryPartner } from "../types";

/**
 * Delivery partners have no self-signup - an admin creates their login here
 * (via the secondaryAuth app instance in firebase.ts, so it doesn't disturb
 * the admin's own session), then shares the email/password with them. This
 * only provisions the account; the actual delivery-partner app (job list,
 * accept/decline, navigation) is a separate, not-yet-built project - this
 * page is just the account-management groundwork for it.
 */
export default function DeliveryPartnersPage() {
  const { data: partners, loading, error } = useCollection<DeliveryPartner>("delivery_partners");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    if (!name.trim() || !email.trim() || password.length < 6) {
      setFormError("Name, email, and a password of at least 6 characters are required.");
      return;
    }

    setCreating(true);
    try {
      const credential = await createUserWithEmailAndPassword(secondaryAuth, email.trim(), password);
      const uid = credential.user.uid;
      await setDoc(doc(db, "delivery_partners", uid), {
        name: name.trim(),
        phone: phone.trim(),
        email: email.trim().toLowerCase(),
        isActive: true,
        createdAt: serverTimestamp(),
      });
      await logActivity("Created delivery partner", "delivery_partners", uid, { email: email.trim() });
      // The secondary instance just auto-signed itself in as the account it
      // created; sign it back out so that session doesn't linger.
      await signOutSecondary(secondaryAuth);
      setName("");
      setPhone("");
      setEmail("");
      setPassword("");
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to create delivery partner.");
    } finally {
      setCreating(false);
    }
  }

  async function toggleActive(partner: DeliveryPartner) {
    setBusyId(partner.id);
    try {
      const next = !partner.isActive;
      await updateDoc(doc(db, "delivery_partners", partner.id), { isActive: next });
      await logActivity("Toggled delivery partner active", "delivery_partners", partner.id, { isActive: next });
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(partner: DeliveryPartner) {
    if (
      !confirm(
        `Remove ${partner.name ?? partner.email ?? partner.id} from delivery partners? It'll move to the Recycle Bin, not disappear forever.`,
      )
    ) {
      return;
    }
    setBusyId(partner.id);
    try {
      await softDelete("delivery_partners", partner.id, partner, partner.name ?? partner.email ?? partner.id);
      await logActivity("Deleted delivery partner (moved to recycle bin)", "delivery_partners", partner.id);
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading delivery partners…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  const inputClass =
    "rounded-xl border border-apple-border2 bg-apple-surface2 px-3.5 py-2.5 text-sm text-apple-text placeholder:text-apple-secondary/70 focus:outline-none focus:ring-2 focus:ring-apple-blue/40";

  return (
    <div className="space-y-6">
      <div className="bg-apple-surface rounded-2xl shadow-card border border-apple-border p-5">
        <h2 className="text-sm font-semibold text-apple-text mb-3 flex items-center gap-2">
          <UserPlus size={16} /> Add a delivery partner
        </h2>
        <form onSubmit={handleCreate} className="grid gap-2 sm:grid-cols-2">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Full name"
            className={inputClass}
          />
          <input
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="Phone / WhatsApp number"
            className={inputClass}
          />
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Email"
            className={inputClass}
          />
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Temporary password (6+ characters)"
            className={inputClass}
          />
          <button
            type="submit"
            disabled={creating}
            className="sm:col-span-2 bg-apple-blue hover:brightness-95 disabled:opacity-60 text-white rounded-xl px-4 py-2.5 text-sm font-medium transition-all duration-150"
          >
            {creating ? "Creating…" : "Create delivery partner"}
          </button>
        </form>
        <p className="text-xs text-apple-secondary mt-2">
          Delivery partners can&apos;t sign themselves up. Share this email and password with them directly.
        </p>
        {formError && (
          <div className="text-sm text-apple-red bg-apple-red/10 border border-apple-red/20 rounded-xl px-3 py-2 mt-3">
            {formError}
          </div>
        )}
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-apple-subtle text-apple-secondary text-xs uppercase tracking-wide">
            <tr>
              <th className="text-left px-5 py-3 font-medium">Name</th>
              <th className="text-left px-5 py-3 font-medium">Email</th>
              <th className="text-left px-5 py-3 font-medium">Phone</th>
              <th className="text-left px-5 py-3 font-medium">Status</th>
              <th className="text-right px-5 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-apple-border">
            {partners.map((partner) => (
              <tr key={partner.id}>
                <td className="px-5 py-3 text-apple-text font-medium flex items-center gap-2">
                  <Bike size={14} className="text-apple-secondary" />
                  {partner.name ?? "(no name)"}
                </td>
                <td className="px-5 py-3 text-apple-secondary">{partner.email ?? "–"}</td>
                <td className="px-5 py-3 text-apple-secondary">{partner.phone ?? "–"}</td>
                <td className="px-5 py-3">
                  <button
                    onClick={() => toggleActive(partner)}
                    disabled={busyId === partner.id}
                    className={`text-xs font-medium px-2.5 py-1 rounded-full transition-colors duration-150 ${
                      partner.isActive
                        ? "bg-apple-blue/10 text-apple-blue"
                        : "bg-apple-hover text-apple-secondary"
                    }`}
                  >
                    {partner.isActive ? "Active" : "Inactive"}
                  </button>
                </td>
                <td className="px-5 py-3 text-right">
                  <button
                    onClick={() => handleDelete(partner)}
                    disabled={busyId === partner.id}
                    className="inline-flex items-center gap-1 text-apple-red hover:underline disabled:opacity-50 text-xs font-medium"
                  >
                    <Trash2 size={13} />
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {partners.length === 0 && (
          <p className="text-sm text-apple-secondary p-5">No delivery partners yet.</p>
        )}
      </div>
    </div>
  );
}
