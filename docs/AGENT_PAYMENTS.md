# Agent orders: payment verification, refunds and hardening

This is the contract for AI-agent (MCP) orders. The MCP server
(`mcp-server/`), the Android app (`app/`), admin-web (`admin-web/`) and
`firestore.rules` all follow it. **Change it here first, then in all four.**

## Why it works this way

TiffinBox has no bKash/Nagad merchant or agent account yet. So agent orders
are paid by **Send Money to TiffinBox's personal number**
(`app_config/settings.agentPaymentNumber`, default `01304376749`). No API can
confirm those payments, so **an admin checks each one by hand** against the
SMS or statement on that phone. The design makes that check reliable:

- The customer gives **three facts**:
  - the **TrxID**,
  - the **method** (bKash/Nagad),
  - the **last 4 digits of the number they paid from**.
- The admin matches all three, plus the **amount** and **time**, against the
  received-money SMS: *"You have received Tk 385.00 from 01XXXXXX5678 ...
  TrxID ABC123XYZ at 19/11/2026 14:30"*.
- A TrxID can be claimed by one order only (`payment_txn_ids`).
- Every outcome is recorded on the order, including money that has to go
  back (`refundStatus`), so nothing is lost.

When a merchant account exists later, only the "admin checks by hand" step
changes (the merchant API confirms the payment). Everything else stays.

## Order states (agent orders, `source: "agent"`)

```
AwaitingConfirmation --customer taps Confirm (15 min)--> AwaitingPayment
AwaitingPayment --TrxID + method + sender last 4 (60 min + 30 min grace)--> PaymentSubmitted
PaymentSubmitted --admin: Approve--> Placed (chef sees it; placedAt reset)
PaymentSubmitted --admin: Ask again--> AwaitingPayment (payment claim deleted, paymentNote shown, new payBy)
PaymentSubmitted --admin: Reject (no money found)--> Cancelled (cancelReason "payment_rejected", plates back)
AwaitingConfirmation / AwaitingPayment --deadline passed--> Cancelled ("auto_expired", plates back)
AwaitingConfirmation / AwaitingPayment --customer or agent cancel--> Cancelled ("customer", plates back)
Placed (paid) --chef rejects / 12 h not accepted / admin cancels--> Cancelled + refundStatus "due"
```

The chef can't see or touch an order until it is `Placed`. A paid order can
never be self-cancelled by the customer; money goes back only through a refund.

## Fields

On `orders/{id}` (agent orders):

| Field | Written by | Meaning |
|---|---|---|
| `source` = `"agent"`, `agentClientName`, `agentConnectionId` | MCP server | Origin |
| `confirmBy` | MCP server | Confirm deadline (placedAt + 15 min) |
| `payBy` | app on Confirm, admin on Ask again | Payment deadline (+60 min). Submissions accepted until `payBy + 30 min` (grace) |
| `paymentMethod` (`BKASH`/`NAGAD`), `paymentTransactionId` (8-12 A-Z0-9), `paymentSenderLast4` (4 digits), `paymentSubmittedAt`, `paymentSubmittedVia` (`agent`/`app`) | MCP server or app | What the customer paid with |
| `paymentAmountReceived`, `paymentVerifiedAt`, `paymentVerifiedBy` | admin | What the admin actually saw arrive |
| `paymentNote` | admin (Ask again) | Shown to the customer, e.g. "TrxID not found, check it" |
| `rejectReason` | admin (Reject) | Shown to the customer |
| `refundStatus` (`"due"`/`"refunded"`), `refundAmount`, `refundReason` | whoever cancels a paid order (chef app, admin, expiry) | Money owed back |
| `refundTrxId`, `refundMethod`, `refundedAt`, `refundedBy` | admin | Refund done |

On `payment_txn_ids/{METHOD}_{TXNID}` (one per TrxID, never reused):
`{ orderId, customerId, method, senderLast4, createdAt }`. Created by the MCP
server or the customer's app in the same atomic write that sets
`PaymentSubmitted`. Only an admin may delete one, and only on "Ask again".

Constants (same value everywhere):
- `CONFIRM_WINDOW` = 15 min
- `PAYMENT_WINDOW` = 60 min
- `PAYMENT_GRACE` = 30 min
- `ACCEPT_TIMEOUT` = 12 h (existing)
- `PAYMENT_SLA_ALERT` = 30 min (admin warning)

## Admin checklist (Agent Payments page)

For each `PaymentSubmitted` order, the page shows: expected ৳, method, TrxID,
sender last 4, time submitted. The admin opens the TiffinBox bKash/Nagad
app/SMS and:

1. **Found, same amount, sender digits match** → enter the amount received →
   **Approve**. Runs in a transaction; it requires the status to still be
   `PaymentSubmitted`. The coupon use is counted. Customer and chef are
   notified, and the order goes into their chat.
2. **Found but more than expected** → Approve. The extra is recorded as
   `refundStatus "due"` with `refundAmount` = the difference.
3. **Found but less than expected** → **Ask again** with a note ("Received
   ৳300 of ৳385 - send the remaining ৳85 and give both TrxIDs"). Partial
   payments are settled by hand. Alternatively **Reject** and record a refund
   of what arrived.
4. **Not found / wrong digits** → **Ask again** (typo) or **Reject** (no
   money at all).

The page warns when a payment has waited more than 30 minutes, and when the
order's coupon is already used up. **Refunds** tab: every order with
`refundStatus "due"` → **Mark refunded** with the refund TrxID → the customer
is notified. Paid orders are never moved to the Recycle Bin while a refund is
due.

## Expiry without Cloud Functions

- The MCP Worker runs a **Cron Trigger every 5 minutes** (free plan). It
  cancels `AwaitingConfirmation` orders past `confirmBy` and `AwaitingPayment`
  orders past `payBy + grace`, giving the plates back. Once an hour it also
  cancels paid agent orders still `Placed` 12 h after approval, with
  `refundStatus "due"` and `refundReason "chef_timeout"`.
- The app and admin-web do the same lazily on read, as a backup.

## Abuse limits (MCP server)

- Pairing codes:
  - Wrong-code lockout is keyed by IP, with IPv6 bucketed by /64.
  - A global daily cap on pairing attempts.
  - The MCP read budget is checked before the code lookup.
  - At most 3 agent connections per customer.
- Per-customer daily Firestore read cap, as well as the MCP-wide budget.
- Messages to kitchens:
  - Only to kitchens the customer has a real order with (`Placed` →
    `Delivered`) in the last 30 days.
  - 20 a day per customer, 5 a day per kitchen, 15 s apart.
- "Per day" for orders and messages means the **Dhaka** day.
- The consent page shows **where the tokens go** (the redirect host).
  Unknown hosts are labelled "Unverified agent". The host is saved on the
  connection and shown in the app.
