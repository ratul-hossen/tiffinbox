import { useMemo, useState } from "react";
import { addDoc, collection, doc, increment, setDoc, updateDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { formatCurrency, formatDateTime } from "../lib/format";
import { cancelOrderReleasingPlates } from "../lib/orderPlates";
import type { Chef, Order } from "../types";
import Badge from "./ui/Badge";

/**
 * AI agent orders (mcp-server) are prepaid: the customer sends money by
 * bKash/Nagad to TiffinBox's number and gives the TrxID. Nothing reaches the
 * kitchen until an admin checks that payment here, in the bKash/Nagad
 * merchant app or statement, and approves it.
 *  - Approve: the order becomes "Placed" (the chef sees it like any order),
 *    the customer and the chef are notified, and the order goes into their chat.
 *  - Reject: cancelled, plates go back to the menu, the customer is told why.
 * The chef is paid by TiffinBox separately, outside the app.
 */
type Tab = "verify" | "waiting";

export default function AgentPaymentsPage({ search }: { search: string }) {
  const { data: orders } = useCollection<Order>("orders");
  const { data: chefs } = useCollection<Chef>("chefs");
  const [tab, setTab] = useState<Tab>("verify");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const kitchenName = (id?: string) => chefs.find((c) => c.id === id)?.kitchenName ?? "Kitchen";
  const agentOrders = useMemo(() => orders.filter((o) => o.source === "agent"), [orders]);
  const q = search.trim().toLowerCase();
  const matches = (o: Order) =>
    !q ||
    [o.id, o.customerName, o.customerPhone, o.paymentTransactionId, kitchenName(o.chefId)]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(q));
  const toVerify = agentOrders
    .filter((o) => o.status === "PaymentSubmitted")
    .filter(matches)
    .sort((a, b) => (a.paymentSubmittedAt ?? 0) - (b.paymentSubmittedAt ?? 0));
  const waiting = agentOrders
    .filter((o) => o.status === "AwaitingConfirmation" || o.status === "AwaitingPayment")
    .filter(matches)
    .sort((a, b) => (b.placedAt ?? 0) - (a.placedAt ?? 0));

  async function approve(o: Order) {
    if (!confirm(`Approve ${o.paymentMethod} TrxID ${o.paymentTransactionId} for ${formatCurrency(o.totalPrice ?? 0)}?\n\nOnly approve after you have seen this payment arrive in the ${o.paymentMethod === "NAGAD" ? "Nagad" : "bKash"} account.`)) return;
    setBusyId(o.id);
    setMessage(null);
    try {
      const now = Date.now();
      // placedAt restarts so the chef's 12-hour acceptance window starts now.
      await updateDoc(doc(db, "orders", o.id), {
        status: "Placed",
        placedAt: now,
        paymentVerifiedAt: now,
        paymentVerifiedBy: auth.currentUser?.email ?? null,
      });
      const short = o.id.slice(-6).toUpperCase();
      const items = (o.items ?? []).map((i) => `${i.mealName} x${i.quantity}`).join(", ");
      const kitchen = kitchenName(o.chefId);
      if (o.customerId) {
        await addDoc(collection(db, "notifications", o.customerId, "items"), {
          title: "পেমেন্ট যাচাই হয়েছে · Payment verified",
          body: `Order #${short} (${items}) has gone to ${kitchen}.`,
          createdAt: now,
        });
      }
      if (o.chefId) {
        await addDoc(collection(db, "notifications", o.chefId, "items"), {
          title: "New order (prepaid)",
          body: `${items} - ৳${o.totalPrice ?? 0}, already paid. Open your dashboard to accept it.`,
          createdAt: now,
        });
      }
      // Same order message the app posts in the customer-kitchen chat.
      if (o.customerId && o.chefId) {
        const chatId = `${o.customerId}_${o.chefId}`;
        const text =
          `🍱 নতুন অর্ডার (AI এজেন্টের মাধ্যমে, আগে পেমেন্ট হয়েছে)\nঅর্ডার আইডি: #${short}\nখাবার: ${items}\n` +
          `মোট মূল্য: ৳${o.totalPrice ?? 0}\nপেমেন্ট: ${o.paymentMethod} (যাচাই করা)`;
        await setDoc(
          doc(db, "chats", chatId),
          { customerId: o.customerId, chefId: o.chefId, chefName: kitchen, lastMessage: text, lastMessageAt: now, unreadForChef: increment(1) },
          { merge: true },
        );
        await addDoc(collection(db, "chats", chatId, "messages"), {
          senderId: o.customerId,
          text,
          sentAt: now,
          type: "text",
          viaAgent: true,
        });
      }
      await logActivity("verify_agent_payment", "orders", o.id, {
        method: o.paymentMethod,
        transactionId: o.paymentTransactionId,
        total: o.totalPrice,
      });
      setMessage({ ok: true, text: `Order #${short} approved and sent to ${kitchen}.` });
    } catch (err) {
      setMessage({ ok: false, text: err instanceof Error ? err.message : String(err) });
    } finally {
      setBusyId(null);
    }
  }

  async function reject(o: Order) {
    const reason = prompt(
      "Why is this payment rejected? The customer will see this.",
      "We couldn't find this payment. Please check the TrxID and order again.",
    );
    if (reason === null) return;
    setBusyId(o.id);
    setMessage(null);
    try {
      await cancelOrderReleasingPlates(o, "payment_rejected");
      await updateDoc(doc(db, "orders", o.id), { rejectReason: reason.slice(0, 200) });
      if (o.customerId) {
        await addDoc(collection(db, "notifications", o.customerId, "items"), {
          title: "পেমেন্ট গ্রহণ হয়নি · Payment not accepted",
          body: `Order #${o.id.slice(-6).toUpperCase()} was cancelled: ${reason.slice(0, 200)}`,
          createdAt: Date.now(),
        });
      }
      await logActivity("reject_agent_payment", "orders", o.id, { reason, transactionId: o.paymentTransactionId });
      setMessage({ ok: true, text: "Rejected. The customer has been told." });
    } catch (err) {
      setMessage({ ok: false, text: err instanceof Error ? err.message : String(err) });
    } finally {
      setBusyId(null);
    }
  }

  const list = tab === "verify" ? toVerify : waiting;

  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        {(
          [
            ["verify", `Check payment (${toVerify.length})`],
            ["waiting", `Waiting on customer (${waiting.length})`],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={`rounded-full px-3 py-1.5 text-sm font-medium border ${
              tab === key ? "bg-apple-blue text-white border-apple-blue" : "border-apple-border text-apple-text hover:bg-apple-hover"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "verify" && (
        <p className="text-sm text-apple-secondary">
          Open the TiffinBox bKash/Nagad account, find a received payment with this TrxID and amount, then Approve. Never
          approve a payment you can't see.
        </p>
      )}
      {message && <p className={`text-sm ${message.ok ? "text-apple-green" : "text-apple-red"}`}>{message.text}</p>}
      {list.length === 0 ? (
        <p className="text-sm text-apple-secondary">Nothing here.</p>
      ) : (
        <ul className="space-y-3">
          {list.map((o) => (
            <li key={o.id} className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-4 space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold">#{o.id.slice(-6).toUpperCase()}</span>
                <Badge tone={o.status === "PaymentSubmitted" ? "amber" : "neutral"}>{o.status}</Badge>
                <span className="text-xs text-apple-secondary">via {o.agentClientName || "AI agent"}</span>
                <span className="ml-auto text-lg font-bold">{formatCurrency(o.totalPrice ?? 0)}</span>
              </div>
              <div className="text-sm">
                <span className="font-medium">{o.customerName || "Customer"}</span>
                {o.customerPhone ? <span className="text-apple-secondary"> · {o.customerPhone}</span> : null}
                <span className="text-apple-secondary"> → {kitchenName(o.chefId)}</span>
              </div>
              <div className="text-sm text-apple-secondary">
                {(o.items ?? []).map((i) => `${i.quantity}× ${i.mealName}`).join(", ")}
              </div>
              {o.status === "PaymentSubmitted" ? (
                <div className="grid sm:grid-cols-3 gap-2 text-sm">
                  <div className="bg-apple-bg rounded-xl px-3 py-2">
                    <div className="text-xs text-apple-secondary">Method</div>
                    <div className="font-medium">{o.paymentMethod === "NAGAD" ? "Nagad" : "bKash"}</div>
                  </div>
                  <div className="bg-apple-bg rounded-xl px-3 py-2">
                    <div className="text-xs text-apple-secondary">TrxID</div>
                    <div className="font-mono font-semibold">{o.paymentTransactionId}</div>
                  </div>
                  <div className="bg-apple-bg rounded-xl px-3 py-2">
                    <div className="text-xs text-apple-secondary">Submitted</div>
                    <div className="font-medium">
                      {formatDateTime(o.paymentSubmittedAt)} ({o.paymentSubmittedVia === "app" ? "app" : "agent"})
                    </div>
                  </div>
                </div>
              ) : (
                <div className="text-xs text-apple-secondary">
                  {o.status === "AwaitingConfirmation"
                    ? `Customer hasn't confirmed yet (until ${formatDateTime(o.confirmBy)}).`
                    : `Confirmed; waiting for payment (until ${formatDateTime(o.payBy)}).`}{" "}
                  Placed {formatDateTime(o.placedAt)}.
                </div>
              )}
              {o.status === "PaymentSubmitted" && (
                <div className="flex gap-2 pt-1">
                  <button
                    type="button"
                    disabled={busyId === o.id}
                    onClick={() => approve(o)}
                    className="text-sm bg-apple-green text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
                  >
                    Payment received - approve
                  </button>
                  <button
                    type="button"
                    disabled={busyId === o.id}
                    onClick={() => reject(o)}
                    className="text-sm bg-apple-red/10 text-apple-red rounded-lg px-4 py-2 font-medium disabled:opacity-40"
                  >
                    Reject
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <p className="text-xs text-apple-secondary">
        Every approve / reject is recorded in the Activity Log with your email ({auth.currentUser?.email}).
      </p>
    </div>
  );
}

/** Amber count pill next to "Agent Payments" in the sidebar. */
export function AgentPaymentsCount() {
  const { data: orders } = useCollection<Order>("orders");
  const count = orders.filter((o) => o.source === "agent" && o.status === "PaymentSubmitted").length;
  if (count === 0) return null;
  return (
    <span className="ml-auto min-w-[20px] text-center text-[11px] font-semibold rounded-full bg-apple-amber text-white px-1.5 py-0.5">
      {count}
    </span>
  );
}
