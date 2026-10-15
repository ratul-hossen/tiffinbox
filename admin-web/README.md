# TiffinBox Admin Web

A standalone React + Vite + TypeScript admin panel for the TiffinBox
Firebase project (the same one the Android app in `../app` uses). It talks
directly to Firestore via the client SDK — there is no backend server.

It lets an admin:

- Log in with Firebase Auth (email/password, or **Sign in with Google** — see
  setup note below)
- View & manage **Chefs** (verify, toggle today's availability, edit
  name/bio/specialty, delete, open a detail view with their meals/orders/reviews)
- View & manage **Meals** (filter by chef, edit price/quantity/available,
  delete)
- View **Customers/Users** (read-only; sparse until the signup flow backfills
  it; open a detail view with role/createdAt and — for customers — order history)
- View & manage **Orders** (filter by status/chef, change status, revenue
  total, open an itemized detail view with a status timeline, export the
  current filtered list as CSV)
- View **Reviews** (filter by chef, flag/delete for moderation)
- View & manage **Subscriptions** (filter by active, force-cancel)
- See a **Dashboard** with stat cards, a 14-day orders chart, an order-status
  breakdown chart, and a top-chefs leaderboard
- See an **Activity Log** of every mutating admin action (who did what, to
  what, and when) — a durable, append-only audit trail
- Use a **Recycle Bin**: every "Delete" button (Chefs, Meals, Reviews,
  Delivery Partners, Admins) is a soft delete — the item disappears from the
  app immediately but its full data is kept in `recycle_bin` until an admin
  either **Restores** it (writes it back to its original collection/id) or
  **Deletes forever** (actually gone)

A global search box in the top bar filters whichever page is currently open.

## 0. Google Sign-In setup (one-time, in Firebase Console)

The admin-web Login screen now offers "Sign in with Google" alongside
email/password. For it to work:

1. Firebase Console → **Authentication → Sign-in method** → enable the
   **Google** provider (one toggle — Firebase handles the OAuth client for
   web automatically; this is **not** the same as the Android app's SHA-1
   fingerprint setup, and needs no extra configuration here).
2. Once this admin panel is deployed somewhere (Vercel, etc. — a separate
   step, not covered by this repo), add that deployed domain to
   **Authentication → Settings → Authorized domains**. If you skip this, the
   Google sign-in popup will fail with an `auth/unauthorized-domain` error
   once it's live on the public URL (it works fine on `localhost` during
   local dev without this step).

## 1. Set up the Firebase Web App config

This must point at the **same Firebase project** the Android app already
uses, so it reads/writes the exact same Firestore collections.

1. Go to the [Firebase Console](https://console.firebase.google.com/) →
   open the TiffinBox project.
2. Project Settings (gear icon) → **Your apps** → if there's no "Web app"
   yet, click **Add app → Web**, give it any nickname (e.g. "admin-web"),
   and finish the wizard. If a web app already exists, just open it.
3. Copy the `firebaseConfig` values it shows you.
4. Create `admin-web/.env.local` (this file is gitignored — do **not** use
   `.env.example`, that's just the template) with:

   ```
   VITE_FIREBASE_API_KEY=...
   VITE_FIREBASE_AUTH_DOMAIN=...
   VITE_FIREBASE_PROJECT_ID=...
   VITE_FIREBASE_STORAGE_BUCKET=...
   VITE_FIREBASE_MESSAGING_SENDER_ID=...
   VITE_FIREBASE_APP_ID=...
   ```

## 2. Install & run locally

```
cd admin-web
npm install
npm run dev
```

Open the printed `localhost` URL. This is **local-only** — nothing here
deploys or hosts the app anywhere. Putting it on the public internet
(Firebase Hosting, Vercel, etc.) is a separate step the project owner must
explicitly decide on and approve later.

You'll need a Firebase Auth user to log in with. If you don't have one yet,
either create one from Firebase Console → Authentication → Add user, or
sign up through the Android app (same project), then use that email/password
here.

## 3. Grant yourself admin access

There's no separate backend, so "is this user an admin" is decided purely
by Firestore: if a document exists at `admins/{their-uid}`, they're an
admin. After Firebase Auth succeeds, the app checks that doc; if it's
missing, you'll see an "Access denied" screen and none of the admin UI
renders.

The easiest way to grant the **first** admin: log in once with your account.
On the Access Denied screen, if the `admins` collection is still completely
empty, you'll see a **"Make me the first admin (dev bootstrap)"** button —
click it, and you're in. (This mirrors the same "dev convenience button"
pattern the Android app already uses for seeding sample data — see
`app/src/main/java/com/ratulhossen/tiffinbox/data/firebase/FirestoreSeedRepository.kt`.)
The button only works while the collection is empty, so it can't be used to
self-grant access once a real admin already exists.

Alternatively, an existing admin (or the project owner, manually) can add
another admin from the Firebase Console's Firestore **Data** tab: create a
document in the `admins` collection whose **document ID is that person's
Firebase Auth uid**, with any field (e.g. `{ addedAt: <timestamp> }`).

## 4. Firestore rules

`../firestore.rules` has been extended (not restructured) with an
`isAdmin()` helper and an `admins` collection, plus broadened admin
read/write on `chefs`, `meals`, `orders`, `reviews`, `users` and the
`subscriptions` collection, plus an append-only `admin_activity_log`
collection (admins can read and create entries; update/delete are always
denied, so the Activity Log tab stays a trustworthy audit trail), plus an
admin-only `recycle_bin` collection for the soft-delete/Restore feature
(`chefs` and `reviews` `create` also accept `isAdmin()` now, in addition to
their normal owner check, so a Restore can write either doc type back to
its original id on someone else's behalf).

**This file is not auto-deployed.** It's the source of truth to keep the
*live* Firebase Console rules in sync with, but publishing changes here
requires manually re-pasting the whole file into **Firebase Console →
Firestore Database → Rules → Publish** every time it changes — there is no
CI/CD step that does this automatically. If the admin panel ever shows
"Missing or insufficient permissions" on a collection the code expects to
read/write, the live console rules have likely drifted from this file —
re-paste and republish it.

## Notes / scope

- No test suite, no routing library (just a simple tab switcher) — kept
  deliberately lean.
- All writes go straight to Firestore from the browser via the client SDK;
  there is no admin backend/API.
- Do not commit `.env.local` — it's gitignored.
- Visual design follows an "Apple-premium" system modeled on the project
  owner's own portfolio admin: a grouped sidebar (Overview/Catalog/
  Operations/People/System), a big page-title + description header, and a
  set of design tokens (`apple-bg`, `apple-surface`, `apple-surface2`,
  `apple-text`, `apple-secondary`, `apple-blue`, `apple-green`,
  `apple-amber`, `apple-red`, plus the flat `apple-border`/`apple-hover`/
  `apple-subtle` fill tokens) defined once in `tailwind.config.js` rather
  than hardcoded hex values scattered through components. Every token is a
  CSS variable (`src/index.css`) that flips between a **dark** palette
  (the default) and a **light** one depending on `data-theme` on
  `<html>`, toggled from the sidebar (sun/moon icon) and persisted via
  `src/hooks/useTheme.ts` (`localStorage`, key
  `tiffinbox-admin-theme`) — components never branch on theme themselves,
  they just use the token classes. Icons are from `lucide-react`.
