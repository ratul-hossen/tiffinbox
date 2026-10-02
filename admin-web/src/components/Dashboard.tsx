import { useMemo } from "react";
import { ChefHat, Package, Repeat, Soup, Users as UsersIcon, Wallet } from "lucide-react";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Meal, AppUser, Order, OrderStatus, Subscription } from "../types";
import { ORDER_STATUSES } from "../types";
import { STATUS_CHART_COLOR } from "../lib/orderStatus";
import { formatCurrency } from "../lib/format";
import StatCard from "./StatCard";

export default function Dashboard() {
  const { data: chefs } = useCollection<Chef>("chefs");
  const { data: meals } = useCollection<Meal>("meals");
  const { data: users } = useCollection<AppUser>("users");
  const { data: orders, loading: ordersLoading } = useCollection<Order>("orders");
  const { data: subscriptions } = useCollection<Subscription>("subscriptions");

  const totalRevenue = useMemo(
    () => orders.reduce((sum, o) => sum + (o.totalPrice ?? 0), 0),
    [orders],
  );

  const activeSubscriptions = useMemo(
    () => subscriptions.filter((s) => s.isActive).length,
    [subscriptions],
  );

  const customers = useMemo(() => users.filter((u) => u.role === "CUSTOMER").length, [users]);

  const ordersPerDay = useMemo(() => {
    const days: { date: string; label: string; count: number }[] = [];
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    for (let i = 13; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const key = d.toISOString().slice(0, 10);
      days.push({
        date: key,
        label: d.toLocaleDateString(undefined, { month: "short", day: "numeric" }),
        count: 0,
      });
    }

    const byKey = new Map(days.map((d) => [d.date, d]));
    for (const order of orders) {
      if (!order.placedAt) continue;
      const key = new Date(order.placedAt).toISOString().slice(0, 10);
      const bucket = byKey.get(key);
      if (bucket) bucket.count += 1;
    }

    return days;
  }, [orders]);

  const statusBreakdown = useMemo(() => {
    const counts = new Map<string, number>();
    for (const o of orders) {
      const status = (o.status as string) ?? "Placed";
      counts.set(status, (counts.get(status) ?? 0) + 1);
    }
    return ORDER_STATUSES.map((status) => ({
      status,
      count: counts.get(status) ?? 0,
      fill: STATUS_CHART_COLOR[status as OrderStatus],
    })).filter((d) => d.count > 0);
  }, [orders]);

  const topChefs = useMemo(() => {
    const byChef = new Map<string, { orders: number; revenue: number }>();
    for (const o of orders) {
      if (!o.chefId) continue;
      const entry = byChef.get(o.chefId) ?? { orders: 0, revenue: 0 };
      entry.orders += 1;
      entry.revenue += o.totalPrice ?? 0;
      byChef.set(o.chefId, entry);
    }
    const chefName = (id: string) => chefs.find((c) => c.id === id)?.kitchenName ?? id;
    return Array.from(byChef.entries())
      .map(([chefId, stats]) => ({ chefId, name: chefName(chefId), ...stats }))
      .sort((a, b) => b.orders - a.orders)
      .slice(0, 5);
  }, [orders, chefs]);

  const maxTopChefOrders = Math.max(1, ...topChefs.map((c) => c.orders));
  const maxDailyOrders = Math.max(1, ...ordersPerDay.map((d) => d.count));

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold text-apple-text tracking-tight">Dashboard</h2>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
        <StatCard label="Chefs" value={chefs.length} icon={ChefHat} />
        <StatCard label="Meals" value={meals.length} icon={Soup} />
        <StatCard label="Customers" value={customers} icon={UsersIcon} />
        <StatCard label="Orders" value={orders.length} icon={Package} />
        <StatCard label="Revenue" value={formatCurrency(totalRevenue)} icon={Wallet} />
        <StatCard label="Active subs" value={activeSubscriptions} icon={Repeat} />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 bg-apple-surface rounded-2xl border border-black/5 p-6 shadow-card hover:shadow-card-hover transition-shadow duration-200">
          <h3 className="text-sm font-semibold text-apple-text mb-4">Orders per day (last 14 days)</h3>
          {ordersLoading ? (
            <p className="text-sm text-apple-secondary">Loading…</p>
          ) : (
            <div className="flex items-end gap-1.5" style={{ height: 200 }}>
              {ordersPerDay.map((d) => (
                <div key={d.date} className="flex-1 flex flex-col items-center gap-1.5 group">
                  <span className="text-[10px] text-apple-secondary opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                    {d.count}
                  </span>
                  <div
                    className="w-full bg-apple-blue rounded-t-md transition-all duration-200 group-hover:bg-apple-blue-dark"
                    style={{ height: `${Math.max(4, (d.count / maxDailyOrders) * 160)}px` }}
                  />
                  <span className="text-[10px] text-apple-secondary whitespace-nowrap">{d.label}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="bg-apple-surface rounded-2xl border border-black/5 p-6 shadow-card hover:shadow-card-hover transition-shadow duration-200">
          <h3 className="text-sm font-semibold text-apple-text mb-4">Orders by status</h3>
          {statusBreakdown.length === 0 ? (
            <p className="text-sm text-apple-secondary">No orders yet.</p>
          ) : (
            <StatusDonut data={statusBreakdown} />
          )}
        </div>
      </div>

      <div className="bg-apple-surface rounded-2xl border border-black/5 p-6 shadow-card hover:shadow-card-hover transition-shadow duration-200">
        <h3 className="text-sm font-semibold text-apple-text mb-4">Top chefs by order count</h3>
        {topChefs.length === 0 ? (
          <p className="text-sm text-apple-secondary">No orders yet.</p>
        ) : (
          <div className="space-y-3">
            {topChefs.map((c, idx) => (
              <div key={c.chefId} className="flex items-center gap-3">
                <span className="w-5 text-xs font-semibold text-apple-secondary text-right">
                  {idx + 1}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between text-sm mb-1">
                    <span className="font-medium text-apple-text truncate">{c.name}</span>
                    <span className="text-apple-secondary shrink-0 ml-2">
                      {c.orders} orders · {formatCurrency(c.revenue)}
                    </span>
                  </div>
                  <div className="h-1.5 bg-black/5 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-apple-blue rounded-full"
                      style={{ width: `${(c.orders / maxTopChefOrders) * 100}%` }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function StatusDonut({ data }: { data: { status: string; count: number; fill: string }[] }) {
  const total = data.reduce((sum, d) => sum + d.count, 0);
  const radius = 42;
  const circumference = 2 * Math.PI * radius;
  let offsetSoFar = 0;

  return (
    <div className="flex flex-col items-center gap-4">
      <svg viewBox="0 0 100 100" width={160} height={160} className="-rotate-90">
        <circle cx="50" cy="50" r={radius} fill="none" stroke="#00000010" strokeWidth={14} />
        {data.map((d) => {
          const fraction = total === 0 ? 0 : d.count / total;
          const dash = fraction * circumference;
          const gap = circumference - dash;
          const strokeDashoffset = -offsetSoFar;
          offsetSoFar += dash;
          return (
            <circle
              key={d.status}
              cx="50"
              cy="50"
              r={radius}
              fill="none"
              stroke={d.fill}
              strokeWidth={14}
              strokeDasharray={`${dash} ${gap}`}
              strokeDashoffset={strokeDashoffset}
              strokeLinecap="butt"
            />
          );
        })}
      </svg>
      <div className="flex flex-wrap justify-center gap-x-4 gap-y-1.5">
        {data.map((d) => (
          <div key={d.status} className="flex items-center gap-1.5 text-xs text-apple-secondary">
            <span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: d.fill }} />
            {d.status} ({d.count})
          </div>
        ))}
      </div>
    </div>
  );
}
