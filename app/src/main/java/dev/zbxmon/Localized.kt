package dev.zbxmon

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dev.zbxmon.data.PinMismatchException
import dev.zbxmon.data.PrefsStore
import dev.zbxmon.data.ZabbixException
import java.util.Locale

/** Applies the in-app language choice (English / Spanish / system) to any context. */
object Localized {
    fun wrap(base: Context): Context {
        val tag = PrefsStore(base).load().language.tag ?: return base
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(cfg)
    }
}

/** Localized string for non-UI contexts (notifications, services). */
fun Context.str(@StringRes id: Int, vararg args: Any): String = Localized.wrap(this).getString(id, *args)

fun Context.plural(@PluralsRes id: Int, n: Int, vararg args: Any): String =
    Localized.wrap(this).resources.getQuantityString(id, n, *args)

/** Human-readable, localized text for any error surfaced to the user. */
fun Context.errorText(e: Throwable): String {
    val chain = generateSequence(e) { it.cause }.toList()
    chain.filterIsInstance<ZabbixException>().firstOrNull()?.let { z ->
        return when (z.kind) {
            ZabbixException.Kind.NO_SERVER -> str(R.string.err_no_server)
            ZabbixException.Kind.NOT_JSONRPC -> str(R.string.err_not_jsonrpc)
            ZabbixException.Kind.API -> z.detail.ifBlank { str(R.string.err_api) }
            ZabbixException.Kind.HTTP -> str(R.string.err_http, z.detail)
            ZabbixException.Kind.BAD_URL -> str(R.string.err_bad_url)
        }
    }
    if (chain.any { it is PinMismatchException }) return str(R.string.err_pin)
    return e.message ?: e.toString()
}
