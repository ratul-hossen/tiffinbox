/**
 * The "connect your agent" page. No password is ever typed here: the user
 * proves who they are with a one-time code from the app they're already
 * signed in to (Profile → AI Agent (MCP) → Connect a new agent), which the
 * app writes to `mcp_pairing/{code}` = { uid, createdAt }.
 *
 * Protections: 8-character codes from a 31-letter alphabet (~850 billion
 * combinations), valid 5 minutes, single use (deleted with an updateTime
 * precondition, so two racing requests can't both win), 5 wrong codes per IP
 * (IPv6: per /64) → 15-minute lockout, a global daily cap on attempts, the
 * MCP read budget checked before the code is looked up, optional Cloudflare
 * Turnstile, customers only, at most 3 agents per customer.
 *
 * The page shows where the tokens will be sent (the OAuth redirect host).
 * Hosts we don't recognise are labelled "Unverified agent"; the host is
 * stored on the connection and named in the "new agent" notification.
 */
import type { AuthRequest } from "@cloudflare/workers-oauth-provider";
import type { Env } from "./env";
import { cleanText, loadConfig, loadUser } from "./catalog";
import { Firestore } from "./firestore";
import type { Guard } from "./guard";
import { LIMITS } from "./limits";
import { ipKey, isKnownRedirectHost, isLoopbackHost } from "./util";

export const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
/**
 * What a new connection may do. "menu:read" alone is what phase A connections
 * got; phase B tools (account, orders, chat) check the connection's own
 * stored scopes, so an old browsing-only connection must be reconnected.
 */
export const SCOPES = ["menu:read", "account", "orders", "chat"];
/** Advertised to MCP clients as the minimum for basic use (unchanged, so old tokens keep working). */
export const REQUIRED_SCOPES = ["menu:read"];

export function normalizeCode(raw: string): string | null {
  const code = raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (code.length !== 8) return null;
  return [...code].every((c) => CODE_ALPHABET.includes(c)) ? code : null;
}

const escapeHtml = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

const SECURITY_HEADERS = {
  "content-type": "text/html; charset=utf-8",
  "x-frame-options": "DENY",
  "referrer-policy": "no-referrer",
  "x-content-type-options": "nosniff",
  "content-security-policy":
    "default-src 'none'; style-src 'unsafe-inline'; script-src https://challenges.cloudflare.com; " +
    "frame-src https://challenges.cloudflare.com; form-action 'self' https: http://localhost:* http://127.0.0.1:*; base-uri 'none'",
};

function page(title: string, body: string, status = 200): Response {
  return new Response(
    `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(title)}</title>
<style>
:root{--bg:#f6f6f7;--card:#fff;--text:#111;--muted:#666;--accent:#d32f2f;--border:#e2e2e5}
@media (prefers-color-scheme:dark){:root{--bg:#111;--card:#1c1c1e;--text:#f2f2f2;--muted:#a0a0a5;--border:#333}}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans Bengali",sans-serif}
main{max-width:440px;margin:40px auto;padding:0 16px}
.card{background:var(--card);border:1px solid var(--border);border-radius:16px;padding:24px}
h1{font-size:22px;margin:0 0 8px}p{color:var(--muted);margin:8px 0}
ol{color:var(--muted);padding-left:20px}
input{width:100%;box-sizing:border-box;font:600 24px ui-monospace,monospace;letter-spacing:4px;text-align:center;padding:12px;border:1px solid var(--border);border-radius:12px;background:var(--bg);color:var(--text);text-transform:uppercase}
button{width:100%;margin-top:16px;padding:14px;border:0;border-radius:12px;background:var(--accent);color:#fff;font:600 16px system-ui}
.err{color:var(--accent);font-weight:600}
.dest{border:1px solid var(--border);border-radius:12px;padding:12px 14px;margin:12px 0}
.dest .label{font-size:13px;color:var(--muted)}
.dest .host{font:600 18px ui-monospace,monospace;word-break:break-all;color:var(--text)}
.dest.warn{border:2px solid var(--accent);background:color-mix(in srgb,var(--accent) 8%,transparent)}
.dest.warn .note{color:var(--accent);font-weight:600;margin:6px 0 0}
.brand{font-weight:700;color:var(--accent);margin-bottom:12px}
</style></head><body><main><div class="brand">TiffinBox</div><div class="card">${body}</div></main></body></html>`,
    { status, headers: SECURITY_HEADERS },
  );
}

/** Where the OAuth tokens go: the redirect URI's host, and whether it's an agent we recognise. */
export interface RedirectTarget {
  host: string;
  known: boolean;
  loopback: boolean;
}

