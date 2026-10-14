package com.nova.vpn.android

import android.os.Bundle
import android.app.Activity
import android.app.StatusBarManager
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.net.VpnService
import android.widget.Toast
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.ProfileLinkParser
import com.nova.vpn.android.data.ProfileImportFileReader
import com.nova.vpn.android.data.ProfileVault
import com.nova.vpn.android.data.AppRoutingMode
import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.AppRoutingStore
import com.nova.vpn.android.data.AppListEntry
import com.nova.vpn.android.data.filterAppList
import com.nova.vpn.android.data.AppPreferencesStore
import com.nova.vpn.android.data.SiteDomain
import com.nova.vpn.android.data.SiteRuleEditor
import androidx.compose.runtime.saveable.rememberSaveable
import com.nova.vpn.android.data.SiteRoutingRule
import com.nova.vpn.android.data.SiteRoutingTarget
import com.nova.vpn.android.data.AndroidRelease
import com.nova.vpn.android.data.AndroidReleaseChecker
import com.nova.vpn.android.data.SelectedProfileStore
import com.nova.vpn.android.data.SingBoxConfigFactory
import com.nova.vpn.android.data.SubscriptionFetcher
import com.nova.vpn.android.wear.WearStatusSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var tileConnectRequested by mutableStateOf(false)
    private val profileVault by lazy(LazyThreadSafetyMode.NONE) { ProfileVault(applicationContext) }
    private val appRoutingStore by lazy(LazyThreadSafetyMode.NONE) { AppRoutingStore(applicationContext) }
    private val appPreferencesStore by lazy(LazyThreadSafetyMode.NONE) { AppPreferencesStore(applicationContext) }
    private val selectedProfileStore by lazy(LazyThreadSafetyMode.NONE) { SelectedProfileStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VpnRuntime.attach(applicationContext)
        tileConnectRequested = intent?.action == NovaVpnTileService.ACTION_CONNECT_FROM_TILE
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        setContent {
            NovaAndroidApp(
                profileVault, appRoutingStore, appPreferencesStore, selectedProfileStore, sharedText,
                tileConnectRequested = tileConnectRequested,
                onTileConnectHandled = { tileConnectRequested = false }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == NovaVpnTileService.ACTION_CONNECT_FROM_TILE) tileConnectRequested = true
    }
}

private enum class Page(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home),
    Servers("Серверы", Icons.Rounded.Shield),
    Routing("Маршрутизация", Icons.Rounded.Router),
    Settings("Настройки", Icons.Rounded.Settings)
}

private enum class DesignPreset(val storedValue: String, val label: String, val cardRadius: Int) {
    IOS_GLASS("IOS_GLASS", "iOS 27 · Liquid Glass", 28),
    AMBER_GLASS("AMBER_GLASS", "Amber Glass", 30),
    ONE_UI("ONE_UI", "One UI 8.0", 30),
    WINDOWS_LIGHT("WINDOWS_LIGHT", "Windows 11 Light", 16),
    WINDOWS_DARK("WINDOWS_DARK", "Windows 11 Dark", 12);

    companion object {
        fun fromStored(value: String): DesignPreset = entries.firstOrNull { it.storedValue == value } ?: when (value) {
            "LAVENDER" -> ONE_UI
            "SKY" -> IOS_GLASS
            "CREAM" -> AMBER_GLASS
            "NIGHT" -> WINDOWS_DARK
            else -> IOS_GLASS
        }
    }
}

private val LocalDesignPreset = compositionLocalOf { DesignPreset.IOS_GLASS }

