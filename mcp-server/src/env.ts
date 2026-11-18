import type { OAuthHelpers } from "@cloudflare/workers-oauth-provider";

export interface Env {
  OAUTH_KV: KVNamespace;
  OAUTH_PROVIDER: OAuthHelpers;
  GUARD: DurableObjectNamespace;
  IP_LIMITER: RateLimit;
  TOKEN_LIMITER: RateLimit;

  /** This Worker's public origin, e.g. https://tiffinbox-mcp.<account>.workers.dev (no trailing slash). */
  PUBLIC_URL: string;
  FIREBASE_PROJECT_ID: string;
  /** The service account key JSON (a Worker secret). Not needed with the emulator. */
  FIREBASE_SERVICE_ACCOUNT?: string;
  /** "127.0.0.1:8080" for local tests against the Firestore emulator. Never set in production. */
  FIRESTORE_EMULATOR_HOST?: string;

  TURNSTILE_SITE_KEY?: string;
  TURNSTILE_SECRET_KEY?: string;
}

/** What every MCP request knows about its caller (set when the code is accepted). */
export interface AgentProps {
  uid: string;
  connectionId: string;
  clientName: string;
}
