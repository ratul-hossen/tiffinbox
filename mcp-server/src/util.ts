/**
 * Small pure helpers (no Workers-only imports, so vitest can test them):
 * IP bucketing for limits, the two "day" calendars, deterministic order ids
 * and the list of agent hosts the consent page recognises.
 */

/**
 * The key an IP is rate-limited and locked out by. IPv4 as is; IPv6 bucketed
 * to its /64, because one customer line usually gets a whole /64 and could
 * otherwise rotate through billions of addresses.
 */
export function ipKey(raw: string | null | undefined): string {
  const ip = (raw ?? "").trim().toLowerCase();
  if (!ip) return "unknown";
  if (!ip.includes(":")) return ip.slice(0, 64);
  const mapped = /^(?:0{0,4}:){0,5}:?ffff:(\d{1,3}(?:\.\d{1,3}){3})$/.exec(ip);
  if (mapped) return mapped[1]!;
  const addr = ip.replace(/^\[|\]$/g, "").split("%")[0]!;
  const halves = addr.split("::");
  if (halves.length > 2) return addr.slice(0, 64);
  const part = (s: string | undefined) => (s ? s.split(":") : []);
  // An embedded IPv4 tail (::ffff:1.2.3.4 style) is two 16-bit groups; only the first four matter here.
  const head = part(halves[0]);
  const tail = part(halves[1]).flatMap((g) => (g.includes(".") ? ["0", "0"] : [g]));
  const groups = halves.length === 2 ? [...head, ...Array(Math.max(0, 8 - head.length - tail.length)).fill("0"), ...tail] : head;
  if (groups.length !== 8 || !groups.every((g) => /^[0-9a-f]{1,4}$/.test(g))) return addr.slice(0, 64);
  return `${groups.slice(0, 4).map((g) => parseInt(g, 16).toString(16)).join(":")}::/64`;
}

/** The Pacific-time day: Firestore's free quota resets then, so the read budget uses it. */
export function quotaDay(now = Date.now()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Los_Angeles" }).format(new Date(now));
}

/** Today in Bangladesh, "yyyy-MM-dd". "Per day" for orders and messages means this day. */
export function dhakaDay(now = Date.now()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Dhaka" }).format(new Date(now));
}

/**
 * The order id an idempotency key produces: hex SHA-256 of "uid:key", first
 * 20 characters. The same key always maps to the same document, so creating
 * it with an exists=false precondition makes retries atomic.
 */
export async function orderIdFor(uid: string, idempotencyKey: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${uid}:${idempotencyKey}`));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 20);
}

/** Hosts of agents we recognise. Desktop agents redirect to this computer (loopback). */
export function isKnownRedirectHost(rawHost: string): boolean {
  const host = rawHost.trim().toLowerCase().replace(/^\[|\]$/g, "");
  if (host === "localhost" || host === "127.0.0.1" || host === "::1") return true;
  if (host === "claude.ai" || host.endsWith(".claude.ai")) return true;
  if (host === "anthropic.com") return true;
  if (host === "chatgpt.com") return true;
  if (host.endsWith(".openai.com")) return true;
  return false;
}

/** Same, for a loopback address (a program on the user's own computer). */
export function isLoopbackHost(rawHost: string): boolean {
  const host = rawHost.trim().toLowerCase().replace(/^\[|\]$/g, "");
  return host === "localhost" || host === "::1" || /^127\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(host);
}
