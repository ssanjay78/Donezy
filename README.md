# HobbyLog

HobbyLog is a local-first Android starter app for small personal trackers: plant care, mechanical keyboards, sneaker collecting, aquarium maintenance, coffee brewing ratios, and similar hobbies.

## What is included

- Native Android app in Kotlin
- Jetpack Compose UI
- Local SQLite storage
- Hobby tracker creation
- Timestamped log entries
- Local reminder notifications
- Android 13+ notification permission request

## Data model

Each tracker has:

- Name
- Category
- Notes
- Optional next reminder time
- Log history

The app stores data on the device in `hobby_log.db`. There is no backend, account system, analytics, or cloud sync.

## Notifications

Reminders use Android local notifications scheduled with `AlarmManager`. These are not server push notifications through Firebase Cloud Messaging. That is intentional for this local-first version.

## Run it

Open this folder in Android Studio:

```text
outputs/HobbyLog
```

Then sync Gradle, choose an emulator or Android device, and run the `app` configuration.

## Good next upgrades

- Add edit/delete for trackers and logs
- Add custom reminder dates and recurring intervals
- Add photo attachments
- Add CSV export/import
- Migrate SQLite helper to Room once the schema grows
- Add optional Firebase/Supabase sync when multi-device support becomes important
