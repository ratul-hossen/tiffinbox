# TiffinBox — Project Memory & In-Progress Plan

This file is the durable cross-session memory for this project. Read it
first in any new session before making changes. Update it whenever the
plan below changes or a phase completes.

## What TiffinBox is

A Bangladeshi home-chef food marketplace with three codebases/surfaces in
one repo (`ratul-hossen/tiffinbox`):
- `app/` — Android app (Kotlin + Jetpack Compose + Firebase), for
  customers and chefs.
- `admin-web/` — React + Vite + TS + Tailwind admin panel (Firebase client
  SDK), deployed to Vercel at tiffinbox-admin.vercel.app.
- A planned, separate **delivery-partner app** (not yet built) — see
  "Delivery system" below.

Firebase project: `tiffinbox-2cb8b`, on the **free Spark plan**. The
project owner has repeatedly and explicitly declined to add a Google
Cloud / Firebase billing account (Blaze plan). **Every feature must work
without Firebase Storage, Cloud Functions, or Google Maps Platform**
unless the owner explicitly says otherwise in a given conversation. See
"Known billing constraint" below for the established workarounds.

## Git workflow (important, don't skip)

- Develop on `claude/great-cray-r5ziwz`, push there.
- **Never merge to `main` yourself.** Open a PR; the repo owner clicks
  "Merge pull request" on GitHub themselves, so the merge is attributed to
  their own GitHub account for their contribution graph.
- If that branch's PR has already been merged and you have new work,
  follow the repo's standard instructions for restarting a branch after a
  merged PR (see the session's own system prompt / CONTRIBUTING-style
  guidance) rather than stacking commits silently on an already-merged
  branch history.

## Known billing constraint — the single most important recurring decision

Firebase Storage, Cloud Functions (2nd gen), and Google Maps Platform all
require the paid Blaze plan (a Google Cloud billing account on file,
i.e. a credit card). The owner has said no to this multiple times. The
established free-tier workarounds, already built and proven in this
codebase:

- **Image uploads (payment screenshots, kitchen photos, food photos):**
  compress client-side (see `app/src/main/java/.../util/ImageCompressor.kt`)
  and store as a base64 string directly on the Firestore document. Works
  because Firestore documents allow up to 1 MiB; a resized/compressed JPEG
  comfortably fits. Already used for bKash payment screenshots
  (`bkashScreenshotBase64` on `orders/{orderId}`). **Reuse this exact
  pattern for kitchen photos and food item photos** — do not reach for
  Firebase Storage.
- **Location / "nearby chefs":** plain Android location APIs
  (`FusedLocationProviderClient`, free Play Services) + the device's
  built-in `Geocoder` (free, on-device) + a pure-math Haversine distance
  calculation (`app/src/main/java/.../util/GeoUtils.kt`). No Google Maps
  SDK, no API key, no billing. There is currently **no visual map** (no
  pins, no satellite/street view) anywhere in the app — only distance
  numbers and sorted/filtered lists. If the owner ever says yes to Maps
  billing, layer an actual map UI on top of the same stored lat/lng
  fields; don't rebuild the location logic.
- **Video posts (chef video content) — NEW, not yet built:** true video
  file upload needs Firebase Storage (Blaze). The free-tier approach
  decided unilaterally this session (owner's instructions explicitly
  authorize "take a better approach yourself" when there's a cost
  problem): chefs paste an **external video link** (YouTube/Facebook/Google
  Drive share link) rather than uploading a file. The app stores the URL
  string and opens it externally (or embeds a WebView/YouTube player) —
  zero storage cost. If the owner wants true native upload/hosting later,
  that requires Blaze and should be confirmed explicitly first.
- **Delivery-partner accounts:** admin-web creates them via a **secondary
  Firebase App instance** (`admin-web/src/firebase.ts` → `secondaryAuth`)
  so account creation doesn't need a Cloud Function and doesn't hijack the
  admin's own session. No self-signup for delivery partners, by design.

**Rule of thumb for all future features:** before reaching for Storage,
Cloud Functions, or Maps, check whether the base64-in-Firestore /
on-device / link-paste pattern above already solves it for free. If a
feature genuinely cannot be done without Blaze, stop and ask the owner
explicitly — don't silently add a billing dependency.

