package dev.zbxmon.notify

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        PollEngine.run(applicationContext)
        return Result.success()
    }

    companion object {
        const val NAME = "zbx_poll"

        fun schedule(ctx: Context, minutes: Int) {
            val req = PeriodicWorkRequestBuilder<PollWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }
    }
}
