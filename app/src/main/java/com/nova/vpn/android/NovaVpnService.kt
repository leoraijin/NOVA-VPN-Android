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
import com.nova.vpn.android.data.AppRoutingStore
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
    private val interfacePublications = ConcurrentHashMap<InterfaceUpdateListener, InterfacePublicationGate>()
    // A previous service's native close must complete before a recreated service starts.
    private val serviceQueue = NATIVE_QUEUE
    private val serviceIdentity = SERVICE_SEQUENCE.incrementAndGet()
    private val commands = ConnectionCoordinator()
    private data class Configuration(val config: String, val name: String, val engine: VpnEngine, val startId: Int,
                                     val identity: AppliedConfiguration, val rollbackFor: String? = null)
    @Volatile private var desiredConfiguration: Configuration? = null
    private var appliedConfiguration: Configuration? = null
    private val recoveryQueue = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "nova-vpn-recovery").apply { isDaemon = true } }
    private val recoveryLock = Any()
    private var recoveryTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var physicalMonitor: PhysicalNetworkMonitor? = null
    private var automaticServers: AutomaticServerController? = null
    @Volatile private var foregroundStarted = false
    @Volatile private var physicalNetwork: PhysicalNetworkSnapshot? = null
    private var tunPolicyKey: String? = null
    @Volatile private var latestStartId = 0
    @Volatile private var activeProfileName = "Выбранная конфигурация"
    private var commandServer: CommandServer? = null
    private var debugLogClient: CommandClient? = null
    @Volatile private var tunDescriptor: ParcelFileDescriptor? = null
    @Volatile private var realityHandle = 0L
    @Volatile private var activeEngine: VpnEngine? = null
    private val realityHealthQueue = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "nova-reality-health").apply { isDaemon = true }
    }
    private var realityHealthTask: java.util.concurrent.ScheduledFuture<*>? = null
    private val probeQueue = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "nova-vpn-check").apply { isDaemon = true } }
    @Volatile private var trafficProbe: AndroidTrafficProbe? = null
    private val notificationRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_NOTIFICATION && VpnRuntime.state.value.connected) {
                updateNotification(VpnRuntime.state.value.recoveryText() ?: "Туннель подключён")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (com.nova.vpn.android.data.RestoreRecoveryState.problem.value != null) return
        VpnUserRequests.gate.retainService(serviceIdentity)
        synchronized(RUNTIME_OWNER_LOCK) { RUNTIME_OWNER.set(serviceIdentity) }
        VpnRuntime.attach(applicationContext)
        VpnDiagnostics.attachAndroidClock()
        VpnNotifications.prepare(this)
        val monitor = PhysicalNetworkMonitor(connectivity, ::onPhysicalNetwork)
        physicalMonitor = monitor
        runCatching { monitor.start() }.onFailure {
            monitor.close(); physicalMonitor = null
            VpnDiagnostics.record("network_monitor_failed")
        }
        val filter = IntentFilter(ACTION_REFRESH_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(notificationRefreshReceiver, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(notificationRefreshReceiver, filter)
        }
        automaticServers = AutomaticServerController(this, serviceIdentity) {
            val view = commands.view()
            val desired = desiredConfiguration
            val profileId = desired?.identity?.profileId
            val network = physicalNetwork?.signature
            if (!view.wanted || !view.ready || view.phase == ConnectionCoordinator.Phase.CONNECTING ||
                desired == null || profileId == null || network == null) null
            else AutoServerSession(desired.identity.requestId, profileId, network)
        }.also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (com.nova.vpn.android.data.RestoreRecoveryState.problem.value != null) {
            if (intent?.action == ACTION_STOP && commands.view().wanted) stopTunnel(startId)
            else finishRejectedStart(startId)
            return START_NOT_STICKY
        }
        val token = intent?.getStringExtra(VpnUserRequests.EXTRA_REQUEST)
        if (token != null) {
            var result = START_NOT_STICKY
            val accepted = VpnUserRequests.gate.accept(token) {
                if (intent.getBooleanExtra(VpnUserRequests.EXTRA_AUTOMATIC, false) &&
                    !AutomaticProbePause.belongsTo(token, serviceIdentity)) {
                    serviceQueue.execute { AutomaticProbePause.settled(token) }
                    if (!commands.view().wanted) finishRejectedStart(startId)
                } else result = handleStartCommand(intent, startId)
            }
            if (!accepted) {
                // A superseded intent may arrive while an older native start is
                // still unwinding. Retire its ownership only behind that work.
                serviceQueue.execute { AutomaticProbePause.settled(token) }
                if (!commands.view().wanted) finishRejectedStart(startId)
            }
            return result
        }
        // Platform/legacy callers have no preparation token; arrival is their intent.
        VpnUserRequests.gate.begin()
        return handleStartCommand(intent, startId)
    }

    private fun handleStartCommand(intent: Intent?, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_STOP) {
            runCatching { AppRoutingStore(this).cancelPendingExcept(null) }
            if (!commands.view().wanted && !foregroundStarted) finishRejectedStart(startId) else stopTunnel(startId)
            return START_NOT_STICKY
        }
        fun identifier(key: String) = intent?.getStringExtra(key)?.takeIf { it.length in 1..128 }
        val identity = AppliedConfiguration(identifier(VpnUserRequests.EXTRA_REQUEST) ?: java.util.UUID.randomUUID().toString(),
            identifier(EXTRA_PROFILE_ID), identifier(EXTRA_ROUTING_PRESET_ID))
        VpnApplyRequests.tracker.accepted(identity)
        // The previous START may have been superseded before delivery, so there
        // need not be a desiredConfiguration corresponding to its staged rules.
        AppRoutingStore(this).cancelPendingExcept(identity.requestId)
        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config.isNullOrBlank()) {
            VpnApplyRequests.tracker.failed(identity.requestId)
            serviceQueue.execute { AutomaticProbePause.settled(identity.requestId) }
            AppRoutingStore(this).cancelPending(identity.requestId)
            if (!commands.view().wanted) {
                VpnRuntime.setFailure("Не передана конфигурация VPN")
                WearStatusSync.publish(this, "error")
                finishRejectedStart(startId)
            }
            return START_NOT_STICKY
        }

        val requestedProfileName = intent?.getStringExtra(EXTRA_PROFILE_NAME)
            ?.trim()?.replace(Regex("\\s+"), " ")?.take(48)?.takeIf(String::isNotBlank)
            ?: "Выбранная конфигурация"

        val ticket = commands.connect()
        val supersededAutomatic = AutomaticProbePause.requestsFor(serviceIdentity) - identity.requestId
        // Old generations can no longer start. The queue barrier also waits for
        // any native call already in progress to unwind before releasing them.
        serviceQueue.execute { supersededAutomatic.forEach(AutomaticProbePause::settled) }
        try {
            commands.accept(ticket, requireNetwork = false) { startForegroundCompat() }
        } catch (error: Throwable) {
            commands.stop()
            VpnApplyRequests.tracker.failed(identity.requestId)
            serviceQueue.execute { AutomaticProbePause.settled(identity.requestId) }
            AppRoutingStore(this).cancelPending(identity.requestId)
            Log.e(TAG, "Unable to enter foreground", error)
            VpnRuntime.setFailure(error.message ?: "Не удалось запустить VPN-службу Android")
            WearStatusSync.publish(this, "error")
            finishRejectedStart(startId)
            return START_NOT_STICKY
        }
        if (physicalMonitor == null) {
            commands.stop()
            VpnApplyRequests.tracker.failed(identity.requestId)
            serviceQueue.execute { AutomaticProbePause.settled(identity.requestId) }
            AppRoutingStore(this).cancelPending(identity.requestId)
            VpnRuntime.setFailure("Не удалось отслеживать физическую сеть. Повторите подключение.")
            finishRejectedStart(startId)
            return START_NOT_STICKY
        }
        val request = Configuration(config, requestedProfileName, AppPreferencesStore(this).vpnEngine(), startId, identity)
        desiredConfiguration = request
        cancelRecovery()
        trafficProbe?.cancel()
        if (commands.current(ticket)) enqueueConfiguration(request, ticket, recovery = false)
        else publishRecovery(ticket, waiting = true)
        return START_NOT_STICKY
    }

    private fun enqueueConfiguration(request: Configuration, ticket: ConnectionCoordinator.Ticket, recovery: Boolean) {
        serviceQueue.execute {
            if (!commands.current(ticket)) return@execute
            val config = request.config
            val startId = request.startId
            val previous = appliedConfiguration
            // Publish startup only after the serial queue accepted this request.
            // A second START may arrive before native start returns its handle;
            // rejecting that queued request must not erase the live tunnel state.
            if (!commands.accept(ticket) {
                if (!recovery) VpnDiagnostics.reset()
                VpnDiagnostics.setTransportFromConfig(config)
                VpnDiagnostics.setEngine(request.engine == VpnEngine.REALITY_CORE)
                activeProfileName = request.name
                if (recovery) publishRecovery(ticket, waiting = false) else VpnRuntime.setConnecting()
                WearStatusSync.publish(this, "connecting")
            }) return@execute
            var stage = "vpn-permission"
            try {
                Log.i(TAG, "VPN engine startup stage=$stage")
                if (VpnService.prepare(this) != null) {
                    VpnDiagnostics.record("vpn_permission_required")
                    error("Не выдано разрешение Android на VPN")
                }
                VpnDiagnostics.record("vpn_permission_granted")
                val engine = request.engine
                VpnDiagnostics.setEngine(engine == VpnEngine.REALITY_CORE)
                check(activeEngine == null || activeEngine == engine) { "Перед сменой ядра отключите VPN." }
                if (engine == VpnEngine.REALITY_CORE) {
                    stage = "reality-start"
                    startReality(request, ticket)
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
                if (!commands.current(ticket)) return@execute
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
                if (!commands.accept(ticket) {
                    commitRouting(request)
                    commands.succeeded(ticket)
                    appliedConfiguration = request
                    VpnApplyRequests.tracker.applied(request.identity, request.rollbackFor)
                    VpnRuntime.setConnected(request.identity)
                    VpnDiagnostics.record("tunnel_active")
                    WearStatusSync.publish(this, "connected")
                    updateNotification("VPN подключён")
                }) { closeEngine(keepTun = commands.view().wanted); return@execute }
                AutomaticProbePause.settled(request.rollbackFor ?: request.identity.requestId)
                if (probe != null && commands.current(ticket)) {
                    commands.accept(ticket) { VpnRuntime.setTrafficChecking() }
                    probeQueue.execute {
                        val success = probe.check()
                        commands.accept(ticket) { if (trafficProbe === probe) {
                            VpnRuntime.setTrafficResult(success)
                            updateNotification(if (success) "HTTPS через VPN подтверждён" else "Туннель активен · проверка не подтверждена")
                        } }
                    }
                }
            } catch (error: Throwable) {
                if (!commands.current(ticket)) { closeEngine(keepTun = commands.view().wanted); return@execute }
                VpnDiagnostics.record("startup_failed")
                trafficProbe?.cancel(); trafficProbe = null
                Log.e(TAG, "VPN start failed at stage=$stage", error)
                if (previous != null && previous.config != request.config && previous.engine == request.engine) {
                    closeEngine(keepTun = true)
                    var rollback: ConnectionCoordinator.Ticket? = null
                    commands.accept(ticket) {
                        desiredConfiguration = previous.copy(startId = startId, rollbackFor = request.rollbackFor ?: request.identity.requestId)
                        rollback = commands.connect()
                        VpnDiagnostics.record("configuration_rollback")
                    }
                    rollback?.let { scheduleRecovery(it, 0) }
                } else if (error is InvalidConfiguration || stage in setOf("vpn-permission", "config-validation")) {
                    val failedTicket = commands.terminal(ticket) ?: return@execute
                    closeEngine()
                    commands.acceptTerminal(failedTicket) {
                        AppRoutingStore(this).cancelPending(request.rollbackFor ?: request.identity.requestId)
                        VpnApplyRequests.tracker.failed(request.rollbackFor ?: request.identity.requestId)
                        AutomaticProbePause.settled(request.rollbackFor ?: request.identity.requestId)
                        desiredConfiguration = null
                        VpnRuntime.setFailure("Конфигурация или разрешение VPN не прошли проверку. Откройте диагностику.")
                        WearStatusSync.publish(this, "error")
                        removeForeground()
                        stopSelfResult(startId)
                    }
                } else {
                    closeEngine(keepTun = true)
                    retryAfterFailure(ticket)
                }
            }
        }
    }

    override fun onRevoke() {
        VpnUserRequests.gate.begin()
        stopTunnel(latestStartId)
        super.onRevoke()
    }

    override fun onDestroy() {
        automaticServers?.close(); automaticServers = null
        commands.stop()
        val interrupted = desiredConfiguration
        desiredConfiguration = null
        ownedRuntime {
            runCatching { interrupted?.let { AppRoutingStore(this).cancelPending(it.rollbackFor ?: it.identity.requestId) } }
            VpnApplyRequests.tracker.stopped()
        }
        cancelRecovery()
        physicalMonitor?.close(); physicalMonitor = null
        recoveryQueue.shutdownNow()
        runCatching { unregisterReceiver(notificationRefreshReceiver) }
        trafficProbe?.cancel(); trafficProbe = null
        probeQueue.shutdownNow()
        realityHealthQueue.shutdownNow()
        interfacePublications.clear()
        fun finishDestroy() {
            try {
                closeEngine()
                AutomaticProbePause.serviceClosed(serviceIdentity)
                ownedRuntime {
                    if (VpnRuntime.state.value.error == null) {
                        VpnRuntime.setDisconnected()
                        WearStatusSync.publish(this, "disconnected")
                    }
                }
            } finally {
                VpnUserRequests.gate.releaseService(serviceIdentity)
            }
        }
        runCatching { serviceQueue.execute(::finishDestroy) }.onFailure { finishDestroy() }
        super.onDestroy()
    }

    private fun stopTunnel(stopAfterStartId: Int = latestStartId) {
        val stopped = commands.stop()
        VpnApplyRequests.tracker.stopped()
        runCatching { desiredConfiguration?.let { AppRoutingStore(this).cancelPending(it.rollbackFor ?: it.identity.requestId) } }
        desiredConfiguration = null
        cancelRecovery()
        trafficProbe?.cancel(); trafficProbe = null
        serviceQueue.execute {
            closeEngine()
            AutomaticProbePause.serviceClosed(serviceIdentity)
            appliedConfiguration = null
            val published = commands.acceptStopped(stopped) {
                ownedRuntime {
                    VpnRuntime.setDisconnected()
                    WearStatusSync.publish(this, "disconnected")
                    removeForeground()
                }
            }
            if (!published && RUNTIME_OWNER.get() == serviceIdentity) {
                val view = commands.view()
                publishRecovery(view.ticket, waiting = !view.ready)
            }
            // A later startService() increments startId. Do not let this stale stop
            // request tear down a tunnel started after the stop was queued.
            if (stopAfterStartId > 0) stopSelfResult(stopAfterStartId)
            else stopSelf()
        }
    }

    private fun closeEngine(keepTun: Boolean = false) {
        trafficProbe?.cancel()
        trafficProbe = null
        realityHealthTask?.cancel(false)
        realityHealthTask = null
        if (realityHandle != 0L) {
            val handle = realityHandle
            realityHandle = 0L
            runCatching { RealityCoreBridge.stop(handle) }
        }
        activeEngine = null
        runCatching { debugLogClient?.disconnect() }
        debugLogClient = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        if (!keepTun) {
            runCatching { tunDescriptor?.close() }
            tunDescriptor = null
            tunPolicyKey = null
        }
    }

    private fun ownedRuntime(action: () -> Unit) = synchronized(RUNTIME_OWNER_LOCK) {
        if (RUNTIME_OWNER.get() == serviceIdentity) action()
    }

    private class InvalidConfiguration : IllegalArgumentException("Неподдерживаемая конфигурация VPN")

    private fun startReality(request: Configuration, ticket: ConnectionCoordinator.Ticket) {
        val config = request.config
        runCatching { RealityCoreBridge.requireAvailable() }.getOrElse { throw InvalidConfiguration() }
        // Conversion and native validation happen BEFORE closing an existing tunnel.
        val probe = runCatching { AndroidTrafficProbe.prepare(config) }.getOrElse { throw InvalidConfiguration() }
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
        val prepared = runCatching { RealityConfigFactory.prepare(probe?.config ?: config, packageName, packageUid) }
            .getOrElse { throw InvalidConfiguration() }
        val runtimeConfig = prepared.config
        if (!runCatching { RealityCoreBridge.validate(runtimeConfig, filesDir.absolutePath) }.getOrDefault(false)) throw InvalidConfiguration()
        if (!commands.current(ticket)) return
        val policyKey = TunReusePolicy.reality(prepared.includedPackages, prepared.excludedPackages) { name ->
            runCatching { packageManager.getApplicationInfo(name, 0).uid }.getOrElse { throw InvalidConfiguration() }
        }
        val retainedTun = tunDescriptor.takeIf { tunPolicyKey == policyKey }
        trafficProbe?.cancel()
        closeEngine(keepTun = true)
        if (!commands.current(ticket)) return
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
        val descriptor = retainedTun ?: (builder.establish() ?: error("Android не создал TUN-интерфейс")).also {
            val previous = tunDescriptor
            tunDescriptor = it
            tunPolicyKey = policyKey
            previous?.close()
        }
        // Native consumes a duplicate. The service retains the Android TUN during recovery.
        val fd = ParcelFileDescriptor.dup(descriptor.fileDescriptor).use { it.detachFd() }
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
        if (!commands.accept(ticket) {
            commitRouting(request)
            commands.succeeded(ticket)
            appliedConfiguration = request
            VpnApplyRequests.tracker.applied(request.identity, request.rollbackFor)
            VpnRuntime.setConnected(request.identity)
            VpnDiagnostics.record("tunnel_active")
            WearStatusSync.publish(this, "connected")
            updateNotification("VPN подключён · Reality Core")
        }) { closeEngine(keepTun = commands.view().wanted); return }
        AutomaticProbePause.settled(request.rollbackFor ?: request.identity.requestId)
        if (probe != null) {
            commands.accept(ticket) { VpnRuntime.setTrafficChecking() }
            probeQueue.execute {
                val success = probe.check()
                commands.accept(ticket) { if (trafficProbe === probe) {
                    VpnRuntime.setTrafficResult(success)
                    updateNotification(if (success) "HTTPS через VPN подтверждён · Reality Core" else "Reality Core · проверка не подтверждена")
                } }
            }
        }
        // Only reads the native terminal-error channel; no periodic network probes.
        realityHealthTask = realityHealthQueue.scheduleWithFixedDelay({
            runCatching { serviceQueue.execute {
                if (commands.current(ticket) && realityHandle != 0L && !RealityCoreBridge.isAlive(realityHandle)) {
                    trafficProbe?.cancel(); trafficProbe = null
                    closeEngine(keepTun = true)
                    VpnDiagnostics.record("reality_terminal_failure")
                    retryAfterFailure(ticket)
                }
            } }
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun onPhysicalNetwork(snapshot: PhysicalNetworkSnapshot) {
        physicalNetwork = snapshot
        val ticket = commands.networkChanged(snapshot.signature, snapshot.network != null) ?: return
        VpnDiagnostics.networkChanged()
        commands.accept(ticket, requireNetwork = false) { cancelRecovery() }
        // Invalidate immediately; no native calls or synchronous Binder work on CM's callback.
        runCatching { serviceQueue.execute {
            if (physicalNetwork?.signature == snapshot.signature) {
                runCatching { setUnderlyingNetworks(snapshot.network?.let { arrayOf(it) }) }
                interfacePublications.keys.forEach { publishDefaultInterface(it, snapshot.network) }
            }
        } }
        if (commands.view().wanted) runCatching { recoveryQueue.execute {
            commands.accept(ticket, requireNetwork = false) {
                trafficProbe?.cancel()
                VpnDiagnostics.record(if (snapshot.network == null) "recovery_wait_network" else "recovery_network_changed")
                publishRecovery(ticket, waiting = snapshot.network == null)
                if (snapshot.network != null) scheduleRecovery(ticket, 350)
            }
        } }
    }

    private fun cancelRecovery() = synchronized(recoveryLock) {
        recoveryTask?.cancel(false)
        recoveryTask = null
    }

    private fun scheduleRecovery(ticket: ConnectionCoordinator.Ticket, delayMillis: Long) = commands.accept(ticket) {
        // Validate before touching the current timer. Always take the coordinator
        // before recoveryLock, including calls from accepted network callbacks.
        synchronized(recoveryLock) {
            if (desiredConfiguration == null || recoveryQueue.isShutdown) return@synchronized
            recoveryTask?.cancel(false)
            recoveryTask = recoveryQueue.schedule({
                val request = desiredConfiguration
                if (request != null && commands.current(ticket)) enqueueConfiguration(request, ticket, recovery = true)
            }, delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    private fun publishRecovery(ticket: ConnectionCoordinator.Ticket, waiting: Boolean) {
        commands.accept(ticket, requireNetwork = false) {
            VpnRuntime.setRecovery(waiting, commands.view().failures, tunDescriptor != null)
            updateNotification(VpnRuntime.state.value.recoveryText() ?: "Восстановление соединения")
        }
    }

    private fun retryAfterFailure(ticket: ConnectionCoordinator.Ticket) {
        val retry = commands.failed(ticket) ?: return
        VpnDiagnostics.record("recovery_retry_${retry.number}")
        publishRecovery(retry.ticket, waiting = false)
        scheduleRecovery(retry.ticket, retry.delayMillis)
    }

    private fun registerRealityNetworkMonitor() {
        val network = selectUnderlay()
        runCatching { setUnderlyingNetworks(network?.let { arrayOf(it) }) }
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
            override fun writeDNSQuery(query: io.nekohasekai.libbox.DnsQuery?) = Unit
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

    private fun commitRouting(request: Configuration) {
        val store = AppRoutingStore(this)
        if (request.rollbackFor != null) store.cancelPending(request.rollbackFor)
        else request.identity.routingPresetId?.let { store.commitPending(request.identity.requestId, it) }
    }

    private fun startForegroundCompat() {
        val notification = VpnNotifications.build(this, activeProfileName, "Подключение…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(VpnNotifications.ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(VpnNotifications.ID, notification)
        }
        foregroundStarted = true
    }

    /** An ignored foreground START still owes Android its foreground acknowledgement. */
    private fun finishRejectedStart(startId: Int) {
        if (commands.view().wanted) return
        if (!foregroundStarted) {
            VpnNotifications.prepare(this)
            val notification = VpnNotifications.build(this, "NOVA VPN", "Запрос подключения завершён")
            if (Build.VERSION.SDK_INT >= 34) startForeground(VpnNotifications.ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            else startForeground(VpnNotifications.ID, notification)
            foregroundStarted = true
        }
        removeForeground()
        stopSelfResult(startId)
    }

    private fun removeForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
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
        val mtu = normalizeAndroidTunMtu(options.mtu)
        val policy = mutableListOf("mtu:$mtu")
        val builder = Builder().setSession("NOVA VPN").setMtu(mtu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val has4Address = addAddresses(builder, options.inet4Address, policy)
        val has6Address = addAddresses(builder, options.inet6Address, policy)

        if (options.autoRoute) {
            val dns = options.dnsServerAddress
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                while (dns.hasNext()) {
                    val address = dns.next()
                    builder.addDnsServer(address)
                    policy += "dns:${policy.count { it.startsWith("dns:") }}:$address"
                }
            }
            val has4: Boolean
            val has6: Boolean
            if (Build.VERSION.SDK_INT >= 33) {
                has4 = addRoutes(builder, options.inet4RouteAddress, policy)
                has6 = addRoutes(builder, options.inet6RouteAddress, policy)
                addExcludedRoutes(builder, options.inet4RouteExcludeAddress, policy)
                addExcludedRoutes(builder, options.inet6RouteExcludeAddress, policy)
            } else {
                has4 = addRoutes(builder, options.inet4RouteRange, policy)
                has6 = addRoutes(builder, options.inet6RouteRange, policy)
            }
            if (!has4 && has4Address) { builder.addRoute("0.0.0.0", 0); policy += "route:0.0.0.0/0" }
            if (!has6 && has6Address) { builder.addRoute("::", 0); policy += "route:::/0" }
        }

        addApplications(builder, options.includePackage, allow = true, policy = policy)
        addApplications(builder, options.excludePackage, allow = false, policy = policy)

        val key = TunReusePolicy.singBox(policy)
        if (tunDescriptor != null && key == tunPolicyKey) return tunDescriptor!!.fd
        val previous = tunDescriptor
        tunDescriptor = builder.establish() ?: error("Android не создал TUN-интерфейс")
        tunPolicyKey = key
        previous?.close()
        return tunDescriptor!!.fd
    }

    private fun addAddresses(builder: Builder, iterator: RoutePrefixIterator, policy: MutableList<String>): Boolean {
        var added = false
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.addAddress(prefix.address(), prefix.prefix())
            policy += "address:${policy.count { it.startsWith("address:") }}:${prefix.address()}/${prefix.prefix()}"
            added = true
        }
        return added
    }

    private fun addRoutes(builder: Builder, iterator: RoutePrefixIterator, policy: MutableList<String>): Boolean {
        var added = false
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.addRoute(prefix.address(), prefix.prefix())
            policy += "route:${prefix.address()}/${prefix.prefix()}"
            added = true
        }
        return added
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun addExcludedRoutes(builder: Builder, iterator: RoutePrefixIterator, policy: MutableList<String>) {
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.excludeRoute(android.net.IpPrefix(InetAddress.getByName(prefix.address()), prefix.prefix()))
            policy += "exclude_route:${prefix.address()}/${prefix.prefix()}"
        }
    }

    private fun addApplications(builder: Builder, iterator: StringIterator, allow: Boolean, policy: MutableList<String>) {
        var requested = false
        var added = false
        while (iterator.hasNext()) {
            requested = true
            val packageName = iterator.next()
            try {
                if (allow) builder.addAllowedApplication(packageName) else builder.addDisallowedApplication(packageName)
                policy += "${if (allow) "include" else "exclude"}:$packageName:${packageManager.getApplicationInfo(packageName, 0).uid}"
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
        publishDefaultInterface(listener, selectUnderlay())
    }

    private fun selectUnderlay(): Network? = physicalNetwork?.network
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
        interfacePublications.remove(listener)
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
        private val SERVICE_SEQUENCE = java.util.concurrent.atomic.AtomicLong()
        private val RUNTIME_OWNER = java.util.concurrent.atomic.AtomicLong()
        private val RUNTIME_OWNER_LOCK = Any()
        private val NATIVE_QUEUE = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "nova-vpn-service").apply { isDaemon = true }
        }
        const val ACTION_START = "com.nova.vpn.android.START"
        const val ACTION_STOP = "com.nova.vpn.android.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_ROUTING_PRESET_ID = "routing_preset_id"
        const val ACTION_REFRESH_NOTIFICATION = "com.nova.vpn.android.REFRESH_NOTIFICATION"
        private const val TAG = "NovaVpnService"
        private val LIBBOX_LOCK = Any()
        @Volatile private var libboxReady = false
    }
}

