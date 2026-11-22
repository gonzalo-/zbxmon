package dev.zbxmon

import android.app.Application
import android.content.Context
import dev.zbxmon.data.PrefsStore
import dev.zbxmon.data.ProfileStore
import dev.zbxmon.notify.MonitorService
import dev.zbxmon.notify.Notifier
import dev.zbxmon.notify.PollWorker

class App : Application() {
    lateinit var profiles: ProfileStore
    lateinit var prefs: PrefsStore

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileStore(this)
        prefs = PrefsStore(this)
        Notifier.createChannels(this)
        Sched.applyWork(this)
    }

    companion object {
        fun get(ctx: Context): App = ctx.applicationContext as App
    }
}

object Sched {
    /** Periodic WorkManager polling. Safe to call from any context. */
    fun applyWork(ctx: Context) {
        val a = App.get(ctx)
        if (a.profiles.all().any { it.enabled }) PollWorker.schedule(ctx, a.prefs.load().pollMinutes)
        else PollWorker.cancel(ctx)
    }

    /** Foreground service. Only call while the app is visible or from BOOT_COMPLETED. */
    fun applyService(ctx: Context) {
        val a = App.get(ctx)
        val on = a.prefs.load().fgEnabled && a.profiles.all().any { it.enabled }
        runCatching { if (on) MonitorService.start(ctx) else MonitorService.stop(ctx) }
    }
}
