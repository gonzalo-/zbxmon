package dev.zbxmon

import androidx.annotation.StringRes
import dev.zbxmon.data.Host
import dev.zbxmon.data.HostState
import dev.zbxmon.data.Problem
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatting helpers shared by the UI and the notifications. */
object Fmt {
    private val EMOJI = listOf("⚪", "🔵", "🟡", "🟠", "🔴", "🔥")
    private val NAME = listOf(R.string.sev_0, R.string.sev_1, R.string.sev_2, R.string.sev_3, R.string.sev_4, R.string.sev_5)
    private val SHORT = listOf(
        R.string.sev_short_0, R.string.sev_short_1, R.string.sev_short_2,
        R.string.sev_short_3, R.string.sev_short_4, R.string.sev_short_5
    )

    fun sevEmoji(s: Int) = EMOJI[s.coerceIn(0, 5)]
    @StringRes fun sevNameRes(s: Int): Int = NAME[s.coerceIn(0, 5)]
    @StringRes fun sevShortRes(s: Int): Int = SHORT[s.coerceIn(0, 5)]

    fun hostState(h: Host, ps: List<Problem>?): HostState {
        val live = ps?.filter { !it.suppressed }.orEmpty()
        return when {
            !h.enabled -> HostState.DISABLED
            live.isNotEmpty() -> HostState.PROBLEM
            h.ifaceDown -> HostState.IFACE_DOWN
            !ps.isNullOrEmpty() -> HostState.MAINTENANCE
            else -> HostState.OK
        }
    }

    /** ⏸️ disabled · severity emoji for the worst active problem · 📵 interface down · 🔧 maintenance · 🟢 OK */
    fun hostEmoji(h: Host, ps: List<Problem>?): String = when (hostState(h, ps)) {
        HostState.DISABLED -> "⏸️"
        HostState.PROBLEM -> sevEmoji(ps.orEmpty().filter { !it.suppressed }.maxOf { it.severity })
        HostState.IFACE_DOWN -> "📵"
        HostState.MAINTENANCE -> "🔧"
        HostState.OK -> "🟢"
    }

    fun age(clock: Long, now: Long = System.currentTimeMillis() / 1000): String {
        if (clock <= 0) return "—"
        val s = (now - clock).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 86400 -> "${s / 3600}h ${(s % 3600) / 60}m"
            else -> "${s / 86400}d ${(s % 86400) / 3600}h"
        }
    }

    fun date(clock: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(clock * 1000))

    fun hms(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

    fun axis(t: Long, long: Boolean): String =
        SimpleDateFormat(if (long) "dd/MM HH:mm" else "HH:mm", Locale.getDefault()).format(Date(t * 1000))

    /** Emoji summary of an event.acknowledge action bitmask. */
    fun ackEmoji(action: Int): String = buildString {
        if (action and 1 != 0) append("🔒")
        if (action and 2 != 0) append("✅")
        if (action and 16 != 0) append("↩️")
        if (action and 8 != 0) append("🎚️")
        if (action and 4 != 0) append("💬")
        if (isEmpty()) append("📝")
    }

    /** Human-readable length of a time span given in seconds. */
    fun span(seconds: Long): String = duration(seconds.toDouble())

    // ---- values ----

    fun value(raw: String, units: String): String {
        val d = raw.toDoubleOrNull() ?: return raw.take(80).ifBlank { "—" }
        return number(d, units)
    }

    fun number(d: Double, units: String): String = when (units) {
        "B" -> scale(d, 1024.0, listOf("B", "KB", "MB", "GB", "TB", "PB"))
        "Bps" -> scale(d, 1024.0, listOf("Bps", "KBps", "MBps", "GBps", "TBps"))
        "bps" -> scale(d, 1000.0, listOf("bps", "Kbps", "Mbps", "Gbps", "Tbps"))
        "s", "uptime" -> duration(d)
        "unixtime" -> date(d.toLong())
        "" -> plain(d)
        else -> plain(d) + " " + units.removePrefix("!")
    }

    private fun plain(d: Double): String =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else "%.2f".format(d)

    private fun scale(v: Double, base: Double, u: List<String>): String {
        var x = v
        var i = 0
        while (Math.abs(x) >= base && i < u.lastIndex) { x /= base; i++ }
        return "%.2f %s".format(x, u[i]).replace(".00 ", " ")
    }

    private fun duration(sec: Double): String {
        val s = sec.toLong()
        return when {
            sec < 1 && sec != 0.0 -> "%.0f ms".format(sec * 1000)
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            s < 86400 -> "${s / 3600}h ${(s % 3600) / 60}m"
            else -> "${s / 86400}d ${(s % 86400) / 3600}h"
        }
    }
}
