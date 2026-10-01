import { useState } from "react";
import { deleteDoc, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef } from "../types";

export default function ChefsPage() {
  const { data: chefs, loading, error } = useCollection<Chef>("chefs");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ kitchenName: string; bio: string; specialty: string }>({
    kitchenName: "",
    bio: "",
    specialty: "",
  });
  const [busyId, setBusyId] = useState<string | null>(null);

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
      await updateDoc(doc(db, "chefs", chefId), {
        kitchenName: draft.kitchenName,
        bio: draft.bio,
        specialty: draft.specialty
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
      });
      setEditingId(null);
    } finally {
      setBusyId(null);
    }
  }

  async function toggleField(chef: Chef, field: "isVerified" | "isAvailableToday") {
    setBusyId(chef.id);
    try {
      await updateDoc(doc(db, "chefs", chef.id), { [field]: !chef[field] });
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(chefId: string) {
    if (!confirm("Delete this chef? This cannot be undone.")) return;
    setBusyId(chefId);
    try {
      await deleteDoc(doc(db, "chefs", chefId));
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-slate-400">Loading chefs…</p>;
  if (error) return <p className="text-sm text-red-600">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">Chefs ({chefs.length})</h2>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {chefs.map((chef) => (
          <div key={chef.id} className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm">
            {editingId === chef.id ? (
              <div className="space-y-2">
                <input
                  className="w-full text-sm border border-slate-300 rounded px-2 py-1"
                  value={draft.kitchenName}
                  onChange={(e) => setDraft({ ...draft, kitchenName: e.target.value })}
                  placeholder="Kitchen name"
                />
                <textarea
                  className="w-full text-sm border border-slate-300 rounded px-2 py-1"
                  value={draft.bio}
                  onChange={(e) => setDraft({ ...draft, bio: e.target.value })}
                  placeholder="Bio"
                  rows={2}
                />
                <input
                  className="w-full text-sm border border-slate-300 rounded px-2 py-1"
                  value={draft.specialty}
                  onChange={(e) => setDraft({ ...draft, specialty: e.target.value })}
                  placeholder="Specialty (comma separated)"
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => saveEdit(chef.id)}
                    disabled={busyId === chef.id}
                    className="text-xs bg-brand-600 hover:bg-brand-700 text-white rounded px-3 py-1"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => setEditingId(null)}
                    className="text-xs bg-slate-100 hover:bg-slate-200 rounded px-3 py-1"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-medium text-slate-900">
                      {chef.emoji ?? "👨‍🍳"} {chef.kitchenName ?? "(unnamed kitchen)"}
                    </div>
                    <div className="text-xs text-slate-500">{chef.ownerName}</div>
                  </div>
                  <div className="text-xs text-slate-400">{chef.id}</div>
                </div>

                <p className="text-sm text-slate-600 mt-2 line-clamp-2">{chef.bio}</p>

                {chef.specialty && chef.specialty.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-2">
                    {chef.specialty.map((s) => (
                      <span key={s} className="text-xs bg-brand-50 text-brand-700 rounded-full px-2 py-0.5">
                        {s}
                      </span>
                    ))}
                  </div>
                )}

                <div className="grid grid-cols-2 gap-2 text-xs text-slate-500 mt-3">
                  <div>Rating: {chef.overallRating ?? "–"}</div>
                  <div>Orders: {chef.totalOrders ?? 0}</div>
                  <div>Distance: {chef.distanceKm ?? "–"} km</div>
                </div>

                <div className="flex flex-wrap items-center gap-2 mt-3">
                  <button
                    onClick={() => toggleField(chef, "isVerified")}
                    disabled={busyId === chef.id}
                    className={`text-xs rounded-full px-2 py-1 font-medium ${
                      chef.isVerified
                        ? "bg-green-100 text-green-700"
                        : "bg-slate-100 text-slate-600"
                    }`}
                  >
                    {chef.isVerified ? "✓ Verified" : "Not verified"}
                  </button>
                  <button
                    onClick={() => toggleField(chef, "isAvailableToday")}
                    disabled={busyId === chef.id}
                    className={`text-xs rounded-full px-2 py-1 font-medium ${
                      chef.isAvailableToday
                        ? "bg-blue-100 text-blue-700"
                        : "bg-slate-100 text-slate-600"
                    }`}
                  >
                    {chef.isAvailableToday ? "Available today" : "Unavailable"}
                  </button>
                </div>

                <div className="flex gap-2 mt-3 pt-3 border-t border-slate-100">
                  <button
                    onClick={() => startEdit(chef)}
                    className="text-xs text-slate-600 hover:text-slate-900 underline"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(chef.id)}
                    disabled={busyId === chef.id}
                    className="text-xs text-red-600 hover:text-red-800 underline"
                  >
                    Delete
                  </button>
                </div>
              </>
            )}
          </div>
        ))}
      </div>

      {chefs.length === 0 && (
        <p className="text-sm text-slate-400">No chefs yet.</p>
      )}
    </div>
  );
}
