package com.nova.vpn.android

import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
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
    onDelete: (ImportedProfile) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onChooseBest: (List<ImportedProfile>) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { AppPreferencesStore(context) }
    var modeName by rememberSaveable { mutableStateOf(preferences.serverGrouping()) }
    val mode = ServerGrouping.entries.firstOrNull { it.name == modeName } ?: ServerGrouping.NONE
    var menu by remember { mutableStateOf(false) }
    var chosen by rememberSaveable(modeName) { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(preferences.favoritesOnly()) }
    var sortName by rememberSaveable { mutableStateOf(preferences.serverSort().takeIf { saved -> ServerSort.entries.any { it.name == saved } } ?: ServerSort.MANUAL.name) }
    var sortMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val autoStore = remember(context) { AutoServerSettingsStore(context) }
    val autoOptions by autoStore.state.collectAsState()
    val automaticState by AutoServerRuntime.state.collectAsState()
    var automaticError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(autoStore) { onDispose { autoStore.close() } }
    var editingProfile by remember { mutableStateOf<ImportedProfile?>(null) }
    var personalName by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var savingEdit by remember { mutableStateOf(false) }
    fun saveEdit(transform: (List<ImportedProfile>) -> List<ImportedProfile>) {
        if (savingEdit) return
        savingEdit = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { com.nova.vpn.android.data.ProfileVault(context).update(transform) } }
                .onSuccess { editingProfile = null; editError = null }
                .onFailure { editError = it.message?.takeIf { it == "Название: до 96 символов без переводов строк" }
                    ?: "Не удалось сохранить изменения." }
            savingEdit = false
        }
    }
    editingProfile?.let { profile ->
        AlertDialog(onDismissRequest = { if (!savingEdit) { editingProfile = null; editError = null } },
            title = { Text("Настройки сервера") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Название провайдера: ${profile.name}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = personalName, onValueChange = { personalName = it; editError = null },
                        label = { Text("Моё название") }, placeholder = { Text(profile.name) }, singleLine = true,
                        supportingText = { Text("Пустое поле — название провайдера") }, enabled = !savingEdit)
                    Text("Порядок действует при сортировке «Мой порядок».", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(enabled = !savingEdit, onClick = { sortName = ServerSort.MANUAL.name
                            saveEdit { com.nova.vpn.android.data.ProfileUserEdits.move(it, profile.id, -1) } }) { Text("Выше") }
                        TextButton(enabled = !savingEdit, onClick = { sortName = ServerSort.MANUAL.name
                            saveEdit { com.nova.vpn.android.data.ProfileUserEdits.move(it, profile.id, 1) } }) { Text("Ниже") }
                    }
                    editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(enabled = !savingEdit, onClick = {
                saveEdit { com.nova.vpn.android.data.ProfileUserEdits.rename(it, profile.id, personalName) }
            }) { Text("Сохранить") } }, dismissButton = { TextButton(enabled = !savingEdit, onClick = { editingProfile = null; editError = null }) { Text("Отмена") } })
    }
    LaunchedEffect(sortName, favoritesOnly) { preferences.setServerListOptions(sortName, favoritesOnly) }
    val map by produceState<ServerMapData?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { ServerMapData.load(context) }.getOrNull() }
    }
    val revision by com.nova.vpn.android.data.ProfileVault.changes.collectAsState()
    val snapshot by produceState<com.nova.vpn.android.data.ProfileSnapshot?>(null, revision) {
        value = withContext(Dispatchers.IO) { runCatching { com.nova.vpn.android.data.ProfileVault(context).loadSnapshot() }.getOrNull() }
    }
    val groups by produceState<Map<String, List<ImportedProfile>>>(emptyMap(), profiles, mode, map, snapshot) {
        value = withContext(Dispatchers.Default) {
            if (mode == ServerGrouping.SUBSCRIPTION && snapshot != null) subscriptionGroups(snapshot!!, profiles)
            else profiles.groupBy { profileGroup(it, mode) { name -> map?.resolve(name)?.country } }.toSortedMap()
        }
    }
    val effective = chosen?.takeIf { groups.containsKey(it) }
    val grouped = effective?.let { groups.getValue(it) } ?: profiles
    val visible by produceState(grouped, grouped, query, favoritesOnly, sortName, map, snapshot) {
        value = withContext(Dispatchers.Default) {
            val sources = snapshot?.subscriptions.orEmpty().associate { it.id to it.name }
            val names = snapshot?.memberships.orEmpty().groupBy { it.profileId }
                .mapValues { (_, memberships) -> memberships.mapNotNull { sources[it.subscriptionId] } }
            ServerListFilter.apply(grouped, query, favoritesOnly,
                ServerSort.valueOf(sortName), names) { name -> map?.resolve(name)?.country }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      // Keep a usable list viewport in landscape and with large fonts. Filters
      // can scroll independently instead of consuming all available height.
      val compact = maxHeight < 400.dp
      val controlsHeight = (maxHeight * if (compact) .25f else .45f)
          .coerceAtLeast(if (compact) 80.dp else 112.dp).coerceAtMost(maxHeight * .6f)
      Column(Modifier.fillMaxSize()) {
       Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Поиск серверов") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
          Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("Избранное") })
            Box {
                TextButton(onClick = { menu = true }) { Text("Группировка: ${mode.label}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ServerGrouping.entries.forEach { entry -> DropdownMenuItem(text = { Text(entry.label) }, onClick = {
                        modeName = entry.name; preferences.setServerGrouping(entry.name); chosen = null; menu = false
                    }) }
                }
            }
            Box {
                TextButton(onClick = { sortMenu = true }) { Text("Сортировка") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf(ServerSort.MANUAL to "Мой порядок", ServerSort.NAME to "Название", ServerSort.COUNTRY to "Страна",
                        ServerSort.LATENCY to "Задержка").forEach { (entry, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { sortName = entry.name; sortMenu = false })
                    }
                }
            }
            TextButton(onClick = { query = ""; favoritesOnly = false; chosen = null }) { Text("Сбросить") }
          }
            TextButton(onClick = onOpenSubscriptions) { Text("Подписки") }
        }
        Text("Найдено: ${visible.size} из ${profiles.size}", modifier = Modifier.padding(horizontal = 16.dp))
        FilterChip(selected = autoOptions.enabled, enabled = autoOptions.enabled || visible.isNotEmpty(),
            modifier = Modifier.padding(horizontal = 16.dp), label = { Text(if (autoOptions.enabled) "Авторежим включён" else "Авторежим") },
            onClick = { scope.launch {
                runCatching { withContext(Dispatchers.IO) { autoStore.save(!autoOptions.enabled, visible.map { it.id }.toSet()) } }
                    .onSuccess { automaticError = null }.onFailure { automaticError = "Не удалось сохранить авторежим." }
            } })
        if (autoOptions.enabled) Text(when {
            automaticState.checking -> "Проверяем доступность серверов…"
            !vpnState.connected && !vpnState.connecting -> "Авторежим начнёт работу после подключения VPN."
            automaticState.limited -> "Автопереключение временно отложено. Сервер можно выбрать вручную."
            automaticState.reason == AutoSwitchPolicy.Reason.CURRENT_FAILED -> "Сервер заменён после повторного отказа."
            automaticState.reason == AutoSwitchPolicy.Reason.BETTER_RESPONSE -> "Выбран сервер с меньшей задержкой ответа."
            else -> "Проверяем до 5 серверов за раунд. В наборе: ${autoOptions.candidateIds.size}."
        }, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        automaticError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChooseBest(visible) }, enabled = visible.isNotEmpty() && !checks.running && !vpnState.connecting) {
                Text("Выбрать лучший")
            }
            if (checks.running) TextButton(onClick = onCancelCheck) { Text("Отменить подбор") }
        }
        Text("Подбор среди найденных серверов по успешности HTTPS-проверок и задержке ответа.",
            modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (selectedId.isNotBlank() && visible.none { it.id == selectedId } && profiles.any { it.id == selectedId }) {
            TextButton(onClick = { query = ""; favoritesOnly = false; chosen = null }) { Text("Показать выбранный сервер") }
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
       }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalToggleFavorite provides onToggleFavorite,
                LocalEditServer provides { profile -> editingProfile = profile; personalName = profile.userSettings.personalName.orEmpty(); editError = null }) {
                if (visible.isEmpty() && profiles.isNotEmpty()) Text("Ничего не найдено. Измените поиск или сбросьте фильтры.", Modifier.padding(16.dp))
                else UngroupedServersPage(visible, selectedId, vpnState, checks, onCheck, onCancelCheck, onConnect,
                    error, shareStatus, shareProfileId, transferBusy, onAdd, onSelect, onSendToWatch, onDelete)
            }
        }
      }
    }
}

