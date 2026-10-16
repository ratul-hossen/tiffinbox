import { useEffect, useMemo, useState } from "react";
import {
  addDoc,
  collection,
  doc,
  onSnapshot,
  orderBy,
  query,
  updateDoc,
} from "firebase/firestore";
import { MessageCircle, Send, ShieldCheck, Trash2 } from "lucide-react";
import { auth, db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { softDeleteChat } from "../lib/recycleBin";
import { formatDateTime } from "../lib/format";
import type { ChatMessage, ChatThread } from "../types";

interface Props {
  search: string;
}

/**
 * Lets an admin read any customer<->chef conversation and drop a message
 * into it. Admin messages are written with isAdmin: true + senderId:
 * "admin" so both the Android app and this panel can render them with a
 * distinct "Admin" label instead of attributing them to the customer or
 * chef — see ChatScreen.kt on the Android side for the matching render
 * logic.
 */
export default function ChatsPage({ search }: Props) {
  const { data: threads, loading, error } = useCollection<ChatThread>("chats", {
    orderByField: "lastMessageAt",
    direction: "desc",
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return threads;
    return threads.filter((t) => {
      const haystack = `${t.chefName ?? ""} ${t.customerId ?? ""} ${t.chefId ?? ""} ${t.lastMessage ?? ""}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [threads, search]);

  const selectedThread = threads.find((t) => t.id === selectedId) ?? null;

  useEffect(() => {
    if (!selectedId) {
      setMessages([]);
      return;
    }
    const q = query(
      collection(db, "chats", selectedId, "messages"),
      orderBy("sentAt", "asc"),
    );
    const unsubscribe = onSnapshot(q, (snap) => {
      setMessages(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as ChatMessage));
    });
    return unsubscribe;
  }, [selectedId]);

  async function handleSend(e: React.FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text || !selectedId || !selectedThread) return;

    setSending(true);
    try {
      const now = Date.now();
      await addDoc(collection(db, "chats", selectedId, "messages"), {
        senderId: "admin",
        text,
        sentAt: now,
        isAdmin: true,
        adminEmail: auth.currentUser?.email ?? null,
      });
      await updateDoc(doc(db, "chats", selectedId), {
        lastMessage: text,
        lastMessageAt: now,
      });
      await logActivity("admin_chat_message", "chats", selectedId, { text });
      setInput("");
    } catch (e) {
      alert(e instanceof Error ? e.message : "Failed to send message.");
    } finally {
      setSending(false);
    }
  }

  async function handleDelete() {
    if (!selectedId || !selectedThread) return;
    const name = selectedThread.chefName || "this conversation";
    if (!confirm(`Delete the chat with ${name}? It'll move to the Recycle Bin, not disappear forever.`)) return;

    setDeleting(true);
    try {
      await softDeleteChat(selectedId, selectedThread, name);
      await logActivity("admin_chat_deleted", "chats", selectedId, { chefName: selectedThread.chefName ?? null });
      setSelectedId(null);
    } catch (e) {
      alert(e instanceof Error ? e.message : "Failed to delete the chat.");
    } finally {
      setDeleting(false);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading chats…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="flex flex-col md:flex-row gap-4 h-[calc(100vh-180px)] min-h-[480px]">
      <div className="md:w-80 shrink-0 bg-apple-surface rounded-2xl border border-apple-border shadow-card overflow-y-auto">
        {filtered.length === 0 ? (
          <div className="px-4 py-8 text-center">
            <MessageCircle className="mx-auto mb-2 text-apple-secondary" size={24} />
            <p className="text-sm text-apple-secondary">No conversations yet.</p>
          </div>
        ) : (
          filtered.map((t) => (
            <button
              key={t.id}
              onClick={() => setSelectedId(t.id)}
              className={`w-full text-left px-4 py-3 border-b border-apple-border last:border-0 transition-colors duration-150 ${
                selectedId === t.id ? "bg-apple-blue/10" : "hover:bg-apple-subtle"
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-medium text-apple-text truncate">
                  {t.chefName || "Chef"}
                </span>
                <span className="text-[11px] text-apple-secondary shrink-0">
                  {formatDateTime(t.lastMessageAt)}
                </span>
              </div>
              <p className="text-xs text-apple-secondary truncate mt-0.5">
                {t.lastMessage || "No messages yet"}
              </p>
              <p className="text-[11px] text-apple-secondary/70 mt-0.5">
                Customer {(t.customerId ?? "").slice(0, 8)}…
              </p>
            </button>
          ))
        )}
      </div>

      <div className="flex-1 bg-apple-surface rounded-2xl border border-apple-border shadow-card flex flex-col overflow-hidden">
        {!selectedThread ? (
          <div className="flex-1 flex items-center justify-center text-sm text-apple-secondary">
            Select a conversation to view it.
          </div>
        ) : (
          <>
            <div className="px-5 py-3 border-b border-apple-border flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-semibold text-apple-text">{selectedThread.chefName || "Chef"}</p>
                <p className="text-xs text-apple-secondary">
                  Customer {(selectedThread.customerId ?? "").slice(0, 10)}… · Chef{" "}
                  {(selectedThread.chefId ?? "").slice(0, 10)}…
                </p>
              </div>
              <button
                onClick={handleDelete}
                disabled={deleting}
                title="Delete this conversation (moves to the Recycle Bin)"
                className="shrink-0 text-apple-red hover:bg-apple-red/10 disabled:opacity-50 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors duration-150 inline-flex items-center gap-1.5"
              >
                <Trash2 size={14} />
                {deleting ? "Deleting…" : "Delete"}
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
              {messages.length === 0 && (
                <p className="text-sm text-apple-secondary text-center mt-8">No messages yet.</p>
              )}
              {messages.map((m) => {
                const isAdminMsg = m.isAdmin === true;
                const isChefMsg = !isAdminMsg && m.senderId?.startsWith("chef:");
                const label = isAdminMsg ? "Admin" : isChefMsg ? selectedThread.chefName || "Chef" : "Customer";
                return (
                  <div
                    key={m.id}
                    className={`flex ${isAdminMsg ? "justify-end" : "justify-start"}`}
                  >
                    <div className="max-w-[70%]">
                      <div
                        className={`text-[11px] mb-1 flex items-center gap-1 ${
                          isAdminMsg ? "justify-end text-apple-amber" : "text-apple-secondary"
                        }`}
                      >
                        {isAdminMsg && <ShieldCheck size={11} />}
                        {label} · {formatDateTime(m.sentAt)}
                      </div>
                      <div
                        className={`rounded-2xl px-3.5 py-2 text-sm ${
                          isAdminMsg
                            ? "bg-apple-amber/15 text-apple-text border border-apple-amber/30"
                            : isChefMsg
                              ? "bg-apple-blue text-white"
                              : "bg-apple-hover text-apple-text"
                        }`}
                      >
                        {m.text}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            <form onSubmit={handleSend} className="px-4 py-3 border-t border-apple-border flex gap-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="Message as Admin…"
                className="flex-1 rounded-xl border border-apple-border2 bg-apple-surface2 px-3.5 py-2.5 text-sm text-apple-text placeholder:text-apple-secondary/70 focus:outline-none focus:ring-2 focus:ring-apple-blue/40"
              />
              <button
                type="submit"
                disabled={sending || !input.trim()}
                className="bg-apple-blue hover:brightness-95 disabled:opacity-50 text-white rounded-xl px-4 py-2.5 text-sm font-medium transition-all duration-150 inline-flex items-center gap-1.5"
              >
                <Send size={15} />
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
