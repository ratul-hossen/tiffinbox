import { useEffect, useState } from "react";
import {
  collection,
  limit as limitConstraint,
  onSnapshot,
  orderBy,
  query,
  type DocumentData,
} from "firebase/firestore";
import { db } from "../firebase";

export interface UseCollectionOptions {
  /** Field to order by server-side (e.g. "timestamp" for the activity log). */
  orderByField?: string;
  direction?: "asc" | "desc";
  /** Optional cap on how many docs to subscribe to. */
  limit?: number;
}

/**
 * Live-subscribes to an entire Firestore collection and maps each doc to
 * `{ id, ...data }`. Returns an empty array (not an error) for a collection
 * that doesn't exist yet or has no docs — several of these (e.g. `users`)
 * are expected to be sparse.
 *
 * Pass `options.orderByField` when a page needs server-side ordering (e.g.
 * the Activity Log page, which must read newest-first) instead of sorting
 * client-side after the fact.
 */
export function useCollection<T extends { id: string }>(
  collectionName: string,
  options: UseCollectionOptions = {},
) {
  const { orderByField, direction = "desc", limit: limitCount } = options;
  const [data, setData] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    const constraints = [];
    if (orderByField) constraints.push(orderBy(orderByField, direction));
    if (limitCount) constraints.push(limitConstraint(limitCount));

    const q =
      constraints.length > 0
        ? query(collection(db, collectionName), ...constraints)
        : collection(db, collectionName);

    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        const rows = snap.docs.map((d) => ({ id: d.id, ...(d.data() as DocumentData) })) as T[];
        setData(rows);
        setLoading(false);
        setError(null);
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      },
    );
    return unsubscribe;
  }, [collectionName, orderByField, direction, limitCount]);

  return { data, loading, error };
}
