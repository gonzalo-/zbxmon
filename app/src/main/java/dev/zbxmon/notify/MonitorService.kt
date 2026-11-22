package dev.zbxmon.notify

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.zbxmon.App
import dev.zbxmon.R
import dev.zbxmon.str
import kotlinx.coroutines.*

/** Optional foreground service: polls every N seconds without depending on FCM/Google services. */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.foreground(this, str(R.string.fg_starting))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.FG_ID, n)
        }
        job?.cancel()
        job = scope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            while (isActive) {
                val summary = PollEngine.run(applicationContext)
                nm.notify(Notifier.FG_ID, Notifier.foreground(this@MonitorService, summary))
                delay(App.get(applicationContext).prefs.load().fgSeconds.coerceAtLeast(15) * 1000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }
}
