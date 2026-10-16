import { useMemo, useState } from "react";
import { doc, setDoc, updateDoc } from "firebase/firestore";
import { BadgeCheck, ImageOff } from "lucide-react";
import { auth, db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { formatDateTime } from "../lib/format";
import type { Chef, ChefVerification } from "../types";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

type Filter = "pending" | "verified" | "all";

/**
 * Chef Verification: the chef's private NID photo (chef_verifications/{uid})
 * next to their kitchen photo. The admin ticks NID and kitchen separately;
 * both ticked = the "Verified" badge customers see. The NID photo can be
 * deleted after checking, keeping only the yes/no result.
 */
export default function VerificationsPage({ search }: Props) {
  const { data: chefs } = useCollection<Chef>("chefs");
  const { data: requests, loading, error } = useCollection<ChefVerification>("chef_verifications");
  const [filter, setFilter] = useState<Filter>("pending");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [zoom, setZoom] = useState<string | null>(null);

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return requests
      .map((r) => ({ request: r, chef: chefs.find((c) => c.id === r.id) }))
      .filter(({ request, chef }) => {
        const verified = !!chef?.nidVerified && !!chef?.kitchenVerified;
        if (filter === "pending" && (verified || request.status === "rejected")) return false;
        if (filter === "verified" && !verified) return false;
        if (!q) return true;
        return `${chef?.kitchenName ?? ""} ${chef?.ownerName ?? ""} ${chef?.phoneNumber ?? ""}`.toLowerCase().includes(q);
      })
      .sort((a, b) => (b.request.submittedAt ?? 0) - (a.request.submittedAt ?? 0));
  }, [requests, chefs, filter, search]);

  async function setCheck(chef: Chef, field: "nidVerified" | "kitchenVerified", value: boolean) {
    setBusyId(chef.id);
    try {
      await updateDoc(doc(db, "chefs", chef.id), { [field]: value });
      const next = { ...chef, [field]: value };
      const status = next.nidVerified && next.kitchenVerified ? "verified" : "pending";
      await setDoc(
        doc(db, "chef_verifications", chef.id),
        { status, reviewedAt: Date.now(), reviewedBy: auth.currentUser?.email ?? null },
        { merge: true },
      );
      await logActivity(value ? "Verified chef check" : "Removed chef check", "chefs", chef.id, { field });
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to update.");
    } finally {
      setBusyId(null);
    }
  }

  async function reject(chef: Chef) {
    const note = prompt("Why? The chef sees this note so they can send a clearer photo.", "NID photo is not readable");
    if (note === null) return;
    setBusyId(chef.id);
    try {
      await updateDoc(doc(db, "chefs", chef.id), { nidVerified: false });
      await setDoc(
        doc(db, "chef_verifications", chef.id),
        { status: "rejected", note, reviewedAt: Date.now(), reviewedBy: auth.currentUser?.email ?? null },
        { merge: true },
      );
      await logActivity("Rejected chef NID", "chef_verifications", chef.id, { note });
    } finally {
      setBusyId(null);
    }
  }

  async function deleteNidPhoto(chefId: string) {
    if (!confirm("Delete the NID photo now? The verified result stays; the image is removed for privacy.")) return;
    await updateDoc(doc(db, "chef_verifications", chefId), { nidPhotoBase64: "" });
    await logActivity("Deleted NID photo after review", "chef_verifications", chefId);
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading verification requests…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap gap-2">
        {(["pending", "verified", "all"] as Filter[]).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`rounded-full px-3 py-1.5 text-sm font-medium border ${
              filter === f ? "bg-apple-blue text-white border-apple-blue" : "border-apple-border text-apple-text hover:bg-apple-hover"
            }`}
          >
            {f === "pending" ? "Needs review" : f === "verified" ? "Verified" : "All"}
          </button>
        ))}
      </div>
      <p className="text-sm text-apple-secondary">{rows.length} requests</p>

      {rows.length === 0 && (
        <p className="text-sm text-apple-secondary">Nothing here. Chefs send their NID from Edit Profile → Get verified.</p>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {rows.map(({ request, chef }) => (
          <div key={request.id} className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 className="font-semibold text-apple-text">{chef?.kitchenName ?? "(chef deleted)"}</h3>
                <p className="text-xs text-apple-secondary">
                  {chef?.ownerName} · {chef?.phoneNumber} · sent {formatDateTime(request.submittedAt)}
                </p>
              </div>
              {chef?.nidVerified && chef?.kitchenVerified ? (
                <Badge tone="green">
                  <BadgeCheck size={14} /> Verified
                </Badge>
              ) : request.status === "rejected" ? (
                <Badge tone="red">Rejected</Badge>
              ) : (
                <Badge tone="amber">Pending</Badge>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <p className="text-xs text-apple-secondary mb-1">NID photo (private)</p>
                {request.nidPhotoBase64 ? (
                  <img
                    src={`data:image/jpeg;base64,${request.nidPhotoBase64}`}
                    alt="NID"
                    onClick={() => setZoom(request.nidPhotoBase64 ?? null)}
                    className="rounded-lg w-full aspect-[4/3] object-cover cursor-zoom-in border border-apple-border"
                  />
                ) : (
                  <div className="rounded-lg w-full aspect-[4/3] bg-apple-subtle flex items-center justify-center text-apple-secondary text-xs gap-1">
                    <ImageOff size={14} /> No photo
                  </div>
                )}
              </div>
              <div>
                <p className="text-xs text-apple-secondary mb-1">Kitchen photo</p>
                {chef?.photoBase64 ? (
                  <img
                    src={`data:image/jpeg;base64,${chef.photoBase64}`}
                    alt="Kitchen"
                    onClick={() => setZoom(chef.photoBase64 ?? null)}
                    className="rounded-lg w-full aspect-[4/3] object-cover cursor-zoom-in border border-apple-border"
                  />
                ) : (
                  <div className="rounded-lg w-full aspect-[4/3] bg-apple-subtle flex items-center justify-center text-apple-secondary text-xs gap-1">
                    <ImageOff size={14} /> No kitchen photo yet
                  </div>
                )}
              </div>
            </div>

            {request.note && request.status === "rejected" && (
              <p className="text-xs text-apple-red">Note to chef: {request.note}</p>
            )}

            {chef && (
              <div className="flex flex-wrap gap-2">
                <button
                  disabled={busyId === chef.id || !request.nidPhotoBase64}
                  onClick={() => setCheck(chef, "nidVerified", !chef.nidVerified)}
                  className={`text-sm rounded-lg px-3 py-1.5 font-medium disabled:opacity-40 ${
                    chef.nidVerified ? "bg-apple-green/15 text-apple-green" : "bg-apple-blue text-white"
                  }`}
                >
                  {chef.nidVerified ? "✓ NID verified (undo)" : "Mark NID verified"}
                </button>
                <button
                  disabled={busyId === chef.id || !chef.photoBase64}
                  onClick={() => setCheck(chef, "kitchenVerified", !chef.kitchenVerified)}
                  className={`text-sm rounded-lg px-3 py-1.5 font-medium disabled:opacity-40 ${
                    chef.kitchenVerified ? "bg-apple-green/15 text-apple-green" : "bg-apple-blue text-white"
                  }`}
                >
                  {chef.kitchenVerified ? "✓ Kitchen verified (undo)" : "Mark kitchen verified"}
                </button>
                <button
                  disabled={busyId === chef.id}
                  onClick={() => reject(chef)}
                  className="text-sm rounded-lg px-3 py-1.5 font-medium text-apple-red hover:bg-apple-red/10"
                >
                  Reject NID
                </button>
                {request.nidPhotoBase64 && chef.nidVerified && (
                  <button
                    onClick={() => deleteNidPhoto(chef.id)}
                    className="text-sm rounded-lg px-3 py-1.5 font-medium text-apple-secondary hover:bg-apple-hover"
                  >
                    Delete NID photo
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {zoom && (
        <div
          className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-6 cursor-zoom-out"
          onClick={() => setZoom(null)}
        >
          <img src={`data:image/jpeg;base64,${zoom}`} alt="" className="max-h-full max-w-full rounded-xl" />
        </div>
      )}
    </div>
  );
}
