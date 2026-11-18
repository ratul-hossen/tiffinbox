# Changelog

All notable changes to the TiffinBox Android app. Versions follow
[Semantic Versioning](https://semver.org): MAJOR.MINOR.PATCH.
How to cut a release: [docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

### Added
- **AI Agent (MCP), phase A:** customers can connect their own AI agent
  (Claude, ChatGPT or another MCP client) from Profile → **AI Agent (MCP)**.
  The agent can search kitchens, read menus and check delivery fees. It signs
  in with a one-time 8-letter code from the app; no password is typed on the
  web. Ordering through an agent comes in phase B.
  - New `mcp-server/` (Cloudflare Worker, free plan).
  - admin-web: an on/off switch, the server address and a daily read budget in
    App Settings; per-user Disconnect and Block in User Details.
  - New Firestore rules: `mcp_pairing`, `mcp_blocks`,
    `users/{uid}/agent_connections`. Re-paste the rules in Firebase Console.

## [1.2.1] - 2026-11-18

### Changed
- **Release APKs are now signed with TiffinBox's permanent release key**
  (stored as GitHub secrets), instead of a new temporary debug key on every
  build. Its fingerprint is registered in Firebase, so **"Continue with
  Google" now works in the APK from the Releases page**, and future versions
  install as normal updates on top of this one.

### Upgrade note
- Coming from **v1.2.0**: uninstall v1.2.0 once before installing v1.2.1
  (v1.2.0 was signed with a temporary key, so Android won't update over it).
  From v1.2.1 onwards, updates install directly.

### Fixed
- Android CI no longer fails before the build starts (the SDK setup step
  tried to install a package Google has removed). PR checks run once per
  pull request instead of twice.

## [1.2.0] - 2026-11-18

First tagged release. Fixes from the tester feedback round.

### Fixed
- **Login:** the password field now has a show/hide (eye) button, also on
  Change Password.
- **Saved passwords:** email and password fields are tagged for autofill, and
  after a successful login or sign-up the phone offers to save the password,
  so it can be filled in next time.
- **Chef stays a chef on reopen:** if the role couldn't be read from the server
  at start-up (slow or no internet), the app opened the customer home. It now
  uses the role last seen on the phone.
- **Chef "Continue with Google":** a Google account already registered as a
  customer used to open the customer home without saying why. It now explains
  which role the account belongs to. A network error no longer treats an
  existing account as a brand-new sign-up.
- **Back button from Profile pages:** going back from Settings, Edit Profile
  and other screens opened from a tab now returns to that tab instead of Home.
  The system back button on any other tab goes to Home first.
- **Kitchen search** now searches all kitchens by default ("Nearby" is
  optional), because map/location features are not complete yet.
- **Chat button:** the always-on green dot is replaced by a red count of
  unread messages. Chat lists and the chef dashboard show unread counts too.
- **Buttons hidden behind the phone's navigation bar:** the bottom bars on
  Meal Details, Cart, Checkout and Chat, plus the login, sign-up, location,
  order tracking, review and chef dashboard screens, now stay above the
  system back/home buttons and the keyboard.
- The Notifications list now always shows the newest 100 items.

### Added
- **Order notifications on the phone:** customers get a notification when an
  order is placed, accepted, cooking, on the way and delivered. Chefs get one
  for each new order. This works while the app is open or in the background.
- **Chef order progress (no rider needed):** an accepted order now moves to
  "Active orders" on the chef dashboard. The chef takes it through
  Start cooking, Send out for delivery and Delivered. For cash-on-delivery
  orders the chef is reminded to collect the cash. Before this, an order got
  stuck once accepted.
- **Favourite food:** a heart on every dish (meal cards and Meal Details).
  Favourites has two tabs, **Food** and **Kitchens**. Favourites are now
  saved to the account and kept after the app restarts.
- **Release system:** version lives in `version.properties`, and a
  `CHANGELOG.md`. A GitHub Actions workflow builds the APK and publishes a
  GitHub Release for every `v*` tag. Another workflow builds and tests every
  pull request.

## [0.1.0]

Development builds before tagged releases (pull requests #1-#38).
