package com.nova.vpn.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.*

internal data class DnsControlState(val label: String, val enabled: Boolean, val open: () -> Unit)
internal val LocalDnsControl = staticCompositionLocalOf<DnsControlState?> { null }

@Composable
internal fun HomeDnsControl() {
    val control = LocalDnsControl.current ?: return
    NovaOutlinedButton(onClick = control.open, enabled = control.enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("DNS · ${control.label}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun CustomDnsDialog(settings: CustomDnsSettings, onDismiss: () -> Unit, onSave: (CustomDnsSettings, (String?) -> Unit) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var editing by remember { mutableStateOf<CustomDnsServer?>(null) }
    var adding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (!adding && editing == null) AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("DNS для сайтов") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Выбранные сайты используют этот DNS и прямое подключение. Домены включают поддомены. Для этого режима нужно ядро sing-box.", style = MaterialTheme.typography.bodyMedium)
            NovaTextButton(enabled = !saving, onClick = { draft = draft.copy(selectedId = null) }) {
                Text(if (draft.selectedId == null) "✓ Обычная маршрутизация" else "Обычная маршрутизация")
            }
            draft.servers.forEach { server ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        NovaTextButton(enabled = !saving, onClick = { draft = draft.copy(selectedId = server.id) }) {
                            Text((if (draft.selectedId == server.id) "✓ " else "") + server.name)
                        }
                        Text(server.address, style = MaterialTheme.typography.bodySmall)
                        Text("Сайтов — ${server.domains.size} · приложений — ${draft.appServers.values.count { it == server.id }}",
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            NovaTextButton(enabled = !saving, onClick = { editing = server }) { Text("Изменить") }
                            NovaTextButton(enabled = !saving, onClick = { draft = draft.copy(servers = draft.servers.filterNot { it.id == server.id },
                                selectedId = draft.selectedId.takeUnless { it == server.id },
                                appServers = draft.appServers.filterValues { it != server.id }) }) { Text("Удалить") }
                        }
                    }
                }
            }
            NovaOutlinedButton(onClick = { adding = true }, enabled = !saving && draft.servers.size < 20) { Text("Добавить DNS-сервер") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        NovaTextButton(enabled = !saving, onClick = {
            saving = true
            error = null
            onSave(draft) { failure ->
                saving = false
                error = failure
                if (failure == null) onDismiss()
            }
        }) { Text(if (saving) "Применяем…" else "Применить") }
    }, dismissButton = { NovaTextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") } })
    if (adding || editing != null) DnsServerEditor(editing, onDismiss = { adding = false; editing = null }) { server ->
        draft = draft.copy(servers = draft.servers.filterNot { it.id == server.id } + server, selectedId = server.id)
        adding = false
        editing = null
    }
}

@Composable
internal fun DnsServerEditor(server: CustomDnsServer?, onDismiss: () -> Unit, editSites: Boolean = true, onSave: (CustomDnsServer) -> Unit) {
    var name by remember { mutableStateOf(server?.name.orEmpty()) }
    var address by remember { mutableStateOf(server?.address.orEmpty()) }
    var domains by remember { mutableStateOf(server?.domains?.joinToString("\n").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (server == null) "Добавить DNS" else "Изменить DNS") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
            OutlinedTextField(address, { address = it }, label = { Text("Адрес DNS") },
                supportingText = { Text("9.9.9.9 · tls://dns.quad9.net · https://dns.google/dns-query") }, singleLine = true)
            if (editSites) OutlinedTextField(domains, { domains = it }, label = { Text("Сайты через DNS") },
                supportingText = { Text("По одному домену в строке, например example.com") }, minLines = 3, maxLines = 6)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { NovaTextButton(onClick = {
        runCatching {
            val sites = domains.lines().map(String::trim).filter(String::isNotEmpty).map {
                requireNotNull(SiteDomain.normalize(it)) { "Некорректный домен — $it" }
            }.distinct()
            CustomDnsServer(server?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), address.trim(), sites).also { it.validate() }
        }.onSuccess(onSave).onFailure { error = it.message ?: "Проверьте параметры DNS" }
    }) { Text("Сохранить") } }, dismissButton = { NovaTextButton(onClick = onDismiss) { Text("Отмена") } })
}
