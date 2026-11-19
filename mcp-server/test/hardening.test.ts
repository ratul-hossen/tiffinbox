import { describe, expect, it } from "vitest";
import { hasRecentRealOrder } from "../src/chat";
import { SAFE_SEGMENT, UnsafePathError, assertSafePath } from "../src/firestore";
import { LIMITS } from "../src/limits";
import { PAYMENT_GRACE_MS, expiryFor, normalizeLast4 } from "../src/orders";
import { dhakaDay, ipKey, isKnownRedirectHost, isLoopbackHost, orderIdFor, quotaDay } from "../src/util";

describe("ipKey", () => {
  it("keeps IPv4 addresses as they are", () => {
    expect(ipKey("203.0.113.7")).toBe("203.0.113.7");
    expect(ipKey(" 203.0.113.7 ")).toBe("203.0.113.7");
  });
  it("buckets IPv6 to its /64, however it is written", () => {
    const k = ipKey("2001:db8:abcd:12:1111:2222:3333:4444");
    expect(k).toBe("2001:db8:abcd:12::/64");
    expect(ipKey("2001:0DB8:ABCD:0012:ffff:0:0:1")).toBe(k);
    expect(ipKey("2001:db8:abcd:12::1")).toBe(k);
    expect(ipKey("[2001:db8:abcd:12::99]")).toBe(k);
    expect(ipKey("2001:db8:abcd:13::1")).not.toBe(k);
  });
  it("handles short and unusual IPv6 forms", () => {
    expect(ipKey("::1")).toBe("0:0:0:0::/64");
    expect(ipKey("2001:db8::")).toBe("2001:db8:0:0::/64");
    expect(ipKey("fe80::1%eth0")).toBe("fe80:0:0:0::/64");
    expect(ipKey("::ffff:198.51.100.4")).toBe("198.51.100.4");
  });
  it("never returns an empty key", () => {
    expect(ipKey("")).toBe("unknown");
    expect(ipKey(null)).toBe("unknown");
    expect(ipKey("local")).toBe("local");
  });
});

describe("Firestore path safety", () => {
  it("accepts the paths the server uses", () => {
    for (const p of [
      "orders/AbC123xyz",
      "meals/m1",
      "chats/uid123_chefA/messages/x-1",
      "payment_txn_ids/BKASH_TRX1234567",
      "users/u1/agent_connections/c1",
      "app_config/settings",
    ]) {
      expect(() => assertSafePath(p)).not.toThrow();
    }
  });
  it("refuses anything that could change the URL or escape the collection", () => {
    for (const p of [
      "orders/../users/x",
      "orders/a?mask.fieldPaths=x",
      "orders/a%2Fb",
      "orders/",
      "orders//x",
      "orders/a b",
      "orders/a:commit",
      "orders/#ABC",
      "orders/অর্ডার",
      `orders/${"x".repeat(129)}`,
      "",
    ]) {
      expect(() => assertSafePath(p), p).toThrow(UnsafePathError);
    }
    expect(SAFE_SEGMENT.test("a".repeat(128))).toBe(true);
  });
});

describe("deterministic order ids", () => {
  it("is the first 20 hex characters of SHA-256(uid:key)", async () => {
    // node -e 'require("crypto").createHash("sha256").update("cust1:e2e-order-key-1").digest("hex").slice(0,20)'
    const expected = "c3df1d2a40da0e117d25";
    expect(await orderIdFor("cust1", "e2e-order-key-1")).toBe(expected);
  });
  it("is the same for the same key and different for another user or key", async () => {
    const a = await orderIdFor("cust1", "key-12345678");
    expect(await orderIdFor("cust1", "key-12345678")).toBe(a);
    expect(await orderIdFor("cust2", "key-12345678")).not.toBe(a);
    expect(await orderIdFor("cust1", "key-12345679")).not.toBe(a);
    expect(a).toMatch(/^[0-9a-f]{20}$/);
    expect(() => assertSafePath(`orders/${a}`)).not.toThrow();
  });
});

describe("days", () => {
  it("counts orders and messages by the Dhaka date", () => {
    // 2026-11-18 20:00 UTC is already 19 November in Dhaka (UTC+6) ...
    expect(dhakaDay(Date.UTC(2026, 10, 18, 20, 0))).toBe("2026-11-19");
    // ... and 17:59 UTC is still the 18th.
    expect(dhakaDay(Date.UTC(2026, 10, 18, 17, 59))).toBe("2026-11-18");
  });
  it("keeps the Pacific date for the Firestore read budget", () => {
    // 2026-11-19 05:00 UTC is still the 18th in California, but the 19th in Dhaka.
    const t = Date.UTC(2026, 10, 19, 5, 0);
    expect(quotaDay(t)).toBe("2026-11-18");
    expect(dhakaDay(t)).toBe("2026-11-19");
  });
});

