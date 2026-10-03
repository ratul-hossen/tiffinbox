import { addDoc, collection, serverTimestamp } from "firebase/firestore";
import { auth, db } from "../firebase";

/**
 * Appends one document to the `admin_activity_log` Firestore collection.
 * This is the single write path for the admin "tracking" feature — every
 * mutating admin action (toggle verified/available, edit chef, delete
 * chef/meal, change order status, delete/flag review, cancel subscription)
 * should call this right alongside its own Firestore write, so there's a
 * durable, append-only audit trail of "who did what, when".
 *
 * Deliberately fire-and-forget-ish: a logging failure (e.g. a transient
 * network blip) must never block or roll back the actual admin action, so
 * errors are swallowed after a console warning rather than thrown.
 */
export async function logActivity(
  action: string,
  targetCollection: string,
  targetId: string,
  details?: Record<string, unknown>,
): Promise<void> {
  const admin = auth.currentUser;
  try {
    await addDoc(collection(db, "admin_activity_log"), {
      adminUid: admin?.uid ?? null,
      adminEmail: admin?.email ?? null,
      action,
      targetCollection,
      targetId,
      details: details ?? null,
      timestamp: serverTimestamp(),
    });
  } catch (err) {
    // Never let audit-log failures block the admin's real action.
    console.warn("Failed to write admin_activity_log entry:", err);
  }
}
