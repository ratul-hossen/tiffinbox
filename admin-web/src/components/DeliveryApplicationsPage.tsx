import { useMemo, useState } from "react";
import { addDoc, collection, doc, setDoc, updateDoc } from "firebase/firestore";
import { CheckCircle2, Mail, MapPin, MessageCircle, RotateCcw, XCircle } from "lucide-react";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { formatDateTime } from "../lib/format";
import type { AppUser, Chef, DeliveryApplication, DeliveryApplicationStatus } from "../types";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

const FILTERS: { key: DeliveryApplicationStatus; label: string }[] = [
  { key: "pending", label: "Waiting for review" },
  { key: "approved", label: "Approved" },
  { key: "rejected", label: "Rejected" },
];

const SLOT_LABELS: Record<string, string> = { Breakfast: "Breakfast", Lunch: "Lunch", Dinner: "Dinner" };

function whatsappLink(number?: string) {
  const digits = (number ?? "").replace(/\D/g, "");
  if (!digits) return undefined;
  // Local BD numbers (01XXXXXXXXX) need the 88 country code for wa.me.
  return `https://wa.me/${digits.startsWith("0") ? `88${digits}` : digits}`;
}

function gmailLink(email: string, kitchenName?: string) {
  const subject = encodeURIComponent(`TiffinBox Delivery - your application${kitchenName ? ` (${kitchenName})` : ""}`);
  return `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(email)}&su=${subject}`;
}

/**
 * Chefs who applied (from the app's chef dashboard) to use TiffinBox's own
 * delivery service. Approving switches that chef's deliveryMode to
 * "tiffinbox", which shows customers the "TiffinBox Delivery" badge on the
 * chef's card/profile; rejecting or moving back to waiting switches it back
 * to "self". The actual delivery operation is handled manually for now
 * (contact the chef over WhatsApp / Gmail) until the delivery-partner app
 * is built.
 */
