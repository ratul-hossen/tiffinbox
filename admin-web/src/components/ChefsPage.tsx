import { useMemo, useState } from "react";
import { deleteDoc, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Meal, Order, Review } from "../types";
import { logActivity } from "../lib/activityLog";
import Badge from "./ui/Badge";
import ChefDetail from "./ChefDetail";

interface Props {
  search: string;
}

export default function ChefsPage({ search }: Props) {
  const { data: chefs, loading, error } = useCollection<Chef>("chefs");
  const { data: meals } = useCollection<Meal>("meals");
  const { data: orders } = useCollection<Order>("orders");
  const { data: reviews } = useCollection<Review>("reviews");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ kitchenName: string; bio: string; specialty: string }>({
    kitchenName: "",
    bio: "",
    specialty: "",
  });
  const [busyId, setBusyId] = useState<string | null>(null);
  const [detailChefId, setDetailChefId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return chefs;
    return chefs.filter(
      (c) =>
        (c.kitchenName ?? "").toLowerCase().includes(q) ||
        (c.ownerName ?? "").toLowerCase().includes(q) ||
        c.id.toLowerCase().includes(q),
    );
  }, [chefs, search]);

  function startEdit(chef: Chef) {
    setEditingId(chef.id);
    setDraft({
      kitchenName: chef.kitchenName ?? "",
      bio: chef.bio ?? "",
      specialty: (chef.specialty ?? []).join(", "),
    });
  }

  async function saveEdit(chefId: string) {
    setBusyId(chefId);
    try {
      const specialty = draft.specialty
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      await updateDoc(doc(db, "chefs", chefId), {
        kitchenName: draft.kitchenName,
        bio: draft.bio,
        specialty,
      });
      await logActivity("Edited chef profile", "chefs", chefId, {
        kitchenName: draft.kitchenName,
        specialty,
      });
      setEditingId(null);
    } finally {
      setBusyId(null);
    }
  }

  async function toggleField(chef: Chef, field: "isVerified" | "isAvailableToday") {
    setBusyId(chef.id);
    try {
      const next = !chef[field];
      await updateDoc(doc(db, "chefs", chef.id), { [field]: next });
      await logActivity(
        field === "isVerified" ? "Toggled chef verification" : "Toggled chef availability",
        "chefs",
        chef.id,
        { [field]: next },
      );
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(chefId: string) {
    if (!confirm("Delete this chef? This cannot be undone.")) return;
    setBusyId(chefId);
    try {
      await deleteDoc(doc(db, "chefs", chefId));
      await logActivity("Deleted chef", "chefs", chefId);
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading chefs…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  const detailChef = chefs.find((c) => c.id === detailChefId) ?? null;

  return (
    <div className="space-y-4">
      <h2 className="text-2xl font-bold text-apple-text tracking-tight">Chefs ({filtered.length})</h2>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {filtered.map((chef) => (
          <div
            key={chef.id}
            className="bg-apple-surface rounded-2xl border border-black/5 p-5 shadow-card hover:shadow-card-hover transition-shadow duration-200"
          >
            {editingId === chef.id ? (
              <div className="space-y-2">
                <input
                  className="w-full text-sm border border-black/10 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
                  value={draft.kitchenName}
                  onChange={(e) => setDraft({ ...draft, kitchenName: e.target.value })}
                  placeholder="Kitchen name"
                />
                <textarea
                  className="w-full text-sm border border-black/10 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
                  value={draft.bio}
                  onChange={(e) => setDraft({ ...draft, bio: e.target.value })}
                  placeholder="Bio"
                  rows={2}
                />
                <input
                  className="w-full text-sm border border-black/10 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
                  value={draft.specialty}
                  onChange={(e) => setDraft({ ...draft, specialty: e.target.value })}
                  placeholder="Specialty (comma separated)"
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => saveEdit(chef.id)}
                    disabled={busyId === chef.id}
                    className="text-xs bg-apple-blue hover:bg-apple-blue-dark disabled:opacity-60 text-white rounded-lg px-3 py-1.5 font-medium transition-colors duration-150"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => setEditingId(null)}
                    className="text-xs bg-black/5 hover:bg-black/10 rounded-lg px-3 py-1.5 transition-colors duration-150"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-semibold text-apple-text">
                      {chef.emoji ?? "👨‍🍳"} {chef.kitchenName ?? "(unnamed kitchen)"}
                    </div>
                    <div className="text-xs text-apple-secondary">{chef.ownerName}</div>
                  </div>
                  <div className="text-xs text-apple-secondary/70">{chef.id.slice(0, 6)}</div>
                </div>

                <p className="text-sm text-apple-secondary mt-2 line-clamp-2">{chef.bio}</p>

                {chef.specialty && chef.specialty.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-2">
                    {chef.specialty.map((s) => (
                      <span
                        key={s}
                        className="text-xs bg-apple-blue/10 text-apple-blue rounded-full px-2 py-0.5"
                      >
                        {s}
                      </span>
                    ))}
                  </div>
                )}

                <div className="grid grid-cols-3 gap-2 text-xs text-apple-secondary mt-3">
                  <div>Rating: {chef.overallRating ?? "–"}</div>
                  <div>Orders: {chef.totalOrders ?? 0}</div>
                  <div>Dist: {chef.distanceKm ?? "–"} km</div>
                </div>

                <div className="flex flex-wrap items-center gap-2 mt-3">
                  <Badge
                    tone={chef.isVerified ? "green" : "neutral"}
                    onClick={() => toggleField(chef, "isVerified")}
                    disabled={busyId === chef.id}
                  >
                    {chef.isVerified ? "Approved" : "Pending approval"}
                  </Badge>
                  <Badge
                    tone={chef.isAvailableToday ? "blue" : "neutral"}
                    onClick={() => toggleField(chef, "isAvailableToday")}
                    disabled={busyId === chef.id}
                  >
                    {chef.isAvailableToday ? "Available today" : "Unavailable"}
                  </Badge>
                  {chef.deliveryMode === "tiffinbox" && (
                    <Badge tone="blue">🛵 TiffinBox Delivery</Badge>
                  )}
                </div>
                {!chef.isVerified && (
                  <p className="text-[11px] text-apple-secondary mt-1.5">
                    Hidden from customers and can&apos;t receive orders until approved.
                  </p>
                )}

                <div className="flex gap-3 mt-3 pt-3 border-t border-black/5 text-xs">
                  <button
                    onClick={() => setDetailChefId(chef.id)}
                    className="text-apple-blue hover:text-apple-blue-dark font-medium transition-colors duration-150"
                  >
                    View details
                  </button>
                  <button
                    onClick={() => startEdit(chef)}
                    className="text-apple-secondary hover:text-apple-text transition-colors duration-150"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(chef.id)}
                    disabled={busyId === chef.id}
                    className="text-apple-red hover:opacity-80 transition-opacity duration-150"
                  >
                    Delete
                  </button>
                </div>
              </>
            )}
          </div>
        ))}
      </div>

      {filtered.length === 0 && <p className="text-sm text-apple-secondary">No chefs found.</p>}

      <ChefDetail
        chef={detailChef}
        meals={meals}
        orders={orders}
        reviews={reviews}
        onClose={() => setDetailChefId(null)}
      />
    </div>
  );
}
