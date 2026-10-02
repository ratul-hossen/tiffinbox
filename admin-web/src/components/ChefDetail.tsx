import { Star } from "lucide-react";
import DetailDrawer from "./ui/DetailDrawer";
import Badge from "./ui/Badge";
import type { Chef, Meal, Order, Review } from "../types";
import { formatCurrency, formatDateTime } from "../lib/format";
import { STATUS_BADGE_TONE } from "../lib/orderStatus";

interface Props {
  chef: Chef | null;
  meals: Meal[];
  orders: Order[];
  reviews: Review[];
  onClose: () => void;
}

/** Full detail view for one chef: profile, their meals, recent orders, and reviews. */
export default function ChefDetail({ chef, meals, orders, reviews, onClose }: Props) {
  if (!chef) return null;

  const chefMeals = meals.filter((m) => m.chefId === chef.id);
  const chefOrders = orders
    .filter((o) => o.chefId === chef.id)
    .sort((a, b) => (b.placedAt ?? 0) - (a.placedAt ?? 0))
    .slice(0, 10);
  const chefReviews = reviews.filter((r) => r.chefId === chef.id);

  return (
    <DetailDrawer
      open={!!chef}
      onClose={onClose}
      title={chef.kitchenName ?? "(unnamed kitchen)"}
      subtitle={chef.ownerName}
    >
      <div className="space-y-6">
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Badge tone={chef.isVerified ? "green" : "neutral"}>
              {chef.isVerified ? "Verified" : "Not verified"}
            </Badge>
            <Badge tone={chef.isAvailableToday ? "blue" : "neutral"}>
              {chef.isAvailableToday ? "Available today" : "Unavailable"}
            </Badge>
          </div>
          <p className="text-sm text-apple-secondary">{chef.bio || "No bio."}</p>
          {chef.specialty && chef.specialty.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-2">
              {chef.specialty.map((s) => (
                <span key={s} className="text-xs bg-apple-blue/10 text-apple-blue rounded-full px-2 py-0.5">
                  {s}
                </span>
              ))}
            </div>
          )}
          <div className="grid grid-cols-3 gap-2 text-xs text-apple-secondary mt-3">
            <div>Rating: {chef.overallRating ?? "–"}</div>
            <div>Orders: {chef.totalOrders ?? 0}</div>
            <div>Distance: {chef.distanceKm ?? "–"} km</div>
          </div>
          <div className="text-xs text-apple-secondary mt-2 break-all">ID: {chef.id}</div>
        </div>

        <section>
          <h4 className="text-sm font-semibold text-apple-text mb-2">
            Meals ({chefMeals.length})
          </h4>
          {chefMeals.length === 0 ? (
            <p className="text-xs text-apple-secondary">No meals listed.</p>
          ) : (
            <ul className="space-y-1.5">
              {chefMeals.map((m) => (
                <li
                  key={m.id}
                  className="flex items-center justify-between text-sm bg-apple-bg rounded-xl px-3 py-2"
                >
                  <span className="truncate">
                    {m.emoji ?? "🍽️"} {m.name}
                  </span>
                  <span className="text-apple-secondary shrink-0 ml-2">{formatCurrency(m.price ?? 0)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <h4 className="text-sm font-semibold text-apple-text mb-2">
            Recent orders ({chefOrders.length})
          </h4>
          {chefOrders.length === 0 ? (
            <p className="text-xs text-apple-secondary">No orders yet.</p>
          ) : (
            <ul className="space-y-1.5">
              {chefOrders.map((o) => (
                <li
                  key={o.id}
                  className="flex items-center justify-between text-sm bg-apple-bg rounded-xl px-3 py-2"
                >
                  <div className="truncate">
                    <div className="font-medium">{formatCurrency(o.totalPrice ?? 0)}</div>
                    <div className="text-xs text-apple-secondary">{formatDateTime(o.placedAt)}</div>
                  </div>
                  <Badge tone={STATUS_BADGE_TONE[(o.status as keyof typeof STATUS_BADGE_TONE) ?? "Placed"] ?? "neutral"}>
                    {o.status ?? "Placed"}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <h4 className="text-sm font-semibold text-apple-text mb-2">
            Reviews ({chefReviews.length})
          </h4>
          {chefReviews.length === 0 ? (
            <p className="text-xs text-apple-secondary">No reviews yet.</p>
          ) : (
            <ul className="space-y-2">
              {chefReviews.map((r) => (
                <li key={r.id} className="bg-apple-bg rounded-xl px-3 py-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-apple-secondary">{formatDateTime(r.createdAt)}</span>
                    <span className="text-sm font-semibold text-apple-blue flex items-center gap-1">
                      <Star size={12} fill="currentColor" /> {r.overallRating?.toFixed(1) ?? "–"}
                    </span>
                  </div>
                  {r.comment && <p className="text-sm text-apple-text mt-1">{r.comment}</p>}
                  <div className="grid grid-cols-4 gap-1 text-xs text-apple-secondary mt-2">
                    <div>Taste {r.tasteRating ?? "–"}</div>
                    <div>Hygiene {r.hygieneRating ?? "–"}</div>
                    <div>Pkg {r.packagingRating ?? "–"}</div>
                    <div>On-time {r.onTimeRating ?? "–"}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </DetailDrawer>
  );
}
