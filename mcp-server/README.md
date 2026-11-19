# TiffinBox MCP server

Lets a customer's own AI agent (Claude, ChatGPT, or any MCP client) use
TiffinBox for them: find kitchens, read menus, check delivery fees, see their
orders, chats, notifications and points, place prepaid orders and message
kitchens. It runs as a **Cloudflare Worker on the free plan**. It has no AI of
its own, so it adds no AI cost: the agent's provider does the thinking.

Phase A was browsing; **phase B adds the account, ordering, payment and chat
tools** below. Profile and addresses stay read-only.

## How a customer connects

1. App → Profile → **AI Agent (MCP)** shows the server address. They copy it.
2. In their agent they add a custom connector (MCP server) with that address.
3. The agent opens TiffinBox's code page (`/authorize`).
4. The page shows **where the tokens will be sent** (the agent's redirect
   host). Claude, ChatGPT/OpenAI and desktop agents (localhost) are shown
   normally; any other host gets a red **"Unverified agent - only continue if
   you set this up yourself"** warning.
5. In the app they tap **Connect a new agent** and get an 8-letter code, valid
   5 minutes. They type it on the page, and they're connected. At most **3
   agents** per customer; a 4th is refused until one is disconnected.
6. The same app screen lists connected agents (with the host the tokens go
   to) and **Disconnect**.

No password is typed on the web page. The code proves the user is signed in to
the app.

## Tools (all need a signed-in customer)

