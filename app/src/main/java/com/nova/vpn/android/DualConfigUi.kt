@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.nova.vpn.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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

internal fun selectDualServer(settings: AppRoutingSettings, id: String, secondary: Boolean): AppRoutingSettings {
    val multi = requireNotNull(settings.multiServer)
    require(id != if (secondary) multi.defaultProfileId else multi.secondaryProfileId) {
        "Для основного VPN и VPN-2 выберите разные конфиги"
    }
    val next = if (secondary) multi.copy(secondaryProfileId = id, rules = multi.rules.map { rule ->
        if (multi.secondaryProfileId != null && rule.target.profileId == multi.secondaryProfileId)
            rule.copy(target = ServerRouteTarget(ServerRouteKind.PROFILE, id)) else rule
    }) else multi.copy(defaultProfileId = id)
    return updateDualSettings(settings, next)
}

@Composable
internal fun DualModeControl(settings: AppRoutingSettings, busy: Boolean, onToggle: (Boolean) -> Unit,
    onServers: () -> Unit, profiles: List<ImportedProfile>) {
    val multi = settings.multiServer
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Два VPN-конфига", style = MaterialTheme.typography.titleMedium)
                Text(if (multi == null) "Второй сервер для выбранных приложений и сайтов"
                    else "Основной VPN и VPN-2", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = multi != null, onCheckedChange = onToggle, enabled = !busy)
        }
        if (multi != null) {
            fun name(id: String?) = if (id == null) "Выберите в «Серверах»"
                else profiles.firstOrNull { it.id == id }?.displayName ?: "Конфиг недоступен"
            Text("Основной VPN · ${name(multi.defaultProfileId)}", style = MaterialTheme.typography.bodySmall)
            Text("VPN-2 · ${name(multi.secondaryProfileId)}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onServers) { Text("Выбрать конфиги") }
        }
    }
}

internal data class DualServerActions(val primary: String, val secondary: String?, val busy: Boolean,
    val select: (String, Boolean) -> Unit)
internal val LocalDualServerActions = staticCompositionLocalOf<DualServerActions?> { null }

@Composable
internal fun DualRouteChoices(target: ServerRouteTarget, multi: MultiServerSettings, busy: Boolean,
    onSelect: (ServerRouteTarget) -> Unit) {
    if (target.kind == ServerRouteKind.BLOCK) Text("Текущий маршрут · заблокировано")
    if (target.kind == ServerRouteKind.PROFILE && target.profileId != multi.secondaryProfileId)
        Text(if (target.profileId == multi.defaultProfileId) "Текущий маршрут · основной конфиг" else "Текущий маршрут · ранее назначенный конфиг")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Напрямую" to ServerRouteTarget(ServerRouteKind.DIRECT),
            "Через VPN" to ServerRouteTarget(ServerRouteKind.DEFAULT)).forEach { (label, value) ->
            FilterChip(selected = target == value, enabled = !busy, onClick = { onSelect(value) },
                label = { Text(label) })
        }
        FilterChip(selected = multi.secondaryProfileId != null && target.profileId == multi.secondaryProfileId,
            enabled = !busy && multi.secondaryProfileId != null,
            onClick = { multi.secondaryProfileId?.let { onSelect(ServerRouteTarget(ServerRouteKind.PROFILE, it)) } },
            label = { Text("Через VPN-2") })
    }
}

@Composable
internal fun DualRoutingPage(settings: AppRoutingSettings, profiles: List<ImportedProfile>,
    apps: List<AppListEntry>, busy: Boolean, presetsContent: (@Composable () -> Unit)? = null, onChange: (AppRoutingSettings) -> Unit) {
    val multi = requireNotNull(settings.multiServer)
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val groups by produceState<List<RoutingAppGroup>?>(null, apps, multi.rules.mapNotNull { it.packageName }.toSet()) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadRoutingAppGroups(context.packageManager, context.packageName, apps,
                multi.rules.mapNotNull { it.packageName }.toSet()) }.getOrNull()
        }
    }
    fun save(rules: List<MultiServerRule>) {
        runCatching { onChange(updateDualSettings(settings, multi.copy(rules = rules))) }
            .onFailure { error = it.message ?: "Не удалось изменить маршрут" }
    }
    PageFrame("Маршруты", "Основной VPN и VPN-2") {
        presetsContent?.let { panel -> item { panel() } }
        item {
            Text("Основной VPN · " + (profiles.firstOrNull { it.id == multi.defaultProfileId }?.displayName ?: "Недоступен"))
            Text("VPN-2 · " + (profiles.firstOrNull { it.id == multi.secondaryProfileId }?.displayName ?: "Выберите конфиг во вкладке «Серверы»"))
            Text("Правило сайта имеет приоритет над правилом приложения.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Сайты", "Приложения", "Приложения через VPN-2").forEachIndexed { i, label ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (tab == 0) {
            item {
                OutlinedTextField(value = input, onValueChange = { input = it; error = null },
                    label = { Text("Домен сайта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    val domain = SiteDomain.normalize(input)
                    when {
                        domain == null -> error = "Введите домен, например example.com"
                        multi.rules.any { it.domain == domain } -> error = "Этот сайт уже добавлен"
                        else -> { save(multi.rules + MultiServerRule(domain = domain,
                            target = ServerRouteTarget(ServerRouteKind.DEFAULT))); input = "" }
                    }
                }) { Text("Добавить сайт") }
            }
            items(multi.rules.filter { it.domain != null }, key = { "site:${it.domain}:${it.includeSubdomains}" }) { rule ->
                SoftCard {
                    Text(requireNotNull(rule.domain))
                    DualRouteChoices(rule.target, multi, busy) { target ->
                        save(multi.rules.map { if (it == rule) it.copy(target = target) else it })
                    }
                    TextButton(enabled = !busy, onClick = { save(multi.rules - rule) }) { Text("Удалить правило") }
                }
            }
        } else {
            item {
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Поиск приложений") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (tab == 2) Text("Включение VPN-2 заменяет прежний маршрут приложения. Выключение возвращает основной VPN.")
                if (groups == null) Text("Загружаем приложения…")
            }
            items(groups.orEmpty().filter { group ->
                group.members.any { it.label.contains(search, true) || it.packageName.contains(search, true) }
            }, key = { it.key }) { group ->
                val targets = group.packages.map { name ->
                    multi.rules.firstOrNull { it.packageName == name }?.target ?: ServerRouteTarget(ServerRouteKind.DEFAULT)
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
                    group.unavailableReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (tab == 2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Через VPN-2", Modifier.weight(1f))
                            Switch(checked = multi.secondaryProfileId != null && target.profileId == multi.secondaryProfileId,
                                enabled = !busy && group.unavailableReason == null && multi.secondaryProfileId != null,
                                onCheckedChange = { enabled ->
                                    val route = if (enabled) ServerRouteTarget(ServerRouteKind.PROFILE, requireNotNull(multi.secondaryProfileId))
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
