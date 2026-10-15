package com.nova.vpn.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.vpn.android.data.*

/** A viewing area above reachable grouped controls; scrolling reveals a compact title. */
@Composable
internal fun OneUIFrame(title: String, subtitle: String, tutorialTargetIndex: Int = 1,
    icon: ImageVector? = null, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 } }
    val config = LocalConfiguration.current
    val compact = config.screenHeightDp < 650 || config.fontScale > 1.3f
    val step = LocalTutorialStep.current
    LaunchedEffect(step, tutorialTargetIndex) { if (step != null) state.scrollToItem(tutorialTargetIndex) }
    Column(Modifier.fillMaxSize()) {
        Crossfade(collapsed, label = "one-ui-header") { small ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (small) title else "NOVA VPN", fontSize = if (small) 20.sp else 13.sp,
                    fontWeight = if (small) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (small) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(contentType = "one-ui-viewing-area") {
                Column(Modifier.fillMaxWidth().heightIn(min = if (compact) 120.dp else 206.dp).padding(horizontal = 12.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    icon?.let { Icon(it, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)) }
                    Text(title, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(subtitle, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            content()
        }
    }
}

@Composable
internal fun OneUIGroup(target: TutorialStep? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(Modifier.fillMaxWidth().then(if (target != null && LocalTutorialStep.current == target)
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        color = MaterialTheme.colorScheme.surface, shape = shape) { Column(content = content) }
}

@Composable
internal fun OneUIDivider() { HorizontalDivider(Modifier.padding(start = 64.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)) }

@Composable
internal fun OneUIRow(title: String, detail: String? = null, icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .heightIn(min = 68.dp).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Box(Modifier.size(32.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(it, null, Modifier.size(19.dp), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
            detail?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        else if (onClick != null) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun OneUIHome(profiles: List<ImportedProfile>, selectedId: String, profile: ImportedProfile?, state: VpnUiState,
    error: String?, onCopy: () -> Unit, onSelect: (String) -> Unit, onToggle: () -> Unit, onManage: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var choose by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val status = when {
        error != null || state.warning != null -> "Нужна проверка"
        state.trafficChecking -> "Проверяем доступ через VPN"
        state.trafficVerified -> "Доступ через VPN подтверждён"
        state.connected -> "Туннель включён"
        state.connecting -> "Подключаемся к серверу"
        else -> "Не подключено"
    }
    OneUIFrame("Подключение", status, icon = Icons.Rounded.Shield) {
        item {
            val control = if (profile == null && !state.connected) Modifier.clickable(role = Role.Button, onClick = onManage)
                else Modifier.toggleable(state.connected, enabled = !state.connecting, role = Role.Switch, onValueChange = { onToggle() })
            Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).then(control)
                .then(if (LocalTutorialStep.current == TutorialStep.Connect) Modifier.border(2.dp, c.primary, RoundedCornerShape(24.dp)) else Modifier),
                color = if (state.connected) c.primary else c.surface, contentColor = if (state.connected) c.onPrimary else c.onSurface,
                shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(24.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (profile == null && !state.connected) "Добавить сервер" else if (state.connecting) "Подключение…" else if (state.connected) "Включено" else "Выключено", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (profile == null && !state.connected) "Для первого подключения" else if (state.connected) "Нажмите, чтобы отключить" else "Нажмите, чтобы подключить", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    if (state.connecting) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    else if (profile == null && !state.connected) Icon(Icons.Rounded.Add, null, Modifier.size(28.dp))
                    else Switch(state.connected, null, colors = SwitchDefaults.colors(checkedThumbColor = c.primary, checkedTrackColor = c.onPrimary,
                        uncheckedTrackColor = c.outlineVariant, uncheckedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White))
                }
            }
        }
        if (error != null || state.warning != null || state.transportNotice != null || state.connected) item {
            OneUIGroup {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: state.warning ?: state.transportNotice ?: if (state.trafficVerified) "HTTPS-запрос через VPN выполнен." else "Доступ через VPN пока не подтверждён.",
                        fontSize = 14.sp, color = if (error != null || state.warning != null) c.error else c.onSurfaceVariant)
                    if (state.trafficCheckFailed) Text("Туннель может продолжать работать. Проверьте соединение перед использованием.", fontSize = 13.sp, color = c.onSurfaceVariant)
                }
            }
        }
        item {
            OneUIGroup {
                OneUIRow("Сервер", profile?.name ?: "Не выбран", Icons.Rounded.Public, Color(0xFF3478C9),
                    { if (profiles.isEmpty()) onManage() else choose = !choose })
                AnimatedVisibility(choose) {
                    Column(Modifier.selectableGroup()) {
                        profiles.forEach { p ->
                            Row(Modifier.fillMaxWidth().selectable(p.id == selectedId, role = Role.RadioButton,
                                onClick = { onSelect(p.id); choose = false }).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, Modifier.weight(1f)); RadioButton(p.id == selectedId, null)
                            }
                        }
                        NovaTextButton(onManage, Modifier.padding(horizontal = 16.dp)) { Text("Все серверы") }
                    }
                }
                OneUIDivider()
                OneUIRow("Протокол", profile?.protocol?.uppercase() ?: "Появится после добавления сервера", Icons.Rounded.Lock, Color(0xFF558A47))
            }
        }
        item {
            OneUIGroup {
                OneUIRow("Состояние и диагностика", "Сведения о подключении", Icons.Rounded.Info, Color(0xFF8971BE), { details = !details })
                AnimatedVisibility(details) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NOVA VPN ${BuildConfig.VERSION_NAME}", fontSize = 13.sp)
                        Text("Журнал не содержит ключей, адресов сервера и истории сайтов.", fontSize = 13.sp, color = c.onSurfaceVariant)
                        NovaTextButton(onCopy) { Text("Скопировать диагностику") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OneUISettings(onTutorial: () -> Unit, onNotifications: () -> Unit, auto: Boolean,
    appearance: Appearance, updates: Boolean, checking: Boolean, updateStatus: String?, error: String?,
    onTile: () -> Unit, onProduct: (String) -> Unit, onAppearance: (Appearance) -> Unit,
    onUpdates: (Boolean) -> Unit, onCheck: () -> Unit, onAuto: (Boolean) -> Unit) {
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    OneUIFrame("Настройки", "NOVA VPN", tutorialTargetIndex = 3) {
        item {
            OneUIGroup {
                OneUIRow("Оформление", "One UI 9 · " + if (appearance.isDark) "Тёмная" else "Светлая", Icons.Rounded.Palette, Color(0xFF8270BE), { appearanceOpen = !appearanceOpen })
                AnimatedVisibility(appearanceOpen) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { AppearancePicker(appearance, onAppearance) } }
            }
        }
        item {
            OneUIGroup {
                OneUIRow("Автоподключение", "При открытии приложения", Icons.Rounded.PowerSettingsNew, Color(0xFF3478C9), trailing = { NovaSwitch(auto, onAuto) })
                OneUIDivider()
                OneUIRow("Уведомления", "Состояние VPN", Icons.Rounded.Notifications, Color(0xFFBD7736), onNotifications)
                OneUIDivider()
                OneUIRow("Плитка в шторке", "Быстрое управление", Icons.Rounded.Tune, Color(0xFF558A47), onTile)
                error?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            OneUIGroup(TutorialStep.Updates) {
                OneUIRow("Проверять обновления", "Автоматически", Icons.Rounded.SystemUpdate, Color(0xFF3478C9), trailing = { NovaSwitch(updates, onUpdates) })
                OneUIDivider()
                OneUIRow(if (checking) "Проверяем…" else "Проверить сейчас", updateStatus ?: "Публичные релизы NOVA", onClick = if (checking) null else onCheck)
            }
        }
        item {
            OneUIGroup {
                OneUIRow("Знакомство с NOVA", "Шесть шагов для начала работы", Icons.Rounded.Help, Color(0xFF8971BE), onTutorial)
                OneUIDivider()
                OneUIRow("Хранилище профилей", "AES-GCM · Android Keystore", Icons.Rounded.Lock, Color(0xFF558A47))
            }
        }
        item {
            OneUIGroup {
                OneUIRow("NOVA для Windows", "Открыть GitHub", Icons.Rounded.DesktopWindows, Color(0xFF3478C9), { onProduct("https://github.com/leoraijin/NOVA-VPN") })
                OneUIDivider()
                OneUIRow("NOVA для часов", "Открыть GitHub", Icons.Rounded.Watch, Color(0xFFBD7736), { onProduct("https://github.com/leoraijin/NOVA-VPN-WearOS") })
            }
            Text("Версия ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OneUIServers(profiles: List<ImportedProfile>, selectedId: String, state: VpnUiState,
    checks: ServerCheckState, onCheck: () -> Unit, onCancel: () -> Unit, onConnect: () -> Unit,
    error: String?, shareStatus: String?, shareId: String?, busy: Boolean, onAdd: () -> Unit,
    onSelect: (String) -> Unit, onWatch: (ImportedProfile) -> Unit, onDelete: (ImportedProfile) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showMap by rememberSaveable { mutableStateOf(false) }
    val tutorial = LocalTutorialStep.current
    LaunchedEffect(tutorial) { if (tutorial == TutorialStep.Servers) showMap = true }
    val visible = remember(profiles, query) { profiles.filter { it.name.contains(query, true) || it.protocol.contains(query, true) } }
    OneUIFrame("Серверы", "Сохранено профилей: ${profiles.size}", tutorialTargetIndex = if (tutorial == TutorialStep.Servers) 2 else 1) {
        item {
            OneUIGroup(TutorialStep.Import) {
                OneUIRow("Добавить конфигурацию", "Ссылка, QR-код или файл", Icons.Rounded.Add, Color(0xFF3478C9), onAdd)
            }
        }
        item {
            OneUIGroup(TutorialStep.Servers) {
                OneUIRow("Карта серверов", "Расположение и проверка", Icons.Rounded.Map, Color(0xFF558A47), { showMap = !showMap })
                AnimatedVisibility(showMap) { ServerMapPanel(profiles, selectedId, checks, state, onCheck, onCancel, onSelect, onConnect) }
            }
        }
        error?.let { item { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) } }
        if (profiles.isNotEmpty()) item {
            TextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Поиск серверов") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(24.dp),
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Очистить поиск") } },
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        }
        if (profiles.isEmpty()) item {
            OneUIGroup { OneUIRow("Нет сохранённых серверов", "Добавьте конфигурацию вашего VPN-провайдера.") }
        } else if (visible.isEmpty()) item {
            OneUIGroup { OneUIRow("Ничего не найдено", "Измените запрос или очистите поиск.") }
        }
        items(visible, key = { it.id }) { profile ->
            var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
            OneUIGroup {
                Row(Modifier.fillMaxWidth().selectable(profile.id == selectedId, role = Role.RadioButton, onClick = { onSelect(profile.id) })
                    .heightIn(min = 76.dp).padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.name, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
                        Text(profile.protocol.uppercase() + if (profile.id == selectedId) " · Выбран" else "", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton({ expanded = !expanded }) { Icon(Icons.Rounded.MoreVert, "Действия с профилем ${profile.name}") }
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.server, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NovaOutlinedButton({ onWatch(profile) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy && shareId == profile.id) "Передача…" else "Передать на часы") }
                        if (shareId == profile.id) shareStatus?.let { Text(it, fontSize = 13.sp) }
                        NovaTextButton({ onDelete(profile) }) { Text("Удалить профиль", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OneUIRouteTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Сайты", "Приложения").forEachIndexed { i, label ->
            Column(Modifier.weight(1f).selectable(selected == i, role = Role.Tab, onClick = { onSelect(i) }).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 17.sp, fontWeight = if (selected == i) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(36.dp).height(3.dp).background(if (selected == i) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape))
            }
        }
    }
}

@Composable
internal fun OneUIRouteMode(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.selectableGroup()) {
        listOf("Через VPN", "Напрямую").forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().selectable(selected == i, role = Role.RadioButton, onClick = { onSelect(i) }).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f)); RadioButton(selected == i, null)
            }
        }
    }
}
