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
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
// Low-importance channel for the foreground "sound playback" service notification.
// IMPORTANCE_MIN keeps it out of the status bar and collapsed at the bottom of the shade.
private const val CHANNEL_PLAYBACK     = "sound_playback"
private const val EXTRA_HOBBY_ID       = "hobby_id"
private const val EXTRA_HOBBY_NAME     = "hobby_name"
private const val EXTRA_SNOOZE_AT      = "snooze_at"
private const val EXTRA_SOUND_URI      = "sound_uri"
private const val EXTRA_SOUND_DURATION = "sound_duration"
private const val ACTION_STREAK_RESCUE = "com.swarnkary.donezy.STREAK_RESCUE"
const val ACTION_LOG_DONE              = "com.swarnkary.donezy.REMINDER_LOG_DONE"
const val ACTION_REMINDER_DISMISS      = "com.swarnkary.donezy.REMINDER_DISMISS"
const val ACTION_REMINDER_SNOOZE       = "com.swarnkary.donezy.REMINDER_SNOOZE"
const val ACTION_STOP_SOUND            = "com.swarnkary.donezy.STOP_SOUND"
private const val STREAK_REQUEST_CODE  = 0x57Ea_C0DE
private const val SOUND_SERVICE_NOTIF_ID = 0x50_0001
// Per-hobby PendingIntent request codes derive from hobbyId.toInt() + a per-action offset
// so the three action buttons on one notification don't collide with each other or the
// content-tap intent (which uses the bare hobbyId.toInt()).
private const val RC_OFFSET_DONE    = 0x10_0000
private const val RC_OFFSET_SNOOZE  = 0x20_0000
private const val RC_OFFSET_DISMISS = 0x30_0000
private const val RC_OFFSET_SHOW    = 0x40_0000
// Two-pulse, ~1.4s total — long enough to register, short enough not to annoy.
private val VIBRATION_PATTERN = longArrayOf(0, 350, 250, 350, 200, 350)

/**
 * Channel IDs are versioned. Bumping the version invalidates older channels (whose
 * sound/vibrate config is immutable) and forces a clean recreate. Bump this whenever
 * we materially change the channel definition (sound, vibration pattern, importance).
 */
private const val CHANNEL_VERSION = "v4"

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
            // Always set channel sound to null, because we will handle sound manually via MediaPlayer
            // to support adjustable duration (1-30s) and custom local audio files.
            setSound(null, null)
        }
        mgr.createNotificationChannel(channel)
    }

    if (mgr.getNotificationChannel(CHANNEL_STREAK) == null) {
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAK, "Streak rescue", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "End-of-day nudge to keep your streak alive" }
        )
    }

    if (mgr.getNotificationChannel(CHANNEL_PLAYBACK) == null) {
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_PLAYBACK, "Reminder sound", NotificationManager.IMPORTANCE_MIN)
                .apply {
                    description = "Temporary notification while a reminder sound is playing"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
        )
    }
}

// ─── Reminder alarm scheduling ────────────────────────────────────────────────

