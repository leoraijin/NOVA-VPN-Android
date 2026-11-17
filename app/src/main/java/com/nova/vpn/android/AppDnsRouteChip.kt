package com.nova.vpn.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppDnsRouteChip(settings: AppRoutingSettings, packages: Set<String>, busy: Boolean, onChange: (AppRoutingSettings, (String?) -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newServer by remember { mutableStateOf<CustomDnsServer?>(null) }
    val ids = packages.map { settings.customDns.appServers[it] }.distinct()
    val selectedId = ids.singleOrNull()
    val selected = settings.customDns.servers.singleOrNull { it.id == selectedId }
    FilterChip(selected = selected != null, enabled = !busy && !saving,
        onClick = { choosing = true; error = null }, label = { Text(selected?.let { "DNS · ${it.name}" } ?: "Через DNS") })
    fun choose(serverId: String) {
        saving = true
        error = null
        scope.launch {
            runCatching {
                require(Build.VERSION.SDK_INT >= 29) { "DNS для приложений требует Android 10 или новее" }
                val group = withContext(Dispatchers.IO) {
                    SharedUidRouting.groupFor(packages.first(), { name ->
                        runCatching { context.packageManager.getApplicationInfo(name, 0).uid }.getOrNull()
                    }, { uid -> context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty() })
                }
                require(context.packageName !in group) { "Группе NOVA VPN нельзя назначить отдельный DNS" }
                val updated = newServer?.takeIf { it.id == serverId && settings.customDns.servers.none { saved -> saved.id == it.id } }
                    ?.let { settings.customDns.copy(servers = settings.customDns.servers + it) } ?: settings.customDns
                onChange(assignDnsApps(settings.copy(customDns = updated), group, serverId)) { failure ->
                    saving = false
                    error = failure
                    if (failure == null) {
                        choosing = false
                        newServer = null
                    }
                }
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                adding = false
                saving = false
                error = it.message ?: "Не удалось назначить DNS приложению"
            }
        }
    }
    val servers = settings.customDns.servers + listOfNotNull(newServer?.takeUnless { draft ->
        settings.customDns.servers.any { it.id == draft.id }
    })
    if (choosing && !adding) AlertDialog(onDismissRequest = { if (!saving) choosing = false },
        title = { Text("DNS для приложения") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Приложение использует выбранный DNS и прямое подключение.")
                if (servers.isEmpty()) Text("Добавьте DNS-сервер, чтобы назначить его приложению.")
                servers.forEach { server ->
                    NovaOutlinedButton(onClick = { choose(server.id) }, enabled = !saving && !busy, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text((if (selectedId == server.id) "✓ " else "") + server.name)
                            Text(server.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                NovaTextButton(onClick = { adding = true }, enabled = !saving && !busy && servers.size < 20) { Text("Добавить DNS-сервер") }
                if (saving) Text("Сохраняем DNS и применяем маршрут…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { NovaTextButton(onClick = { choosing = false }, enabled = !saving) { Text("Закрыть") } })
    if (adding) DnsServerEditor(null, onDismiss = { adding = false }, editSites = false) { server ->
        newServer = server
        adding = false
        choose(server.id)
    }
}
