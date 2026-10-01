import { useState } from "react";
import { signOut } from "firebase/auth";
import { auth } from "./firebase";
import { useAdminAuth } from "./hooks/useAdminAuth";
import Login from "./components/Login";
import AccessDenied from "./components/AccessDenied";
import Dashboard from "./components/Dashboard";
import ChefsPage from "./components/ChefsPage";
import MealsPage from "./components/MealsPage";
import UsersPage from "./components/UsersPage";
import OrdersPage from "./components/OrdersPage";
import ReviewsPage from "./components/ReviewsPage";
import SubscriptionsPage from "./components/SubscriptionsPage";

type Tab = "dashboard" | "chefs" | "meals" | "users" | "orders" | "reviews" | "subscriptions";

const TABS: { key: Tab; label: string; emoji: string }[] = [
  { key: "dashboard", label: "Dashboard", emoji: "📊" },
  { key: "chefs", label: "Chefs", emoji: "👨‍🍳" },
  { key: "meals", label: "Meals", emoji: "🍲" },
  { key: "users", label: "Users", emoji: "🧑‍🤝‍🧑" },
  { key: "orders", label: "Orders", emoji: "📦" },
  { key: "reviews", label: "Reviews", emoji: "⭐" },
  { key: "subscriptions", label: "Subscriptions", emoji: "🔁" },
];

export default function App() {
  const { status, user, recheck } = useAdminAuth();
  const [tab, setTab] = useState<Tab>("dashboard");

  if (status === "loading" || status === "checking-admin") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <p className="text-sm text-slate-400">Loading…</p>
      </div>
    );
  }

  if (status === "signed-out") {
    return <Login />;
  }

  if (status === "denied") {
    return <AccessDenied user={user!} onBootstrapped={recheck} />;
  }

  // status === "admin"
  return (
    <div className="min-h-screen flex flex-col md:flex-row">
      <aside className="md:w-56 bg-slate-900 text-slate-200 flex flex-col">
        <div className="px-5 py-5 border-b border-slate-800">
          <div className="text-lg font-semibold text-white">🍱 TiffinBox</div>
          <div className="text-xs text-slate-400">Admin panel</div>
        </div>
        <nav className="flex-1 overflow-y-auto">
          {TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={`w-full flex items-center gap-2 text-left px-5 py-3 text-sm transition-colors ${
                tab === t.key
                  ? "bg-slate-800 text-white font-medium"
                  : "text-slate-300 hover:bg-slate-800/60"
              }`}
            >
              <span>{t.emoji}</span>
              {t.label}
            </button>
          ))}
        </nav>
        <div className="px-5 py-4 border-t border-slate-800 text-xs text-slate-400">
          <div className="truncate mb-2">{user?.email}</div>
          <button
            onClick={() => signOut(auth)}
            className="text-slate-300 hover:text-white underline"
          >
            Sign out
          </button>
        </div>
      </aside>

      <main className="flex-1 p-4 md:p-8 overflow-y-auto">
        {tab === "dashboard" && <Dashboard />}
        {tab === "chefs" && <ChefsPage />}
        {tab === "meals" && <MealsPage />}
        {tab === "users" && <UsersPage />}
        {tab === "orders" && <OrdersPage />}
        {tab === "reviews" && <ReviewsPage />}
        {tab === "subscriptions" && <SubscriptionsPage />}
      </main>
    </div>
  );
}
