@file:OptIn(ExperimentalMaterial3Api::class)

package dev.zbxmon.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zbxmon.Fmt
import dev.zbxmon.R
import dev.zbxmon.data.AckAction
import dev.zbxmon.data.Host
import dev.zbxmon.data.Item
import dev.zbxmon.data.Point
import dev.zbxmon.data.Resolved
import dev.zbxmon.errorText

@Composable
fun ProblemDetailScreen(vm: MainViewModel, eventId: String) {
    val ctx = LocalContext.current
    val p = vm.problems.firstOrNull { it.eventId == eventId }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var snoozeDialog by remember { mutableStateOf(false) }

    fun send(a: AckAction) {
        busy = true
        vm.send(eventId, a, msg) { err ->
            busy = false
            info = if (err == null) ctx.getString(R.string.sent_ok) else "❌ ${ctx.errorText(err)}"
            if (err == null) msg = ""
        }
    }

    Scaffold(topBar = { Bar(stringResource(R.string.problem_title), vm::back) }) { pad ->
        if (p == null) {
            Box(Modifier.padding(pad).padding(16.dp)) { Text(stringResource(R.string.problem_gone)) }
            return@Scaffold
        }
        val snoozeKey = vm.problemSnoozeKey(p.eventId)
        val until = vm.snoozeUntil(snoozeKey)

        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${Fmt.sevEmoji(p.severity)} ${stringResource(Fmt.sevNameRes(p.severity))}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(p.name, style = MaterialTheme.typography.titleLarge)
            Text("🖥️ ${p.hostName}")
            Text("🕒 ${Fmt.date(p.clock)}  (⏱️ ${Fmt.age(p.clock)})")
            Text(
                stringResource(
                    when {
                        p.suppressed -> R.string.problem_suppressed
                        p.acked -> R.string.problem_acked
                        else -> R.string.problem_unacked
                    }
                )
            )
            OutlinedTextField(
                value = msg, onValueChange = { msg = it },
                label = { Text(stringResource(R.string.msg_label)) }, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!p.acked) {
                    Button(onClick = { send(AckAction.ACK) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_ack))
                    }
                } else {
                    OutlinedButton(onClick = { send(AckAction.UNACK) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_unack))
                    }
                }
                OutlinedButton(
                    onClick = { send(AckAction.COMMENT) },
                    enabled = !busy && msg.isNotBlank(), modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.btn_comment)) }
            }
            info?.let { Text(it) }

            // Local-only actions: snooze notifications for this problem, and share it as text.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { snoozeDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.snooze_btn))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val text = "${Fmt.sevEmoji(p.severity)} ${ctx.getString(Fmt.sevNameRes(p.severity))} · ${p.hostName}\n" +
                            "${p.name}\n🕒 ${Fmt.date(p.clock)}"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_title)))
                    }
                ) { Text(stringResource(R.string.btn_share)) }
            }
            snoozeLabel(until)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            if (p.acks.isNotEmpty()) {
                Section(stringResource(R.string.history))
                p.acks.sortedByDescending { it.clock }.forEach { a ->
                    Column {
                        Text(
                            "${Fmt.ackEmoji(a.action)} ${Fmt.date(a.clock)} · ${a.user}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (a.message.isNotBlank()) Text(a.message)
                    }
                }
            }
        }

        if (snoozeDialog) {
            SnoozeDialog(
                current = until,
                onPick = { vm.setSnooze(snoozeKey, it); snoozeDialog = false },
                onDismiss = { snoozeDialog = false }
            )
        }
    }
}

@Composable
fun HostDetailScreen(vm: MainViewModel, host: Host) {
    val sp = LocalSpacing.current
    val load = rememberLoad(host.id) { vm.api().items(host.id) }
    var q by remember { mutableStateOf("") }
    var snoozeDialog by remember { mutableStateOf(false) }
    val ps = vm.problems.filter { it.hostId == host.id }
    val shown = load.data.orEmpty().filter {
        q.isBlank() || it.name.contains(q, true) || it.key.contains(q, true)
    }
    val noIp = stringResource(R.string.no_ip)
    val disabled = stringResource(R.string.host_disabled)
    val snoozeKey = vm.hostSnoozeKey(host.id)
    val until = vm.snoozeUntil(snoozeKey)

    Scaffold(topBar = {
        Bar(host.name, vm::back) { IconButton(onClick = { load.reload() }) { Text("🔄", fontSize = 20.sp) } }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(sp.pad),
            verticalArrangement = Arrangement.spacedBy(sp.gap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${Fmt.hostEmoji(host, ps.ifEmpty { null })}  ${host.ip.ifBlank { noIp }}" +
                            if (!host.enabled) "  ·  $disabled" else "",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (host.groups.isNotEmpty() || host.tags.isNotEmpty()) {
                        Text(
                            (host.groups.map { "📁 $it" } + host.tags.map { "🏷️ $it" }).joinToString("   "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { snoozeDialog = true }) { Text(stringResource(R.string.snooze_btn)) }
                        snoozeLabel(until)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (ps.isNotEmpty()) {
                item { Section(stringResource(R.string.problems_section)) }
                items(ps, key = { "p" + it.eventId }) { p ->
                    ProblemCard(p, muted = vm.problemMark(p)) { vm.push(Screen.ProblemDetail(p.eventId)) }
                }
            }
            item {
                Section(stringResource(R.string.items_section))
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.filter_items)) }, modifier = Modifier.fillMaxWidth()
                )
            }
            load.error?.let { e -> item { ErrorBanner(errMsg(e)) } }
            if (load.loading && load.data == null) item { CircularProgressIndicator() }
            items(shown, key = { "i" + it.id }) { item ->
                ItemRow(item) { if (item.numeric) vm.push(Screen.ItemGraph(host, item)) }
            }
        }

        if (snoozeDialog) {
            SnoozeDialog(
                current = until,
                onPick = { vm.setSnooze(snoozeKey, it); snoozeDialog = false },
                onDismiss = { snoozeDialog = false }
            )
        }
    }
}

