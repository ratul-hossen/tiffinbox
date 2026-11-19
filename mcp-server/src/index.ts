/**
 * TiffinBox MCP server (Cloudflare Worker), v0.3.0.
 *
 *   /mcp         MCP (Streamable HTTP, stateless, POST only) - needs an OAuth access token
 *   /authorize   the code page (auth.ts)
 *   /token, /register, /.well-known/*  handled by @cloudflare/workers-oauth-provider
 *   scheduled    Cron Trigger every 5 minutes: expires late agent orders (orders.ts)
 *
 * Layers of protection, outermost first:
 *   1. Cloudflare edge DDoS protection (automatic)
 *   2. per-IP limit on every request (IPv6 per /64), body size cap,
 *      GET/DELETE on /mcp refused before any token or Firestore work
 *   3. no valid token -> rejected before any Firestore read
 *   4. per-connection limit per minute (edge) and per day (Guard)
 *   5. connection must still exist (Disconnect in the app - its OAuth grant
 *      is then revoked too), user must be a non-blocked customer, MCP must be
 *      switched on in App Settings
 *   6. MCP-wide daily Firestore read budget and a per-customer share, so
 *      agent traffic can never use up the free quota the app itself needs
 */
import OAuthProvider from "@cloudflare/workers-oauth-provider";
import { WebStandardStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js";
import { REQUIRED_SCOPES, SCOPES, guardStub, handleAuthorize, landingPage } from "./auth";
import { loadConfig, loadUser } from "./catalog";
import type { AgentProps, Env } from "./env";
import { Firestore } from "./firestore";
import { LIMITS } from "./limits";
import { sweepExpiredOrders } from "./orders";
import { buildServer } from "./tools";
import { ipKey } from "./util";

export { Guard } from "./guard";

const jsonError = (status: number, message: string, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "content-type": "application/json", ...extra },
  });

// "This connection still exists" is trusted for a minute per isolate, and
// lastUsedAt is written at most every 10 minutes, to keep Firestore use low.
const connectionSeen = new Map<string, { scopes: string[] | null; at: number }>();
const lastUsedWritten = new Map<string, number>();
/** Set once this isolate has made sure the Guard's clean-up alarm exists. */
let cleanupScheduled = false;

/**
 * The scopes the user granted on this connection, or null when it was
 * disconnected. [onGone] runs once per fresh "it's gone" lookup.
 */
async function connectionScopes(db: Firestore, props: AgentProps, onGone: () => void): Promise<string[] | null> {
  const key = `${props.uid}/${props.connectionId}`;
  const hit = connectionSeen.get(key);
  if (hit && Date.now() - hit.at < LIMITS.connectionCheckMs) return hit.scopes;
  const doc = await db.get(`users/${props.uid}/agent_connections/${props.connectionId}`, ["scopes"]);
  const scopes = doc ? (Array.isArray(doc.data.scopes) ? doc.data.scopes.filter((s): s is string => typeof s === "string") : []) : null;
  connectionSeen.set(key, { scopes, at: Date.now() });
  if (scopes === null) onGone();
  return scopes;
}

/**
 * Disconnected in the app or by an admin: revoke the OAuth grant as well, so
 * the refresh token stops working too (best effort - the connection check
 * already refuses every call).
 */
async function revokeGrantOf(env: Env, props: AgentProps): Promise<void> {
  try {
    let cursor: string | undefined;
    do {
      const page = await env.OAUTH_PROVIDER.listUserGrants(props.uid, { limit: 100, cursor });
      for (const grant of page.items) {
        if (grant.metadata?.connectionId === props.connectionId) await env.OAUTH_PROVIDER.revokeGrant(grant.id, props.uid);
      }
      cursor = page.cursor;
    } while (cursor);
  } catch (e) {
    console.error("revoking a disconnected agent's grant failed", e);
  }
}

