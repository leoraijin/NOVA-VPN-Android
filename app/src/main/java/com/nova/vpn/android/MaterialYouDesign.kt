@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.nova.vpn.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.vpn.android.data.*

@Composable
internal fun MaterialFrame(title: String, subtitle: String, tutorialTargetIndex: Int = 1, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val step = LocalTutorialStep.current
    LaunchedEffect(step, tutorialTargetIndex) { if (step != null) state.scrollToItem(tutorialTargetIndex) }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(16.dp, 24.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal)
                Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/** Material composition: tonal planes, large actions and expressive geometry. */
@Composable
internal fun MaterialPanel(color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    foreground: Color = MaterialTheme.colorScheme.onSurface, target: TutorialStep? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(Modifier.fillMaxWidth().then(if (target != null && LocalTutorialStep.current == target)
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        shape = shape, color = color, contentColor = foreground) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun MaterialActionRow(icon: ImageVector, title: String, detail: String,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, expanded: Boolean? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .heightIn(min = 72.dp).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing() else if (expanded != null) NovaDisclosure(expanded) else if (onClick != null) Icon(Icons.Rounded.ArrowForward, null, Modifier.size(20.dp))
    }
}

@Composable
internal fun MaterialHome(profiles: List<ImportedProfile>, selectedId: String, profile: ImportedProfile?,
    state: VpnUiState, error: String?, onCopy: () -> Unit, onSelect: (String) -> Unit,
    onToggle: () -> Unit, onManage: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var choose by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    val warning = error ?: state.warning
    val headline = when {
        state.recoveryStatus != null -> state.recoveryText().orEmpty()
        warning != null -> "Нужна проверка"
        state.trafficChecking -> "Проверяем доступ"
        state.trafficVerified -> "Вы подключены"
        state.connected -> "Туннель включён"
        state.connecting -> "Подключаемся"
        else -> "VPN выключен"
    }
    PageFrame("Интернет и VPN", "NOVA VPN") {
        item {
            MaterialPanel(if (warning != null) c.errorContainer else c.primaryContainer,
                if (warning != null) c.onErrorContainer else c.onPrimaryContainer, TutorialStep.Connect) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Состояние VPN", style = MaterialTheme.typography.labelLarge)
                    Surface(shape = RoundedCornerShape(20.dp), color = c.secondaryContainer, contentColor = c.onSecondaryContainer) {
                        Icon(if (warning != null) Icons.Rounded.PriorityHigh else Icons.Rounded.Shield, null, Modifier.padding(14.dp).size(28.dp))
                    }
                }
                Text(headline, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal)
                Text(when {
                    state.recoveryStatus != null -> "Можно отменить подключение кнопкой ниже."
                    warning != null -> warning
                    state.trafficVerified -> "Доступ через VPN подтверждён HTTPS-запросом."
                    state.connected -> "Туннель работает. Доступ через VPN пока не подтверждён."
                    state.connecting -> "Устанавливаем соединение с сервером…"
                    profile == null -> "Добавьте сервер и подключайтесь одним касанием."
                    else -> "Сервер выбран. Можно подключаться."
                }, style = MaterialTheme.typography.bodyMedium)
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val radius by animateDpAsState(if (pressed) 16.dp else 28.dp, spring(.75f, 450f), label = "material-connect-shape")
                Button(onClick = if (profile == null && !state.connected) onManage else onToggle,
                    interactionSource = interactions, shape = RoundedCornerShape(radius),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    if (state.connecting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(if (profile == null && !state.connected) Icons.Rounded.Add else Icons.Rounded.PowerSettingsNew, null)
                    Spacer(Modifier.width(12.dp))
                    Text(if (state.connecting) "Отменить" else if (state.connected) "Отключить" else if (profile == null) "Добавить сервер" else "Подключить", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        state.transportNotice?.let { item { MaterialPanel { Text(it, style = MaterialTheme.typography.bodyMedium) } } }
        item {
            MaterialPanel {
                Text("Точка подключения", style = MaterialTheme.typography.titleMedium)
                MaterialActionRow(Icons.Rounded.Public, profile?.name ?: "Сервер не выбран", profile?.protocol?.uppercase() ?: "Ссылка, QR-код или файл",
                    onClick = { if (profiles.isEmpty()) onManage() else choose = !choose }, expanded = if (profiles.isEmpty()) null else choose)
                AnimatedVisibility(choose) {
                    Column(Modifier.selectableGroup()) {
                        profiles.forEach { p ->
                            Row(Modifier.fillMaxWidth().selectable(p.id == selectedId, role = Role.RadioButton,
                                onClick = { onSelect(p.id); choose = false }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(p.id == selectedId, null)
                                Spacer(Modifier.width(12.dp)); Text(p.displayName, Modifier.weight(1f))
                            }
                        }
                        TextButton(onManage) { Text("Управление серверами") }
                    }
                }
            }
        }
        item {
            MaterialPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(12.dp))
                    Text("Вы выбираете маршрут", style = MaterialTheme.typography.titleMedium)
                }
                Text("В разделе «Маршруты» можно отдельно настроить сайты и приложения.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            TextButton({ diagnostics = !diagnostics }) { Icon(Icons.Rounded.Info, null); Spacer(Modifier.width(8.dp)); Text("Состояние и диагностика"); Spacer(Modifier.width(8.dp)); NovaDisclosure(diagnostics) }
            AnimatedVisibility(diagnostics) {
                MaterialPanel {
                    Text("NOVA VPN ${BuildConfig.VERSION_NAME}")
                    Text("Журнал событий без ключей, адресов сервера и истории сайтов.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onCopy) { Text("Открыть диагностику") }
                }
            }
        }
    }
}

@Composable
internal fun MaterialSettings(onTutorial: () -> Unit, onNotifications: () -> Unit, autoConnect: Boolean,
    appearance: Appearance, updateChecks: Boolean, checking: Boolean, updateStatus: String?, error: String?,
    onTile: () -> Unit, onProduct: (String) -> Unit, onAppearance: (Appearance) -> Unit,
    onUpdateChecks: (Boolean) -> Unit, onCheck: () -> Unit, onAuto: (Boolean) -> Unit) {
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    PageFrame("Настройки", "Параметры NOVA", tutorialTargetIndex = 4) {
        item {
            MaterialPanel {
                MaterialActionRow(Icons.Rounded.Palette, "Цвет и оформление",
                    "Pixel · " + if (appearance.isDark) "Тёмная тема" else "Светлая тема",
                    onClick = { appearanceOpen = !appearanceOpen }, expanded = appearanceOpen)
                AnimatedVisibility(appearanceOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AppearancePicker(appearance, onAppearance) }
                }
            }
        }
        item {
            MaterialPanel {
                Text("Подключение", style = MaterialTheme.typography.titleLarge)
                MaterialActionRow(Icons.Rounded.PowerSettingsNew, "Автоподключение", "При открытии приложения", trailing = { NovaSwitch(autoConnect, onAuto) })
                HorizontalDivider(color = c.outlineVariant)
                MaterialActionRow(Icons.Rounded.Notifications, "Уведомление VPN", "Скрыть / показать карточку NOVA", onNotifications)
                MaterialActionRow(Icons.Rounded.Dashboard, "Плитка в шторке", "Быстрое управление VPN", onTile)
                error?.let { Text(it, color = c.error) }
            }
        }
        item {
            MaterialPanel {
                Text("Помощь", style = MaterialTheme.typography.titleMedium)
                Text("Шесть шагов: от первого сервера до правил маршрутизации.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onTutorial) { Text("Знакомство с NOVA"); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, null) }
            }
        }
        item {
            MaterialPanel(target = TutorialStep.Updates) {
                Text("Обновления", style = MaterialTheme.typography.titleLarge)
                MaterialActionRow(Icons.Rounded.SystemUpdate, "Проверять автоматически", "Публичные релизы NOVA", trailing = { NovaSwitch(updateChecks, onUpdateChecks) })
                OutlinedButton(onCheck, enabled = !checking, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (checking) "Проверяем…" else "Проверить сейчас") }
                updateStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            MaterialPanel {
                Text("О приложении", style = MaterialTheme.typography.titleLarge)
                MaterialActionRow(Icons.Rounded.Lock, "Профили на устройстве", "AES-GCM · Android Keystore")
                MaterialActionRow(Icons.Rounded.DesktopWindows, "NOVA для Windows", "Открыть GitHub", { onProduct("https://github.com/leoraijin/NOVA-VPN") })
                MaterialActionRow(Icons.Rounded.Watch, "NOVA для часов", "Открыть GitHub", { onProduct("https://github.com/leoraijin/NOVA-VPN-WearOS") })
                Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { EngineSettingsCard() }
    }
}

@Composable
internal fun MaterialServers(profiles: List<ImportedProfile>, selectedId: String, state: VpnUiState,
    checks: ServerCheckState, onCheck: () -> Unit, onCancel: () -> Unit, onConnect: () -> Unit,
    error: String?, shareStatus: String?, shareId: String?, busy: Boolean, onAdd: () -> Unit,
    onSelect: (String) -> Unit, onWatch: (ImportedProfile) -> Unit, onDelete: (ImportedProfile) -> Unit) {
    val c = MaterialTheme.colorScheme
    var showMap by rememberSaveable { mutableStateOf(false) }
    val tutorial = LocalTutorialStep.current
    LaunchedEffect(tutorial) { if (tutorial == TutorialStep.Servers) showMap = true }
    PageFrame("Серверы", "Выберите точку подключения", tutorialTargetIndex = if (tutorial == TutorialStep.Servers) 2 else 1) {
        item {
            MaterialPanel(target = TutorialStep.Import) {
                MaterialActionRow(Icons.Rounded.Add, "Добавить конфигурацию", "Ссылка, QR-код или файл", onAdd)
                Text("Сохранено профилей: ${profiles.size}", style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant)
            }
        }
        item {
            MaterialPanel(target = TutorialStep.Servers) {
                MaterialActionRow(Icons.Rounded.Map, "Карта и проверка", "Расположение и доступность", { showMap = !showMap }, expanded = showMap)
                AnimatedVisibility(showMap) { ServerMapPanel(profiles, selectedId, checks, state, onCheck, onCancel, onSelect, onConnect) }
            }
        }
        error?.let { item { MaterialPanel(c.errorContainer, c.onErrorContainer) { Text(it) } } }
        if (profiles.isEmpty()) item {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Нет сохранённых серверов", fontSize = 26.sp, lineHeight = 32.sp)
                Text("Добавьте ссылку, отсканируйте QR-код или выберите файл конфигурации вашего провайдера.", color = c.onSurfaceVariant)
            }
        }
        items(profiles, key = { it.id }) { profile ->
            val selected = selectedId == profile.id
            MaterialPanel(if (selected) c.primaryContainer else c.surfaceContainerLow, if (selected) c.onPrimaryContainer else c.onSurface) {
                Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { onSelect(profile.id) }).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.protocol.uppercase(), style = MaterialTheme.typography.labelSmall)
                        Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                        FavoriteServerButton(profile)
                    }
                    RadioButton(selected, null)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selected) "Выбран для подключения" else "Нажмите, чтобы выбрать", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    ProfileActionsMenu(profile, busy, onWatch, onDelete)
                }
                if (shareId == profile.id) shareStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
internal fun MaterialRouteTabs(selected: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected, containerColor = Color.Transparent) {
        listOf("Сайты", "Приложения").forEachIndexed { i, label ->
            Tab(selected == i, { onSelect(i) }, text = { Text(label) },
                icon = { Icon(if (i == 0) Icons.Rounded.Language else Icons.Rounded.Apps, null) })
        }
    }
}

@Composable
internal fun MaterialRouteMode(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Через VPN", "Напрямую").forEachIndexed { i, label ->
            FilterChip(selected == i, { onSelect(i) }, label = { Text(label) },
                leadingIcon = if (selected == i) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