/** Reads the redirect target via the provider's describeConsent(), falling back to the parsed redirectUri. */
async function redirectTarget(env: Env, req: AuthRequest): Promise<RedirectTarget> {
  let host = "";
  try {
    host = (await env.OAUTH_PROVIDER.describeConsent(req)).redirectHost;
  } catch {
    try {
      host = new URL(req.redirectUri).hostname || req.redirectUri;
    } catch {
      host = req.redirectUri;
    }
  }
  host = cleanText(host, 120) || "unknown";
  return { host, known: isKnownRedirectHost(host), loopback: isLoopbackHost(host) };
}

function destination(target: RedirectTarget): string {
  const where = target.loopback ? "<p class=\"note\" style=\"color:var(--muted);font-weight:400\">An app on this computer (a desktop agent).</p>" : "";
  return target.known
    ? `<div class="dest"><div class="label">Tokens will be sent to:</div><div class="host">${escapeHtml(target.host)}</div>${where}</div>`
    : `<div class="dest warn"><div class="label">Tokens will be sent to:</div><div class="host">${escapeHtml(target.host)}</div>
<p class="note">Unverified agent - only continue if you set this up yourself.</p>
<p class="note">যাচাই করা হয়নি এমন এজেন্ট - শুধু নিজে সেট আপ করে থাকলে এগিয়ে যান।</p></div>`;
}

function codeForm(env: Env, clientName: string, action: string, target: RedirectTarget, error = ""): Response {
  const turnstile = env.TURNSTILE_SITE_KEY
    ? `<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
<div class="cf-turnstile" data-sitekey="${escapeHtml(env.TURNSTILE_SITE_KEY)}" style="margin-top:12px"></div>`
    : "";
  return page(
    "Connect to TiffinBox",
    `<h1>Connect ${escapeHtml(clientName)}</h1>
<p><b>${escapeHtml(clientName)}</b> wants to use TiffinBox for you. It will be able to:</p>
<ul style="color:var(--muted);padding-left:20px;margin:8px 0">
<li>find kitchens and read menus;</li>
<li>see your name, email, saved addresses, orders, chats, notifications and points;</li>
<li>prepare orders - <b>each one needs your Confirm in the app</b> and advance payment (no cash on delivery);</li>
<li>send messages you ask for to kitchens you've ordered from.</li>
</ul>
<p>It can't change your profile or addresses, and never sees your phone or payment PIN.</p>
${destination(target)}
<ol><li>Open the TiffinBox app.</li><li>Profile → <b>AI Agent (MCP)</b> → <b>Connect a new agent</b>.</li><li>Type the 8-letter code here.</li></ol>
<p>অ্যাপে Profile → AI Agent (MCP) → নতুন এজেন্ট যুক্ত করুন — তারপর কোডটি এখানে লিখুন।</p>
${error ? `<p class="err">${escapeHtml(error)}</p>` : ""}
<form method="post" action="${escapeHtml(action)}">
<input name="code" autocomplete="one-time-code" inputmode="text" maxlength="9" placeholder="XXXX-XXXX" required autofocus>
${turnstile}
<button type="submit">Connect</button></form>
<p style="font-size:13px">Only connect agents you trust. You can disconnect it any time in the app.</p>`,
    error ? 400 : 200,
  );
}

async function turnstileOk(env: Env, form: FormData, ip: string): Promise<boolean> {
  if (!env.TURNSTILE_SITE_KEY || !env.TURNSTILE_SECRET_KEY) return true;
  const res = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
    method: "POST",
    body: new URLSearchParams({
      secret: env.TURNSTILE_SECRET_KEY,
      response: String(form.get("cf-turnstile-response") ?? ""),
      remoteip: ip,
    }),
  });
  return res.ok && ((await res.json()) as { success: boolean }).success;
}

function randomId(length = 20): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  const abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  return [...bytes].map((b) => abc[b % abc.length]).join("");
}

function guard(env: Env): DurableObjectStub<Guard> {
  return env.GUARD.get(env.GUARD.idFromName("guard")) as DurableObjectStub<Guard>;
}
export { guard as guardStub };

export async function handleAuthorize(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  let oauthReq: AuthRequest;
  try {
    oauthReq = await env.OAUTH_PROVIDER.parseAuthRequest(request);
  } catch {
    return page("Invalid request", "<h1>Invalid request</h1><p>Start the connection again from your AI agent.</p>", 400);
  }
  const client = await env.OAUTH_PROVIDER.lookupClient(oauthReq.clientId);
  if (!client) return page("Unknown agent", "<h1>Unknown agent</h1><p>Start the connection again from your AI agent.</p>", 400);
  const clientName = cleanText(client.clientName, 40) || "An AI agent";
  const target = await redirectTarget(env, oauthReq);
  // The form posts back to this exact URL, so the OAuth parameters are
  // re-parsed and re-validated on submit instead of trusted from a hidden field.
  const action = new URL(request.url).pathname + new URL(request.url).search;

  if (request.method === "GET") return codeForm(env, clientName, action, target);
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const db = new Firestore(env);
  const g = guard(env);
  try {
    return await submitCode(request, env, ctx, db, g, oauthReq, clientName, action, target);
  } finally {
    // Every Firestore read on this page counts against the MCP budget too.
    if (db.reads > 0) ctx.waitUntil(g.addReads(db.reads).catch(() => {}));
  }
}