private fun designColorScheme(preset: DesignPreset) = when (preset) {
    DesignPreset.IOS_GLASS -> lightColorScheme(
        primary = Color(0xFF007AFF), onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E9FD), onPrimaryContainer = Color(0xFF123B61),
        secondary = Color(0xFF208783), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDEEEB),
        background = Color(0xFFE9EBF3), onBackground = Color(0xFF162334),
        surface = Color(0xFFFCFDFF), onSurface = Color(0xFF162334),
        surfaceVariant = Color(0xFFF4F5FB), onSurfaceVariant = Color(0xFF405369),
        error = Color(0xFFC63C49), outline = Color(0xFFB3C2D3)
    )
    DesignPreset.AMBER_GLASS -> lightColorScheme(
        primary = Color(0xFFB94710), onPrimary = Color.White,
        primaryContainer = Color(0xFFF4D5B1), onPrimaryContainer = Color(0xFF54250C),
        secondary = Color(0xFF347DA5), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCECF2),
        background = Color(0xFFF5D5AE), onBackground = Color(0xFF38281D),
        surface = Color(0xFFFFF9F1), onSurface = Color(0xFF38281D),
        surfaceVariant = Color(0xFFFBE9D4), onSurfaceVariant = Color(0xFF503821),
        error = Color(0xFFB33D32), outline = Color(0xFFDCC09E)
    )
    DesignPreset.ONE_UI -> lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFDDD1EF), onPrimaryContainer = Color(0xFF33264E),
        secondary = Color(0xFF437F68), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2EEE8),
        background = Color(0xFFF1EEF8), onBackground = Color(0xFF241B35),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF241B35),
        surfaceVariant = Color(0xFFF0EAF8), onSurfaceVariant = Color(0xFF635670),
        error = Color(0xFFA9344B), outline = Color(0xFFD6CBE5)
    )
    DesignPreset.WINDOWS_LIGHT -> lightColorScheme(
        primary = Color(0xFF0067C0), onPrimary = Color.White,
        primaryContainer = Color(0xFFD5E8F7), onPrimaryContainer = Color(0xFF153C5B),
        secondary = Color(0xFF16827D), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDEDEA),
        background = Color(0xFFF0F0F0), onBackground = Color(0xFF202020),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF202020),
        surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFF5C5C5C),
        error = Color(0xFFB23B52), outline = Color(0xFFCCCCCC)
    )
    DesignPreset.WINDOWS_DARK -> darkColorScheme(
        primary = Color(0xFF60CDFF), onPrimary = Color(0xFF00344D),
        primaryContainer = Color(0xFF33465C), onPrimaryContainer = Color(0xFFE5F5FF),
        secondary = Color(0xFF52C9C0), onSecondary = Color(0xFF003B38),
        secondaryContainer = Color(0xFF263E3D),
        background = Color(0xFF202020), onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF2C2C2C), onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF333333), onSurfaceVariant = Color(0xFFBFBFBF),
        error = Color(0xFFF0808C), outline = Color(0xFF4C4C4C)
    )
}

private fun Drawable?.toAppImageBitmap() = this?.let { drawable ->
    runCatching {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = Rect(drawable.bounds)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        drawable.bounds = oldBounds
        bitmap.asImageBitmap()
    }.getOrNull()
}

