package com.nova.vpn.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.vpn.android.data.*

@Composable
internal fun HomePage(profiles: List<ImportedProfile>, selectedProfileId: String, profile: ImportedProfile?,
    vpnState: VpnUiState, error: String?, onCopyDiagnostics: () -> Unit,
    onSelectProfile: (String) -> Unit, onToggle: () -> Unit, onManageProfiles: () -> Unit = {}) {
    if (LocalDesignPreset.current == DesignStyle.WINDOWS_11) {
        WindowsHome(profiles, selectedProfileId, profile, vpnState, error, onCopyDiagnostics, onSelectProfile, onToggle, onManageProfiles)
        return
    }
    if (LocalDesignPreset.current == DesignStyle.ONE_UI) {
        OneUIHome(profiles, selectedProfileId, profile, vpnState, error, onCopyDiagnostics, onSelectProfile, onToggle, onManageProfiles)
        return
    }
    if (LocalDesignPreset.current == DesignStyle.MATERIAL_YOU) {
        MaterialHome(profiles, selectedProfileId, profile, vpnState, error, onCopyDiagnostics, onSelectProfile, onToggle, onManageProfiles)
        return
    }
    if (LocalDesignPreset.current != DesignStyle.IOS_LIQUID_GLASS) {
        StandardHomePage(profiles, selectedProfileId, profile, vpnState, error, onCopyDiagnostics, onSelectProfile, onToggle)
        return
    }
    val c = MaterialTheme.colorScheme
    var serversExpanded by rememberSaveable { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val status = when {
        vpnState.trafficChecking -> "Проверяем соединение"
        vpnState.warning != null -> "Нужна проверка"
        vpnState.trafficVerified -> "Соединение проверено"
        vpnState.connected -> "Туннель включён"
        vpnState.connecting -> "Подключаемся"
        else -> "Не подключено"
    }
    val statusColor by animateColorAsState(if (vpnState.warning != null) c.error else if (vpnState.trafficVerified) c.secondary else c.onSurface, label = "ios-status")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 124.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NOVA", fontSize = 32.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold)
                    Text("Личное соединение", fontSize = 13.sp, color = c.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Shield, null, tint = c.primary, modifier = Modifier.size(28.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(.78f, 420f), label = "ios-power")
                Box(Modifier.size(184.dp).background(Brush.radialGradient(listOf(c.primary.copy(alpha = .13f), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(142.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .shadow(6.dp, CircleShape).clip(CircleShape).novaGlass()
                        .border(1.dp, c.outline.copy(alpha = .35f), CircleShape)
                        .then(if (LocalTutorialStep.current == TutorialStep.Connect) Modifier.border(2.dp, c.primary, CircleShape) else Modifier)
                        .clickable(interactionSource = interactions, indication = null, enabled = !vpnState.connecting,
                            role = Role.Button, onClickLabel = if (vpnState.connected) "Отключить VPN" else if (profile == null) "Добавить сервер" else "Подключить VPN",
                            onClick = if (profile == null && !vpnState.connected) onManageProfiles else onToggle), contentAlignment = Alignment.Center) {
                        if (vpnState.connecting) CircularProgressIndicator(Modifier.size(42.dp), color = c.primary, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.PowerSettingsNew, if (vpnState.connected) "Отключить VPN" else if (profile == null) "Добавить сервер" else "Подключить VPN", tint = c.primary, modifier = Modifier.size(48.dp))
                    }
                }
                Text(status, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = statusColor, textAlign = TextAlign.Center)
                Text(when {
                    vpnState.trafficChecking -> "Проверяем доступ через VPN"
                    vpnState.connected && !vpnState.trafficVerified -> "Доступ через VPN пока не подтверждён"
                    vpnState.trafficVerified -> "HTTPS-запрос через VPN выполнен"
                    profile == null -> "Добавьте сервер, чтобы начать"
                    else -> "Нажмите кнопку, чтобы подключиться"
                }, fontSize = 14.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        if (!error.isNullOrBlank() || vpnState.warning != null || vpnState.transportNotice != null) item {
            IOSGroup {
                Text(error ?: vpnState.warning ?: vpnState.transportNotice.orEmpty(), color = if (error != null || vpnState.warning != null) c.error else c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                if (vpnState.trafficCheckFailed) Text("Контрольный запрос не ответил. Туннель может продолжать работать.", fontSize = 12.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
        item {
            IOSSection("СЕРВЕР") {
                IOSGroup {
                    IOSRow(Icons.Rounded.Public, profile?.name ?: "Добавить сервер", profile?.protocol?.uppercase() ?: "Ссылка, QR-код или файл",
                        onClick = { if (profiles.isEmpty()) onManageProfiles() else serversExpanded = !serversExpanded }, expanded = if (profiles.isEmpty()) null else serversExpanded)
                    AnimatedVisibility(serversExpanded) {
                        Column {
                            profiles.forEach { p ->
                                HorizontalDivider(Modifier.padding(start = 54.dp), color = c.outlineVariant.copy(alpha = .45f))
                                Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onSelectProfile(p.id); serversExpanded = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name, Modifier.weight(1f), fontSize = 16.sp)
                                    if (p.id == selectedProfileId) Icon(Icons.Rounded.Check, "Выбран", tint = c.primary)
                                }
                            }
                            IOSRow(Icons.Rounded.Add, "Все серверы", onClick = onManageProfiles)
                        }
                    }
                }
            }
        }
        item {
            IOSGroup {
                IOSRow(Icons.Rounded.Info, "О соединении", if (detailsExpanded) "Скрыть подробности" else "Состояние и диагностика", onClick = { detailsExpanded = !detailsExpanded }, expanded = detailsExpanded)
                AnimatedVisibility(detailsExpanded) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("NOVA VPN ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = c.onSurfaceVariant)
                        Text("Диагностика содержит события соединения без ключей, адресов сервера и истории сайтов.", fontSize = 13.sp, color = c.onSurfaceVariant)
                        NovaTextButton(onCopyDiagnostics) { Text("Скопировать диагностику") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun IOSGroup(tutorialTarget: TutorialStep? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().then(if (tutorialTarget != null && LocalTutorialStep.current == tutorialTarget) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)) else Modifier), shape = RoundedCornerShape(22.dp), color = Color.Transparent) {
        Column(Modifier.novaGlass(), content = content)
    }
}

@Composable
internal fun IOSSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 16.dp), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun IOSRow(icon: ImageVector, title: String, detail: String? = null, onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null, expanded: Boolean? = null) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            detail?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = c.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing() else if (expanded != null) NovaDisclosure(expanded) else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = c.onSurfaceVariant.copy(alpha = .6f), modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun SettingsPage(onStartTutorial: () -> Unit, onNotificationSettings: () -> Unit, autoConnect: Boolean,
    appearance: Appearance, updateChecksEnabled: Boolean, checkingUpdates: Boolean, updateStatus: String?, error: String?,
    onAddQuickSettingsTile: () -> Unit, onOpenProduct: (String) -> Unit, onAppearanceChanged: (Appearance) -> Unit,
    onUpdateChecksChanged: (Boolean) -> Unit, onCheckUpdates: () -> Unit, onAutoConnectChanged: (Boolean) -> Unit) {
    if (appearance.style == DesignStyle.WINDOWS_11) {
        WindowsSettings(onStartTutorial, onNotificationSettings, autoConnect, appearance, updateChecksEnabled, checkingUpdates,
            updateStatus, error, onAddQuickSettingsTile, onOpenProduct, onAppearanceChanged, onUpdateChecksChanged, onCheckUpdates, onAutoConnectChanged)
        return
    }
    if (appearance.style == DesignStyle.ONE_UI) {
        OneUISettings(onStartTutorial, onNotificationSettings, autoConnect, appearance, updateChecksEnabled, checkingUpdates,
            updateStatus, error, onAddQuickSettingsTile, onOpenProduct, onAppearanceChanged, onUpdateChecksChanged, onCheckUpdates, onAutoConnectChanged)
        return
    }
    if (appearance.style == DesignStyle.MATERIAL_YOU) {
        MaterialSettings(onStartTutorial, onNotificationSettings, autoConnect, appearance, updateChecksEnabled, checkingUpdates,
            updateStatus, error, onAddQuickSettingsTile, onOpenProduct, onAppearanceChanged, onUpdateChecksChanged, onCheckUpdates, onAutoConnectChanged)
        return
    }
    if (appearance.style != DesignStyle.IOS_LIQUID_GLASS) {
        StandardSettingsPage(onStartTutorial, onNotificationSettings, autoConnect, appearance, updateChecksEnabled, checkingUpdates,
            updateStatus, error, onAddQuickSettingsTile, onOpenProduct, onAppearanceChanged, onUpdateChecksChanged, onCheckUpdates, onAutoConnectChanged)
        return
    }
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    PageFrame("Настройки", "Ваше приложение. Ваши предпочтения.", tutorialTargetIndex = 4) {
        item {
            IOSSection("ПЕРСОНАЛИЗАЦИЯ") {
                IOSGroup {
                    IOSRow(Icons.Rounded.Palette, "Оформление", "iOS 27 Liquid Glass · " + if (appearance.isDark) "Тёмная" else "Светлая", onClick = { appearanceOpen = !appearanceOpen }, expanded = appearanceOpen)
                    AnimatedVisibility(appearanceOpen) { Column(Modifier.padding(16.dp)) { AppearancePicker(appearance, onAppearanceChanged) } }
                }
            }
        }
        item {
            IOSSection("ПОДКЛЮЧЕНИЕ") {
                IOSGroup {
                    IOSRow(Icons.Rounded.PowerSettingsNew, "Автоподключение", "При открытии приложения", trailing = { NovaSwitch(autoConnect, onAutoConnectChanged) })
                    HorizontalDivider(Modifier.padding(start = 58.dp), color = c.outlineVariant.copy(alpha = .45f))
                    IOSRow(Icons.Rounded.Notifications, "Уведомление VPN", "Скрыть / показать карточку NOVA", onClick = onNotificationSettings)
                    HorizontalDivider(Modifier.padding(start = 58.dp), color = c.outlineVariant.copy(alpha = .45f))
                    IOSRow(Icons.Rounded.Tune, "Быстрое управление", "Добавить плитку в шторку Android", onClick = onAddQuickSettingsTile)
                }
                error?.let { Text(it, color = c.error, fontSize = 13.sp) }
            }
        }
        item { IOSGroup { IOSRow(Icons.Rounded.HelpOutline, "Знакомство с NOVA", "Шесть шагов для начала работы", onClick = onStartTutorial) } }
        item {
            IOSSection("ОБНОВЛЕНИЯ") {
                SoftCard(tutorialTarget = TutorialStep.Updates) {
                    IOSRow(Icons.Rounded.SystemUpdate, "Автоматическая проверка", "Публичные релизы NOVA", trailing = { NovaSwitch(updateChecksEnabled, onUpdateChecksChanged) })
                    NovaTextButton(onCheckUpdates, enabled = !checkingUpdates) { Text(if (checkingUpdates) "Проверяем…" else "Проверить сейчас") }
                    updateStatus?.let { Text(it, fontSize = 13.sp, color = c.onSurfaceVariant) }
                }
            }
        }
        item {
            IOSSection("О ПРИЛОЖЕНИИ") {
                IOSGroup {
                    IOSRow(Icons.Rounded.Lock, "Хранилище профилей", "AES-GCM · Android Keystore")
                    IOSRow(Icons.Rounded.DesktopWindows, "NOVA для Windows", "GitHub", onClick = { onOpenProduct("https://github.com/leoraijin/NOVA-VPN") })
                    IOSRow(Icons.Rounded.Watch, "NOVA для часов", "GitHub · профили передаются вручную", onClick = { onOpenProduct("https://github.com/leoraijin/NOVA-VPN-WearOS") })
                }
                Text("NOVA VPN ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = c.onSurfaceVariant)
            }
        }
        item { EngineSettingsCard() }
    }
}

@Composable
internal fun UngroupedServersPage(
    profiles: List<ImportedProfile>,
    selectedId: String,
    vpnState: VpnUiState,
    checks: ServerCheckState,
    onCheck: () -> Unit,
    onCancelCheck: () -> Unit,
    onConnect: () -> Unit,
    error: String?,
    shareStatus: String?,
    shareProfileId: String?,
    transferBusy: Boolean,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onSendToWatch: (ImportedProfile) -> Unit,
    onDelete: (ImportedProfile) -> Unit
) {
    if (LocalDesignPreset.current == DesignStyle.WINDOWS_11) {
        WindowsServers(profiles, selectedId, vpnState, checks, onCheck, onCancelCheck, onConnect, error, shareStatus, shareProfileId, transferBusy, onAdd, onSelect, onSendToWatch, onDelete)
        return
    }
    if (LocalDesignPreset.current == DesignStyle.ONE_UI) {
        OneUIServers(profiles, selectedId, vpnState, checks, onCheck, onCancelCheck, onConnect, error, shareStatus, shareProfileId, transferBusy, onAdd, onSelect, onSendToWatch, onDelete)
        return
    }
    if (LocalDesignPreset.current == DesignStyle.MATERIAL_YOU) {
        MaterialServers(profiles, selectedId, vpnState, checks, onCheck, onCancelCheck, onConnect, error, shareStatus, shareProfileId, transferBusy, onAdd, onSelect, onSendToWatch, onDelete)
        return
    }
    if (LocalDesignPreset.current != DesignStyle.IOS_LIQUID_GLASS) {
        StandardServersPage(profiles, selectedId, vpnState, checks, onCheck, onCancelCheck, onConnect, error, shareStatus, shareProfileId, transferBusy, onAdd, onSelect, onSendToWatch, onDelete)
        return
    }
    val c = MaterialTheme.colorScheme
    var showMap by rememberSaveable { mutableStateOf(false) }
    val serverTutorial = LocalTutorialStep.current
    LaunchedEffect(serverTutorial) { if (serverTutorial == TutorialStep.Servers) showMap = true }
    PageFrame("Серверы", "Ваши подключения в одном месте", tutorialTargetIndex = if (serverTutorial == TutorialStep.Servers) 2 else 1) {
        item {
            IOSGroup(tutorialTarget = TutorialStep.Import) { IOSRow(Icons.Rounded.Add, "Добавить конфигурацию", "Ссылка, QR-код или файл", onClick = onAdd) }
        }
        item {
            IOSGroup(tutorialTarget = TutorialStep.Servers) {
                IOSRow(Icons.Rounded.Public, "Карта серверов", if (showMap) "Скрыть карту" else "Расположение и доступность", onClick = { showMap = !showMap }, expanded = showMap)
                AnimatedVisibility(showMap) { ServerMapPanel(profiles, selectedId, checks, vpnState, onCheck, onCancelCheck, onSelect, onConnect) }
            }
        }
        error?.let { item { Text(it, color = c.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) } }
        if (profiles.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Public, null, tint = c.onSurfaceVariant.copy(alpha = .5f), modifier = Modifier.size(48.dp))
                Text("Пока нет серверов", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Добавьте конфигурацию от вашего VPN-провайдера.", color = c.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
            item { Text("ВАШИ СЕРВЕРЫ · ${profiles.size}", fontSize = 11.sp, letterSpacing = 1.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp)) }
            items(profiles, key = { it.id }) { profile ->
                var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
                IOSGroup {
                    IOSRow(Icons.Rounded.Public, profile.name, profile.protocol.uppercase(), onClick = { expanded = !expanded }, trailing = {
                        if (profile.id == selectedId) Icon(Icons.Rounded.CheckCircle, "Выбран", tint = c.primary, modifier = Modifier.size(22.dp))
                        NovaDisclosure(expanded)
                    })
                    AnimatedVisibility(expanded) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(profile.server, fontSize = 13.sp, color = c.onSurfaceVariant)
                            if (profile.id != selectedId) NovaButton({ onSelect(profile.id) }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать сервер") }
                            NovaTextButton({ onSendToWatch(profile) }, enabled = !transferBusy) { Text(if (transferBusy && shareProfileId == profile.id) "Передача…" else "Передать на часы") }
                            if (shareProfileId == profile.id) shareStatus?.let { Text(it, fontSize = 13.sp, color = c.onSurfaceVariant) }
                            NovaTextButton({ onDelete(profile) }) { Text("Удалить профиль", color = c.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun IOSRoutingTabs(selected: Int, labels: List<String> = listOf("Сайты", "Приложения"), onSelect: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(14.dp)).background(c.surfaceVariant).padding(3.dp)) {
        labels.forEachIndexed { index, label ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(11.dp), color = if (selected == index) c.surface else Color.Transparent, shadowElevation = if (selected == index) 1.dp else 0.dp) {
                Box(Modifier.heightIn(min = 44.dp).selectable(selected = selected == index, role = Role.Tab, onClick = { onSelect(index) }).padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 14.sp, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
