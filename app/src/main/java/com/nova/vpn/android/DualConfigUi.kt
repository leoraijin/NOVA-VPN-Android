@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.nova.vpn.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun updateDualSettings(settings: AppRoutingSettings, multi: MultiServerSettings): AppRoutingSettings {
    val next = multi.copy(revision = requireNotNull(settings.multiServer).revision + 1)
    next.validate()
    return settings.copy(multiServer = next,
        history = MultiServerRoutingHistory(settings.history?.single, next))
}

internal fun selectDualServer(settings: AppRoutingSettings, id: String, secondary: Boolean): AppRoutingSettings =
    selectVpnSlot(settings, id, if (secondary) 2 else 1)

internal fun selectVpnSlot(settings: AppRoutingSettings, id: String, slot: Int): AppRoutingSettings {
    val multi = requireNotNull(settings.multiServer)
    val next = if (slot == 1) {
        require(id !in multi.slots.values) { "Этот конфиг уже назначен другому VPN" }
        multi.copy(defaultProfileId = id)
    } else multi.withSlot(slot, id)
    return updateDualSettings(settings, next)
}

@Composable
internal fun DualModeControl(settings: AppRoutingSettings, busy: Boolean, onToggle: (Boolean) -> Unit,
    onServers: () -> Unit, profiles: List<ImportedProfile>, vpnState: VpnUiState, onChange: (AppRoutingSettings) -> Unit) {
    val multi = settings.multiServer
    var choosingSlot by remember { mutableStateOf<Int?>(null) }
    var removingSlot by remember { mutableStateOf<Int?>(null) }
    var slotError by remember { mutableStateOf<String?>(null) }
    choosingSlot?.let { slot ->
        var query by remember(slot) { mutableStateOf("") }
        val candidates by produceState(emptyList<ImportedProfile>(), profiles) {
            value = withContext(Dispatchers.Default) {
                profiles.filter { !it.retiredFromSubscription && MultiServerProfileAdapter.problem(it) == null }
            }
        }
        val visible = remember(candidates, query) { candidates.filter { it.displayName.contains(query.trim(), ignoreCase = true) } }
        AlertDialog(onDismissRequest = { choosingSlot = null }, title = { Text("Выбрать VPN-$slot") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(query, { query = it }, label = { Text("Поиск сервера") }, singleLine = true)
                    slotError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                items(visible, key = { it.id }) { profile ->
                    NovaTextButton(enabled = !busy && profile.id != multi?.defaultProfileId && multi?.slots?.none { it.key != slot && it.value == profile.id } == true,
                        onClick = { runCatching { onChange(selectVpnSlot(settings, profile.id, slot)); choosingSlot = null }
                            .onFailure { slotError = it.message } }) { Text(profile.displayName) }
                }
            } }, confirmButton = { NovaTextButton(onClick = { choosingSlot = null }) { Text("Отмена") } })
    }
    removingSlot?.let { slot ->
        AlertDialog(onDismissRequest = { removingSlot = null }, title = { Text("Удалить VPN-$slot?") },
            text = { Text("Его отдельные правила сайтов и приложений будут удалены. Сам конфиг останется в списке серверов.") },
            confirmButton = { NovaTextButton(enabled = !busy, onClick = {
                onChange(updateDualSettings(settings, requireNotNull(multi).withSlot(slot, null))); removingSlot = null
            }) { Text("Удалить") } }, dismissButton = { NovaTextButton(onClick = { removingSlot = null }) { Text("Отмена") } })
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .novaGlassControl(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
        .then(if (LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS) Modifier.padding(horizontal = 12.dp, vertical = 8.dp) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClickLabel = if (expanded) "Свернуть настройки VPN" else "Развернуть настройки VPN") { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Несколько VPN-конфигов", style = MaterialTheme.typography.titleMedium)
                Text(if (busy) "Применяются настройки…" else if (multi != null && !multi.secondaryPaused) "Режим включён" else "Режим выключен",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Использовать несколько VPN", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    NovaSwitch(checked = multi != null && !multi.secondaryPaused, onCheckedChange = onToggle, enabled = !busy)
                }
                if (vpnState.connected || vpnState.connecting) {
                    val primaryName = profiles.firstOrNull { it.id == vpnState.appliedConfiguration?.profileId }?.displayName ?: "Выбранный сервер"
                    vpnState.exitLines(primaryName).forEach { line ->
                        Surface(color = if (line.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(line.title, style = MaterialTheme.typography.titleSmall)
                                Text(line.status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (multi != null) {
                    fun name(id: String?) = if (id == null) "Не выбран" else profiles.firstOrNull { it.id == id }?.displayName ?: "Конфиг недоступен"
                    Text("Основной VPN · ${name(multi.defaultProfileId)} · отключён", style = MaterialTheme.typography.bodySmall)
                    multi.slots.forEach { (slot, id) -> Text("VPN-$slot · ${name(id)} · отключён", style = MaterialTheme.typography.bodySmall) }
                }
                if (multi?.secondaryPaused == true) Text("Правила всех дополнительных VPN сохранены и сейчас идут через основной VPN.", style = MaterialTheme.typography.bodySmall)
                if (multi != null) {
                    multi.slots.forEach { (slot, id) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NovaTextButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = { slotError = null; choosingSlot = slot }) {
                                Text("VPN-$slot · " + (profiles.firstOrNull { it.id == id }?.displayName ?: "Недоступен"))
                            }
                            NovaTextButton(enabled = !busy, onClick = { removingSlot = slot }) { Text("Удалить") }
                        }
                    }
                    val free = (2..10).firstOrNull { it !in multi.slots }
                    NovaTextButton(enabled = !busy && free != null, onClick = { slotError = null; choosingSlot = free }) {
                        Text(if (free == null) "Добавлено 10 VPN" else "+ Добавить VPN")
                    }
                    NovaTextButton(onClick = onServers) { Text("Все серверы") }
                }
            }
        }
    }
}

internal data class DualServerActions(val primary: String, val secondary: String?, val busy: Boolean,
    val slots: Map<Int, String>, val select: (String, Int) -> Unit)
internal val LocalDualServerActions = staticCompositionLocalOf<DualServerActions?> { null }

@Composable
internal fun DualRouteChoices(target: ServerRouteTarget, multi: MultiServerSettings, busy: Boolean,
    onSelect: (ServerRouteTarget) -> Unit) {
    if (target.kind == ServerRouteKind.BLOCK) Text("Текущий маршрут · заблокировано")
    if (target.kind == ServerRouteKind.PROFILE && target.profileId !in multi.slots.values)
        Text(if (target.profileId == multi.defaultProfileId) "Текущий маршрут · основной конфиг" else "Текущий маршрут · ранее назначенный конфиг")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Напрямую" to ServerRouteTarget(ServerRouteKind.DIRECT),
            "Через VPN" to ServerRouteTarget(ServerRouteKind.DEFAULT)).forEach { (label, value) ->
            FilterChip(selected = target == value, enabled = !busy, onClick = { onSelect(value) },
                label = { Text(label) })
        }
        multi.slots.forEach { (slot, id) ->
            FilterChip(selected = target.profileId == id, enabled = !busy,
                onClick = { onSelect(ServerRouteTarget(ServerRouteKind.PROFILE, id)) }, label = { Text("Через VPN-$slot") })
        }
    }
}

