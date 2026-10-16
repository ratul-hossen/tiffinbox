import { addDoc, collection, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { logActivity } from "./activityLog";
import type { Chef, ChefApprovalStatus } from "../types";

/**
 * Single write path for every chef approval decision (Chef Approvals page and
 * the Chefs page toggle). `isVerified` stays the one field the app actually
 * gates on; `approvalStatus` additionally records "rejected" so a rejected
 * chef drops out of the waiting list and sees a rejected screen in the app.
 * Any back-and-forth with the chef happens manually over WhatsApp / email
 * (owner's choice) - no in-app messaging here.
 */
export async function setChefApproval(chef: Chef, status: ChefApprovalStatus): Promise<void> {
  await updateDoc(doc(db, "chefs", chef.id), {
    isVerified: status === "approved",
    approvalStatus: status,
    approvalUpdatedAt: Date.now(),
  });

  if (status === "approved") {
    // In-app notification history (notifications/{uid}/items) - the chef's
    // pending screen already flips live, this just leaves a record.
    await addDoc(collection(db, "notifications", chef.id, "items"), {
      title: "Account approved 🎉",
      body: "Your kitchen profile has been approved - you can now manage your menu and receive orders.",
      createdAt: Date.now(),
    });
  }

  await logActivity(`Chef approval: ${status}`, "chefs", chef.id, {
    kitchenName: chef.kitchenName ?? null,
  });
}

/** Waiting on an admin decision: not approved, not rejected, not demo data. */
export function isAwaitingReview(chef: Chef): boolean {
  return !chef.isVerified && !chef.isDemo && chef.approvalStatus !== "rejected";
}
