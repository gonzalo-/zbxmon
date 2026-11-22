package dev.zbxmon.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zbxmon.App
import dev.zbxmon.Sched
import dev.zbxmon.data.*
import dev.zbxmon.notify.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class HostDetail(val host: Host) : Screen
    data class ItemGraph(val host: Host, val item: Item) : Screen
    data class ProblemDetail(val eventId: String) : Screen
    data object Profiles : Screen
    data class ProfileEdit(val id: String?) : Screen
    data object History : Screen
    data object Config : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    private val store = App.get(app).profiles
    private val prefsStore = App.get(app).prefs

    var profiles by mutableStateOf(store.all()); private set
    var prefs by mutableStateOf(prefsStore.load()); private set

    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableIntStateOf(0)

    /** Host search text: kept here so it survives navigating to other screens (not saved to disk). */
    var hostQuery by mutableStateOf("")

    var favorites by mutableStateOf(Favorites.load(app)); private set
    var snoozes by mutableStateOf(Snoozes.all(app)); private set

    var hosts by mutableStateOf<List<Host>>(emptyList()); private set
    var problems by mutableStateOf<List<Problem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<Throwable?>(null); private set
    var updated by mutableLongStateOf(0L); private set

    val active: Profile?
        get() = profiles.firstOrNull { it.id == prefs.activeProfileId } ?: profiles.firstOrNull()

    private var apiCache: Pair<String, ZabbixApi>? = null

    fun api(): ZabbixApi {
        val p = active ?: throw ZabbixException(ZabbixException.Kind.NO_SERVER)
        val key = p.id + p.tokenEnc + p.url + p.pinSha256
        apiCache?.let { if (it.first == key) return it.second }
        return ZabbixApi(p).also { apiCache = key to it }
    }

    // ---- favorites and snoozes (local only) ----
    fun favKey(h: Host) = "${active?.id.orEmpty()}|${h.id}"

    fun toggleFavorite(h: Host) {
        val k = favKey(h)
        favorites = if (k in favorites) favorites - k else favorites + k
        Favorites.save(ctx, favorites)
    }

    fun hostSnoozeKey(hostId: String) = Snoozes.hostKey(active?.id.orEmpty(), hostId)
    fun problemSnoozeKey(eventId: String) = Snoozes.problemKey(active?.id.orEmpty(), eventId)

    /** Epoch millis until which [key] is muted, or null when it is not muted. */
    fun snoozeUntil(key: String): Long? = snoozes[key]?.takeIf { it > System.currentTimeMillis() }

    fun setSnooze(key: String, until: Long?) {
        Snoozes.set(ctx, key, until)
        snoozes = Snoozes.all(ctx)
    }

    /** 🔕 muted forever, 😴 snoozed, empty when notifications are on. */
    fun muteMark(until: Long?): String = when {
        until == null -> ""
        until == Snoozes.FOREVER -> "🔕"
        else -> "😴"
    }

    fun problemMark(p: Problem): String =
        muteMark(snoozeUntil(problemSnoozeKey(p.eventId)) ?: snoozeUntil(hostSnoozeKey(p.hostId)))

    // ---- navigation ----
    fun push(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // ---- data ----
    private var job: Job? = null

    fun refresh(silent: Boolean = false) {
        if (active == null) {
            hosts = emptyList(); problems = emptyList(); error = null
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            if (!silent) loading = true
            try {
                val a = api()
                val (h, p) = coroutineScope {
                    val hh = async { a.hosts() }
                    val pp = async { a.problems() }
                    hh.await() to pp.await()
                }
                hosts = h; problems = p; error = null; updated = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
            loading = false
        }
    }

    fun send(eventId: String, a: AckAction, msg: String, done: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val r = runCatching { api().ack(eventId, a, msg.trim()) }
            done(r.exceptionOrNull())
            if (r.isSuccess) refresh(silent = true)
        }
    }

    // ---- profiles / settings ----
    /** Saves list filters automatically; unlike [updatePrefs] it never touches background scheduling. */
    fun updateFilters(p: Prefs) {
        prefs = p
        prefsStore.save(p)
    }

    fun updatePrefs(p: Prefs) {
        val languageChanged = p.language != prefs.language
        val schedulingChanged = p.pollMinutes != prefs.pollMinutes || p.fgEnabled != prefs.fgEnabled ||
            p.fgSeconds != prefs.fgSeconds
        prefs = p
        prefsStore.save(p)
        if (languageChanged) Notifier.createChannels(ctx) // rename channels in the new language
        if (schedulingChanged) {
            Sched.applyWork(ctx)
            Sched.applyService(ctx)
        }
    }

    fun selectProfile(id: String) {
        hosts = emptyList(); problems = emptyList()
        updatePrefs(prefs.copy(activeProfileId = id))
        refresh()
    }

    fun saveProfile(p: Profile) {
        store.upsert(p)
        profiles = store.all()
        if (prefs.activeProfileId == null || profiles.none { it.id == prefs.activeProfileId }) {
            updatePrefs(prefs.copy(activeProfileId = p.id))
        } else {
            Sched.applyWork(ctx); Sched.applyService(ctx)
        }
        Known.clear(ctx, p.id) // URL/token may have changed: re-seed instead of notifying everything
        refresh()
    }

    fun deleteProfile(id: String) {
        store.delete(id)
        Known.clear(ctx, id)
        profiles = store.all()
        if (prefs.activeProfileId == id) updatePrefs(prefs.copy(activeProfileId = profiles.firstOrNull()?.id))
        else { Sched.applyWork(ctx); Sched.applyService(ctx) }
        hosts = emptyList(); problems = emptyList()
        refresh()
    }
}