export default function DeliveryApplicationsPage({ search }: Props) {
  const { data: applications, loading, error } = useCollection<DeliveryApplication>("delivery_applications");
  const { data: chefs } = useCollection<Chef>("chefs");
  const { data: users } = useCollection<AppUser>("users");
  const [filter, setFilter] = useState<DeliveryApplicationStatus>("pending");
  const [busyId, setBusyId] = useState<string | null>(null);

  const statusOf = (a: DeliveryApplication): DeliveryApplicationStatus => a.status ?? "pending";

  const counts = useMemo(() => {
    const c: Record<DeliveryApplicationStatus, number> = { pending: 0, approved: 0, rejected: 0 };
    applications.forEach((a) => (c[statusOf(a)] += 1));
    return c;
  }, [applications]);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return applications
      .filter((a) => statusOf(a) === filter)
      .filter((a) =>
        !q
          ? true
          : `${a.kitchenName ?? ""} ${a.ownerName ?? ""} ${a.contactNumber ?? ""} ${a.pickupAddress ?? ""}`
              .toLowerCase()
              .includes(q),
      )
      .sort((x, y) => Number(y.updatedAt ?? 0) - Number(x.updatedAt ?? 0));
  }, [applications, filter, search]);

  async function decide(app: DeliveryApplication, status: DeliveryApplicationStatus) {
    const chefId = app.chefId ?? app.id;
    let adminNote = app.adminNote ?? "";
    if (status !== "pending") {
      const answer = prompt(
        status === "approved"
          ? "Optional message for the chef (shown in their app):"
          : "Reason / message for the chef (optional, shown in their app):",
        adminNote,
      );
      if (answer === null) return; // cancelled
      adminNote = answer.trim();
    }
    setBusyId(app.id);
    try {
      await updateDoc(doc(db, "delivery_applications", app.id), { status, adminNote, reviewedAt: Date.now() });
      // Only an approved application may use TiffinBox delivery (and show the badge).
      if (chefs.some((c) => c.id === chefId)) {
        await setDoc(doc(db, "chefs", chefId), { deliveryMode: status === "approved" ? "tiffinbox" : "self" }, { merge: true });
      }
      if (status !== "pending") {
        await addDoc(collection(db, "notifications", chefId, "items"), {
          title: status === "approved" ? "TiffinBox Delivery approved 🛵" : "TiffinBox Delivery application update",
          body:
            (status === "approved"
              ? "Your kitchen now shows the TiffinBox Delivery badge to customers."
              : "Your TiffinBox Delivery application was not approved this time.") + (adminNote ? ` ${adminNote}` : ""),
          createdAt: Date.now(),
        });
      }
      await logActivity(`Delivery application ${status}`, "delivery_applications", app.id, { adminNote });
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to update application.");
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading applications…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            className={`text-sm rounded-full px-4 py-1.5 transition-colors duration-150 ${
              filter === f.key
                ? "bg-apple-blue text-white font-medium"
                : "bg-apple-surface border border-apple-border text-apple-secondary hover:text-apple-text"
            }`}
          >
            {f.label} <span className="opacity-75">({counts[f.key]})</span>
          </button>
        ))}
      </div>

      {visible.length === 0 && (
        <div className="bg-apple-surface rounded-2xl border border-apple-border p-10 text-center text-sm text-apple-secondary">
          {filter === "pending" ? "No delivery applications waiting right now." : "Nothing here."}
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {visible.map((app) => {
          const chefId = app.chefId ?? app.id;
          const chef = chefs.find((c) => c.id === chefId);
          const email = users.find((u) => u.id === chefId)?.email;
          const busy = busyId === app.id;
          const status = statusOf(app);
          const hasLocation = !!app.latitude && !!app.longitude;
          return (
            <div key={app.id} className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-hidden">
              <div className="flex gap-4 p-5">
                <div className="w-16 h-16 shrink-0 rounded-xl overflow-hidden bg-apple-subtle flex items-center justify-center text-3xl">
                  {chef?.photoBase64 ? (
                    <img src={`data:image/jpeg;base64,${chef.photoBase64}`} alt="" className="w-full h-full object-cover" />
                  ) : (
                    chef?.emoji ?? "🛵"
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="font-semibold text-apple-text truncate">{app.kitchenName || chef?.kitchenName || "(no kitchen name)"}</div>
                      <div className="text-sm text-apple-secondary truncate">{app.ownerName || chef?.ownerName || "—"}</div>
                    </div>
                    <Badge tone={status === "approved" ? "green" : status === "rejected" ? "red" : "blue"}>
                      {status === "approved" ? "Approved" : status === "rejected" ? "Rejected" : "New"}
                    </Badge>
                  </div>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    <Badge tone="neutral">~{app.expectedDailyOrders ?? 0} orders/day</Badge>
                    <Badge tone="neutral">{app.deliveryRadiusKm ?? 0} km radius</Badge>
                    {(app.mealSlots ?? []).map((s) => (
                      <Badge key={s} tone="neutral">
                        {SLOT_LABELS[s] ?? s}
                      </Badge>
                    ))}
                    {chef?.isDemo && <Badge tone="amber">Demo chef</Badge>}
                  </div>
                </div>
              </div>

              <div className="px-5 pb-4 space-y-1.5 text-xs text-apple-secondary">
                <div>
                  Contact: <span className="text-apple-text">{app.contactNumber || "—"}</span>
                </div>
                <div>
                  <MapPin size={11} className="inline mr-1" />
                  Pickup: <span className="text-apple-text">{app.pickupAddress || "—"}</span>
                  {hasLocation && (
                    <a
                      href={`https://www.google.com/maps?q=${app.latitude},${app.longitude}`}
                      target="_blank"
                      rel="noreferrer"
                      className="ml-2 text-apple-blue hover:underline"
                    >
                      Map ↗
                    </a>
                  )}
                </div>
                {app.note && (
                  <div>
                    Note: <span className="text-apple-text">{app.note}</span>
                  </div>
                )}
                {app.adminNote && (
                  <div>
                    Your message: <span className="text-apple-text">{app.adminNote}</span>
                  </div>
                )}
                <div>Applied: {formatDateTime(app.createdAt)}</div>
              </div>

              <div className="px-5 pb-4 flex flex-wrap gap-2">
                {whatsappLink(app.contactNumber) && (
                  <a
                    href={whatsappLink(app.contactNumber)}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1.5 text-xs font-medium rounded-full px-3 py-1.5 bg-apple-green/10 text-apple-green hover:bg-apple-green/20"
                  >
                    <MessageCircle size={13} /> WhatsApp
                  </a>
                )}
                {email && (
                  <a
                    href={gmailLink(email, app.kitchenName)}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1.5 text-xs font-medium rounded-full px-3 py-1.5 bg-apple-blue/10 text-apple-blue hover:bg-apple-blue/20"
                    title={email}
                  >
                    <Mail size={13} /> Gmail · {email}
                  </a>
                )}
              </div>

              <div className="flex border-t border-apple-border text-sm font-medium">
                {status !== "approved" && (
                  <button
                    disabled={busy}
                    onClick={() => decide(app, "approved")}
                    className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-green hover:bg-apple-green/10 disabled:opacity-40"
                  >
                    <CheckCircle2 size={16} /> Approve
                  </button>
                )}
                {status !== "pending" && (
                  <button
                    disabled={busy}
                    onClick={() => decide(app, "pending")}
                    className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-secondary hover:bg-apple-hover border-l border-apple-border first:border-l-0 disabled:opacity-40"
                  >
                    <RotateCcw size={16} /> Back to waiting
                  </button>
                )}
                {status !== "rejected" && (
                  <button
                    disabled={busy}
                    onClick={() => decide(app, "rejected")}
                    className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-red hover:bg-apple-red/10 border-l border-apple-border first:border-l-0 disabled:opacity-40"
                  >
                    <XCircle size={16} /> Reject
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