internal fun subscriptionGroups(snapshot: com.nova.vpn.android.data.ProfileSnapshot, profiles: List<ImportedProfile>): Map<String, List<ImportedProfile>> {
    val nodes = profiles.associateBy { it.id }
    val duplicateNames = snapshot.subscriptions.groupingBy { it.name }.eachCount()
    val links = snapshot.memberships.groupBy { it.subscriptionId }
    val result = sortedMapOf<String, List<ImportedProfile>>()
    snapshot.subscriptions.forEach { source ->
        val members = links[source.id].orEmpty().mapNotNull { nodes[it.profileId] }
        if (members.isNotEmpty()) result[if (duplicateNames.getValue(source.name) > 1) "${source.name} · ${source.id.take(8)}" else source.name] = members
    }
    val owned = snapshot.memberships.map { it.profileId }.toSet()
    val manual = profiles.filter { it.id in snapshot.manuallyOwned || it.id !in owned }
    if (manual.isNotEmpty()) result["Добавлены вручную"] = manual
    return result
}

internal val LocalToggleFavorite = staticCompositionLocalOf<(String) -> Unit> { {} }
internal val LocalEditServer = staticCompositionLocalOf<(ImportedProfile) -> Unit> { {} }

@Composable
internal fun FavoriteServerButton(profile: ImportedProfile) {
    val toggle = LocalToggleFavorite.current
    val edit = LocalEditServer.current
    Column {
    TextButton(onClick = { toggle(profile.id) }) {
        Text(if (profile.userSettings.favorite) "★ Убрать из избранного" else "☆ В избранное")
    }
    TextButton(onClick = { edit(profile) }) { Text("Название и порядок") }
    }
}
