// Shapes mirror the Firestore documents written by the TiffinBox Android app.
// Every field is optional-ish on read because older/partial docs may be
// missing fields (especially `users`, which only started being populated
// via the new signup flow) — code should not assume completeness.

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
}

export interface AppUser {
  id: string; // Firebase Auth uid
  name?: string;
  email?: string;
  role?: "CUSTOMER" | "CHEF" | string;
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

export interface Subscription {
  id: string;
  customerId?: string;
  chefId?: string;
  chefName?: string;
  plan?: "WEEKLY" | "MONTHLY" | string;
  mealsPerWeek?: number;
  pricePerMeal?: number;
  totalPrice?: number;
  startedAt?: number;
  isActive?: boolean;
}
