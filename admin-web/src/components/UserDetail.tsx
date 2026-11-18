import type { ReactNode } from "react";
import { Mail, MapPin, MessageCircle, Phone } from "lucide-react";
import DetailDrawer from "./ui/DetailDrawer";
import AgentAccessSection from "./AgentAccessSection";
import Badge from "./ui/Badge";
import type { AppUser, Chef, Meal, Order } from "../types";
import { formatCurrency, formatDate, formatDateTime } from "../lib/format";
import { STATUS_BADGE_TONE } from "../lib/orderStatus";

interface Props {
  user: AppUser | null;
  orders: Order[];
  chefs: Chef[];
  meals: Meal[];
  onClose: () => void;
}

function whatsappLink(number?: string) {
  const digits = (number ?? "").replace(/\D/g, "");
  if (!digits) return undefined;
  return `https://wa.me/${digits.startsWith("0") ? `88${digits}` : digits}`;
}

function mapLink(lat?: number, lng?: number) {
  return lat && lng ? `https://www.google.com/maps?q=${lat},${lng}` : undefined;
}

function Field({ label, children, wide }: { label: string; children: ReactNode; wide?: boolean }) {
  return (
    <div className={`bg-apple-bg rounded-xl px-3 py-2 ${wide ? "col-span-2" : ""}`}>
      <div className="text-xs text-apple-secondary">{label}</div>
      <div className="font-medium break-words">{children}</div>
    </div>
  );
}

/** A location row: readable address, coordinates, and a Google Maps link. */
function LocationBlock({ label, address, lat, lng, badge }: { label: string; address?: string; lat?: number; lng?: number; badge?: string }) {
  const link = mapLink(lat, lng);
  return (
    <div className="bg-apple-bg rounded-xl px-3 py-2.5 text-sm">
      <div className="flex items-center gap-2 text-xs text-apple-secondary">
        <MapPin size={12} /> {label}
        {badge && <Badge tone="blue">{badge}</Badge>}
      </div>
      <div className="font-medium mt-0.5">{address || (link ? "(no readable address saved)" : "Not set")}</div>
      {link && (
        <div className="text-xs text-apple-secondary mt-0.5">
          {lat!.toFixed(5)}, {lng!.toFixed(5)} ·{" "}
          <a href={link} target="_blank" rel="noreferrer" className="text-apple-blue hover:underline">
            Open in Google Maps ↗
          </a>
        </div>
      )}
    </div>
  );
}

/**
 * Everything known about one account: contact numbers, role, and location.
 * Customers: the default location (users/{uid}.address/latitude/longitude -
 * exactly what the app's Home header shows and measures "nearby chefs"
 * from) plus every saved address, and their order history. Chefs: their
 * kitchen (chefs/{uid}, same uid) with its location, payment numbers,
 * approval / delivery status, menu size and orders received.
 */