fun scheduleReminder(context: Context, hobbyId: Long, hobbyName: String, triggerAt: Long) {
    val diff = triggerAt - System.currentTimeMillis()
    if (diff <= 65_000L) {
        if (diff <= 0L) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_HOBBY_ID, hobbyId)
                putExtra(EXTRA_HOBBY_NAME, hobbyName)
            }
            context.sendBroadcast(intent)
        } else {
            // Use handler for short delays up to 65 seconds to bypass exact alarm throttling entirely.
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra(EXTRA_HOBBY_ID, hobbyId)
                    putExtra(EXTRA_HOBBY_NAME, hobbyName)
                }
                context.sendBroadcast(intent)
            }, diff)
        }
        return
    }
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra(EXTRA_HOBBY_ID, hobbyId)
        putExtra(EXTRA_HOBBY_NAME, hobbyName)
    }
    val pending = PendingIntent.getBroadcast(
        context, hobbyId.toInt(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val mgr = context.getSystemService(AlarmManager::class.java)

    // We use setAlarmClock() as the PRIMARY path. Unlike setExactAndAllowWhileIdle() — which
    // Android still batches into Doze maintenance windows and can defer by tens of seconds
    // (the ~35s lag) — setAlarmClock() is delivered at the precise time even in Doze, and it
    // does NOT require the SCHEDULE_EXACT_ALARM permission. The trade-off (a visible "alarm
    // set" icon + an optional show-app intent) is acceptable for an on-time reminder.
    val showIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(EXTRA_HOBBY_ID, hobbyId)
    }
    val showPending = PendingIntent.getActivity(
        context, hobbyId.toInt() + RC_OFFSET_SHOW, showIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    try {
        mgr.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPending), pending)
    } catch (_: Exception) {
        // Extremely defensive fallback: if setAlarmClock is somehow unavailable, fire as
        // close to on-time as the platform allows.
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mgr.canScheduleExactAlarms() else true
        try {
            if (canExact) mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } catch (_: SecurityException) {
            mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
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

/**
 * Advance a recurrence from its previously-scheduled trigger ([scheduledAt]) rather than
 * from "now", so a late-firing alarm keeps its original time-of-day instead of drifting.
 * If the next computed occurrence is still in the past (e.g. the device was off for days),
 * keep advancing until it lands in the future so we never schedule an alarm in the past.
 */
fun advanceRecurrence(recurrence: Recurrence, scheduledAt: Long?): Long? {
    if (recurrence is Recurrence.None) return null
    val base = scheduledAt ?: System.currentTimeMillis()
    var next = nextOccurrence(recurrence, base) ?: return null
    val now = System.currentTimeMillis()
    var guard = 0
    while (next <= now && guard < 1000) {
        val advanced = nextOccurrence(recurrence, next) ?: return next
        if (advanced <= next) break // safety: no forward progress
        next = advanced
        guard++
    }
    return next
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

        // Stop custom sound playback on any user action
        if (intent.action != null) {
            SoundPlaybackService.stop(context)
        }

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
                // Advance from the *scheduled* time (not now) so a late-firing alarm doesn't
                // drift the recurrence later each cycle. If the resulting time is still in the
                // past (alarm fired very late / device was off), roll forward until it's future.
                val next = hobby?.let { advanceRecurrence(it.recurrence, it.nextReminderAt) }
                if (next != null) {
                    repository.updateReminderSync(hobbyId, next)
                    scheduleReminder(context, hobbyId, hobbyName, next)
                } else {
                    repository.updateReminderSync(hobbyId, null)
                }
                NextDueWidgetProvider.refreshAll(context)
                StreakWidgetProvider.refreshAll(context)
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
                StreakWidgetProvider.refreshAll(context)
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
                StreakWidgetProvider.refreshAll(context)
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

        if (sound) {
            val customSound = settings.customSoundUri.value
            val duration = settings.playbackDurationSeconds.value
            SoundPlaybackService.start(context, customSound, duration)
        }
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
                ReminderReconcileWorker.schedule(context)
                NextDueWidgetProvider.refreshAll(context)
                StreakWidgetProvider.refreshAll(context)
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
        val mgr = context.getSystemService(AlarmManager::class.java)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mgr.canScheduleExactAlarms() else true
        try {
            if (canExact) {
                mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val showPending = PendingIntent.getActivity(
                    context, STREAK_REQUEST_CODE + RC_OFFSET_SHOW, showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                mgr.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPending), pending)
            }
        } catch (_: SecurityException) {
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val showPending = PendingIntent.getActivity(
                context, STREAK_REQUEST_CODE + RC_OFFSET_SHOW, showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                mgr.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPending), pending)
            } catch (_: Exception) {
                mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
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
                    val candidates = repo.streakCandidatesSync(2)
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

/** True if the app is exempt from battery optimization (Doze won't defer its alarms). */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Open the system dialog asking the user to exempt Donezy from battery optimization, so Doze
 * and OEM battery managers stop delaying/dropping reminders while the app is closed. Falls back
 * to the app's settings screen if the direct request action isn't available.
 */
@android.annotation.SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(direct) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** True when the OS will honor exact alarms (always pre-S; gated by permission on S+). */
fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
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

/**
 * Plays the reminder sound from a short-lived foreground service.
 *
 * Why a service and not a bare MediaPlayer in the receiver: a BroadcastReceiver's process is
 * only guaranteed alive until onReceive() (plus goAsync) returns. A MediaPlayer started there
 * — together with its postDelayed stop — can be killed mid-playback the instant the receiver
 * finishes, especially when the app is closed. That produced silent/clipped reminders and a
 * "wrong duration". A foreground service keeps the process alive for the full playback window.
 *
 * The service also stops playback on screen-off (power-button press) while deliberately
 * leaving the reminder notification itself untouched in the shade.
 */
class SoundPlaybackService : android.app.Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }

    // Power button / screen off → stop the sound, but keep the reminder notification.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_SCREEN_OFF) stopSelf()
        }
    }
    private var screenReceiverRegistered = false

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SOUND) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately so the OS can't kill us mid-playback.
        startForeground(SOUND_SERVICE_NOTIF_ID, buildPlaybackNotification())

        if (!screenReceiverRegistered) {
            registerReceiver(screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF))
            screenReceiverRegistered = true
        }

        val soundUriStr = intent?.getStringExtra(EXTRA_SOUND_URI)
        val duration = (intent?.getIntExtra(EXTRA_SOUND_DURATION, 3) ?: 3).coerceIn(1, 30)
        startPlayback(soundUriStr, duration)
        return START_NOT_STICKY
    }

    private fun startPlayback(soundUriStr: String?, durationSeconds: Int) {
        handler.removeCallbacks(stopRunnable)
        releasePlayer()
        // Try the custom track first; fall back to the default notification sound so the user
        // always hears *something* rather than silence if the custom file can't be read.
        val customUri = soundUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (!playUri(customUri) && customUri != null) {
            playUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else if (customUri == null) {
            playUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        // Stop after the configured duration regardless of the track's natural length.
        handler.postDelayed(stopRunnable, durationSeconds * 1000L)
    }

    /** Returns true if playback started. */
    private fun playUri(uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
            mediaPlayer = MediaPlayer().apply {
                if (uri.scheme == "file") setDataSource(uri.path) else setDataSource(this@SoundPlaybackService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build()
                )
                // Loop so a short clip fills the requested duration; the timed stopRunnable
                // is the single source of truth for when playback ends.
                isLooping = true
                setOnErrorListener { _, _, _ -> stopSelf(); true }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            releasePlayer()
            false
        }
    }

    private fun buildPlaybackNotification() =
        NotificationCompat.Builder(this, CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification_check)
            .setContentTitle("Reminder")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun releasePlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenReceiverRegistered = false
        }
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, soundUri: String?, durationSeconds: Int) {
            val intent = Intent(context, SoundPlaybackService::class.java).apply {
                putExtra(EXTRA_SOUND_URI, soundUri)
                putExtra(EXTRA_SOUND_DURATION, durationSeconds)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // startForegroundService can throw if we're not allowed to start from background
                // on some OEMs; the reminder notification is already posted, so just swallow it.
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SoundPlaybackService::class.java).apply {
                action = ACTION_STOP_SOUND
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service not running / can't be started → nothing playing to stop.
            }
        }
    }
}
