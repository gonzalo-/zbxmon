@file:OptIn(ExperimentalMaterial3Api::class)

package dev.zbxmon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.zbxmon.Fmt
import dev.zbxmon.R
import dev.zbxmon.data.Host
import dev.zbxmon.data.HostSort
import dev.zbxmon.data.HostState
import dev.zbxmon.data.Problem
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refreshSeconds = vm.prefs.uiRefreshSeconds
    // Auto-refresh while the app is visible, at the interval chosen in Settings.
    LaunchedEffect(vm.active?.id, refreshSeconds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refresh(silent = vm.hosts.isNotEmpty())
                delay(refreshSeconds * 1000L)
            }
        }
    }
    var menu by remember { mutableStateOf(false) }
    val pending = vm.problems.count { !it.suppressed && !it.acked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { menu = true }) {
                            Text(
                                (vm.active?.name ?: stringResource(R.string.app_name)) + " ▾",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            vm.profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text((if (p.id == vm.active?.id) "● " else "○ ") + p.name) },
                                    onClick = { menu = false; vm.selectProfile(p.id) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_manage_servers)) },
                                onClick = { menu = false; vm.push(Screen.Profiles) }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Text("🔄", fontSize = 20.sp) }
                    IconButton(onClick = { vm.push(Screen.Config) }) { Text("⚙️", fontSize = 20.sp) }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == 0, onClick = { vm.tab = 0 },
                    icon = { Text("🖥️", fontSize = 20.sp) }, label = { Text(stringResource(R.string.tab_hosts)) }
                )
                NavigationBarItem(
                    selected = vm.tab == 1, onClick = { vm.tab = 1 },
                    icon = {
                        BadgedBox(badge = { if (pending > 0) Badge { Text("$pending") } }) {
                            Text("🚨", fontSize = 20.sp)
                        }
                    },
                    label = { Text(stringResource(R.string.tab_problems)) }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.active == null) {
                Welcome(vm)
            } else {
                PullToRefreshBox(isRefreshing = vm.loading, onRefresh = { vm.refresh() }) {
                    Column(Modifier.fillMaxSize()) {
                        vm.error?.let { ErrorBanner(errMsg(it)) }
                        SummaryRow(vm)
                        if (vm.tab == 0) HostsTab(vm) else ProblemsTab(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun Welcome(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📡", fontSize = 56.sp)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.welcome_hint))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.push(Screen.ProfileEdit(null)) }) { Text(stringResource(R.string.welcome_add)) }
    }
}

@Composable
fun SummaryRow(vm: MainViewModel) {
    val sp = LocalSpacing.current
    val live = vm.problems.filter { !it.suppressed }
    val c = live.groupingBy { it.severity }.eachCount()
    val badHosts = live.map { it.hostId }.toSet().size
    val ok = (vm.hosts.count { it.enabled } - badHosts).coerceAtLeast(0)
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = sp.pad - 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.summary_line, ok, badHosts, vm.hosts.size),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                (5 downTo 1).joinToString("  ") { "${Fmt.sevEmoji(it)}${c[it] ?: 0}" } +
                    (if (vm.updated > 0) "    🕒 ${Fmt.hms(vm.updated)}" else ""),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filters shared by both tabs
// ---------------------------------------------------------------------------------------------

/** Filter options built from the hosts: "g:<group>" and "t:<tag[=value]>". */
private fun groupOptions(hosts: List<Host>): List<String> =
    hosts.flatMap { h -> h.groups.map { "g:$it" } + h.tags.map { "t:$it" } }.distinct().sorted()

private fun matchesGroup(h: Host, filter: String): Boolean = when {
    filter.isEmpty() -> true
    filter.startsWith("g:") -> filter.drop(2) in h.groups
    else -> filter.drop(2) in h.tags
}

/** 🏷️ chip: filter by host group or tag. Hidden when the server has none. The choice is saved automatically. */
@Composable
private fun GroupChip(vm: MainViewModel, options: List<String>, active: String) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active.isNotEmpty(),
            onClick = { open = true },
            label = { Text(if (active.isEmpty()) "🏷️" else "🏷️ ${active.drop(2)}", maxLines = 1) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text((if (active.isEmpty()) "✓ " else "") + stringResource(R.string.filter_all)) },
                onClick = { open = false; vm.updateFilters(vm.prefs.copy(groupFilter = "")) }
            )
            options.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Text((if (o == active) "✓ " else "") + (if (o.startsWith("g:")) "📁 " else "🏷️ ") + o.drop(2))
                    },
                    onClick = { open = false; vm.updateFilters(vm.prefs.copy(groupFilter = o)) }
                )
            }
        }
    }
}

