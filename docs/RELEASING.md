# Releasing TiffinBox

Every release is a git tag like `v1.2.0`. Pushing the tag makes GitHub
Actions (`.github/workflows/release.yml`) run the tests, build the APK and
publish it on the repo's **Releases** page, with notes taken from
`CHANGELOG.md`.

## Version numbers

`MAJOR.MINOR.PATCH`, kept in one place: [`version.properties`](../version.properties).

| Change | Bump | Example |
|---|---|---|
| Only bug fixes | PATCH | 1.2.0 → 1.2.1 |
| New features, nothing broken | MINOR | 1.2.1 → 1.3.0 |
| Big redesign / breaking change | MAJOR | 1.3.0 → 2.0.0 |

Android's `versionCode` is calculated from it (`major*10000 + minor*100 + patch`,
so 1.2.0 → 10200). Never lower the version: Android refuses to update an
installed app to a smaller `versionCode`.

## Cutting a release

1. On your feature branch, set the new `VERSION_NAME` in `version.properties`.
2. Add a `## [x.y.z] - date` section at the top of `CHANGELOG.md`.
3. Open a pull request. After the admin merges it into `main`:

```bash
git checkout main
git pull
git tag v1.2.0
git push origin v1.2.0
```

The tag must match `version.properties` exactly, or the workflow stops.

## One-time setup: repository secrets

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Required | What it is |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | Yes | The full text of `app/google-services.json` (Firebase Console → Project settings → Android app). It isn't in the repo on purpose. |
| `TIFFINBOX_KEYSTORE_BASE64` | No | The release keystore file, base64-encoded (`base64 -w0 release.jks`). |
| `TIFFINBOX_KEYSTORE_PASSWORD` | With keystore | The keystore password. |
| `TIFFINBOX_KEY_ALIAS` | With keystore | The key alias inside it. |
| `TIFFINBOX_KEY_PASSWORD` | With keystore | The key password. |

Without the keystore secrets the release still works. It ships the
debug-signed APK, which is fine for testers. Once you publish on the Play
Store, add a real keystore and **keep it safe**: losing it means you can't
update the app. Also register that keystore's SHA-1 in Firebase Console, or
Google Sign-In won't work in release builds.

## Pull request checks

`.github/workflows/android-ci.yml` builds the app and runs the unit tests on
every pull request and branch push. It needs no secrets.
