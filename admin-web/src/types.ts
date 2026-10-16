// Shapes mirror the Firestore documents written by the TiffinBox Android app.
// Every field is optional-ish on read because older/partial docs may be
// missing fields (especially `users`, which only started being populated
// via the new signup flow) — code should not assume completeness.

export type ChefApprovalStatus = "pending" | "approved" | "rejected";

export interface Chef {
  id: string;
  kitchenName?: string;
  ownerName?: string;
  emoji?: string;
  distanceKm?: number;
  bio?: string;
  specialty?: string[];
  overallRating?: number;
  taste?: number;
  hygiene?: number;
  packaging?: number;
  onTime?: number;
  totalOrders?: number;
  isVerified?: boolean;
  isAvailableToday?: boolean;
  latitude?: number;
  longitude?: number;
  deliveryMode?: "self" | "tiffinbox" | string;
  photoBase64?: string;
  kitchenType?: string;
  yearsOfExperience?: number;
  bkashNumber?: string;
  nagadNumber?: string;
  whatsappNumber?: string;
  phoneNumber?: string;
  // Set by admin-web's Chef Approvals page (lib/chefApproval.ts). Older docs
  // have none - treat a missing status as "pending" when !isVerified.
  approvalStatus?: ChefApprovalStatus;
  approvalUpdatedAt?: number;
  // true only for chefs created via the "Seed demo chefs" button on
  // ChefsPage - lets "Remove all demo chefs" find exactly this data and
  // nothing else, and lets the app show a "Demo" badge to real users.
  isDemo?: boolean;
}

export interface Meal {
  id: string;
  chefId?: string;
  name?: string;
  emoji?: string;
  description?: string;
  price?: number;
  category?: string;
  isVeg?: boolean;
  allergens?: string[];
  dietaryTags?: string[];
  availableQuantity?: number;
  portionSize?: string;
  isAvailable?: boolean;
  photoBase64?: string;
  // Mirrors the owning chef's isDemo flag, set at creation time so "Remove
  // all demo chefs" can delete demo meals directly without a join query.
  isDemo?: boolean;
}

export interface AppUser {
  id: string; // Firebase Auth uid
  name?: string;
  email?: string;
  role?: "CUSTOMER" | "CHEF" | string;
  phoneNumber?: string;
  whatsappNumber?: string;
  createdAt?: number; // epoch millis
}

export interface OrderItem {
  mealId?: string;
  mealName?: string;
  quantity?: number;
  unitPrice?: number;
}

export type OrderStatus =
  | "Placed"
  | "Accepted"
  | "Cooking"
  | "OutForDelivery"
  | "Delivered"
  | "Cancelled";

export const ORDER_STATUSES: OrderStatus[] = [
  "Placed",
  "Accepted",
  "Cooking",
  "OutForDelivery",
  "Delivered",
  "Cancelled",
];

export interface Order {
  id: string;
  customerId?: string;
  chefId?: string;
  items?: OrderItem[];
  subtotal?: number;
  deliveryFee?: number;
  platformFee?: number;
  totalPrice?: number;
  deliveryAddress?: string;
  paymentMethod?: string;
  // Manual mobile-banking proof (bKash or Nagad - same field for both; which one is in paymentMethod).
  paymentTransactionId?: string;
  paymentScreenshotBase64?: string;
  deliveryHandledBy?: "self" | "tiffinbox" | string;
  deliveryPartnerId?: string;
  status?: OrderStatus | string;
  placedAt?: number; // epoch millis
}

export interface Review {
  id: string;
  customerId?: string;
  chefId?: string;
  tasteRating?: number;
  hygieneRating?: number;
  packagingRating?: number;
  onTimeRating?: number;
  overallRating?: number;
  comment?: string;
  createdAt?: number;
}

// A Firestore Timestamp as it comes back from onSnapshot (serverTimestamp()
// resolves to an object with toDate()/toMillis(), not a plain number).
export interface FirestoreTimestampLike {
  toMillis?: () => number;
  toDate?: () => Date;
  seconds?: number;
}

export interface AdminActivityLogEntry {
  id: string;
  adminUid?: string | null;
  adminEmail?: string | null;
  action?: string;
  targetCollection?: string;
  targetId?: string;
  details?: Record<string, unknown> | null;
  timestamp?: FirestoreTimestampLike | number | null;
}

export interface Admin {
  id: string; // Firebase Auth uid
  email?: string;
  role?: "super_admin" | "admin";
  addedAt?: number | FirestoreTimestampLike | null;
  note?: string;
}

// chats/{customerId_chefId} — mirrors FirestoreChatRepository.kt's schema.
// sentAt/lastMessageAt are plain epoch-millis numbers (System.currentTimeMillis()
// on the Android side), not Firestore server timestamps — admin-web writes
// Date.now() for the same reason, so every client reads the same shape.
export interface ChatThread {
  id: string;
  customerId?: string;
  chefId?: string;
  chefName?: string;
  lastMessage?: string;
  lastMessageAt?: number;
}

export interface ChatMessage {
  id: string;
  senderId?: string;
  text?: string;
  sentAt?: number;
  isAdmin?: boolean;
  adminEmail?: string | null;
}

// delivery_partners/{uid} - the uid belongs to a Firebase Auth account
// created ONLY by an admin (DeliveryPartnersPage), via a secondary app
// instance (see firebase.ts) - there is no self-signup for delivery
// partners, by design.
export interface DeliveryPartner {
  id: string; // Firebase Auth uid
  name?: string;
  email?: string;
  phone?: string;
  isActive?: boolean;
  createdAt?: number | FirestoreTimestampLike | null;
}

// self_service_requests/{id} - a customer-initiated point-to-point delivery
// request, independent of ordering from a chef (see
// FirestoreSelfServiceRepository.kt). No delivery-partner app exists yet to
// act on these, so this is visible/manageable here in the interim.
export interface SelfServiceRequest {
  id: string;
  customerId?: string;
  pickupAddress?: string;
  dropAddress?: string;
  preferredTime?: string;
  note?: string;
  status?: "pending" | "assigned" | string;
  deliveryPartnerId?: string;
  createdAt?: number | FirestoreTimestampLike | null;
}

// recycle_bin/{collection}__{id} - a soft-deleted doc's full data snapshot,
// written by lib/recycleBin.ts right before the real doc is deleted from its
// own collection. "Restore" writes `data` back to `sourceCollection`/
// `sourceId`; "Delete forever" just removes this entry.
export interface RecycleBinEntry {
  id: string;
  sourceCollection: string;
  sourceId: string;
  data?: Record<string, unknown>;
  label?: string;
  deletedAt?: number;
  deletedByEmail?: string | null;
}

export interface Subscription {
  id: string;
  customerId?: string;
  chefId?: string;
  chefName?: string;
  plan?: "WEEKLY" | "DAYS_10" | "DAYS_15" | "MONTHLY" | string;
  mealsPerWeek?: number;
  mealType?: "Lunch" | "Dinner" | "Both" | string;
  pricePerMeal?: number;
  totalPrice?: number;
  startedAt?: number;
  isActive?: boolean;
}
