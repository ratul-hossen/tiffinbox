import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp } from "firebase/firestore";
import fs from "node:fs";

const env = await initializeTestEnvironment({
  projectId: "demo-tiffinbox",
  firestore: { host: "127.0.0.1", port: 8899, rules: fs.readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
});

const CUST = "cust1", CHEF_UID = "chefuid1", CHEF = "chef1", ADMIN = "admin1";

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "admins", ADMIN), { role: "admin", email: "a@b.c" });
  await setDoc(doc(db, "chefs", CHEF), { userId: CHEF_UID, kitchenName: "K", isVerified: true, maxDeliveryFee: 0 });
  await setDoc(doc(db, "users", CUST), { role: "customer", name: "C" });
});

const cust = env.authenticatedContext(CUST).firestore();
const chef = env.authenticatedContext(CHEF_UID).firestore();
const admin = env.authenticatedContext(ADMIN).firestore();
const anon = env.unauthenticatedContext().firestore();

// Exactly the shape FirestoreOrderRepository.placeOrder writes.
const order = (over = {}) => ({
  customerId: CUST, chefId: CHEF,
  items: [{ mealId: "m1", mealName: "Biryani", quantity: 2, unitPrice: 150 }],
  subtotal: 300, deliveryFee: 35, platformFee: 10,
  couponCode: "", discount: 0, pointsRedeemed: 0, rankDiscount: 0,
  estimatedDeliveryMinutes: 35, totalPrice: 345,
  deliveryAddress: "Ashulia", customerName: "C", customerPhone: "017",
  deliveryLatitude: 23.8, deliveryLongitude: 90.4,
  paymentMethod: "cod", paymentTransactionId: "", paymentScreenshotBase64: "",
  deliveryHandledBy: "self", deliveryPartnerId: "", status: "Placed",
  placedAt: Date.now(), mealIds: ["m1"], mealQuantities: { m1: 2 },
  platesReserved: true, platesRestored: false, isPreOrder: false, scheduledFor: 0,
  ...over,
});

let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); console.log("  PASS  " + name); pass++; }
  catch (e) { console.log("  FAIL  " + name + "\n        " + String(e).split("\n")[0]); fail++; }
}
const place = (id, over) => setDoc(doc(cust, "orders", id), order(over));

console.log("\n--- orders: a real order must still go through ---");
await t("plain COD order", () => assertSucceeds(place("o1")));
await t("free delivery (chef charges nothing)", () => assertSucceeds(place("o2", { deliveryFee: 0, totalPrice: 310 })));
await t("top-rank order: points + rank discount + coupon", () =>
  assertSucceeds(place("o3", { discount: 50, pointsRedeemed: 60, rankDiscount: 60, couponCode: "EID50", deliveryFee: 0, totalPrice: 146 })));
await t("everything free (total 0)", () => assertSucceeds(place("o4", { discount: 310, totalPrice: 0 })));
await t("pre-order, no money fields beyond the required ones", () =>
  assertSucceeds(place("o5", { isPreOrder: true, platesReserved: false, scheduledFor: Date.now() + 86400000 })));
await t("surcharged TiffinBox order, 1200 fee", () =>
  assertSucceeds(place("o6", { deliveryHandledBy: "tiffinbox", deliveryFee: 1200, totalPrice: 1510 })));
await t("admin restores an order from the Recycle Bin", () =>
  assertSucceeds(setDoc(doc(admin, "orders", "o7"), order({ status: "Delivered", totalPrice: 345 }))));

console.log("\n--- orders: tampering must be blocked ---");
await t("negative total", () => assertFails(place("x1", { totalPrice: -500 })));
await t("negative delivery fee", () => assertFails(place("x2", { deliveryFee: -100 })));
await t("negative platform fee", () => assertFails(place("x3", { platformFee: -10 })));
await t("negative coupon discount", () => assertFails(place("x4", { discount: -999 })));
await t("negative points redeemed", () => assertFails(place("x5", { pointsRedeemed: -50 })));
await t("rank discount above the 60 cap", () => assertFails(place("x6", { rankDiscount: 5000 })));
await t("absurd delivery fee (9999)", () => assertFails(place("x7", { deliveryFee: 9999 })));
await t("absurd platform fee (900)", () => assertFails(place("x8", { platformFee: 900 })));
await t("fee as a string instead of a number", () => assertFails(place("x9", { deliveryFee: "0" })));
await t("someone else's customerId", () => assertFails(place("x10", { customerId: "someoneelse" })));
await t("self-assigned Delivered status", () => assertFails(place("x11", { status: "Delivered" })));

console.log("\n--- the highest fee the app can now produce (MAX_FEE 1000, doubled by a surcharge) ---");
await t("fee 2000 (ceiling + a x2 surcharge)", () => assertSucceeds(place("y1", { deliveryFee: 2000, totalPrice: 2310 })));
await t("fee 5500 from the old unbounded config", () => assertFails(place("y2", { deliveryFee: 5500, totalPrice: 5810 })));