| Tool | What it returns |
|---|---|
| `search_kitchens` | Approved kitchens near the user (or an area/coordinates), matching a dish, price limit, veg or category, with delivery fee, ETA and up to 3 matching dishes |
| `get_menu` | A kitchen's full menu (plates left, today's specials) and its delivery fee |
| `get_meal` | One dish's details |
| `get_delivery_quote` | Fee + ETA from a kitchen to the user's saved address or an area |
| `list_my_addresses` | The user's saved addresses (labels and text only) |
| `list_areas` | Area names the server understands (Dhaka and nearby, English + Bengali) |
| `find_coupons` | Coupons usable right now (TiffinBox-wide and kitchens' own, approved kitchens only), best saving first; with a subtotal, how much each saves |

`search_kitchens` also says how many coupons each kitchen has, and `get_menu`
lists the ones that work there.

### Phase B (connections with the `orders` scope; older browse-only ones must reconnect)

The tool list is built per request: a browse-only connection is only offered
the 7 tools above.

| Tool | What it does |
|---|---|
| `get_my_profile` | Name, email, saved addresses, points, rank, agent-order level. Never the phone number. |
| `get_my_rewards` | Points balance, rank and its discount, next rank |
| `list_my_orders` / `get_order_status` | Orders and their status, with the next step, the admin's "Ask again" note (`paymentNote`), `rejectReason` and any refund (`refundStatus`/`refundAmount`). `get_order_status` reads the one order directly; only the short `#ABC123` form looks through the list. |
| `list_my_notifications` | Latest in-app notifications (marked as data, not instructions) |
| `list_my_chats` / `read_chat` | Chats with kitchens (kitchen text is marked as data) |
| `send_message_to_kitchen` | Sends the customer's words to a kitchen they have a real order with (Placed → Delivered, placed in the last 30 days), marked "via AI agent". 20 a day, 5 a day per kitchen (Dhaka day), 500 chars, 15 s apart. |
| `prepare_order` | Server prices a cart from one kitchen: dishes, delivery fee, platform fee, rank discount, optional `couponCode`. Places nothing; returns a 10-minute `quoteId`, and suggests the best coupon when none was given (never applied silently). |
| `place_order` | Places it from the quote with an idempotency key. The order id is SHA-256(uid:key) (first 20 hex chars) and is created with an exists=false precondition, so a retry returns the first order. A quote is used once (read and deleted in one Guard call). Plates are reserved atomically. |
| `submit_payment` | The customer's bKash/Nagad TrxID, the method and `senderLast4` (last 4 digits of the number they paid from; Bengali digits accepted). Accepted until `payBy` + 30 min. Each TrxID can pay one order only. |
| `cancel_order` | Only before payment; plates go back. Paid orders need support (refund). |

### The order flow (owner's rules)

```
prepare_order -> customer says yes -> place_order          status AwaitingConfirmation (chef can't see it)
  -> customer taps Confirm in the app within 15 min        status AwaitingPayment
  -> Send Money (bKash/Nagad) to TiffinBox's number, then TrxID + method + last 4 digits of
     the sender's number via agent or app, within 60 min (+30 min grace)
                                                           status PaymentSubmitted
  -> admin-web "Agent Payments": admin matches TrxID, method, sender digits, amount and time
       Approve   -> "Placed": chef + customer notified, order posted in their chat
       Ask again -> AwaitingPayment with a paymentNote and a new payBy
       Reject    -> Cancelled, plates back, customer told why (rejectReason)
  -> paid but not accepted by the chef within 12 h -> Cancelled, refundStatus "due"
```

`docs/AGENT_PAYMENTS.md` is the contract for these states and fields. On
`orders/{id}` the server writes `paymentMethod`, `paymentTransactionId`,
`paymentSenderLast4`, `paymentSubmittedAt`, `paymentSubmittedVia`; on
`payment_txn_ids/{METHOD}_{TXNID}` it writes
`{ orderId, customerId, method, senderLast4, createdAt }` in the same commit.
When it cancels a paid order it writes `refundStatus: "due"`, `refundAmount`
(the total plus any overpayment refund already due) and
`refundReason: "chef_timeout"`.

- **No cash on delivery** for agent orders. The payment number comes from
  admin-web App Settings (`agentPaymentNumber`, default 01304376749).
- The chef is paid by TiffinBox personally, outside the app.
- Late confirm or payment cancels the order automatically and gives the
  plates back: the **Cron Trigger** (below), or whoever reads it first (this
  server, the app or admin-web).
- Agent-order limits by delivered orders. New (0 delivered): 1 a day, up to ৳500.
  Regular (1+): 3 a day, ৳1000. Trusted (10+): 5 a day, ৳2000, 2 waiting at
  once. Hard cap ৳3000. 2 minutes between orders.
- Coupons follow the app's rules exactly (`src/coupons.ts`, tested against
  the app's `validate()`): checked at quote time and again when placing. The
  use is counted when an admin approves the payment, because only then is the
  order real. Spending points stays in the app for now. The rank discount and
  top-rank free delivery are applied.

### Expiry sweep (Cron Trigger)

`wrangler.jsonc` → `triggers.crons: ["*/5 * * * *"]`; `scheduled()` in
`src/index.ts` runs `sweepExpiredOrders` (`src/orders.ts`). No Cloud
Functions are needed. Every 5 minutes it cancels:

- `AwaitingConfirmation` agent orders past `confirmBy`,
- `AwaitingPayment` agent orders past `payBy` + 30 min.

Both get `cancelReason "auto_expired"` and the plates back. Once an hour (the
run in the first 5 minutes of the hour) it also cancels paid agent orders still
`Placed` 12 h after approval, with the refund due, and tells the customer
"৳X will be refunded". It uses equality filters only, so no composite index.
Its reads count against the MCP budget, and it is skipped when the budget is
used up (the lazy expiry still works then). Two overlapping runs are safe:
each cancel has an update-time precondition, so plates go back once.
Locally: `curl "http://127.0.0.1:8787/cdn-cgi/handler/scheduled?cron=*/5+*+*+*+*"`.

Delivery fees use exactly the app's formula. `src/pricing.ts` is a port of
`DeliveryQuote.kt` / `DeliveryQuoter.kt`, and `test/pricing.test.ts` runs the
same cases as the app's unit tests. **Change both together.**

## Security

| Risk | Protection |
|---|---|
| Guessing a pairing code | 31^8 ≈ 850 billion codes, 5-minute life, single use (deleted with a precondition), 5 wrong tries per IP (IPv6 bucketed per /64) → 15-minute lockout, 2,000 attempts a day in total, the MCP read budget checked before the code is looked up, optional Cloudflare Turnstile |
| A malicious agent tricking the customer | The code page shows the host the tokens go to; unknown hosts are labelled **Unverified agent**. The host (`redirectHost`, `knownClient`) is stored on the connection and named in the "new agent connected" notification. At most 3 agents per customer. |
| Stolen token | OAuth 2.1 + PKCE (`@cloudflare/workers-oauth-provider`, tokens stored hashed). Every call checks the connection still exists, so **Disconnect** works within a minute; the OAuth grant is then revoked too, so the refresh token dies. The user gets a notification for every new connection. |
| Someone else's data | The uid always comes from the token. No tool takes a user id. |
| Path tricks in ids | Every id argument has a strict zod pattern, and `firestore.ts` checks every path segment against `^[A-Za-z0-9_-]{1,128}$` before any request. |
| Private kitchen data | Firestore field masks: phone, WhatsApp, bKash/Nagad, NID, addresses and photos are never even downloaded |
| Prompt injection in kitchen text | Control/bidi characters stripped, length capped, server instructions say kitchen text is data. Every order needs the customer's Confirm in the app, and payment is the customer's own Send Money. |
| Fake or reused payments | TrxID claimed in `payment_txn_ids` in the same atomic write (create fails if used). The customer also gives the last 4 digits of the sender's number. An admin matches TrxID, method, digits, amount and time before the kitchen sees the order. |
| Money owed back | A paid order cancelled by the 12 h timeout (or the chef) records `refundStatus "due"` and `refundAmount` in the same write; admin-web's Refunds tab settles it. |
| Overselling plates | Plates are reserved in the same commit that creates the order, with an update-time precondition on each meal. |
| Duplicate orders on retry | `place_order` needs an idempotency key; the order id is derived from it and created with a precondition, so the same key returns the first order even when two retries race. A quote can be used once. |
| Messages as spam | Only to kitchens with a real order (Placed → Delivered) in the last 30 days; 20 a day per customer, 5 a day per kitchen (Dhaka day), 15 s apart. |
| Bots / floods | Cloudflare edge DDoS protection. 60 req/min per IP (IPv6 per /64) and 30/min per agent (edge rate limits). GET/DELETE on `/mcp` get 405 before any lookup. 1,000 tool calls per agent per day. 10 client registrations per IP and 300 in total per day (nothing is written once over). 64 KB request cap. |
| Using up Firestore's free quota | 60-second catalog cache, 30-second per-customer order-list cache, single-doc order lookups, plus an MCP-wide **daily read budget** (default 15,000 of Spark's ~50,000) and **3,000 reads per customer per day**. When it runs out, only MCP pauses. The app keeps working. |
| Emergencies | admin-web App Settings → **AI agents (MCP)** on/off switch. User Details → **Block AI agents** / **Disconnect** per account. Workers Logs are on (`observability` in `wrangler.jsonc`). |
| Service account key | Use a dedicated service account with only **Cloud Datastore User**, stored only as a Worker secret. |