## Already-built features (as of this plan being written)

Everything below is implemented, committed, and either merged to `main`
or sitting in an open PR on `claude/great-cray-r5ziwz`:

- Full customer + chef Android app: browse chefs/meals, cart, checkout,
  order tracking (real Firestore-backed, not a fake timer), reviews,
  favorites, subscriptions (weekly/monthly plans), chef dashboard with
  real earnings/forecast charts (Vico), chef menu manager.
- Firebase Auth (email/password + Google Sign-In via Credential Manager),
  email verification (fire-and-forget on signup), **real SMS OTP phone
  verification** (Firebase Phone Auth) mandatory right after signup for
  both roles and both signup paths (email/password AND Google).
- WhatsApp number mandatory at signup for both roles; bKash number
  mandatory for chefs only. Collected via `CompleteGoogleProfileScreen`
  for Google sign-ups (mirrors the email/password signup form fields).
- **Mandatory location setup** right after signup for both roles
  (`LocationSetupScreen`): GPS auto-detect or manual address →
  on-device Geocoder. Chef location is required ("verified location"
  requirement). Home screen and the full chef list compute real haversine
  distances and filter to the chef's ~3km `NEARBY_RADIUS_KM` radius once a
  customer has a saved location.
- **Chef admin-approval workflow:** the existing `isVerified` boolean
  field on `chefs/{uid}` (already toggleable in admin-web's Chefs page,
  now labeled "Approved"/"Pending approval") gates everything — an
  unverified chef's own dashboard shows a waiting-for-approval screen
  instead of the real one, and unverified chefs are filtered out of every
  customer-facing listing (`FirestoreCatalogRepository.getChefs()`).
- **bKash payment at checkout:** mandatory transaction ID + a payment
  screenshot (compressed, base64-in-Firestore, see billing section above)
  before an order can be placed with that method. Chef dashboard shows an
  in-app image-preview dialog; admin-web shows it inline in the order
  detail drawer.
- **WhatsApp-style chat:** a green chat button on Home (next to cart) →
  customer-side chat inbox (`CustomerChatListScreen`, mirrors the
  chef-side one) → redesigned chat thread (tinted wallpaper, tailed
  bubbles, per-message timestamps, pill input + circular send button).
- **Delivery-system groundwork (not the full delivery app yet):** a chef
  can set `deliveryMode` to `"self"` or `"tiffinbox"` from their dashboard
  (shown as a "🛵 TiffinBox Delivery" badge to customers on chef
  cards/detail). Orders record `deliveryHandledBy` and an (unassigned)
  `deliveryPartnerId`. admin-web has a **Delivery Partners** page that
  creates admin-provisioned accounts in a new `delivery_partners`
  Firestore collection (see billing section — secondary Firebase App
  instance trick). `firestore.rules` already has a `delivery_partners`
  match block and lets an assigned delivery partner read/update their
  order. **The actual delivery-partner Android app (job list,
  accept/decline, live location, navigation) does not exist yet** — that's
  the single largest remaining piece of unbuilt work in the whole project.
- admin-web: Dashboard, Chefs, Meals, Users, Orders, Reviews,
  Subscriptions, Activity Log, Chats, Delivery Partners, Admins (with a
  super-admin tier) pages — all built and deployed.

## Current task: Visual redesign toward the "Feathers" reference mockups