/** ↕️ chip: sort order for the Hosts and Problems lists. */
@Composable
private fun SortChip(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = false, onClick = { open = true }, label = { Text("↕️") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HostSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text((if (s == vm.prefs.sort) "✓ " else "") + stringResource(s.labelRes)) },
                    onClick = { open = false; vm.updateFilters(vm.prefs.copy(sort = s)) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hosts tab
// ---------------------------------------------------------------------------------------------

@Composable
fun ColumnScope.HostsTab(vm: MainViewModel) {
    val sp = LocalSpacing.current
    val prefs = vm.prefs
    val shown = prefs.hostShow
    val query = vm.hostQuery
    val favs = vm.favorites
    val options = remember(vm.hosts) { groupOptions(vm.hosts) }
    val group = prefs.groupFilter.takeIf { it in options } ?: ""
    val byHost = remember(vm.problems) { vm.problems.groupBy { it.hostId } }
    val states = remember(vm.hosts, byHost) { vm.hosts.associate { it.id to Fmt.hostState(it, byHost[it.id]) } }
    val counts = remember(states) { states.values.groupingBy { it }.eachCount() }

    val list = remember(vm.hosts, states, shown, query, group, favs, prefs.sort, vm.active?.id) {
        val live = { h: Host -> byHost[h.id].orEmpty().filter { !it.suppressed } }
        val base: Comparator<Host> = when (prefs.sort) {
            HostSort.SEVERITY -> compareByDescending<Host> { h -> live(h).maxOfOrNull { it.severity } ?: -1 }
                .thenBy { it.name.lowercase() }
            HostSort.NAME -> compareBy<Host> { it.name.lowercase() }
            HostSort.RECENT -> compareByDescending<Host> { h -> live(h).maxOfOrNull { it.clock } ?: 0L }
                .thenBy { it.name.lowercase() }
        }
        vm.hosts
            .filter {
                states.getValue(it.id) in shown && matchesGroup(it, group) &&
                    (query.isBlank() || it.name.contains(query, true) || it.ip.contains(query))
            }
            .sortedWith(compareByDescending<Host> { vm.favKey(it) in favs }.then(base)) // ⭐ favorites first
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query, onValueChange = { vm.hostQuery = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hosts)) }, modifier = Modifier.weight(1f)
        )
        GroupChip(vm, options, group)
        SortChip(vm)
    }
    // One chip per host state with its count. Tap to show/hide; the choice is saved automatically.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HostState.entries.forEach { s ->
            FilterChip(
                selected = s in shown,
                onClick = { vm.updateFilters(prefs.copy(hostShow = if (s in shown) shown - s else shown + s)) },
                label = { Text("${s.emoji} ${counts[s] ?: 0}") }
            )
        }
    }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(sp.pad),
        verticalArrangement = Arrangement.spacedBy(sp.gap)
    ) {
        items(list, key = { it.id }) { h ->
            HostCard(
                h = h,
                ps = byHost[h.id],
                fav = vm.favKey(h) in favs,
                muted = vm.muteMark(vm.snoozeUntil(vm.hostSnoozeKey(h.id))),
                onFav = { vm.toggleFavorite(h) },
                onClick = { vm.push(Screen.HostDetail(h)) }
            )
        }
        if (list.isEmpty()) {
            item { Text(stringResource(if (vm.loading) R.string.loading else R.string.no_hosts)) }
        }
    }
}

