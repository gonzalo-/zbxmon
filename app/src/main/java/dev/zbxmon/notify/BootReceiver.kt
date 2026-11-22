package dev.zbxmon.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.zbxmon.Sched

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Sched.applyWork(ctx)
            Sched.applyService(ctx)
        }
    }
}
