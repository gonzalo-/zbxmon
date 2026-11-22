package dev.zbxmon.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.zbxmon.Fmt
import dev.zbxmon.MainActivity
import dev.zbxmon.R
import dev.zbxmon.data.Problem
import dev.zbxmon.plural
import dev.zbxmon.str

object Notifier {
    const val CH_HIGH = "zbx_high"
    const val CH_MID = "zbx_mid"
    const val CH_LOW = "zbx_low"
    const val CH_OK = "zbx_ok"
    const val CH_FG = "zbx_fg"
    const val FG_ID = 1001

    /** Creates or updates the channels (updating renames them after a language change). */
    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_HIGH, ctx.str(R.string.ch_high), NotificationManager.IMPORTANCE_HIGH)
                    .apply { enableVibration(true) },
                NotificationChannel(CH_MID, ctx.str(R.string.ch_mid), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_LOW, ctx.str(R.string.ch_low), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_OK, ctx.str(R.string.ch_ok), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_FG, ctx.str(R.string.ch_fg), NotificationManager.IMPORTANCE_MIN)
            )
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channelFor(sev: Int) = when {
        sev >= 4 -> CH_HIGH
        sev >= 2 -> CH_MID
        else -> CH_LOW
    }

    private fun open(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @SuppressLint("MissingPermission")
    private fun send(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        if (canNotify(ctx)) NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    fun problem(ctx: Context, profile: String, eventId: String, sev: Int, host: String, name: String) {
        val b = NotificationCompat.Builder(ctx, channelFor(sev))
            .setSmallIcon(R.drawable.ic_stat_zbx)
            .setContentTitle("${Fmt.sevEmoji(sev)} ${ctx.str(Fmt.sevNameRes(sev))} · $host")
            .setContentText(name)
            .setStyle(NotificationCompat.BigTextStyle().bigText(name))
            .setSubText(profile)
            .setContentIntent(open(ctx))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (sev >= 4) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        send(ctx, ("p$eventId").hashCode(), b)
    }

    /** One grouped notification for all new problems of a poll. */
    fun digest(ctx: Context, profile: String, ps: List<Problem>) {
        if (ps.isEmpty()) return
        val worst = ps.maxOf { it.severity }
        val lines = ps.take(6).map { "${Fmt.sevEmoji(it.severity)} ${it.hostName} · ${it.name}" }
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val b = NotificationCompat.Builder(ctx, channelFor(worst))
            .setSmallIcon(R.drawable.ic_stat_zbx)
            .setContentTitle(ctx.plural(R.plurals.notif_digest_title, ps.size, ps.size))
            .setContentText(lines.first())
            .setStyle(style)
            .setSubText(profile)
            .setContentIntent(open(ctx))
            .setAutoCancel(true)
            .setPriority(if (worst >= 4) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        send(ctx, ("digest$profile").hashCode(), b)
    }

    fun more(ctx: Context, profile: String, n: Int) {
        val b = NotificationCompat.Builder(ctx, CH_MID)
            .setSmallIcon(R.drawable.ic_stat_zbx)
            .setContentTitle(ctx.str(R.string.notif_more_title, n))
            .setContentText(ctx.str(R.string.notif_more_text))
            .setSubText(profile)
            .setContentIntent(open(ctx))
            .setAutoCancel(true)
        send(ctx, ("more$profile").hashCode(), b)
    }

    fun recovered(ctx: Context, profile: String, lines: List<String>) {
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        val b = NotificationCompat.Builder(ctx, CH_OK)
            .setSmallIcon(R.drawable.ic_stat_zbx)
            .setContentTitle(ctx.plural(R.plurals.notif_recovered_title, lines.size, lines.size))
            .setContentText(lines.first())
            .setStyle(style)
            .setSubText(profile)
            .setContentIntent(open(ctx))
            .setAutoCancel(true)
        send(ctx, ("ok$profile").hashCode(), b)
    }

    fun test(ctx: Context) = problem(
        ctx, ctx.str(R.string.notif_test_profile), "test", 4,
        ctx.str(R.string.notif_test_host), ctx.str(R.string.notif_test_text)
    )

    fun foreground(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_FG)
            .setSmallIcon(R.drawable.ic_stat_zbx)
            .setContentTitle(ctx.str(R.string.fg_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open(ctx))
            .build()
}
