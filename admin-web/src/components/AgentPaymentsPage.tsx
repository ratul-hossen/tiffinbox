import { useEffect, useMemo, useRef, useState } from "react";
import { addDoc, collection, doc, increment, runTransaction, setDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import { logActivity } from "../lib/activityLog";
import { formatCurrency, formatDateTime } from "../lib/format";
import { cancelOrderReleasingPlates } from "../lib/orderPlates";
import type { Chef, Coupon, Order } from "../types";
import { PAYMENT_GRACE_MS, PAYMENT_SLA_ALERT_MS, PAYMENT_WINDOW_MS } from "../types";
import Badge from "./ui/Badge";
import StatCard from "./StatCard";

/**
 * AI agent orders (mcp-server) are prepaid: the customer sends money by
 * bKash/Nagad to TiffinBox's number and gives the TrxID, the method and the
 * last 4 digits of the number they paid from. Nothing reaches the kitchen
 * until an admin finds that payment in the TiffinBox bKash/Nagad app/SMS.
 * The contract (states, fields, windows) is docs/AGENT_PAYMENTS.md.
 *  - Approve: transaction, still PaymentSubmitted -> "Placed"; coupon use is
 *    counted; an overpayment is recorded as a refund due.
 *  - Ask again: back to AwaitingPayment with a note and a new payBy; the
 *    TrxID claim is deleted so the customer can submit it again.
 *  - Reject: no money found -> cancelled, plates back, the TrxID stays claimed.
 *  - Refunds due: money owed back -> Mark refunded with the refund TrxID.
 * The chef is paid by TiffinBox separately, outside the app.
 */
type Tab = "verify" | "waiting" | "refunds" | "verified";
type Msg = { ok: boolean; text: string } | null;

const TRX_RE = /^[A-Z0-9]{8,12}$/;
const methodName = (m?: string) => (m === "NAGAD" ? "Nagad" : "bKash");
const short = (id: string) => id.slice(-6).toUpperCase();
const itemsText = (o: Order) => (o.items ?? []).map((i) => `${i.mealName} x${i.quantity}`).join(", ");
const errText = (err: unknown) => (err instanceof Error ? err.message : String(err));

function startOfToday(): number {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** The coupon problem to warn about, if any (it was valid when the order was made). */
function couponWarning(code: string, coupons: Coupon[], now: number): string | null {
  const c = coupons.find((x) => x.id === code.toUpperCase());
  if (!c) return "This coupon no longer exists.";
  if (c.cancelledByAdmin || c.isActive === false) return "This coupon is now switched off.";
  if ((c.maxUses ?? 0) > 0 && (c.usedCount ?? 0) >= (c.maxUses ?? 0))
    return `This coupon is used up (${c.usedCount}/${c.maxUses}).`;
  if (c.validUntil && c.validUntil < now) return "This coupon has expired since.";
  return null;
}

async function notify(uid: string | undefined, title: string, body: string) {
  if (!uid) return;
  await addDoc(collection(db, "notifications", uid, "items"), { title, body, createdAt: Date.now() });
}

export default function AgentPaymentsPage({ search }: { search: string }) {
  const { data: orders, loading } = useCollection<Order>("orders");
  const { data: chefs } = useCollection<Chef>("chefs");
  const { data: coupons } = useCollection<Coupon>("coupons");
  const [tab, setTab] = useState<Tab>("verify");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState<Msg>(null);
  const [now, setNow] = useState(Date.now());
  // Per-card inputs, keyed by order id.
  const [received, setReceived] = useState<Record<string, string>>({});
  const [senderOk, setSenderOk] = useState<Record<string, boolean>>({});
  const [askOpen, setAskOpen] = useState<string | null>(null);
  const [askNote, setAskNote] = useState<Record<string, string>>({});
  const [refundTrx, setRefundTrx] = useState<Record<string, string>>({});
  const [refundMethod, setRefundMethod] = useState<Record<string, string>>({});

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(t);
  }, []);

  // Lazy expiry (backup for the MCP Worker's 5-minute cron): once per page
  // load, cancel agent orders past their deadline and give the plates back.
  // The transaction re-checks the status, so a payment that just came in wins.
  const expiryRan = useRef(false);
  useEffect(() => {
    if (loading || expiryRan.current) return;
    expiryRan.current = true;
    const t = Date.now();
    const expired = orders.filter(
      (o) =>
        o.source === "agent" &&
        ((o.status === "AwaitingConfirmation" && !!o.confirmBy && t > o.confirmBy) ||
          (o.status === "AwaitingPayment" && !!o.payBy && t > o.payBy + PAYMENT_GRACE_MS)),
    );
    expired.forEach((o) => {
      cancelOrderReleasingPlates(o, "auto_expired", { expectStatus: [String(o.status)] })
        .then((done) => {
          if (done) return logActivity("expire_agent_order", "orders", o.id, { status: o.status });
        })
        .catch(() => {});
    });
  }, [loading, orders]);

  const kitchenName = (id?: string) => chefs.find((c) => c.id === id)?.kitchenName ?? "Kitchen";
  const agentOrders = useMemo(() => orders.filter((o) => o.source === "agent"), [orders]);
  const q = search.trim().toLowerCase();
  const matches = (o: Order) =>
    !q ||
    [o.id, o.customerName, o.customerPhone, o.paymentTransactionId, o.refundTrxId, kitchenName(o.chefId)]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(q));

  const toVerifyAll = agentOrders.filter((o) => o.status === "PaymentSubmitted");
  const refundsAll = orders.filter((o) => o.refundStatus === "due");
  const today = startOfToday();
  const verifiedTodayAll = agentOrders.filter((o) => (o.paymentVerifiedAt ?? 0) >= today);

  const toVerify = toVerifyAll.filter(matches).sort((a, b) => (a.paymentSubmittedAt ?? 0) - (b.paymentSubmittedAt ?? 0));
  const waitingAll = agentOrders.filter((o) => o.status === "AwaitingConfirmation" || o.status === "AwaitingPayment");
  const waiting = waitingAll
    .filter(matches)
    .sort((a, b) => (b.placedAt ?? 0) - (a.placedAt ?? 0));
  const refunds = refundsAll.filter(matches).sort((a, b) => (a.cancelledAt ?? a.paymentVerifiedAt ?? 0) - (b.cancelledAt ?? b.paymentVerifiedAt ?? 0));
  const verifiedToday = verifiedTodayAll.filter(matches).sort((a, b) => (b.paymentVerifiedAt ?? 0) - (a.paymentVerifiedAt ?? 0));

  const sum = (list: Order[], f: (o: Order) => number) => list.reduce((s, o) => s + f(o), 0);

  async function run(id: string, fn: () => Promise<Msg>) {
    setBusyId(id);
    setMessage(null);
    try {
      setMessage(await fn());
    } catch (err) {
      setMessage({ ok: false, text: errText(err) });
    } finally {
      setBusyId(null);
    }
  }

  const alreadyHandled = (status?: string): Msg => ({
    ok: false,
    text: `Already handled - this order is now "${status ?? "gone"}". Nothing was changed.`,
  });

  function approve(o: Order) {
    const amount = Number(received[o.id]);
    run(o.id, async () => {
      type R =
        | { kind: "handled"; status?: string }
        | { kind: "short"; expected: number }
        | { kind: "ok"; order: Order; at: number; overpaid: number; couponCounted: boolean };
      const ref = doc(db, "orders", o.id);
      const result = await runTransaction<R>(db, async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists()) return { kind: "handled" };
        const cur = { id: snap.id, ...snap.data() } as Order;
        if (cur.status !== "PaymentSubmitted") return { kind: "handled", status: String(cur.status) };
        const expected = cur.totalPrice ?? 0;
        if (amount < expected) return { kind: "short", expected };
        // Reads before writes: the coupon is read now and counted below.
        const couponRef = cur.couponCode ? doc(db, "coupons", cur.couponCode.toUpperCase()) : null;
        const couponExists = couponRef ? (await tx.get(couponRef)).exists() : false;
        const at = Date.now();
        const overpaid = amount - expected;
        const update: Record<string, unknown> = {
          status: "Placed",
          // placedAt restarts so the chef's 12-hour acceptance window starts now.
          placedAt: at,
          paymentAmountReceived: amount,
          paymentVerifiedAt: at,
          paymentVerifiedBy: auth.currentUser?.email ?? null,
        };
        if (overpaid > 0) {
          update.refundStatus = "due";
          update.refundAmount = overpaid;
          update.refundReason = "overpaid";
        }
        tx.update(ref, update);
        if (couponRef && couponExists) tx.update(couponRef, { usedCount: increment(1) });
        return { kind: "ok", order: cur, at, overpaid, couponCounted: couponExists };
      });
      if (result.kind === "handled") return alreadyHandled(result.status);
      if (result.kind === "short")
        return {
          ok: false,
          text: `৳${amount} is less than the ৳${result.expected} expected. Use "Ask again" instead.`,
        };

      const cur = result.order;
      const items = itemsText(cur);
      const kitchen = kitchenName(cur.chefId);
      await notify(cur.customerId, "পেমেন্ট যাচাই হয়েছে · Payment verified", `Order #${short(cur.id)} (${items}) has gone to ${kitchen}.`);
      await notify(
        cur.chefId,
        "New order (prepaid)",
        `${items} - ৳${cur.totalPrice ?? 0}, already paid. Open your dashboard to accept it.`,
      );
      // Same order message the app posts in the customer-kitchen chat.
      if (cur.customerId && cur.chefId) {
        const chatId = `${cur.customerId}_${cur.chefId}`;
        const text =
          `🍱 নতুন অর্ডার (AI এজেন্টের মাধ্যমে, আগে পেমেন্ট হয়েছে)\nঅর্ডার আইডি: #${short(cur.id)}\nখাবার: ${items}\n` +
          `মোট মূল্য: ৳${cur.totalPrice ?? 0}\nপেমেন্ট: ${cur.paymentMethod} (যাচাই করা)`;
        await setDoc(
          doc(db, "chats", chatId),
          {
            customerId: cur.customerId,
            chefId: cur.chefId,
            chefName: kitchen,
            lastMessage: text,
            lastMessageAt: result.at,
            unreadForChef: increment(1),
          },
          { merge: true },
        );
        await addDoc(collection(db, "chats", chatId, "messages"), {
          senderId: cur.customerId,
          text,
          sentAt: result.at,
          type: "text",
          viaAgent: true,
        });
      }
      await logActivity("verify_agent_payment", "orders", cur.id, {
        method: cur.paymentMethod,
        transactionId: cur.paymentTransactionId,
        senderLast4: cur.paymentSenderLast4 ?? null,
        total: cur.totalPrice,
        amountReceived: amount,
        overpaid: result.overpaid,
        couponCounted: result.couponCounted,
      });
      return {
        ok: true,
        text:
          `Order #${short(cur.id)} approved and sent to ${kitchen}.` +
          (result.overpaid > 0 ? ` ৳${result.overpaid} overpaid - added to Refunds due.` : ""),
      };
    });
  }

  function askAgain(o: Order) {
    const note = (askNote[o.id] ?? "").trim().slice(0, 300);
    if (!note) return;
    run(o.id, async () => {
      const ref = doc(db, "orders", o.id);
      const result = await runTransaction(db, async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists()) return { ok: false as const };
        const cur = { id: snap.id, ...snap.data() } as Order;
        if (cur.status !== "PaymentSubmitted") return { ok: false as const, status: String(cur.status) };
        const payBy = Date.now() + PAYMENT_WINDOW_MS;
        tx.update(ref, {
          status: "AwaitingPayment",
          payBy,
          paymentNote: note,
          paymentTransactionId: "",
          paymentSenderLast4: "",
        });
        // Free the TrxID so the customer can submit it again (only admins may delete a claim).
        if (cur.paymentMethod && cur.paymentTransactionId) {
          tx.delete(doc(db, "payment_txn_ids", `${cur.paymentMethod}_${cur.paymentTransactionId}`));
        }
        return { ok: true as const, order: cur, payBy };
      });
      if (!result.ok) return alreadyHandled(result.status);
      const cur = result.order;
      const until = new Date(result.payBy).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
      await notify(
        cur.customerId,
        "পেমেন্ট আবার দিন · Please check your payment",
        `Order #${short(cur.id)}: ${note} Send the payment details again by ${until}.`,
      );
      await logActivity("ask_again_agent_payment", "orders", cur.id, {
        note,
        oldTransactionId: cur.paymentTransactionId,
        method: cur.paymentMethod,
      });
      setAskOpen(null);
      return { ok: true, text: `Order #${short(cur.id)} sent back to the customer with your note.` };
    });
  }

  function reject(o: Order) {
    const reason = prompt(
      "Why is this payment rejected? The customer will see this.",
      "We couldn't find this payment. Please check the TrxID and order again.",
    );
    if (reason === null) return;
    const amount = Number(received[o.id]);
    // Some money arrived (less than expected) and the admin rejects anyway: record it as owed back.
    const partial = Number.isFinite(amount) && amount > 0;
    if (partial && !confirm(`You entered ৳${amount} received. Record a refund of ৳${amount} for this customer?`)) return;
    run(o.id, async () => {
      const extra: Record<string, unknown> = { rejectReason: reason.slice(0, 200) };
      if (partial) {
        extra.paymentAmountReceived = amount;
        extra.refundStatus = "due";
        extra.refundAmount = amount;
        extra.refundReason = "payment_rejected";
      }
      // The TrxID claim in payment_txn_ids is kept on purpose: that TrxID can't pay another order.
      const done = await cancelOrderReleasingPlates(o, "payment_rejected", { expectStatus: ["PaymentSubmitted"], extra });
      if (!done) return alreadyHandled();
      await notify(
        o.customerId,
        "পেমেন্ট গ্রহণ হয়নি · Payment not accepted",
        `Order #${short(o.id)} was cancelled: ${reason.slice(0, 200)}` + (partial ? ` ৳${amount} will be refunded.` : ""),
      );
      await logActivity("reject_agent_payment", "orders", o.id, {
        reason,
        transactionId: o.paymentTransactionId,
        refundDue: partial ? amount : 0,
      });
      return { ok: true, text: "Rejected. The customer has been told." + (partial ? " Refund added to Refunds due." : "") };
    });
  }

  function markRefunded(o: Order) {
    const trx = (refundTrx[o.id] ?? "").trim().toUpperCase();
    const method = refundMethod[o.id] ?? (o.paymentMethod === "NAGAD" ? "NAGAD" : "BKASH");
    if (!TRX_RE.test(trx)) return;
    run(o.id, async () => {
      const ref = doc(db, "orders", o.id);
      const result = await runTransaction(db, async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists()) return { ok: false as const };
        const cur = { id: snap.id, ...snap.data() } as Order;
        if (cur.refundStatus !== "due") return { ok: false as const, status: String(cur.refundStatus ?? "no refund") };
        tx.update(ref, {
          refundStatus: "refunded",
          refundTrxId: trx,
          refundMethod: method,
          refundedAt: Date.now(),
          refundedBy: auth.currentUser?.email ?? null,
        });
        return { ok: true as const, order: cur };
      });
      if (!result.ok) return { ok: false, text: `Already handled - refund is "${result.status ?? "gone"}". Nothing was changed.` };
      const cur = result.order;
      const amount = cur.refundAmount ?? 0;
      await notify(
        cur.customerId,
        "টাকা ফেরত দেওয়া হয়েছে · Refund sent",
        `৳${amount} refunded for order #${short(cur.id)} by ${methodName(method)}, TrxID ${trx}.`,
      );
      await logActivity("mark_refunded", "orders", cur.id, {
        amount,
        reason: cur.refundReason ?? null,
        method,
        refundTrxId: trx,
      });
      return { ok: true, text: `Refund of ৳${amount} for #${short(cur.id)} recorded. The customer has been told.` };
    });
  }

  const tabs: [Tab, string][] = [
    ["verify", `Check payment (${toVerifyAll.length})`],
    ["waiting", `Waiting on customer (${waitingAll.length})`],
    ["refunds", `Refunds due (${refundsAll.length})`],
    ["verified", `Verified today (${verifiedTodayAll.length})`],
  ];
  const list = tab === "verify" ? toVerify : tab === "waiting" ? waiting : tab === "refunds" ? refunds : verifiedToday;

  const inputClass =
    "text-sm border border-apple-border2 rounded-lg px-3 py-2 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50";

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="To check"
          value={toVerifyAll.length}
          caption={`${formatCurrency(sum(toVerifyAll, (o) => o.totalPrice ?? 0))} waiting for you`}
        />
        <StatCard
          label="Verified today"
          value={formatCurrency(sum(verifiedTodayAll, (o) => o.paymentAmountReceived ?? o.totalPrice ?? 0))}
          caption={`${verifiedTodayAll.length} payments`}
        />
        <StatCard
          label="Refunds due"
          value={formatCurrency(sum(refundsAll, (o) => o.refundAmount ?? 0))}
          caption={`${refundsAll.length} customers to pay back`}
        />
      </div>

      <div className="flex flex-wrap gap-2">
        {tabs.map(([key, label]) => (
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
          Open the TiffinBox bKash/Nagad account and find the received-money SMS with this TrxID. Check the amount, the
          sender's last 4 digits and the time, then Approve. Never approve a payment you can't see.
        </p>
      )}
      {tab === "refunds" && (
        <p className="text-sm text-apple-secondary">
          Send the money back to the customer's number by bKash/Nagad, then enter that refund's TrxID here.
        </p>
      )}
      {message && <p className={`text-sm ${message.ok ? "text-apple-green" : "text-apple-red"}`}>{message.text}</p>}
      {list.length === 0 ? (
        <p className="text-sm text-apple-secondary">Nothing here.</p>
      ) : (
        <ul className="space-y-3">
          {list.map((o) => {
            const busy = busyId === o.id;
            const expected = o.totalPrice ?? 0;
            const amountStr = received[o.id] ?? "";
            const amount = Number(amountStr);
            const amountOk = amountStr.trim() !== "" && Number.isFinite(amount) && amount > 0;
            const isShort = amountOk && amount < expected;
            const waitedMs = now - (o.paymentSubmittedAt ?? now);
            const coupon = o.couponCode ? couponWarning(o.couponCode, coupons, now) : null;
            const trx = (refundTrx[o.id] ?? "").trim().toUpperCase();
            return (
              <li key={o.id} className="bg-apple-surface rounded-2xl border border-apple-border shadow-card p-4 space-y-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold">#{short(o.id)}</span>
                  {tab === "refunds" ? (
                    <Badge tone="amber">Refund due</Badge>
                  ) : (
                    <Badge tone={o.status === "PaymentSubmitted" ? "amber" : o.status === "Cancelled" ? "red" : "neutral"}>
                      {o.status}
                    </Badge>
                  )}
                  {o.status === "PaymentSubmitted" && waitedMs > PAYMENT_SLA_ALERT_MS && (
                    <Badge tone="red">Waiting &gt; 30 min · {Math.floor(waitedMs / 60000)} min</Badge>
                  )}
                  {o.source === "agent" && <span className="text-xs text-apple-secondary">via {o.agentClientName || "AI agent"}</span>}
                  <span className="ml-auto text-lg font-bold">
                    {tab === "refunds" ? formatCurrency(o.refundAmount ?? 0) : formatCurrency(expected)}
                  </span>
                </div>
                <div className="text-sm">
                  <span className="font-medium">{o.customerName || "Customer"}</span>
                  {o.customerPhone ? <span className="text-apple-secondary"> · {o.customerPhone}</span> : null}
                  <span className="text-apple-secondary"> → {kitchenName(o.chefId)}</span>
                </div>
                <div className="text-sm text-apple-secondary">
                  {(o.items ?? []).map((i) => `${i.quantity}× ${i.mealName}`).join(", ")}
                </div>

                {o.couponCode && tab !== "refunds" ? (
                  <div className="text-xs text-apple-secondary">
                    Coupon <span className="font-mono font-semibold">{o.couponCode}</span> −{formatCurrency(o.discount ?? 0)}
                    {o.status === "PaymentSubmitted" ? " (counted when you approve)" : ""}
                    {coupon && o.status === "PaymentSubmitted" && (
                      <span className="block mt-1 text-apple-amber font-medium">
                        Warning: {coupon} The customer was quoted this discount; approve only if you still honour it.
                      </span>
                    )}
                  </div>
                ) : null}

                {tab === "verify" && (
                  <>
                    <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 text-sm">
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Expected</div>
                        <div className="font-semibold">{formatCurrency(expected)}</div>
                      </div>
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Method</div>
                        <div className="font-medium">{methodName(o.paymentMethod)}</div>
                      </div>
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">TrxID</div>
                        <div className="font-mono font-semibold break-all">{o.paymentTransactionId}</div>
                      </div>
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Sender's last 4</div>
                        <div className="font-mono font-semibold">{o.paymentSenderLast4 || "–"}</div>
                      </div>
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Submitted</div>
                        <div className="font-medium">
                          {formatDateTime(o.paymentSubmittedAt)} ({o.paymentSubmittedVia === "app" ? "app" : "agent"})
                        </div>
                      </div>
                    </div>
                    <div className="flex flex-wrap items-end gap-3 pt-1">
                      <label className="text-xs text-apple-secondary">
                        Amount received (৳)
                        <input
                          type="number"
                          min={0}
                          step="1"
                          inputMode="decimal"
                          required
                          value={amountStr}
                          onChange={(e) => setReceived((r) => ({ ...r, [o.id]: e.target.value }))}
                          className={`${inputClass} block mt-1 w-36`}
                          placeholder={String(expected)}
                        />
                      </label>
                      <label className="flex items-center gap-2 text-sm pb-2">
                        <input
                          type="checkbox"
                          checked={!!senderOk[o.id]}
                          onChange={(e) => setSenderOk((s) => ({ ...s, [o.id]: e.target.checked }))}
                        />
                        Sender's last 4 digits match{o.paymentSenderLast4 ? ` (…${o.paymentSenderLast4})` : ""}
                      </label>
                    </div>
                    {isShort && (
                      <p className="text-xs text-apple-red">
                        ৳{amount} is less than the ৳{expected} expected - Approve is blocked. Use Ask again to ask for the
                        remaining ৳{expected - amount}, or Reject and record a refund of what arrived.
                      </p>
                    )}
                    {amountOk && amount > expected && (
                      <p className="text-xs text-apple-amber">
                        ৳{amount - expected} more than expected - it will be recorded as a refund due.
                      </p>
                    )}
                    <div className="flex flex-wrap gap-2 pt-1">
                      <button
                        type="button"
                        disabled={busy || !amountOk || isShort || !senderOk[o.id]}
                        onClick={() => approve(o)}
                        className="text-sm bg-apple-green text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
                      >
                        Payment received - approve
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => {
                          setAskOpen(askOpen === o.id ? null : o.id);
                          setAskNote((n) => ({
                            ...n,
                            [o.id]:
                              n[o.id] ??
                              (isShort
                                ? `Received ৳${amount} of ৳${expected} - send the remaining ৳${expected - amount} and give both TrxIDs.`
                                : "TrxID not found, please check it and send it again."),
                          }));
                        }}
                        className="text-sm bg-apple-amber/10 text-apple-amber rounded-lg px-4 py-2 font-medium disabled:opacity-40"
                      >
                        Ask again
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => reject(o)}
                        className="text-sm bg-apple-red/10 text-apple-red rounded-lg px-4 py-2 font-medium disabled:opacity-40"
                      >
                        Reject (no money found)
                      </button>
                    </div>
                    {askOpen === o.id && (
                      <div className="bg-apple-bg rounded-xl p-3 space-y-2">
                        <label className="block text-xs text-apple-secondary">
                          Note for the customer (required) - they get a new 60-minute window to pay or resend the TrxID.
                          <textarea
                            rows={2}
                            maxLength={300}
                            value={askNote[o.id] ?? ""}
                            onChange={(e) => setAskNote((n) => ({ ...n, [o.id]: e.target.value }))}
                            className={`${inputClass} block mt-1 w-full`}
                          />
                        </label>
                        <button
                          type="button"
                          disabled={busy || !(askNote[o.id] ?? "").trim()}
                          onClick={() => askAgain(o)}
                          className="text-sm bg-apple-amber text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
                        >
                          Send back to customer
                        </button>
                      </div>
                    )}
                  </>
                )}

                {tab === "waiting" && (
                  <div className="text-xs text-apple-secondary space-y-1">
                    <div>
                      {o.status === "AwaitingConfirmation"
                        ? `Customer hasn't confirmed yet (until ${formatDateTime(o.confirmBy)}).`
                        : `Confirmed; waiting for payment (until ${formatDateTime(o.payBy)}, plus 30 min grace).`}{" "}
                      Placed {formatDateTime(o.placedAt)}.
                    </div>
                    {o.paymentNote && o.status === "AwaitingPayment" && (
                      <div className="text-apple-amber">Asked again: “{o.paymentNote}”</div>
                    )}
                  </div>
                )}

                {tab === "verified" && (
                  <div className="text-xs text-apple-secondary">
                    {methodName(o.paymentMethod)} <span className="font-mono">{o.paymentTransactionId}</span> · received{" "}
                    {formatCurrency(o.paymentAmountReceived ?? o.totalPrice ?? 0)} · verified {formatDateTime(o.paymentVerifiedAt)} by{" "}
                    {o.paymentVerifiedBy ?? "–"} · now <span className="font-medium">{o.status}</span>
                    {o.refundStatus ? ` · refund ${o.refundStatus}` : ""}
                  </div>
                )}

                {tab === "refunds" && (
                  <>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-sm">
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Refund</div>
                        <div className="font-semibold">{formatCurrency(o.refundAmount ?? 0)}</div>
                      </div>
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Reason</div>
                        <div className="font-medium">{o.refundReason || "–"}</div>
                      </div>
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Paid with</div>
                        <div className="font-medium">
                          {methodName(o.paymentMethod)} <span className="font-mono">{o.paymentTransactionId || ""}</span>
                        </div>
                      </div>
                      <div className="bg-apple-bg rounded-xl px-3 py-2">
                        <div className="text-xs text-apple-secondary">Order</div>
                        <div className="font-mono font-medium break-all">{o.id}</div>
                      </div>
                    </div>
                    <div className="flex flex-wrap items-end gap-3 pt-1">
                      <label className="text-xs text-apple-secondary">
                        Refund method
                        <select
                          value={refundMethod[o.id] ?? (o.paymentMethod === "NAGAD" ? "NAGAD" : "BKASH")}
                          onChange={(e) => setRefundMethod((m) => ({ ...m, [o.id]: e.target.value }))}
                          className={`${inputClass} block mt-1`}
                        >
                          <option value="BKASH">bKash</option>
                          <option value="NAGAD">Nagad</option>
                        </select>
                      </label>
                      <label className="text-xs text-apple-secondary">
                        Refund TrxID
                        <input
                          value={refundTrx[o.id] ?? ""}
                          onChange={(e) => setRefundTrx((t) => ({ ...t, [o.id]: e.target.value }))}
                          className={`${inputClass} block mt-1 w-44 font-mono uppercase`}
                          placeholder="8-12 letters/digits"
                          maxLength={12}
                        />
                      </label>
                      <button
                        type="button"
                        disabled={busy || !TRX_RE.test(trx)}
                        onClick={() => markRefunded(o)}
                        className="text-sm bg-apple-green text-white rounded-lg px-4 py-2 font-medium disabled:opacity-40"
                      >
                        Mark refunded
                      </button>
                    </div>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <p className="text-xs text-apple-secondary">
        Every approve / ask again / reject / refund is recorded in the Activity Log with your email ({auth.currentUser?.email}).
      </p>
    </div>
  );
}

/** Amber count pill next to "Agent Payments" in the sidebar: payments to check + refunds due. */
export function AgentPaymentsCount() {
  const { data: orders } = useCollection<Order>("orders");
  const toCheck = orders.filter((o) => o.source === "agent" && o.status === "PaymentSubmitted").length;
  const refunds = orders.filter((o) => o.refundStatus === "due").length;
  const count = toCheck + refunds;
  if (count === 0) return null;
  return (
    <span
      title={`${toCheck} to check · ${refunds} refunds due`}
      className="ml-auto min-w-[20px] text-center text-[11px] font-semibold rounded-full bg-apple-amber text-white px-1.5 py-0.5"
    >
      {count}
    </span>
  );
}
