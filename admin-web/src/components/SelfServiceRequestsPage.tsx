import { useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useCollection } from "../hooks/useCollection";
import type { DeliveryPartner, SelfServiceRequest } from "../types";
import { logActivity } from "../lib/activityLog";
import { formatDateTime } from "../lib/format";
import Badge from "./ui/Badge";

/**
 * Self Service = a customer-initiated point-to-point delivery request,
 * independent of ordering food from a chef (see
 * FirestoreSelfServiceRepository.kt). Customers can already hire a delivery
 * partner themselves from the app; this page exists so an admin can also
 * see and manually assign/reassign one, since there's no delivery-partner
 * app yet to notify a hired partner directly (same groundwork-only
 * reasoning as the Delivery Partners page).
 */
export default function SelfServiceRequestsPage() {
  const { data: requests, loading, error } = useCollection<SelfServiceRequest>("self_service_requests");
  const { data: partners } = useCollection<DeliveryPartner>("delivery_partners");
  const [busyId, setBusyId] = useState<string | null>(null);

  async function assignPartner(request: SelfServiceRequest, partnerId: string) {
    setBusyId(request.id);
    try {
      await updateDoc(doc(db, "self_service_requests", request.id), {
        deliveryPartnerId: partnerId,
        status: partnerId ? "assigned" : "pending",
      });
      await logActivity("Assigned delivery partner to self-service request", "self_service_requests", request.id, {
        deliveryPartnerId: partnerId,
      });
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <p className="text-sm text-apple-secondary">Loading self-service requests…</p>;
  if (error) return <p className="text-sm text-apple-red">Error: {error}</p>;

  return (
    <div className="bg-apple-surface rounded-2xl border border-black/5 shadow-card overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-black/[0.02] text-apple-secondary text-xs uppercase tracking-wide">
          <tr>
            <th className="text-left px-5 py-3 font-medium">Customer</th>
            <th className="text-left px-5 py-3 font-medium">Pickup</th>
            <th className="text-left px-5 py-3 font-medium">Drop</th>
            <th className="text-left px-5 py-3 font-medium">Requested</th>
            <th className="text-left px-5 py-3 font-medium">Status</th>
            <th className="text-left px-5 py-3 font-medium">Delivery Partner</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-black/5">
          {requests.map((request) => (
            <tr key={request.id}>
              <td className="px-5 py-3 text-apple-secondary">{request.customerId ?? "–"}</td>
              <td className="px-5 py-3 text-apple-text">{request.pickupAddress ?? "–"}</td>
              <td className="px-5 py-3 text-apple-text">{request.dropAddress ?? "–"}</td>
              <td className="px-5 py-3 text-apple-secondary">{formatDateTime(request.createdAt)}</td>
              <td className="px-5 py-3">
                <Badge tone={request.status === "assigned" ? "green" : "neutral"}>
                  {request.status === "assigned" ? "Assigned" : "Pending"}
                </Badge>
              </td>
              <td className="px-5 py-3">
                <select
                  value={request.deliveryPartnerId ?? ""}
                  onChange={(e) => assignPartner(request, e.target.value)}
                  disabled={busyId === request.id}
                  className="text-xs border border-black/10 rounded-lg px-2 py-1.5 bg-apple-surface focus:outline-none focus:ring-2 focus:ring-apple-blue/50"
                >
                  <option value="">Unassigned</option>
                  {partners.map((partner) => (
                    <option key={partner.id} value={partner.id}>
                      {partner.name ?? partner.id}
                    </option>
                  ))}
                </select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {requests.length === 0 && (
        <p className="text-sm text-apple-secondary p-5">No self-service requests yet.</p>
      )}
    </div>
  );
}
