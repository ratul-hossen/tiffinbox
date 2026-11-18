import { useMemo, useState } from "react";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Order } from "../types";
import { AGENT_PENDING_STATUSES } from "../types";
import { formatCurrency } from "../lib/format";
import StatCard from "./StatCard";

type Range = "7" | "30" | "90" | "all";
type Grouping = "day" | "month";

const RANGES: { key: Range; label: string }[] = [
  { key: "7", label: "7 days" },
  { key: "30", label: "30 days" },
  { key: "90", label: "90 days" },
  { key: "all", label: "All time" },
];

const DAY_MS = 24 * 60 * 60 * 1000;

function bucketKey(ts: number, grouping: Grouping): string {
  const d = new Date(ts);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  if (grouping === "month") return `${y}-${m}`;
  return `${y}-${m}-${String(d.getDate()).padStart(2, "0")}`;
}

function bucketLabel(key: string, grouping: Grouping): string {
  if (grouping === "month") {
    const [y, m] = key.split("-").map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: "short", year: "2-digit" });
  }
  const [y, m, d] = key.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

/**
 * Revenue report. "Sales" counts every order that wasn't cancelled (an
 * order's totalPrice already has any coupon discount taken off). "Platform
 * earnings" = platformFee + deliveryFee on those orders - TiffinBox's share;
 * the rest goes to chefs. Computed client-side from the live orders
 * collection (no Cloud Functions / aggregation on the free plan).
 */
