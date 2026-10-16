import { useEffect, useMemo, useRef, useState } from "react";
import { addDoc, collection, doc, setDoc, updateDoc } from "firebase/firestore";
import { Ban, CalendarPlus, CheckCircle2, Mail, MapPin, MessageCircle, RotateCcw, XCircle } from "lucide-react";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { formatDate, formatDateTime } from "../lib/format";
import type { AppUser, Chef, DeliveryApplication, DeliveryApplicationStatus } from "../types";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

type Filter = "pending" | "active" | "ended";

const FILTERS: { key: Filter; label: string }[] = [
  { key: "pending", label: "Waiting for review" },
  { key: "active", label: "Active subscriptions" },
  { key: "ended", label: "Ended / rejected" },
];

const DURATIONS = [1, 3, 6, 12];

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

function addMonths(from: number, months: number): number {
  const d = new Date(from);
  d.setMonth(d.getMonth() + months);
  return d.getTime();
}

/** Stored status, with an approved-but-past-validUntil agreement read as "expired". */
function effectiveStatus(a: DeliveryApplication): DeliveryApplicationStatus {
  const status = a.status ?? "pending";
  return status === "approved" && (a.validUntil ?? 0) < Date.now() ? "expired" : status;
}

function filterOf(status: DeliveryApplicationStatus): Filter {
  if (status === "pending") return "pending";
  return status === "approved" ? "active" : "ended";
}

const STATUS_BADGE: Record<DeliveryApplicationStatus, { tone: "green" | "red" | "blue" | "amber" | "neutral"; label: string }> = {
  pending: { tone: "blue", label: "New" },
  approved: { tone: "green", label: "Active" },
  rejected: { tone: "red", label: "Rejected" },
  cancelled: { tone: "red", label: "Cancelled" },
  expired: { tone: "neutral", label: "Expired" },
};

/**
 * TiffinBox Delivery subscriptions. A chef applies from the app (Delivery
 * Option); approving starts an agreement for N months - the chef's
 * deliveryMode becomes "tiffinbox" and customers see the TiffinBox Delivery
 * badge. Renew extends it; Cancel subscription ends it now. When the period
 * runs out the kitchen goes back to self-delivery automatically (the app
 * already treats a past `deliveryUntil` as self; this page / the chef's app
 * also record it as "expired" the first time they see it - no Cloud
 * Functions needed). The delivery operation itself is still manual for now.
 */
