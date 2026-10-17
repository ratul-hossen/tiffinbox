import { useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { Star } from "lucide-react";
import { db } from "../firebase";
import { logActivity } from "../lib/activityLog";
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
              {chef.isVerified ? "Approved" : "Pending approval"}
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

        <MaxDeliveryFeeEditor key={chef.id} chef={chef} />

        <section className="bg-apple-bg rounded-xl p-3 text-xs space-y-1.5">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-apple-text">Meal Plan Template</span>
            <Badge tone={chef.mealPlanEnabled !== false ? "green" : "neutral"}>
              {chef.mealPlanEnabled !== false ? "Enabled" : "Disabled"}
            </Badge>
          </div>
          {chef.mealPlanDescription && (
            <p className="text-apple-secondary italic">"{chef.mealPlanDescription}"</p>
          )}
          <div className="text-apple-secondary">
            Rate: {chef.mealPlanPricePerMealMin ? `${formatCurrency(chef.mealPlanPricePerMealMin)} - ${formatCurrency(chef.mealPlanPricePerMealMax ?? chef.mealPlanPricePerMealMin)}/meal` : "Not set (menu average)"}
          </div>
          {chef.mealPlanPackages && chef.mealPlanPackages.length > 0 && (
            <div className="text-apple-secondary">
              Packages: {chef.mealPlanPackages.join(", ")}
            </div>
          )}
          {chef.mealPlanMealTypes && chef.mealPlanMealTypes.length > 0 && (
            <div className="text-apple-secondary">
              Meal Slots: {chef.mealPlanMealTypes.join(", ")}
            </div>
          )}
        </section>

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

const MAX_FEE_LIMIT = 1000;

/** Sets the ceiling on what this kitchen may charge when it delivers itself. */
function MaxDeliveryFeeEditor({ chef }: { chef: Chef }) {
  const current = chef.maxDeliveryFee ?? 0;
  const [value, setValue] = useState(current > 0 ? String(current) : "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const hasOwnRates =
    (chef.selfDeliveryBaseFee ?? -1) >= 0 ||
    (chef.selfDeliveryPerKmFee ?? -1) >= 0 ||
    (chef.selfDeliveryIncludedKm ?? -1) >= 0;

  async function save() {
    const trimmed = value.trim();
    const amount = trimmed === "" ? 0 : Number(trimmed);
    if (!Number.isInteger(amount) || amount < 0 || amount > MAX_FEE_LIMIT) {
      setError(`Enter a whole number from 0 to ${MAX_FEE_LIMIT}. 0 uses the App Settings default.`);
      return;
    }
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await updateDoc(doc(db, "chefs", chef.id), { maxDeliveryFee: amount });
      await logActivity("Set chef max delivery fee", "chefs", chef.id, { maxDeliveryFee: amount });
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="rounded-2xl border border-apple-border bg-apple-surface p-4 space-y-3">
      <div>
        <h4 className="text-sm font-semibold text-apple-text">Delivery charge cap</h4>
        <p className="text-xs text-apple-secondary mt-1">
          The highest fee this kitchen may charge when it delivers itself. The chef can set a lower
          fee in their app but never above this. 0 uses the App Settings default.
        </p>
      </div>

      <div className="flex items-center gap-2">
        <span className="text-sm text-apple-secondary">৳</span>
        <input
          type="number"
          inputMode="numeric"
          min={0}
          max={MAX_FEE_LIMIT}
          value={value}
          placeholder="0"
          onChange={(e) => {
            setValue(e.target.value);
            setSaved(false);
          }}
          className="w-32 rounded-xl border border-apple-border bg-apple-bg px-3 py-2 text-sm"
        />
        <button
          type="button"
          onClick={save}
          disabled={busy}
          className="rounded-xl bg-apple-blue px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {busy ? "Saving…" : "Save cap"}
        </button>
        {saved && <span className="text-xs text-apple-green">Saved</span>}
      </div>

      {error && <p className="text-xs text-apple-red">{error}</p>}

      <p className="text-xs text-apple-secondary">
        Current cap: {current > 0 ? formatCurrency(current) : "App Settings default"}
      </p>
      <p className="text-xs text-apple-secondary">
        Chef's own rates:{" "}
        {hasOwnRates
          ? `base ${formatCurrency(chef.selfDeliveryBaseFee ?? 0)}, ${formatCurrency(
              chef.selfDeliveryPerKmFee ?? 0,
            )}/km after ${chef.selfDeliveryIncludedKm ?? 0} km`
          : "not set (app default applies)"}
      </p>
    </section>
  );
}
