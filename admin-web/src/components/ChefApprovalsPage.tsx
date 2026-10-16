import { useMemo, useState } from "react";
import { CheckCircle2, Mail, MapPin, MessageCircle, Phone, RotateCcw, XCircle } from "lucide-react";
import { useCollection } from "../hooks/useCollection";
import { isAwaitingReview, setChefApproval } from "../lib/chefApproval";
import type { AppUser, Chef, ChefApprovalStatus, Meal } from "../types";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

type Filter = "pending" | "rejected";

const FILTERS: { key: Filter; label: string }[] = [
  { key: "pending", label: "Waiting for review" },
  { key: "rejected", label: "Rejected" },
];

function whatsappLink(number?: string) {
  const digits = (number ?? "").replace(/\D/g, "");
  return digits ? `https://wa.me/${digits}` : undefined;
}

// Opens Gmail's compose window in a new tab, addressed to the chef.
function gmailLink(email: string, kitchenName?: string) {
  const subject = encodeURIComponent(`TiffinBox - your kitchen application${kitchenName ? ` (${kitchenName})` : ""}`);
  return `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(email)}&su=${subject}`;
}

export default function ChefApprovalsPage({ search }: Props) {
  const { data: chefs, loading, error } = useCollection<Chef>("chefs");
  const { data: meals } = useCollection<Meal>("meals");
  // Chef docs don't carry the email - it lives on users/{uid} (same uid).
  const { data: users } = useCollection<AppUser>("users");
  const [filter, setFilter] = useState<Filter>("pending");
  const [busyId, setBusyId] = useState<string | null>(null);

  const counts = useMemo(
    () => ({
      pending: chefs.filter(isAwaitingReview).length,
      rejected: chefs.filter((c) => !c.isVerified && c.approvalStatus === "rejected").length,
    }),
    [chefs],
  );

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return chefs
      .filter((c) => (filter === "pending" ? isAwaitingReview(c) : !c.isVerified && c.approvalStatus === "rejected"))
      .filter((c) =>
        !q
          ? true
          : `${c.kitchenName ?? ""} ${c.ownerName ?? ""} ${c.phoneNumber ?? ""} ${c.whatsappNumber ?? ""}`
              .toLowerCase()
              .includes(q),
      );
  }, [chefs, filter, search]);

  const mealCount = (chefId: string) => meals.filter((m) => m.chefId === chefId).length;
  const emailOf = (chefId: string) => users.find((u) => u.id === chefId)?.email;

  async function decide(chef: Chef, status: ChefApprovalStatus) {
    if (status === "rejected" && !confirm(`Reject ${chef.kitchenName ?? "this chef"}? They won't be listed to customers.`)) return;
    setBusyId(chef.id);
    try {
      await setChefApproval(chef, status);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to update chef.");
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading chefs…</p>;
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
          {filter === "pending" ? "🎉 No chefs waiting for approval right now." : "Nothing here."}
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {visible.map((chef) => {
          const busy = busyId === chef.id;
          const email = emailOf(chef.id);
          const hasLocation = !!chef.latitude && !!chef.longitude;
          return (
            <div key={chef.id} className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-hidden">
              <div className="flex gap-4 p-5">
                <div className="w-24 h-24 shrink-0 rounded-xl overflow-hidden bg-apple-subtle flex items-center justify-center text-4xl">
                  {chef.photoBase64 ? (
                    <img
                      src={`data:image/jpeg;base64,${chef.photoBase64}`}
                      alt=""
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    chef.emoji ?? "🍱"
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="font-semibold text-apple-text truncate">{chef.kitchenName || "(no kitchen name)"}</div>
                      <div className="text-sm text-apple-secondary truncate">{chef.ownerName || "—"}</div>
                    </div>
                    {chef.approvalStatus === "rejected" && <Badge tone="red">Rejected</Badge>}
                    {isAwaitingReview(chef) && <Badge tone="blue">New</Badge>}
                  </div>
                  <div className="mt-2 flex flex-wrap gap-1.5 text-xs">
                    {chef.kitchenType && <Badge tone="neutral">{chef.kitchenType}</Badge>}
                    <Badge tone="neutral">{chef.yearsOfExperience ?? 0} yrs experience</Badge>
                    <Badge tone="neutral">{mealCount(chef.id)} menu items</Badge>
                    <Badge tone={chef.photoBase64 ? "green" : "amber"}>{chef.photoBase64 ? "Photo ✓" : "No photo"}</Badge>
                    <Badge tone={hasLocation ? "green" : "amber"}>{hasLocation ? "Location ✓" : "No location"}</Badge>
                  </div>
                </div>
              </div>

              {chef.bio && <p className="px-5 -mt-1 pb-3 text-sm text-apple-secondary line-clamp-3">{chef.bio}</p>}

              <div className="px-5 pb-4 grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
                <div className="text-apple-secondary">
                  <Phone size={11} className="inline mr-1" />
                  Phone: <span className="text-apple-text">{chef.phoneNumber || "—"}</span>
                </div>
                <div className="text-apple-secondary">
                  WhatsApp:{" "}
                  {whatsappLink(chef.whatsappNumber) ? (
                    <a href={whatsappLink(chef.whatsappNumber)} target="_blank" rel="noreferrer" className="text-apple-blue hover:underline">
                      {chef.whatsappNumber}
                    </a>
                  ) : (
                    <span className="text-apple-text">—</span>
                  )}
                </div>
                <div className="text-apple-secondary">
                  bKash: <span className="text-apple-text">{chef.bkashNumber || "—"}</span>
                </div>
                <div className="text-apple-secondary">
                  Nagad: <span className="text-apple-text">{chef.nagadNumber || "—"}</span>
                </div>
                <div className="col-span-2 text-apple-secondary">
                  <MapPin size={11} className="inline mr-1" />
                  {hasLocation ? (
                    <a
                      href={`https://www.google.com/maps?q=${chef.latitude},${chef.longitude}`}
                      target="_blank"
                      rel="noreferrer"
                      className="text-apple-blue hover:underline"
                    >
                      View kitchen location on map ↗
                    </a>
                  ) : (
                    "No location saved yet"
                  )}
                </div>
              </div>

              <div className="px-5 pb-4 flex flex-wrap gap-2">
                {whatsappLink(chef.whatsappNumber) && (
                  <a
                    href={whatsappLink(chef.whatsappNumber)}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1.5 text-xs font-medium rounded-full px-3 py-1.5 bg-apple-green/10 text-apple-green hover:bg-apple-green/20"
                  >
                    <MessageCircle size={13} /> WhatsApp
                  </a>
                )}
                {email && (
                  <a
                    href={gmailLink(email, chef.kitchenName)}
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
                <button
                  disabled={busy}
                  onClick={() => decide(chef, "approved")}
                  className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-green hover:bg-apple-green/10 disabled:opacity-40"
                >
                  <CheckCircle2 size={16} /> Approve
                </button>
                {chef.approvalStatus === "rejected" ? (
                  <button
                    disabled={busy}
                    onClick={() => decide(chef, "pending")}
                    className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-secondary hover:bg-apple-hover border-l border-apple-border disabled:opacity-40"
                  >
                    <RotateCcw size={16} /> Back to waiting
                  </button>
                ) : (
                  <button
                    disabled={busy}
                    onClick={() => decide(chef, "rejected")}
                    className="flex-1 flex items-center justify-center gap-1.5 py-3 text-apple-red hover:bg-apple-red/10 border-l border-apple-border disabled:opacity-40"
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
