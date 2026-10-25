package com.nova.vpn.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.vpn.android.data.*

/** Fluent content layers adapted to touch; no desktop window controls on a phone. */
@Composable
internal fun WindowsFrame(title: String, subtitle: String, tutorialTargetIndex: Int = 1, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val step = LocalTutorialStep.current
    LaunchedEffect(step, tutorialTargetIndex) { if (step != null) state.scrollToItem(tutorialTargetIndex) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text("NOVA VPN", Modifier.padding(start = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
internal fun WindowsCard(target: TutorialStep? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = c.surface,
        border = BorderStroke(if (target != null && LocalTutorialStep.current == target) 2.dp else 1.dp,
            if (target != null && LocalTutorialStep.current == target) c.primary else c.outlineVariant)) { Column(content = content) }
}

@Composable
private fun WindowsRow(title: String, detail: String? = null, icon: ImageVector = Icons.Outlined.Settings,
    onClick: (() -> Unit)? = null, expanded: Boolean? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            detail?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailing != null) trailing()
        else if (expanded != null) NovaDisclosure(expanded)
        else if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
    }
}

@Composable
private fun WindowsHeading(text: String) { Text(text, Modifier.padding(top = 16.dp, bottom = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }

@Composable
internal fun WindowsHome(profiles: List<ImportedProfile>, selectedId: String, profile: ImportedProfile?, state: VpnUiState,
    error: String?, onCopy: () -> Unit, onSelect: (String) -> Unit, onToggle: () -> Unit, onManage: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var choose by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val status = when {
        state.recoveryStatus != null -> state.recoveryText().orEmpty()
        error != null || state.warning != null -> "Требуется внимание"
        state.trafficChecking -> "Проверка доступа"
        state.trafficVerified -> "Доступ подтверждён"
        state.connected -> "Туннель включён"
        state.connecting -> "Подключение…"
        else -> "Не подключено"
    }
    WindowsFrame("Сеть и VPN", "Управление защищённым подключением") {
        item {
            WindowsCard(TutorialStep.Connect) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).background(c.primaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Shield, null, Modifier.size(32.dp), tint = c.primary)
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text("VPN", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(status, fontSize = 13.sp, lineHeight = 19.sp, color = if (error != null || state.warning != null) c.error else c.onSurfaceVariant)
                        }
                    }
                    if (state.connecting || state.trafficChecking) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.primary)
                    Text(when {
                        profile == null -> "Добавьте подключение вашего VPN-провайдера."
                        state.trafficVerified -> "Контрольный HTTPS-запрос через VPN выполнен."
                        state.connected -> "Доступ через VPN пока не подтверждён."
                        else -> "Подключитесь, чтобы направить трафик через выбранный сервер."
                    }, fontSize = 13.sp, lineHeight = 19.sp, color = c.onSurfaceVariant)
                    NovaButton(if (profile == null && !state.connected) onManage else onToggle,
                        Modifier.fillMaxWidth()) {
                        Text(if (profile == null && !state.connected) "Добавить подключение" else if (state.connecting) "Отменить" else if (state.connected) "Отключить" else "Подключить")
                    }
                }
                HorizontalDivider(color = c.outlineVariant)
                Row(Modifier.fillMaxWidth().background(c.surfaceVariant).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Public, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
                    Text(profile?.name ?: "Подключение не настроено", Modifier.weight(1f).padding(start = 10.dp), fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        if (error != null || state.warning != null || state.transportNotice != null) item {
            WindowsCard { WindowsRow("Сведения о соединении", error ?: state.warning ?: state.transportNotice, Icons.Outlined.Info) }
        }
        item { WindowsHeading("Параметры подключения") }
        item {
            WindowsCard {
                WindowsRow("Выбранный сервер", profile?.name ?: "Добавьте конфигурацию", Icons.Outlined.Dns,
                    { if (profiles.isEmpty()) onManage() else choose = !choose }, choose)
                AnimatedVisibility(choose) {
                    Column(Modifier.selectableGroup().background(c.surfaceVariant)) {
                        profiles.forEach { p ->
                            Row(Modifier.fillMaxWidth().selectable(p.id == selectedId, role = Role.RadioButton, onClick = { onSelect(p.id); choose = false })
                                .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(p.id == selectedId, null); Text(p.displayName, Modifier.weight(1f), fontSize = 14.sp)
                            }
                        }
                        NovaTextButton(onManage, Modifier.padding(horizontal = 12.dp)) { Text("Управление серверами") }
                    }
                }
            }
        }
        item { WindowsCard { WindowsRow("Протокол", profile?.protocol?.uppercase() ?: "Не задан", Icons.Outlined.Lock) } }
        item {
            WindowsCard {
                WindowsRow("Диагностика", "События и состояние VPN", Icons.Outlined.Info, { details = !details }, details)
                AnimatedVisibility(details) {
                    Column(Modifier.fillMaxWidth().background(c.surfaceVariant).padding(16.dp)) {
                        Text("Журнал без ключей, адресов сервера и истории сайтов.", fontSize = 13.sp, lineHeight = 19.sp)
                        NovaOutlinedButton(onCopy, Modifier.padding(top = 12.dp)) { Text("Открыть диагностику") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WindowsSettings(onTutorial: () -> Unit, onNotifications: () -> Unit, auto: Boolean,
    appearance: Appearance, updates: Boolean, checking: Boolean, updateStatus: String?, error: String?,
    onTile: () -> Unit, onProduct: (String) -> Unit, onAppearance: (Appearance) -> Unit,
    onUpdates: (Boolean) -> Unit, onCheck: () -> Unit, onAuto: (Boolean) -> Unit) {
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var updateOpen by rememberSaveable { mutableStateOf(false) }
    val tutorial = LocalTutorialStep.current
    LaunchedEffect(tutorial) { if (tutorial == TutorialStep.Updates) updateOpen = true }
    WindowsFrame("Параметры", "Настройте NOVA под себя", tutorialTargetIndex = if (error == null) 8 else 9) {
        item { WindowsHeading("Персонализация") }
        item {
            WindowsCard {
                WindowsRow("Оформление", "Windows 11 · " + if (appearance.isDark) "Тёмная" else "Светлая", Icons.Outlined.Palette, { appearanceOpen = !appearanceOpen }, appearanceOpen)
                AnimatedVisibility(appearanceOpen) { Column(Modifier.padding(16.dp)) { AppearancePicker(appearance, onAppearance) } }
            }
        }
        item { WindowsHeading("Подключение") }
        item { WindowsCard { WindowsRow("Автоподключение", "При открытии NOVA", Icons.Outlined.PowerSettingsNew, trailing = { NovaSwitch(auto, onAuto) }) } }
        item { WindowsCard { WindowsRow("Уведомление VPN", "Скрыть / показать карточку NOVA", Icons.Outlined.Notifications, onNotifications) } }
        item { WindowsCard { WindowsRow("Быстрое управление", "Добавить плитку в шторку", Icons.Outlined.Tune, onTile) } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { WindowsHeading("Обновления и помощь") }
        item {
            WindowsCard(TutorialStep.Updates) {
                WindowsRow("Обновления NOVA", updateStatus ?: "Публичные релизы", Icons.Outlined.SystemUpdate, { updateOpen = !updateOpen }, updateOpen)
                AnimatedVisibility(updateOpen) {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                        WindowsRow("Проверять автоматически", trailing = { NovaSwitch(updates, onUpdates) })
                        NovaOutlinedButton(onCheck, Modifier.padding(16.dp), enabled = !checking) { Text(if (checking) "Проверяем…" else "Проверить сейчас") }
                    }
                }
            }
        }
        item { WindowsCard { WindowsRow("Знакомство с NOVA", "Пошаговая настройка", Icons.Outlined.HelpOutline, onTutorial) } }
        item { WindowsCard { WindowsRow("Хранилище профилей", "AES-GCM · Android Keystore", Icons.Outlined.Lock) } }
        item { WindowsHeading("Другие устройства") }
        item { WindowsCard { WindowsRow("NOVA для Windows", "Открыть GitHub", Icons.Outlined.DesktopWindows, { onProduct("https://github.com/leoraijin/NOVA-VPN") }) } }
        item { WindowsCard { WindowsRow("NOVA для часов", "Открыть GitHub", Icons.Outlined.Watch, { onProduct("https://github.com/leoraijin/NOVA-VPN-WearOS") }) } }
        item { Text("NOVA VPN · ${BuildConfig.VERSION_NAME}", Modifier.padding(vertical = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { EngineSettingsCard() }
    }
}

@Composable
internal fun WindowsServers(profiles: List<ImportedProfile>, selectedId: String, state: VpnUiState,
    checks: ServerCheckState, onCheck: () -> Unit, onCancel: () -> Unit, onConnect: () -> Unit,
    error: String?, shareStatus: String?, shareId: String?, busy: Boolean, onAdd: () -> Unit,
    onSelect: (String) -> Unit, onWatch: (ImportedProfile) -> Unit, onDelete: (ImportedProfile) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showMap by rememberSaveable { mutableStateOf(false) }
    val tutorial = LocalTutorialStep.current
    LaunchedEffect(tutorial) { if (tutorial == TutorialStep.Servers) showMap = true }
    val visible = remember(profiles, query) { profiles.filter { it.name.contains(query, true) || it.protocol.contains(query, true) } }
    val c = MaterialTheme.colorScheme
    WindowsFrame("VPN-подключения", "Управление сохранёнными профилями", tutorialTargetIndex = if (tutorial == TutorialStep.Servers) 2 else 1) {
        item {
            WindowsCard(TutorialStep.Import) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NovaTextButton(onAdd) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Добавить") }
                    VerticalDivider(Modifier.height(24.dp), color = c.outlineVariant)
                    NovaTextButton({ showMap = !showMap }) { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Карта") }
                }
            }
        }
        item {
            AnimatedVisibility(showMap) { WindowsCard(TutorialStep.Servers) { ServerMapPanel(profiles, selectedId, checks, state, onCheck, onCancel, onSelect, onConnect) } }
        }
        item {
            TextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Найти подключение", fontSize = 14.sp) }, singleLine = true,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                trailingIcon = { if (query.isEmpty()) Icon(Icons.Outlined.Search, null) else IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "Очистить поиск") } },
                colors = TextFieldDefaults.colors(focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, focusedIndicatorColor = c.primary, unfocusedIndicatorColor = c.outline))
        }
        error?.let { item { Text(it, color = c.error, modifier = Modifier.padding(12.dp)) } }
        item { WindowsHeading("Подключения · ${visible.size}") }
        if (visible.isEmpty()) item { WindowsCard { WindowsRow(if (profiles.isEmpty()) "Пока нет подключений" else "Ничего не найдено", if (profiles.isEmpty()) "Добавьте ссылку, QR-код или файл." else "Измените запрос или очистите поиск.", Icons.Outlined.Search) } }
        items(visible, key = { it.id }) { profile ->
            var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
            WindowsCard {
                Row(Modifier.fillMaxWidth().background(if (selectedId == profile.id) c.surfaceVariant else c.surface), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(start = 3.dp).width(3.dp).height(24.dp).background(if (selectedId == profile.id) c.primary else Color.Transparent, RoundedCornerShape(2.dp)))
                    Row(Modifier.weight(1f).selectable(selectedId == profile.id, role = Role.RadioButton, onClick = { onSelect(profile.id) }).heightIn(min = 80.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Dns, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(profile.displayName, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                            FavoriteServerButton(profile)
                            Text(profile.protocol.uppercase() + if (selectedId == profile.id) " · Выбран" else "", fontSize = 12.sp, color = c.onSurfaceVariant)
                        }
                    }
                    IconButton({ expanded = !expanded }) { NovaDisclosure(expanded) }
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Адрес сервера", fontSize = 12.sp, color = c.onSurfaceVariant)
                        Text(profile.server, fontSize = 14.sp)
                        NovaOutlinedButton({ onWatch(profile) }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (busy && shareId == profile.id) "Передача…" else "Передать на часы") }
                        if (shareId == profile.id) shareStatus?.let { Text(it, fontSize = 13.sp) }
                        NovaTextButton({ onDelete(profile) }) { Text("Удалить подключение", color = c.error) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WindowsRouteTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Сайты", "Приложения").forEachIndexed { i, label ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(if (selected == i) c.surface else c.surfaceVariant)
                .selectable(selected == i, role = Role.Tab, onClick = { onSelect(i) }).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 14.sp, fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected == i) c.primary else c.outlineVariant))
            }
        }
    }
}

@Composable
internal fun WindowsRouteMode(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.selectableGroup()) {
        listOf("Через VPN", "Напрямую").forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().selectable(selected == i, role = Role.RadioButton, onClick = { onSelect(i) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == i, null); Text(label, fontSize = 14.sp)
            }
        }
    }
}
