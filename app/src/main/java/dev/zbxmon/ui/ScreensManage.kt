@file:OptIn(ExperimentalMaterial3Api::class)

package dev.zbxmon.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zbxmon.Donate
import dev.zbxmon.Fmt
import dev.zbxmon.R
import dev.zbxmon.data.*
import dev.zbxmon.errorText
import dev.zbxmon.notify.Notifier
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ProfilesScreen(vm: MainViewModel) {
    var toDelete by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        topBar = { Bar(stringResource(R.string.servers_title), vm::back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.push(Screen.ProfileEdit(null)) }) { Text("➕", fontSize = 22.sp) }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vm.profiles.isEmpty()) item { Text(stringResource(R.string.no_servers)) }
            items(vm.profiles, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = p.id == vm.active?.id, onClick = { vm.selectProfile(p.id) })
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(
                                    p.url + when {
                                        p.pinSha256.isNotBlank() -> "  🔐 pin"
                                        p.url.trim().startsWith("http://") -> "  ⚠️ http"
                                        else -> "  🔒"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔔")
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = p.enabled, onCheckedChange = { vm.saveProfile(p.copy(enabled = it)) })
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.push(Screen.ProfileEdit(p.id)) }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { toDelete = p }) { Text("🗑️") }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_title, p.name)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProfile(p.id); toDelete = null }) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun ProfileEditScreen(vm: MainViewModel, id: String?) {
    val ctx = LocalContext.current
    val existing = remember { vm.profiles.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "https://") }
    var user by remember { mutableStateOf(existing?.user ?: "") }
    var token by remember { mutableStateOf(existing?.let { runCatching { it.token }.getOrDefault("") } ?: "") }
    var showToken by remember { mutableStateOf(false) }
    var selfSigned by remember { mutableStateOf(existing?.pinSha256?.isNotBlank() == true) }
    var pin by remember { mutableStateOf(existing?.pinSha256 ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun build() = Profile(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        url = url.trim(),
        user = user.trim(),
        tokenEnc = Crypto.encrypt(token.trim()),
        pinSha256 = if (selfSigned) pin.trim() else "",
        enabled = existing?.enabled ?: true
    )

    Scaffold(
        topBar = {
            Bar(stringResource(if (existing == null) R.string.new_server else R.string.edit_server), vm::back)
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                name, { name = it }, label = { Text(stringResource(R.string.field_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                url, { url = it }, label = { Text(stringResource(R.string.field_url)) }, singleLine = true,
                supportingText = { Text(stringResource(R.string.field_url_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
            )
            if (url.trim().startsWith("http://")) {
                Text(stringResource(R.string.http_warning), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                user, { user = it }, label = { Text(stringResource(R.string.field_user)) }, singleLine = true,
                supportingText = { Text(stringResource(R.string.field_user_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                token, { token = it }, label = { Text(stringResource(R.string.field_token)) }, singleLine = true,
                supportingText = { Text(stringResource(R.string.field_token_hint)) },
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(if (showToken) "🙈" else "👁️", Modifier.clickable { showToken = !showToken }.padding(8.dp))
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = selfSigned, onCheckedChange = { selfSigned = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.self_signed))
            }
            if (selfSigned) {
                OutlinedTextField(
                    pin, { pin = it }, label = { Text(stringResource(R.string.field_fingerprint)) }, minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = runCatching { Tls.fetchFingerprint(url) }.fold(
                                { pin = it; ctx.getString(R.string.fp_fetched, it) },
                                { "❌ ${ctx.errorText(it)}" }
                            )
                            busy = false
                        }
                    }
                ) { Text(stringResource(R.string.btn_fetch_fp)) }
            }

            OutlinedButton(
                enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        status = runCatching { ZabbixApi(build()).check() }.fold(
                            { ctx.getString(R.string.test_ok, it) },
                            { "❌ ${ctx.errorText(it)}" }
                        )
                        busy = false
                    }
                }
            ) { Text(stringResource(R.string.btn_test)) }

            status?.let { Text(it) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Button(
                enabled = !busy && name.isNotBlank() && url.isNotBlank() && token.isNotBlank() &&
                    (!selfSigned || pin.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.saveProfile(build()); vm.back() }
            ) { Text(stringResource(R.string.btn_save)) }
        }
    }
}

/** Button that shows an HH:mm time (minutes after midnight) and opens a time picker. */
@Composable
private fun TimeButton(minutes: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(onClick = {
        TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
    }) { Text("%02d:%02d".format(minutes / 60, minutes % 60)) }
}

@Composable
fun ConfigScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val p = vm.prefs

    Scaffold(topBar = { Bar(stringResource(R.string.settings_title), vm::back) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Section(stringResource(R.string.sec_style))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeStyle.entries.forEach {
                    FilterChip(
                        selected = p.theme == it, onClick = { vm.updatePrefs(p.copy(theme = it)) },
                        label = { Text(stringResource(it.labelRes)) }
                    )
                }
            }

            Section(stringResource(R.string.sec_display))
            Text(stringResource(R.string.density_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Density.entries.forEach {
                    FilterChip(
                        selected = p.density == it, onClick = { vm.updatePrefs(p.copy(density = it)) },
                        label = { Text(stringResource(it.labelRes)) }
                    )
                }
            }
            Text(stringResource(R.string.refresh_label))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { s ->
                    FilterChip(
                        selected = p.uiRefreshSeconds == s, onClick = { vm.updatePrefs(p.copy(uiRefreshSeconds = s)) },
                        label = { Text("${s}s") }
                    )
                }
            }

            Section(stringResource(R.string.sec_language))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach {
                    FilterChip(
                        selected = p.language == it, onClick = { vm.updatePrefs(p.copy(language = it)) },
                        label = { Text(stringResource(it.labelRes)) }
                    )
                }
            }

            Section(stringResource(R.string.sec_notifications))
            Text(stringResource(R.string.notify_from))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..5).forEach { s ->
                    FilterChip(
                        selected = p.minSeverity == s,
                        onClick = { vm.updatePrefs(p.copy(minSeverity = s)) },
                        label = { Text("${Fmt.sevEmoji(s)} ${stringResource(Fmt.sevShortRes(s))}") }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = p.notifyRecovery, onCheckedChange = { vm.updatePrefs(p.copy(notifyRecovery = it)) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notify_recovery))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = p.digest, onCheckedChange = { vm.updatePrefs(p.copy(digest = it)) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notify_digest))
            }

            Section(stringResource(R.string.sec_quiet))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = p.quietEnabled, onCheckedChange = { vm.updatePrefs(p.copy(quietEnabled = it)) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quiet_switch))
            }
            if (p.quietEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.quiet_from))
                    TimeButton(p.quietStartMin) { vm.updatePrefs(p.copy(quietStartMin = it)) }
                    Text(stringResource(R.string.quiet_to))
                    TimeButton(p.quietEndMin) { vm.updatePrefs(p.copy(quietEndMin = it)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = p.quietAllowDisaster, onCheckedChange = { vm.updatePrefs(p.copy(quietAllowDisaster = it)) })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quiet_allow_disaster))
                }
                Text(stringResource(R.string.quiet_note), style = MaterialTheme.typography.bodySmall)
            }

            Section(stringResource(R.string.sec_polling))
            Text(stringResource(R.string.polling_note), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    FilterChip(
                        selected = p.pollMinutes == m, onClick = { vm.updatePrefs(p.copy(pollMinutes = m)) },
                        label = { Text("$m min") }
                    )
                }
            }

            Section(stringResource(R.string.sec_fg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = p.fgEnabled, onCheckedChange = { vm.updatePrefs(p.copy(fgEnabled = it)) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fg_switch))
            }
            if (p.fgEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120, 300).forEach { s ->
                        FilterChip(
                            selected = p.fgSeconds == s, onClick = { vm.updatePrefs(p.copy(fgSeconds = s)) },
                            label = { Text("${s}s") }
                        )
                    }
                }
            }

            Section(stringResource(R.string.sec_permissions))
            if (Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = { perm.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.btn_allow_notif))
                }
            }
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text(stringResource(R.string.btn_battery)) }
            OutlinedButton(onClick = { Notifier.test(ctx) }) { Text(stringResource(R.string.btn_test_notif)) }

            DonateSection()
        }
    }
}

private fun copyText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ZbxMon", text))
    Toast.makeText(ctx, ctx.getString(R.string.donate_copied), Toast.LENGTH_SHORT).show()
}

/** Opens a bitcoin: URI in whatever wallet app is installed. */
private fun openWallet(ctx: Context, uri: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, ctx.getString(R.string.donate_no_wallet), Toast.LENGTH_LONG).show()
    }
}

/** Donation info with copy / open-in-wallet buttons. */
@Composable
private fun DonateSection() {
    val ctx = LocalContext.current
    Section(stringResource(R.string.sec_donate))
    Text(stringResource(R.string.donate_text))

    Text(stringResource(R.string.donate_btc), fontWeight = FontWeight.Bold)
    SelectionContainer {
        Text(Donate.BTC_ADDRESS, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { copyText(ctx, Donate.BTC_ADDRESS) }) { Text(stringResource(R.string.donate_copy)) }
        OutlinedButton(onClick = { openWallet(ctx, "bitcoin:${Donate.BTC_ADDRESS}") }) {
            Text(stringResource(R.string.donate_open))
        }
    }
}
