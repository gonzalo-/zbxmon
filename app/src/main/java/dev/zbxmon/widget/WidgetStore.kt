package dev.zbxmon.widget

import android.content.Context

/** Last poll summary, written by the poller and read by the home screen widget. */
object WidgetStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun save(ctx: Context, text: String) {
        sp(ctx).edit().putString("text", text).putLong("time", System.currentTimeMillis()).apply()
    }

    fun text(ctx: Context): String = sp(ctx).getString("text", "") ?: ""
    fun time(ctx: Context): Long = sp(ctx).getLong("time", 0L)
}
