package com.nova.vpn.android

import android.annotation.TargetApi
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.LinkProperties
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification as BoxNotification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import com.nova.vpn.android.wear.WearStatusSync
import com.nova.vpn.android.data.AppPreferencesStore
import com.nova.vpn.android.data.VpnEngine
import com.nova.vpn.android.data.RealityConfigFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface as JNetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Android VpnService host for the sing-box libbox engine. */
class NovaVpnService : VpnService(), PlatformInterface, CommandServerHandler {
    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val monitors = ConcurrentHashMap<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()
    private val availableUnderlays = ConcurrentHashMap<Network, NetworkCapabilities>()
    private val interfacePublications = ConcurrentHashMap<InterfaceUpdateListener, InterfacePublicationGate>()
    private val serviceQueue = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nova-vpn-service").apply { isDaemon = true }
    }
    @Volatile private var latestStartId = 0
    @Volatile private var activeProfileName = "Выбранная конфигурация"
    private var commandServer: CommandServer? = null
    private var debugLogClient: CommandClient? = null
    private var tunDescriptor: ParcelFileDescriptor? = null
    @Volatile private var realityHandle = 0L
    @Volatile private var activeEngine: VpnEngine? = null
    private var realityNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val realityHealthQueue = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "nova-reality-health").apply { isDaemon = true }
    }
    private var realityHealthTask: java.util.concurrent.ScheduledFuture<*>? = null
    private val probeQueue = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "nova-vpn-check").apply { isDaemon = true } }
    @Volatile private var trafficProbe: AndroidTrafficProbe? = null
    private val notificationRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_NOTIFICATION && VpnRuntime.state.value.connected) {
                updateNotification("Туннель подключён")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        VpnRuntime.attach(applicationContext)
        VpnNotifications.prepare(this)
        val filter = IntentFilter(ACTION_REFRESH_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(notificationRefreshReceiver, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(notificationRefreshReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_STOP) {
            stopTunnel(startId)
            return START_NOT_STICKY
        }
        // Native stop closes its TUN. Refuse live changes rather than briefly
        // restoring unprotected routing while a replacement is being established.
        if (activeEngine == VpnEngine.REALITY_CORE && realityHandle != 0L) {
            VpnRuntime.setTransportWarning("Reality Core: перед сменой сервера или правил отключите VPN, затем подключитесь снова. Текущий туннель оставлен без изменений.")
            return START_NOT_STICKY
        }

        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config.isNullOrBlank()) {
            VpnRuntime.setFailure("Не передана конфигурация VPN")
            WearStatusSync.publish(this, "error")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val requestedProfileName = intent?.getStringExtra(EXTRA_PROFILE_NAME)
            ?.trim()?.replace(Regex("\\s+"), " ")?.take(48)?.takeIf(String::isNotBlank)
            ?: "Выбранная конфигурация"

        try {
            startForegroundCompat()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to enter foreground", error)
            VpnRuntime.setFailure(error.message ?: "Не удалось запустить VPN-службу Android")
            WearStatusSync.publish(this, "error")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        serviceQueue.execute {
            if (startId != latestStartId) return@execute
            if (activeEngine == VpnEngine.REALITY_CORE && realityHandle != 0L) {
                VpnRuntime.setTransportWarning("Reality Core: отключите VPN перед сменой конфигурации. Текущий туннель оставлен без изменений.")
                return@execute
            }
            // Publish startup only after the serial queue accepted this request.
            // A second START may arrive before native start returns its handle;
            // rejecting that queued request must not erase the live tunnel state.
            VpnDiagnostics.reset()
            VpnRuntime.setConnecting()
            WearStatusSync.publish(this, "connecting")
            activeProfileName = requestedProfileName
            var stage = "vpn-permission"
            try {
                Log.i(TAG, "VPN engine startup stage=$stage")
                if (VpnService.prepare(this) != null) error("Не выдано разрешение Android на VPN")
                val engine = AppPreferencesStore(this).vpnEngine()
                VpnDiagnostics.setEngine(engine == VpnEngine.REALITY_CORE)
                check(activeEngine == null || activeEngine == engine) { "Перед сменой ядра отключите VPN." }
                if (engine == VpnEngine.REALITY_CORE) {
                    stage = "reality-start"
                    startReality(config, startId)
                    return@execute
                }
                activeEngine = VpnEngine.SING_BOX
                VpnDiagnostics.record("engine_sing_box")
                stage = "libbox-setup"
                VpnDiagnostics.record("engine_setup")
                Log.i(TAG, "VPN engine startup stage=$stage")
                ensureLibboxSetup()
                stage = "config-validation"
                VpnDiagnostics.record("config_validation")
                Log.i(TAG, "VPN engine startup stage=$stage")
                trafficProbe?.cancel()
                val probe = runCatching { AndroidTrafficProbe.prepare(config) }.getOrNull()
                trafficProbe = probe
                val runtimeConfig = probe?.config ?: config
                Libbox.checkConfig(runtimeConfig)
                stage = "command-server-create"
                Log.i(TAG, "VPN engine startup stage=$stage")
                val server = commandServer ?: CommandServer(this, this).also { created ->
                    stage = "command-server-start"
                    Log.i(TAG, "VPN engine startup stage=$stage")
                    created.start()
                    commandServer = created
                }
                // Reuse libbox's reload path for profile/routing changes. It replaces
                // the running config without deliberately closing the VPN service first.
                stage = "tunnel-start-or-reload"
                Log.i(TAG, "VPN engine startup stage=$stage")
                server.startOrReloadService(runtimeConfig, OverrideOptions())
                VpnDiagnostics.record("engine_started")
                startDebugLogStream()
                VpnRuntime.setConnected()
                VpnDiagnostics.record("tunnel_active")
                WearStatusSync.publish(this, "connected")
                updateNotification("VPN подключён")
                if (probe != null && startId == latestStartId) {
                    VpnRuntime.setTrafficChecking()
                    probeQueue.execute {
                        val success = probe.check()
                        if (startId == latestStartId && trafficProbe === probe) {
                            VpnRuntime.setTrafficResult(success)
                            updateNotification(if (success) "HTTPS через VPN подтверждён" else "Туннель активен · проверка не подтверждена")
                            Log.i(TAG, "Profile HTTPS verification success=$success")
                        }
                    }
                }
            } catch (error: Throwable) {
                VpnDiagnostics.record("startup_failed")
                trafficProbe?.cancel(); trafficProbe = null
                Log.e(TAG, "VPN start failed at stage=$stage", error)
                closeEngine()
                VpnRuntime.setFailure(error.message ?: "Не удалось запустить VPN")
                WearStatusSync.publish(this, "error")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        stopTunnel(latestStartId)
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(notificationRefreshReceiver) }
        trafficProbe?.cancel(); trafficProbe = null
        probeQueue.shutdownNow()
        realityHealthQueue.shutdownNow()
        monitors.values.forEach { runCatching { connectivity.unregisterNetworkCallback(it) } }
        monitors.clear()
        interfacePublications.clear()
        availableUnderlays.clear()
        runCatching {
            serviceQueue.execute {
                closeEngine()
                if (VpnRuntime.state.value.error == null) {
                    VpnRuntime.setDisconnected()
                    WearStatusSync.publish(this, "disconnected")
                }
            }
            serviceQueue.shutdown()
        }.onFailure {
            closeEngine()
            if (VpnRuntime.state.value.error == null) {
                VpnRuntime.setDisconnected()
                WearStatusSync.publish(this, "disconnected")
            }
            serviceQueue.shutdownNow()
        }
        super.onDestroy()
    }

    private fun stopTunnel(stopAfterStartId: Int = latestStartId) {
        trafficProbe?.cancel(); trafficProbe = null
        serviceQueue.execute {
            closeEngine()
            VpnRuntime.setDisconnected()
            WearStatusSync.publish(this, "disconnected")
            stopForeground(STOP_FOREGROUND_REMOVE)
            // A later startService() increments startId. Do not let this stale stop
            // request tear down a tunnel started after the stop was queued.
            if (stopAfterStartId > 0) stopSelfResult(stopAfterStartId)
            else stopSelf()
        }
    }

    private fun closeEngine() {
        trafficProbe?.cancel()
        trafficProbe = null
        realityHealthTask?.cancel(false)
        realityHealthTask = null
        if (realityHandle != 0L) {
            val handle = realityHandle
            realityHandle = 0L
            runCatching { RealityCoreBridge.stop(handle) }
        }
        realityNetworkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        realityNetworkCallback = null
        activeEngine = null
        runCatching { debugLogClient?.disconnect() }
        debugLogClient = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
    }

    private fun startReality(config: String, startId: Int) {
        RealityCoreBridge.requireAvailable()
        // Conversion and native validation happen BEFORE closing an existing tunnel.
        val probe = AndroidTrafficProbe.prepare(config)
        val packageUid: ((String) -> Int)? = if (Build.VERSION.SDK_INT >= 29) { name ->
            val uid = runCatching { packageManager.getApplicationInfo(name, 0).uid }.getOrNull()
            require(uid != null && uid >= 0) { "Приложение недоступно. Обновите список приложений маршрутизации." }
            // Android cannot distinguish packages sharing a Linux UID. Do not promise otherwise.
            require(packageManager.getPackageInfo(name, 0).sharedUserId == null &&
                packageManager.getPackagesForUid(uid)?.toSet() == setOf(name)) {
                "Совместная маршрутизация недоступна для приложений с общим UID. Выберите sing-box."
            }
            uid
        } else null
        val prepared = RealityConfigFactory.prepare(probe?.config ?: config, packageName, packageUid)
        val runtimeConfig = prepared.config
        check(RealityCoreBridge.validate(runtimeConfig, filesDir.absolutePath)) {
            "Этот профиль или его правила не поддерживаются Reality Core. Выберите sing-box."
        }
        trafficProbe?.cancel()
        closeEngine()
        activeEngine = VpnEngine.REALITY_CORE
        VpnDiagnostics.record("engine_reality_core")
        VpnDiagnostics.record("reality_revision_994ce4ef")
        registerRealityNetworkMonitor()
        val builder = Builder().setSession("NOVA VPN · Reality Core").setMtu(1500)
            .addAddress("172.19.0.1", 30).addRoute("0.0.0.0", 0).addDnsServer("172.19.0.2")
        // Capture IPv6 even when AAAA is disabled: it must not bypass the VPN.
        builder.addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0)
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
        prepared.includedPackages.forEach { builder.addAllowedApplication(it) }
        prepared.excludedPackages.forEach { builder.addDisallowedApplication(it) }
        val descriptor = builder.establish() ?: error("Android не создал TUN-интерфейс")
        // A detached FD is consumed by native start on both success and failure.
        val fd = descriptor.detachFd()
        descriptor.close()
        realityHandle = RealityCoreBridge.start(runtimeConfig, filesDir.absolutePath, fd,
            object : RealityCoreBridge.Callbacks {
                override fun protectSocket(fd: Int): Boolean = runCatching {
                    autoDetectInterfaceControl(fd)
                    true
                }.onFailure { VpnDiagnostics.record("reality_protect_failed") }.getOrDefault(false)
                override fun coreWarning(message: String) { VpnDiagnostics.coreMessage(message) }
                override fun connectionOwner(protocol: Int, source: String, destination: String): Long {
                    if (Build.VERSION.SDK_INT < 29) return -1L
                    return ConnectionOwnerLookup.resolve(protocol, source, destination) { type, local, remote ->
                        connectivity.getConnectionOwnerUid(type, local, remote)
                    }
                }
            })
        check(realityHandle != 0L) { "Не удалось запустить Reality Core. Выберите sing-box или отправьте диагностический отчёт." }
        trafficProbe = probe
        VpnDiagnostics.record("engine_started")
        VpnRuntime.setConnected()
        VpnDiagnostics.record("tunnel_active")
        WearStatusSync.publish(this, "connected")
        updateNotification("VPN подключён · Reality Core")
        if (probe != null) {
            VpnRuntime.setTrafficChecking()
            probeQueue.execute {
                val success = probe.check()
                if (trafficProbe === probe) {
                    VpnRuntime.setTrafficResult(success)
                    updateNotification(if (success) "HTTPS через VPN подтверждён · Reality Core" else "Reality Core · проверка не подтверждена")
                }
            }
        }
        // Only reads the native terminal-error channel; no periodic network probes.
        realityHealthTask = realityHealthQueue.scheduleWithFixedDelay({
            runCatching { serviceQueue.execute {
                if (realityHandle != 0L && !RealityCoreBridge.isAlive(realityHandle)) {
                    trafficProbe?.cancel(); trafficProbe = null
                    closeEngine()
                    VpnDiagnostics.record("reality_terminal_failure")
                    VpnRuntime.setFailure("Reality Core остановилось. Отправьте диагностический отчёт или выберите sing-box.")
                    WearStatusSync.publish(this, "error")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(latestStartId)
                }
            } }
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun registerRealityNetworkMonitor() {
        availableUnderlays.clear()
        connectivity.allNetworks.forEach { network ->
            connectivity.getNetworkCapabilities(network)?.takeIf {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }?.let { availableUnderlays[network] = it }
        }
        fun publish() {
            val network = selectUnderlay()
            runCatching { setUnderlyingNetworks(network?.let { arrayOf(it) }) }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.getNetworkCapabilities(network)?.let { availableUnderlays[network] = it }
                publish()
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                availableUnderlays[network] = capabilities
                publish()
            }
            override fun onLost(network: Network) { availableUnderlays.remove(network); publish() }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) { publish() }
        }
        realityNetworkCallback = callback
        connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
        publish()
    }

    /** Read core warnings for safe UI notices; detailed logs/connection events are debug-only. */
    private fun startDebugLogStream() {
        if (debugLogClient != null) return
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandLog)
            if (BuildConfig.DEBUG) addCommand(Libbox.CommandConnections)
        }
        val client = CommandClient(object : CommandClientHandler {
            override fun clearLogs() = Unit
            override fun connected() {
                Log.d(TAG, "sing-box log stream connected")
            }
            override fun disconnected(message: String?) {
                Log.d(TAG, "sing-box log stream disconnected: ${message.orEmpty()}")
            }
            override fun setDefaultLogLevel(level: Int) {
                Log.d(TAG, "sing-box log level=$level")
            }
            override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) = Unit
            override fun updateClashMode(newMode: String?) = Unit
            override fun writeConnectionEvents(events: io.nekohasekai.libbox.ConnectionEvents?) {
                if (events == null) return
                val iterator = events.iterator()
                while (iterator.hasNext()) {
                    val connection = iterator.next().connection ?: continue
                    Log.d(TAG, "sing-box flow protocol=${connection.protocol}, " +
                        "network=${connection.network}, destination=${connection.displayDestination()}, " +
                        "outbound=${connection.outboundType}[${connection.outbound}]")
                }
            }
            override fun writeGroups(groups: io.nekohasekai.libbox.OutboundGroupIterator?) = Unit
            override fun writeOutbounds(outbounds: io.nekohasekai.libbox.OutboundGroupItemIterator?) = Unit
            override fun writeLogs(messageList: LogIterator?) {
                if (messageList == null) return
                while (messageList.hasNext()) {
                    val entry = messageList.next()
                    val rawMessage = entry.message.orEmpty()
                    if (entry.level <= 3) VpnDiagnostics.coreMessage(rawMessage)
                    VpnTransportHealth.warningFor(rawMessage)?.let { warning ->
                        VpnRuntime.setTransportWarning(warning)
                        // Keep the per-request diagnostic; do not mark the whole VPN or watch as failed.
                    }
                    if (!BuildConfig.DEBUG) continue
                    val message = redactDiagnosticSecrets(rawMessage)
                    when (entry.level) {
                        1, 2 -> Log.e(TAG, "sing-box: $message")
                        3 -> Log.w(TAG, "sing-box: $message")
                        else -> Log.d(TAG, "sing-box: $message")
                    }
                }
            }
            override fun writeStatus(message: StatusMessage?) = Unit
        }, options)
        debugLogClient = client
        runCatching { client.connect() }
            .onFailure { Log.w(TAG, "Unable to attach sing-box log stream", it) }
    }

    private fun redactDiagnosticSecrets(message: String): String = message
        .replace(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "[redacted]")
        .replace(Regex("(?i)vless://\\S+"), "vless://[redacted]")

    private fun ensureLibboxSetup() {
        synchronized(LIBBOX_LOCK) {
            if (libboxReady) return
            val working = getExternalFilesDir(null) ?: filesDir
            val options = io.nekohasekai.libbox.SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = working.absolutePath
                tempPath = cacheDir.absolutePath
                fixAndroidStack = true
                appVersion = BuildConfig.VERSION_CODE.toString()
                appMarketingVersion = BuildConfig.VERSION_NAME
                logMaxLines = 1500
            }
            Libbox.setup(options)
            libboxReady = true
        }
    }

    private fun startForegroundCompat() {
        val notification = VpnNotifications.build(this, activeProfileName, "Подключение…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(VpnNotifications.ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(VpnNotifications.ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        runCatching { VpnNotifications.update(this, activeProfileName, status) }
            .onFailure { Log.w(TAG, "Unable to update VPN notification", it) }
    }

    // libbox PlatformInterface
    override fun localDNSTransport(): io.nekohasekai.libbox.LocalDNSTransport = AndroidLocalDnsTransport { selectUnderlay() }
    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun autoDetectInterfaceControl(fd: Int) {
        check(protect(fd)) { "Не удалось исключить сокет ядра из VPN-туннеля" }
        selectUnderlay()?.let { network ->
            ParcelFileDescriptor.fromFd(fd).use { network.bindSocket(it.fileDescriptor) }
        }
    }
    override fun useProcFS() = Build.VERSION.SDK_INT < 29

    override fun openTun(options: TunOptions): Int {
        check(VpnService.prepare(this) == null) { "Android VPN permission is missing" }
        val builder = Builder().setSession("NOVA VPN").setMtu(normalizeAndroidTunMtu(options.mtu))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val has4Address = addAddresses(builder, options.inet4Address)
        val has6Address = addAddresses(builder, options.inet6Address)

        if (options.autoRoute) {
            val dns = options.dnsServerAddress
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                while (dns.hasNext()) builder.addDnsServer(dns.next())
            }
            val has4: Boolean
            val has6: Boolean
            if (Build.VERSION.SDK_INT >= 33) {
                has4 = addRoutes(builder, options.inet4RouteAddress)
                has6 = addRoutes(builder, options.inet6RouteAddress)
                addExcludedRoutes(builder, options.inet4RouteExcludeAddress)
                addExcludedRoutes(builder, options.inet6RouteExcludeAddress)
            } else {
                has4 = addRoutes(builder, options.inet4RouteRange)
                has6 = addRoutes(builder, options.inet6RouteRange)
            }
            if (!has4 && has4Address) builder.addRoute("0.0.0.0", 0)
            if (!has6 && has6Address) builder.addRoute("::", 0)
        }

        addApplications(builder, options.includePackage, allow = true)
        addApplications(builder, options.excludePackage, allow = false)

        tunDescriptor?.close()
        tunDescriptor = builder.establish() ?: error("Android не создал TUN-интерфейс")
        return tunDescriptor!!.fd
    }

    private fun addAddresses(builder: Builder, iterator: RoutePrefixIterator): Boolean {
        var added = false
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.addAddress(prefix.address(), prefix.prefix())
            added = true
        }
        return added
    }

    private fun addRoutes(builder: Builder, iterator: RoutePrefixIterator): Boolean {
        var added = false
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.addRoute(prefix.address(), prefix.prefix())
            added = true
        }
        return added
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun addExcludedRoutes(builder: Builder, iterator: RoutePrefixIterator) {
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.excludeRoute(android.net.IpPrefix(InetAddress.getByName(prefix.address()), prefix.prefix()))
        }
    }

    private fun addApplications(builder: Builder, iterator: StringIterator, allow: Boolean) {
        var requested = false
        var added = false
        while (iterator.hasNext()) {
            requested = true
            val packageName = iterator.next()
            try {
                if (allow) builder.addAllowedApplication(packageName) else builder.addDisallowedApplication(packageName)
                added = true
            } catch (error: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Ignoring unavailable routed package: $packageName", error)
            }
        }
        // An empty Builder allow-list means ALL apps. If the selected apps were
        // uninstalled, refuse to capture every other application instead.
        check(!allow || !requested || added) {
            "Выбранные для VPN приложения недоступны. Обновите список приложений."
        }
    }

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String?, sourcePort: Int,
        destinationAddress: String?, destinationPort: Int
    ): ConnectionOwner = ConnectionOwner().apply {
        // libbox's platform wrapper assumes a non-null owner; returning null can
        // panic in its packet-routing goroutine when Android cannot resolve a UID.
        userId = -1
        userName = ""
        processPath = ""
        setAndroidPackageNames(StringArrayIterator(emptyList()))
        if (Build.VERSION.SDK_INT >= 29 && sourceAddress != null && destinationAddress != null
            && (ipProtocol == OsConstants.IPPROTO_TCP || ipProtocol == OsConstants.IPPROTO_UDP)) {
            // Failure remains a non-null unknown owner; successful lookups enable app rules.
            runCatching {
                val uid = connectivity.getConnectionOwnerUid(ipProtocol,
                    InetSocketAddress(sourceAddress, sourcePort),
                    InetSocketAddress(destinationAddress, destinationPort))
                if (uid >= 0) {
                    val packages = packageManager.getPackagesForUid(uid).orEmpty().toList()
                    userId = uid
                    userName = packages.firstOrNull().orEmpty()
                    setAndroidPackageNames(StringArrayIterator(packages))
                }
            }
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        interfacePublications[listener] = InterfacePublicationGate()
        connectivity.allNetworks.forEach { network ->
            connectivity.getNetworkCapabilities(network)?.takeIf {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }?.let { availableUnderlays[network] = it }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.getNetworkCapabilities(network)?.let { availableUnderlays[network] = it }
                publishDefaultInterface(listener, selectUnderlay())
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                availableUnderlays[network] = capabilities
                publishDefaultInterface(listener, selectUnderlay())
            }
            override fun onLost(network: Network) {
                availableUnderlays.remove(network)
                publishDefaultInterface(listener, selectUnderlay())
            }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                // Interface/DNS can become ready after onAvailable/onCapabilitiesChanged.
                if (availableUnderlays.containsKey(network)) {
                    publishDefaultInterface(listener, selectUnderlay())
                }
            }
        }
        monitors.put(listener, callback)?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity.registerNetworkCallback(request, callback)
        publishDefaultInterface(listener, selectUnderlay())
    }

    private fun selectUnderlay(): Network? = availableUnderlays.entries
        .filter { it.value.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            && it.value.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) }
        .maxByOrNull { (_, capabilities) ->
            val validated = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0
            validated + when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 3
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 2
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
                else -> 0
            }
        }?.key

    private fun publishDefaultInterface(listener: InterfaceUpdateListener, network: Network?) {
        val name = network?.let { connectivity.getLinkProperties(it)?.interfaceName }.orEmpty()
        val index = runCatching { JNetworkInterface.getByName(name)?.index ?: 0 }.getOrDefault(0)
        interfacePublications[listener]?.publish(network?.networkHandle, name, index) {
            val caps = network?.let { connectivity.getNetworkCapabilities(it) }
            VpnDiagnostics.record(when {
                network == null -> "underlay_none"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "underlay_wifi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "underlay_cellular"
                else -> "underlay_other"
            })
            if (name.isBlank() || index <= 0) VpnDiagnostics.record("underlay_interface_not_ready")
            runCatching { setUnderlyingNetworks(network?.let { arrayOf(it) }) }
            if (name.isNotBlank() && index > 0) listener.updateDefaultInterface(name, index, false, false)
            else listener.updateDefaultInterface("", 0, false, false)
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        monitors.remove(listener)?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        interfacePublications.remove(listener)
        if (monitors.isEmpty()) {
            availableUnderlays.clear()
            runCatching { setUnderlyingNetworks(null) }
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val physical = connectivity.allNetworks.mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return@mapNotNull null
            val links = connectivity.getLinkProperties(network) ?: return@mapNotNull null
            links.interfaceName?.let { it to (links to capabilities) }
        }.toMap()
        val interfaces = runCatching {
            Collections.list(JNetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback && physical.containsKey(it.name) }
                .mapNotNull { networkInterface ->
                    val (links, capabilities) = physical.getValue(networkInterface.name)
                    // libbox parses these as netip.Prefix values. Android's hostAddress
                    // omits the prefix and may append an IPv6 scope (e.g. %wlan0),
                    // which makes libbox panic while starting its interface monitor.
                    val addresses = networkInterface.interfaceAddresses.mapNotNull { interfaceAddress ->
                        val address = interfaceAddress.address.hostAddress?.substringBefore('%')
                            ?: return@mapNotNull null
                        val prefixLength = interfaceAddress.networkPrefixLength.toInt()
                        val maxPrefixLength = if (interfaceAddress.address.address.size == 4) 32 else 128
                        if (prefixLength !in 0..maxPrefixLength) null else "$address/$prefixLength"
                    }
                    BoxNetworkInterface().apply {
                        index = networkInterface.index
                        mtu = 1500
                        name = networkInterface.name
                        this.addresses = StringArrayIterator(addresses)
                        flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                        type = when {
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                            else -> Libbox.InterfaceTypeOther
                        }
                        dnsServer = StringArrayIterator(links.dnsServers.mapNotNull { it.hostAddress })
                        gateway = StringArrayIterator(links.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway?.hostAddress })
                        metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }
                }
        }.getOrDefault(emptyList())
        return BoxNetworkInterfaceIterator(interfaces)
    }

    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() = Unit
    override fun sendNotification(notification: BoxNotification) = Unit
    override fun cancelNotification(identifier: String, typeID: Int) = Unit
    override fun startNeighborMonitor(listener: NeighborUpdateListener) =
        throw UnsupportedOperationException("Android neighbor monitor is not enabled")
    override fun closeNeighborMonitor(listener: NeighborUpdateListener) = Unit
    override fun registerMyInterface(name: String) = Unit
    override fun usePlatformShell() = false
    override fun checkPlatformShell() = Unit
    override fun openShellSession(
        user: PlatformUser, command: String, environ: StringIterator, term: String, rows: Int, cols: Int
    ): ShellSession = throw UnsupportedOperationException("Android shell callbacks are not supported")
    override fun lookupUser(username: String): PlatformUser =
        throw UnsupportedOperationException("Android user lookup is not supported")
    override fun lookupSFTPServer(): String = ""
    override fun readSystemSSHHostKey(): String = ""
    override fun tailscaleHostname(): String = ""
    override fun usePlatformBridge() = false
    override fun createBridge(options: BridgeOptions): BridgeSession =
        throw UnsupportedOperationException("Android bridge callbacks are not supported")

    // libbox CommandServerHandler
    override fun serviceStop() = stopTunnel(latestStartId)
    override fun serviceReload() = Unit
    override fun getSystemProxyStatus() = SystemProxyStatus().apply { available = false; enabled = false }
    override fun setSystemProxyEnabled(enabled: Boolean) = Unit
    override fun triggerNativeCrash() = throw UnsupportedOperationException("Native crash trigger is disabled")
    override fun writeDebugMessage(message: String?) {
        Log.d(TAG, message.orEmpty())
    }
    override fun connectSSHAgent() = -1

    private class StringArrayIterator(private val values: List<String>) : StringIterator {
        private var index = 0
        override fun len() = values.size
        override fun hasNext() = index < values.size
        override fun next() = values[index++]
    }

    private class BoxNetworkInterfaceIterator(private val values: List<BoxNetworkInterface>) : NetworkInterfaceIterator {
        private var index = 0
        override fun hasNext() = index < values.size
        override fun next() = values[index++]
    }

    companion object {
        const val ACTION_START = "com.nova.vpn.android.START"
        const val ACTION_STOP = "com.nova.vpn.android.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val ACTION_REFRESH_NOTIFICATION = "com.nova.vpn.android.REFRESH_NOTIFICATION"
        private const val TAG = "NovaVpnService"
        private val LIBBOX_LOCK = Any()
        @Volatile private var libboxReady = false
    }
}