@Composable
internal fun DualRoutingPage(settings: AppRoutingSettings, profiles: List<ImportedProfile>,
    apps: List<AppListEntry>, busy: Boolean, presetsContent: (@Composable () -> Unit)? = null, onChange: (AppRoutingSettings) -> Unit) {
    val multi = requireNotNull(settings.multiServer)
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(multi.slots.keys) { if (tab !in listOf(0, 1) && tab !in multi.slots && (tab - 20) !in multi.slots) tab = 0 }
    var input by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val packageRules = remember(multi.rules) { multi.rules.filter { it.packageName != null }.groupBy { it.packageName!! } }
    val siteRules = remember(multi.rules) { multi.rules.filter { it.domain != null } }
    val siteNames = remember(siteRules) { siteRules.mapNotNull { it.domain }.toSet() }
    val assignedPackages = packageRules.keys
    val groups by produceState<List<RoutingAppGroup>?>(null, apps, assignedPackages) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadRoutingAppGroups(context.packageManager, context.packageName, apps, assignedPackages) }.getOrNull()
        }
    }
    fun save(rules: List<MultiServerRule>) {
        runCatching { onChange(updateDualSettings(settings, multi.copy(rules = rules))) }
            .onFailure { error = it.message ?: "Не удалось изменить маршрут" }
    }
    PageFrame("Маршруты", "Основной VPN и дополнительные конфиги") {
        presetsContent?.let { panel -> item { panel() } }
        item {
            Text("Основной VPN · " + (profiles.firstOrNull { it.id == multi.defaultProfileId }?.displayName ?: "Недоступен"))
            multi.slots.forEach { (slot, id) -> Text("VPN-$slot · " + (profiles.firstOrNull { it.id == id }?.displayName ?: "Недоступен")) }
            if (multi.secondaryPaused) Text("Режим выключен. Правила дополнительных VPN сохранены и сейчас идут через основной VPN.")
            Text("Правило сайта имеет приоритет над правилом приложения.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(0 to "Сайты", 1 to "Приложения") + multi.slots.keys.flatMap { slot ->
                    listOf((20 + slot) to "Сайты через VPN-$slot", slot to "Приложения через VPN-$slot") }).forEach { (i, label) ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (tab == 0 || tab >= 22) {
            item {
                OutlinedTextField(value = input, onValueChange = { input = it; error = null },
                    label = { Text("Домен сайта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    val domain = SiteDomain.normalize(input)
                    when {
                        domain == null -> error = "Введите домен, например example.com"
                        domain in siteNames -> error = "Этот сайт уже добавлен"
                        else -> { save(multi.rules + MultiServerRule(domain = domain,
                            target = if (tab >= 22 && multi.slots[tab - 20] != null) ServerRouteTarget(ServerRouteKind.PROFILE, multi.slots[tab - 20]) else ServerRouteTarget(ServerRouteKind.DEFAULT))); input = "" }
                    }
                }) { Text("Добавить сайт") }
            }
            items(siteRules.filter { tab == 0 || it.target.profileId == multi.slots[tab - 20] }, key = { "site:${it.domain}:${it.includeSubdomains}" }) { rule ->
                SoftCard {
                    Text(requireNotNull(rule.domain))
                    DualRouteChoices(rule.target, multi, busy) { target ->
                        save(multi.rules.map { if (it == rule) it.copy(target = target) else it })
                    }
                    NovaTextButton(enabled = !busy, onClick = { save(multi.rules - rule) }) { Text("Удалить правило") }
                }
            }
        } else {
            item {
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Поиск приложений") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (tab in 2..10) Text("Включение VPN-$tab заменяет прежний маршрут приложения. Выключение возвращает основной VPN.")
                if (groups == null) Text("Загружаем приложения…")
            }
            items(groups.orEmpty().filter { group ->
                group.members.any { it.label.contains(search, true) || it.packageName.contains(search, true) }
            }, key = { it.key }) { group ->
                val targets = group.packages.map { name ->
                    packageRules[name]?.firstOrNull()?.target ?: ServerRouteTarget(ServerRouteKind.DEFAULT)
                }.distinct()
                val target = targets.singleOrNull() ?: ServerRouteTarget(ServerRouteKind.BLOCK)
                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.members.forEach { app ->
                            key(app.packageName) {
                                val appIcon by produceState(AppIconCache.peek(app.packageName), app.packageName) {
                                    value = AppIconCache.load(context.packageManager, app.packageName)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(Modifier.size(44.dp)) {
                                        appIcon?.let { bitmap ->
                                            Image(bitmap = bitmap, contentDescription = null,
                                                modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Text(app.label, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    group.unavailableReason?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        if (group.packages.any { name -> name in packageRules }) {
                            NovaTextButton(enabled = !busy,
                                onClick = { save(assignRoutingApps(multi.rules, group.packages, null)) }) {
                                Text("Удалить сохранённое правило")
                            }
                        }
                    }
                    if (tab in 2..10) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Через VPN-$tab", Modifier.weight(1f))
                            NovaSwitch(checked = multi.slots[tab] != null && target.profileId == multi.slots[tab],
                                enabled = !busy && group.unavailableReason == null && multi.slots[tab] != null,
                                onCheckedChange = { enabled ->
                                    val route = if (enabled) ServerRouteTarget(ServerRouteKind.PROFILE, requireNotNull(multi.slots[tab]))
                                        else ServerRouteTarget(ServerRouteKind.DEFAULT)
                                    save(assignRoutingApps(multi.rules, group.packages, route))
                                })
                        }
                    } else DualRouteChoices(target, multi, busy || group.unavailableReason != null) {
                        save(assignRoutingApps(multi.rules, group.packages, it))
                    }
                }
            }
        }
    }
}