export default function UserDetail({ user, orders, chefs, meals, onClose }: Props) {
  if (!user) return null;

  const isChef = user.role === "CHEF";
  const chef = isChef ? chefs.find((c) => c.id === user.id) : undefined;
  const userOrders = orders
    .filter((o) => (isChef ? o.chefId === user.id : o.customerId === user.id))
    .sort((a, b) => (b.placedAt ?? 0) - (a.placedAt ?? 0));
  const spent = userOrders.filter((o) => o.status !== "Cancelled").reduce((s, o) => s + (o.totalPrice ?? 0), 0);
  const saved = user.savedAddresses ?? [];
  const defaultIdx = user.defaultAddressIndex ?? 0;
  const wa = whatsappLink(user.whatsappNumber || chef?.whatsappNumber);
  const deliveryActive = chef?.deliveryMode === "tiffinbox" && (chef.deliveryUntil ?? 0) > Date.now();

  return (
    <DetailDrawer open={!!user} onClose={onClose} title={user.name ?? "(unnamed user)"} subtitle={user.email}>
      <div className="space-y-6">
        <div className="grid grid-cols-2 gap-3 text-sm">
          <Field label="Role">{user.role ?? "unknown"}</Field>
          <Field label="Joined">{user.createdAt ? formatDateTime(user.createdAt) : "–"}</Field>
          <Field label="Phone">{user.phoneNumber || chef?.phoneNumber || "–"}</Field>
          <Field label="WhatsApp">{user.whatsappNumber || chef?.whatsappNumber || "–"}</Field>
          <Field label="Email" wide>
            {user.email || "–"}
          </Field>
          <Field label="UID" wide>
            <span className="break-all">{user.id}</span>
          </Field>
          <Field label="Push notifications">{user.fcmToken ? "Enabled on a device" : "No device token"}</Field>
          <Field label={isChef ? "Orders received" : "Orders placed"}>
            {userOrders.length} · {formatCurrency(spent)}
          </Field>
        </div>

        <div className="flex flex-wrap gap-2">
          {wa && (
            <a
              href={wa}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 text-xs font-medium rounded-full px-3 py-1.5 bg-apple-green/10 text-apple-green hover:bg-apple-green/20"
            >
              <MessageCircle size={13} /> WhatsApp
            </a>
          )}
          {(user.phoneNumber || chef?.phoneNumber) && (
            <a
              href={`tel:${user.phoneNumber || chef?.phoneNumber}`}
              className="inline-flex items-center gap-1.5 text-xs font-medium rounded-full px-3 py-1.5 bg-apple-hover text-apple-text hover:opacity-80"
            >
              <Phone size={13} /> Call
            </a>
          )}
          {user.email && (
            <a
              href={`https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(user.email)}`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 text-xs font-medium rounded-full px-3 py-1.5 bg-apple-blue/10 text-apple-blue hover:bg-apple-blue/20"
            >
              <Mail size={13} /> Gmail
            </a>
          )}
        </div>

        <section className="space-y-2">
          <h4 className="text-sm font-semibold text-apple-text">Location</h4>
          {isChef ? (
            chef ? (
              <LocationBlock label="Kitchen location" address={chef.address} lat={chef.latitude} lng={chef.longitude} />
            ) : (
              <p className="text-xs text-apple-secondary">No kitchen profile found for this chef account.</p>
            )
          ) : (
            <>
              <LocationBlock
                label="Current location (shown on the app's Home & used for nearby chefs)"
                address={user.address}
                lat={user.latitude}
                lng={user.longitude}
              />
              {saved.map((a, i) => (
                <LocationBlock
                  key={i}
                  label={`Saved address · ${a.label || `#${i + 1}`}`}
                  address={a.address}
                  lat={a.latitude}
                  lng={a.longitude}
                  badge={i === defaultIdx ? "Default" : undefined}
                />
              ))}
              {saved.length > 0 &&
                (() => {
                  const def = saved[defaultIdx];
                  const mismatch =
                    def && (Math.abs((def.latitude ?? 0) - (user.latitude ?? 0)) > 1e-6 || Math.abs((def.longitude ?? 0) - (user.longitude ?? 0)) > 1e-6);
                  return mismatch ? (
                    <p className="text-xs text-apple-amber">
                      Current location differs from the default saved address - it will match again the next time the user edits their addresses.
                    </p>
                  ) : (
                    <p className="text-xs text-apple-green">✓ Default saved address matches the location used on Home.</p>
                  );
                })()}
            </>
          )}
        </section>

        {isChef && chef && (
          <section className="space-y-2">
            <h4 className="text-sm font-semibold text-apple-text">Kitchen</h4>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <Field label="Kitchen name" wide>
                {chef.kitchenName || "–"}
                {chef.isDemo && (
                  <span className="ml-2">
                    <Badge tone="amber">Demo</Badge>
                  </span>
                )}
              </Field>
              <Field label="Approval">
                {chef.isVerified ? "Approved" : chef.approvalStatus === "rejected" ? "Rejected" : "Pending"}
              </Field>
              <Field label="Delivery">
                {deliveryActive ? `TiffinBox until ${formatDate(chef.deliveryUntil)}` : "Self delivery"}
              </Field>
              <Field label="bKash">{chef.bkashNumber || "–"}</Field>
              <Field label="Nagad">{chef.nagadNumber || "–"}</Field>
              <Field label="Kitchen type">{chef.kitchenType || "–"}</Field>
              <Field label="Experience">{chef.yearsOfExperience ?? 0} yrs</Field>
              <Field label="Menu items">{meals.filter((m) => m.chefId === chef.id).length}</Field>
              <Field label="Available today">{chef.isAvailableToday === false ? "No" : "Yes"}</Field>
            </div>
          </section>
        )}

        {!isChef && <AgentAccessSection uid={user.id} />}

        <section>
          <h4 className="text-sm font-semibold text-apple-text mb-2">
            {isChef ? "Orders received" : "Order history"} ({userOrders.length})
          </h4>
          {userOrders.length === 0 ? (
            <p className="text-xs text-apple-secondary">No orders yet.</p>
          ) : (
            <ul className="space-y-1.5">
              {userOrders.map((o) => (
                <li key={o.id} className="flex items-center justify-between text-sm bg-apple-bg rounded-xl px-3 py-2">
                  <div className="truncate">
                    <div className="font-medium">
                      {formatCurrency(o.totalPrice ?? 0)}
                      <span className="text-xs text-apple-secondary font-normal">
                        {" "}
                        · {(o.items ?? []).map((it) => `${it.quantity}× ${it.mealName}`).join(", ")}
                      </span>
                    </div>
                    <div className="text-xs text-apple-secondary">
                      {formatDateTime(o.placedAt)}
                      {o.deliveryAddress ? ` · to ${o.deliveryAddress}` : ""}
                    </div>
                  </div>
                  <Badge tone={STATUS_BADGE_TONE[(o.status as keyof typeof STATUS_BADGE_TONE) ?? "Placed"] ?? "neutral"}>
                    {o.status ?? "Placed"}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </DetailDrawer>
  );
}
