import { useMemo } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useCollection } from "../hooks/useCollection";
import type { Chef, Meal, AppUser, Order, Subscription } from "../types";
import StatCard from "./StatCard";

function formatCurrency(n: number) {
  return `৳${n.toLocaleString(undefined, { maximumFractionDigits: 0 })}`;
}

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

  return (
    <div className="space-y-6">
      <h2 className="text-lg font-semibold text-slate-900">Dashboard</h2>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
        <StatCard label="Chefs" value={chefs.length} emoji="👨‍🍳" />
        <StatCard label="Meals" value={meals.length} emoji="🍲" />
        <StatCard label="Customers" value={customers} emoji="🧑‍🤝‍🧑" />
        <StatCard label="Orders" value={orders.length} emoji="📦" />
        <StatCard label="Revenue" value={formatCurrency(totalRevenue)} emoji="💰" />
        <StatCard label="Active subs" value={activeSubscriptions} emoji="🔁" />
      </div>

      <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
        <h3 className="text-sm font-medium text-slate-700 mb-3">Orders per day (last 14 days)</h3>
        {ordersLoading ? (
          <p className="text-sm text-slate-400">Loading…</p>
        ) : (
          <div style={{ width: "100%", height: 240 }}>
            <ResponsiveContainer>
              <BarChart data={ordersPerDay} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="label" tick={{ fontSize: 11, fill: "#64748b" }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: "#64748b" }} />
                <Tooltip />
                <Bar dataKey="count" fill="#f97316" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </div>
  );
}