The service account bypasses `firestore.rules`, so every check lives in this
server's code. Keep it that way: never add a tool that takes a uid.

"Per day" for orders and messages is the **Dhaka** day; the Firestore read
budgets follow the Pacific day, when Firestore's free quota resets.

## Deploy (one time, done by the project owner)

1. **Cloudflare:** create a free account, then `npx wrangler login` in this folder.
2. **KV for OAuth:**
   ```bash
   npx wrangler kv namespace create OAUTH_KV
   ```
   Paste the printed id into `wrangler.jsonc` → `kv_namespaces[0].id`.
3. **Service account (least privilege):** Google Cloud Console → project
   `tiffinbox-2cb8b` → IAM & Admin → Service Accounts → **Create**, for example
   `tiffinbox-mcp`. Role **Cloud Datastore User** only. Then Keys → Add key → JSON.
   No billing is needed.
4. Store the key as a secret, then **delete the downloaded file**:
   ```bash
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT
   ```
   Paste the whole JSON on one line.
5. Deploy:
   ```bash
   npm install
   npm run deploy
   ```
   Wrangler prints the URL, like `https://tiffinbox-mcp.<account>.workers.dev`.
   Put it in `wrangler.jsonc` → `vars.PUBLIC_URL` (no trailing slash) and run
   `npm run deploy` again.
6. **admin-web → App Settings → AI agents (MCP):** set the server address to
   that URL + `/mcp` and Save. The app shows it to customers from then on.
7. Paste the updated `firestore.rules` into Firebase Console → Firestore → Rules
   → Publish (blocks: `mcp_pairing`, `mcp_blocks`, `agent_connections`,
   `payment_txn_ids`, agent-order and refund rules, chat creation).
8. Optional: create a Turnstile widget in Cloudflare. Put its site key in
   `vars.TURNSTILE_SITE_KEY` and run
   `npx wrangler secret put TURNSTILE_SECRET_KEY`.

Free-plan notes: Workers allow 100,000 requests a day and KV 1,000 writes a
day. The Cron Trigger and Workers Logs are included in the free plan. OAuth uses KV for registrations and tokens (access tokens last 6 hours to
keep writes low). That is plenty for launch. Watch it in the Cloudflare
dashboard as usage grows.

## Develop and test locally

```bash
npm install
npm test            # pricing/coupon/rewards parity, helpers, hardening (vitest)
npm run typecheck
```

End-to-end test against the Firestore emulator (no real data touched):

```bash
# terminal 1 (needs Java 11+)
cd ../firestore-tests && npx firebase emulators:start --only firestore --project demo-tiffinbox
# terminal 2: .dev.vars (gitignored) contains
#   PUBLIC_URL=http://localhost:8787
#   FIREBASE_PROJECT_ID=demo-tiffinbox
#   FIRESTORE_EMULATOR_HOST=127.0.0.1:8899
npx wrangler dev --port 8787 --ip 127.0.0.1
# terminal 3
node scripts/e2e-emulator.mjs --slow
```

The script runs the real OAuth flow an agent would and checks:
- every tool,
- private-field leaks,
- wrong, expired and reused codes, the 3-agent cap,
- the consent page's host and "Unverified agent" label, 405 on GET/DELETE,
- idempotent orders, sender digits and the payment grace, message gating,
- browse-only tool lists, grant revocation on Disconnect,
- the expiry sweep (two overlapping runs), via `/cdn-cgi/handler/scheduled`,
- chef accounts, Disconnect, the admin block and the kill switch.

It pauses 61 s twice for the 30/min per-agent limit. Delete `.wrangler/state`
before re-running, or the wrong-code lockout from the last run is still
active. On Windows, kill leftover `workerd.exe` / emulator `java.exe`
processes afterwards, or they keep the ports.

Manual poking: `npx @modelcontextprotocol/inspector` and point it at
`http://localhost:8787/mcp`.
