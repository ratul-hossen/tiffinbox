import { useMemo, useState } from "react";
import { deleteDoc, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Meal } from "../types";

export default function MealsPage() {
  const { data: meals, loading, error } = useCollection<Meal>("meals");
  const { data: chefs } = useCollection<Chef>("chefs");
  const [chefFilter, setChefFilter] = useState<string>("all");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ price: string; availableQuantity: string }>({
    price: "",
    availableQuantity: "",
  });

  const chefName = (chefId?: string) =>
    chefs.find((c) => c.id === chefId)?.kitchenName ?? chefId ?? "(unknown chef)";

  const filtered = useMemo(
    () => (chefFilter === "all" ? meals : meals.filter((m) => m.chefId === chefFilter)),
    [meals, chefFilter],
  );

  function startEdit(meal: Meal) {
    setEditingId(meal.id);
    setDraft({
      price: String(meal.price ?? ""),
      availableQuantity: String(meal.availableQuantity ?? ""),
    });
  }

  async function saveEdit(mealId: string) {
    setBusyId(mealId);
    try {
      await updateDoc(doc(db, "meals", mealId), {
        price: Number(draft.price) || 0,
        availableQuantity: Number(draft.availableQuantity) || 0,
      });
      setEditingId(null);
    } finally {
      setBusyId(null);
    }
  }

  async function toggleAvailable(meal: Meal) {
    setBusyId(meal.id);
    try {
      await updateDoc(doc(db, "meals", meal.id), { isAvailable: !meal.isAvailable });
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(mealId: string) {
    if (!confirm("Delete this meal? This cannot be undone.")) return;
    setBusyId(mealId);
    try {
      await deleteDoc(doc(db, "meals", mealId));
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-slate-400">Loading meals…</p>;
  if (error) return <p className="text-sm text-red-600">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="text-lg font-semibold text-slate-900">Meals ({filtered.length})</h2>
        <select
          value={chefFilter}
          onChange={(e) => setChefFilter(e.target.value)}
          className="text-sm border border-slate-300 rounded px-2 py-1"
        >
          <option value="all">All chefs</option>
          {chefs.map((c) => (
            <option key={c.id} value={c.id}>
              {c.kitchenName ?? c.id}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
              <th className="px-4 py-2">Meal</th>
              <th className="px-4 py-2">Chef</th>
              <th className="px-4 py-2">Category</th>
              <th className="px-4 py-2">Price</th>
              <th className="px-4 py-2">Qty</th>
              <th className="px-4 py-2">Available</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((meal) => (
              <tr key={meal.id} className="border-b border-slate-50 last:border-0">
                <td className="px-4 py-2">
                  {meal.emoji ?? "🍽️"} {meal.name}
                </td>
                <td className="px-4 py-2 text-slate-500">{chefName(meal.chefId)}</td>
                <td className="px-4 py-2 text-slate-500">
                  {meal.category} {meal.isVeg ? "🌱" : ""}
                </td>
                <td className="px-4 py-2">
                  {editingId === meal.id ? (
                    <input
                      className="w-20 border border-slate-300 rounded px-1 py-0.5 text-sm"
                      value={draft.price}
                      onChange={(e) => setDraft({ ...draft, price: e.target.value })}
                    />
                  ) : (
                    `৳${meal.price ?? 0}`
                  )}
                </td>
                <td className="px-4 py-2">
                  {editingId === meal.id ? (
                    <input
                      className="w-16 border border-slate-300 rounded px-1 py-0.5 text-sm"
                      value={draft.availableQuantity}
                      onChange={(e) => setDraft({ ...draft, availableQuantity: e.target.value })}
                    />
                  ) : (
                    meal.availableQuantity ?? 0
                  )}
                </td>
                <td className="px-4 py-2">
                  <button
                    onClick={() => toggleAvailable(meal)}
                    disabled={busyId === meal.id}
                    className={`text-xs rounded-full px-2 py-1 font-medium ${
                      meal.isAvailable ? "bg-green-100 text-green-700" : "bg-slate-100 text-slate-600"
                    }`}
                  >
                    {meal.isAvailable ? "Yes" : "No"}
                  </button>
                </td>
                <td className="px-4 py-2 whitespace-nowrap">
                  {editingId === meal.id ? (
                    <div className="flex gap-2">
                      <button
                        onClick={() => saveEdit(meal.id)}
                        disabled={busyId === meal.id}
                        className="text-xs text-brand-700 hover:text-brand-900 underline"
                      >
                        Save
                      </button>
                      <button
                        onClick={() => setEditingId(null)}
                        className="text-xs text-slate-500 underline"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <div className="flex gap-2">
                      <button
                        onClick={() => startEdit(meal)}
                        className="text-xs text-slate-600 hover:text-slate-900 underline"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDelete(meal.id)}
                        disabled={busyId === meal.id}
                        className="text-xs text-red-600 hover:text-red-800 underline"
                      >
                        Delete
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <p className="text-sm text-slate-400 px-4 py-6">No meals found.</p>
        )}
      </div>
    </div>
  );
}
