package com.example.hobbylog

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val CHANNEL_ID      = "hobby_reminders"
private const val EXTRA_HOBBY_ID   = "hobby_id"
private const val EXTRA_HOBBY_NAME = "hobby_name"
private const val HOUR_MS          = 60L * 60L * 1000L

fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hobby reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Care, maintenance, and logging reminders"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

fun scheduleReminder(context: Context, hobbyId: Long, hobbyName: String, triggerAt: Long) {
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra(EXTRA_HOBBY_ID, hobbyId)
        putExtra(EXTRA_HOBBY_NAME, hobbyName)
    }
    val pending = PendingIntent.getBroadcast(
        context,
        hobbyId.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    context.getSystemService(AlarmManager::class.java)
        .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureNotificationChannel(context)

        val hobbyId   = intent.getLongExtra(EXTRA_HOBBY_ID, 0L)
        val hobbyName = intent.getStringExtra(EXTRA_HOBBY_NAME).orEmpty().ifBlank { "your hobby" }

        // Show the notification (needs permission check on API 33+)
        showNotification(context, hobbyId, hobbyName)

        // Auto-reschedule on a background thread (BroadcastReceiver is on main thread)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db         = AppDatabase(context.applicationContext)
                val repository = HobbyRepository(db)
                val interval   = repository.reminderIntervalHoursSync(hobbyId)
                if (interval > 0L) {
                    val next = System.currentTimeMillis() + interval * HOUR_MS
                    repository.updateReminderSync(hobbyId, next)
                    scheduleReminder(context, hobbyId, hobbyName, next)
                } else {
                    // One-shot: clear the reminder timestamp so the card shows "Not scheduled"
                    repository.updateReminderSync(hobbyId, null)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, hobbyId: Long, hobbyName: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            hobbyId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Time to check in — $hobbyName")
            .setContentText("Open HobbyLog and add your latest note.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(hobbyId.toInt(), notification)
    }
}
