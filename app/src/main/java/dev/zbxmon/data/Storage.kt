package dev.zbxmon.data

import android.content.Context
import androidx.annotation.StringRes
import dev.zbxmon.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Frontend base URL without api_jsonrpc.php, e.g. https://zbx.example.com/zabbix */
    val url: String,
    /** Display label only: an API token already belongs to a user. */
    val user: String = "",
    val tokenEnc: String,
    /** Empty = normal system TLS validation. Set = self-signed certificate pinned by SHA-256 fingerprint. */
    val pinSha256: String = "",
    /** Whether background notifications are enabled for this server. */
    val enabled: Boolean = true
) {
    val token: String get() = Crypto.decrypt(tokenEnc)
    val apiUrl: String get() = url.trim().trimEnd('/') + "/api_jsonrpc.php"
}

class ProfileStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<Profile> =
        sp.getString("list", null)
            ?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() }
            ?: emptyList()

    private fun save(list: List<Profile>) {
        sp.edit().putString("list", json.encodeToString(list)).apply()
    }

    fun upsert(p: Profile) {
        val cur = all()
        save(if (cur.any { it.id == p.id }) cur.map { if (it.id == p.id) p else it } else cur + p)
    }

    fun delete(id: String) = save(all().filter { it.id != id })
}

enum class ThemeStyle(@StringRes val labelRes: Int) {
    MINIMAL(R.string.theme_minimal),
    TERMINAL(R.string.theme_terminal)
}

/** Order of the Hosts and Problems lists. */
enum class HostSort(@StringRes val labelRes: Int) {
    SEVERITY(R.string.sort_severity),
    NAME(R.string.sort_name),
    RECENT(R.string.sort_recent)
}

enum class Density(@StringRes val labelRes: Int) {
    COMFORTABLE(R.string.density_comfortable),
    COMPACT(R.string.density_compact)
}

/** UI language. [tag] is a BCP 47 language tag, or null to follow the system. */
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    EN("en", R.string.lang_en),
    ES("es", R.string.lang_es),
    SYSTEM(null, R.string.lang_system)
}

data class Prefs(
    val theme: ThemeStyle = ThemeStyle.MINIMAL,
    val language: AppLanguage = AppLanguage.EN,
    val activeProfileId: String? = null,
    /** Minimum severity (0..5) that triggers a notification. */
    val minSeverity: Int = 2,
    /** WorkManager polling interval in minutes (Android enforces a 15 minute minimum). */
    val pollMinutes: Int = 15,
    val fgEnabled: Boolean = false,
    val fgSeconds: Int = 60,
    val notifyRecovery: Boolean = true,
    // --- Persistent list filters (saved automatically) ---
    /** Host states shown in the Hosts tab. */
    val hostShow: Set<HostState> = HostState.entries.toSet(),
    /** Minimum severity shown in the Problems tab (0 = all). */
    val problemMinSeverity: Int = 0,
    val problemOnlyUnack: Boolean = false,
    val problemShowSuppressed: Boolean = true,
    /** Group/tag filter: "" = everything, "g:<group>" or "t:<tag[=value]>". */
    val groupFilter: String = "",
    val sort: HostSort = HostSort.SEVERITY,
    // --- Display ---
    val density: Density = Density.COMFORTABLE,
    /** How often the visible screen refreshes itself. */
    val uiRefreshSeconds: Int = 30,
    // --- Notification behaviour ---
    val quietEnabled: Boolean = false,
    /** Quiet hours as minutes after midnight. */
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    val quietAllowDisaster: Boolean = true,
    /** One grouped notification per poll instead of one per problem. */
    val digest: Boolean = false
)

class PrefsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun load() = Prefs(
        theme = runCatching { ThemeStyle.valueOf(sp.getString("theme", "") ?: "") }.getOrDefault(ThemeStyle.MINIMAL),
        language = runCatching { AppLanguage.valueOf(sp.getString("lang", "") ?: "") }.getOrDefault(AppLanguage.EN),
        activeProfileId = sp.getString("active", null),
        minSeverity = sp.getInt("minSev", 2),
        pollMinutes = sp.getInt("pollMin", 15),
        fgEnabled = sp.getBoolean("fg", false),
        fgSeconds = sp.getInt("fgSec", 60),
        notifyRecovery = sp.getBoolean("recov", true),
        // A missing key means "never saved": show everything. An empty string means "hide everything".
        hostShow = sp.getString("hostShow", null)
            ?.split(",")
            ?.mapNotNull { runCatching { HostState.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: HostState.entries.toSet(),
        problemMinSeverity = sp.getInt("pMinSev", 0),
        problemOnlyUnack = sp.getBoolean("pUnack", false),
        problemShowSuppressed = sp.getBoolean("pSupp", true),
        groupFilter = sp.getString("group", "") ?: "",
        sort = runCatching { HostSort.valueOf(sp.getString("sort", "") ?: "") }.getOrDefault(HostSort.SEVERITY),
        density = runCatching { Density.valueOf(sp.getString("density", "") ?: "") }.getOrDefault(Density.COMFORTABLE),
        uiRefreshSeconds = sp.getInt("uiRefresh", 30),
        quietEnabled = sp.getBoolean("quiet", false),
        quietStartMin = sp.getInt("quietStart", 22 * 60),
        quietEndMin = sp.getInt("quietEnd", 7 * 60),
        quietAllowDisaster = sp.getBoolean("quietDisaster", true),
        digest = sp.getBoolean("digest", false)
    )

    fun save(p: Prefs) {
        sp.edit()
            .putString("theme", p.theme.name)
            .putString("lang", p.language.name)
            .putString("active", p.activeProfileId)
            .putInt("minSev", p.minSeverity)
            .putInt("pollMin", p.pollMinutes)
            .putBoolean("fg", p.fgEnabled)
            .putInt("fgSec", p.fgSeconds)
            .putBoolean("recov", p.notifyRecovery)
            .putString("hostShow", p.hostShow.joinToString(",") { it.name })
            .putInt("pMinSev", p.problemMinSeverity)
            .putBoolean("pUnack", p.problemOnlyUnack)
            .putBoolean("pSupp", p.problemShowSuppressed)
            .putString("group", p.groupFilter)
            .putString("sort", p.sort.name)
            .putString("density", p.density.name)
            .putInt("uiRefresh", p.uiRefreshSeconds)
            .putBoolean("quiet", p.quietEnabled)
            .putInt("quietStart", p.quietStartMin)
            .putInt("quietEnd", p.quietEndMin)
            .putBoolean("quietDisaster", p.quietAllowDisaster)
            .putBoolean("digest", p.digest)
            .apply()
    }
}

/**
 * Problems already notified, per profile (eventId -> "severity|hostId|host|name").
 * Stored under a versioned key so data from older formats is ignored (it simply re-seeds).
 * A null result means the first run: state is seeded silently instead of notifying everything.
 */
object Known {
    private val json = Json

    private fun key(profileId: String) = "v2:$profileId"

    fun load(ctx: Context, profileId: String): Map<String, String>? =
        ctx.getSharedPreferences("known", Context.MODE_PRIVATE).getString(key(profileId), null)
            ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }

    fun save(ctx: Context, profileId: String, m: Map<String, String>) {
        ctx.getSharedPreferences("known", Context.MODE_PRIVATE).edit()
            .putString(key(profileId), json.encodeToString(m)).apply()
    }

    fun clear(ctx: Context, profileId: String) {
        ctx.getSharedPreferences("known", Context.MODE_PRIVATE).edit().remove(key(profileId)).apply()
    }
}

/** Favorite hosts pinned to the top of the list, stored as "profileId|hostId". */
object Favorites {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(ctx: Context): Set<String> = sp(ctx).getStringSet("set", emptySet())?.toSet() ?: emptySet()

    fun save(ctx: Context, s: Set<String>) {
        sp(ctx).edit().putStringSet("set", s).apply()
    }
}

/**
 * Local notification snoozes/mutes (they do not change anything in Zabbix).
 * key -> epoch millis until which notifications are muted; [FOREVER] means muted until turned back on.
 */
object Snoozes {
    const val FOREVER = Long.MAX_VALUE
    private val json = Json

    fun hostKey(profileId: String, hostId: String) = "h:$profileId:$hostId"
    fun problemKey(profileId: String, eventId: String) = "p:$profileId:$eventId"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("snooze", Context.MODE_PRIVATE)

    /** Active snoozes only: expired entries are dropped. */
    fun all(ctx: Context): Map<String, Long> {
        val now = System.currentTimeMillis()
        val raw = sp(ctx).getString("map", null)
            ?.let { runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrNull() }
            ?: emptyMap()
        return raw.filterValues { it > now }
    }

    /** until = null clears the snooze. */
    fun set(ctx: Context, key: String, until: Long?) {
        val m = all(ctx).toMutableMap()
        if (until == null) m.remove(key) else m[key] = until
        sp(ctx).edit().putString("map", json.encodeToString(m)).apply()
    }
}
