import { useMemo, useState } from "react";
import { signOut } from "firebase/auth";
import {
  Bike,
  ChefHat,
  ExternalLink,
  History,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageCircle,
  Moon,
  Navigation,
  Package,
  ShieldCheck,
  Soup,
  Star,
  Repeat,
  Sun,
  Trash2,
  TrendingUp,
  TicketPercent,
  UserCheck,
  Users as UsersIcon,
  type LucideIcon,
} from "lucide-react";
import { auth } from "./firebase";
import { useAdminAuth } from "./hooks/useAdminAuth";
import { useTheme } from "./hooks/useTheme";
import Login from "./components/Login";
import AccessDenied from "./components/AccessDenied";
import Dashboard from "./components/Dashboard";
import ChefsPage from "./components/ChefsPage";
import RevenuePage from "./components/RevenuePage";
import CouponsPage from "./components/CouponsPage";
import ChefApprovalsPage from "./components/ChefApprovalsPage";
import PendingApprovalsCount from "./components/PendingApprovalsCount";
import MealsPage from "./components/MealsPage";
import UsersPage from "./components/UsersPage";
import OrdersPage from "./components/OrdersPage";
import ReviewsPage from "./components/ReviewsPage";
import SubscriptionsPage from "./components/SubscriptionsPage";
import ActivityLogPage from "./components/ActivityLogPage";
import AdminsPage from "./components/AdminsPage";
import ChatsPage from "./components/ChatsPage";
import DeliveryPartnersPage from "./components/DeliveryPartnersPage";
import SelfServiceRequestsPage from "./components/SelfServiceRequestsPage";
import RecycleBinPage from "./components/RecycleBinPage";
import SearchInput from "./components/ui/SearchInput";

type Tab =
  | "dashboard"
  | "revenue"
  | "coupons"
  | "chefApprovals"
  | "chefs"
  | "meals"
  | "users"
  | "orders"
  | "reviews"
  | "subscriptions"
  | "activity"
  | "admins"
  | "chats"
  | "deliveryPartners"
  | "selfServiceRequests"
  | "recycleBin";

interface TabMeta {
  key: Tab;
  label: string;
  icon: LucideIcon;
  group: string;
  searchPlaceholder?: string;
  description: string;
}

// Grouped the same way the sidebar renders them - small uppercase section
// labels above each cluster, mirroring the "YOU / JOURNEY / WORK / LIFE /
// SITE" grouping this redesign is modeled on.
const TABS: TabMeta[] = [
  {
    key: "dashboard",
    label: "Dashboard",
    icon: LayoutDashboard,
    group: "Overview",
    description: "Everything on TiffinBox, in one place.",
  },
  {
    key: "revenue",
    label: "Revenue",
    icon: TrendingUp,
    group: "Overview",
    description: "Sales, platform earnings and top chefs over time.",
  },
  {
    key: "chefApprovals",
    label: "Chef Approvals",
    icon: UserCheck,
    group: "Catalog",
    searchPlaceholder: "Search pending chefs…",
    description: "New chefs waiting for review - approve, ask for changes, or reject with a message.",
  },
  {
    key: "chefs",
    label: "Chefs",
    icon: ChefHat,
    group: "Catalog",
    searchPlaceholder: "Search chefs…",
    description: "Approve, edit, and manage every chef's kitchen profile.",
  },
  {
    key: "meals",
    label: "Meals",
    icon: Soup,
    group: "Catalog",
    searchPlaceholder: "Search meals…",
    description: "Every dish on the menu, across every chef.",
  },
  {
    key: "orders",
    label: "Orders",
    icon: Package,
    group: "Operations",
    searchPlaceholder: "Search orders…",
    description: "Track status, payment proof, and delivery handling.",
  },
  {
    key: "coupons",
    label: "Coupons",
    icon: TicketPercent,
    group: "Operations",
    searchPlaceholder: "Search coupons…",
    description: "Promo codes - platform-wide or per chef. Cancel any chef's coupon here too.",
  },
  {
    key: "subscriptions",
    label: "Subscriptions",
    icon: Repeat,
    group: "Operations",
    searchPlaceholder: "Search subscriptions…",
    description: "Active and past weekly/monthly meal plans.",
  },
  {
    key: "reviews",
    label: "Reviews",
    icon: Star,
    group: "Operations",
    searchPlaceholder: "Search reviews…",
    description: "Customer feedback on taste, hygiene, and delivery.",
  },
  {
    key: "selfServiceRequests",
    label: "Self Service",
    icon: Navigation,
    group: "Operations",
    description: "Point-to-point delivery requests awaiting a partner.",
  },
  {
    key: "users",
    label: "Users",
    icon: UsersIcon,
    group: "People",
    searchPlaceholder: "Search users…",
    description: "Every customer and chef account on the platform.",
  },
  {
    key: "deliveryPartners",
    label: "Delivery Partners",
    icon: Bike,
    group: "People",
    description: "Admin-provisioned delivery accounts - no public self-signup.",
  },
  {
    key: "admins",
    label: "Admins",
    icon: ShieldCheck,
    group: "People",
    description: "Who has access to this panel.",
  },
  {
    key: "chats",
    label: "Chats",
    icon: MessageCircle,
    group: "System",
    searchPlaceholder: "Search chats…",
    description: "Customer-chef conversations, moderated here.",
  },
  {
    key: "activity",
    label: "Activity Log",
    icon: History,
    group: "System",
    searchPlaceholder: "Search activity…",
    description: "An audit trail of every admin action.",
  },
  {
    key: "recycleBin",
    label: "Recycle Bin",
    icon: Trash2,
    group: "System",
    searchPlaceholder: "Search recycle bin…",
    description: "Deleted chefs, meals, reviews and more - restore or delete forever.",
  },
];

