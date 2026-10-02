import { useEffect, useState } from "react";
import { onAuthStateChanged, type User } from "firebase/auth";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { auth, db } from "../firebase";

export type AdminAuthStatus =
  | "loading" // checking Firebase Auth state
  | "signed-out" // no Firebase user
  | "checking-admin" // signed in, checking admins/{uid}
  | "denied" // signed in but not in admins collection
  | "admin"; // signed in and confirmed admin

export type AdminRole = "super_admin" | "admin";

// The one hardcoded super admin, per the project owner's explicit request.
// Only this email can self-heal its own admins/{uid} doc up to
// role="super_admin" (see firestore.rules) — every other admin (including
// ones this super admin invites later) is a regular "admin".
const SUPER_ADMIN_EMAIL = "hossen240504@gmail.com";

interface AdminAuthState {
  status: AdminAuthStatus;
  user: User | null;
  role: AdminRole | null;
  isSuperAdmin: boolean;
  error: string | null;
  recheck: () => void;
}

/**
 * Admin gate: after Firebase Auth succeeds, this checks whether a document
 * exists at admins/{uid}. If it doesn't, status becomes "denied" and the
 * rest of the admin UI never renders for them. We deliberately do NOT force
 * a sign-out here — the AccessDenied screen keeps the user's session alive
 * just long enough to show their UID (to hand to an existing admin) and a
 * manual "Sign out" button. No admin-only data or actions ever render for a
 * "denied" user.
 */
export function useAdminAuth(): AdminAuthState {
  const [status, setStatus] = useState<AdminAuthStatus>("loading");
  const [user, setUser] = useState<User | null>(null);
  const [role, setRole] = useState<AdminRole | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      if (!firebaseUser) {
        setUser(null);
        setRole(null);
        setStatus("signed-out");
        return;
      }

      setUser(firebaseUser);
      setStatus("checking-admin");

      try {
        const adminRef = doc(db, "admins", firebaseUser.uid);
        let adminDoc = await getDoc(adminRef);

        if (!adminDoc.exists()) {
          setRole(null);
          setStatus("denied");
          return;
        }

        let currentRole = (adminDoc.data().role as AdminRole | undefined) ?? "admin";

        // One-time self-heal: the hardcoded super-admin email upgrades its
        // own doc the first time it doesn't already have role="super_admin".
        // Firestore rules only allow this exact transition (see
        // firestore.rules admins/{adminId} `allow update`), so this can't be
        // used to grant super-admin to anyone else.
        if (firebaseUser.email === SUPER_ADMIN_EMAIL && currentRole !== "super_admin") {
          try {
            await setDoc(adminRef, { role: "super_admin" }, { merge: true });
            currentRole = "super_admin";
          } catch {
            // If this fails (e.g. rules not yet deployed), fall back to
            // whatever role was already stored rather than blocking login.
          }
        }

        setRole(currentRole);
        setStatus("admin");
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to verify admin access.");
        setStatus("denied");
      }
    });

    return unsubscribe;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nonce]);

  return {
    status,
    user,
    role,
    isSuperAdmin: role === "super_admin",
    error,
    recheck: () => setNonce((n) => n + 1),
  };
}
