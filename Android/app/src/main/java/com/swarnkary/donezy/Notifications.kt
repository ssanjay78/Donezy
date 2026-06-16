package com.swarnkary.donezy

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// One channel per (sound × vibrate) combo. Channel settings are immutable after first
// create() on Android 8+, so flipping a switch in app settings has to *route* to a
// different channel rather than mutate the existing one. Channel IDs are versioned
// below so we can change sound/vibration without forcing a Clear Data.

private const val CHANNEL_STREAK       = "streak_rescue"
private const val EXTRA_HOBBY_ID       = "hobby_id"
private const val EXTRA_HOBBY_NAME     = "hobby_name"
private const val EXTRA_SNOOZE_AT      = "snooze_at"
private const val ACTION_STREAK_RESCUE = "com.swarnkary.donezy.STREAK_RESCUE"
const val ACTION_LOG_DONE              = "com.swarnkary.donezy.REMINDER_LOG_DONE"
const val ACTION_REMINDER_DISMISS      = "com.swarnkary.donezy.REMINDER_DISMISS"
const val ACTION_REMINDER_SNOOZE       = "com.swarnkary.donezy.REMINDER_SNOOZE"
private const val STREAK_REQUEST_CODE  = 0x57Ea_C0DE
// Per-hobby PendingIntent request codes derive from hobbyId.toInt() + a per-action offset
// so the three action buttons on one notification don't collide with each other or the
// content-tap intent (which uses the bare hobbyId.toInt()).
private const val RC_OFFSET_DONE    = 0x10_0000
private const val RC_OFFSET_SNOOZE  = 0x20_0000
private const val RC_OFFSET_DISMISS = 0x30_0000
// Two-pulse, ~1.4s total — long enough to register, short enough not to annoy.
private val VIBRATION_PATTERN = longArrayOf(0, 350, 250, 350, 200, 350)

/**
 * Channel IDs are versioned. Bumping the version invalidates older channels (whose
 * sound/vibrate config is immutable) and forces a clean recreate. Bump this whenever
 * we materially change the channel definition (sound, vibration pattern, importance).
 */
private const val CHANNEL_VERSION = "v3"

private fun reminderChannelId(sound: Boolean, vibrate: Boolean): String =
    "hobby_reminders_${CHANNEL_VERSION}_" +
        (if (sound) "s" else "ns") + "_" + (if (vibrate) "v" else "nv")

fun ensureNotificationChannel(context: Context) {
    // minSdk = 26 (O) — channels always required.
    val mgr = context.getSystemService(NotificationManager::class.java)

    // Drop any reminder channels from older versions so the user gets the latest sound/
    // vibration settings without having to clear app data.
    mgr.notificationChannels
        .filter { it.id.startsWith("hobby_reminders_") && !it.id.contains(CHANNEL_VERSION) }
        .forEach { mgr.deleteNotificationChannel(it.id) }

    listOf(true to true, true to false, false to true, false to false).forEach { (sound, vibrate) ->
        val id = reminderChannelId(sound, vibrate)
        if (mgr.getNotificationChannel(id) != null) return@forEach
        val name = "Reminders" + if (!sound && !vibrate) " (silent)" else ""
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tracker reminders"
            enableVibration(vibrate)
            if (vibrate) vibrationPattern = VIBRATION_PATTERN
            if (sound) {
                val attrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
            } else {
                setSound(null, null)
            }
        }
        mgr.createNotificationChannel(channel)
    }

    if (mgr.getNotificationChannel(CHANNEL_STREAK) == null) {
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAK, "Streak rescue", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "End-of-day nudge to keep your streak alive" }
        )
    }
}

// ─── Reminder alarm scheduling ────────────────────────────────────────────────

