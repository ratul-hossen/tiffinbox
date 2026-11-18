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

  // ---- phase B: ordering through an agent (owner-approved numbers) ----
  /** A price quote from prepare_order is good for this long. */
  quoteTtlMs: 10 * 60 * 1000,
  /** The customer has this long to tap Confirm in the app, or the order is cancelled. */
  confirmWindowMs: 15 * 60 * 1000,
  /** After confirming, this long to send money and give the TrxID. */
  paymentWindowMs: 60 * 60 * 1000,
  /** Minimum gap between two agent orders from one customer. */
  orderCooldownMs: 2 * 60 * 1000,
  /** Plates of one dish per order (same cap the app uses for pre-orders). */
  maxPlatesPerItem: 10,
  maxItemsPerOrder: 10,
  /** Agent orders per day / largest total / waiting at once, by trust level. */
  trust: {
    new: { perDay: 1, maxTotal: 500, pendingAtOnce: 1, label: "new (no delivered order yet)" },
    regular: { perDay: 3, maxTotal: 1000, pendingAtOnce: 1, label: "regular (1+ delivered orders)" },
    trusted: { perDay: 5, maxTotal: 2000, pendingAtOnce: 2, label: "trusted (10+ delivered orders)" },
  },
  trustedAfterDelivered: 10,
  /** Hard ceiling whatever the trust level. */
  hardMaxTotal: 3000,

  /** Messages an agent may send to kitchens per customer per day, and their length. */
  messagesPerDay: 20,
  maxMessageChars: 500,
  messageCooldownMs: 15 * 1000,
} as const;

export type TrustLevel = keyof typeof LIMITS.trust;
