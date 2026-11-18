/**
 * The "connect your agent" page. No password is ever typed here: the user
 * proves who they are with a one-time code from the app they're already
 * signed in to (Profile → AI Agent (MCP) → Connect a new agent), which the
 * app writes to `mcp_pairing/{code}` = { uid, createdAt }.
 *
 * Protections: 8-character codes from a 31-letter alphabet (~850 billion
 * combinations), valid 5 minutes, single use (deleted with an updateTime
 * precondition, so two racing requests can't both win), 5 wrong codes per IP
 * → 15-minute lockout, optional Cloudflare Turnstile, customers only.
 */
import type { AuthRequest } from "@cloudflare/workers-oauth-provider";
import type { Env } from "./env";
import { cleanText, loadUser } from "./catalog";
import { Firestore } from "./firestore";
import type { Guard } from "./guard";
import { LIMITS } from "./limits";

export const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export const SCOPES = ["menu:read"];

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
.brand{font-weight:700;color:var(--accent);margin-bottom:12px}
</style></head><body><main><div class="brand">TiffinBox</div><div class="card">${body}</div></main></body></html>`,
    { status, headers: SECURITY_HEADERS },
  );
}

function codeForm(env: Env, clientName: string, action: string, error = ""): Response {
  const turnstile = env.TURNSTILE_SITE_KEY
    ? `<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
<div class="cf-turnstile" data-sitekey="${escapeHtml(env.TURNSTILE_SITE_KEY)}" style="margin-top:12px"></div>`
    : "";
  return page(
    "Connect to TiffinBox",
    `<h1>Connect ${escapeHtml(clientName)}</h1>
<p><b>${escapeHtml(clientName)}</b> wants to browse TiffinBox kitchens and menus for you.</p>
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
  // The form posts back to this exact URL, so the OAuth parameters are
  // re-parsed and re-validated on submit instead of trusted from a hidden field.
  const action = new URL(request.url).pathname + new URL(request.url).search;

  if (request.method === "GET") return codeForm(env, clientName, action);
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const ip = request.headers.get("cf-connecting-ip") ?? "local";
  const g = guard(env);
  const allowed = await g.pairingAllowed(ip);
  if (!allowed.ok) {
    return codeForm(env, clientName, action, `Too many wrong codes. Try again in ${Math.ceil(allowed.retryAfterSec / 60)} minutes.`);
  }
  const form = await request.formData();
  if (!(await turnstileOk(env, form, ip))) return codeForm(env, clientName, action, "Please complete the check and try again.");

  const code = normalizeCode(String(form.get("code") ?? ""));
  const db = new Firestore(env);
  const wrong = async (msg = "That code is wrong or has expired. Make a new one in the app.") => {
    await g.pairingFailed(ip);
    return codeForm(env, clientName, action, msg);
  };
  if (!code) return wrong();

  const pairing = await db.get(`mcp_pairing/${code}`);
  ctx.waitUntil(g.addReads(db.reads));
  if (!pairing) return wrong();
  const uid = typeof pairing.data.uid === "string" ? pairing.data.uid : "";
  const createdAt = typeof pairing.data.createdAt === "number" ? pairing.data.createdAt : 0;
  // Single use: delete it now, and only if nobody else consumed it first.
  const consumed = await db.delete(`mcp_pairing/${code}`, pairing.updateTime);
  if (!consumed || !uid || uid.includes(":") || Date.now() - createdAt > LIMITS.pairingCodeTtlMs) return wrong();

  const user = await loadUser(db, uid);
  if (!user.exists || user.role !== "customer") {
    return codeForm(env, clientName, action, "AI agents are available for customer accounts only.");
  }
  if (user.mcpBlocked) return codeForm(env, clientName, action, "AI agent access is turned off for this account.");
  await g.pairingSucceeded(ip);

  const connectionId = randomId();
  const now = Date.now();
  await db.create(`users/${uid}/agent_connections`, connectionId, {
    clientName,
    clientId: oauthReq.clientId,
    scopes: SCOPES,
    createdAt: now,
    lastUsedAt: now,
  });
  // Tell the user on their phone, so a connection they didn't make is noticed.
  ctx.waitUntil(
    db
      .create(`notifications/${uid}/items`, randomId(), {
        title: "নতুন AI এজেন্ট যুক্ত হয়েছে · New AI agent connected",
        body: `${clientName} can now browse TiffinBox for you. Not you? Disconnect it in Profile → AI Agent (MCP).`,
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
