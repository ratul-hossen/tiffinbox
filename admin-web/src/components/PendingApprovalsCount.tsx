import { useCollection } from "../hooks/useCollection";
import { isAwaitingReview } from "../lib/chefApproval";
import type { Chef } from "../types";

/** Red count pill next to "Chef Approvals" in the sidebar - only rendered once signed in. */
export default function PendingApprovalsCount() {
  const { data: chefs } = useCollection<Chef>("chefs");
  const count = chefs.filter(isAwaitingReview).length;
  if (count === 0) return null;
  return (
    <span className="ml-auto min-w-[20px] text-center text-[11px] font-semibold rounded-full bg-apple-red text-white px-1.5 py-0.5">
      {count}
    </span>
  );
}