export default function DeliveryApplicationsPage({ search }: Props) {
  const { data: applications, loading, error } = useCollection<DeliveryApplication>("delivery_applications");
  const { data: chefs } = useCollection<Chef>("chefs");
  const { data: users } = useCollection<AppUser>("users");
  const [filter, setFilter] = useState<Filter>("pending");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [months, setMonths] = useState<Record<string, number>>({});
  const expiring = useRef(new Set<string>());

  const chefIdOf = (a: DeliveryApplication) => a.chefId ?? a.id;
  const monthsOf = (id: string) => months[id] ?? 1;

  async function notifyChef(chefId: string, title: string, body: string) {
    await addDoc(collection(db, "notifications", chefId, "items"), { title, body, createdAt: Date.now() });
  }

  async function setChefDelivery(chefId: string, mode: "self" | "tiffinbox", until: number) {
    if (!chefs.some((c) => c.id === chefId)) return; // chef deleted - nothing to switch
    await setDoc(doc(db, "chefs", chefId), { deliveryMode: mode, deliveryUntil: until }, { merge: true });
  }

  // Record agreements whose time ran out (once per page session per doc).
  useEffect(() => {
    applications
      .filter((a) => a.status === "approved" && (a.validUntil ?? 0) < Date.now() && !expiring.current.has(a.id))
      .forEach(async (a) => {
        expiring.current.add(a.id);
        try {
          await updateDoc(doc(db, "delivery_applications", a.id), { status: "expired", expiredAt: Date.now() });
          await setChefDelivery(chefIdOf(a), "self", 0);
          await notifyChef(
            chefIdOf(a),
            "TiffinBox Delivery agreement ended",
            "Your kitchen is back on self-delivery. Apply again any time from Delivery Option.",
          );
        } catch {
          expiring.current.delete(a.id);
        }
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applications, chefs]);

  const counts = useMemo(() => {
    const c: Record<Filter, number> = { pending: 0, active: 0, ended: 0 };
    applications.forEach((a) => (c[filterOf(effectiveStatus(a))] += 1));
    return c;
  }, [applications]);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return applications
      .filter((a) => filterOf(effectiveStatus(a)) === filter)
      .filter((a) =>
        !q
          ? true
          : `${a.kitchenName ?? ""} ${a.ownerName ?? ""} ${a.contactNumber ?? ""} ${a.pickupAddress ?? ""}`
              .toLowerCase()
              .includes(q),
      )
      .sort((x, y) => Number(y.updatedAt ?? 0) - Number(x.updatedAt ?? 0));
  }, [applications, filter, search]);

  async function run(app: DeliveryApplication, action: () => Promise<void>) {
    setBusyId(app.id);
    try {
      await action();
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to update.");
    } finally {
      setBusyId(null);
    }
  }

  /** Approve a new request, or renew an active one (extends from its current end date). */
  function approveOrRenew(app: DeliveryApplication) {
    const m = monthsOf(app.id);
    const renewing = effectiveStatus(app) === "approved";
    const from = renewing ? Math.max(Date.now(), app.validUntil ?? 0) : Date.now();
    const validUntil = addMonths(from, m);
    const message = prompt(
      `${renewing ? "Renew" : "Approve"} for ${m} month(s), until ${formatDate(validUntil)}.\n\nOptional message for the chef:`,
      "",
    );
    if (message === null) return;
    return run(app, async () => {
      await updateDoc(doc(db, "delivery_applications", app.id), {
        status: "approved",
        validUntil,
        approvedAt: Date.now(),
        adminNote: message.trim(),
      });
      await setChefDelivery(chefIdOf(app), "tiffinbox", validUntil);
      await notifyChef(
        chefIdOf(app),
        renewing ? "TiffinBox Delivery renewed 🛵" : "TiffinBox Delivery approved 🛵",
        `Your agreement runs until ${formatDate(validUntil)}. Customers now see the TiffinBox Delivery badge.` +
          (message.trim() ? ` ${message.trim()}` : ""),
      );
      await logActivity(renewing ? "Renewed delivery subscription" : "Approved delivery application", "delivery_applications", app.id, {
        months: m,
        validUntil,
      });
    });
  }

  function endWith(app: DeliveryApplication, status: "rejected" | "cancelled" | "pending") {
    let note = app.adminNote ?? "";
    if (status !== "pending") {
      const answer = prompt(
        status === "cancelled"
          ? `Cancel ${app.kitchenName ?? "this kitchen"}'s delivery subscription now? It switches back to self-delivery and the badge disappears.\n\nReason / message for the chef (optional):`
          : "Reason / message for the chef (optional):",
        "",
      );
      if (answer === null) return;
      note = answer.trim();
    }
    return run(app, async () => {
      await updateDoc(doc(db, "delivery_applications", app.id), {
        status,
        adminNote: note,
        ...(status === "cancelled" ? { cancelledAt: Date.now(), validUntil: Date.now() } : {}),
      });
      await setChefDelivery(chefIdOf(app), "self", 0);
      if (status !== "pending") {
        await notifyChef(
          chefIdOf(app),
          status === "cancelled" ? "TiffinBox Delivery subscription cancelled" : "TiffinBox Delivery application update",
          (status === "cancelled"
            ? "Your kitchen is back on self-delivery."
            : "Your TiffinBox Delivery application was not approved this time.") + (note ? ` ${note}` : ""),
        );
      }
      await logActivity(`Delivery application ${status}`, "delivery_applications", app.id, { adminNote: note });
    });
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
          const chefId = chefIdOf(app);
          const chef = chefs.find((c) => c.id === chefId);
          const email = users.find((u) => u.id === chefId)?.email;
          const busy = busyId === app.id;
          const status = effectiveStatus(app);
          const badge = STATUS_BADGE[status];
          const hasLocation = !!app.latitude && !!app.longitude;
          const daysLeft = status === "approved" ? Math.ceil(((app.validUntil ?? 0) - Date.now()) / 86_400_000) : 0;
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
                      <div className="font-semibold text-apple-text truncate">
                        {app.kitchenName || chef?.kitchenName || "(no kitchen name)"}
                      </div>
                      <div className="text-sm text-apple-secondary truncate">{app.ownerName || chef?.ownerName || "—"}</div>
                    </div>
                    <Badge tone={badge.tone}>{badge.label}</Badge>
                  </div>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    <Badge tone="neutral">~{app.expectedDailyOrders ?? 0} orders/day</Badge>
                    <Badge tone="neutral">{app.deliveryRadiusKm ?? 0} km radius</Badge>
                    {(app.mealSlots ?? []).map((s) => (
                      <Badge key={s} tone="neutral">
                        {s}
                      </Badge>
                    ))}
                    {chef?.isDemo && <Badge tone="amber">Demo chef</Badge>}
                  </div>
                </div>
              </div>

              <div className="px-5 pb-4 space-y-1.5 text-xs text-apple-secondary">
                {status === "approved" && (
                  <div className="text-apple-green font-medium">
                    Agreement until {formatDate(app.validUntil)} · {daysLeft} day(s) left · chef is{" "}
                    {chef?.deliveryMode === "tiffinbox" ? "using TiffinBox delivery" : "currently on self-delivery"}
                  </div>
                )}
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

              <div className="px-5 pb-4 flex flex-wrap items-center gap-2">
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
                {status !== "rejected" && (
                  <label className="ml-auto inline-flex items-center gap-1.5 text-xs text-apple-secondary">
                    Agreement
                    <select
                      value={monthsOf(app.id)}
                      onChange={(e) => setMonths((prev) => ({ ...prev, [app.id]: Number(e.target.value) }))}
                      className="bg-apple-subtle border border-apple-border rounded-lg px-2 py-1 text-apple-text"
                    >
                      {DURATIONS.map((m) => (
                        <option key={m} value={m}>
                          {m} month{m > 1 ? "s" : ""}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
              </div>

              <div className="flex border-t border-apple-border text-sm font-medium [&>button+button]:border-l [&>button+button]:border-apple-border">
                {status === "approved" ? (
                  <>
                    <button
                      disabled={busy}
                      onClick={() => approveOrRenew(app)}
                      className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-green hover:bg-apple-green/10 disabled:opacity-40"
                    >
                      <CalendarPlus size={16} /> Renew
                    </button>
                    <button
                      disabled={busy}
                      onClick={() => endWith(app, "cancelled")}
                      className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-red hover:bg-apple-red/10 disabled:opacity-40"
                    >
                      <Ban size={16} /> Cancel subscription
                    </button>
                  </>
                ) : (
                  <>
                    {status !== "rejected" && (
                      <button
                        disabled={busy}
                        onClick={() => approveOrRenew(app)}
                        className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-green hover:bg-apple-green/10 disabled:opacity-40"
                      >
                        <CheckCircle2 size={16} /> {status === "pending" ? "Accept request" : "Re-activate"}
                      </button>
                    )}
                    {status === "pending" ? (
                      <button
                        disabled={busy}
                        onClick={() => endWith(app, "rejected")}
                        className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-red hover:bg-apple-red/10 disabled:opacity-40"
                      >
                        <XCircle size={16} /> Reject
                      </button>
                    ) : (
                      <button
                        disabled={busy}
                        onClick={() => endWith(app, "pending")}
                        className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-secondary hover:bg-apple-hover disabled:opacity-40"
                      >
                        <RotateCcw size={16} /> Back to waiting
                      </button>
                    )}
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
