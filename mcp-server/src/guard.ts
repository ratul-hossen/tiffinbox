/**
 * One Durable Object that keeps the counters a per-minute edge limit can't:
 *  - per-connection tool calls per day,
 *  - the MCP-wide Firestore read budget per day, and each customer's share,
 *  - wrong pairing codes per IP (lockout) and pairing attempts per day,
 *  - OAuth client registrations per IP and in total per day,
 *  - prepare_order quotes, and per-customer order / message slots.
 *
 * Two calendars: the read budget follows the Pacific-time day (that's when
 * Firestore's free quota resets); order and message limits follow the Dhaka
 * day, which is what a customer means by "today". IPs arrive already
 * bucketed by [ipKey] (IPv6 per /64). A single instance is plenty at
 * TiffinBox's scale.
 */
import { DurableObject } from "cloudflare:workers";
import { LIMITS } from "./limits";
import { dhakaDay, quotaDay } from "./util";

export { quotaDay };

export type CallVerdict = { ok: true } | { ok: false; reason: "daily_calls" | "read_budget" | "user_reads" };
export type PairingVerdict = { ok: true } | { ok: false; reason: "locked" | "busy"; retryAfterSec: number };
/** "msgk" is the per-kitchen message count; its key is "uid:kitchenId". */
export type SlotKind = "order" | "msg" | "msgk";
export type SlotVerdict = { ok: true } | { ok: false; reason: "daily" | "cooldown"; retryAfterSec: number };

const DAY_MS = 24 * 3600_000;
/** Counters whose key carries a date: "<kind>:<yyyy-MM-dd>:...". */
const DAY_KEYED = /^(calls|reads|ureads|reg|regall|pair|order|msg|msgk):(\d{4}-\d{2}-\d{2})(?::|$)/;
/** Cooldown timestamps: "<kind>last:<key>" = ms of the last send. */
const LAST_KEYED = /^(order|msg|msgk)last:/;

export class Guard extends DurableObject {
  private async num(key: string): Promise<number> {
    return (await this.ctx.storage.get<number>(key)) ?? 0;
  }

  private async bump(key: string, by = 1): Promise<number> {
    const next = (await this.num(key)) + by;
    await this.ctx.storage.put(key, next);
    return next;
  }

  /** Called before every tool call. Nothing is written when the read budget is already used up. */
  async checkCall(connectionId: string, uid: string, readBudget: number): Promise<CallVerdict> {
    const day = quotaDay();
    if ((await this.num(`reads:${day}`)) >= readBudget) return { ok: false, reason: "read_budget" };
    if ((await this.num(`ureads:${day}:${uid}`)) >= LIMITS.readsPerCustomerPerDay) return { ok: false, reason: "user_reads" };
    const calls = await this.bump(`calls:${day}:${connectionId}`);
    if (calls > LIMITS.callsPerConnectionPerDay) return { ok: false, reason: "daily_calls" };
    return { ok: true };
  }

  /** Called after a request with the documents it actually read (callers skip n == 0). */
  async addReads(n: number, uid?: string): Promise<void> {
    if (!(n > 0)) return;
    const day = quotaDay();
    await this.bump(`reads:${day}`, n);
    if (uid) await this.bump(`ureads:${day}:${uid}`, n);
  }

  async readsToday(): Promise<number> {
    return this.num(`reads:${quotaDay()}`);
  }

  /** Is the MCP-wide read budget still available today? (Read-only.) */
  async readBudgetLeft(readBudget: number): Promise<boolean> {
    return (await this.num(`reads:${quotaDay()}`)) < readBudget;
  }

  /**
   * May this IP try a pairing code now? Checked before the code is looked up
   * in Firestore: the IP's lockout, the MCP read budget and a global daily
   * cap on attempts. Counts the attempt only when it is allowed.
   */
  async pairingAllowed(ip: string, readBudget: number): Promise<PairingVerdict> {
    const now = Date.now();
    const lockedUntil = await this.num(`lock:${ip}`);
    if (lockedUntil > now) return { ok: false, reason: "locked", retryAfterSec: Math.ceil((lockedUntil - now) / 1000) };
    const day = quotaDay(now);
    if ((await this.num(`reads:${day}`)) >= readBudget) return { ok: false, reason: "busy", retryAfterSec: 3600 };
    const attempts = await this.num(`pair:${day}`);
    if (attempts >= LIMITS.pairingAttemptsPerDay) return { ok: false, reason: "busy", retryAfterSec: 3600 };
    await this.ctx.storage.put(`pair:${day}`, attempts + 1);
    return { ok: true };
  }

  async pairingFailed(ip: string): Promise<void> {
    const prev = await this.ctx.storage.get<{ count: number; at: number } | number>(`fails:${ip}`);
    const count = (typeof prev === "number" ? prev : prev?.count ?? 0) + 1;
    if (count >= LIMITS.pairingFailuresBeforeLock) {
      await this.ctx.storage.put(`lock:${ip}`, Date.now() + LIMITS.pairingLockMinutes * 60_000);
      await this.ctx.storage.delete(`fails:${ip}`);
    } else {
      await this.ctx.storage.put(`fails:${ip}`, { count, at: Date.now() });
    }
  }

