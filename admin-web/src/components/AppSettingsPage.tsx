import { useEffect, useState } from "react";
import { doc, onSnapshot, serverTimestamp, setDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import { logActivity } from "../lib/activityLog";

/**
 * App-wide settings the Android app reads from `app_config/settings`.
 * Currently: how far (km) a kitchen may be from a customer and still be
 * recommended to them (Home "Recommended", AI picks, Today's banner, the chef
 * list's Recommended sort). The app falls back to 10 km if this doc is missing.
 */
const DEFAULT_RECOMMEND_RADIUS_KM = 10;
const MIN_KM = 1;
const MAX_KM = 50;
const PRESETS = [2, 5, 10, 15];

const inputClass =
  "mt-1 text-sm border border-apple-border2 rounded-lg px-3 py-2 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 w-full";

export default function AppSettingsPage() {
  const [saved, setSaved] = useState<number | null>(null);
  const [value, setValue] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    return onSnapshot(
      doc(db, "app_config", "settings"),
      (snap) => {
        const km = Number(snap.data()?.recommendRadiusKm);
        const current = Number.isFinite(km) && km > 0 ? km : DEFAULT_RECOMMEND_RADIUS_KM;
        setSaved(current);
        setValue((v) => (v === "" ? String(current) : v));
        setLoading(false);
      },
      (err) => {
        setMessage({ ok: false, text: err.message });
        setLoading(false);
      },
    );
  }, []);

  const parsed = Number(value);
  const valid = Number.isFinite(parsed) && parsed >= MIN_KM && parsed <= MAX_KM;

  async function save(km: number) {
    setSaving(true);
    setMessage(null);
    try {
      await setDoc(
        doc(db, "app_config", "settings"),
        { recommendRadiusKm: km, updatedAt: serverTimestamp(), updatedBy: auth.currentUser?.email ?? null },
        { merge: true },
      );
      await logActivity("update_recommend_radius", "app_config", "settings", { from: saved, to: km });
      setValue(String(km));
      setMessage({ ok: true, text: `Saved. Customers now get recommendations within ${km} km.` });
    } catch (err) {
      setMessage({ ok: false, text: err instanceof Error ? err.message : String(err) });
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading settings…</p>;

  return (
    <div className="space-y-5 max-w-xl">
      <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-apple-text">Recommendation distance</h2>
          <p className="text-sm text-apple-secondary mt-1">
            Kitchens farther than this from a customer's saved location are never recommended to them. "Home Chefs
            Nearby" stays at 3 km. Currently <span className="font-semibold text-apple-text">{saved} km</span>.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          {PRESETS.map((km) => (
            <button
              key={km}
              type="button"
              onClick={() => setValue(String(km))}
              className={`rounded-full px-3 py-1.5 text-sm font-medium border ${
                Number(value) === km
                  ? "bg-apple-blue text-white border-apple-blue"
                  : "border-apple-border text-apple-text hover:bg-apple-hover"
              }`}
            >
              {km} km
            </button>
          ))}
        </div>

        <label className="block text-xs text-apple-secondary">
          Custom ({MIN_KM}–{MAX_KM} km)
          <input
            className={inputClass}
            type="number"
            min={MIN_KM}
            max={MAX_KM}
            step={0.5}
            value={value}
            onChange={(e) => setValue(e.target.value)}
          />
        </label>

        <div className="flex items-center gap-3">
          <button
            type="button"
            disabled={!valid || saving || parsed === saved}
            onClick={() => save(parsed)}
            className="text-sm bg-apple-blue text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
          >
            {saving ? "Saving…" : "Save"}
          </button>
          {!valid && <span className="text-xs text-apple-red">Enter a number from {MIN_KM} to {MAX_KM}.</span>}
        </div>

        {message && (
          <p className={`text-sm ${message.ok ? "text-apple-green" : "text-apple-red"}`}>{message.text}</p>
        )}
        <p className="text-xs text-apple-secondary">
          Applies the next time a customer opens Home or the chef list. Remember: firestore.rules must include the
          app_config block (re-paste the rules in Firebase Console once).
        </p>
      </div>
    </div>
  );
}
