import { useMemo, useState } from "react";
import { deleteDoc, doc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Review } from "../types";

export default function ReviewsPage() {
  const { data: reviews, loading, error } = useCollection<Review>("reviews");
  const { data: chefs } = useCollection<Chef>("chefs");
  const [chefFilter, setChefFilter] = useState<string>("all");
  const [busyId, setBusyId] = useState<string | null>(null);

  const chefName = (chefId?: string) =>
    chefs.find((c) => c.id === chefId)?.kitchenName ?? chefId ?? "(unknown chef)";

  const filtered = useMemo(
    () => (chefFilter === "all" ? reviews : reviews.filter((r) => r.chefId === chefFilter)),
    [reviews, chefFilter],
  );

  async function handleDelete(reviewId: string) {
    if (!confirm("Delete (flag) this review? This cannot be undone.")) return;
    setBusyId(reviewId);
    try {
      await deleteDoc(doc(db, "reviews", reviewId));
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-slate-400">Loading reviews…</p>;
  if (error) return <p className="text-sm text-red-600">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="text-lg font-semibold text-slate-900">Reviews ({filtered.length})</h2>
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

      <div className="grid gap-3 md:grid-cols-2">
        {filtered.map((review) => (
          <div key={review.id} className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm">
            <div className="flex items-start justify-between">
              <div>
                <div className="font-medium text-slate-900">{chefName(review.chefId)}</div>
                <div className="text-xs text-slate-400">
                  {review.createdAt ? new Date(review.createdAt).toLocaleString() : ""}
                </div>
              </div>
              <div className="text-sm font-semibold text-brand-700">
                ⭐ {review.overallRating?.toFixed(1) ?? "–"}
              </div>
            </div>

            <p className="text-sm text-slate-600 mt-2">{review.comment}</p>

            <div className="grid grid-cols-4 gap-1 text-xs text-slate-500 mt-3">
              <div>Taste: {review.tasteRating ?? "–"}</div>
              <div>Hygiene: {review.hygieneRating ?? "–"}</div>
              <div>Pkg: {review.packagingRating ?? "–"}</div>
              <div>On-time: {review.onTimeRating ?? "–"}</div>
            </div>

            <div className="mt-3 pt-3 border-t border-slate-100">
              <button
                onClick={() => handleDelete(review.id)}
                disabled={busyId === review.id}
                className="text-xs text-red-600 hover:text-red-800 underline"
              >
                Flag / delete
              </button>
            </div>
          </div>
        ))}
      </div>

      {filtered.length === 0 && (
        <p className="text-sm text-slate-400">No reviews found.</p>
      )}
    </div>
  );
}
