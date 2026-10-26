package com.nova.vpn.android
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.*
import com.nova.vpn.android.data.VpnEngine
import io.nekohasekai.libbox.*
import java.net.NetworkInterface as JNetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
/** Bound only while the user requested a batch; native state lives in a separate process. */
class ServerCheckService : Service() {
    private val queue = Executors.newSingleThreadExecutor()
    private val closing = AtomicBoolean(false)
    @Volatile private var probe: AndroidTrafficProbe? = null
    private var server: CommandServer? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when(message.what) {
            CHECK -> {
                val config = message.data.getString("config") ?: return@Handler true
                val reply = message.replyTo ?: return@Handler true
                val requestId = message.arg1
                val diagnostic = message.data.getBoolean("diagnostic")
                val selectedEngine = VpnEngine.fromId(message.data.getString("engine"))
                if (!closing.get()) queue.execute {
                    if (closing.get()) return@execute
                    var millis = -1
                    var assessment = ProbeAssessment(null, 0, 0)
                    var realityHandle = 0L
                    var unsupported = false
                    VpnDiagnostics.attachAndroidClock()
                    VpnDiagnostics.setEngine(selectedEngine == VpnEngine.REALITY_CORE)
                    VpnDiagnostics.setTransportFromConfig(config)
                    VpnDiagnostics.reset()
                    VpnDiagnostics.record("service_start")
                    fun progress() {
                        if (diagnostic && !closing.get()) runCatching { reply.send(Message.obtain(null, PROGRESS, requestId, 0).apply {
                            data = Bundle().apply { putString("diagnostic_events", VpnDiagnostics.exportEvents()) }
                        }) }
                    }
                    progress()
                    try {
                        val platform = CheckPlatform(this)
                        if (diagnostic) {
                            VpnDiagnostics.record("dns_system_lookup_started")
                            progress()
                            val dns = DiagnosticDnsProbe.check(DiagnosticDnsProbe.host(config)) { host ->
                                platform.diagnosticNetwork()?.getAllByName(host)?.isNotEmpty() == true
                            }
                            VpnDiagnostics.record(dns.code)
                            progress()
                        }
                        if (closing.get()) return@execute
                        val check = AndroidTrafficProbe.prepare(config) ?: error("Unsupported check")
                        if (diagnostic) check.onProgress = ::progress
                        probe = check
                        if (closing.get()) { check.cancel(); return@execute }
                        VpnDiagnostics.record("config_validation")
                        if (selectedEngine == VpnEngine.REALITY_CORE) {
                            try { RealityCoreBridge.requireAvailable() }
                            catch (_: IllegalStateException) { throw UnsupportedProbeEngine() }
                            val dir = probeDirectory()
                            if (!RealityCoreBridge.validate(check.config, dir.absolutePath)) throw UnsupportedProbeEngine()
                            VpnDiagnostics.record("engine_reality_core")
                            realityHandle = RealityCoreBridge.start(check.config, dir.absolutePath, -1,
                                object : RealityCoreBridge.Callbacks {
                                    override fun protectSocket(fd: Int): Boolean = runCatching {
                                        platform.autoDetectInterfaceControl(fd); true
                                    }.getOrElse { VpnDiagnostics.record("reality_protect_failed"); false }
                                    override fun coreWarning(message: String) { VpnDiagnostics.coreMessage(message) }
                                })
                            check(realityHandle != 0L) { "Check core start failed" }
                        } else {
                            val engine = server ?: createServer(platform).also { server = it }
                            try { Libbox.checkConfig(check.config) }
                            catch (_: Exception) { throw UnsupportedProbeEngine() }
                            VpnDiagnostics.record("engine_sing_box")
                            engine.startOrReloadService(check.config, OverrideOptions())
                        }
                        VpnDiagnostics.record("engine_started")
                        progress()
                        val samples = mutableListOf<Int?>()
                        repeat(3) {
                          for (target in ProbeTarget.entries) {
                            if (closing.get()) error("Cancelled")
                            samples += if (check.check(4_000, target)) check.responseMillis else null
                          }
                        }
                        assessment = ProbeAssessment.from(samples)
                        millis = assessment.millis ?: -1
                    } catch (_: UnsupportedProbeEngine) {
                        unsupported = true
                        VpnDiagnostics.record("engine_config_unsupported")
                    } catch (_: LinkageError) {
                        unsupported = true
                        VpnDiagnostics.record("engine_unavailable")
                    } catch (error: Exception) {
                        VpnDiagnostics.coreMessage(error.message.orEmpty())
                    } finally {
                        probe?.cancel(); probe = null
                        if (realityHandle != 0L) runCatching { RealityCoreBridge.stop(realityHandle) }
                        runCatching { server?.closeService() }
                    }
                    if (!closing.get()) runCatching { reply.send(Message.obtain(null, RESULT, requestId, millis).apply {
                        data = Bundle().apply { putInt("successes", assessment.successes); putInt("attempts", assessment.attempts)
                            putBoolean("unsupported", unsupported)
                            putString("diagnostic_events", VpnDiagnostics.exportEvents()) }
                    }) }
                }
            }
            CANCEL -> { closing.set(true); probe?.cancel() }
        }
        true
    })
    private class UnsupportedProbeEngine : Exception()
    private fun probeDirectory() = java.io.File(cacheDir, "manual-server-checks").apply { mkdirs() }
    private fun createServer(platform: CheckPlatform): CommandServer {
        val dir = probeDirectory()
        Libbox.setup(SetupOptions().apply {
            basePath = dir.absolutePath; workingPath = dir.absolutePath; tempPath = dir.absolutePath
            fixAndroidStack = true; logMaxLines = 1
            commandServerSecret = java.util.UUID.randomUUID().toString()
            appVersion = BuildConfig.VERSION_CODE.toString(); appMarketingVersion = BuildConfig.VERSION_NAME
        })
        return CommandServer(object : CommandServerHandler {
            override fun serviceStop() = Unit
            override fun serviceReload() = Unit
            override fun getSystemProxyStatus() = SystemProxyStatus().apply { available = false; enabled = false }
            override fun setSystemProxyEnabled(enabled: Boolean) = Unit
            override fun triggerNativeCrash() = Unit
            override fun writeDebugMessage(message: String?) { message?.let(VpnDiagnostics::coreMessage) }
            override fun connectSSHAgent() = -1
        }, platform).apply { start() }
    }
    override fun onBind(intent: Intent) = messenger.binder
    override fun onUnbind(intent: Intent?): Boolean {
        closing.set(true); probe?.cancel()
        return false
    }
    override fun onDestroy() {
        closing.set(true); probe?.cancel()
        queue.shutdownNow()
        super.onDestroy()
        // A blocked protocol handshake can outlive Box.close(). This process owns
        // only the disposable checker; releasing it closes every native socket and heap.
        val processName = if (Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName() else {
            (getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        if (processName == "$packageName:server_checks") Process.killProcess(Process.myPid())
    }
    companion object { internal const val CHECK = 1; internal const val CANCEL = 2; internal const val RESULT = 3; internal const val PROGRESS = 4 }
}
/** All sockets and DNS use the physical network; this platform cannot open a VPN interface. */
private class CheckPlatform(context: Context) : PlatformInterface {
    fun diagnosticNetwork(): Network? = network()
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private fun network(): Network? = connectivity.allNetworks.filter { network ->
        connectivity.getNetworkCapabilities(network)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
    }.maxByOrNull { n -> connectivity.getNetworkCapabilities(n)?.let {
        (if(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0) +
            (if(it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 3 else 1)
    } ?: 0 }
    override fun localDNSTransport(): LocalDNSTransport = AndroidLocalDnsTransport(::network)
    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun autoDetectInterfaceControl(fd: Int) {
        val network = network() ?: error("No physical network")
        ParcelFileDescriptor.fromFd(fd).use { network.bindSocket(it.fileDescriptor) }
    }
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val name = network()?.let { connectivity.getLinkProperties(it)?.interfaceName }.orEmpty()
        val index = runCatching { JNetworkInterface.getByName(name)?.index ?: 0 }.getOrDefault(0)
        listener.updateDefaultInterface(name, index, false, false)
    }
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) = Unit
    override fun openTun(options: TunOptions): Int = error("Manual checks do not open TUN")
    override fun getInterfaces(): NetworkInterfaceIterator {
        val physical = network()
        val links = physical?.let(connectivity::getLinkProperties)
        val javaInterface = links?.interfaceName?.let { runCatching { JNetworkInterface.getByName(it) }.getOrNull() }
        val values = if (javaInterface == null) emptyList() else listOf(NetworkInterface().apply {
            name = javaInterface.name; index = javaInterface.index; mtu = javaInterface.mtu
            flags = android.system.OsConstants.IFF_UP or android.system.OsConstants.IFF_RUNNING
            type = Libbox.InterfaceTypeOther
            addresses = Strings(javaInterface.interfaceAddresses.mapNotNull { address ->
                address.address.hostAddress?.substringBefore('%')?.let { "$it/${address.networkPrefixLength}" }
            })
            dnsServer = Strings(links.dnsServers.mapNotNull { it.hostAddress })
        })
        return object : NetworkInterfaceIterator {
            var index = 0
            override fun hasNext() = index < values.size
            override fun next() = values[index++]
        }
    }
    private class Strings(private val values: List<String>) : StringIterator {
        private var index = 0
        override fun len() = values.size
        override fun hasNext() = index < values.size
        override fun next() = values[index++]
    }
    override fun findConnectionOwner(protocol: Int, source: String?, sourcePort: Int, destination: String?, destinationPort: Int) =
        ConnectionOwner().apply { userId = -1; userName = ""; processPath = "" }
    override fun useProcFS() = false
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() = Unit
    override fun sendNotification(notification: Notification) = Unit
    override fun cancelNotification(identifier: String, typeID: Int) = Unit
    override fun startNeighborMonitor(listener: NeighborUpdateListener) = Unit
    override fun closeNeighborMonitor(listener: NeighborUpdateListener) = Unit
    override fun registerMyInterface(name: String) = Unit
    override fun usePlatformShell() = false
    override fun checkPlatformShell() = Unit
    override fun lookupUser(name: String): PlatformUser = error("Unsupported")
    override fun openShellSession(user: PlatformUser, command: String, args: StringIterator, cwd: String, columns: Int, rows: Int): ShellSession = error("Unsupported")
    override fun lookupSFTPServer() = ""
    override fun readSystemSSHHostKey() = ""
    override fun tailscaleHostname() = ""
    override fun usePlatformBridge() = false
    override fun createBridge(options: BridgeOptions): BridgeSession = error("Unsupported")
}
