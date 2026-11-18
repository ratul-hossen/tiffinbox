import { useEffect, useState } from "react";
import { doc, onSnapshot, serverTimestamp, setDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import { logActivity } from "../lib/activityLog";

/**
 * AI agent (MCP) settings in `app_config/settings`, read by mcp-server/ and by
 * the app's Profile -> AI Agent (MCP) screen:
 *  - mcpEnabled: the kill switch. Off = every agent call is refused at once.
 *  - mcpServerUrl: the address the app shows customers (the Worker's URL + /mcp).
 *  - mcpDailyReadBudget: Firestore reads MCP may use per day. Spark gives the
 *    whole project ~50,000; when MCP hits this, only MCP stops - never the app.
 */
const DEFAULT_BUDGET = 15000;
const BUDGET_MIN = 100;
const BUDGET_MAX = 40000;

const inputClass =
  "mt-1 text-sm border border-apple-border2 rounded-lg px-3 py-2 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50 w-full";

export default function McpSettingsCard() {
  const [enabled, setEnabled] = useState(true);
  const [savedUrl, setSavedUrl] = useState("");
  const [savedBudget, setSavedBudget] = useState(DEFAULT_BUDGET);
  const [url, setUrl] = useState<string | null>(null);
  const [budget, setBudget] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(
    () =>
      onSnapshot(doc(db, "app_config", "settings"), (snap) => {
        const d = snap.data() ?? {};
        const b = Number(d.mcpDailyReadBudget);
        const nextBudget = Number.isFinite(b) && b >= BUDGET_MIN ? b : DEFAULT_BUDGET;
        const nextUrl = typeof d.mcpServerUrl === "string" ? d.mcpServerUrl : "";
        setEnabled(d.mcpEnabled !== false);
        setSavedUrl(nextUrl);
        setSavedBudget(nextBudget);
        setUrl((u) => u ?? nextUrl);
        setBudget((v) => v ?? String(nextBudget));
      }),
    [],
  );

  async function write(patch: Record<string, unknown>, action: string, okText: string) {
    setBusy(true);
    setMessage(null);
    try {
      await setDoc(
        doc(db, "app_config", "settings"),
        { ...patch, updatedAt: serverTimestamp(), updatedBy: auth.currentUser?.email ?? null },
        { merge: true },
      );
      await logActivity(action, "app_config", "settings", patch);
      setMessage({ ok: true, text: okText });
    } catch (err) {
      setMessage({ ok: false, text: err instanceof Error ? err.message : String(err) });
    } finally {
      setBusy(false);
    }
  }

  const trimmedUrl = (url ?? "").trim();
  const urlOk = trimmedUrl === "" || /^https:\/\/[^\s/]+\.[^\s]+\/mcp$/.test(trimmedUrl);
  const budgetNum = Number(budget);
  const budgetOk = Number.isInteger(budgetNum) && budgetNum >= BUDGET_MIN && budgetNum <= BUDGET_MAX;
  const changed = trimmedUrl !== savedUrl || budgetNum !== savedBudget;

  return (
    <div className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-5 space-y-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold text-apple-text">AI agents (MCP)</h2>
          <p className="text-sm text-apple-secondary mt-1">
            Lets customers connect Claude, ChatGPT and other AI agents to browse kitchens and menus (Profile → AI Agent
            (MCP) in the app). Turning it off stops every agent within a minute.
          </p>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={() =>
            write(
              { mcpEnabled: !enabled },
              enabled ? "disable_mcp" : "enable_mcp",
              enabled ? "AI agent access is now OFF." : "AI agent access is now ON.",
            )
          }
          className={`shrink-0 rounded-full px-4 py-2 text-sm font-semibold ${
            enabled ? "bg-apple-green/15 text-apple-green" : "bg-apple-red/15 text-apple-red"
          } disabled:opacity-40`}
        >
          {enabled ? "ON · turn off" : "OFF · turn on"}
        </button>
      </div>
      <label className="block text-xs text-apple-secondary">
        Server address shown in the app (ends with /mcp)
        <input
          className={inputClass}
          placeholder="https://tiffinbox-mcp.example.workers.dev/mcp"
          value={url ?? ""}
          onChange={(e) => setUrl(e.target.value)}
        />
      </label>
      <label className="block text-xs text-apple-secondary">
        Daily Firestore read budget for agents ({BUDGET_MIN}–{BUDGET_MAX})
        <input
          className={inputClass}
          type="number"
          min={BUDGET_MIN}
          max={BUDGET_MAX}
          value={budget ?? ""}
          onChange={(e) => setBudget(e.target.value)}
        />
      </label>
      <p className="text-xs text-apple-secondary">
        The free plan allows about 50,000 reads a day for the whole app. When agents use up this budget, only agent
        access pauses until the next day; the app keeps working.
      </p>
      <div className="flex items-center gap-3">
        <button
          type="button"
          disabled={busy || !changed || !urlOk || !budgetOk}
          onClick={() =>
            write({ mcpServerUrl: trimmedUrl, mcpDailyReadBudget: budgetNum }, "update_mcp_settings", "Saved.")
          }
          className="text-sm bg-apple-blue text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
        >
          Save AI agent settings
        </button>
        {!urlOk && <span className="text-xs text-apple-red">Use the https:// address ending in /mcp.</span>}
        {!budgetOk && <span className="text-xs text-apple-red">Budget out of range.</span>}
      </div>
      {message && <p className={`text-sm ${message.ok ? "text-apple-green" : "text-apple-red"}`}>{message.text}</p>}
    </div>
  );
}
