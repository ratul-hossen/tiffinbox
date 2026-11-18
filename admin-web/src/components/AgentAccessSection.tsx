import { useEffect, useState } from "react";
import { collection, deleteDoc, doc, onSnapshot, serverTimestamp, setDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import { logActivity } from "../lib/activityLog";
import { formatDateTime } from "../lib/format";

interface Connection {
  id: string;
  clientName?: string;
  createdAt?: number;
  lastUsedAt?: number;
}

/**
 * A customer's AI agents (MCP): the agents they connected
 * (users/{uid}/agent_connections, written by mcp-server/) and the admin's
 * per-account block (mcp_blocks/{uid}). Disconnect / block take effect on the
 * agent's next call (within a minute).
 */
export default function AgentAccessSection({ uid }: { uid: string }) {
  const [connections, setConnections] = useState<Connection[]>([]);
  const [blocked, setBlocked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const unsubConnections = onSnapshot(collection(db, "users", uid, "agent_connections"), (snap) =>
      setConnections(
        snap.docs
          .map((d) => ({ id: d.id, ...(d.data() as Omit<Connection, "id">) }))
          .sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0)),
      ),
    );
    const unsubBlock = onSnapshot(doc(db, "mcp_blocks", uid), (snap) => setBlocked(snap.data()?.blocked === true));
    return () => {
      unsubConnections();
      unsubBlock();
    };
  }, [uid]);

  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  const disconnect = (c: Connection) =>
    run(async () => {
      if (!confirm(`Disconnect ${c.clientName ?? "this agent"} from this account?`)) return;
      await deleteDoc(doc(db, "users", uid, "agent_connections", c.id));
      await logActivity("disconnect_agent", "users", uid, { connectionId: c.id, clientName: c.clientName ?? null });
    });

  const toggleBlock = () =>
    run(async () => {
      if (blocked) {
        await deleteDoc(doc(db, "mcp_blocks", uid));
        await logActivity("unblock_mcp", "mcp_blocks", uid);
      } else {
        if (!confirm("Turn off AI agent access for this account? Connected agents stop working within a minute.")) return;
        await setDoc(doc(db, "mcp_blocks", uid), {
          blocked: true,
          blockedAt: serverTimestamp(),
          blockedBy: auth.currentUser?.email ?? null,
        });
        await logActivity("block_mcp", "mcp_blocks", uid);
      }
    });

  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-apple-text">AI agents (MCP)</h4>
        <button
          type="button"
          disabled={busy}
          onClick={toggleBlock}
          className={`text-xs font-medium rounded-full px-3 py-1.5 disabled:opacity-40 ${
            blocked ? "bg-apple-green/10 text-apple-green" : "bg-apple-red/10 text-apple-red"
          }`}
        >
          {blocked ? "Allow AI agents again" : "Block AI agents"}
        </button>
      </div>
      {blocked && <p className="text-xs text-apple-red">AI agent access is blocked for this account.</p>}
      {connections.length === 0 ? (
        <p className="text-xs text-apple-secondary">No agents connected.</p>
      ) : (
        <ul className="space-y-1.5">
          {connections.map((c) => (
            <li key={c.id} className="flex items-center justify-between text-sm bg-apple-bg rounded-xl px-3 py-2">
              <div>
                <div className="font-medium">{c.clientName || "AI agent"}</div>
                <div className="text-xs text-apple-secondary">
                  Connected {c.createdAt ? formatDateTime(c.createdAt) : "–"} · last used{" "}
                  {c.lastUsedAt ? formatDateTime(c.lastUsedAt) : "–"}
                </div>
              </div>
              <button
                type="button"
                disabled={busy}
                onClick={() => disconnect(c)}
                className="text-xs font-medium text-apple-red hover:underline disabled:opacity-40"
              >
                Disconnect
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && <p className="text-xs text-apple-red">{error}</p>}
    </section>
  );
}
