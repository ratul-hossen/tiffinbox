import { useMemo, useState } from "react";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { Ban, Plus, RotateCcw, Trash2 } from "lucide-react";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { softDelete } from "../lib/recycleBin";
import { formatCurrency, formatDateTime } from "../lib/format";
import type { Chef, Coupon } from "../types";
import Badge from "./ui/Badge";

interface Props {
  search: string;
}

type CouponState = "active" | "scheduled" | "expired" | "cancelled" | "used_up";

function couponState(c: Coupon, now: number): CouponState {
  if (c.isActive === false) return "cancelled";
  if (c.maxUses && (c.usedCount ?? 0) >= c.maxUses) return "used_up";
  if (c.validFrom && now < c.validFrom) return "scheduled";
  if (c.validUntil && now > c.validUntil) return "expired";
  return "active";
}

const STATE_BADGE: Record<CouponState, { tone: "green" | "amber" | "red" | "blue" | "neutral"; label: string }> = {
  active: { tone: "green", label: "Active" },
  scheduled: { tone: "blue", label: "Scheduled" },
  expired: { tone: "neutral", label: "Expired" },
  cancelled: { tone: "red", label: "Cancelled" },
  used_up: { tone: "amber", label: "Used up" },
};

function toLocalInput(ms: number): string {
  const d = new Date(ms - new Date(ms).getTimezoneOffset() * 60000);
  return d.toISOString().slice(0, 16);
}

const inputClass =
  "text-sm border border-apple-border2 rounded-lg px-3 py-2 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 w-full";

/**
 * Promo codes. Admins create platform-wide coupons (all kitchens) or ones
 * for a single chef, with a validity window; chefs create their own from the
 * app. Admins can cancel/reactivate/delete any coupon, including a chef's.
 */
