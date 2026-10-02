import { signOut, type User } from "firebase/auth";
import { auth } from "../firebase";

interface Props {
  user: User;
  onBootstrapped: () => void;
}

/**
 * Shown when a signed-in Firebase user has no admins/{uid} doc. There is no
 * self-bootstrap path anymore — firestore.rules only lets an existing super
 * admin create admins/{adminId} docs (see the "Admins" tab) — so this just
 * surfaces the signed-in user's own uid for them to hand to an existing
 * admin.
 */
export default function AccessDenied({ user, onBootstrapped }: Props) {
  async function handleSignOut() {
    try {
      await signOut(auth);
    } catch {
      // ignore
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-apple-bg px-4">
      <div className="w-full max-w-md bg-apple-surface rounded-2xl shadow-card border border-black/5 p-8 text-center">
        <div className="text-3xl mb-2">🚫</div>
        <h1 className="text-xl font-bold text-apple-text tracking-tight">Access denied</h1>
        <p className="text-sm text-apple-secondary mt-2">
          Signed in as <span className="font-medium text-apple-text">{user.email}</span>, but
          this account is not an admin.
        </p>

        <p className="text-sm text-apple-secondary mt-4">
          Ask an existing admin to add you from the "Admins" tab, using your
          Firebase Auth uid:
        </p>
        <code className="block text-xs bg-black/5 px-2 py-1.5 rounded mt-2 break-all">
          {user.uid}
        </code>

        <button
          onClick={onBootstrapped}
          className="mt-4 text-sm text-apple-blue hover:underline transition-colors duration-150"
        >
          I've been added — check again
        </button>

        <div>
          <button
            onClick={handleSignOut}
            className="mt-4 text-sm text-apple-secondary hover:text-apple-text underline transition-colors duration-150"
          >
            Sign out
          </button>
        </div>
      </div>
    </div>
  );
}
