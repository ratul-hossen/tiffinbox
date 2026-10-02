import DetailDrawer from "./ui/DetailDrawer";
import Badge from "./ui/Badge";
import type { AppUser, Order } from "../types";
import { formatCurrency, formatDateTime } from "../lib/format";
import { STATUS_BADGE_TONE } from "../lib/orderStatus";

interface Props {
  user: AppUser | null;
  orders: Order[];
  onClose: () => void;
}

/**
 * There is no reliable uid <-> chefId link in the current schema (chefs
 * don't consistently store a userId field — see firestore.rules' own
 * comments on this), so a chef account's detail view only shows what's
 * honestly available: role, email, createdAt, and — for a customer — their
 * order history. No chef-profile link is guessed at.
 */
export default function UserDetail({ user, orders, onClose }: Props) {
  if (!user) return null;

  const isCustomer = user.role === "CUSTOMER";
  const customerOrders = isCustomer
    ? orders.filter((o) => o.customerId === user.id).sort((a, b) => (b.placedAt ?? 0) - (a.placedAt ?? 0))
    : [];

  return (
    <DetailDrawer open={!!user} onClose={onClose} title={user.name ?? "(unnamed user)"} subtitle={user.email}>
      <div className="space-y-6">
        <div className="grid grid-cols-2 gap-3 text-sm">
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">Role</div>
            <div className="font-medium">{user.role ?? "unknown"}</div>
          </div>
          <div className="bg-apple-bg rounded-xl px-3 py-2">
            <div className="text-xs text-apple-secondary">Created</div>
            <div className="font-medium">{user.createdAt ? formatDateTime(user.createdAt) : "–"}</div>
          </div>
          <div className="bg-apple-bg rounded-xl px-3 py-2 col-span-2">
            <div className="text-xs text-apple-secondary">UID</div>
            <div className="font-medium break-all">{user.id}</div>
          </div>
        </div>

        {isCustomer ? (
          <section>
            <h4 className="text-sm font-semibold text-apple-text mb-2">
              Order history ({customerOrders.length})
            </h4>
            {customerOrders.length === 0 ? (
              <p className="text-xs text-apple-secondary">No orders placed yet.</p>
            ) : (
              <ul className="space-y-1.5">
                {customerOrders.map((o) => (
                  <li
                    key={o.id}
                    className="flex items-center justify-between text-sm bg-apple-bg rounded-xl px-3 py-2"
                  >
                    <div className="truncate">
                      <div className="font-medium">{formatCurrency(o.totalPrice ?? 0)}</div>
                      <div className="text-xs text-apple-secondary">{formatDateTime(o.placedAt)}</div>
                    </div>
                    <Badge
                      tone={
                        STATUS_BADGE_TONE[(o.status as keyof typeof STATUS_BADGE_TONE) ?? "Placed"] ?? "neutral"
                      }
                    >
                      {o.status ?? "Placed"}
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </section>
        ) : (
          <p className="text-xs text-apple-secondary">
            This account's role is "{user.role ?? "unknown"}". There's no reliable link from a user
            account to a chef profile in the current data model, so no chef-side detail is shown here —
            check the Chefs tab directly if you're looking for their kitchen.
          </p>
        )}
      </div>
    </DetailDrawer>
  );
}
