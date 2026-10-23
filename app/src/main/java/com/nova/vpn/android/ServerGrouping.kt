package com.nova.vpn.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.AppPreferencesStore
import com.nova.vpn.android.data.ImportedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI

internal enum class ServerGrouping(val label: String) {
    NONE("Без группировки"), COUNTRY("Страна"), TRANSPORT("Транспорт"), PROTOCOL("Протокол"), SUBSCRIPTION("Подписка")
}

internal fun profileTransport(profile: ImportedProfile): String = runCatching {
    if (profile.fullConfigJson != null) return "Полная конфигурация"
    JSONObject(profile.outboundJson).optJSONObject("transport")?.optString("type")
        ?.takeIf(String::isNotBlank)?.uppercase() ?: when (profile.protocol.lowercase()) {
            "hysteria2", "tuic" -> "QUIC"
            "shadowsocks" -> "TCP / UDP"
            else -> "TCP"
        }
}.getOrDefault("Неизвестный транспорт")

internal fun profileGroup(profile: ImportedProfile, mode: ServerGrouping, country: (String) -> String?): String = when (mode) {
    ServerGrouping.NONE -> "Все"
    ServerGrouping.COUNTRY -> country(profile.name) ?: "Страна не указана"
    ServerGrouping.TRANSPORT -> profileTransport(profile)
    ServerGrouping.PROTOCOL -> profile.protocol.uppercase()
    ServerGrouping.SUBSCRIPTION -> profile.subscriptionUrl?.let { runCatching { URI(it).host }.getOrNull() }
        ?: "Добавлены вручную"
}

/** Shared grouping for every design; existing cards, selection and actions stay in one place. */
@Composable
internal fun ServersPage(
    profiles: List<ImportedProfile>, selectedId: String, vpnState: VpnUiState,
    checks: ServerCheckState, onCheck: () -> Unit, onCancelCheck: () -> Unit, onConnect: () -> Unit,
    error: String?, shareStatus: String?, shareProfileId: String?, transferBusy: Boolean,
    onAdd: () -> Unit, onSelect: (String) -> Unit, onSendToWatch: (ImportedProfile) -> Unit,
    onDelete: (ImportedProfile) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { AppPreferencesStore(context) }
    var modeName by rememberSaveable { mutableStateOf(preferences.serverGrouping()) }
    val mode = ServerGrouping.entries.firstOrNull { it.name == modeName } ?: ServerGrouping.NONE
    var menu by remember { mutableStateOf(false) }
    var chosen by rememberSaveable(modeName) { mutableStateOf<String?>(null) }
    val map by produceState<ServerMapData?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { ServerMapData.load(context) }.getOrNull() }
    }
    val groups by produceState<Map<String, List<ImportedProfile>>>(emptyMap(), profiles, mode, map) {
        value = withContext(Dispatchers.Default) {
            profiles.groupBy { profileGroup(it, mode) { name -> map?.resolve(name)?.country } }.toSortedMap()
        }
    }
    val effective = chosen?.takeIf { groups.containsKey(it) }
    val visible = effective?.let { groups.getValue(it) } ?: profiles
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Box {
                TextButton(onClick = { menu = true }) { Text("Группировка: ${mode.label}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ServerGrouping.entries.forEach { entry -> DropdownMenuItem(text = { Text(entry.label) }, onClick = {
                        modeName = entry.name; preferences.setServerGrouping(entry.name); chosen = null; menu = false
                    }) }
                }
            }
            TextButton(onClick = onAdd) { Text("Добавить") }
        }
        if (mode != ServerGrouping.NONE) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = effective == null, onClick = { chosen = null }, label = { Text("Все · ${profiles.size}") })
                groups.forEach { (label, members) -> FilterChip(selected = effective == label,
                    onClick = { chosen = label }, label = { Text("$label · ${members.size}") }) }
            }
            if (mode == ServerGrouping.COUNTRY) Text("Страна определяется по названию или флагу сервера.",
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.weight(1f)) {
            UngroupedServersPage(visible, selectedId, vpnState, checks, onCheck, onCancelCheck, onConnect,
                error, shareStatus, shareProfileId, transferBusy, onAdd, onSelect, onSendToWatch, onDelete)
        }
    }
}
