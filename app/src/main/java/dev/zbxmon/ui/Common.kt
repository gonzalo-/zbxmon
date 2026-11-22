@file:OptIn(ExperimentalMaterial3Api::class)

package dev.zbxmon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zbxmon.Fmt
import dev.zbxmon.R
import dev.zbxmon.data.Density
import dev.zbxmon.data.Problem
import dev.zbxmon.data.Snoozes
import dev.zbxmon.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** List spacing derived from the density setting: [pad] inside cards and around lists, [gap] between rows. */
data class Spacing(val pad: Dp, val gap: Dp)

val LocalSpacing = compositionLocalOf { Spacing(16.dp, 12.dp) }

@Composable
fun Root(vm: MainViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    val spacing = if (vm.prefs.density == Density.COMPACT) Spacing(8.dp, 6.dp) else Spacing(16.dp, 12.dp)
    CompositionLocalProvider(LocalSpacing provides spacing) {
        when (val s = vm.stack.last()) {
            Screen.Home -> HomeScreen(vm)
            is Screen.HostDetail -> HostDetailScreen(vm, s.host)
            is Screen.ItemGraph -> ItemGraphScreen(vm, s.host, s.item)
            is Screen.ProblemDetail -> ProblemDetailScreen(vm, s.eventId)
            Screen.History -> HistoryScreen(vm)
            Screen.Profiles -> ProfilesScreen(vm)
            is Screen.ProfileEdit -> ProfileEditScreen(vm, s.id)
            Screen.Config -> ConfigScreen(vm)
        }
    }
}

@Composable
fun Bar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Text("⬅️", fontSize = 20.sp) } },
        actions = actions
    )
}

/** Localized text for an error, resolved with the current (language-aware) context. */
@Composable
fun errMsg(e: Throwable): String = LocalContext.current.errorText(e)

@Composable
fun ErrorBanner(msg: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            "⚠️ $msg", Modifier.padding(10.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

@Composable
fun ProblemCard(p: Problem, muted: String = "", onClick: () -> Unit) {
    val sp = LocalSpacing.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(sp.pad), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.sevEmoji(p.severity), fontSize = 26.sp)
            Spacer(Modifier.width(sp.pad))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "🖥️ ${p.hostName} · ⏱️ ${Fmt.age(p.clock)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(muted + (if (p.suppressed) "🔧" else if (p.acked) "✅" else "🔔"), fontSize = 18.sp)
        }
    }
}

/** Text describing an active snooze/mute, or null when notifications are on. */
@Composable
fun snoozeLabel(until: Long?): String? = when {
    until == null -> null
    until == Snoozes.FOREVER -> stringResource(R.string.snooze_muted)
    else -> stringResource(R.string.snooze_active_until, Fmt.date(until / 1000))
}

/** Picks how long to mute notifications. onPick(null) turns them back on. */
@Composable
fun SnoozeDialog(current: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val hour = 3_600_000L
    val now = System.currentTimeMillis()
    val options = listOf(
        R.string.snooze_1h to now + hour,
        R.string.snooze_4h to now + 4 * hour,
        R.string.snooze_8h to now + 8 * hour,
        R.string.snooze_24h to now + 24 * hour,
        R.string.snooze_forever to Snoozes.FOREVER
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snooze_title)) },
        text = {
            Column {
                options.forEach { (res, until) ->
                    TextButton(onClick = { onPick(until) }) { Text(stringResource(res)) }
                }
                if (current != null) {
                    TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.snooze_cancel)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Simple async loader exposing loading / error / data state plus a manual reload. */
class Load<T>(private val scope: CoroutineScope, private val block: suspend () -> T) {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<Throwable?>(null)
    var loading by mutableStateOf(false)

    fun reload() {
        scope.launch {
            loading = true; error = null
            try {
                data = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
            loading = false
        }
    }
}

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): Load<T> {
    val scope = rememberCoroutineScope()
    val l = remember(key) { Load(scope, block) }
    LaunchedEffect(l) { l.reload() }
    return l
}
