# Donezy — Privacy Policy

**Effective date:** 2026-06-13
**Publisher:** Swarnkary (swarnkary.com)
**App:** Donezy (Android, Google Play)
**Contact:** privacy@swarnkary.com

## Summary in one paragraph

Donezy is a local-first habit and routine tracker. It does not collect, transmit,
sell, or share any personal data. Everything you create — trackers, log entries,
photos, reminders, theme preferences, achievements — stays inside the app's
private storage on your device. There is no server, no analytics SDK, no advertising
SDK, no crash-reporting service, and no required account.

## What data the app stores on your device

- **Trackers** (name, category, notes, optional reminder time, recurrence rule, weekly goal)
- **Log entries** (timestamp, text, optional rating, optional photo)
- **Photos** you attach to a log, copied into the app's private files directory
- **App preferences** (theme, sound, vibrate, streak-rescue toggle)

This data is held in:
- A SQLite database (`hobby_log.db`) inside the app's private data directory
- Photos in the app's private `photos/` subdirectory
- A SharedPreferences XML file for settings

All locations are sandboxed: only Donezy can read them. They are excluded from
Google's automatic Drive backup (see `backup_rules.xml` and `data_extraction_rules.xml`)
unless you explicitly export a JSON backup yourself.

## What data the app sends off-device

**None.** The app does not declare the `INTERNET` permission and makes no network
requests.

## Permissions and why they are used

| Permission | Purpose |
|---|---|
| `POST_NOTIFICATIONS` | Show reminder notifications you have explicitly scheduled |
| `RECEIVE_BOOT_COMPLETED` | Restore your scheduled reminders after a phone restart |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` (API 32-) | Fire reminders at the exact minute you chose |
| `VIBRATE` | Vibrate (configurable in Settings) when a reminder fires |
| Photo picker (no permission needed) | Attach a photo to a log entry — we use Android's scoped photo picker |

None of these are used for tracking, advertising, or analytics.

## Data sharing

The app does not share data with third parties. The Backup feature lets *you*
share a JSON file via Android's standard share sheet — only you decide where it
goes (Drive, email, local file, etc.). Donezy itself never initiates any transfer.

## Data deletion

- **Per item:** delete a tracker or log entry from inside the app at any time
- **Whole archive:** Settings or Home → tap a tracker → menu → Delete
- **Everything:** uninstall the app — all local data is removed by Android

You do not need to contact us to delete your data.

## Children's privacy

The app does not knowingly collect any data from anyone, including children. No
account creation, no advertising, no analytics, no third-party SDKs.

## Security

All data is stored in Android's app-private sandbox, which the OS protects from
other apps and from non-rooted users. We do not transmit data, so transit
encryption is not applicable.

## Changes to this policy

If we update this policy, we will publish the new version at
`swarnkary.com/donezy/privacy` and bump the *Effective date* above. Donezy will
continue to operate without an account, so we will not contact you about
policy updates — please re-read the page if you are concerned.

## Contact

privacy@swarnkary.com