On 2026-10-18ish the owner shared a set of reference UI screenshots (a
different, more polished food-delivery app concept, labeled "this
screenshot is TiffinBox's previous/original idea") and asked for the
**whole app** to be restyled and extended to match, "sob ekbar e" (all at
once). Given honest scope, this is being done incrementally with this
file as the tracker — do not promise a single-pass full redesign to the
owner; work through the phases below and update this file as each lands.

**Color theme — the one explicit, unambiguous instruction:** change the
app's color theme to a **black + white + red combination**, and make it
**user-selectable** (a theme picker in Settings), not just a hardcoded
swap. The reference screenshots themselves are green/orange — the owner
is not asking to copy their color, just their general polish/layout; the
black+white+red request is a separate, explicit instruction to follow
literally.

**Reference screenshots show (for context, not literal pixel targets):**
- A 3-step stepper onboarding for chef signup (Personal Info → Kitchen
  Info → Location), collecting: kitchen name, kitchen type (dropdown:
  Home Kitchen/etc.), years of experience, a kitchen photo, then a
  location/delivery-radius step with a map + circular radius overlay and a
  radius slider (5km etc.).
- A role-select screen with **three** roles: Seller, Buyer, **and
  Delivery Man** shown side by side. **Do not copy this as a public
  self-signup Delivery Man role** — the owner's own, more recent,
  explicit instruction earlier this same project (and already built) is
  that delivery-partner accounts are admin-provisioned only, via a
  separate app, with no public self-signup. Reconcile in favor of the
  already-built, more recent instruction.
- Food/package management: a food item form (photo, name, description,
  price, meal type Lunch/Dinner/Both), and a "package" concept (7/10/15/30
  day subscription-style bundles with a meal type and daily capacity) —
  TiffinBox already has a simpler weekly/monthly subscription model
  (`FirestoreSubscriptionRepository.kt`); extending it to match 7/10/15/30
  day increments + daily capacity is a reasonable, scoped enhancement, not
  a rebuild.
- Chef dashboard stat tiles (today's orders/earnings), order
  accept/reject cards, order detail with a vertical status timeline,
  earnings with daily/weekly/monthly tabs and a bar chart (TiffinBox
  already has most of this via `EarningsChart`/Vico).
- Customer-side: nearby sellers list with distance + availability badges,
  a seller profile with packages vs. daily menu tabs, order payment method
  selection (bKash/Nagad/Card/COD — TiffinBox already has bKash + COD),
  order tracking with a live-location "view map" link (groundwork only,
  per the no-maps-billing constraint — a real embedded live map needs
  Blaze).
- Real **food/kitchen photos** throughout instead of the current emoji
  placeholders (`chef.emoji`, meal emoji) — this is a genuine, valuable,
  scoped piece of work: swap to the base64-in-Firestore image pattern.
- A **video posts** feature for chefs ("video add korte parbe, post korte
  parbe") — new feature, not in the reference screenshots at all but
  asked for directly in chat. Build as a link-paste field (see billing
  section), not a native upload, unless told otherwise.

### Phased plan (update statuses here as work lands)

1. **[x] Theme system — DONE.** `ThemePreferences` (SharedPreferences-backed,
   `ui/theme/ThemePreferences.kt`), `ThemePalette.BLACK_RED` (new default)
   and `.CLASSIC_GREEN` in `ui/theme/Color.kt`/`Theme.kt`, a real
   `SettingsScreen` (`ui/screens/settings/SettingsScreen.kt`) wired from
   Profile's "Settings" menu item → `Routes.SETTINGS`, `MainActivity`
   calls `ThemePreferences.init(this)` before `setContent`. Every other
   screen already reads `MaterialTheme.colorScheme.*` rather than hardcoded
   colors, so they automatically follow whichever palette is selected —
   no per-screen changes were needed for this step.
2. **[x] Kitchen/food photo uploads — DONE.** `ChefUi.photoBase64` and
   `MealUi.photoBase64` (`data/model/UiModels.kt`), read/written via
   `FirestoreCatalogRepository`/`FirestoreChefRepository.updatePhoto()`/
   `FirestoreMealRepository.addMeal()`. A reusable `Base64Thumbnail`
   composable (`ui/components/Base64Thumbnail.kt`) decodes the base64 and
   falls back to the emoji when blank — used in `ChefCard`,
   `ChefDetailScreen`, `MealCard`, and the `MenuManagerScreen` meal list.
   Photo pickers (`ActivityResultContracts.PickVisualMedia` +
   `ImageCompressor.compressToBase64`) are wired into
   `MenuManagerScreen`'s add-meal dialog (food photo) and
   `ChefDashboardScreen` (kitchen photo, its own Card above the
   availability toggle). Not yet done: a kitchen-photo step inside the
   chef *signup* flow itself — that's folded into step 4
   (onboarding restructuring) below, not a gap in this step.
3. **[x] Chef video posts — DONE.** `ChefUi.videos: List<ChefVideoUi>`
   (`{url, caption, postedAt}`), appended via
   `FirestoreChefRepository.addVideo()` (`FieldValue.arrayUnion`, no
   sub-collection needed). `ChefDashboardScreen` has a "Post a video" card
   (URL + caption fields, Post button, list of the chef's own posted
   videos). `ChefDetailScreen` shows a public "Videos" section — tapping
   an entry opens the link externally via `Intent.ACTION_VIEW` (no
   embedded WebView; simplest option, per the plan).
4. **[ ] Onboarding/signup visual restructuring (task #64)** — restructure
   the existing single-screen chef signup form (`LoginScreen.kt` signup
   mode) into a 3-step stepper: **Step 1 Personal Info** (name, email,
   password) → **Step 2 Kitchen Info** (kitchen name, new `kitchenType`
   dropdown e.g. Home Kitchen/Cloud Kitchen/Restaurant, new
   `yearsOfExperience`, WhatsApp number) → **Step 3 Confirm** (bKash
   number required, Nagad number optional) → submit goes through the
   existing phone-verification → location-setup chain unchanged (not
   duplicating `LocationSetupScreen`; the reference's in-stepper map step
   is *this app's* separate mandatory LocationSetupScreen right after, not
   a 4th stepper step — same outcome, reuses existing code). New fields
   (`kitchenType`, `yearsOfExperience`) added to `ChefUi` +
   `FirestoreChefRepository.createChefProfile` + mirrored into
   `CompleteGoogleProfileScreen` (Google sign-up path) for parity, per the
   fix already applied for WhatsApp/bKash/location in that path. Customer
   signup stays single-screen (reference emphasizes multi-step for Seller
   only).
5. **[ ] Package system enhancement (task #63)** — `SubscriptionPlan`
   gets day-count-based entries (`DAYS_7`, `DAYS_10`, `DAYS_15`,
   `DAYS_30`) alongside the legacy `WEEKLY`/`MONTHLY` (kept, never
   removed — existing subscribers' stored plan name must keep resolving).
   `SubscribeScreen` adds a Lunch/Dinner/Both meal-type chip row next to
   the existing meals-per-week stepper, matching the reference's "Package
   Details" screen (Package Type day-count, Meal Type). The reference's
   separate "Daily Capacity" field is **not** duplicated as new state —
   `MealUi.availableQuantity` (already built) already is each meal's daily
   capacity; re-use it rather than adding a second, parallel capacity
   number that could drift out of sync.
6. **[ ] General visual polish pass** — stat-tile dashboards (chef
   dashboard already has `EarningStat` tiles), order status vertical
   timeline (`OrderTrackingScreen` already has one, built earlier this
   project — nothing to add there). Remaining: apply the same
   stat-tile/timeline visual language to the customer Orders/history list
   if time allows once tasks #62–67 land; not a blocking gap, just
   unfinished polish.
7. **[ ] (Much larger, separate effort, not in this redesign pass) The
   actual delivery-partner Android app** — tracked here only as a
   pointer, see "Delivery system groundwork" above. Do not start this
   inside the same pass as the visual redesign; it's a distinct project.

### New items added after owner reviewed the reference screenshots a second time (2026-10-15)

Owner asked for a single reconciled plan covering done work + phases
4–6 + these additional screens spotted in the reference images that
weren't in the original phase list. Tracked as tasks #62–67 (see task
list), all free-tier:

- **[x] Nagad payment (task #62) — DONE.** `CheckoutScreen`'s
  `PaymentMethod.NAGAD` is now `available = true`, using the same manual-
  proof flow as bKash (transaction ID + screenshot). Ended up *better*
  than originally planned: rather than reusing bKash-named fields, the
  order's proof fields were generalized to `paymentTransactionId`/
  `paymentScreenshotBase64` (app + admin-web `OrderDetail.tsx`), so the
  label shown is correct for whichever method was actually used. Added
  `nagadNumber` on the chef doc alongside `bkashNumber`. **Card stays
  "coming soon"** — real card processing needs a paid merchant gateway
  (SSLCommerz/Stripe/etc.), out of scope for the free-tier constraint;
  an honest limitation, not an oversight.
- **[x] Settings sub-pages (task #65) — DONE.** Change Password
  (functional, `FirebaseAuthRepository.changePassword()` — reauthenticate
  + `updatePassword`; `hasPasswordProvider` guards Google-only accounts),
  Payment Methods, Help & Support, Terms & Conditions, Privacy Policy
  (the last three static — Terms/Privacy explicitly labeled early-stage
  placeholders, not legal advice). Wired from `SettingsScreen` and from
  `ProfileScreen`'s previously-dead "Payment Method"/"Notifications" menu
  items.
- **[x] In-app Notifications screen (task #66) — DONE.** A
  `notifications/{uid}/items` Firestore subcollection
  (`FirestoreNotificationRepository.kt`, free — no Cloud Functions,
  written directly by the client at the moment each event happens: order
  placed, order status change by the chef, chef-approval granted via
  admin-web), read by `NotificationsScreen`. In addition to the existing
  FCM push (`NotificationHelper.kt`), not a replacement. Wired from
  ProfileScreen (customer) and a new bell icon on ChefDashboardScreen's
  header (chef). `firestore.rules` documents why writes are deliberately
  cross-user here (no Cloud Function to broker it).
- **[x] Self Service + Hire Delivery Man (task #67) — DONE.** A
  `self_service_requests/{id}` Firestore collection
  (`FirestoreSelfServiceRepository.kt`), `SelfServiceRequestScreen`
  (pickup/drop address, time, note — reached from a new ProfileScreen
  menu item) and `HireDeliveryManScreen` (lists `delivery_partners` where
  `isActive == true`; hiring sets `deliveryPartnerId` +
  `status: "assigned"`). Since the actual delivery-partner Android app
  doesn't exist yet (phase 7, still out of scope), admin-web gets a
  **Self Service** page to see every request and manually assign/reassign
  a partner via a dropdown, so the feature is end-to-end usable now
  rather than a write-only stub.

### Explicit owner delegation on this task

The owner said, in their own words (paraphrased from Bengali): make all
of this match, mix it all together, and if there's any problem with an
approach, take a better one yourself — i.e. implementation-detail
judgment calls are pre-authorized. This does **not** extend to taking on
new Blaze/billing costs (that pattern has been asked and declined
multiple times already — always use the free-tier equivalents above) or
to silently dropping the admin-provisioned-only delivery-partner design
in favor of the reference screenshots' public self-signup role.

## File map for things mentioned above

- `app/src/main/java/com/ratulhossen/tiffinbox/ui/theme/Color.kt`,
  `Theme.kt` — color schemes.
- `app/src/main/java/com/ratulhossen/tiffinbox/MainActivity.kt` — theme
  entry point.
- `app/src/main/java/com/ratulhossen/tiffinbox/util/ImageCompressor.kt` —
  reusable base64 image compression.
- `app/src/main/java/com/ratulhossen/tiffinbox/util/GeoUtils.kt` —
  haversine distance + `NEARBY_RADIUS_KM`.
- `app/src/main/java/com/ratulhossen/tiffinbox/data/location/LocationRepository.kt`
  — GPS + Geocoder.
- `app/src/main/java/com/ratulhossen/tiffinbox/ui/screens/auth/LoginScreen.kt`,
  `CompleteGoogleProfileScreen.kt`, `PhoneVerificationScreen.kt`,
  `LocationSetupScreen.kt` — the full signup chain, both paths.
- `app/src/main/java/com/ratulhossen/tiffinbox/ui/screens/chefside/ChefDashboardScreen.kt`
  — approval gate, delivery-mode toggle, bKash screenshot viewer.
- `app/src/main/java/com/ratulhossen/tiffinbox/ui/screens/profile/ProfileScreen.kt`
  — has the dead "Settings" menu item to wire up.
- `admin-web/src/components/DeliveryPartnersPage.tsx`,
  `admin-web/src/firebase.ts` (`secondaryAuth`) — delivery-partner account
  creation.
- `firestore.rules` — source of truth for security rules; **not
  auto-deployed** — any change here must be manually re-pasted into
  Firebase Console → Firestore Database → Rules by the owner.
