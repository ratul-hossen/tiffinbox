/**
 * Every limit in one place. The per-minute ones (60 per IP, 30 per agent)
 * live in wrangler.jsonc's `ratelimits`, because Cloudflare enforces those
 * at the edge.
 */
export const LIMITS = {
  /** Tool calls one agent connection may make per day. */
  callsPerConnectionPerDay: 1000,
  /**
   * Firestore documents the MCP server may read per day, all users together.
   * Spark gives the whole project ~50,000; this keeps MCP to 30% so a flood
   * of agent traffic can switch MCP off but never take the app down.
   * The admin can lower/raise it in App Settings (`mcpDailyReadBudget`).
   */
  defaultDailyReadBudget: 15_000,
  /** Wrong pairing codes from one IP before it is locked out. */
  pairingFailuresBeforeLock: 5,
  pairingLockMinutes: 15,
  /** A pairing code is valid this long after the app creates it. */
  pairingCodeTtlMs: 5 * 60 * 1000,
  /** New OAuth client registrations allowed per IP per day. */
  registrationsPerIpPerDay: 10,
  /** How long a loaded catalog / config is reused before re-reading Firestore. */
  catalogCacheMs: 60 * 1000,
  /** How long "this connection still exists" is trusted before re-checking. */
  connectionCheckMs: 60 * 1000,
  /** lastUsedAt on a connection is written at most this often. */
  lastUsedWriteMs: 10 * 60 * 1000,
  /** Results returned by search_kitchens at most. */
  maxSearchResults: 20,
  /** Longest kitchen-written text passed to an agent. */
  maxDescriptionChars: 300,
  maxRequestBodyBytes: 64 * 1024,
} as const;
