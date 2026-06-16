# Testing & distributing Donezy (iOS)

Unlike Android, iOS has **no universal installer file** you can hand to any phone — every
real-device install goes through Apple's signing system. This guide covers the **free**
ways to run and test Donezy, plus the paid routes for sharing it.

---

## ✅ Recommended (free, no account, full app + widget): iOS Simulator

The Simulator runs the **complete app and the home-screen widget** with no Apple Developer
account, no physical device, and no 7-day expiry. App Groups work in the Simulator, so the
app↔widget shared database behaves exactly like production. This is the best free way to
test everything.

### Steps

1. **Install Xcode** (free) from the Mac App Store, then open it once to let it install
   the iOS platform components.

2. **Open the project:**
   ```bash
   open /Users/sanjason/Projects/Donezy/MacOs/Donezy.xcodeproj
   ```

3. **Pick a simulator** in the run-destination dropdown at the top (e.g. *iPhone 16 Pro*).

4. **Run the app:** press **⌘R** (or the ▶ button). The app builds and launches in the
   Simulator.

### Testing the widget in the Simulator

1. With the app running once (so it has created some trackers/data), press the Simulator's
   **Home** gesture (⇧⌘H) to go to the home screen.
2. Long-press an empty area → tap **Edit** → **+** (top-left) → search **Donezy** → add the
   **Next due** widget.
3. The widget reads the shared database and shows the next-due tracker. Tapping **✓ Logged**
   logs from the widget; tapping the widget body deep-links into the app.

### Testing notifications in the Simulator

1. When the app first launches, allow notifications at the prompt.
2. Create a tracker with a reminder a minute or two out, then **background the app**
   (⇧⌘H) — the local notification fires on schedule.
3. The notification carries the Donezy icon; tapping it opens the tracker, and the
   **Done / Snooze 1h / Dismiss** actions work from the banner.
   *(You can also drag-and-drop a `.apns` payload file onto the Simulator window to
   simulate a delivery instantly, but local reminders fire on their own.)*

---

## Free, on a *physical* iPhone — with one caveat

You can install on your own iPhone for free using a **Personal Team** (any Apple ID, no
$99 program), but **a free Apple ID cannot sign the App Group entitlement** that the
widget uses. So on a free account the **widget target must be disabled** — the main app
still runs fully. (Streaks, reminders, achievements, backup, etc. all work; only the
home-screen widget is unavailable.)

### Steps

1. Connect your iPhone via cable and trust the Mac.
2. In Xcode: **Xcode → Settings → Accounts → +** and add your Apple ID.
3. Select the **Donezy** target → **Signing & Capabilities** → check *Automatically manage
   signing* → choose your **Personal Team**.
4. **Disable the widget** for the free build (it can't be signed without App Groups):
   - In the scheme/target list, or in the Donezy target's **Frameworks, Libraries, and
     Embedded Content**, remove **DonezyWidget.appex**, *or* simply delete the
     `com.apple.security.application-groups` entitlement from **both** targets. The app then
     falls back to its own Documents directory automatically (see `Shared/AppGroup.swift`).
5. Select your iPhone as the run destination and press **⌘R**.
6. On the phone: **Settings → General → VPN & Device Management → trust** your developer
   certificate.

**Free-account limits:** the app **expires after ~7 days** (re-run from Xcode to renew),
installs only on devices you physically connect, and one free Apple ID can hold ~3
side-loaded apps.

---

## Free-ish sideload without Xcode each time: AltStore Classic

[AltStore](https://altstore.io) sideloads a `.ipa` using your free Apple ID.

- **Free** (donation-optional).
- Requires **AltServer** running on a Mac/PC on the **same Wi-Fi** to re-sign.
- Apps **expire every 7 days** and must be refreshed (AltStore can auto-refresh in the
  background while AltServer is reachable).
- **Max 3 sideloaded apps**, and the **same free-account limit applies — no App Groups, so
  the widget won't work**. Build the app target as a dev `.ipa` (widget removed) and add it
  to AltStore.

---

## Sharing with other people (paid — $99/year Apple Developer Program)

There is **no free way** to put the app (especially *with* the widget) on someone else's
iPhone. All of these need the paid program, and the widget works on all of them because a
paid account can sign App Groups:

| Route | Who can install | Notes |
|-------|-----------------|-------|
| **TestFlight** | Up to 10,000 testers via email or a public link | Closest to "share with anyone." Upload one build; testers use Apple's TestFlight app. Lightweight review. |
| **App Store** | Anyone, worldwide | Full App Review. |
| **Ad Hoc `.ipa`** | Only devices whose UDIDs you registered (max 100/yr) | The nearest thing to an `.apk`, but locked to pre-registered devices — not universal. |

### Producing a build for TestFlight / App Store (requires Xcode)

1. Set your paid **Team** on both targets under *Signing & Capabilities*.
2. Select **Any iOS Device (arm64)** as the destination.
3. **Product → Archive**.
4. In the Organizer: **Distribute App → App Store Connect** (TestFlight/App Store) or
   **Ad Hoc** for a `.ipa`.
5. Bump `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` (in `gen_pbxproj.py` or Xcode) for
   each new upload.

---

## TL;DR

- **Just want to see it work, free, including the widget → iOS Simulator.** ✅
- **On your own phone, free → Personal Team in Xcode, widget disabled** (Apple won't sign
  App Groups for free accounts).
- **Share with others / ship the widget on real devices → paid program + TestFlight.**
- **A single universal installer for "any iPhone" does not exist on iOS** — it's an Apple
  platform rule, not a limitation of this project.
