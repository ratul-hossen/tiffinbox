import { useEffect, useState } from "react";
import { doc, onSnapshot, serverTimestamp, setDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import { logActivity } from "../lib/activityLog";

/**
 * App-wide settings the Android app reads from `app_config/settings`
 * (FirestoreAppConfigRepository). Missing values fall back to the defaults
 * below - the same defaults the app uses.
 *  - recommendRadiusKm: how far a kitchen may be and still be recommended.
 *  - deliveryBaseFee / deliveryIncludedKm / deliveryPerKmFee: delivery fee =
 *    base for the first N km, then per-km for every started km after that.
 *  - deliveryMaxFee: ceiling on a self-delivering kitchen's fee (0 = no cap).
 *  - platformFee / preOrderMaxPlates / fewPlatesThreshold: order and plate rules.
 */
const DEFAULTS = {
  recommendRadiusKm: 10,
  deliveryBaseFee: 20,
  deliveryIncludedKm: 2,
  deliveryPerKmFee: 5,
  deliveryMaxFee: 0,
  platformFee: 10,
  preOrderMaxPlates: 10,
  fewPlatesThreshold: 5,
};
type Settings = typeof DEFAULTS;
type Key = keyof Settings;

const LIMITS: Record<Key, { min: number; max: number; step: number }> = {
  recommendRadiusKm: { min: 1, max: 50, step: 0.5 },
  deliveryBaseFee: { min: 0, max: 500, step: 1 },
  deliveryIncludedKm: { min: 0, max: 20, step: 0.5 },
  deliveryPerKmFee: { min: 0, max: 100, step: 1 },
  deliveryMaxFee: { min: 0, max: 1000, step: 1 },
  platformFee: { min: 0, max: 200, step: 1 },
  preOrderMaxPlates: { min: 1, max: 50, step: 1 },
  fewPlatesThreshold: { min: 1, max: 20, step: 1 },
};
const RADIUS_PRESETS = [2, 5, 10, 15];

const inputClass =
  "mt-1 text-sm border border-apple-border2 rounded-lg px-3 py-2 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 w-full";

function feeFor(km: number, s: Settings) {
  return s.deliveryBaseFee + Math.ceil(Math.max(0, km - s.deliveryIncludedKm)) * s.deliveryPerKmFee;
}

export default function AppSettingsPage() {
  const [saved, setSaved] = useState<Settings>(DEFAULTS);
  const [form, setForm] = useState<Record<Key, string> | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    return onSnapshot(
      doc(db, "app_config", "settings"),
      (snap) => {
        const data = snap.data() ?? {};
        const next = { ...DEFAULTS };
        (Object.keys(DEFAULTS) as Key[]).forEach((k) => {
          const v = Number(data[k]);
          if (Number.isFinite(v) && v >= LIMITS[k].min) next[k] = v;
        });
        setSaved(next);
        setForm((f) => f ?? (Object.fromEntries(Object.entries(next).map(([k, v]) => [k, String(v)])) as Record<Key, string>));
        setLoading(false);
      },
      (err) => {
        setMessage({ ok: false, text: err.message });
        setLoading(false);
      },
    );
  }, []);

  if (loading || !form) return <p className="text-sm text-apple-secondary">Loading settings…</p>;

  const parsed = Object.fromEntries((Object.keys(DEFAULTS) as Key[]).map((k) => [k, Number(form[k])])) as Settings;
  const invalid = (Object.keys(DEFAULTS) as Key[]).filter(
    (k) => !Number.isFinite(parsed[k]) || parsed[k] < LIMITS[k].min || parsed[k] > LIMITS[k].max,
  );
  const changed = (Object.keys(DEFAULTS) as Key[]).some((k) => parsed[k] !== saved[k]);

  async function save() {
    setSaving(true);
    setMessage(null);
    try {
      await setDoc(
        doc(db, "app_config", "settings"),
        { ...parsed, updatedAt: serverTimestamp(), updatedBy: auth.currentUser?.email ?? null },
        { merge: true },
      );
      await logActivity("update_app_settings", "app_config", "settings", { from: saved, to: parsed });
      setMessage({ ok: true, text: "Saved. The app uses the new values the next time it opens." });
    } catch (err) {
      setMessage({ ok: false, text: err instanceof Error ? err.message : String(err) });
    } finally {
      setSaving(false);
    }
  }

  const field = (k: Key, label: string) => (
    <label className="block text-xs text-apple-secondary">
      {label} ({LIMITS[k].min}–{LIMITS[k].max})
      <input
        className={inputClass}
        type="number"
        min={LIMITS[k].min}
        max={LIMITS[k].max}
        step={LIMITS[k].step}
        value={form[k]}
        onChange={(e) => setForm({ ...form, [k]: e.target.value })}
      />
    </label>
  );

  return (
    <div className="space-y-5 max-w-xl">
      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-apple-text">Recommendation distance</h2>
          <p className="text-sm text-apple-secondary mt-1">
            Kitchens farther than this from a customer's saved location are never recommended to them. "Home Chefs
            Nearby" stays at 3 km. Currently <span className="font-semibold text-apple-text">{saved.recommendRadiusKm} km</span>.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {RADIUS_PRESETS.map((km) => (
            <button
              key={km}
              type="button"
              onClick={() => setForm({ ...form, recommendRadiusKm: String(km) })}
              className={`rounded-full px-3 py-1.5 text-sm font-medium border ${
                parsed.recommendRadiusKm === km
                  ? "bg-apple-blue text-white border-apple-blue"
                  : "border-apple-border text-apple-text hover:bg-apple-hover"
              }`}
            >
              {km} km
            </button>
          ))}
        </div>
        {field("recommendRadiusKm", "Custom, km")}
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-apple-text">Delivery fee</h2>
          <p className="text-sm text-apple-secondary mt-1">
            Shown to customers on kitchen cards, Chef and Meal pages, Cart and Checkout. The base fee covers the first
            km; each started km after that adds the per-km fee. Top-rank customers still get free delivery.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          {field("deliveryBaseFee", "Base fee, ৳")}
          {field("deliveryIncludedKm", "Included km")}
          {field("deliveryPerKmFee", "Per extra km, ৳")}
          {field("deliveryMaxFee", "Max fee for kitchens delivering themselves, ৳")}
        </div>
        <p className="text-xs text-apple-secondary">
          0 = no cap. A kitchen's own cap (set on its Chef page) takes priority over this one.
        </p>
        {invalid.length === 0 && (
          <p className="text-xs text-apple-secondary">
            Example: 1 km → ৳{feeFor(1, parsed)} · 3 km → ৳{feeFor(3, parsed)} · 5 km → ৳{feeFor(5, parsed)}
          </p>
        )}
      </div>

      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-apple-text">Orders and plates</h2>
          <p className="text-sm text-apple-secondary mt-1">
            Platform fee added to every order, the most plates a customer can pre-order at once, and when a meal
            starts showing "plates left".
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          {field("platformFee", "Platform fee, ৳")}
          {field("preOrderMaxPlates", "Max plates per pre-order")}
          {field("fewPlatesThreshold", "Warn when plates left is at most")}
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          disabled={invalid.length > 0 || saving || !changed}
          onClick={save}
          className="text-sm bg-apple-blue text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
        >
          {saving ? "Saving…" : "Save settings"}
        </button>
        {invalid.length > 0 && <span className="text-xs text-apple-red">Some values are out of range.</span>}
      </div>
      {message && <p className={`text-sm ${message.ok ? "text-apple-green" : "text-apple-red"}`}>{message.text}</p>}
      <p className="text-xs text-apple-secondary">
        Needs the app_config block in firestore.rules (re-paste the rules in Firebase Console once).
      </p>
    </div>
  );
}