export default function CouponsPage({ search }: Props) {
  const { data: coupons, loading, error } = useCollection<Coupon>("coupons");
  const { data: chefs } = useCollection<Chef>("chefs");
  const now = Date.now();

  const [showForm, setShowForm] = useState(false);
  const [code, setCode] = useState("");
  const [discountType, setDiscountType] = useState<"percent" | "flat">("percent");
  const [value, setValue] = useState("10");
  const [minOrder, setMinOrder] = useState("0");
  const [maxDiscount, setMaxDiscount] = useState("0");
  const [chefId, setChefId] = useState("");
  const [validFrom, setValidFrom] = useState(toLocalInput(now));
  const [validUntil, setValidUntil] = useState(toLocalInput(now + 7 * 24 * 60 * 60 * 1000));
  const [maxUses, setMaxUses] = useState("0");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const chefName = (id?: string) => (id ? chefs.find((c) => c.id === id)?.kitchenName ?? id : "All kitchens");

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return [...coupons]
      .sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0))
      .filter((c) => !q || `${c.code ?? c.id} ${chefName(c.chefId)}`.toLowerCase().includes(q));
  }, [coupons, search, chefs]);

  async function createCoupon() {
    setFormError(null);
    const normalized = code.trim().toUpperCase().replace(/\s+/g, "");
    const numericValue = Number(value);
    const from = new Date(validFrom).getTime();
    const until = new Date(validUntil).getTime();
    if (!/^[A-Z0-9_-]{3,20}$/.test(normalized)) return setFormError("Code: 3-20 letters/numbers, no spaces.");
    if (!numericValue || numericValue <= 0) return setFormError("Discount must be more than 0.");
    if (discountType === "percent" && numericValue > 100) return setFormError("Percent discount can't exceed 100.");
    if (!until || until <= from) return setFormError("'Valid until' must be after 'Valid from'.");
    setBusy(true);
    try {
      const ref = doc(db, "coupons", normalized);
      if ((await getDoc(ref)).exists()) {
        setFormError("That code already exists - pick another.");
        return;
      }
      await setDoc(ref, {
        code: normalized,
        discountType,
        value: numericValue,
        minOrder: Number(minOrder) || 0,
        maxDiscount: discountType === "percent" ? Number(maxDiscount) || 0 : 0,
        chefId,
        createdBy: "admin",
        validFrom: from,
        validUntil: until,
        isActive: true,
        usedCount: 0,
        maxUses: Number(maxUses) || 0,
        createdAt: Date.now(),
      });
      await logActivity("Created coupon", "coupons", normalized, { chefId: chefId || null });
      setShowForm(false);
      setCode("");
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to create coupon.");
    } finally {
      setBusy(false);
    }
  }

  async function setActive(c: Coupon, active: boolean) {
    await updateDoc(doc(db, "coupons", c.id), { isActive: active, cancelledByAdmin: !active });
    await logActivity(active ? "Reactivated coupon" : "Cancelled coupon", "coupons", c.id, {
      createdBy: c.createdBy ?? null,
    });
  }

  async function remove(c: Coupon) {
    if (!confirm(`Delete coupon ${c.code ?? c.id}? It'll move to the Recycle Bin.`)) return;
    await softDelete("coupons", c.id, c, c.code ?? c.id);
    await logActivity("Deleted coupon (moved to recycle bin)", "coupons", c.id);
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading coupons…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-apple-secondary">{filtered.length} coupons</p>
        <button
          onClick={() => setShowForm((v) => !v)}
          className="flex items-center gap-1.5 text-sm bg-apple-blue text-white rounded-lg px-3 py-1.5 font-medium"
        >
          <Plus size={14} /> New coupon
        </button>
      </div>

      {showForm && (
        <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 grid gap-3 md:grid-cols-3">
          <label className="text-xs text-apple-secondary">
            Code
            <input className={inputClass} value={code} onChange={(e) => setCode(e.target.value)} placeholder="EID20" />
          </label>
          <label className="text-xs text-apple-secondary">
            Type
            <select className={inputClass} value={discountType} onChange={(e) => setDiscountType(e.target.value as "percent" | "flat")}>
              <option value="percent">Percent (%)</option>
              <option value="flat">Flat amount (৳)</option>
            </select>
          </label>
          <label className="text-xs text-apple-secondary">
            {discountType === "percent" ? "Discount %" : "Discount ৳"}
            <input className={inputClass} type="number" value={value} onChange={(e) => setValue(e.target.value)} />
          </label>
          <label className="text-xs text-apple-secondary">
            Minimum order ৳
            <input className={inputClass} type="number" value={minOrder} onChange={(e) => setMinOrder(e.target.value)} />
          </label>
          {discountType === "percent" && (
            <label className="text-xs text-apple-secondary">
              Max discount ৳ (0 = no cap)
              <input className={inputClass} type="number" value={maxDiscount} onChange={(e) => setMaxDiscount(e.target.value)} />
            </label>
          )}
          <label className="text-xs text-apple-secondary">
            Kitchen
            <select className={inputClass} value={chefId} onChange={(e) => setChefId(e.target.value)}>
              <option value="">All kitchens (platform-wide)</option>
              {chefs.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.kitchenName ?? c.id}
                </option>
              ))}
            </select>
          </label>
          <label className="text-xs text-apple-secondary">
            Valid from
            <input className={inputClass} type="datetime-local" value={validFrom} onChange={(e) => setValidFrom(e.target.value)} />
          </label>
          <label className="text-xs text-apple-secondary">
            Valid until
            <input className={inputClass} type="datetime-local" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
          </label>
          <label className="text-xs text-apple-secondary">
            Max uses (0 = unlimited)
            <input className={inputClass} type="number" value={maxUses} onChange={(e) => setMaxUses(e.target.value)} />
          </label>
          <div className="md:col-span-3 flex items-center justify-end gap-3">
            {formError && <span className="text-sm text-apple-red mr-auto">{formError}</span>}
            <button onClick={() => setShowForm(false)} className="text-sm px-3 py-1.5 rounded-lg text-apple-secondary hover:bg-apple-hover">
              Cancel
            </button>
            <button
              onClick={createCoupon}
              disabled={busy}
              className="text-sm px-4 py-1.5 rounded-lg bg-apple-blue text-white font-medium disabled:opacity-50"
            >
              Create coupon
            </button>
          </div>
        </div>
      )}

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-apple-subtle text-apple-secondary text-xs uppercase tracking-wide">
            <tr>
              <th className="text-left px-4 py-3 font-medium">Code</th>
              <th className="text-left px-4 py-3 font-medium">Discount</th>
              <th className="text-left px-4 py-3 font-medium">Kitchen</th>
              <th className="text-left px-4 py-3 font-medium">Created by</th>
              <th className="text-left px-4 py-3 font-medium">Valid</th>
              <th className="text-left px-4 py-3 font-medium">Used</th>
              <th className="text-left px-4 py-3 font-medium">Status</th>
              <th className="text-right px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-apple-border">
            {filtered.map((c) => {
              const state = couponState(c, now);
              return (
                <tr key={c.id}>
                  <td className="px-4 py-3 font-mono font-semibold text-apple-text">{c.code ?? c.id}</td>
                  <td className="px-4 py-3 text-apple-text">
                    {c.discountType === "flat" ? formatCurrency(c.value ?? 0) : `${c.value ?? 0}%`}
                    {c.minOrder ? <div className="text-xs text-apple-secondary">min {formatCurrency(c.minOrder)}</div> : null}
                  </td>
                  <td className="px-4 py-3 text-apple-secondary">{chefName(c.chefId)}</td>
                  <td className="px-4 py-3">
                    <Badge tone={c.createdBy === "chef" ? "amber" : "blue"}>{c.createdBy === "chef" ? "Chef" : "Admin"}</Badge>
                  </td>
                  <td className="px-4 py-3 text-xs text-apple-secondary">
                    {c.validFrom ? formatDateTime(c.validFrom) : "—"}
                    <br />→ {c.validUntil ? formatDateTime(c.validUntil) : "—"}
                  </td>
                  <td className="px-4 py-3 text-apple-secondary">
                    {c.usedCount ?? 0}
                    {c.maxUses ? ` / ${c.maxUses}` : ""}
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={STATE_BADGE[state].tone}>{STATE_BADGE[state].label}</Badge>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-3 text-xs font-medium">
                      {c.isActive === false ? (
                        <button onClick={() => setActive(c, true)} className="inline-flex items-center gap-1 text-apple-blue hover:underline">
                          <RotateCcw size={13} /> Reactivate
                        </button>
                      ) : (
                        <button onClick={() => setActive(c, false)} className="inline-flex items-center gap-1 text-apple-amber hover:underline">
                          <Ban size={13} /> Cancel
                        </button>
                      )}
                      <button onClick={() => remove(c)} className="inline-flex items-center gap-1 text-apple-red hover:underline">
                        <Trash2 size={13} /> Delete
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {filtered.length === 0 && <p className="text-sm text-apple-secondary p-5">No coupons yet.</p>}
      </div>
    </div>
  );
}