fun scheduleReminder(context: Context, hobbyId: Long, hobbyName: String, triggerAt: Long) {
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra(EXTRA_HOBBY_ID, hobbyId)
        putExtra(EXTRA_HOBBY_NAME, hobbyName)
    }
    val pending = PendingIntent.getBroadcast(
        context, hobbyId.toInt(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val mgr = context.getSystemService(AlarmManager::class.java)
    val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mgr.canScheduleExactAlarms() else true
    try {
        if (canExact) {
            mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    } catch (_: SecurityException) {
        // Some OEMs revoke SCHEDULE_EXACT_ALARM dynamically. Fall back to inexact rather than crash.
        mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
}

fun cancelReminder(context: Context, hobbyId: Long) {
    val intent = Intent(context, ReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
        context, hobbyId.toInt(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    context.getSystemService(AlarmManager::class.java).cancel(pending)
}

fun nextOccurrence(recurrence: Recurrence, from: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (recurrence is Recurrence.None) return null
    val fromZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone)
    return when (recurrence) {
        is Recurrence.Hourly  -> from + recurrence.hours * HOUR_MS
        Recurrence.Daily      -> fromZdt.plusDays(1).toInstant().toEpochMilli()
        is Recurrence.Weekly  -> nextWeeklyMatch(fromZdt, recurrence.dayMask, zone)
        is Recurrence.Monthly -> nextMonthlyMatch(fromZdt, recurrence.dayOfMonth)
        Recurrence.None       -> null
    }
}

private fun nextWeeklyMatch(from: ZonedDateTime, dayMask: Int, zone: ZoneId): Long {
    if (dayMask == 0) return from.plusDays(7).toInstant().toEpochMilli()
    val time = from.toLocalTime()
    var probe = from.plusDays(1).toLocalDate()
    repeat(7) {
        if (dayMask and (1 shl probe.dayOfWeek.bitIndex()) != 0) {
            return ZonedDateTime.of(probe, time, zone).toInstant().toEpochMilli()
        }
        probe = probe.plusDays(1)
    }
    return from.plusDays(7).toInstant().toEpochMilli()
}

private fun DayOfWeek.bitIndex(): Int = (value - 1) // Mon=0 … Sun=6

private fun nextMonthlyMatch(from: ZonedDateTime, dayOfMonth: Int): Long {
    val nextMonth = from.plusMonths(1)
    val maxDay = nextMonth.toLocalDate().lengthOfMonth()
    val day = dayOfMonth.coerceAtMost(maxDay)
    val target = LocalDateTime.of(nextMonth.year, nextMonth.month, day, from.hour, from.minute)
    return target.atZone(from.zone).toInstant().toEpochMilli()
}

// ─── Reminder receiver ────────────────────────────────────────────────────────

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureNotificationChannel(context)

        val hobbyId   = intent.getLongExtra(EXTRA_HOBBY_ID, 0L)
        val hobbyName = intent.getStringExtra(EXTRA_HOBBY_NAME).orEmpty().ifBlank { "your hobby" }

        when (intent.action) {
            ACTION_LOG_DONE         -> { handleDone(context, hobbyId); return }
            ACTION_REMINDER_DISMISS -> { dismiss(context, hobbyId); return }
            ACTION_REMINDER_SNOOZE  -> {
                val snoozeAt = intent.getLongExtra(EXTRA_SNOOZE_AT, 0L)
                if (snoozeAt > 0L) handleSnooze(context, hobbyId, hobbyName, snoozeAt)
                return
            }
        }

        // Default action (null): the scheduled alarm fired → show the reminder and re-arm.
        showReminderNotification(context, hobbyId, hobbyName)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ServiceLocator.repository(context)
                val hobby = repository.hobbyByIdSync(hobbyId)
                val next = hobby?.let { nextOccurrence(it.recurrence, System.currentTimeMillis()) }
                if (next != null) {
                    repository.updateReminderSync(hobbyId, next)
                    scheduleReminder(context, hobbyId, hobbyName, next)
                } else {
                    repository.updateReminderSync(hobbyId, null)
                }
                NextDueWidgetProvider.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** "Done" action: log from the shade, advance the recurrence, dismiss the notification. */
    private fun handleDone(context: Context, hobbyId: Long) {
        dismiss(context, hobbyId)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.repository(context)
                repo.addLogSync(hobbyId, "Logged from reminder")
                val hobby = repo.hobbyByIdSync(hobbyId)
                if (hobby != null) {
                    val next = nextOccurrence(hobby.recurrence, System.currentTimeMillis())
                    if (next != null) {
                        repo.updateReminderSync(hobbyId, next)
                        scheduleReminder(context, hobbyId, hobby.name, next)
                    } else {
                        repo.updateReminderSync(hobbyId, null)
                    }
                }
                NextDueWidgetProvider.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** "Snooze" commit (from SnoozeActivity): re-arm this reminder at the chosen time. */
    private fun handleSnooze(context: Context, hobbyId: Long, hobbyName: String, snoozeAt: Long) {
        dismiss(context, hobbyId)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.repository(context)
                val name = repo.hobbyByIdSync(hobbyId)?.name ?: hobbyName
                repo.updateReminderSync(hobbyId, snoozeAt)
                scheduleReminder(context, hobbyId, name, snoozeAt)
                NextDueWidgetProvider.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dismiss(context: Context, hobbyId: Long) {
        NotificationManagerCompat.from(context).cancel(hobbyId.toInt())
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun showReminderNotification(context: Context, hobbyId: Long, hobbyName: String) {
        // hasNotificationPermission() does the runtime check that lint asks for; the
        // suppress is needed because the helper hides that check from static analysis.
        if (!hasNotificationPermission(context)) return
        val settings = ServiceLocator.settingsPreferences(context)
        val sound    = settings.soundEnabled.value
        val vibrate  = settings.vibrateEnabled.value

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_HOBBY_ID, hobbyId)
        }
        val contentIntent = PendingIntent.getActivity(
            context, hobbyId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Action buttons ──────────────────────────────────────────────────────
        // Done: log straight from the shade via the receiver (no UI).
        val donePending = PendingIntent.getBroadcast(
            context, hobbyId.toInt() + RC_OFFSET_DONE,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_LOG_DONE
                putExtra(EXTRA_HOBBY_ID, hobbyId)
                putExtra(EXTRA_HOBBY_NAME, hobbyName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Snooze: opens a small translucent picker activity to choose the interval.
        val snoozePending = PendingIntent.getActivity(
            context, hobbyId.toInt() + RC_OFFSET_SNOOZE,
            Intent(context, SnoozeActivity::class.java).apply {
                action = ACTION_REMINDER_SNOOZE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_HOBBY_ID, hobbyId)
                putExtra(EXTRA_HOBBY_NAME, hobbyName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Dismiss: just clears the notification (receiver cancels it).
        val dismissPending = PendingIntent.getBroadcast(
            context, hobbyId.toInt() + RC_OFFSET_DISMISS,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER_DISMISS
                putExtra(EXTRA_HOBBY_ID, hobbyId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, reminderChannelId(sound, vibrate))
            .setSmallIcon(R.drawable.ic_notification_check)
            .setLargeIcon(launcherIconBitmap(context))
            .setColor(0xFF1A6B48.toInt())
            .setContentTitle(hobbyName)
            .setContentText("Tap to log progress.")
            .setContentIntent(contentIntent)
            // Tap dismisses; otherwise it stays in the shade for the user to action later.
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .addAction(R.drawable.ic_notification_check, "Done", donePending)
            .addAction(R.drawable.ic_notification_snooze, "Snooze", snoozePending)
            .addAction(R.drawable.ic_notification_dismiss, "Dismiss", dismissPending)

        // minSdk = 26 means channel settings always apply, no pre-O builder fallback.
        NotificationManagerCompat.from(context).notify(hobbyId.toInt(), builder.build())
    }
}

// ─── Boot receiver ────────────────────────────────────────────────────────────

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        ensureNotificationChannel(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.repository(context)
                val now = System.currentTimeMillis()
                for (h in repo.activeHobbiesWithReminderSync()) {
                    val triggerAt = h.nextReminderAt ?: continue
                    val effective = if (triggerAt < now) now + 60_000L else triggerAt
                    if (effective != triggerAt) repo.updateReminderSync(h.id, effective)
                    scheduleReminder(context, h.id, h.name, effective)
                }
                if (ServiceLocator.settingsPreferences(context).streakRescueEnabled.value) {
                    StreakRescueScheduler.scheduleNext(context)
                }
                NextDueWidgetProvider.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

// ─── Streak rescue ────────────────────────────────────────────────────────────

object StreakRescueScheduler {

    fun scheduleNext(context: Context) {
        val now = ZonedDateTime.now()
        var target = now.with(LocalTime.of(23, 30))
        if (!target.isAfter(now)) target = target.plusDays(1)
        val triggerAt = target.toInstant().toEpochMilli()

        val intent = Intent(context, StreakRescueReceiver::class.java).apply {
            action = ACTION_STREAK_RESCUE
        }
        val pending = PendingIntent.getBroadcast(
            context, STREAK_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun cancel(context: Context) {
        val intent = Intent(context, StreakRescueReceiver::class.java).apply { action = ACTION_STREAK_RESCUE }
        val pending = PendingIntent.getBroadcast(
            context, STREAK_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }
}

class StreakRescueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ensureNotificationChannel(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ServiceLocator.settingsPreferences(context)
                if (hasNotificationPermission(context) && settings.streakRescueEnabled.value) {
                    val repo = ServiceLocator.repository(context)
                    val today = LocalDate.now()
                    val candidates = repo.allHobbiesSync()
                        .filter { !it.isArchived }
                        .mapNotNull { hobby ->
                            val logs = repo.detail(hobby.id)?.logs ?: return@mapNotNull null
                            val streak = HobbyViewModel.computeStreak(logs)
                            val loggedToday = logs.any {
                                java.time.Instant.ofEpochMilli(it.createdAt)
                                    .atZone(ZoneId.systemDefault()).toLocalDate() == today
                            }
                            if (streak >= 2 && !loggedToday) hobby to streak else null
                        }
                    if (candidates.isNotEmpty()) {
                        val sample = candidates.maxBy { it.second }
                        showStreakRescue(context, sample.first, sample.second, candidates.size)
                    }
                }
            } finally {
                if (ServiceLocator.settingsPreferences(context).streakRescueEnabled.value) {
                    StreakRescueScheduler.scheduleNext(context)
                }
                pendingResult.finish()
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun showStreakRescue(context: Context, hobby: Hobby, streak: Int, total: Int) {
        // The caller checks hasNotificationPermission() before invoking this method.
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_HOBBY_ID, hobby.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context, STREAK_REQUEST_CODE, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = "🔥 Don't lose your $streak-day streak"
        val body = if (total > 1)
            "${hobby.name} and ${total - 1} other tracker${if (total - 1 == 1) "" else "s"} need a log today."
        else "Open Donezy and add a quick log for ${hobby.name}."

        val notification = NotificationCompat.Builder(context, CHANNEL_STREAK)
            .setSmallIcon(R.drawable.ic_notification_check)
            .setLargeIcon(launcherIconBitmap(context))
            .setColor(0xFF1A6B48.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(STREAK_REQUEST_CODE, notification)
    }
}

// ─── Snooze ─────────────────────────────────────────────────────────────────

/** A single snooze choice: a human label and the absolute epoch-millis to re-fire at. */
data class SnoozeOption(val label: String, val triggerAt: Long)

/**
 * Build the snooze interval choices shown when the user taps "Snooze". Times are absolute
 * so they survive the activity round-trip. Fixed-time options (this evening / tomorrow
 * morning) roll forward to the next valid occurrence when the moment has already passed.
 */
fun snoozeOptions(now: ZonedDateTime = ZonedDateTime.now()): List<SnoozeOption> {
    fun atTimeFrom(base: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val candidate = base.with(time)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }
    val evening  = atTimeFrom(now, LocalTime.of(18, 0))
    val tomorrow = atTimeFrom(now.plusDays(1), LocalTime.of(9, 0))
    return listOf(
        SnoozeOption("1 hour",                now.plusHours(1).toEpoch()),
        SnoozeOption("3 hours",               now.plusHours(3).toEpoch()),
        SnoozeOption("This evening (6:00 PM)", evening.toEpoch()),
        SnoozeOption("Tomorrow (9:00 AM)",     tomorrow.toEpoch()),
    )
}

private fun ZonedDateTime.toEpoch(): Long = toInstant().toEpochMilli()

/** Build the broadcast that commits a chosen snooze; used by [SnoozeActivity]. */
fun snoozeCommitIntent(context: Context, hobbyId: Long, hobbyName: String, triggerAt: Long): Intent =
    Intent(context, ReminderReceiver::class.java).apply {
        action = ACTION_REMINDER_SNOOZE
        putExtra(EXTRA_HOBBY_ID, hobbyId)
        putExtra(EXTRA_HOBBY_NAME, hobbyName)
        putExtra(EXTRA_SNOOZE_AT, triggerAt)
    }

/** Read the hobby id/name a [SnoozeActivity] was launched with. */
fun Intent.snoozeHobbyId(): Long = getLongExtra(EXTRA_HOBBY_ID, 0L)
fun Intent.snoozeHobbyName(): String =
    getStringExtra(EXTRA_HOBBY_NAME).orEmpty().ifBlank { "your tracker" }

/** Cancel the reminder notification for [hobbyId] (e.g. after the snooze picker dismisses it). */
fun cancelReminderNotification(context: Context, hobbyId: Long) {
    NotificationManagerCompat.from(context).cancel(hobbyId.toInt())
}

fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

/**
 * Rasterize the launcher icon (mipmap/ic_launcher) so it can be used as a notification's
 * large icon. Adaptive icons need explicit drawing because Bitmap.createBitmap() rejects
 * 0x0 intrinsic dimensions. Cached after the first call.
 */
@Volatile private var cachedLauncherBitmap: Bitmap? = null

fun launcherIconBitmap(context: Context): Bitmap? {
    cachedLauncherBitmap?.let { return it }
    val drawable: Drawable = ResourcesCompat.getDrawable(
        context.resources, R.mipmap.ic_launcher, context.theme
    ) ?: return null

    val size = (96 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    cachedLauncherBitmap = bitmap
    return bitmap
}