@Composable
fun HostCard(
    h: Host,
    ps: List<Problem>?,
    fav: Boolean,
    muted: String,
    onFav: () -> Unit,
    onClick: () -> Unit
) {
    val sp = LocalSpacing.current
    val live = ps?.filter { !it.suppressed }.orEmpty()
    val noIp = stringResource(R.string.no_ip)
    val disabled = stringResource(R.string.host_disabled)
    val ifaceDown = stringResource(R.string.host_iface_down)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(sp.pad), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.hostEmoji(h, ps), fontSize = 28.sp)
            Spacer(Modifier.width(sp.pad))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        muted,
                        h.ip.ifBlank { noIp },
                        when {
                            !h.enabled -> disabled
                            h.ifaceDown && live.isEmpty() -> ifaceDown
                            else -> ""
                        }
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (live.isNotEmpty()) {
                Text("🚨 ${live.size}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (fav) "⭐" else "☆", fontSize = 22.sp, modifier = Modifier.clickable { onFav() }.padding(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Problems tab
// ---------------------------------------------------------------------------------------------

@Composable
fun ColumnScope.ProblemsTab(vm: MainViewModel) {
    val sp = LocalSpacing.current
    val prefs = vm.prefs
    val minSev = prefs.problemMinSeverity
    val onlyUnack = prefs.problemOnlyUnack
    val showSuppressed = prefs.problemShowSuppressed
    val options = remember(vm.hosts) { groupOptions(vm.hosts) }
    val group = prefs.groupFilter.takeIf { it in options } ?: ""
    val allowedHosts = remember(vm.hosts, group) {
        if (group.isEmpty()) null else vm.hosts.filter { matchesGroup(it, group) }.map { it.id }.toSet()
    }
    val list = remember(vm.problems, minSev, onlyUnack, showSuppressed, allowedHosts, prefs.sort) {
        val cmp: Comparator<Problem> = when (prefs.sort) {
            HostSort.SEVERITY -> compareByDescending<Problem> { !it.suppressed }
                .thenByDescending { it.severity }
                .thenByDescending { it.clock }
            HostSort.NAME -> compareBy<Problem>({ it.hostName.lowercase() }, { it.name.lowercase() })
            HostSort.RECENT -> compareByDescending<Problem> { it.clock }
        }
        vm.problems
            .filter {
                it.severity >= minSev && (!onlyUnack || !it.acked) && (showSuppressed || !it.suppressed) &&
                    (allowedHosts == null || it.hostId in allowedHosts)
            }
            .sortedWith(cmp)
    }
    // Filters are saved automatically and survive navigation and restarts.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0 to stringResource(R.string.filter_all), 2 to "🟡+", 3 to "🟠+", 4 to "🔴+").forEach { (v, l) ->
            FilterChip(
                selected = minSev == v,
                onClick = { vm.updateFilters(prefs.copy(problemMinSeverity = v)) },
                label = { Text(l) }
            )
        }
        FilterChip(
            selected = onlyUnack,
            onClick = { vm.updateFilters(prefs.copy(problemOnlyUnack = !onlyUnack)) },
            label = { Text(stringResource(R.string.filter_unack)) }
        )
        FilterChip(
            selected = showSuppressed,
            onClick = { vm.updateFilters(prefs.copy(problemShowSuppressed = !showSuppressed)) },
            label = { Text("🔧") }
        )
        GroupChip(vm, options, group)
        SortChip(vm)
        FilterChip(selected = false, onClick = { vm.push(Screen.History) }, label = { Text("🕘") })
    }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(sp.pad),
        verticalArrangement = Arrangement.spacedBy(sp.gap)
    ) {
        items(list, key = { it.eventId }) { p ->
            ProblemCard(p, muted = vm.problemMark(p)) { vm.push(Screen.ProblemDetail(p.eventId)) }
        }
        if (list.isEmpty()) {
            item { Text(stringResource(if (vm.loading) R.string.loading else R.string.no_problems)) }
        }
    }
}