  async pairingSucceeded(ip: string): Promise<void> {
    await this.ctx.storage.delete(`fails:${ip}`);
  }

  /** Per-IP and global daily caps on OAuth client registrations. Nothing is written once over a limit. */
  async registrationAllowed(ip: string): Promise<boolean> {
    const day = quotaDay();
    const perIpKey = `reg:${day}:${ip}`;
    const allKey = `regall:${day}`;
    const [perIp, all] = await Promise.all([this.num(perIpKey), this.num(allKey)]);
    if (perIp >= LIMITS.registrationsPerIpPerDay || all >= LIMITS.registrationsPerDay) return false;
    await this.ctx.storage.put({ [perIpKey]: perIp + 1, [allKey]: all + 1 });
    return true;
  }

  // ---- quotes and per-customer order / message slots ----

  /** Stores a prepare_order quote for [uid] until [expiresAt]. */
  async putQuote(id: string, uid: string, quote: unknown, expiresAt: number): Promise<void> {
    await this.ctx.storage.put(`quote:${id}`, { uid, quote, expiresAt });
  }

  /**
   * Reads and deletes a quote in one step, so two racing place_order calls
   * can't both use it. Only [uid]'s own quote is taken; null when it is
   * missing, expired or someone else's.
   */
  async takeQuote<T>(id: string, uid: string): Promise<T | null> {
    const key = `quote:${id}`;
    const hit = await this.ctx.storage.get<{ uid?: string; quote: T; expiresAt: number }>(key);
    if (!hit || hit.uid !== uid) return null;
    await this.ctx.storage.delete(key);
    return hit.expiresAt < Date.now() ? null : hit.quote;
  }

  /**
   * Checks and records a send: at most [perDay] per Dhaka day and one per
   * [cooldownMs] (0 = no cooldown). [kind] keeps the counters apart. Only
   * counts when allowed.
   */
  async takeSlot(kind: SlotKind, key: string, perDay: number, cooldownMs: number): Promise<SlotVerdict> {
    const countKey = `${kind}:${dhakaDay()}:${key}`;
    const lastKey = `${kind}last:${key}`;
    const now = Date.now();
    if (cooldownMs > 0) {
      const last = await this.num(lastKey);
      if (now - last < cooldownMs) {
        return { ok: false, reason: "cooldown", retryAfterSec: Math.ceil((cooldownMs - (now - last)) / 1000) };
      }
    }
    const count = await this.num(countKey);
    if (count >= perDay) return { ok: false, reason: "daily", retryAfterSec: 0 };
    await this.ctx.storage.put(countKey, count + 1);
    if (cooldownMs > 0) await this.ctx.storage.put(lastKey, now);
    return { ok: true };
  }

  /** Gives back a slot taken by [takeSlot] when the write that followed failed. */
  async returnSlot(kind: SlotKind, key: string): Promise<void> {
    const countKey = `${kind}:${dhakaDay()}:${key}`;
    const count = await this.num(countKey);
    if (count > 0) await this.ctx.storage.put(countKey, count - 1);
    await this.ctx.storage.delete(`${kind}last:${key}`);
  }

  /** Drops old counters, cooldowns, lockouts and quotes once a day, so storage doesn't grow forever. */
  async alarm(): Promise<void> {
    const now = Date.now();
    // Older than any day either calendar can still be on (UTC date two days ago).
    const cutoff = new Date(now - 2 * DAY_MS).toISOString().slice(0, 10);
    const all = await this.ctx.storage.list<unknown>();
    const stale = [...all.entries()]
      .filter(([k, v]) => {
        const day = DAY_KEYED.exec(k);
        if (day) return day[2]! < cutoff;
        if (LAST_KEYED.test(k)) return typeof v !== "number" || v < now - DAY_MS;
        if (k.startsWith("lock:")) return typeof v !== "number" || v < now;
        if (k.startsWith("fails:")) return typeof v === "number" || ((v as { at?: number })?.at ?? 0) < now - DAY_MS;
        if (k.startsWith("quote:")) return ((v as { expiresAt?: number })?.expiresAt ?? 0) < now;
        // Idempotency records from before order ids were derived from the key.
        if (k.startsWith("idem:")) return ((v as { at?: number })?.at ?? 0) < now - 2 * DAY_MS;
        return false;
      })
      .map(([k]) => k);
    for (let i = 0; i < stale.length; i += 128) await this.ctx.storage.delete(stale.slice(i, i + 128));
    await this.ctx.storage.setAlarm(now + DAY_MS);
  }

  /** Makes sure the daily clean-up alarm is set. Returns true once it is (callers cache that). */
  async ensureCleanup(): Promise<boolean> {
    if ((await this.ctx.storage.getAlarm()) === null) await this.ctx.storage.setAlarm(Date.now() + 3600_000);
    return true;
  }
}
