# ─────────────────────────────────────────────────────────────────────────────
# Donezy ProGuard rules
# Most defaults from proguard-android-optimize.txt cover Compose/Kotlin/AndroidX.
# Keep rules below for things R8 cannot infer from reflection/serialization.
# ─────────────────────────────────────────────────────────────────────────────

# Domain models — keep all fields, names, and annotations because BackupCodec
# encodes/decodes them with hand-written JSON keys that must match field names.
-keep class com.swarnkary.donezy.Hobby { *; }
-keep class com.swarnkary.donezy.HobbyLog { *; }
-keep class com.swarnkary.donezy.HobbyDetail { *; }
-keep class com.swarnkary.donezy.Recurrence { *; }
-keep class com.swarnkary.donezy.Recurrence$* { *; }
-keepclassmembers enum com.swarnkary.donezy.** { *; }

# AppWidgetProvider + receivers are referenced from the manifest only.
-keep class com.swarnkary.donezy.NextDueWidgetProvider { *; }
-keep class com.swarnkary.donezy.StreakWidgetProvider { *; }
-keep class com.swarnkary.donezy.ReminderReceiver { *; }
-keep class com.swarnkary.donezy.StreakRescueReceiver { *; }
-keep class com.swarnkary.donezy.BootReceiver { *; }
-keep class com.swarnkary.donezy.NotificationSoundPlayer { *; }

# Coil — pull in the right OkHttp/coroutines setup.
-keep class coil.** { *; }
-dontwarn coil.**

# Compose tooling/preview — already handled by AGP, but be explicit so debug-only
# previews don't get pruned even if someone enables minifyEnabled on debug.
-keep class androidx.compose.ui.tooling.** { *; }

# Crash-friendly stack traces in Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
