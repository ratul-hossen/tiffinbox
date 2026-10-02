import { useMemo, useState } from "react";
import { signOut } from "firebase/auth";
import {
  ChefHat,
  History,
  LayoutDashboard,
  LogOut,
  Package,
  Soup,
  Star,
  Repeat,
  ShieldCheck,
  Users as UsersIcon,
  type LucideIcon,
} from "lucide-react";
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
import ActivityLogPage from "./components/ActivityLogPage";
import AdminsPage from "./components/AdminsPage";
import SearchInput from "./components/ui/SearchInput";

type Tab =
  | "dashboard"
  | "chefs"
  | "meals"
  | "users"
  | "orders"
  | "reviews"
  | "subscriptions"
  | "activity"
  | "admins";

const TABS: { key: Tab; label: string; icon: LucideIcon; searchPlaceholder?: string }[] = [
  { key: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { key: "chefs", label: "Chefs", icon: ChefHat, searchPlaceholder: "Search chefs…" },
  { key: "meals", label: "Meals", icon: Soup, searchPlaceholder: "Search meals…" },
  { key: "users", label: "Users", icon: UsersIcon, searchPlaceholder: "Search users…" },
  { key: "orders", label: "Orders", icon: Package, searchPlaceholder: "Search orders…" },
  { key: "reviews", label: "Reviews", icon: Star, searchPlaceholder: "Search reviews…" },
  {
    key: "subscriptions",
    label: "Subscriptions",
    icon: Repeat,
    searchPlaceholder: "Search subscriptions…",
  },
  { key: "activity", label: "Activity Log", icon: History, searchPlaceholder: "Search activity…" },
  { key: "admins", label: "Admins", icon: ShieldCheck },
];

export default function App() {
  const { status, user, isSuperAdmin, recheck } = useAdminAuth();
  const [tab, setTab] = useState<Tab>("dashboard");
  const [search, setSearch] = useState("");

  const activeTabMeta = useMemo(() => TABS.find((t) => t.key === tab)!, [tab]);

  function selectTab(next: Tab) {
    setTab(next);
    setSearch("");
  }

  if (status === "loading" || status === "checking-admin") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-apple-bg">
        <p className="text-sm text-apple-secondary">Loading…</p>
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
    <div className="min-h-screen flex flex-col md:flex-row bg-apple-bg">
      <aside className="md:w-64 md:h-screen md:sticky md:top-0 flex flex-col bg-white/70 backdrop-blur-xl2 border-b md:border-b-0 md:border-r border-black/5 z-20">
        <div className="px-5 py-5">
          <div className="text-lg font-bold text-apple-text tracking-tight flex items-center gap-2">
            <span className="text-xl">🍱</span> TiffinBox
          </div>
          <div className="text-xs text-apple-secondary mt-0.5">Admin panel</div>
        </div>
        <nav className="flex-1 overflow-y-auto px-3 pb-3 flex flex-row md:flex-col gap-1 overflow-x-auto md:overflow-x-visible">
          {TABS.map((t) => {
            const Icon = t.icon;
            const active = tab === t.key;
            return (
              <button
                key={t.key}
                onClick={() => selectTab(t.key)}
                className={`flex items-center gap-2.5 text-left px-3.5 py-2.5 text-sm rounded-xl whitespace-nowrap transition-all duration-150 ${
                  active
                    ? "bg-apple-blue text-white font-medium shadow-card"
                    : "text-apple-secondary hover:bg-black/5 hover:text-apple-text"
                }`}
              >
                <Icon size={17} strokeWidth={2} />
                {t.label}
              </button>
            );
          })}
        </nav>
        <div className="px-5 py-4 border-t border-black/5 text-xs text-apple-secondary hidden md:block">
          <div className="truncate mb-2 text-apple-text font-medium">{user?.email}</div>
          <button
            onClick={() => signOut(auth)}
            className="inline-flex items-center gap-1.5 text-apple-secondary hover:text-apple-red transition-colors duration-150"
          >
            <LogOut size={14} /> Sign out
          </button>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="sticky top-0 z-10 bg-white/70 backdrop-blur-xl2 border-b border-black/5 px-4 md:px-8 py-3 flex items-center justify-between gap-4">
          <h1 className="text-base font-semibold text-apple-text tracking-tight shrink-0">
            {activeTabMeta.label}
          </h1>
          {activeTabMeta.searchPlaceholder && (
            <SearchInput value={search} onChange={setSearch} placeholder={activeTabMeta.searchPlaceholder} />
          )}
          <button
            onClick={() => signOut(auth)}
            className="md:hidden inline-flex items-center gap-1.5 text-xs text-apple-secondary hover:text-apple-red transition-colors duration-150 shrink-0"
          >
            <LogOut size={14} /> Sign out
          </button>
        </header>

        <main className="flex-1 p-4 md:p-8 overflow-y-auto">
          {tab === "dashboard" && <Dashboard />}
          {tab === "chefs" && <ChefsPage search={search} />}
          {tab === "meals" && <MealsPage search={search} />}
          {tab === "users" && <UsersPage search={search} />}
          {tab === "orders" && <OrdersPage search={search} />}
          {tab === "reviews" && <ReviewsPage search={search} />}
          {tab === "subscriptions" && <SubscriptionsPage search={search} />}
          {tab === "activity" && <ActivityLogPage search={search} />}
          {tab === "admins" && <AdminsPage isSuperAdmin={isSuperAdmin} />}
        </main>
      </div>
    </div>
  );
}
