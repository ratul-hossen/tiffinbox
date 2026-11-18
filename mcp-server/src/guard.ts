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

  /** Old day-keyed counters are dropped once a day so storage doesn't grow forever. */
  async alarm(): Promise<void> {
    const today = quotaDay();
    const all = await this.ctx.storage.list<number>();
    const stale = [...all.keys()].filter(
      (k) => /^(calls|reads|reg):/.test(k) && !k.includes(today),
    );
    for (let i = 0; i < stale.length; i += 128) await this.ctx.storage.delete(stale.slice(i, i + 128));
    await this.ctx.storage.setAlarm(Date.now() + 24 * 3600_000);
  }

  async ensureCleanup(): Promise<void> {
    if ((await this.ctx.storage.getAlarm()) === null) await this.ctx.storage.setAlarm(Date.now() + 3600_000);
  }
}
