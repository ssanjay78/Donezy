package com.swarnkary.donezy

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Safety net for reminder delivery.
 *
 * Exact alarms can be delayed or dropped entirely when the app is closed — Doze, OEM battery
 * managers, or a denied SCHEDULE_EXACT_ALARM permission on API 33+ all cause this. WorkManager
 * survives process death and reboots, so a periodic pass that re-arms anything overdue (and
 * fires the notification immediately for already-missed reminders) keeps reminders honest even
 * when the precise alarm never ran.
 */
class ReminderReconcileWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val context = applicationContext
            ensureNotificationChannel(context)
            val repo = ServiceLocator.repository(context)
            val now = System.currentTimeMillis()

            for (h in repo.activeHobbiesWithReminderSync()) {
                val triggerAt = h.nextReminderAt ?: continue
                if (triggerAt <= now) {
                    // This reminder's moment has already passed and no alarm fired it.
                    // Deliver it now via the receiver, which also advances the recurrence.
                    val intent = android.content.Intent(context, ReminderReceiver::class.java).apply {
                        putExtra("hobby_id", h.id)
                        putExtra("hobby_name", h.name)
                    }
                    context.sendBroadcast(intent)
                } else {
                    // Still in the future — make sure an alarm is actually armed for it.
                    scheduleReminder(context, h.id, h.name, triggerAt)
                }
            }
        } catch (_: Throwable) {
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "reminder_reconcile"

        /** Schedule the periodic reconciliation pass (idempotent — keeps the existing one). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderReconcileWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(Constraints.Builder().build()).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
