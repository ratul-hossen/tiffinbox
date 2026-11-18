/**
 * One Durable Object that keeps the counters a per-minute edge limit can't:
 *  - per-connection tool calls per day,
 *  - the MCP-wide Firestore read budget per day,
 *  - wrong pairing codes per IP (lockout),
 *  - OAuth client registrations per IP per day.
 *
 * "Day" is the Pacific-time day, because that's when Firestore's free quota
 * resets. A single instance is plenty at TiffinBox's scale.
 */
import { DurableObject } from "cloudflare:workers";
import { LIMITS } from "./limits";

export function quotaDay(now = Date.now()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Los_Angeles" }).format(new Date(now));
}

export type CallVerdict = { ok: true } | { ok: false; reason: "daily_calls" | "read_budget" };

export class Guard extends DurableObject {
  private async bump(key: string, by = 1): Promise<number> {
    const next = ((await this.ctx.storage.get<number>(key)) ?? 0) + by;
    await this.ctx.storage.put(key, next);
    return next;
  }

  /** Called before every tool call. */
  async checkCall(connectionId: string, readBudget: number): Promise<CallVerdict> {
    const day = quotaDay();
    const reads = (await this.ctx.storage.get<number>(`reads:${day}`)) ?? 0;
    if (reads >= readBudget) return { ok: false, reason: "read_budget" };
    const calls = await this.bump(`calls:${day}:${connectionId}`);
    if (calls > LIMITS.callsPerConnectionPerDay) return { ok: false, reason: "daily_calls" };
    return { ok: true };
  }

  /** Called after a request with the documents it actually read. */
  async addReads(n: number): Promise<void> {
    if (n > 0) await this.bump(`reads:${quotaDay()}`, n);
  }

  async readsToday(): Promise<number> {
    return (await this.ctx.storage.get<number>(`reads:${quotaDay()}`)) ?? 0;
  }

  /** Is this IP allowed to try a pairing code right now? */
  async pairingAllowed(ip: string): Promise<{ ok: boolean; retryAfterSec: number }> {
    const lockedUntil = (await this.ctx.storage.get<number>(`lock:${ip}`)) ?? 0;
    const now = Date.now();
    if (lockedUntil > now) return { ok: false, retryAfterSec: Math.ceil((lockedUntil - now) / 1000) };
    return { ok: true, retryAfterSec: 0 };
  }

  async pairingFailed(ip: string): Promise<void> {
    const fails = await this.bump(`fails:${ip}`);
    if (fails >= LIMITS.pairingFailuresBeforeLock) {
      await this.ctx.storage.put(`lock:${ip}`, Date.now() + LIMITS.pairingLockMinutes * 60_000);
      await this.ctx.storage.delete(`fails:${ip}`);
    }
  }

  async pairingSucceeded(ip: string): Promise<void> {
    await this.ctx.storage.delete(`fails:${ip}`);
  }

  async registrationAllowed(ip: string): Promise<boolean> {
    return (await this.bump(`reg:${quotaDay()}:${ip}`)) <= LIMITS.registrationsPerIpPerDay;
  }

  // ---- phase B: quotes, idempotency, per-customer order and message limits ----

  /** Stores a prepare_order quote until [expiresAt]. */
  async putQuote(id: string, quote: unknown, expiresAt: number): Promise<void> {
    await this.ctx.storage.put(`quote:${id}`, { quote, expiresAt });
  }

  async getQuote<T>(id: string): Promise<T | null> {
    const hit = await this.ctx.storage.get<{ quote: T; expiresAt: number }>(`quote:${id}`);
    if (!hit) return null;
    if (hit.expiresAt < Date.now()) {
      await this.ctx.storage.delete(`quote:${id}`);
      return null;
    }
    return hit.quote;
  }

  async dropQuote(id: string): Promise<void> {
    await this.ctx.storage.delete(`quote:${id}`);
  }

  /** The order an idempotency key already produced, so a retried place_order never orders twice. */
  async idempotentOrder(uid: string, key: string): Promise<string | null> {
    return (await this.ctx.storage.get<{ orderId: string }>(`idem:${uid}:${key}`))?.orderId ?? null;
  }

  async rememberIdempotent(uid: string, key: string, orderId: string): Promise<void> {
    await this.ctx.storage.put(`idem:${uid}:${key}`, { orderId, at: Date.now() });
  }

  /**
   * Checks and records a send: at most [perDay] per day and one per
   * [cooldownMs]. [kind] keeps orders and messages apart. Only counts when allowed.
   */
  async takeSlot(
    kind: "order" | "msg",
    uid: string,
    perDay: number,
    cooldownMs: number,
  ): Promise<{ ok: true } | { ok: false; reason: "daily" | "cooldown"; retryAfterSec: number }> {
    const day = quotaDay();
    const countKey = `${kind}:${day}:${uid}`;
    const lastKey = `${kind}last:${uid}`;
    const now = Date.now();
    const last = (await this.ctx.storage.get<number>(lastKey)) ?? 0;
    if (now - last < cooldownMs) return { ok: false, reason: "cooldown", retryAfterSec: Math.ceil((cooldownMs - (now - last)) / 1000) };
    const count = (await this.ctx.storage.get<number>(countKey)) ?? 0;
    if (count >= perDay) return { ok: false, reason: "daily", retryAfterSec: 0 };
    await this.ctx.storage.put(countKey, count + 1);
    await this.ctx.storage.put(lastKey, now);
    return { ok: true };
  }

  /** Gives back a slot taken by [takeSlot] when the write that followed failed. */
  async returnSlot(kind: "order" | "msg", uid: string): Promise<void> {
    const countKey = `${kind}:${quotaDay()}:${uid}`;
    const count = (await this.ctx.storage.get<number>(countKey)) ?? 0;
    if (count > 0) await this.ctx.storage.put(countKey, count - 1);
    await this.ctx.storage.delete(`${kind}last:${uid}`);
  }

  /** Old day-keyed counters are dropped once a day so storage doesn't grow forever. */
  async alarm(): Promise<void> {
    const today = quotaDay();
    const all = await this.ctx.storage.list<unknown>();
    const now = Date.now();
    const stale = [...all.entries()]
      .filter(([k, v]) => {
        if (/^(calls|reads|reg|order|msg):/.test(k)) return !k.includes(today);
        if (k.startsWith("quote:")) return ((v as { expiresAt?: number })?.expiresAt ?? 0) < now;
        if (k.startsWith("idem:")) return ((v as { at?: number })?.at ?? 0) < now - 2 * 24 * 3600_000;
        return false;
      })
      .map(([k]) => k);
    for (let i = 0; i < stale.length; i += 128) await this.ctx.storage.delete(stale.slice(i, i + 128));
    await this.ctx.storage.setAlarm(Date.now() + 24 * 3600_000);
  }

  async ensureCleanup(): Promise<void> {
    if ((await this.ctx.storage.getAlarm()) === null) await this.ctx.storage.setAlarm(Date.now() + 3600_000);
  }
}
