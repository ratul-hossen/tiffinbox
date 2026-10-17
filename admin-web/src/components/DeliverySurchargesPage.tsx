import { useMemo, useState } from "react";
import { addDoc, collection, doc, updateDoc } from "firebase/firestore";
import { Plus, Trash2 } from "lucide-react";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { softDelete } from "../lib/recycleBin";
import { formatDateTime } from "../lib/format";
import type { DeliverySurcharge } from "../types";
import Badge, { type BadgeTone } from "./ui/Badge";
import LocationPicker from "./ui/LocationPicker";

interface Props {
  search: string;
}

type Status = "live" | "scheduled" | "ended" | "off";

const STATUS: Record<Status, { tone: BadgeTone; label: string }> = {
  live: { tone: "green", label: "Live now" },
  scheduled: { tone: "blue", label: "Scheduled" },
  ended: { tone: "neutral", label: "Ended" },
  off: { tone: "red", label: "Off" },
};

function statusOf(s: DeliverySurcharge, now: number): Status {
  if (s.isActive === false) return "off";
  if (s.validFrom && now < s.validFrom) return "scheduled";
  if (s.validUntil && now > s.validUntil) return "ended";
  return "live";
}

interface Draft {
  areaName: string;
  latitude: string;
  longitude: string;
  radiusKm: string;
  type: "flat" | "multiply";
  amount: string;
  validFrom: string;
  validUntil: string;
  isActive: boolean;
}

const EMPTY_DRAFT: Draft = {
  areaName: "",
  latitude: "",
  longitude: "",
  radiusKm: "2",
  type: "flat",
  amount: "10",
  validFrom: "",
  validUntil: "",
  isActive: true,
};

