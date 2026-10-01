import { useEffect, useState } from "react";
import { collection, onSnapshot, type DocumentData } from "firebase/firestore";
import { db } from "../firebase";

/**
 * Live-subscribes to an entire Firestore collection and maps each doc to
 * `{ id, ...data }`. Returns an empty array (not an error) for a collection
 * that doesn't exist yet or has no docs — several of these (e.g. `users`)
 * are expected to be sparse.
 */
export function useCollection<T extends { id: string }>(collectionName: string) {
  const [data, setData] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    const unsubscribe = onSnapshot(
      collection(db, collectionName),
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
  }, [collectionName]);

  return { data, loading, error };
}
