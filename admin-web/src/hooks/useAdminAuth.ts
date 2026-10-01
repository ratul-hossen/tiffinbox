import { useEffect, useState } from "react";
import { onAuthStateChanged, type User } from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";
import { auth, db } from "../firebase";

export type AdminAuthStatus =
  | "loading" // checking Firebase Auth state
  | "signed-out" // no Firebase user
  | "checking-admin" // signed in, checking admins/{uid}
  | "denied" // signed in but not in admins collection
  | "admin"; // signed in and confirmed admin

interface AdminAuthState {
  status: AdminAuthStatus;
  user: User | null;
  error: string | null;
  recheck: () => void;
}

/**
 * Admin gate: after Firebase Auth succeeds, this checks whether a document
 * exists at admins/{uid}. If it doesn't, status becomes "denied" and the
 * rest of the admin UI never renders for them. We deliberately do NOT force
 * a sign-out here (unlike auto-signing-out immediately) — the AccessDenied
 * screen keeps the user's session alive just long enough to offer a manual
 * "Sign out" button, and — when the admins collection is still empty — a
 * one-click "bootstrap" button that lets this user add themselves as the
 * first admin. Either way, no admin-only data or actions ever render.
 */
export function useAdminAuth(): AdminAuthState {
  const [status, setStatus] = useState<AdminAuthStatus>("loading");
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      if (!firebaseUser) {
        setUser(null);
        setStatus("signed-out");
        return;
      }

      setUser(firebaseUser);
      setStatus("checking-admin");

      try {
        const adminDoc = await getDoc(doc(db, "admins", firebaseUser.uid));
        if (adminDoc.exists()) {
          setStatus("admin");
        } else {
          setStatus("denied");
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to verify admin access.");
        setStatus("denied");
      }
    });

    return unsubscribe;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nonce]);

  return { status, user, error, recheck: () => setNonce((n) => n + 1) };
}