console.log("\n--- delivery_surcharges ---");
await t("app reads surcharges while signed out", () => assertSucceeds(getDoc(doc(anon, "delivery_surcharges", "s1"))));
await t("admin creates a surcharge", () => assertSucceeds(setDoc(doc(admin, "delivery_surcharges", "s1"), { areaName: "Ashulia", latitude: 23.8, longitude: 90.4, radiusKm: 3, type: "flat", amount: 20, isActive: true, validFrom: 0, validUntil: 0 })));
await t("chef cannot create a surcharge", () => assertFails(setDoc(doc(chef, "delivery_surcharges", "s2"), { areaName: "hack", amount: 500 })));
await t("customer cannot create a surcharge", () => assertFails(setDoc(doc(cust, "delivery_surcharges", "s3"), { areaName: "hack", amount: 500 })));

console.log("\n--- maxDeliveryFee is the admin's to set ---");
await t("chef cannot raise their own cap", () => assertFails(updateDoc(doc(chef, "chefs", CHEF), { maxDeliveryFee: 999 })));
await t("chef can still set their own rates", () => assertSucceeds(updateDoc(doc(chef, "chefs", CHEF), { selfDeliveryBaseFee: 30, selfDeliveryPerKmFee: 8, selfDeliveryIncludedKm: 2 })));
await t("admin sets the cap", () => assertSucceeds(updateDoc(doc(admin, "chefs", CHEF), { maxDeliveryFee: 60 })));


console.log("\n--- AI agent (MCP) pairing codes and connections ---");
const pair = (db, code, over = {}) => setDoc(doc(db, "mcp_pairing", code), { uid: CUST, createdAt: serverTimestamp(), ...over });
await t("customer creates a pairing code for themselves", () => assertSucceeds(pair(cust, "K7P2M9QX")));
await t("nobody can read a code back, not even its owner", () => assertFails(getDoc(doc(cust, "mcp_pairing", "K7P2M9QX"))));
await t("someone else cannot read a code", () => assertFails(getDoc(doc(chef, "mcp_pairing", "K7P2M9QX"))));
await t("a code for another user's account", () => assertFails(pair(chef, "A2B3C4D5")));
await t("a code that doesn't look like one (lowercase, 0/O/1/I/L)", () => assertFails(pair(cust, "k0o1il23")));
await t("a code with a forged createdAt", () => assertFails(pair(cust, "B2C3D4E5", { createdAt: Timestamp.fromMillis(Date.now() + 3600000) })));
await t("a code with extra fields", () => assertFails(pair(cust, "C2D3E4F5", { trusted: true })));
await t("signed out: no codes", () => assertFails(pair(anon, "D2E3F4G5")));
await t("owner cancels their own code", () => assertSucceeds(deleteDoc(doc(cust, "mcp_pairing", "K7P2M9QX"))));
await t("someone else cannot cancel it", async () => {
  await pair(cust, "E2F3G4H5");
  await assertFails(deleteDoc(doc(chef, "mcp_pairing", "E2F3G4H5")));
});

await env.withSecurityRulesDisabled(async (ctx) => {
  await setDoc(doc(ctx.firestore(), "users", CUST, "agent_connections", "conn1"), { clientName: "Claude", createdAt: 1 });
});
const conn = (db) => doc(db, "users", CUST, "agent_connections", "conn1");
await t("owner sees their connected agents", () => assertSucceeds(getDoc(conn(cust))));
await t("other users cannot see them", () => assertFails(getDoc(conn(chef))));
await t("a client cannot fake a connection", () => assertFails(setDoc(doc(cust, "users", CUST, "agent_connections", "fake"), { clientName: "x" })));
await t("a client cannot edit a connection", () => assertFails(updateDoc(conn(cust), { clientName: "renamed" })));
await t("admin can view a user's agents", () => assertSucceeds(getDoc(conn(admin))));
await t("owner disconnects an agent", () => assertSucceeds(deleteDoc(conn(cust))));

await t("customer cannot unblock themselves", () => assertFails(setDoc(doc(cust, "mcp_blocks", CUST), { blocked: false })));
await t("admin blocks a customer's AI agent access", () => assertSucceeds(setDoc(doc(admin, "mcp_blocks", CUST), { blocked: true })));
await t("customer can see they are blocked", () => assertSucceeds(getDoc(doc(cust, "mcp_blocks", CUST))));
await t("customer cannot remove the block", () => assertFails(deleteDoc(doc(cust, "mcp_blocks", CUST))));
await t("app_config MCP switch is admin-only", () => assertFails(setDoc(doc(cust, "app_config", "settings"), { mcpEnabled: true }, { merge: true })));

console.log(`\n${pass} passed, ${fail} failed`);
await env.cleanup();
process.exit(fail ? 1 : 0);
