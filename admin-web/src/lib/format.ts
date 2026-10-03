import type { FirestoreTimestampLike } from "../types";

export function formatCurrency(n: number): string {
  return `৳${n.toLocaleString(undefined, { maximumFractionDigits: 0 })}`;
}

/** Converts a Firestore Timestamp-like value OR epoch millis into a Date, or null. */
export function toDate(value?: FirestoreTimestampLike | number | null): Date | null {
  if (value == null) return null;
  if (typeof value === "number") return new Date(value);
  if (typeof value.toDate === "function") return value.toDate();
  if (typeof value.toMillis === "function") return new Date(value.toMillis());
  if (typeof value.seconds === "number") return new Date(value.seconds * 1000);
  return null;
}

export function formatDateTime(value?: FirestoreTimestampLike | number | null): string {
  const d = toDate(value);
  return d ? d.toLocaleString() : "–";
}

export function formatDate(value?: FirestoreTimestampLike | number | null): string {
  const d = toDate(value);
  return d ? d.toLocaleDateString() : "–";
}
