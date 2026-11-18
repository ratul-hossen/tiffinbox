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
4. In the app they tap **Connect a new agent** and get an 8-letter code, valid
   5 minutes. They type it on the page, and they're connected.
5. The same app screen lists connected agents with **Disconnect**.

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

### Phase B (connections made after phase B; older ones must reconnect)

| Tool | What it does |
|---|---|
| `get_my_profile` | Name, email, saved addresses, points, rank, agent-order level. Never the phone number. |
| `get_my_rewards` | Points balance, rank and its discount, next rank |
| `list_my_orders` / `get_order_status` | Orders and their status, with the next step. Full id or `#ABC123`. |
| `list_my_notifications` | Latest in-app notifications |
| `list_my_chats` / `read_chat` | Chats with kitchens (kitchen text is marked as data) |
| `send_message_to_kitchen` | Sends the customer's words to a kitchen they ordered from, marked "via AI agent". 20 a day, 500 chars, 15 s apart. |
| `prepare_order` | Server prices a cart from one kitchen: dishes, delivery fee, platform fee, rank discount. Places nothing; returns a 10-minute `quoteId`. |
| `place_order` | Places it from the quote with an idempotency key. Plates are reserved atomically. |
| `submit_payment` | The customer's bKash/Nagad TrxID. Each TrxID can pay one order only. |
| `cancel_order` | Only before payment; plates go back. Paid orders need support (refund). |

### The order flow (owner's rules)

```
prepare_order -> customer says yes -> place_order          status AwaitingConfirmation (chef can't see it)
  -> customer taps Confirm in the app within 15 min        status AwaitingPayment
  -> Send Money (bKash/Nagad) to TiffinBox's number, TrxID via agent or app, within 60 min
                                                           status PaymentSubmitted
  -> admin-web "Agent Payments": admin checks the money arrived
       Approve -> "Placed": chef + customer notified, order posted in their chat
       Reject  -> Cancelled, plates back, customer told why
```

- **No cash on delivery** for agent orders. The payment number comes from
  admin-web App Settings (`agentPaymentNumber`, default 01304376749).
- The chef is paid by TiffinBox personally, outside the app.
- Late confirm or payment cancels the order automatically (whoever reads it
  first: this server, the app or admin-web) and gives the plates back.
- Agent-order limits by delivered orders. New (0 delivered): 1 a day, up to ৳500.
  Regular (1+): 3 a day, ৳1000. Trusted (10+): 5 a day, ৳2000, 2 waiting at
  once. Hard cap ৳3000. 2 minutes between orders.
- Coupons and spending points stay in the app for now; the rank discount and
  top-rank free delivery are applied.

Delivery fees use exactly the app's formula. `src/pricing.ts` is a port of
`DeliveryQuote.kt` / `DeliveryQuoter.kt`, and `test/pricing.test.ts` runs the
same cases as the app's unit tests. **Change both together.**

## Security

| Risk | Protection |
|---|---|
| Guessing a pairing code | 31^8 ≈ 850 billion codes, 5-minute life, single use (deleted with a precondition), 5 wrong tries per IP → 15-minute lockout, optional Cloudflare Turnstile |
| Stolen token | OAuth 2.1 + PKCE (`@cloudflare/workers-oauth-provider`, tokens stored hashed). Every call checks the connection still exists, so **Disconnect** works within a minute. The user gets a notification for every new connection. |
| Someone else's data | The uid always comes from the token. No tool takes a user id. |
| Private kitchen data | Firestore field masks: phone, WhatsApp, bKash/Nagad, NID, addresses and photos are never even downloaded |
| Prompt injection in kitchen text | Control/bidi characters stripped, length capped, server instructions say kitchen text is data. Every order needs the customer's Confirm in the app, and payment is the customer's own Send Money. |
| Fake or reused payments | TrxID claimed in `payment_txn_ids` in the same atomic write (create fails if used). An admin checks every payment before the kitchen sees the order. |
| Overselling plates | Plates are reserved in the same commit that creates the order, with an update-time precondition on each meal. |
| Duplicate orders on retry | `place_order` needs an idempotency key; the same key returns the first order. |
| Bots / floods | Cloudflare edge DDoS protection. 60 req/min per IP and 30/min per agent (edge rate limits). 1,000 tool calls per agent per day. 10 client registrations per IP per day. 64 KB request cap. |
| Using up Firestore's free quota | 60-second catalog cache, plus an MCP-wide **daily read budget** (default 15,000 of Spark's ~50,000). When it runs out, only MCP pauses. The app keeps working. |
| Emergencies | admin-web App Settings → **AI agents (MCP)** on/off switch. User Details → **Block AI agents** / **Disconnect** per account. |
| Service account key | Use a dedicated service account with only **Cloud Datastore User**, stored only as a Worker secret. |

The service account bypasses `firestore.rules`, so every check lives in this
server's code. Keep it that way: never add a tool that takes a uid.

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
   → Publish (new blocks: `mcp_pairing`, `mcp_blocks`, `agent_connections`).
8. Optional: create a Turnstile widget in Cloudflare. Put its site key in
   `vars.TURNSTILE_SITE_KEY` and run
   `npx wrangler secret put TURNSTILE_SECRET_KEY`.

Free-plan notes: Workers allow 100,000 requests a day and KV 1,000 writes a
day. OAuth uses KV for registrations and tokens (access tokens last 6 hours to
keep writes low). That is plenty for launch. Watch it in the Cloudflare
dashboard as usage grows.

## Develop and test locally

```bash
npm install
npm test            # pricing parity + helpers (vitest)
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
- wrong, expired and reused codes,
- chef accounts, Disconnect, the admin block and the kill switch.

Delete `.wrangler/state` before re-running, or the wrong-code lockout from the
last run is still active.

Manual poking: `npx @modelcontextprotocol/inspector` and point it at
`http://localhost:8787/mcp`.
