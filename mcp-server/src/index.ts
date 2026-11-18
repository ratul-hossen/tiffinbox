/**
 * TiffinBox MCP server (Cloudflare Worker).
 *
 *   /mcp         MCP (Streamable HTTP, stateless) - needs an OAuth access token
 *   /authorize   the code page (auth.ts)
 *   /token, /register, /.well-known/*  handled by @cloudflare/workers-oauth-provider
 *
 * Layers of protection, outermost first:
 *   1. Cloudflare edge DDoS protection (automatic)
 *   2. per-IP limit on every request, body size cap
 *   3. no valid token -> rejected before any Firestore read
 *   4. per-connection limit per minute (edge) and per day (Guard)
 *   5. connection must still exist (Disconnect in the app), user must be a
 *      non-blocked customer, MCP must be switched on in App Settings
 *   6. MCP-wide daily Firestore read budget, so agent traffic can never use
 *      up the free quota the app itself needs
 */
import OAuthProvider from "@cloudflare/workers-oauth-provider";
import { WebStandardStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js";
import { SCOPES, guardStub, handleAuthorize, landingPage } from "./auth";
import { loadConfig, loadUser } from "./catalog";
import type { AgentProps, Env } from "./env";
import { Firestore } from "./firestore";
import { LIMITS } from "./limits";
import { buildServer } from "./tools";

export { Guard } from "./guard";

const jsonError = (status: number, message: string, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "content-type": "application/json", ...extra },
  });

// "This connection still exists" is trusted for a minute per isolate, and
// lastUsedAt is written at most every 10 minutes, to keep Firestore use low.
const connectionSeen = new Map<string, { ok: boolean; at: number }>();
const lastUsedWritten = new Map<string, number>();

async function connectionExists(db: Firestore, props: AgentProps): Promise<boolean> {
  const key = `${props.uid}/${props.connectionId}`;
  const hit = connectionSeen.get(key);
  if (hit && Date.now() - hit.at < LIMITS.connectionCheckMs) return hit.ok;
  const ok = (await db.get(`users/${props.uid}/agent_connections/${props.connectionId}`, ["createdAt"])) !== null;
  connectionSeen.set(key, { ok, at: Date.now() });
  return ok;
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

      if (!(await connectionExists(db, props))) {
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

      const server = buildServer({
        db,
        props,
        gate: async () => {
          const verdict = await g.checkCall(props.connectionId, config.mcpDailyReadBudget);
          if (verdict.ok) return null;
          return verdict.reason === "read_budget"
            ? "TiffinBox's AI agent access is busy today. Please use the TiffinBox app, or try again tomorrow."
            : "This agent has reached today's limit for TiffinBox. Try again tomorrow.";
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
      ctx.waitUntil(g.addReads(db.reads).then(() => g.ensureCleanup()));
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
    requiredScopes: SCOPES,
    // Revoking is instant anyway (every call checks the connection still
    // exists), so a few hours keeps OAuth's KV writes low on the free plan.
    accessTokenTTL: 6 * 3600,
    refreshTokenTTL: 30 * 24 * 3600,
    // Registrations per IP are limited in the outer fetch below.
  });
  providerFor = base;
  return provider;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const ip = request.headers.get("cf-connecting-ip") ?? "local";
    if (!(await env.IP_LIMITER.limit({ key: ip })).success) {
      return jsonError(429, "Too many requests. Slow down.", { "retry-after": "60" });
    }
    const length = Number(request.headers.get("content-length") ?? 0);
    if (length > LIMITS.maxRequestBodyBytes) return jsonError(413, "Request too large");

    const url = new URL(request.url);
    if (url.pathname === "/register" && request.method === "POST") {
      if (!(await guardStub(env).registrationAllowed(ip))) {
        return jsonError(429, "Too many agent registrations from this network today.");
      }
    }
    return getProvider(env).fetch(request, env, ctx);
  },
} satisfies ExportedHandler<Env>;
