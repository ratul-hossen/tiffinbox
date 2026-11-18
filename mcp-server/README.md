# TiffinBox MCP server

Lets a customer's own AI agent (Claude, ChatGPT, or any MCP client) browse
TiffinBox for them: find kitchens, read menus, check delivery fees. It runs as
a **Cloudflare Worker on the free plan**. It has no AI of its own, so it adds no
AI cost: the agent's provider does the thinking.

**Phase A (this folder today): browsing only.** Ordering (phase B) comes later.

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
| Prompt injection in kitchen text | Control/bidi characters stripped, length capped, server instructions say kitchen text is data. Ordering will need confirmation in the app (phase B). |
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