private fun loadInstalledApps(packageManager: PackageManager, ownPackage: String): List<AppListEntry> {
    val installed = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    }
    return installed
        .asSequence()
        .filter { it.packageName != ownPackage }
        .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
        .map { info ->
            val icon = runCatching { packageManager.getApplicationIcon(info) }.getOrNull()
            AppListEntry(info.packageName, packageManager.getApplicationLabel(info).toString(), icon)
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

@Composable
private fun NovaAndroidApp(
    vault: ProfileVault,
    routingStore: AppRoutingStore,
    preferencesStore: AppPreferencesStore,
    selectedProfileStore: SelectedProfileStore,
    sharedText: String?,
    tileConnectRequested: Boolean,
    onTileConnectHandled: () -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.Home) }
    var profiles by remember { mutableStateOf(emptyList<ImportedProfile>()) }
    var profilesLoaded by remember { mutableStateOf(false) }
    var tileConnectFlow by remember { mutableStateOf(false) }
    var selectedProfileId by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var profileShareStatus by remember { mutableStateOf<String?>(null) }
    var profileShareId by remember { mutableStateOf<String?>(null) }
    var profileTransferBusy by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<ImportedProfile?>(null) }
    var importing by remember { mutableStateOf(false) }
    var routing by remember { mutableStateOf(routingStore.load()) }
    var autoConnect by remember { mutableStateOf(preferencesStore.autoConnectEnabled()) }
    var autoConnectAttempted by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var designPreset by remember { mutableStateOf(DesignPreset.fromStored(preferencesStore.selectedDesign())) }
    var updateChecksEnabled by remember { mutableStateOf(preferencesStore.updateChecksEnabled()) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updatePrompt by remember { mutableStateOf<AndroidRelease?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var installedApps by remember { mutableStateOf(emptyList<AppListEntry>()) }
    var appsLoading by remember { mutableStateOf(true) }
    var appsError by remember { mutableStateOf(false) }
    var pendingVpnConfig by remember { mutableStateOf<String?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    val vpnState by VpnRuntime.state.collectAsState()
    val scope = rememberCoroutineScope()
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            showImport = true
        } else {
            importing = true
            scope.launch {
                val fileContents = runCatching {
                    withContext(Dispatchers.IO) { ProfileImportFileReader.read(context.contentResolver, uri) }
                }
                importing = false
                fileContents.onSuccess { contents ->
                    importText = contents
                    importError = null
                    showImport = true
                }.onFailure { error ->
                    importError = error.message?.take(180) ?: "Не удалось прочитать файл конфигурации. Используйте текстовый JSON, TXT или VPN-файл до 4 МБ."
                    showImport = true
                }
            }
        }
    }
    fun launchQrScanner() {
        showImport = false
        val scannerOptions = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        runCatching {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, scannerOptions)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val payload = barcode.rawValue
                    if (payload.isNullOrBlank()) {
                        importError = "В QR-коде не найден текст конфигурации."
                    } else {
                        importText = payload
                        importError = null
                    }
                    showImport = true
                }
                .addOnCanceledListener { showImport = true }
                .addOnFailureListener {
                    importError = "Не удалось запустить сканер QR. Проверьте Google Play services или выберите файл/вставьте ссылку."
                    showImport = true
                }
        }.onFailure {
            importError = "Не удалось запустить сканер QR. Проверьте Google Play services или выберите файл/вставьте ссылку."
            showImport = true
        }
    }
    val vpnConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val config = pendingVpnConfig
        pendingVpnConfig = null
        if (result.resultCode == Activity.RESULT_OK && !config.isNullOrBlank()) {
            startVpnService(context, config)
            connectError = null
        } else if (result.resultCode != Activity.RESULT_OK) {
            connectError = "Для VPN нужно разрешение Android."
        }
    }

    fun requestVpnStart(config: String) {
        runCatching {
            connectError = null
            val consent = VpnService.prepare(context)
            if (consent != null) {
                pendingVpnConfig = config
                vpnConsentLauncher.launch(consent)
            } else startVpnService(context, config)
        }.onFailure { error ->
            connectError = error.message ?: "Не удалось запустить VPN."
        }
    }

    fun applyLiveConfiguration(profileId: String, appRouting: AppRoutingSettings) {
        if (!vpnState.connected && !vpnState.connecting) return
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(300)
            runCatching {
                withContext(Dispatchers.Default) {
                    SingBoxConfigFactory.build(profile, appRouting, context.packageName)
                }
            }.onSuccess { config ->
                runCatching { startVpnService(context, config) }
                    .onSuccess { connectError = null }
                    .onFailure { error ->
                        connectError = error.message ?: "Не удалось применить параметры к VPN."
                    }
            }.onFailure { error ->
                connectError = error.message ?: "Не удалось подготовить параметры VPN."
            }
        }
    }

    fun checkForUpdates(showCurrentVersion: Boolean) {
        if (checkingUpdates) return
        scope.launch {
            checkingUpdates = true
            updateStatus = "Проверка наличия обновлений…"
            val latest = withContext(Dispatchers.IO) { runCatching { AndroidReleaseChecker.fetchLatest() }.getOrNull() }
            checkingUpdates = false
            when {
                latest == null -> updateStatus = if (showCurrentVersion) "Не удалось связаться с GitHub. Попробуйте позже." else null
                AndroidReleaseChecker.isNewerVersion(latest.version, BuildConfig.VERSION_NAME) -> {
                    updateStatus = null
                    updatePrompt = latest
                }
                showCurrentVersion -> updateStatus = "Установлена последняя версия NOVA VPN для Android."
                else -> updateStatus = null
            }
        }
    }

    fun openUpdate(release: AndroidRelease) {
        val target = release.downloadUrl ?: release.pageUrl
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
            .onFailure { updateStatus = "Не удалось открыть загрузку. Откройте страницу релизов GitHub вручную." }
        updatePrompt = null
    }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            importText = sharedText
            importError = null
            showImport = true
        }
    }

    LaunchedEffect(vault) {
        runCatching { withContext(Dispatchers.IO) { vault.load() } }
            .onSuccess { stored ->
                profiles = stored
                selectedProfileId = selectedProfileStore.load()
                    ?.takeIf { id -> stored.any { it.id == id } }
                    ?: stored.firstOrNull()?.id.orEmpty()
            }
            .onFailure { importError = "Не удалось открыть защищённое хранилище профилей." }
        profilesLoaded = true
    }
    LaunchedEffect(tileConnectRequested, profilesLoaded, profiles, selectedProfileId) {
        if (!tileConnectRequested || !profilesLoaded) return@LaunchedEffect
        tileConnectFlow = true
        onTileConnectHandled()
        val selected = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
        if (selected == null) {
            connectError = "Сначала добавьте VPN-конфигурацию в NOVA VPN."
            return@LaunchedEffect
        }
        runCatching {
            withContext(Dispatchers.Default) { SingBoxConfigFactory.build(selected, routing, context.packageName) }
        }.onSuccess(::requestVpnStart)
            .onFailure { error -> connectError = error.message ?: "Не удалось подготовить конфигурацию VPN." }
    }
    LaunchedEffect(profiles, selectedProfileId, autoConnect, sharedText) {
        if (tileConnectFlow || autoConnectAttempted || !autoConnect || profiles.isEmpty() || !sharedText.isNullOrBlank()) return@LaunchedEffect
        autoConnectAttempted = true
        if (vpnState.connected || vpnState.connecting) return@LaunchedEffect
        val selected = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.Default) {
                SingBoxConfigFactory.build(selected, routing, context.packageName)
            }
        }.onSuccess(::requestVpnStart)
            .onFailure { error -> connectError = error.message ?: "Не удалось подготовить автоподключение." }
    }
    LaunchedEffect(routingStore) {
        appsLoading = true
        runCatching {
            withContext(Dispatchers.IO) { loadInstalledApps(context.packageManager, context.packageName) }
        }.onSuccess {
            installedApps = it
            appsError = false
        }.onFailure {
            appsError = true
        }
        appsLoading = false
    }

    LaunchedEffect(Unit) {
        if (updateChecksEnabled) checkForUpdates(showCurrentVersion = false)
    }

    LaunchedEffect(vpnState) {
        val syncState = when {
            vpnState.connected -> "connected"
            vpnState.connecting -> "connecting"
            vpnState.error != null -> "error"
            else -> "disconnected"
        }
        WearStatusSync.publish(context, syncState)
    }

    val colors = designColorScheme(designPreset)
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                NavigationBar(
                    containerColor = colors.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Page.values().forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { insets ->
          CompositionLocalProvider(LocalDesignPreset provides designPreset) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(colors.background, colors.surfaceVariant, colors.background)))
                    .padding(insets)
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "page"
                ) { selected ->
                    when (selected) {
                        Page.Home -> HomePage(
                            profiles = profiles,
                            selectedProfileId = selectedProfileId,
                            profile = profiles.firstOrNull { it.id == selectedProfileId },
                            vpnState = vpnState,
                            error = connectError ?: vpnState.error,
                            onSelectProfile = {
                                selectedProfileId = it
                                scope.launch(Dispatchers.IO) {
                                    runCatching { selectedProfileStore.save(it) }
                                }
                                applyLiveConfiguration(it, routing)
                            },
                            onToggle = {
                                if (vpnState.connected || vpnState.connecting) {
                                    runCatching {
                                        context.startService(Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP))
                                    }.onFailure { connectError = it.message ?: "Не удалось отключить VPN" }
                                } else {
                                    val selected = profiles.firstOrNull { it.id == selectedProfileId }
                                    if (selected == null) {
                                        connectError = "Сначала импортируйте VPN-конфигурацию."
                                    } else {
                                        runCatching {
                                            SingBoxConfigFactory.build(selected, routing, context.packageName)
                                        }.onSuccess(::requestVpnStart).onFailure { error ->
                                            connectError = error.message ?: "Конфигурация не прошла подготовку."
                                        }
                                    }
                                }
                            }
                        )
                        Page.Servers -> ServersPage(
                            profiles = profiles,
                            selectedId = selectedProfileId,
                            error = profileError,
                            shareStatus = profileShareStatus,
                            shareProfileId = profileShareId,
                            transferBusy = profileTransferBusy,
                            onAdd = { importError = null; importText = ""; showImport = true },
                            onSelect = {
                                selectedProfileId = it
                                scope.launch(Dispatchers.IO) {
                                    runCatching { selectedProfileStore.save(it) }
                                }
                                applyLiveConfiguration(it, routing)
                            },
                            onSendToWatch = { profile ->
                                profileShareId = profile.id
                                profileTransferBusy = true
                                profileShareStatus = "Передача профиля «${profile.name}»… Ожидаем подтверждение часов."
                                runCatching { SingBoxConfigFactory.build(profile, ownPackage = context.packageName) }
                                    .onSuccess { config ->
                                        com.nova.vpn.android.wear.WearProfileTransfer.send(context, config) { message ->
                                            profileTransferBusy = false
                                            profileShareStatus = message ?: "Профиль сохранён на часах. Можно подключить VPN часов независимо от телефона."
                                        }
                                    }
                                    .onFailure { profileTransferBusy = false; profileShareStatus = it.message ?: "Не удалось подготовить профиль для часов." }
                            },
                            onDelete = { profileToDelete = it; profileError = null }
                        )
                        Page.Routing -> RoutingPage(
                            apps = installedApps,
                            loading = appsLoading,
                            failed = appsError,
                            settings = routing,
                            onSettingsChanged = { next ->
                                routing = next
                                scope.launch(Dispatchers.IO) { runCatching { routingStore.save(next) } }
                                applyLiveConfiguration(selectedProfileId, next)
                            }
                        )
                        Page.Settings -> SettingsPage(
                            autoConnect = autoConnect,
                            designPreset = designPreset,
                            updateChecksEnabled = updateChecksEnabled,
                            checkingUpdates = checkingUpdates,
                            updateStatus = updateStatus,
                            error = settingsError,
                            onOpenProduct = { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    .onFailure { Toast.makeText(context, "Не удалось открыть GitHub. Установите браузер или приложение GitHub.", Toast.LENGTH_LONG).show() }
                            },
                            onAddQuickSettingsTile = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                        ComponentName(context, NovaVpnTileService::class.java),
                                        "NOVA VPN",
                                        Icon.createWithResource(context, R.drawable.ic_qs_vpn),
                                        context.mainExecutor
                                    ) { result ->
                                        val message = when (result) {
                                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Плитка NOVA VPN добавлена."
                                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Плитка NOVA VPN уже добавлена."
                                            else -> "Добавление не выполнено. Добавьте NOVA VPN вручную через редактирование быстрых настроек."
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Откройте редактирование быстрых настроек и добавьте NOVA VPN.", Toast.LENGTH_LONG).show()
                                }
                            },
                            onDesignChanged = { next ->
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { preferencesStore.setSelectedDesign(next.storedValue) } }
                                        .onSuccess { designPreset = next; settingsError = null }
                                        .onFailure { settingsError = "Не удалось сохранить оформление." }
                                }
                            },
                            onUpdateChecksChanged = { enabled ->
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { preferencesStore.setUpdateChecksEnabled(enabled) } }
                                        .onSuccess { updateChecksEnabled = enabled; updateStatus = null }
                                        .onFailure { settingsError = "Не удалось сохранить настройку обновлений." }
                                }
                            },
                            onCheckUpdates = { checkForUpdates(showCurrentVersion = true) },
                            onAutoConnectChanged = { enabled ->
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { preferencesStore.setAutoConnectEnabled(enabled) }
                                    }.onSuccess {
                                        autoConnect = enabled
                                        settingsError = null
                                    }.onFailure {
                                        settingsError = "Не удалось сохранить настройку."
                                    }
                                }
                            }
                        )
                    }
                }
            }
          }
        }

        if (showImport) {
            AlertDialog(
                onDismissRequest = { if (!importing) showImport = false },
                title = { Text("Добавить конфигурацию") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Добавьте VPN-ссылку, HTTPS-подписку, список ссылок или sing-box JSON. Можно отсканировать QR-код, выбрать файл или вставить текст. Данные сохраняются зашифрованно на устройстве.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                enabled = !importing,
                                onClick = { launchQrScanner() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Сканировать QR") }
                            OutlinedButton(
                                enabled = !importing,
                                onClick = {
                                    showImport = false
                                    importFileLauncher.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Выбрать файл") }
                        }
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it; importError = null },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = { Text("vless://…, QR-ссылка или sing-box JSON") }
                        )
                        importError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !importing && importText.isNotBlank(), onClick = {
                        importing = true
                        val rawImport = importText
                        scope.launch {
                            runCatching {
                                val source = withContext(Dispatchers.IO) {
                                    SubscriptionFetcher.fetchIfUrl(rawImport)
                                }
                                withContext(Dispatchers.Default) { ProfileLinkParser.parseText(source) }
                            }.onSuccess { incoming ->
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        vault.save(ImportedProfile.distinctByConfiguration(profiles + incoming))
                                    }
                                }.onSuccess {
                                    profiles = ImportedProfile.distinctByConfiguration(profiles + incoming)
                                    if (selectedProfileId.isBlank()) {
                                        selectedProfileId = incoming.firstOrNull()?.id.orEmpty()
                                        if (selectedProfileId.isNotBlank()) {
                                            withContext(Dispatchers.IO) {
                                                runCatching { selectedProfileStore.save(selectedProfileId) }
                                            }
                                        }
                                    }
                                    showImport = false
                                    importText = ""
                                }.onFailure {
                                    importError = "Не удалось безопасно сохранить профили. Проверьте свободное место и блокировку экрана устройства."
                                }
                            }.onFailure { error ->
                                importError = error.message?.take(180) ?: "Не удалось прочитать конфигурацию."
                            }
                            importing = false
                        }
                    }) { Text(if (importing) "Импорт…" else "Импортировать") }
                },
                dismissButton = { TextButton(enabled = !importing, onClick = { showImport = false }) { Text("Отмена") } }
            )
        }

        if (checkingUpdates) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Проверка наличия обновлений") },
                text = { Text("Сверяю версию приложения с последним релизом на GitHub.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = { }
            )
        }

        updatePrompt?.let { release ->
            AlertDialog(
                onDismissRequest = { updatePrompt = null },
                title = { Text("Доступно обновление NOVA VPN") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("На GitHub доступна версия ${release.version}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (release.notes.isNotBlank()) {
                            Text(release.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        } else {
                            Text("Сводка изменений будет опубликована вместе с APK.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text("Скачать APK для Android?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { openUpdate(release) }) { Text("Скачать") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { updatePrompt = null }) { Text("Позже") }
                        TextButton(onClick = {
                            updatePrompt = null
                            updateChecksEnabled = false
                            scope.launch(Dispatchers.IO) { runCatching { preferencesStore.setUpdateChecksEnabled(false) } }
                        }) { Text("Больше не показывать") }
                    }
                }
            )
        }

        profileToDelete?.let { deleting ->
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = { Text("Удалить профиль?") },
                text = { Text("«${deleting.name}» будет удалён из этого устройства.") },
                confirmButton = {
                    TextButton(onClick = {
                        profileToDelete = null
                        val remaining = profiles.filterNot { it.id == deleting.id }
                        val deletingSelected = selectedProfileId == deleting.id
                        val nextSelected = if (deletingSelected) remaining.firstOrNull()?.id.orEmpty() else selectedProfileId
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    vault.save(remaining)
                                    if (nextSelected.isBlank()) selectedProfileStore.clear()
                                    else if (deletingSelected) selectedProfileStore.save(nextSelected)
                                }
                            }.onSuccess {
                                profiles = remaining
                                profileError = null
                                if (deletingSelected) {
                                    selectedProfileId = nextSelected
                                    if (nextSelected.isBlank()) {
                                        runCatching {
                                            context.startService(
                                                Intent(context, NovaVpnService::class.java)
                                                    .setAction(NovaVpnService.ACTION_STOP)
                                            )
                                        }.onFailure { connectError = it.message ?: "Не удалось отключить VPN." }
                                    } else {
                                        applyLiveConfiguration(nextSelected, routing)
                                    }
                                }
                            }.onFailure {
                                profileError = "Не удалось удалить профиль из защищённого хранилища."
                            }
                        }
                    }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { profileToDelete = null }) { Text("Отмена") } }
            )
        }
    }
}

