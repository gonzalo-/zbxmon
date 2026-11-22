package dev.zbxmon.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.zbxmon.Fmt
import dev.zbxmon.MainActivity
import dev.zbxmon.R
import dev.zbxmon.notify.PollWorker
import dev.zbxmon.str

/** Home screen widget with the latest per-server summary (🟢 / worst-severity emoji + count). */
class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context)) }
        // Ask for fresh data right away; the poller calls refresh() when it finishes.
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PollWorker>().build())
    }

    companion object {
        private fun views(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_summary)
            val text = WidgetStore.text(ctx)
            val time = WidgetStore.time(ctx)
            rv.setTextViewText(R.id.widget_title, "📡 " + ctx.str(R.string.app_name))
            rv.setTextViewText(R.id.widget_body, text.ifBlank { ctx.str(R.string.widget_empty) })
            rv.setTextViewText(
                R.id.widget_time,
                if (time > 0) ctx.str(R.string.widget_updated, Fmt.hms(time)) else ""
            )
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            return rv
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SummaryWidget::class.java))
            ids.forEach { mgr.updateAppWidget(it, views(ctx)) }
        }
    }
}
