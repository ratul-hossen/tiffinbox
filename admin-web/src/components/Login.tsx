import { useState, type FormEvent } from "react";
import { GoogleAuthProvider, signInWithEmailAndPassword, signInWithPopup } from "firebase/auth";
import { auth } from "../firebase";

function GoogleLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
      <path
        fill="#FFC107"
        d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z"
      />
      <path
        fill="#FF3D00"
        d="M6.306 14.691l6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z"
      />
      <path
        fill="#4CAF50"
        d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238C29.211 35.091 26.715 36 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z"
      />
      <path
        fill="#1976D2"
        d="M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.003-.002 6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z"
      />
    </svg>
  );
}

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [googleSubmitting, setGoogleSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signInWithEmailAndPassword(auth, email.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-in failed.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGoogleSignIn() {
    setError(null);
    setGoogleSubmitting(true);
    try {
      await signInWithPopup(auth, new GoogleAuthProvider());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Google sign-in failed.");
    } finally {
      setGoogleSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-apple-bg px-4">
      <div className="w-full max-w-sm bg-apple-surface rounded-2xl shadow-card border border-apple-border p-8">
        <div className="mb-6 text-center">
          <div className="text-3xl mb-2">🍱</div>
          <h1 className="text-xl font-bold text-apple-text tracking-tight">TiffinBox Admin</h1>
          <p className="text-sm text-apple-secondary mt-1">Sign in with your admin account</p>
        </div>

        <button
          type="button"
          onClick={handleGoogleSignIn}
          disabled={googleSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-white hover:bg-apple-subtle disabled:opacity-60 border border-apple-border2 text-apple-text rounded-xl py-2.5 text-sm font-medium transition-colors duration-150 shadow-card"
        >
          <GoogleLogo />
          {googleSubmitting ? "Signing in…" : "Sign in with Google"}
        </button>

        <div className="flex items-center gap-3 my-5">
          <div className="flex-1 h-px bg-apple-hover2" />
          <span className="text-xs text-apple-secondary">or</span>
          <div className="flex-1 h-px bg-apple-hover2" />
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-apple-text mb-1">Email</label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-xl border border-apple-border2 bg-apple-surface2 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-apple-blue/50 focus:border-apple-blue transition-all duration-150"
              placeholder="you@example.com"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-apple-text mb-1">Password</label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-xl border border-apple-border2 bg-apple-surface2 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-apple-blue/50 focus:border-apple-blue transition-all duration-150"
              placeholder="••••••••"
            />
          </div>

          {error && (
            <div className="text-sm text-apple-red bg-apple-red/10 border border-apple-red/20 rounded-xl px-3 py-2">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-apple-blue hover:bg-apple-blue-dark disabled:opacity-60 text-white rounded-xl py-2.5 text-sm font-medium transition-colors duration-150"
          >
            {submitting ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <p className="text-xs text-apple-secondary/80 mt-6 text-center">
          Use the same Firebase project as the TiffinBox Android app. New here?
          Sign up a regular account first, then grant yourself admin access
          after logging in.
        </p>
      </div>
    </div>
  );
}