export default function RevenuePage() {
  const { data: orders, loading, error } = useCollection<Order>("orders");
  const { data: chefs } = useCollection<Chef>("chefs");
  const [range, setRange] = useState<Range>("30");
  const [grouping, setGrouping] = useState<Grouping>("day");
  const [hovered, setHovered] = useState<string | null>(null);

  const inRange = useMemo(() => {
    const cutoff = range === "all" ? 0 : Date.now() - Number(range) * DAY_MS;
    return orders.filter((o) => (o.placedAt ?? 0) >= cutoff);
  }, [orders, range]);

  // Unverified agent orders aren't sales yet.
  const completed = useMemo(
    () => inRange.filter((o) => o.status !== "Cancelled" && !AGENT_PENDING_STATUSES.includes(String(o.status))),
    [inRange],
  );
  const cancelledCount = inRange.length - completed.length;

  const totals = useMemo(() => {
    const sales = completed.reduce((s, o) => s + (o.totalPrice ?? 0), 0);
    const platform = completed.reduce((s, o) => s + (o.platformFee ?? 0) + (o.deliveryFee ?? 0), 0);
    const discounts = completed.reduce((s, o) => s + (o.discount ?? 0), 0);
    return { sales, platform, discounts, avg: completed.length ? Math.round(sales / completed.length) : 0 };
  }, [completed]);

  // Continuous buckets (empty days/months show as zero, not skipped).
  const buckets = useMemo(() => {
    const sums = new Map<string, { revenue: number; orders: number }>();
    completed.forEach((o) => {
      if (!o.placedAt) return;
      const key = bucketKey(o.placedAt, grouping);
      const entry = sums.get(key) ?? { revenue: 0, orders: 0 };
      entry.revenue += o.totalPrice ?? 0;
      entry.orders += 1;
      sums.set(key, entry);
    });
    const earliest =
      range === "all"
        ? Math.min(...completed.map((o) => o.placedAt ?? Date.now()), Date.now())
        : Date.now() - (Number(range) - 1) * DAY_MS;
    const keys: string[] = [];
    const cursor = new Date(earliest);
    cursor.setHours(0, 0, 0, 0);
    if (grouping === "month") cursor.setDate(1);
    const end = Date.now();
    while (cursor.getTime() <= end && keys.length < 400) {
      keys.push(bucketKey(cursor.getTime(), grouping));
      if (grouping === "month") cursor.setMonth(cursor.getMonth() + 1);
      else cursor.setDate(cursor.getDate() + 1);
    }
    return keys.map((key) => ({ key, ...(sums.get(key) ?? { revenue: 0, orders: 0 }) }));
  }, [completed, grouping, range]);

  const maxRevenue = Math.max(1, ...buckets.map((b) => b.revenue));

  const topChefs = useMemo(() => {
    const byChef = new Map<string, { revenue: number; orders: number }>();
    completed.forEach((o) => {
      const id = o.chefId ?? "unknown";
      const entry = byChef.get(id) ?? { revenue: 0, orders: 0 };
      entry.revenue += o.totalPrice ?? 0;
      entry.orders += 1;
      byChef.set(id, entry);
    });
    return [...byChef.entries()]
      .map(([id, v]) => ({ id, name: chefs.find((c) => c.id === id)?.kitchenName ?? id, ...v }))
      .sort((a, b) => b.revenue - a.revenue)
      .slice(0, 10);
  }, [completed, chefs]);

  if (loading) return <p className="text-sm text-apple-secondary">Loading orders…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  const hoveredBucket = buckets.find((b) => b.key === hovered);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-2">
        {RANGES.map((r) => (
          <button
            key={r.key}
            onClick={() => setRange(r.key)}
            className={`text-sm rounded-full px-4 py-1.5 transition-colors duration-150 ${
              range === r.key
                ? "bg-apple-blue text-white font-medium"
                : "bg-apple-surface border border-apple-border text-apple-secondary hover:text-apple-text"
            }`}
          >
            {r.label}
          </button>
        ))}
        <div className="ml-auto flex rounded-full bg-apple-subtle p-1 text-sm">
          {(["day", "month"] as Grouping[]).map((g) => (
            <button
              key={g}
              onClick={() => setGrouping(g)}
              className={`px-3 py-1 rounded-full ${grouping === g ? "bg-apple-surface text-apple-text shadow-card font-medium" : "text-apple-secondary"}`}
            >
              {g === "day" ? "Daily" : "Monthly"}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard label="Sales" value={formatCurrency(totals.sales)} caption={`${completed.length} orders`} />
        <StatCard label="Platform earnings" value={formatCurrency(totals.platform)} caption="Platform + delivery fees" />
        <StatCard label="Avg. order" value={formatCurrency(totals.avg)} />
        <StatCard label="Coupon discounts" value={formatCurrency(totals.discounts)} />
        <StatCard label="Cancelled" value={cancelledCount} caption="Not counted in sales" />
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5">
        <div className="flex items-baseline justify-between mb-4">
          <h3 className="font-semibold text-apple-text">{grouping === "day" ? "Daily sales" : "Monthly sales"}</h3>
          <div className="text-sm text-apple-secondary h-5">
            {hoveredBucket
              ? `${bucketLabel(hoveredBucket.key, grouping)} · ${formatCurrency(hoveredBucket.revenue)} · ${hoveredBucket.orders} orders`
              : "Hover a bar for details"}
          </div>
        </div>
        {completed.length === 0 ? (
          <p className="text-sm text-apple-secondary py-10 text-center">No sales in this period yet.</p>
        ) : (
          <>
            <div className="flex items-end gap-[2px] h-56 border-b border-apple-border" onMouseLeave={() => setHovered(null)}>
              {buckets.map((b) => (
                <div
                  key={b.key}
                  className="flex-1 h-full flex items-end cursor-default"
                  onMouseEnter={() => setHovered(b.key)}
                  title={`${bucketLabel(b.key, grouping)}: ${formatCurrency(b.revenue)} (${b.orders} orders)`}
                >
                  <div
                    className={`w-full rounded-t-[4px] transition-colors ${
                      hovered === b.key ? "bg-apple-blue" : "bg-apple-blue/70"
                    }`}
                    style={{ height: `${(b.revenue / maxRevenue) * 100}%`, minHeight: b.revenue > 0 ? 2 : 0 }}
                  />
                </div>
              ))}
            </div>
            <div className="flex justify-between text-[11px] text-apple-secondary mt-2">
              <span>{buckets[0] && bucketLabel(buckets[0].key, grouping)}</span>
              <span>Peak {formatCurrency(maxRevenue)}</span>
              <span>{buckets.length > 0 && bucketLabel(buckets[buckets.length - 1].key, grouping)}</span>
            </div>
          </>
        )}
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-x-auto">
        <div className="px-5 pt-5 pb-2 font-semibold text-apple-text">Top chefs</div>
        <table className="w-full text-sm">
          <thead className="text-apple-secondary text-xs uppercase tracking-wide">
            <tr>
              <th className="text-left px-5 py-2 font-medium">#</th>
              <th className="text-left px-5 py-2 font-medium">Kitchen</th>
              <th className="text-right px-5 py-2 font-medium">Orders</th>
              <th className="text-right px-5 py-2 font-medium">Sales</th>
              <th className="text-right px-5 py-2 font-medium">Share</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-apple-border">
            {topChefs.map((c, i) => (
              <tr key={c.id}>
                <td className="px-5 py-2.5 text-apple-secondary">{i + 1}</td>
                <td className="px-5 py-2.5 text-apple-text font-medium">{c.name}</td>
                <td className="px-5 py-2.5 text-right text-apple-secondary">{c.orders}</td>
                <td className="px-5 py-2.5 text-right text-apple-text">{formatCurrency(c.revenue)}</td>
                <td className="px-5 py-2.5 text-right text-apple-secondary">
                  {totals.sales ? Math.round((c.revenue / totals.sales) * 100) : 0}%
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {topChefs.length === 0 && <p className="text-sm text-apple-secondary px-5 pb-5">No sales yet.</p>}
      </div>
    </div>
  );
}
