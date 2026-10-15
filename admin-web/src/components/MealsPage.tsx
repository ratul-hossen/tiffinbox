import { useMemo, useState } from "react";
import { deleteDoc, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Meal } from "../types";
import { logActivity } from "../lib/activityLog";
import { formatCurrency } from "../lib/format";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

export default function MealsPage({ search }: Props) {
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

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return meals.filter((m) => {
      if (chefFilter !== "all" && m.chefId !== chefFilter) return false;
      if (q && !(m.name ?? "").toLowerCase().includes(q) && !m.id.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [meals, chefFilter, search]);

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
      const price = Number(draft.price) || 0;
      const availableQuantity = Number(draft.availableQuantity) || 0;
      await updateDoc(doc(db, "meals", mealId), { price, availableQuantity });
      await logActivity("Edited meal", "meals", mealId, { price, availableQuantity });
      setEditingId(null);
    } finally {
      setBusyId(null);
    }
  }

  async function toggleAvailable(meal: Meal) {
    setBusyId(meal.id);
    try {
      const next = !meal.isAvailable;
      await updateDoc(doc(db, "meals", meal.id), { isAvailable: next });
      await logActivity("Toggled meal availability", "meals", meal.id, { isAvailable: next });
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(mealId: string) {
    if (!confirm("Delete this meal? This cannot be undone.")) return;
    setBusyId(mealId);
    try {
      await deleteDoc(doc(db, "meals", mealId));
      await logActivity("Deleted meal", "meals", mealId);
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading meals…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <p className="text-sm text-apple-secondary">{filtered.length} meals</p>
        <select
          value={chefFilter}
          onChange={(e) => setChefFilter(e.target.value)}
          className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 transition-all duration-150"
        >
          <option value="all">All chefs</option>
          {chefs.map((c) => (
            <option key={c.id} value={c.id}>
              {c.kitchenName ?? c.id}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-apple-secondary border-b border-apple-border">
              <th className="px-4 py-3">Meal</th>
              <th className="px-4 py-3">Chef</th>
              <th className="px-4 py-3">Category</th>
              <th className="px-4 py-3">Price</th>
              <th className="px-4 py-3">Qty</th>
              <th className="px-4 py-3">Available</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((meal) => (
              <tr key={meal.id} className="border-b border-apple-border last:border-0">
                <td className="px-4 py-3">
                  {meal.emoji ?? "🍽️"} {meal.name}
                </td>
                <td className="px-4 py-3 text-apple-secondary">{chefName(meal.chefId)}</td>
                <td className="px-4 py-3 text-apple-secondary">
                  {meal.category} {meal.isVeg ? "🌱" : ""}
                </td>
                <td className="px-4 py-3">
                  {editingId === meal.id ? (
                    <input
                      className="w-20 border border-apple-border2 bg-apple-surface2 rounded-lg px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-apple-blue/50"
                      value={draft.price}
                      onChange={(e) => setDraft({ ...draft, price: e.target.value })}
                    />
                  ) : (
                    formatCurrency(meal.price ?? 0)
                  )}
                </td>
                <td className="px-4 py-3">
                  {editingId === meal.id ? (
                    <input
                      className="w-16 border border-apple-border2 bg-apple-surface2 rounded-lg px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-apple-blue/50"
                      value={draft.availableQuantity}
                      onChange={(e) => setDraft({ ...draft, availableQuantity: e.target.value })}
                    />
                  ) : (
                    meal.availableQuantity ?? 0
                  )}
                </td>
                <td className="px-4 py-3">
                  <Badge
                    tone={meal.isAvailable ? "green" : "neutral"}
                    onClick={() => toggleAvailable(meal)}
                    disabled={busyId === meal.id}
                  >
                    {meal.isAvailable ? "Yes" : "No"}
                  </Badge>
                </td>
                <td className="px-4 py-3 whitespace-nowrap">
                  {editingId === meal.id ? (
                    <div className="flex gap-3 text-xs">
                      <button
                        onClick={() => saveEdit(meal.id)}
                        disabled={busyId === meal.id}
                        className="text-apple-blue hover:text-apple-blue-dark font-medium transition-colors duration-150"
                      >
                        Save
                      </button>
                      <button
                        onClick={() => setEditingId(null)}
                        className="text-apple-secondary transition-colors duration-150"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <div className="flex gap-3 text-xs">
                      <button
                        onClick={() => startEdit(meal)}
                        className="text-apple-secondary hover:text-apple-text transition-colors duration-150"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDelete(meal.id)}
                        disabled={busyId === meal.id}
                        className="text-apple-red hover:opacity-80 transition-opacity duration-150"
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
          <p className="text-sm text-apple-secondary px-4 py-6">No meals found.</p>
        )}
      </div>
    </div>
  );
}