private fun startVpnService(context: android.content.Context, config: String) {
    val intent = Intent(context, NovaVpnService::class.java)
        .setAction(NovaVpnService.ACTION_START)
        .putExtra(NovaVpnService.EXTRA_CONFIG, config)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
    else context.startService(intent)
}

@Composable
private fun PageFrame(title: String, subtitle: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun SoftCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier),
        shape = RoundedCornerShape(LocalDesignPreset.current.cardRadius.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun HomePage(
    profiles: List<ImportedProfile>,
    selectedProfileId: String,
    profile: ImportedProfile?,
    vpnState: VpnUiState,
    error: String?,
    onSelectProfile: (String) -> Unit,
    onToggle: () -> Unit
) = PageFrame("Добрый день", "Ваше подключение под контролем") {
    item { SoftCard {
        Text("СТАТУС", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            when {
                vpnState.trafficChecking -> "Туннель включён. Проверяем HTTPS…"
                vpnState.warning != null -> "Проверка соединения не прошла."
                vpnState.trafficVerified -> "HTTPS через VPN подтверждён."
                vpnState.connected -> "Туннель включён. Трафик не проверен."
                vpnState.connecting -> "Подключаемся…"
                else -> "Готово к подключению"
            },
            fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(profile?.name ?: "Выберите сервер для подключения", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = RoundedCornerShape(50),
            color = when {
                vpnState.warning != null -> MaterialTheme.colorScheme.errorContainer
                vpnState.connected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Text(
                when {
                    vpnState.trafficChecking -> "●  Проверка трафика"
                    vpnState.warning != null -> "●  Ошибка соединения"
                    vpnState.trafficVerified -> "●  Трафик подтверждён"
                    vpnState.connected -> "●  Туннель включён"
                    vpnState.connecting -> "●  Подключение"
                    else -> "●  Не подключено"
                },
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                color = when {
                    vpnState.warning != null -> MaterialTheme.colorScheme.onErrorContainer
                    vpnState.connected -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        vpnState.warning?.let { warning ->
            Text(warning, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
        if (vpnState.trafficCheckFailed) {
            Text("Контрольный HTTPS-запрос не ответил. Это не означает, что VPN остановился: если сайты открываются, можно продолжать работу.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
            val controlShape = CircleShape
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .shadow(10.dp, controlShape)
                    .clip(controlShape)
                    .background(Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
                    ))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.56f), controlShape)
                    .clickable(
                        enabled = !vpnState.connecting,
                        role = Role.Button,
                        onClickLabel = if (vpnState.connected) "Отключить VPN" else "Подключить VPN",
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(
                        when {
                            vpnState.connecting -> "Подключение…"
                            vpnState.connected -> "Отключить"
                            else -> "Подключить"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    } }
    if (!error.isNullOrBlank()) item { SoftCard { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) } }
    item { SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("БЫСТРЫЕ СЕРВЕРЫ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Выберите профиль", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.secondary)
        }
        if (profiles.isEmpty()) {
            Text("Добавьте VPN-конфигурацию во вкладке «Серверы», и она появится здесь.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            profiles.sortedBy { it.id != selectedProfileId }.take(3).forEach { item ->
                val selected = item.id == selectedProfileId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { onSelectProfile(item.id) }),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(item.protocol.take(2).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                item.server.takeIf { it.isNotBlank() } ?: item.protocol.uppercase(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (selected) Text("Активен", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (profiles.size > 3) Text("Ещё ${profiles.size - 3} — во вкладке «Серверы»", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } }
    item { SoftCard {
        Text("РЕЖИМ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("VPN", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Android-клиент работает отдельно от версии для Windows.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable
private fun ServersPage(
    profiles: List<ImportedProfile>,
    selectedId: String,
    error: String?,
    shareStatus: String?,
    shareProfileId: String?,
    transferBusy: Boolean,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onSendToWatch: (ImportedProfile) -> Unit,
    onDelete: (ImportedProfile) -> Unit
) = PageFrame("Серверы", "Профили хранятся зашифрованно на устройстве") {
    item { SoftCard {
        Text("Вставьте VPN-ссылку, HTTPS-подписку, список ссылок или sing-box JSON. Поддерживаются VLESS, VMess, Trojan, Shadowsocks, Hysteria2 и TUIC.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить конфигурацию")
        }
    } }

    error?.let { message -> item { SoftCard { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) } } }

    if (profiles.isEmpty()) {
        item { SoftCard {
            Text("Пока нет профилей", fontWeight = FontWeight.SemiBold)
            Text("Добавьте ссылку или JSON-конфигурацию, чтобы подготовить подключение.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    } else items(profiles, key = { it.id }) { profile ->
        SoftCard(onClick = if (profile.id == selectedId) null else ({ onSelect(profile.id) })) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                    Text("${profile.protocol.uppercase()}${if (profile.server.isNotBlank()) " · ${profile.server}:${profile.port}" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (profile.id == selectedId) {
                    Text("Выбран", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { onSelect(profile.id) }
                ) { Text("Выбрать") }
                IconButton(onClick = { onDelete(profile) }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить ${profile.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = { onSendToWatch(profile) }, enabled = !transferBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (transferBusy && shareProfileId == profile.id) "Передача…" else "Передать на часы")
            }
            if (shareProfileId == profile.id) shareStatus?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RoutingPage(
    apps: List<AppListEntry>,
    loading: Boolean,
    failed: Boolean,
    settings: AppRoutingSettings,
    onSettingsChanged: (AppRoutingSettings) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var siteInput by remember { mutableStateOf("") }
    var siteError by remember { mutableStateOf<String?>(null) }
    var editingDomain by rememberSaveable { mutableStateOf<String?>(null) }
    var editedDomain by rememberSaveable { mutableStateOf("") }
    var editError by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleApps = remember(apps, searchQuery) { filterAppList(apps, searchQuery) }

    editingDomain?.let { original ->
        AlertDialog(
            onDismissRequest = { editingDomain = null; editError = null },
            title = { Text("Редактировать домен") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedDomain,
                        onValueChange = { editedDomain = it; editError = null },
                        label = { Text("Домен") },
                        singleLine = true,
                        isError = editError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    editError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Text("Маршрут «Через VPN» или «Напрямую» сохранится. При включённом VPN новые параметры применяются автоматически с переподключением.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = editedDomain.isNotBlank(), onClick = {
                    val next = try { SiteRuleEditor.rename(settings, original, editedDomain) }
                    catch (error: IllegalArgumentException) { editError = error.message; return@TextButton }
                    onSettingsChanged(next)
                    editingDomain = null
                    editError = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editingDomain = null; editError = null }) { Text("Отмена") } }
        )
    }

    PageFrame("Маршрутизация", "Сначала правила сайтов, затем приложений") {
    item {
        SoftCard {
            Text("Маршрутизация по сайтам", fontWeight = FontWeight.SemiBold)
            Text("Введите домен без https://. Правило охватывает домен и его поддомены и имеет приоритет над маршрутом браузера.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = siteInput,
                    onValueChange = { siteInput = it; siteError = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Домен") },
                    placeholder = { Text("youtube.com") }
                )
                Button(
                    onClick = {
                        val domain = SiteDomain.normalize(siteInput)
                        when {
                            domain == null -> siteError = "Введите домен, например youtube.com, без адреса страницы."
                            settings.sites.any { it.domain == domain } -> siteError = "Это правило уже добавлено."
                            else -> {
                                onSettingsChanged(settings.copy(sites = settings.sites + SiteRoutingRule(domain)))
                                siteInput = ""
                                siteError = null
                            }
                        }
                    },
                    enabled = siteInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Добавить") }
            }
            siteError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (settings.sites.isEmpty()) {
                Text("Нет правил сайтов. Добавьте домен, чтобы закрепить для него VPN или прямой маршрут.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    items(settings.sites, key = { "site:${it.domain}" }) { rule ->
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.domain, fontWeight = FontWeight.SemiBold)
                    Text("Домен и поддомены", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editingDomain = rule.domain; editedDomain = rule.domain; editError = null }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Редактировать домен ${rule.domain}", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    onSettingsChanged(settings.copy(sites = settings.sites.filterNot { it.domain == rule.domain }))
                }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить правило ${rule.domain}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = rule.target == SiteRoutingTarget.VPN,
                    onClick = { onSettingsChanged(settings.copy(sites = settings.sites.map { if (it.domain == rule.domain) it.copy(target = SiteRoutingTarget.VPN) else it })) },
                    label = { Text("Через VPN") }
                )
                FilterChip(
                    selected = rule.target == SiteRoutingTarget.DIRECT,
                    onClick = { onSettingsChanged(settings.copy(sites = settings.sites.map { if (it.domain == rule.domain) it.copy(target = SiteRoutingTarget.DIRECT) else it })) },
                    label = { Text("Напрямую") }
                )
            }
        }
    }
    item {
        SoftCard {
            Text("Режим списка", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSettingsChanged(settings.copy(mode = AppRoutingMode.INCLUDE_SELECTED)) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (settings.mode == AppRoutingMode.INCLUDE_SELECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("Выбранные через VPN", color = if (settings.mode == AppRoutingMode.INCLUDE_SELECTED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
                Button(
                    onClick = { onSettingsChanged(settings.copy(mode = AppRoutingMode.EXCLUDE_SELECTED)) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (settings.mode == AppRoutingMode.EXCLUDE_SELECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("Выбранные напрямую", color = if (settings.mode == AppRoutingMode.EXCLUDE_SELECTED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
            }
            Text(
                if (settings.mode == AppRoutingMode.INCLUDE_SELECTED)
                    "Отмеченные приложения идут через VPN, остальные — напрямую. Сайтовые правила имеют приоритет."
                else "Все приложения идут через VPN, кроме отмеченных. Сайтовые правила имеют приоритет.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Отмечено: ${settings.packages.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
    if (loading) item { SoftCard { Text("Загружаю список приложений…", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    else if (failed) item { SoftCard { Text("Android не предоставил список приложений. Проверьте настройки доступа.", color = MaterialTheme.colorScheme.error) } }
    else if (apps.isEmpty()) item { SoftCard { Text("Не найдено приложений с ярлыком запуска.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    else {
        item {
            SoftCard {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Поиск приложения") },
                    placeholder = { Text("Название или package.name") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = { searchQuery = "" }) { Text("Очистить") }
                        }
                    }
                )
                Text("Найдено: ${visibleApps.size} из ${apps.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (visibleApps.isEmpty()) item {
            SoftCard { Text("Ничего не найдено. Проверьте запрос или очистите поиск.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else items(visibleApps, key = { it.packageName }) { app ->
        val checked = app.packageName in settings.packages
        val appIcon = remember(app.packageName) { app.icon.toAppImageBitmap() }
        SoftCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button) {
                        val next = settings.packages.toMutableSet().apply {
                            if (checked) remove(app.packageName) else add(app.packageName)
                        }
                        onSettingsChanged(settings.copy(packages = next))
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                appIcon?.let { bitmap ->
                    Image(bitmap = bitmap, contentDescription = "Иконка ${app.label}", modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Medium)
                    Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(
                    checked = checked,
                    onCheckedChange = { enabled ->
                        val next = settings.packages.toMutableSet().apply {
                            if (enabled) add(app.packageName) else remove(app.packageName)
                        }
                        onSettingsChanged(settings.copy(packages = next))
                    }
                )
            }
        }
    }
    }
}
}

@Composable
private fun SettingsPage(
    autoConnect: Boolean,
    designPreset: DesignPreset,
    updateChecksEnabled: Boolean,
    checkingUpdates: Boolean,
    updateStatus: String?,
    error: String?,
    onAddQuickSettingsTile: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onDesignChanged: (DesignPreset) -> Unit,
    onUpdateChecksChanged: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit
) = PageFrame("Настройки", "Конфиденциальность и поведение NOVA") {
    item { SoftCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button) { onAutoConnectChanged(!autoConnect) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Автоподключение", fontWeight = FontWeight.Medium)
                Text("Один раз подключить выбранный профиль при открытии приложения.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autoConnect, onCheckedChange = onAutoConnectChanged)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    } }
    item { SoftCard {
        Text("Плитка быстрых настроек", fontWeight = FontWeight.SemiBold)
        Text("Подключайте или отключайте VPN из шторки Android. Плитка показывает состояние и выбранный сервер.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAddQuickSettingsTile, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Добавить плитку NOVA VPN")
        }
    } }
    item { SoftCard {
        Text("Оформление", fontWeight = FontWeight.SemiBold)
        Text("Выберите цветовую тему мобильного клиента.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DesignPreset.entries.forEach { preset ->
                FilterChip(
                    selected = designPreset == preset,
                    onClick = { onDesignChanged(preset) },
                    label = { Text(preset.label) }
                )
            }
        }
    } }
    item { SoftCard {
        SettingRow(Icons.Rounded.MoreHoriz, "Уведомление VPN", "Показывает статус и кнопку отключения во время работы туннеля.")
        SettingRow(Icons.Rounded.Lock, "Хранилище профилей", "Профили шифруются AES-GCM ключом Android Keystore.")
    } }
    item { SoftCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onUpdateChecksChanged(!updateChecksEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Проверять обновления", fontWeight = FontWeight.Medium)
                Text("Проверка при запуске по публичным релизам NOVA для Android.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = updateChecksEnabled, onCheckedChange = onUpdateChecksChanged)
        }
        Button(onClick = onCheckUpdates, enabled = !checkingUpdates, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (checkingUpdates) "Проверка…" else "Проверить обновления")
        }
        updateStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    } }
    item { SoftCard {
        Text("Другие продукты", fontWeight = FontWeight.SemiBold)
        Text("Клиенты NOVA для компьютера и часов. Ссылки открывают их отдельные репозитории GitHub.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onOpenProduct("https://github.com/leoraijin/NOVA-VPN") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("NOVA VPN для Windows · GitHub")
        }
        OutlinedButton(onClick = { onOpenProduct("https://github.com/leoraijin/NOVA-VPN-WearOS") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("NOVA VPN для часов · GitHub")
        }
    } }
    item { SoftCard {
        Text("Отдельный мобильный проект", fontWeight = FontWeight.SemiBold)
        Text("Android-клиент отделён от Windows-версии. VPN телефона и часов работают независимо. Профиль с ключами передаётся на часы только вручную: Серверы → Передать на часы.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
