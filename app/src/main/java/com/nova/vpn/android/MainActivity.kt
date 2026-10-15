package com.nova.vpn.android

import com.nova.vpn.android.data.Appearance
import com.nova.vpn.android.data.DesignStyle
import com.nova.vpn.android.data.ThemeMode
import android.os.Bundle
import android.Manifest
import android.provider.Settings
import android.app.Activity
import android.app.StatusBarManager
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.net.VpnService
import android.widget.Toast
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.nova.vpn.android.NovaAlertDialog as AlertDialog
import com.nova.vpn.android.NovaButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import com.nova.vpn.android.NovaOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.nova.vpn.android.NovaSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import com.nova.vpn.android.NovaTextButton as TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.produceState
import com.nova.vpn.android.data.AppIconCache
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.PaddingValues
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.animation.core.tween
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) AppIconCache.clear()
        else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) AppIconCache.trim()
    }
}

private enum class Page(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home),
    Servers("Серверы", Icons.Rounded.Shield),
    Routing("Маршрутизация", Icons.Rounded.Router),
    Settings("Настройки", Icons.Rounded.Settings)
}

private fun loadInstalledApps(packageManager: PackageManager, ownPackage: String): List<AppListEntry> {
    val installed = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(0)
    }
    return installed
        .asSequence()
        .filter { it.packageName != ownPackage }
        .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
        .map { info ->
            AppListEntry(info.packageName, packageManager.getApplicationLabel(info).toString())
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
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var tutorialIndex by rememberSaveable { mutableStateOf(if (preferencesStore.tutorialDismissed()) -1 else 0) }
    val tutorialStep = TutorialStep.at(tutorialIndex)
    val scope = rememberCoroutineScope()
    var tutorialClosing by remember { mutableStateOf(false) }
    fun finishTutorial() {
        if (tutorialClosing) return
        tutorialClosing = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { preferencesStore.dismissTutorial() } }
                .onSuccess { tutorialIndex = -1 }
                .onFailure { Toast.makeText(context, "Не удалось сохранить обучение. Повторите попытку.", Toast.LENGTH_LONG).show() }
            tutorialClosing = false
        }
    }
    BackHandler(enabled = tutorialStep != null) {
        if (!tutorialClosing) {
            if (tutorialIndex > 0) tutorialIndex-- else finishTutorial()
        }
    }
    LaunchedEffect(tutorialStep) {
        page = when (tutorialStep) {
            TutorialStep.Import, TutorialStep.Servers -> Page.Servers
            TutorialStep.Apps, TutorialStep.Sites -> Page.Routing
            TutorialStep.Updates -> Page.Settings
            TutorialStep.Connect -> Page.Home
            null -> return@LaunchedEffect
        }
    }
    var profiles by remember { mutableStateOf(emptyList<ImportedProfile>()) }
    var profilesLoaded by remember { mutableStateOf(false) }
    var tileConnectFlow by remember { mutableStateOf(false) }
    var selectedProfileId by remember { mutableStateOf("") }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
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
    val systemDark = isSystemInDarkTheme()
    var appearance by remember { mutableStateOf(preferencesStore.appearance(systemDark)) }
    val designPreset = appearance.style
    val appearanceSaveMutex = remember { Mutex() }
    var updateChecksEnabled by remember { mutableStateOf(preferencesStore.updateChecksEnabled()) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updatePrompt by remember { mutableStateOf<AndroidRelease?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var installedApps by remember { mutableStateOf(emptyList<AppListEntry>()) }
    var appsLoading by remember { mutableStateOf(true) }
    var appsError by remember { mutableStateOf(false) }
    var appsLoaded by remember { mutableStateOf(false) }
    var pendingVpnConfig by remember { mutableStateOf<String?>(null) }
    var pendingVpnProfileName by remember { mutableStateOf<String?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    val vpnState by VpnRuntime.state.collectAsStateWithLifecycle()
    val serverChecks = remember { ServerChecks(context.applicationContext, scope) }
    val serverCheckState by serverChecks.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, serverChecks) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) serverChecks.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); serverChecks.cancel() }
    }
    LaunchedEffect(page, vpnState.connecting) {
        if (page != Page.Servers || vpnState.connecting) serverChecks.cancel()
    }
    LaunchedEffect(profiles) { serverChecks.reset() }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            context.sendBroadcast(Intent(NovaVpnService.ACTION_REFRESH_NOTIFICATION).setPackage(context.packageName))
        } else {
            Toast.makeText(context, "Уведомления выключены. Их можно включить в настройках NOVA; VPN продолжает работать.", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(tutorialStep == null) {
        if (tutorialStep == null && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferencesStore.notificationPermissionRequested()) {
            preferencesStore.markNotificationPermissionRequested()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
        val profileName = pendingVpnProfileName
        pendingVpnConfig = null
        pendingVpnProfileName = null
        if (result.resultCode == Activity.RESULT_OK && !config.isNullOrBlank()) {
            startVpnService(context, config, profileName.orEmpty())
            connectError = null
        } else if (result.resultCode != Activity.RESULT_OK) {
            connectError = "Для VPN нужно разрешение Android."
        }
    }

    fun requestVpnStart(config: String, profileName: String) {
        runCatching {
            connectError = null
            val consent = VpnService.prepare(context)
            if (consent != null) {
                pendingVpnConfig = config
                pendingVpnProfileName = profileName
                vpnConsentLauncher.launch(consent)
            } else startVpnService(context, config, profileName)
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
                runCatching { startVpnService(context, config, profile.name) }
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
        if (!showCurrentVersion && !preferencesStore.automaticUpdateCheckDue(System.currentTimeMillis(), BuildConfig.VERSION_NAME)) return
        scope.launch {
            checkingUpdates = true
            preferencesStore.markUpdateCheck(System.currentTimeMillis(), BuildConfig.VERSION_NAME)
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
        }.onSuccess { config -> requestVpnStart(config, selected.name) }
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
        }.onSuccess { config -> requestVpnStart(config, selected.name) }
            .onFailure { error -> connectError = error.message ?: "Не удалось подготовить автоподключение." }
    }
    LaunchedEffect(page, routingStore) {
        if (page != Page.Routing || appsLoaded) return@LaunchedEffect
        appsLoading = true
        try {
            installedApps = withContext(Dispatchers.IO) { loadInstalledApps(context.packageManager, context.packageName) }
            appsError = false
            appsLoaded = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            appsError = true
        } finally {
            appsLoading = false
        }
    }

    LaunchedEffect(tutorialStep == null, updateChecksEnabled) {
        if (tutorialStep == null && updateChecksEnabled) checkForUpdates(showCurrentVersion = false)
    }

    val syncState = when {
        vpnState.connected -> "connected"
        vpnState.connecting -> "connecting"
        vpnState.error != null -> "error"
        else -> "disconnected"
    }
    LaunchedEffect(syncState) {
        WearStatusSync.publish(context, syncState)
    }

    val configuration = LocalConfiguration.current
    val colors = remember(appearance, configuration) { designColorScheme(designPreset, context, appearance.isDark, appearance.materialHue) }
    NovaTheme(appearance = appearance, colorScheme = colors) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                NovaNavigationBar {
                    Page.entries.forEach { item ->
                        NovaNavigationItem(
                            enabled = tutorialStep == null,
                            selected = page == item,
                            onClick = { if (tutorialStep == null) page = item },
                            icon = item.icon, label = item.title
                        )
                    }
                }
            }
        ) { insets ->
          CompositionLocalProvider(LocalDesignPreset provides designPreset, LocalTutorialStep provides tutorialStep) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .novaBackdropSource()
                    .padding(PaddingValues(
                        start = insets.calculateLeftPadding(LocalLayoutDirection.current),
                        end = insets.calculateRightPadding(LocalLayoutDirection.current),
                        top = insets.calculateTopPadding(),
                        bottom = if (designPreset == DesignStyle.IOS_LIQUID_GLASS) 0.dp else insets.calculateBottomPadding()
                    ))
            ) {
              val tutorialAvailableHeight = maxHeight
              Column(Modifier.fillMaxSize()) {
                tutorialStep?.let { step ->
                    TutorialPanel(step, closing = tutorialClosing,
                        onBack = { if (tutorialIndex > 0) tutorialIndex-- },
                        onNext = { if (step == TutorialStep.Connect) finishTutorial() else tutorialIndex++ },
                        onSkip = ::finishTutorial, availableHeight = tutorialAvailableHeight)
                }
                AnimatedContent(
                    modifier = Modifier.weight(1f),
                    targetState = page,
                    transitionSpec = { fadeIn(tween(if (designPreset == DesignStyle.WINDOWS_11) 120 else 220)) togetherWith fadeOut(tween(120)) },
                    label = "page"
                ) { selected ->
                    when (selected) {
                        Page.Home -> HomePage(
                            profiles = profiles,
                            selectedProfileId = selectedProfileId,
                            profile = profiles.firstOrNull { it.id == selectedProfileId },
                            vpnState = vpnState,
                            error = connectError ?: vpnState.error,
                            onCopyDiagnostics = {
                                val report = VpnDiagnostics.report(profiles.firstOrNull { it.id == selectedProfileId }, routing,
                                    Build.VERSION.SDK_INT, vpnState)
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NOVA VPN diagnostics", report))
                                Toast.makeText(context, "Диагностика скопирована. Отправьте этот текст разработчику.", Toast.LENGTH_LONG).show()
                            },
                            onSelectProfile = {
                                selectedProfileId = it
                                scope.launch(Dispatchers.IO) {
                                    runCatching { selectedProfileStore.save(it) }
                                }
                                applyLiveConfiguration(it, routing)
                            },
                            onManageProfiles = { page = Page.Servers },
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
                                        }.onSuccess { config -> requestVpnStart(config, selected.name) }.onFailure { error ->
                                            connectError = error.message ?: "Конфигурация не прошла подготовку."
                                        }
                                    }
                                }
                            }
                        )
                        Page.Servers -> ServersPage(
                            profiles = profiles,
                            selectedId = selectedProfileId,
                            vpnState = vpnState,
                            checks = serverCheckState,
                            onCheck = { serverChecks.start(profiles) },
                            onCancelCheck = { serverChecks.cancel() },
                            onConnect = {
                                serverChecks.cancel()
                                val selected = profiles.firstOrNull { it.id == selectedProfileId }
                                if (selected != null) scope.launch {
                                    runCatching { withContext(Dispatchers.Default) { SingBoxConfigFactory.build(selected, routing, context.packageName) } }
                                        .onSuccess { requestVpnStart(it, selected.name) }
                                        .onFailure { connectError = "Не удалось подготовить подключение." }
                                }
                            },
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
                            onStartTutorial = { tutorialIndex = 0 },
                            onNotificationSettings = {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                                    (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val notificationSettings = if (Build.VERSION.SDK_INT >= 26) {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    runCatching { context.startActivity(notificationSettings) }
                                        .onFailure { settingsError = "Не удалось открыть настройки уведомлений Android." }
                                }
                            },
                            autoConnect = autoConnect,
                            appearance = appearance,
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
                            onAppearanceChanged = { next ->
                                val previous = appearance
                                appearance = next
                                scope.launch {
                                    runCatching { appearanceSaveMutex.withLock {
                                        withContext(Dispatchers.IO) { preferencesStore.setAppearance(next) }
                                    } }
                                        .onSuccess { settingsError = null }
                                        .onFailure {
                                            if (appearance == next) appearance = previous
                                            settingsError = "Не удалось сохранить оформление."
                                        }
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
        }

        if (showImport) {
            AlertDialog(
                onDismissRequest = { if (!importing) showImport = false },
                title = { Text("Добавить конфигурацию") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS || LocalDesignPreset.current == DesignStyle.MATERIAL_YOU || LocalDesignPreset.current == DesignStyle.ONE_UI || LocalDesignPreset.current == DesignStyle.WINDOWS_11) "Вставьте ссылку или JSON-конфигурацию от вашего провайдера. Профиль хранится зашифрованно на устройстве." else "Добавьте VPN-ссылку, HTTPS-подписку, список ссылок, sing-box или Xray JSON. Можно отсканировать QR-код, выбрать файл или вставить текст. Данные сохраняются зашифрованно на устройстве.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                enabled = !importing,
                                onClick = { launchQrScanner() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Сканировать QR") }
                            OutlinedButton(
                                enabled = !importing,
                                onClick = {
                                    showImport = false
                                    importFileLauncher.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Выбрать файл") }
                        }
                        OutlinedTextField(
                            shape = novaFieldShape(),
                            value = importText,
                            enabled = !importing,
                            isError = importError != null,
                            onValueChange = { importText = it; importError = null },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = { Text("vless://…, QR-ссылка, sing-box или Xray JSON") }
                        )
                        importError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
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
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

private fun startVpnService(context: android.content.Context, config: String, profileName: String) {
    val intent = Intent(context, NovaVpnService::class.java)
        .setAction(NovaVpnService.ACTION_START)
        .putExtra(NovaVpnService.EXTRA_CONFIG, config)
        .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, profileName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
    else context.startService(intent)
}

@Composable
internal fun PageFrame(title: String, subtitle: String, tutorialTargetIndex: Int = 1, content: LazyListScope.() -> Unit) {
    if (LocalDesignPreset.current == DesignStyle.MATERIAL_YOU) {
        MaterialFrame(title, subtitle, tutorialTargetIndex, content)
        return
    }
    if (LocalDesignPreset.current == DesignStyle.WINDOWS_11) {
        WindowsFrame(title, subtitle, tutorialTargetIndex, content = content)
        return
    }
    if (LocalDesignPreset.current == DesignStyle.ONE_UI) {
        OneUIFrame(title, subtitle, tutorialTargetIndex, content = content)
        return
    }
    val ios = LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS
    val listState = rememberLazyListState()
    val step = LocalTutorialStep.current
    LaunchedEffect(step, tutorialTargetIndex) {
        if (step != null) listState.scrollToItem(tutorialTargetIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp,
            bottom = if (LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS) 120.dp else 22.dp),
        verticalArrangement = Arrangement.spacedBy(if (ios) 20.dp else 16.dp)
    ) {
        item(contentType = "page-header") {
            Column(Modifier.padding(top = if (LocalDesignPreset.current == DesignStyle.ONE_UI && LocalConfiguration.current.screenHeightDp >= 700) 48.dp else 0.dp, bottom = 4.dp)) {
                Text(title, style = if (ios) MaterialTheme.typography.headlineLarge else if (LocalDesignPreset.current == DesignStyle.ONE_UI) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = if (ios) 13.sp else 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
internal fun SoftCard(onClick: (() -> Unit)? = null, tutorialTarget: TutorialStep? = null, content: @Composable () -> Unit) {
    val ios = LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS
    val highlighted = tutorialTarget != null && LocalTutorialStep.current == tutorialTarget
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary,
                RoundedCornerShape(LocalDesignPreset.current.cardRadius.dp)) else Modifier)
            .then(onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier),
        shape = RoundedCornerShape(LocalDesignPreset.current.cardRadius.dp),
        // Opaque flat cards avoid alpha blending and rounded shadow layers while scrolling.
        colors = CardDefaults.cardColors(containerColor = if (LocalDesignPreset.current == DesignStyle.MATERIAL_YOU) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (ios || LocalDesignPreset.current == DesignStyle.MATERIAL_YOU || LocalDesignPreset.current == DesignStyle.ONE_UI) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(if (ios) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (ios) 10.dp else 14.dp)) { content() }
    }
}

@Composable
internal fun StandardHomePage(
    profiles: List<ImportedProfile>,
    selectedProfileId: String,
    profile: ImportedProfile?,
    vpnState: VpnUiState,
    error: String?,
    onCopyDiagnostics: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onToggle: () -> Unit
) = PageFrame("Добрый день", "Ваше подключение под контролем") {
    item { SoftCard(tutorialTarget = TutorialStep.Connect) {
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
                    vpnState.connected -> MaterialTheme.colorScheme.onSecondaryContainer
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
        vpnState.transportNotice?.let { notice ->
            Text(notice, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
            val controlShape = when (LocalDesignPreset.current) {
                DesignStyle.WINDOWS_11 -> RoundedCornerShape(8.dp)
                DesignStyle.MATERIAL_YOU -> RoundedCornerShape(topStart = 48.dp, topEnd = 24.dp, bottomEnd = 48.dp, bottomStart = 24.dp)
                DesignStyle.ONE_UI -> RoundedCornerShape(36.dp)
                else -> CircleShape
            }
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .shadow(10.dp, controlShape)
                    .clip(controlShape)
                    .background(Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
                    ))
                    .then(if (LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS) Modifier.novaGlass() else Modifier)
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
        Text("NOVA VPN ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onCopyDiagnostics, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Скопировать диагностику")
        }
        Text("Последние события подключения без ключей, адресов сервера и посещённых сайтов.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val quickProfiles = remember(profiles, selectedProfileId) {
                val selected = profiles.firstOrNull { it.id == selectedProfileId }
                buildList {
                    if (selected != null) add(selected)
                    for (profile in profiles) {
                        if (size == 3) break
                        if (profile.id != selectedProfileId) add(profile)
                    }
                }
            }
            quickProfiles.forEach { item ->
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
internal fun StandardServersPage(
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
) = PageFrame("Серверы", "Профили хранятся зашифрованно на устройстве",
    tutorialTargetIndex = if (LocalTutorialStep.current == TutorialStep.Servers) 3 + (if (error != null) 1 else 0) else 2) {
    item(contentType = "server-map") {
        ServerMapPanel(profiles, selectedId, checks, vpnState, onCheck, onCancelCheck, onSelect, onConnect)
    }
    item { SoftCard(tutorialTarget = TutorialStep.Import) {
        Text("Вставьте VPN-ссылку, HTTPS-подписку, список ссылок, sing-box или Xray JSON. Поддерживаются VLESS, VMess, Trojan, Shadowsocks, Hysteria2 и TUIC.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить конфигурацию")
        }
    } }

    error?.let { message -> item { SoftCard { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) } } }

    if (profiles.isEmpty()) {
        item { SoftCard(tutorialTarget = TutorialStep.Servers) {
            Text("Пока нет профилей", fontWeight = FontWeight.SemiBold)
            Text("Добавьте ссылку или JSON-конфигурацию, чтобы подготовить подключение.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    } else items(profiles, key = { it.id }, contentType = { "server" }) { profile ->
        SoftCard(onClick = if (profile.id == selectedId) null else ({ onSelect(profile.id) }), tutorialTarget = TutorialStep.Servers) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                    Text("${profile.protocol.uppercase()}${if (profile.server.isNotBlank()) " · ${profile.server}:${profile.port}" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ServerCheckBadge(checks.results[profile.id], checks.currentId == profile.id)
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
internal fun RoutingPage(
    apps: List<AppListEntry>,
    loading: Boolean,
    failed: Boolean,
    settings: AppRoutingSettings,
    onSettingsChanged: (AppRoutingSettings) -> Unit
) {
    val materialRouting = LocalDesignPreset.current == DesignStyle.MATERIAL_YOU
    val iosRouting = LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS
    val oneUIRouting = LocalDesignPreset.current == DesignStyle.ONE_UI
    val windowsRouting = LocalDesignPreset.current == DesignStyle.WINDOWS_11
    val tabbedRouting = iosRouting || materialRouting || oneUIRouting || windowsRouting
    var iosRoutingTab by rememberSaveable { mutableStateOf(0) }
    val routingTutorial = LocalTutorialStep.current
    LaunchedEffect(routingTutorial) {
        if (routingTutorial == TutorialStep.Apps) iosRoutingTab = 1
        if (routingTutorial == TutorialStep.Sites) iosRoutingTab = 0
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var siteInput by rememberSaveable { mutableStateOf("") }
    var siteError by remember { mutableStateOf<String?>(null) }
    var editingDomain by rememberSaveable { mutableStateOf<String?>(null) }
    var editedDomain by rememberSaveable { mutableStateOf("") }
    var editError by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleApps by produceState(apps, apps, searchQuery) {
        // Cancel superseded searches and keep package scans off the UI thread.
        if (searchQuery.isNotBlank()) delay(120)
        value = withContext(Dispatchers.Default) { filterAppList(apps, searchQuery) }
    }

    editingDomain?.let { original ->
        AlertDialog(
            onDismissRequest = { editingDomain = null; editError = null },
            title = { Text("Редактировать домен") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                            shape = novaFieldShape(),
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

    PageFrame(if (tabbedRouting) "Маршруты" else "Маршрутизация", "Сначала правила сайтов, затем приложений",
        tutorialTargetIndex = if (tabbedRouting) 2 else if (LocalTutorialStep.current == TutorialStep.Apps) 2 + settings.sites.size else 1) {
    if (iosRouting) item { IOSRoutingTabs(iosRoutingTab) { iosRoutingTab = it } }
    if (materialRouting) item { MaterialRouteTabs(iosRoutingTab) { iosRoutingTab = it } }
    if (windowsRouting) item { WindowsRouteTabs(iosRoutingTab) { iosRoutingTab = it } }
    if (oneUIRouting) item { OneUIRouteTabs(iosRoutingTab) { iosRoutingTab = it } }
    if (!tabbedRouting || iosRoutingTab == 0) {
    item {
        SoftCard(tutorialTarget = TutorialStep.Sites) {
            Text("Маршрутизация по сайтам", fontWeight = FontWeight.SemiBold)
            Text(if (tabbedRouting) "Правило применяется к домену и поддоменам." else "Введите домен без https://. Правило охватывает домен и его поддомены и имеет приоритет над маршрутом браузера.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                            shape = novaFieldShape(),
                    value = siteInput,
                    isError = siteError != null,
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
    items(settings.sites, key = { "site:${it.domain}" }, contentType = { "site-rule" }) { rule ->
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
    }
    if (!tabbedRouting || iosRoutingTab == 1) {
    item {
        SoftCard(tutorialTarget = TutorialStep.Apps) {
            Text(if (tabbedRouting) "Выбранные приложения" else "Режим списка", fontWeight = FontWeight.SemiBold)
            if (windowsRouting) WindowsRouteMode(if (settings.mode == AppRoutingMode.INCLUDE_SELECTED) 0 else 1) {
                onSettingsChanged(settings.copy(mode = if (it == 0) AppRoutingMode.INCLUDE_SELECTED else AppRoutingMode.EXCLUDE_SELECTED))
            } else if (oneUIRouting) OneUIRouteMode(if (settings.mode == AppRoutingMode.INCLUDE_SELECTED) 0 else 1) {
                onSettingsChanged(settings.copy(mode = if (it == 0) AppRoutingMode.INCLUDE_SELECTED else AppRoutingMode.EXCLUDE_SELECTED))
            } else if (materialRouting) MaterialRouteMode(if (settings.mode == AppRoutingMode.INCLUDE_SELECTED) 0 else 1) {
                onSettingsChanged(settings.copy(mode = if (it == 0) AppRoutingMode.INCLUDE_SELECTED else AppRoutingMode.EXCLUDE_SELECTED))
            } else if (iosRouting) IOSRoutingTabs(if (settings.mode == AppRoutingMode.INCLUDE_SELECTED) 0 else 1, labels = listOf("Через VPN", "Напрямую")) {
                onSettingsChanged(settings.copy(mode = if (it == 0) AppRoutingMode.INCLUDE_SELECTED else AppRoutingMode.EXCLUDE_SELECTED))
            } else {
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
            }
            Text(
                if (settings.mode == AppRoutingMode.INCLUDE_SELECTED)
                    if (tabbedRouting) "Через VPN — только отмеченные приложения." else "Отмеченные приложения идут через VPN, остальные — напрямую. Сайтовые правила имеют приоритет."
                else if (tabbedRouting) "Через VPN — все приложения, кроме отмеченных." else "Все приложения идут через VPN, кроме отмеченных. Сайтовые правила имеют приоритет.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Отмечено: ${settings.packages.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("IPv6 через VPN", fontWeight = FontWeight.Medium)
                    Text(if (tabbedRouting) "Если сервер поддерживает IPv6." else "Включайте, если сервер поддерживает IPv6. По умолчанию обычные профили используют IPv4 для совместимости; DNS из полного JSON сохраняется.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.ipv6Enabled,
                    onCheckedChange = { onSettingsChanged(settings.copy(ipv6Enabled = it)) })
            }
        }
    }
    if (loading) item { SoftCard { Text("Загружаю список приложений…", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    else if (failed) item { SoftCard { Text("Android не предоставил список приложений. Проверьте настройки доступа.", color = MaterialTheme.colorScheme.error) } }
    else if (apps.isEmpty()) item { SoftCard { Text("Не найдено приложений с ярлыком запуска.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    else {
        item {
            SoftCard {
                OutlinedTextField(
                            shape = novaFieldShape(),
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
        } else items(visibleApps, key = { it.packageName }, contentType = { "application" }) { app ->
        val checked = app.packageName in settings.packages
        val iconContext = LocalContext.current.applicationContext
        val appIcon by produceState(AppIconCache.peek(app.packageName), app.packageName) {
            value = AppIconCache.load(iconContext.packageManager, app.packageName)
        }
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
                Box(Modifier.size(44.dp)) {
                    appIcon?.let { bitmap ->
                        Image(bitmap = bitmap, contentDescription = "Иконка ${app.label}", modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Medium)
                    Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
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
}

@Composable
internal fun StandardSettingsPage(
    onStartTutorial: () -> Unit,
    onNotificationSettings: () -> Unit,
    autoConnect: Boolean,
    appearance: Appearance,
    updateChecksEnabled: Boolean,
    checkingUpdates: Boolean,
    updateStatus: String?,
    error: String?,
    onAddQuickSettingsTile: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onAppearanceChanged: (Appearance) -> Unit,
    onUpdateChecksChanged: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit
) = PageFrame("Настройки", "Конфиденциальность и поведение NOVA", tutorialTargetIndex = 7) {
    item { SoftCard {
        Text("Обучение", fontWeight = FontWeight.SemiBold)
        Text("Шесть шагов: ключи, приложения, сайты, серверы, обновления и подключение.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onStartTutorial, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Начать обучение")
        }
    } }
    item { SoftCard {
        Text("Уведомления о VPN", fontWeight = FontWeight.SemiBold)
        Text("Одна постоянная карточка NOVA с именем сервера и состоянием VPN. По умолчанию без звука, вибрации и всплывающего баннера.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onNotificationSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Настроить уведомления")
        }
    } }
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
        AppearancePicker(appearance = appearance, onChange = onAppearanceChanged)
    } }
    item { SoftCard {
        SettingRow(Icons.Rounded.MoreHoriz, "Уведомление VPN", "Показывает статус и кнопку отключения во время работы туннеля.")
        SettingRow(Icons.Rounded.Lock, "Хранилище профилей", "Профили шифруются AES-GCM ключом Android Keystore.")
    } }
    item { SoftCard(tutorialTarget = TutorialStep.Updates) {
        Text("Установленная версия: ${BuildConfig.VERSION_NAME}", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

