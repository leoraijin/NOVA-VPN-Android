package com.nova.vpn.android

import android.app.Notification
import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
    private val serviceQueue = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nova-vpn-service").apply { isDaemon = true }
    }
    @Volatile private var latestStartId = 0
    private var commandServer: CommandServer? = null
    private var debugLogClient: CommandClient? = null
    private var tunDescriptor: ParcelFileDescriptor? = null
    private val probeQueue = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "nova-vpn-check").apply { isDaemon = true } }
    @Volatile private var trafficProbe: AndroidTrafficProbe? = null

    override fun onCreate() {
        super.onCreate()
        VpnRuntime.attach(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_STOP) {
            stopTunnel(startId)
            return START_NOT_STICKY
        }

        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config.isNullOrBlank()) {
            VpnRuntime.setFailure("Не передана конфигурация VPN")
            WearStatusSync.publish(this, "error")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        VpnRuntime.setConnecting()
        WearStatusSync.publish(this, "connecting")
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
            var stage = "vpn-permission"
            try {
                Log.i(TAG, "VPN engine startup stage=$stage")
                if (VpnService.prepare(this) != null) error("Не выдано разрешение Android на VPN")
                stage = "libbox-setup"
                Log.i(TAG, "VPN engine startup stage=$stage")
                ensureLibboxSetup()
                stage = "config-validation"
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
                startDebugLogStream()
                VpnRuntime.setConnected()
                WearStatusSync.publish(this, "connected")
                updateNotification("Туннель включён")
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
                trafficProbe?.cancel(); trafficProbe = null
                Log.e(TAG, "VPN start failed at stage=$stage", error)
                runCatching { commandServer?.close() }
                commandServer = null
                runCatching { tunDescriptor?.close() }
                tunDescriptor = null
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
        trafficProbe?.cancel(); trafficProbe = null
        probeQueue.shutdownNow()
        monitors.values.forEach { runCatching { connectivity.unregisterNetworkCallback(it) } }
        monitors.clear()
        availableUnderlays.clear()
        runCatching {
            serviceQueue.execute {
                closeEngine()
                VpnRuntime.setDisconnected()
                WearStatusSync.publish(this, "disconnected")
            }
            serviceQueue.shutdown()
        }.onFailure {
            closeEngine()
            VpnRuntime.setDisconnected()
            WearStatusSync.publish(this, "disconnected")
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
        runCatching { debugLogClient?.disconnect() }
        debugLogClient = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
    }

    /** Forward sing-box core logs to logcat only in local debug builds for diagnostics. */
    private fun startDebugLogStream() {
        if (!BuildConfig.DEBUG || debugLogClient != null) return
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandLog)
            addCommand(Libbox.CommandConnections)
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
                    VpnTransportHealth.warningFor(rawMessage)?.let { warning ->
                        VpnRuntime.setTransportWarning(warning)
                        // Keep the per-request diagnostic; do not mark the whole VPN or watch as failed.
                    }
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
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "NOVA VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = buildNotification("Подключение…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, NovaVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("NOVA VPN")
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Отключить",
                stopIntent
            ).build())
            .build()
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
        while (iterator.hasNext()) {
            val packageName = iterator.next()
            try {
                if (allow) builder.addAllowedApplication(packageName) else builder.addDisallowedApplication(packageName)
            } catch (error: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Ignoring unavailable routed package: $packageName", error)
            }
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
            && ipProtocol in setOf(OsConstants.IPPROTO_TCP, OsConstants.IPPROTO_UDP)) {
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
        runCatching { setUnderlyingNetworks(network?.let { arrayOf(it) }) }
        if (name.isNotBlank() && index > 0) listener.updateDefaultInterface(name, index, false, false)
        else listener.updateDefaultInterface("", 0, false, false)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        monitors.remove(listener)?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
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
        private const val TAG = "NovaVpnService"
        private const val CHANNEL_ID = "nova-vpn-status"
        private const val NOTIFICATION_ID = 701
        private val LIBBOX_LOCK = Any()
        @Volatile private var libboxReady = false
    }
}