const mcpApi = {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const props = (ctx as unknown as { props: AgentProps }).props;
    if (!props?.uid || !props.connectionId) return jsonError(401, "invalid_token");

    if (!(await env.TOKEN_LIMITER.limit({ key: props.connectionId })).success) {
      return jsonError(429, "Too many requests from this agent. Slow down.", { "retry-after": "60" });
    }

    const db = new Firestore(env);
    const g = guardStub(env);
    try {
      const config = await loadConfig(db);
      if (!config.mcpEnabled) return jsonError(503, "TiffinBox AI agent access is turned off for now.");

      const scopes = await connectionScopes(db, props, () => ctx.waitUntil(revokeGrantOf(env, props)));
      if (scopes === null) {
        // Disconnected in the app (or by an admin): the agent must connect again.
        return jsonError(401, "invalid_token", { "www-authenticate": 'Bearer error="invalid_token"' });
      }
      const user = await loadUser(db, props.uid);
      if (!user.exists || user.role !== "customer") return jsonError(403, "Customer accounts only.");
      if (user.mcpBlocked) return jsonError(403, "AI agent access is turned off for this account.");

      const now = Date.now();
      if (now - (lastUsedWritten.get(props.connectionId) ?? 0) > LIMITS.lastUsedWriteMs) {
        lastUsedWritten.set(props.connectionId, now);
        ctx.waitUntil(
          db
            .update(`users/${props.uid}/agent_connections/${props.connectionId}`, { lastUsedAt: now })
            .catch(() => {}),
        );
      }

      // Built per request: a browse-only connection only ever sees the browse tools.
      const server = buildServer({
        db,
        props,
        guard: g,
        scopes,
        gate: async () => {
          const verdict = await g.checkCall(props.connectionId, props.uid, config.mcpDailyReadBudget);
          if (verdict.ok) return null;
          if (verdict.reason === "read_budget") {
            return "TiffinBox's AI agent access is busy today. Please use the TiffinBox app, or try again tomorrow.";
          }
          if (verdict.reason === "user_reads") {
            return "This account has used today's TiffinBox allowance for AI agents. Please use the TiffinBox app, or try again tomorrow.";
          }
          return "This agent has reached today's limit for TiffinBox. Try again tomorrow.";
        },
      });
      const transport = new WebStandardStreamableHTTPServerTransport({
        sessionIdGenerator: undefined,
        enableJsonResponse: true,
        maxRequestBodySize: LIMITS.maxRequestBodyBytes,
      });
      await server.connect(transport);
      return await transport.handleRequest(request);
    } catch (e) {
      console.error("mcp error", e);
      return jsonError(500, "Internal error");
    } finally {
      const reads = db.reads;
      ctx.waitUntil(
        (async () => {
          if (reads > 0) await g.addReads(reads, props.uid);
          if (!cleanupScheduled) cleanupScheduled = await g.ensureCleanup();
        })().catch((e) => console.error("guard bookkeeping failed", e)),
      );
    }
  },
};

const site = {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/authorize") return handleAuthorize(request, env, ctx);
    if (url.pathname === "/") return landingPage();
    if (url.pathname === "/health") return new Response("ok");
    return new Response("Not found", { status: 404 });
  },
};

let provider: OAuthProvider<Env> | null = null;
let providerFor = "";

/** Built on first use, because the public URL (the OAuth "resource") comes from env. */
function getProvider(env: Env): OAuthProvider<Env> {
  const base = env.PUBLIC_URL.replace(/\/+$/, "");
  if (provider && providerFor === base) return provider;
  provider = new OAuthProvider<Env>({
    apiRoute: "/mcp",
    apiHandler: mcpApi as never,
    defaultHandler: site as never,
    authorizeEndpoint: "/authorize",
    tokenEndpoint: "/token",
    clientRegistrationEndpoint: "/register",
    scopesSupported: SCOPES,
    resourceMetadata: { resource: `${base}/mcp`, authorization_servers: [base], resource_name: "TiffinBox" },
    requiredScopes: REQUIRED_SCOPES,
    // Revoking is instant anyway (every call checks the connection still
    // exists), so a few hours keeps OAuth's KV writes low on the free plan.
    accessTokenTTL: 6 * 3600,
    refreshTokenTTL: 30 * 24 * 3600,
    // Registrations per IP are limited in the outer fetch below.
  });
  providerFor = base;
  return provider;
}

/** The cron sweep: every 5 minutes for unconfirmed/unpaid orders, hourly for paid ones the chef never accepted. */
async function runSweep(env: Env, scheduledTime: number): Promise<void> {
  const db = new Firestore(env);
  const g = guardStub(env);
  try {
    const config = await loadConfig(db);
    // The sweep's reads count against the same MCP budget; when it is used up the app's lazy expiry still works.
    if (!(await g.readBudgetLeft(config.mcpDailyReadBudget))) return;
    // The 12-hour check needs no 5-minute precision; once an hour saves reads.
    const paid = new Date(scheduledTime).getUTCMinutes() < 5;
    const result = await sweepExpiredOrders(db, { paid });
    if (result.cancelled) console.log("expiry sweep", JSON.stringify(result));
  } catch (e) {
    console.error("expiry sweep failed", e);
  } finally {
    if (db.reads > 0) await g.addReads(db.reads).catch(() => {});
  }
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const ip = request.headers.get("cf-connecting-ip") ?? "local";
    const ipBucket = ipKey(ip);
    if (!(await env.IP_LIMITER.limit({ key: ipBucket })).success) {
      return jsonError(429, "Too many requests. Slow down.", { "retry-after": "60" });
    }
    const length = Number(request.headers.get("content-length") ?? 0);
    if (length > LIMITS.maxRequestBodyBytes) return jsonError(413, "Request too large");

    const url = new URL(request.url);
    // Stateless server: no SSE stream (GET) and no session to end (DELETE).
    // Refused here, before the token is looked up or anything is read.
    if ((url.pathname === "/mcp" || url.pathname.startsWith("/mcp/")) && (request.method === "GET" || request.method === "DELETE")) {
      return new Response(JSON.stringify({ error: "Method not allowed. Use POST." }), {
        status: 405,
        headers: { "content-type": "application/json", allow: "POST" },
      });
    }
    if (url.pathname === "/register" && request.method === "POST") {
      if (!(await guardStub(env).registrationAllowed(ipBucket))) {
        return jsonError(429, "Too many agent registrations today. Try again tomorrow.");
      }
    }
    return getProvider(env).fetch(request, env, ctx);
  },

  async scheduled(controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(runSweep(env, controller.scheduledTime));
  },
} satisfies ExportedHandler<Env>;