const GROUP_ORDER = ["Overview", "Catalog", "Operations", "People", "System"];

export default function App() {
  const { status, user, isSuperAdmin, recheck } = useAdminAuth();
  const { theme, toggleTheme } = useTheme();
  const [tab, setTab] = useState<Tab>("dashboard");
  const [search, setSearch] = useState("");
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const activeTabMeta = useMemo(() => TABS.find((t) => t.key === tab)!, [tab]);
  const groupedTabs = useMemo(
    () => GROUP_ORDER.map((group) => ({ group, tabs: TABS.filter((t) => t.group === group) })),
    [],
  );

  function selectTab(next: Tab) {
    setTab(next);
    setSearch("");
    setMobileNavOpen(false);
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
  const displayName = user?.displayName?.split(" ")[0] ?? user?.email?.split("@")[0] ?? "admin";

  return (
    <div className="min-h-screen flex flex-col md:flex-row bg-apple-bg">
      <header className="md:hidden sticky top-0 z-30 flex items-center justify-between gap-3 bg-apple-surface/90 backdrop-blur-xl2 border-b border-apple-border px-4 py-3">
        <button
          onClick={() => setMobileNavOpen((v) => !v)}
          className="p-1.5 -ml-1.5 text-apple-text"
          aria-label="Toggle navigation"
        >
          <Menu size={20} />
        </button>
        <span className="text-sm font-semibold text-apple-text">{activeTabMeta.label}</span>
        <button onClick={toggleTheme} className="p-1.5 -mr-1.5 text-apple-secondary" aria-label="Toggle theme">
          {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
        </button>
      </header>

      <aside
        className={`md:w-72 md:h-screen md:sticky md:top-0 md:flex flex-col bg-apple-surface border-r border-apple-border z-20 ${
          mobileNavOpen ? "flex" : "hidden"
        }`}
      >
        <div className="px-6 pt-6 pb-4 flex items-start justify-between">
          <div className="flex items-center gap-2.5">
            <span className="text-2xl leading-none">🍱</span>
            <div>
              <div className="text-base font-bold text-apple-text tracking-tight leading-tight">
                TiffinBox
              </div>
              <div className="text-xs text-apple-secondary leading-tight">Admin panel</div>
            </div>
          </div>
          <button
            onClick={toggleTheme}
            className="hidden md:inline-flex p-1.5 rounded-lg text-apple-secondary hover:bg-apple-hover hover:text-apple-text transition-colors duration-150"
            aria-label="Toggle theme"
            title={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
          >
            {theme === "dark" ? <Sun size={16} /> : <Moon size={16} />}
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 pb-3 space-y-5">
          {groupedTabs.map(({ group, tabs }) => (
            <div key={group}>
              <div className="px-3 mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-apple-secondary/80">
                {group}
              </div>
              <div className="space-y-0.5">
                {tabs.map((t) => {
                  const Icon = t.icon;
                  const active = tab === t.key;
                  return (
                    <button
                      key={t.key}
                      onClick={() => selectTab(t.key)}
                      className={`w-full flex items-center gap-2.5 text-left px-3 py-2 text-sm rounded-lg transition-colors duration-150 ${
                        active
                          ? "bg-apple-blue/15 text-apple-blue font-medium"
                          : "text-apple-secondary hover:bg-apple-hover hover:text-apple-text"
                      }`}
                    >
                      <Icon size={16} strokeWidth={2} className={active ? "text-apple-blue" : undefined} />
                      {t.label}
                      {t.key === "chefApprovals" && <PendingApprovalsCount />}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        <div className="px-4 pb-4 pt-2 space-y-3">
          <div className="flex items-center gap-2 bg-apple-surface2 rounded-full px-3 py-1.5 text-xs text-apple-secondary">
            <span className="w-1.5 h-1.5 rounded-full bg-apple-green shrink-0" />
            Writes go live instantly
          </div>
          <a
            href="https://github.com/ratul-hossen/tiffinbox"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-1.5 text-xs text-apple-secondary hover:text-apple-text transition-colors duration-150"
          >
            View repo <ExternalLink size={12} />
          </a>
          <div className="border-t border-apple-border pt-3">
            <div className="truncate mb-2 text-xs text-apple-text font-medium">{user?.email}</div>
            <button
              onClick={() => signOut(auth)}
              className="inline-flex items-center gap-1.5 text-xs text-apple-secondary hover:text-apple-red transition-colors duration-150"
            >
              <LogOut size={13} /> Sign out
            </button>
          </div>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <div className="sticky top-0 z-10 hidden md:flex items-center justify-end gap-4 bg-apple-bg/80 backdrop-blur-xl2 px-8 py-4">
          {activeTabMeta.searchPlaceholder && (
            <SearchInput value={search} onChange={setSearch} placeholder={activeTabMeta.searchPlaceholder} />
          )}
        </div>

        <main className="flex-1 px-4 md:px-8 pb-10 overflow-y-auto fade-in" key={tab}>
          <div className="mb-6 md:mb-8 max-w-3xl">
            {tab === "dashboard" ? (
              <>
                <p className="text-sm font-medium text-apple-blue mb-1">Portfolio admin</p>
                <h1 className="text-3xl md:text-4xl font-bold text-apple-text tracking-tight capitalize">
                  Hello, {displayName}.
                </h1>
              </>
            ) : (
              <h1 className="text-2xl md:text-3xl font-bold text-apple-text tracking-tight">
                {activeTabMeta.label}
              </h1>
            )}
            <p className="text-apple-secondary mt-2">{activeTabMeta.description}</p>
            {activeTabMeta.searchPlaceholder && (
              <div className="mt-4 md:hidden">
                <SearchInput value={search} onChange={setSearch} placeholder={activeTabMeta.searchPlaceholder} />
              </div>
            )}
          </div>

          {tab === "dashboard" && <Dashboard />}
          {tab === "revenue" && <RevenuePage />}
          {tab === "coupons" && <CouponsPage search={search} />}
          {tab === "chefApprovals" && <ChefApprovalsPage search={search} />}
          {tab === "chefs" && <ChefsPage search={search} />}
          {tab === "meals" && <MealsPage search={search} />}
          {tab === "users" && <UsersPage search={search} />}
          {tab === "orders" && <OrdersPage search={search} />}
          {tab === "reviews" && <ReviewsPage search={search} />}
          {tab === "subscriptions" && <SubscriptionsPage search={search} />}
          {tab === "activity" && <ActivityLogPage search={search} />}
          {tab === "chats" && <ChatsPage search={search} />}
          {tab === "deliveryPartners" && <DeliveryPartnersPage />}
          {tab === "selfServiceRequests" && <SelfServiceRequestsPage />}
          {tab === "admins" && <AdminsPage isSuperAdmin={isSuperAdmin} />}
          {tab === "recycleBin" && <RecycleBinPage search={search} isSuperAdmin={isSuperAdmin} />}
        </main>
      </div>
    </div>
  );
}
