package dev.zbxmon.notify

import android.content.Context
import dev.zbxmon.App
import dev.zbxmon.Fmt
import dev.zbxmon.R
import dev.zbxmon.data.Known
import dev.zbxmon.data.Prefs
import dev.zbxmon.data.Problem
import dev.zbxmon.data.Profile
import dev.zbxmon.data.Snoozes
import dev.zbxmon.data.ZabbixApi
import dev.zbxmon.str
import dev.zbxmon.widget.SummaryWidget
import dev.zbxmon.widget.WidgetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** Polling logic shared by WorkManager and the foreground service. Returns a short emoji summary. */
object PollEngine {
    // Serializes runs so the worker and the service never notify the same problem twice.
    private val mutex = Mutex()

    suspend fun run(ctx: Context): String = mutex.withLock {
        val app = App.get(ctx)
        val prefs = app.prefs.load()
        val parts = mutableListOf<String>()
        for (p in app.profiles.all().filter { it.enabled }) {
            parts += try {
                poll(ctx, p, prefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "${p.name}: ⚠️"
            }
        }
        WidgetStore.save(ctx, parts.joinToString("\n"))
        SummaryWidget.refresh(ctx)
        if (parts.isEmpty()) ctx.str(R.string.poll_no_profiles) else parts.joinToString(" · ")
    }

    private fun isQuietNow(p: Prefs, now: Calendar = Calendar.getInstance()): Boolean {
        if (!p.quietEnabled || p.quietStartMin == p.quietEndMin) return false
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (p.quietStartMin < p.quietEndMin) m in p.quietStartMin until p.quietEndMin
        else m >= p.quietStartMin || m < p.quietEndMin
    }

    private suspend fun poll(ctx: Context, p: Profile, prefs: Prefs): String {
        val all = ZabbixApi(p).problems().filter { !it.suppressed }
        val relevant = all.filter { it.severity >= prefs.minSeverity }
        val cur = relevant.associate { it.eventId to "${it.severity}|${it.hostId}|${it.hostName}|${it.name}" }
        val known = Known.load(ctx, p.id)
        var saved = cur

        if (known != null) {
            val now = System.currentTimeMillis()
            val snoozes = Snoozes.all(ctx)
            fun muted(hostId: String, eventId: String) =
                (snoozes[Snoozes.hostKey(p.id, hostId)] ?: 0L) > now ||
                    (snoozes[Snoozes.problemKey(p.id, eventId)] ?: 0L) > now

            val quiet = isQuietNow(prefs)
            val fresh = relevant.filter { it.eventId !in known }.sortedByDescending { it.severity }
            // During quiet hours new problems are held back (not recorded as known) and notified when quiet ends.
            val deferred = if (quiet) {
                fresh.filter { !(prefs.quietAllowDisaster && it.severity >= 5) && !muted(it.hostId, it.eventId) }
            } else emptyList()
            val toNotify = fresh.filter { !muted(it.hostId, it.eventId) && it !in deferred }
            saved = cur - deferred.map { it.eventId }.toSet()

            if (Notifier.canNotify(ctx)) {
                notifyNew(ctx, p.name, toNotify, prefs.digest)
                if (prefs.notifyRecovery && !quiet) {
                    val gone = known.filterKeys { it !in cur }.mapNotNull { (eventId, v) ->
                        val parts = v.split("|", limit = 4)
                        if (parts.size < 4 || muted(parts[1], eventId)) null
                        else "${Fmt.sevEmoji(parts[0].toIntOrNull() ?: 0)} ${parts[2]} · ${parts[3]}"
                    }
                    if (gone.isNotEmpty()) Notifier.recovered(ctx, p.name, gone)
                }
            }
        }
        Known.save(ctx, p.id, saved) // first run only seeds the state, without notifying

        return if (all.isEmpty()) "${p.name}: 🟢" else "${p.name}: ${Fmt.sevEmoji(all.maxOf { it.severity })}${all.size}"
    }

    private fun notifyNew(ctx: Context, profile: String, list: List<Problem>, digest: Boolean) {
        if (list.isEmpty()) return
        if (digest) {
            Notifier.digest(ctx, profile, list)
        } else {
            list.take(5).forEach { Notifier.problem(ctx, profile, it.eventId, it.severity, it.hostName, it.name) }
            if (list.size > 5) Notifier.more(ctx, profile, list.size - 5)
        }
    }
}
