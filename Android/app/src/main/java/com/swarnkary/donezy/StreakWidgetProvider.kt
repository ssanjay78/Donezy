package com.swarnkary.donezy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2x1 home-screen widget: shows the best active streak + tracker name.
 * Tapping the widget opens the app.
 */
class StreakWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in ids) renderWidgetSync(context, mgr, id)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) { renderAllSync(context) }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "com.swarnkary.donezy.WIDGET_STREAK_REFRESH"

        fun refreshAll(context: Context) {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) { renderAllSync(context) }
        }

        private fun renderAllSync(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, StreakWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) renderWidgetSync(context, mgr, id)
        }

        private fun renderWidgetSync(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val repo = ServiceLocator.repository(context)
            val hobbies = repo.allHobbiesSync().filter { !it.isArchived }
            val logs = repo.allLogsSync()
            val views = RemoteViews(context.packageName, R.layout.widget_streak)

            val zone = ZoneId.systemDefault()
            val logsByHobby = logs.groupBy { it.hobbyId }

            // Compute best streak
            val best = hobbies.map { hobby ->
                val hobbyLogs = logsByHobby[hobby.id] ?: emptyList()
                val daySet = hobbyLogs.map { log ->
                    Instant.ofEpochMilli(log.createdAt)
                        .atZone(zone)
                        .toLocalDate()
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                }.toHashSet()

                var streak = 0
                val check = LocalDate.now(zone)
                val todayMs = check.atStartOfDay(zone).toInstant().toEpochMilli()
                val yesterdayMs = check.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                if (daySet.contains(todayMs) || daySet.contains(yesterdayMs)) {
                    var currentCheck = if (daySet.contains(todayMs)) check else check.minusDays(1)
                    while (daySet.contains(currentCheck.atStartOfDay(zone).toInstant().toEpochMilli())) {
                        streak++
                        currentCheck = currentCheck.minusDays(1)
                    }
                }
                hobby to streak
            }.filter { it.second > 0 }.maxByOrNull { it.second }

            if (best == null) {
                views.setTextViewText(R.id.widget_streak_title, context.getString(R.string.widget_streak_default_title))
                views.setTextViewText(R.id.widget_streak_count, context.getString(R.string.widget_streak_default_subtitle))
            } else {
                views.setTextViewText(R.id.widget_streak_title, "Best streak: ${best.first.name}")
                views.setTextViewText(R.id.widget_streak_count, "🔥 ${best.second} day${if (best.second > 1) "s" else ""}")
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, widgetId + 0xB00, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