async function submitCode(
  request: Request,
  env: Env,
  ctx: ExecutionContext,
  db: Firestore,
  g: DurableObjectStub<Guard>,
  oauthReq: AuthRequest,
  clientName: string,
  action: string,
  target: RedirectTarget,
): Promise<Response> {
  const form_ = (error: string) => codeForm(env, clientName, action, target, error);
  const ip = request.headers.get("cf-connecting-ip") ?? "local";
  const ipBucket = ipKey(ip);
  // Before anything is read from Firestore: lockout, global attempt cap, read budget.
  const config = await loadConfig(db);
  const allowed = await g.pairingAllowed(ipBucket, config.mcpDailyReadBudget);
  if (!allowed.ok) {
    return form_(
      allowed.reason === "locked"
        ? `Too many wrong codes. Try again in ${Math.ceil(allowed.retryAfterSec / 60)} minutes.`
        : "TiffinBox can't connect new agents right now. Please try again later.",
    );
  }
  const form = await request.formData();
  if (!(await turnstileOk(env, form, ip))) return form_("Please complete the check and try again.");

  const code = normalizeCode(String(form.get("code") ?? ""));
  const wrong = async (msg = "That code is wrong or has expired. Make a new one in the app.") => {
    await g.pairingFailed(ipBucket);
    return form_(msg);
  };
  if (!code) return wrong();

  const pairing = await db.get(`mcp_pairing/${code}`);
  if (!pairing) return wrong();
  const uid = typeof pairing.data.uid === "string" ? pairing.data.uid : "";
  const createdAt = typeof pairing.data.createdAt === "number" ? pairing.data.createdAt : 0;
  // Single use: delete it now, and only if nobody else consumed it first.
  const consumed = await db.delete(`mcp_pairing/${code}`, pairing.updateTime);
  if (!consumed || !/^[A-Za-z0-9_-]{1,128}$/.test(uid) || Date.now() - createdAt > LIMITS.pairingCodeTtlMs) return wrong();

  const user = await loadUser(db, uid);
  if (!user.exists || user.role !== "customer") return form_("AI agents are available for customer accounts only.");
  if (user.mcpBlocked) return form_("AI agent access is turned off for this account.");
  await g.pairingSucceeded(ipBucket);

  // At most 3 agents at once (reading 4 is enough to know).
  const existing = await db.query("agent_connections", {
    parentPath: `users/${uid}`,
    select: ["createdAt"],
    limit: LIMITS.maxConnectionsPerCustomer + 1,
  });
  if (existing.length >= LIMITS.maxConnectionsPerCustomer) {
    return form_(
      `This account already has ${LIMITS.maxConnectionsPerCustomer} AI agents connected. Disconnect one first in the app ` +
        `(Profile → AI Agent (MCP)), then make a new code.`,
    );
  }

  const connectionId = randomId();
  const now = Date.now();
  await db.create(`users/${uid}/agent_connections`, connectionId, {
    clientName,
    clientId: oauthReq.clientId,
    scopes: SCOPES,
    redirectHost: target.host,
    knownClient: target.known,
    createdAt: now,
    lastUsedAt: now,
  });
  // Tell the user on their phone, so a connection they didn't make is noticed.
  ctx.waitUntil(
    db
      .create(`notifications/${uid}/items`, randomId(), {
        title: "নতুন AI এজেন্ট যুক্ত হয়েছে · New AI agent connected",
        body:
          `${clientName} (tokens go to ${target.host}${target.known ? "" : ", unverified agent"}) can now use TiffinBox for you. ` +
          "Not you? Disconnect it in Profile → AI Agent (MCP).",
        createdAt: now,
      })
      .catch((e) => console.error("notification failed", e)),
  );

  const { redirectTo } = await env.OAUTH_PROVIDER.completeAuthorization({
    request: oauthReq,
    userId: uid,
    metadata: { clientName, connectionId },
    scope: SCOPES,
    props: { uid, connectionId, clientName },
  });
  return Response.redirect(redirectTo, 302);
}

export function landingPage(): Response {
  return page(
    "TiffinBox MCP",
    `<h1>TiffinBox for AI agents</h1>
<p>This is TiffinBox's MCP server. Add <b>this address + /mcp</b> as a connector in your AI agent (Claude, ChatGPT and others), then sign in with a code from the TiffinBox app: Profile → <b>AI Agent (MCP)</b>.</p>`,
  );
}
