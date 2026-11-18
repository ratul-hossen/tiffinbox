import { describe, expect, it } from "vitest";
import { findArea } from "../src/areas";
import { CODE_ALPHABET, normalizeCode } from "../src/auth";
import { cleanText, todayInDhaka } from "../src/catalog";

describe("pairing codes", () => {
  it("accepts the code as the app shows it, with or without the dash and in any case", () => {
    expect(normalizeCode("K7P2-M9QX")).toBe("K7P2M9QX");
    expect(normalizeCode(" k7p2m9qx ")).toBe("K7P2M9QX");
  });
  it("rejects wrong lengths and look-alike characters the app never generates", () => {
    expect(normalizeCode("K7P2M9Q")).toBeNull();
    expect(normalizeCode("K7P2M9QXX")).toBeNull();
    expect(normalizeCode("O0I1L000")).toBeNull();
    expect(CODE_ALPHABET).not.toMatch(/[01OIL]/);
  });
});

describe("kitchen text sent to agents", () => {
  it("strips control and bidi characters and collapses whitespace", () => {
    expect(cleanText("Biryani\u0000\n\n  with‮ egg")).toBe("Biryani with egg");
  });
  it("caps the length", () => {
    expect(cleanText("x".repeat(500), 10)).toHaveLength(10);
  });
  it("turns non-strings into empty text", () => {
    expect(cleanText(42)).toBe("");
  });
});

describe("areas", () => {
  it("finds an area by English or Bengali name, ignoring case", () => {
    expect(findArea("dhanmondi")?.names[0]).toBe("Dhanmondi");
    expect(findArea("মিরপুর")?.names[0]).toBe("Mirpur");
    expect(findArea("Old Dhaka")?.names[0]).toBe("Lalbagh");
  });
  it("returns null for unknown places", () => {
    expect(findArea("Kuala Lumpur")).toBeNull();
    expect(findArea("")).toBeNull();
  });
});

describe("dates", () => {
  it("uses Bangladesh's date for today's specials", () => {
    // 2026-11-18 20:00 UTC is already 19 November in Dhaka (UTC+6).
    expect(todayInDhaka(Date.UTC(2026, 10, 18, 20, 0))).toBe("2026-11-19");
  });
});