@Composable
fun ItemRow(item: Item, onClick: () -> Unit) {
    val sp = LocalSpacing.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(sp.pad), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    item.key, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Fmt.value(item.lastValue, item.units) + if (item.numeric) " 📈" else "",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "⏱️ ${Fmt.age(item.lastClock)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ItemGraphScreen(vm: MainViewModel, host: Host, item: Item) {
    var hours by rememberSaveable { mutableIntStateOf(6) }
    val load = rememberLoad(hours) { vm.api().series(item.id, item.valueType, hours) }
    val data = load.data
    val error = load.error

    Scaffold(topBar = {
        Bar(item.name, vm::back) { IconButton(onClick = { load.reload() }) { Text("🔄", fontSize = 20.sp) } }
    }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🖥️ ${host.name}")
            Text(item.key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "1h", 6 to "6h", 24 to "24h", 168 to "7d").forEach { (h, l) ->
                    FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(l) })
                }
            }
            when {
                error != null -> ErrorBanner(errMsg(error))
                data == null -> CircularProgressIndicator()
                data.isEmpty() -> Text(stringResource(R.string.no_data))
                else -> {
                    Chart(data, item.units, hours > 24)
                    val vs = data.map { it.v }
                    Text(
                        "⬇️ ${Fmt.number(vs.min(), item.units)}   ⌀ ${Fmt.number(vs.average(), item.units)}   " +
                            "⬆️ ${Fmt.number(vs.max(), item.units)}   🔚 ${Fmt.number(vs.last(), item.units)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun Chart(points: List<Point>, units: String, longRange: Boolean) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val minV = points.minOf { it.v }
    val maxV = points.maxOf { it.v }
    val span = (maxV - minV).let { if (it == 0.0) 1.0 else it }
    val t0 = points.first().t
    val t1 = points.last().t.let { if (it == t0) t0 + 1 else it }

    Column {
        Text(Fmt.number(maxV, units), style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val w = size.width
            val h = size.height
            for (i in 0..4) {
                val y = h * i / 4f
                drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
            }
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = (p.t - t0).toFloat() / (t1 - t0).toFloat() * w
                val y = h - ((p.v - minV) / span).toFloat() * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 3f, join = StrokeJoin.Round))
        }
        Text(Fmt.number(minV, units), style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Fmt.axis(t0, longRange), style = MaterialTheme.typography.labelSmall)
            Text(Fmt.axis(t1, longRange), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Problems resolved during the last 7 days. */
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val sp = LocalSpacing.current
    val load = rememberLoad(vm.active?.id) { vm.api().resolved() }
    val data = load.data

    Scaffold(topBar = {
        Bar(stringResource(R.string.history_title), vm::back) {
            IconButton(onClick = { load.reload() }) { Text("🔄", fontSize = 20.sp) }
        }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(sp.pad),
            verticalArrangement = Arrangement.spacedBy(sp.gap)
        ) {
            item {
                Text(
                    stringResource(R.string.history_range),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            load.error?.let { e -> item { ErrorBanner(errMsg(e)) } }
            if (load.loading && data == null) item { CircularProgressIndicator() }
            if (data != null && data.isEmpty()) item { Text(stringResource(R.string.history_none)) }
            items(data.orEmpty(), key = { it.eventId }) { r -> ResolvedCard(r) }
        }
    }
}

@Composable
private fun ResolvedCard(r: Resolved) {
    val sp = LocalSpacing.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(sp.pad), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.sevEmoji(r.severity), fontSize = 26.sp)
            Spacer(Modifier.width(sp.pad))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "🖥️ ${r.hostName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "🕒 ${Fmt.date(r.start)} → ${Fmt.date(r.end)}  (⏱️ ${Fmt.span((r.end - r.start).coerceAtLeast(0))})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("✅", fontSize = 18.sp)
        }
    }
}
