import { useMemo, useState } from "react";
import { deleteDoc, doc } from "firebase/firestore";
import { Star } from "lucide-react";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Review } from "../types";
import { logActivity } from "../lib/activityLog";
import { formatDateTime } from "../lib/format";

interface Props {
  search: string;
}

export default function ReviewsPage({ search }: Props) {
  const { data: reviews, loading, error } = useCollection<Review>("reviews");
  const { data: chefs } = useCollection<Chef>("chefs");
  const [chefFilter, setChefFilter] = useState<string>("all");
  const [busyId, setBusyId] = useState<string | null>(null);

  const chefName = (chefId?: string) =>
    chefs.find((c) => c.id === chefId)?.kitchenName ?? chefId ?? "(unknown chef)";

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return reviews.filter((r) => {
      if (chefFilter !== "all" && r.chefId !== chefFilter) return false;
      if (q) {
        const haystack = `${chefName(r.chefId)} ${r.comment ?? ""} ${r.id}`.toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [reviews, chefFilter, search, chefs]);

  async function handleDelete(reviewId: string) {
    if (!confirm("Delete (flag) this review? This cannot be undone.")) return;
    setBusyId(reviewId);
    try {
      await deleteDoc(doc(db, "reviews", reviewId));
      await logActivity("Flagged/deleted review", "reviews", reviewId);
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading reviews…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <p className="text-sm text-apple-secondary">{filtered.length} reviews</p>
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

      <div className="grid gap-4 md:grid-cols-2">
        {filtered.map((review) => (
          <div
            key={review.id}
            className="bg-apple-surface rounded-2xl border border-apple-border p-5 shadow-card hover:shadow-card-hover transition-shadow duration-200"
          >
            <div className="flex items-start justify-between">
              <div>
                <div className="font-semibold text-apple-text">{chefName(review.chefId)}</div>
                <div className="text-xs text-apple-secondary">{formatDateTime(review.createdAt)}</div>
              </div>
              <div className="text-sm font-semibold text-apple-blue flex items-center gap-1">
                <Star size={14} fill="currentColor" /> {review.overallRating?.toFixed(1) ?? "–"}
              </div>
            </div>

            <p className="text-sm text-apple-secondary mt-2">{review.comment}</p>

            <div className="grid grid-cols-4 gap-1 text-xs text-apple-secondary mt-3">
              <div>Taste: {review.tasteRating ?? "–"}</div>
              <div>Hygiene: {review.hygieneRating ?? "–"}</div>
              <div>Pkg: {review.packagingRating ?? "–"}</div>
              <div>On-time: {review.onTimeRating ?? "–"}</div>
            </div>

            <div className="mt-3 pt-3 border-t border-apple-border">
              <button
                onClick={() => handleDelete(review.id)}
                disabled={busyId === review.id}
                className="text-xs text-apple-red hover:opacity-80 transition-opacity duration-150"
              >
                Flag / delete
              </button>
            </div>
          </div>
        ))}
      </div>

      {filtered.length === 0 && <p className="text-sm text-apple-secondary">No reviews found.</p>}
    </div>
  );
}
