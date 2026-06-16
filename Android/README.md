# Donezy

Donezy is a local-first Android habit and routine tracker. Plan it, do it, done it.

Published by Swarnkary (`swarnkary.com`).

## Features

- Trackers with categories, notes, photos, ratings, and weekly goals
- Reminders with one-shot, hourly, daily, weekly, or monthly recurrence
- Heads-up notifications, deep-linking back to the matching tracker
- Streaks, achievements, and end-of-day streak rescue nudge
- Insights chart (30-day scrollable) and full log history
- 1×1 home-screen widget for the next-due tracker
- JSON backup / restore via Storage Access Framework
- Dark / light / system theme
- All data is stored locally in the app's private sandbox; no network calls, no analytics, no ads

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for the full data policy.

## Build & run (debug)

Requires JDK 17 and the Android SDK with API 35.

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# or, faster:
./gradlew installDebug
```

Multiple devices connected:

```bash
adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
```

## Run unit tests

```bash
./gradlew testDebugUnitTest
```

## Release build (Play Store submission)

1. Generate a release keystore once:

   ```bash
   keytool -genkey -v -keystore donezy-release.jks \
           -keyalg RSA -keysize 2048 -validity 10000 -alias donezy
   ```

2. Copy `keystore.properties.example` to `keystore.properties` (gitignored) and
   fill in the four values. Keep the `.jks` file backed up safely — losing it
   means you can never publish updates without involving Play support.

3. Build the AAB:

   ```bash
   ./gradlew bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`.

4. Verify locally with [bundletool](https://developer.android.com/tools/bundletool):

   ```bash
   bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
       --output=donezy.apks --connected-device
   bundletool install-apks --apks=donezy.apks
   ```

5. Upload the AAB to the Play Console **Internal testing** track first, exercise
   every feature, then promote to closed testing and finally production.

## Play Store submission notes

The repo is configured for Play Console upload:

- Package: `com.swarnkary.donezy`
- `targetSdk = 35` (current Play requirement)
- Release build is minified (R8) with shrinking and explicit ProGuard rules
- Manifest declares `USE_EXACT_ALARM` (auto-granted on API 33+ for task-reminder
  apps); `SCHEDULE_EXACT_ALARM` is capped at API 32 to avoid Play's sensitive
  permission justification flow on newer APIs
- Backup rules exclude the database, photos, and shared prefs from Google Drive
  cloud backup; users must use the in-app Backup feature instead

For the Play Console "Data safety" form, declare:

- *Does your app collect or share user data?* — **No**
- *Encrypts data in transit?* — N/A (no network)
- *Allows users to request deletion?* — Yes (in-app delete + uninstall)

Privacy policy URL for the listing: `https://swarnkary.com/donezy/privacy`
(host the contents of [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at this URL before
submitting).

## Stack

- Kotlin · Jetpack Compose Material3 · Coroutines
- SQLite via `SQLiteOpenHelper` (no Room dependency)
- Coil for image loading
- AndroidX `core-ktx`, `activity-compose`, `lifecycle-viewmodel-compose`
