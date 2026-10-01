import { useEffect, useState } from "react";
import { signOut, type User } from "firebase/auth";
import { collection, doc, getDocs, query, serverTimestamp, setDoc, limit } from "firebase/firestore";
import { auth, db } from "../firebase";

interface Props {
  user: User;
  onBootstrapped: () => void;
}

/**
 * Shown when a signed-in Firebase user has no admins/{uid} doc. Mirrors the
 * Android app's "dev seed button" pattern (see
 * app/src/main/java/com/ratulhossen/tiffinbox/data/firebase/FirestoreSeedRepository.kt):
 * a plainly-labelled, dev-convenience button that performs one easily
 * understood write and reports success/failure inline, rather than a silent
 * or hidden tool. Only offered while the `admins` collection is still empty,
 * so it can bootstrap the very first admin but can't be used to self-grant
 * access once a real admin already exists.
 */
export default function AccessDenied({ user, onBootstrapped }: Props) {
  const [checkingEmpty, setCheckingEmpty] = useState(true);
  const [collectionEmpty, setCollectionEmpty] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const snap = await getDocs(query(collection(db, "admins"), limit(1)));
        if (!cancelled) setCollectionEmpty(snap.empty);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Could not check the admins collection.");
        }
      } finally {
        if (!cancelled) setCheckingEmpty(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleBootstrap() {
    setBootstrapping(true);
    setError(null);
    try {
      await setDoc(doc(db, "admins", user.uid), {
        addedAt: serverTimestamp(),
        email: user.email ?? null,
        note: "self-bootstrapped via admin-web dev button",
      });
      onBootstrapped();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to bootstrap admin access.");
    } finally {
      setBootstrapping(false);
    }
  }

  async function handleSignOut() {
    try {
      await signOut(auth);
    } catch {
      // ignore
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md bg-white rounded-xl shadow-sm border border-slate-200 p-8 text-center">
        <div className="text-3xl mb-2">🚫</div>
        <h1 className="text-xl font-semibold text-slate-900">Access denied</h1>
        <p className="text-sm text-slate-500 mt-2">
          Signed in as <span className="font-medium">{user.email}</span>, but
          this account is not an admin.
        </p>

        {checkingEmpty ? (
          <p className="text-sm text-slate-400 mt-4">Checking admin setup…</p>
        ) : collectionEmpty ? (
          <div className="mt-6 border border-amber-200 bg-amber-50 rounded-lg p-4 text-left">
            <p className="text-sm text-amber-800">
              No admins have been set up yet for this project. Since you're
              signed in, you can make yourself the first admin:
            </p>
            <button
              onClick={handleBootstrap}
              disabled={bootstrapping}
              className="mt-3 w-full bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white rounded-lg py-2 text-sm font-medium transition-colors"
            >
              {bootstrapping ? "Adding you as admin…" : "Make me the first admin (dev bootstrap)"}
            </button>
          </div>
        ) : (
          <p className="text-sm text-slate-500 mt-4">
            Ask the project owner to add a document for your Firebase Auth
            uid (<code className="text-xs bg-slate-100 px-1 py-0.5 rounded">{user.uid}</code>) to
            the <code className="text-xs bg-slate-100 px-1 py-0.5 rounded">admins</code> collection
            in Firestore.
          </p>
        )}

        {error && (
          <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2 mt-4">
            {error}
          </div>
        )}

        <button
          onClick={handleSignOut}
          className="mt-6 text-sm text-slate-500 hover:text-slate-700 underline"
        >
          Sign out
        </button>
      </div>
    </div>
  );
}