describe("consent page hosts", () => {
  it("recognises the big agents and desktop (loopback) clients", () => {
    for (const h of ["claude.ai", "www.claude.ai", "anthropic.com", "chatgpt.com", "platform.openai.com", "localhost", "127.0.0.1", "[::1]", "::1"]) {
      expect(isKnownRedirectHost(h), h).toBe(true);
    }
  });
  it("labels everything else unverified, including look-alikes", () => {
    for (const h of ["evil.example.com", "claude.ai.evil.com", "notclaude.ai", "chatgpt.com.attacker.net", "openai.com.evil.io", "com.example.app:/cb", "192.168.1.5"]) {
      expect(isKnownRedirectHost(h), h).toBe(false);
    }
  });
  it("knows a loopback address", () => {
    expect(isLoopbackHost("localhost")).toBe(true);
    expect(isLoopbackHost("127.0.0.53")).toBe(true);
    expect(isLoopbackHost("[::1]")).toBe(true);
    expect(isLoopbackHost("claude.ai")).toBe(false);
  });
});

describe("payments", () => {
  it("takes exactly 4 digits for the sender's number, Bengali digits too", () => {
    expect(normalizeLast4("5678")).toBe("5678");
    expect(normalizeLast4("৫৬৭৮")).toBe("5678");
    expect(normalizeLast4("567")).toBeNull();
    expect(normalizeLast4("56789")).toBeNull();
    expect(normalizeLast4("56a8")).toBeNull();
  });
});

describe("expiry", () => {
  const now = Date.UTC(2026, 10, 19, 12, 0);
  const doc = (data: Record<string, unknown>) => ({ id: "o1", path: "orders/o1", data: { source: "agent", ...data } });
  it("cancels an unconfirmed order after confirmBy", () => {
    expect(expiryFor(doc({ status: "AwaitingConfirmation", confirmBy: now - 1 }), now)).toBe("unconfirmed");
    expect(expiryFor(doc({ status: "AwaitingConfirmation", confirmBy: now + 1 }), now)).toBeNull();
  });
  it("gives the payment 30 minutes of grace after payBy", () => {
    expect(PAYMENT_GRACE_MS).toBe(30 * 60_000);
    expect(expiryFor(doc({ status: "AwaitingPayment", payBy: now - 29 * 60_000 }), now)).toBeNull();
    expect(expiryFor(doc({ status: "AwaitingPayment", payBy: now - 31 * 60_000 }), now)).toBe("unpaid");
  });
  it("refunds a paid order the chef didn't accept within 12 hours", () => {
    const placedAt = now - LIMITS.acceptTimeoutMs - 1;
    expect(expiryFor(doc({ status: "Placed", placedAt, paymentVerifiedAt: placedAt }), now)).toBe("not_accepted");
    expect(expiryFor(doc({ status: "Placed", placedAt: now - 3600_000, paymentVerifiedAt: now - 3600_000 }), now)).toBeNull();
    // Not verified, or not an agent order: the app handles those.
    expect(expiryFor(doc({ status: "Placed", placedAt }), now)).toBeNull();
    expect(expiryFor({ id: "o2", path: "orders/o2", data: { status: "Placed", placedAt, paymentVerifiedAt: 1 } }, now)).toBeNull();
  });
});

describe("who an agent may message", () => {
  const now = Date.UTC(2026, 10, 19, 12, 0);
  const o = (status: string, daysAgo: number, chefId = "chefA") => ({ data: { chefId, status, placedAt: now - daysAgo * 86400_000 } });
  it("needs a real order (Placed to Delivered) with that kitchen in the last 30 days", () => {
    expect(hasRecentRealOrder([o("Placed", 1)], "chefA", now)).toBe(true);
    expect(hasRecentRealOrder([o("Delivered", 29)], "chefA", now)).toBe(true);
    expect(hasRecentRealOrder([o("Delivered", 31)], "chefA", now)).toBe(false);
    expect(hasRecentRealOrder([o("AwaitingConfirmation", 0), o("PaymentSubmitted", 0), o("Cancelled", 0)], "chefA", now)).toBe(false);
    expect(hasRecentRealOrder([o("Delivered", 1, "chefB")], "chefA", now)).toBe(false);
  });
});
