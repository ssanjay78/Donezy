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

/**
 * 2x1 home-screen widget: shows the next-due tracker plus a one-tap "Logged"
 * action that calls the repository directly. Tapping the title opens the app.
 */
class NextDueWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // onUpdate is invoked through the broadcast lifecycle, so goAsync() is safe here.
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
        when (intent.action) {
            ACTION_QUICK_LOG -> {
                val hobbyId = intent.getLongExtra(EXTRA_HOBBY_ID, 0L)
                if (hobbyId > 0L) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repo = ServiceLocator.repository(context)
                            repo.addLogSync(hobbyId, "Logged from widget")
                            val h = repo.hobbyByIdSync(hobbyId)
                            if (h != null) {
                                val now = System.currentTimeMillis()
                                val next = nextOccurrence(h.recurrence, now)
                                if (next != null) {
                                    repo.updateReminderSync(hobbyId, next)
                                    scheduleReminder(context, hobbyId, h.name, next)
                                }
                            }
                            renderAllSync(context)
                        } finally {
                            pending.finish()
                        }
                    }
                    return
                }
            }
            ACTION_REFRESH -> {
                // Not a broadcast we own, so don't call goAsync(). Just kick off a render.
                @Suppress("OPT_IN_USAGE")
                GlobalScope.launch(Dispatchers.IO) { renderAllSync(context) }
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_QUICK_LOG = "com.swarnkary.donezy.WIDGET_QUICK_LOG"
        const val ACTION_REFRESH   = "com.swarnkary.donezy.WIDGET_REFRESH"
        const val EXTRA_HOBBY_ID   = "hobby_id"

        /**
         * Refresh every installed widget. Safe to call from any context — including
         * ViewModel coroutines and other non-receiver code paths. Renders happen on a
         * background dispatcher; the AppWidgetManager.updateAppWidget calls are thread-safe.
         */
        fun refreshAll(context: Context) {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) { renderAllSync(context) }
        }

        private fun renderAllSync(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, NextDueWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) renderWidgetSync(context, mgr, id)
        }

        private fun renderWidgetSync(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val repo = ServiceLocator.repository(context)
            val due = repo.nextDueHobbySync()
            val views = RemoteViews(context.packageName, R.layout.widget_next_due)

            if (due == null) {
                views.setTextViewText(R.id.widget_title, "Donezy")
                views.setTextViewText(R.id.widget_subtitle, "No trackers yet")
                views.setViewVisibility(R.id.widget_log_button, android.view.View.GONE)
            } else {
                views.setTextViewText(R.id.widget_title, due.name)
                val statusInfo = reminderStatusInfo(due.nextReminderAt)
                views.setTextViewText(R.id.widget_subtitle, "${due.category} · ${statusInfo.detail}")
                views.setViewVisibility(R.id.widget_log_button, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_log_button, "✓ Logged")

                val logIntent = Intent(context, NextDueWidgetProvider::class.java).apply {
                    action = ACTION_QUICK_LOG
                    putExtra(EXTRA_HOBBY_ID, due.id)
                }
                val logPending = PendingIntent.getBroadcast(
                    context, due.id.toInt() + 0xA00, logIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_log_button, logPending)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