/** Epoch millis -> value for a datetime-local input, in local time. */
function toInputValue(ms?: number): string {
  if (!ms) return "";
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromInputValue(value: string): number {
  return value ? new Date(value).getTime() : 0;
}

function draftFrom(s: DeliverySurcharge): Draft {
  return {
    areaName: s.areaName ?? "",
    latitude: s.latitude !== undefined ? String(s.latitude) : "",
    longitude: s.longitude !== undefined ? String(s.longitude) : "",
    radiusKm: s.radiusKm !== undefined ? String(s.radiusKm) : "",
    type: s.type === "multiply" ? "multiply" : "flat",
    amount: s.amount !== undefined ? String(s.amount) : "",
    validFrom: toInputValue(s.validFrom),
    validUntil: toInputValue(s.validUntil),
    isActive: s.isActive !== false,
  };
}

function validate(d: Draft): string | null {
  if (!d.areaName.trim()) return "Give the area a name.";
  const lat = Number(d.latitude);
  const lng = Number(d.longitude);
  if (d.latitude === "" || d.longitude === "" || !(lat >= -90 && lat <= 90) || !(lng >= -180 && lng <= 180)) {
    return "Enter a valid latitude (-90 to 90) and longitude (-180 to 180).";
  }
  const radius = Number(d.radiusKm);
  if (!(radius > 0 && radius <= 20)) return "Radius must be more than 0 and at most 20 km.";
  const amount = Number(d.amount);
  if (d.amount === "" || !(amount >= 0)) return "Amount can't be negative.";
  if (d.type === "multiply" && amount < 1) return "A multiplier must be at least 1 (1.5 = +50%).";
  if (d.type === "flat" && amount > 500) return "A flat amount above ৳500 looks like a typo.";
  const from = fromInputValue(d.validFrom);
  const until = fromInputValue(d.validUntil);
  if (from && until && until < from) return "The end time is before the start time.";
  return null;
}

function payloadFrom(d: Draft) {
  return {
    areaName: d.areaName.trim(),
    latitude: Number(d.latitude),
    longitude: Number(d.longitude),
    radiusKm: Number(d.radiusKm),
    type: d.type,
    amount: Number(d.amount),
    validFrom: fromInputValue(d.validFrom),
    validUntil: fromInputValue(d.validUntil),
    isActive: d.isActive,
  };
}

function describeCharge(s: DeliverySurcharge): string {
  const charge = s.type === "multiply" ? `fee x ${s.amount ?? 0}` : `+৳${s.amount ?? 0}`;
  return `${charge} within ${s.radiusKm ?? 0} km of ${s.latitude ?? 0}, ${s.longitude ?? 0}`;
}

function describeWindow(s: DeliverySurcharge): string {
  if (!s.validFrom && !s.validUntil) return "Always, until switched off";
  const from = s.validFrom ? formatDateTime(s.validFrom) : "now";
  const until = s.validUntil ? formatDateTime(s.validUntil) : "no end";
  return `${from} → ${until}`;
}

const inputClass =
  "mt-1 w-full rounded-lg border border-apple-border2 bg-apple-surface px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-apple-blue/50";

export default function DeliverySurchargesPage({ search }: Props) {
  const { data, loading, error } = useCollection<DeliverySurcharge>("delivery_surcharges");
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const now = Date.now();
  const query = search.trim().toLowerCase();
  const rows = useMemo(
    () =>
      data
        .filter((s) => !query || (s.areaName ?? "").toLowerCase().includes(query))
        .sort((a, b) => (a.areaName ?? "").localeCompare(b.areaName ?? "")),
    [data, query],
  );
  const liveCount = data.filter((s) => statusOf(s, now) === "live").length;

  function startNew() {
    setEditing("new");
    setDraft(EMPTY_DRAFT);
    setFormError(null);
  }

  function startEdit(s: DeliverySurcharge) {
    setEditing(s.id);
    setDraft(draftFrom(s));
    setFormError(null);
  }

  async function save() {
    const problem = validate(draft);
    if (problem) {
      setFormError(problem);
      return;
    }
    if (!editing) return;
    setBusy(true);
    setFormError(null);
    try {
      const payload = payloadFrom(draft);
      if (editing === "new") {
        const ref = await addDoc(collection(db, "delivery_surcharges"), payload);
        await logActivity("Created delivery surcharge", "delivery_surcharges", ref.id, payload);
      } else {
        await updateDoc(doc(db, "delivery_surcharges", editing), payload);
        await logActivity("Edited delivery surcharge", "delivery_surcharges", editing, payload);
      }
      setEditing(null);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  async function toggle(s: DeliverySurcharge) {
    setActionError(null);
    const next = s.isActive === false;
    try {
      await updateDoc(doc(db, "delivery_surcharges", s.id), { isActive: next });
      await logActivity(
        next ? "Switched on delivery surcharge" : "Switched off delivery surcharge",
        "delivery_surcharges",
        s.id,
        { areaName: s.areaName ?? null },
      );
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    }
  }

  async function remove(s: DeliverySurcharge) {
    if (!confirm(`Remove the surcharge for "${s.areaName || s.id}"? It moves to the Recycle Bin.`)) return;
    setActionError(null);
    try {
      const { id, ...rest } = s;
      await softDelete("delivery_surcharges", id, rest, s.areaName || id);
      await logActivity("Removed delivery surcharge", "delivery_surcharges", id, { areaName: s.areaName ?? null });
    } catch (err) {
      setActionError(err instanceof Error ? err.message : String(err));
    }
  }

  const form = editing !== null && (
    <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 space-y-4 max-w-2xl">
      <div>
        <h2 className="text-base font-semibold text-apple-text">
          {editing === "new" ? "New delivery surcharge" : "Edit delivery surcharge"}
        </h2>
        <p className="text-sm text-apple-secondary mt-1">
          Applies to TiffinBox-delivered orders only; kitchens delivering themselves keep their own fee. The area is a
          circle around one point.
        </p>
      </div>

      <label className="block text-xs text-apple-secondary">
        Area name
        <input
          className={inputClass}
          value={draft.areaName}
          placeholder="e.g. Dhanmondi 27"
          onChange={(e) => setDraft({ ...draft, areaName: e.target.value })}
        />
      </label>

      <LocationPicker
        latitude={Number.isFinite(Number(draft.latitude)) && draft.latitude !== "" ? Number(draft.latitude) : null}
        longitude={Number.isFinite(Number(draft.longitude)) && draft.longitude !== "" ? Number(draft.longitude) : null}
        radiusKm={Number(draft.radiusKm) || 0}
        onPick={(lat, lng) => setDraft({ ...draft, latitude: String(lat), longitude: String(lng) })}
      />

      <div className="grid gap-3 sm:grid-cols-3">
        <label className="block text-xs text-apple-secondary">
          Latitude
          <input
            className={inputClass}
            inputMode="decimal"
            value={draft.latitude}
            onChange={(e) => setDraft({ ...draft, latitude: e.target.value })}
          />
        </label>
        <label className="block text-xs text-apple-secondary">
          Longitude
          <input
            className={inputClass}
            inputMode="decimal"
            value={draft.longitude}
            onChange={(e) => setDraft({ ...draft, longitude: e.target.value })}
          />
        </label>
        <label className="block text-xs text-apple-secondary">
          Radius, km (0–20)
          <input
            className={inputClass}
            inputMode="decimal"
            value={draft.radiusKm}
            onChange={(e) => setDraft({ ...draft, radiusKm: e.target.value })}
          />
        </label>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs text-apple-secondary">
          Charge type
          <select
            className={inputClass}
            value={draft.type}
            onChange={(e) => setDraft({ ...draft, type: e.target.value as "flat" | "multiply" })}
          >
            <option value="flat">Flat amount (added in ৳)</option>
            <option value="multiply">Multiplier (fee x amount)</option>
          </select>
        </label>
        <label className="block text-xs text-apple-secondary">
          {draft.type === "multiply" ? "Multiplier (1.5 = +50%)" : "Amount, ৳"}
          <input
            className={inputClass}
            inputMode="decimal"
            value={draft.amount}
            onChange={(e) => setDraft({ ...draft, amount: e.target.value })}
          />
        </label>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs text-apple-secondary">
          Starts (optional, blank = now)
          <input
            className={inputClass}
            type="datetime-local"
            value={draft.validFrom}
            onChange={(e) => setDraft({ ...draft, validFrom: e.target.value })}
          />
        </label>
        <label className="block text-xs text-apple-secondary">
          Ends (optional, blank = no end)
          <input
            className={inputClass}
            type="datetime-local"
            value={draft.validUntil}
            onChange={(e) => setDraft({ ...draft, validUntil: e.target.value })}
          />
        </label>
      </div>

      <label className="flex items-center gap-2 text-sm text-apple-text">
        <input
          type="checkbox"
          checked={draft.isActive}
          onChange={(e) => setDraft({ ...draft, isActive: e.target.checked })}
        />
        Switched on
      </label>

      {formError && <p className="text-xs text-apple-red">{formError}</p>}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={save}
          disabled={busy}
          className="text-sm bg-apple-blue text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
        >
          {busy ? "Saving…" : "Save"}
        </button>
        <button
          type="button"
          onClick={() => setEditing(null)}
          disabled={busy}
          className="text-sm border border-apple-border2 rounded-lg px-4 py-2 text-apple-text"
        >
          Cancel
        </button>
      </div>
    </div>
  );

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-apple-secondary">
          {data.length} areas · {liveCount} live now
        </p>
        <button
          type="button"
          onClick={startNew}
          className="inline-flex items-center gap-1.5 text-sm bg-apple-blue text-white rounded-lg px-4 py-2 font-medium"
        >
          <Plus size={16} /> New surcharge
        </button>
      </div>

      {form}

      {error && <p className="text-sm text-apple-red">{String(error)}</p>}
      {actionError && <p className="text-sm text-apple-red">{actionError}</p>}

      {loading ? (
        <p className="text-sm text-apple-secondary">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-apple-secondary">
          {query ? "No areas match." : "No delivery surcharges yet."}
        </p>
      ) : (
        <ul className="space-y-3">
          {rows.map((s) => {
            const st = STATUS[statusOf(s, now)];
            return (
              <li
                key={s.id}
                className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-4 flex flex-wrap items-start justify-between gap-3"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold text-apple-text">{s.areaName || "(unnamed)"}</span>
                    <Badge tone={st.tone}>{st.label}</Badge>
                  </div>
                  <p className="text-sm text-apple-secondary mt-1">{describeCharge(s)}</p>
                  <p className="text-xs text-apple-secondary mt-1">{describeWindow(s)}</p>
                </div>
                <div className="flex gap-2 shrink-0">
                  <button
                    type="button"
                    onClick={() => toggle(s)}
                    className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 text-apple-text"
                  >
                    {s.isActive === false ? "Switch on" : "Switch off"}
                  </button>
                  <button
                    type="button"
                    onClick={() => startEdit(s)}
                    className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 text-apple-text"
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    onClick={() => remove(s)}
                    aria-label="Remove surcharge"
                    className="text-sm border border-apple-border2 rounded-lg px-3 py-1.5 text-apple-red"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
