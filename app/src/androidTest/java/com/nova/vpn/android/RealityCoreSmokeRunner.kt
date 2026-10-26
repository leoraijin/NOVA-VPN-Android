package com.nova.vpn.android

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.nova.vpn.android.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Synthetic fixtures by default; liveSubscription is an explicit opt-in with one-use local input. */
class RealityCoreSmokeRunner : Instrumentation() {
    private var liveSubscription = false
    private var probeUseNetworkCallbacks = true
    private var lastProbeNetworkHandle = 0L
    private var hostnameBootstrapCycles = 1
    private var hostnameBootstrapOnly = false
    private var bypassVisibilityOnly = false
    private var recoveryOnly = false
    private var revokeOnly = false
    private var dozeOnly = false
    private var recoveryCycles = 2
    private var isolatedEngine: VpnEngine? = null
    private var liveReloadOnly = false
    private var allowWifiToggle = false
    private var xhttpOnly = false
    private var xhttpDownlinkOnly = false
    private var xhttpTlsManifest: JSONObject? = null
    private var xhttpCoreTrace = false
    private var xhttpIpv6First = false
    private var xhttpTlsRefusal = false
    private var xhttpRealityKeyRefusal = false
    private var xhttpBootstrapCase: String? = null
    private var uiSeedOnly = false
    private var subscriptionsOnly = false
    private var subscriptionUiOnly = false
    private var serverCheckOnly = false
    private var expectedProbeSuccesses = 6
    private var probeEngine = VpnEngine.SING_BOX
    private var probeCaBase64: String? = null
    private var automaticServerOnly = false
    private var autoBudgetOnly = false
    private var autoBudgetPhase = "roundtrip"
    private var diagnosticsUiOnly = false
    private var transferCrashPhase: String? = null
    private var subscriptionSchedulePhase: String? = null
    private var serverLibraryStyle: String? = null
    private var serverLibraryTheme = ThemeMode.DARK
    private var diagnosticsTheme = ThemeMode.LIGHT
    override fun onCreate(arguments: Bundle?) {
        liveSubscription = arguments?.getString("liveSubscription") == "true"
        transferCrashPhase = arguments?.getString("transferCrashPhase")
        subscriptionSchedulePhase = arguments?.getString("subscriptionSchedulePhase")
        serverLibraryStyle = arguments?.getString("serverLibraryStyle")
        serverLibraryTheme = ThemeMode.valueOf(arguments?.getString("serverLibraryTheme") ?: "DARK")
        probeUseNetworkCallbacks = arguments?.getString("probeUseNetworkCallbacks") != "false"
        hostnameBootstrapOnly = arguments?.getString("hostnameBootstrapOnly") == "true"
        hostnameBootstrapCycles = arguments?.getString("hostnameBootstrapCycles")?.toInt() ?: 1
        check(hostnameBootstrapCycles in 1..20 && (hostnameBootstrapOnly || hostnameBootstrapCycles == 1))
        bypassVisibilityOnly = arguments?.getString("bypassVisibilityOnly") == "true"
        recoveryOnly = arguments?.getString("recoveryOnly") == "true"
        revokeOnly = arguments?.getString("revokeOnly") == "true"
        check(!revokeOnly || arguments?.getString("allowVpnRevoke") == "true")
        dozeOnly = arguments?.getString("dozeOnly") == "true"
        check(!dozeOnly || arguments?.getString("allowDoze") == "true")
        recoveryCycles = arguments?.getString("recoveryCycles")?.toInt() ?: 2
        check(recoveryCycles in 1..30 && (recoveryOnly || recoveryCycles == 2))
        isolatedEngine = arguments?.getString("isolatedEngine")?.let { VpnEngine.valueOf(it) }
        check(isolatedEngine == null || recoveryOnly || bypassVisibilityOnly)
        liveReloadOnly = arguments?.getString("liveReloadOnly") == "true"
        allowWifiToggle = arguments?.getString("allowWifiToggle") == "true"
        xhttpOnly = arguments?.getString("xhttpOnly") == "true"
        xhttpDownlinkOnly = arguments?.getString("xhttpDownlinkOnly") == "true"
        if (xhttpDownlinkOnly) xhttpOnly = true
        arguments?.getString("xhttpTlsManifestBase64")?.let {
            check(it.length <= 32_768 && xhttpDownlinkOnly)
            xhttpTlsManifest = JSONObject(String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8))
        }
        xhttpCoreTrace = arguments?.getString("xhttpCoreTrace") == "true"
        check(!xhttpCoreTrace || xhttpDownlinkOnly)
        xhttpIpv6First = arguments?.getString("xhttpIpv6First") == "true"
        check(!xhttpIpv6First || xhttpDownlinkOnly)
        xhttpBootstrapCase = arguments?.getString("xhttpBootstrapCase")
        check(xhttpBootstrapCase == null || xhttpBootstrapCase in setOf("ip-domain", "domain-domain") && xhttpDownlinkOnly && xhttpTlsManifest != null)
        xhttpTlsRefusal = arguments?.getString("xhttpTlsRefusal") == "true"
        xhttpRealityKeyRefusal = arguments?.getString("xhttpRealityKeyRefusal") == "true"
        check(!xhttpRealityKeyRefusal || xhttpDownlinkOnly && xhttpTlsManifest != null)
        check(!xhttpRealityKeyRefusal || !xhttpTlsRefusal)
        check(!xhttpTlsRefusal || xhttpTlsManifest != null)
        uiSeedOnly = arguments?.getString("uiSeedOnly") == "true"
        subscriptionsOnly = arguments?.getString("subscriptionsOnly") == "true"
        subscriptionUiOnly = arguments?.getString("subscriptionUiOnly") == "true"
        serverCheckOnly = arguments?.getString("serverCheckOnly") == "true"
        expectedProbeSuccesses = arguments?.getString("expectedProbeSuccesses")?.toIntOrNull() ?: 6
        probeEngine = VpnEngine.fromId(arguments?.getString("probeEngine"))
        probeCaBase64 = arguments?.getString("probeCaBase64")
        automaticServerOnly = arguments?.getString("automaticServerOnly") == "true"
        autoBudgetOnly = arguments?.getString("autoBudgetOnly") == "true"
        autoBudgetPhase = arguments?.getString("autoBudgetPhase") ?: "roundtrip"
        diagnosticsUiOnly = arguments?.getString("diagnosticsUiOnly") == "true"
        if (diagnosticsUiOnly) diagnosticsTheme = when (arguments?.getString("diagnosticsTheme")) {
            null, "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> error("Unsupported diagnostic test theme")
        }
        super.onCreate(arguments); start()
    }
    override fun onStart() {
        if (liveSubscription) {
            val result = Bundle()
            try {
                LiveSubscriptionSmoke.run(this)
                result.putString("stream", "PASS live subscription assessment completed; see per-engine outcomes\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                // Never publish exception messages or stacks from real provider input.
                result.putString("stream", "FAIL live subscription assessment: ${error.javaClass.simpleName}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        serverLibraryStyle?.let { style ->
            val result = Bundle()
            try {
                ServerLibraryUiSmoke.run(this, style, serverLibraryTheme)
                result.putString("stream", "PASS server library UI: $style\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL server library UI: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        subscriptionSchedulePhase?.let { phase ->
            val result = Bundle()
            try {
                SubscriptionScheduleSmoke.run(targetContext, phase)
                result.putString("stream", "PASS subscription schedule phase: $phase\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL subscription schedule $phase: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        transferCrashPhase?.let { phase ->
            val result = Bundle()
            try {
                TransferCrashSmoke.run(this, phase)
                result.putString("stream", "PASS transfer crash phase: $phase\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL transfer crash phase $phase: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (diagnosticsUiOnly) {
            val result = Bundle()
            try {
                DiagnosticsUiSmoke.run(this, diagnosticsTheme)
                result.putString("stream", "PASS Android diagnostic preview in all available designs: ${diagnosticsTheme.name}\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL diagnostic UI: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (autoBudgetOnly) {
            val result = Bundle()
            try {
                AutoSwitchBudgetSmoke.run(targetContext, autoBudgetPhase)
                result.putString("stream", "PASS Android durable switch reservations: $autoBudgetPhase, recreated stores, boot identity, rollback cooldown\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL Android automatic budget: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (automaticServerOnly) { runAutomaticServerSmoke(); return }
        if (serverCheckOnly) {
            val result = Bundle()
            try {
                ServerSelectionSmoke.run(this, expectedProbeSuccesses, probeEngine, probeCaBase64)
                result.putString("stream", "PASS Android server selection and cancellation\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL Android server selection: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (subscriptionUiOnly) {
            val result = Bundle()
            try {
                SubscriptionUiSmoke.run(this)
                result.putString("stream", "PASS Android subscription dialog, editor, search/reset and favorite controls\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL subscription UI: ${error.javaClass.simpleName}\n${error.stackTraceToString().lineSequence().take(10).joinToString("\n")}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (subscriptionsOnly) {
            val result = Bundle()
            try {
                SubscriptionStoreSmoke.run(targetContext)
                result.putString("stream", "PASS Android encrypted legacy migration, concurrent writes, shared subscriptions, 200/304/401/429/partial/empty updates, encrypted full transfer\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL subscription store: ${error.javaClass.simpleName}\n${error.stackTraceToString().lineSequence().take(10).joinToString("\n")}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (uiSeedOnly) {
            val result = Bundle()
            try {
                check(isIsolatedUsbTestPackage(targetContext.packageName))
                val fixtures = listOf("🇩🇪 Germany XHTTP" to "xhttp", "🇳🇱 Netherlands WS" to "ws",
                    "🇺🇸 United States gRPC" to "grpc", "🇩🇪 Germany TCP" to "tcp").map { (name, type) ->
                    ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@fixture.example:443?type=$type#$name").single()
                }
                ProfileVault(targetContext).save(fixtures)
                SelectedProfileStore(targetContext).save(fixtures.first().id)
                AppPreferencesStore(targetContext).apply {
                    dismissTutorial(); setAutoConnectEnabled(false); setUpdateChecksEnabled(false); setServerGrouping("TRANSPORT")
                }
                result.putString("stream", "UI synthetic profiles seeded in isolated USB test package\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "UI seed failed: ${error.javaClass.simpleName}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (xhttpOnly) { runXhttpSmoke(); return }
        val result = Bundle()
        try {
            check(!recoveryOnly || allowWifiToggle) { "Recovery Wi-Fi test requires a wired device and explicit allowWifiToggle=true; TCP ADB may disconnect." }
            check(isIsolatedUsbTestPackage(targetContext.packageName)) { "JNI/TUN smoke requires an isolated USB package" }
            if (isolatedEngine == VpnEngine.SING_BOX) {
                runVpnSmoke()
                result.putString("stream", "Isolated SING_BOX ${if (recoveryOnly) "recovery" else "routing"} smoke passed without Rust JNI initialization.\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            RealityCoreBridge.requireAvailable()
            val protected = AtomicInteger()
            val callbacks = object : RealityCoreBridge.Callbacks {
                override fun protectSocket(fd: Int): Boolean { protected.incrementAndGet(); return true }
                override fun coreWarning(message: String) { VpnDiagnostics.coreMessage(message) }
            }
            val profile = ImportedProfile(name = "Synthetic", protocol = "vless", server = "127.0.0.1", port = 443,
                outboundJson = """{"type":"vless","server":"127.0.0.1","server_port":443,"uuid":"11111111-1111-4111-8111-111111111111","tls":{"enabled":true,"server_name":"localhost"}}""")
            val generated = RealityConfigFactory.prepare(SingBoxConfigFactory.build(profile,
                ownPackage = targetContext.packageName), targetContext.packageName)
            check(RealityCoreBridge.validate(generated.config, targetContext.filesDir.path)) { "Generated config rejected" }
            val badBootstrap = JSONObject(generated.config).apply {
                getJSONObject("dns").put("bootstrap", "missing-test-upstream")
            }
            check(!RealityCoreBridge.validate(badBootstrap.toString(), targetContext.filesDir.path)) { "Missing bootstrap tag accepted" }
            badBootstrap.getJSONObject("dns").put("bootstrap", "remote-dns")
            check(!RealityCoreBridge.validate(badBootstrap.toString(), targetContext.filesDir.path)) { "Proxy bootstrap accepted" }
            check(!RealityCoreBridge.validate("{}", targetContext.filesDir.path)) { "Invalid config accepted" }
            val badTls = JSONObject(generated.config).apply {
                getJSONArray("outbounds").getJSONObject(0).put("tls", JSONObject().put("enabled", true).put("insecure", true))
            }.toString()
            check(!RealityCoreBridge.validate(badTls, targetContext.filesDir.path)) { "Insecure TLS accepted" }
            val pipe = ParcelFileDescriptor.createPipe()
            val transferred = pipe[0].detachFd()
            check(RealityCoreBridge.start("{}", targetContext.filesDir.path, transferred, callbacks) == 0L)
            check(!java.io.File("/proc/self/fd/$transferred").exists()) {
                "Native failed start did not close owned FD"
            }
            pipe[1].close()
            // Repeated real SOCKS traffic, not just library-load checks.
            repeat(8) {
                ServerSocket(0).use { echo ->
                    val worker = thread(isDaemon = true) {
                        echo.accept().use { client -> client.getOutputStream().write(client.getInputStream().read()) }
                    }
                    val port = ServerSocket(0).use { it.localPort }
                    val config = JSONObject().put("inbounds", JSONArray().put(JSONObject()
                        .put("type", "socks").put("tag", "smoke").put("listen", "127.0.0.1").put("listen_port", port)))
                        .put("outbounds", JSONArray().put(JSONObject().put("type", "direct").put("tag", "direct")))
                        .put("route", JSONObject().put("final", "direct")).toString()
                    val handle = RealityCoreBridge.start(config, targetContext.filesDir.path, -1, callbacks)
                    check(handle != 0L) { "Native SOCKS start failed" }
                    try {
                        check(RealityCoreBridge.isAlive(handle))
                        Socket("127.0.0.1", port).use { socket ->
                            socket.soTimeout = 5000
                            val input = socket.getInputStream(); val output = socket.getOutputStream()
                            output.write(byteArrayOf(5, 1, 0)); output.flush()
                            check(input.read() == 5 && input.read() == 0)
                            output.write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1,
                                (echo.localPort shr 8).toByte(), echo.localPort.toByte())); output.flush()
                            check(input.read() == 5 && input.read() == 0)
                            check(input.read() == 0)
                            val size = when (input.read()) { 1 -> 4; 4 -> 16; else -> error("SOCKS address") }
                            repeat(size + 2) { check(input.read() >= 0) }
                            output.write(42); output.flush()
                            check(input.read() == 42) { "Native proxy dropped echo payload" }
                        }
                        check(RealityCoreBridge.isAlive(handle))
                    } finally { RealityCoreBridge.stop(handle) }
                    worker.join(5000)
                    check(!worker.isAlive)
                }
            }
            check(protected.get() >= 8) { "Socket callback not invoked" }
            runVpnSmoke()
            result.putString("stream", if (hostnameBootstrapOnly)
                "Transport hostname bootstrap smoke passed: protected local DNS, TLS identity and separate-UID TUN echo.\n"
                else if (liveReloadOnly)
                "Live reload smoke passed: both engines, burst supersession, invalid-config rollback, rapid stop/start and separate-UID IPv4/IPv6 TUN echo.\n"
                else if (revokeOnly)
                "VPN permission revocation passed: both engines stopped, no spontaneous restart, explicit restart restores IPv4/IPv6.\n"
                else if (dozeOnly)
                "Doze smoke passed: both engines, verified deep-idle state, retained TUN, no idle restart, IPv4/IPv6 after wake.\n"
                else if (recoveryOnly)
                "Recovery smoke passed: engines=${isolatedEngine?.name ?: "REALITY_CORE,SING_BOX"}, physical Wi-Fi loss/return, retained Android TUN, IPv4/IPv6 echo after restart and manual stop cancellation.\n"
                else if (bypassVisibilityOnly)
                "Bypass visibility smoke passed: independent UID, baseline, real IPv4/IPv6 TUN echo and Android exclusion with conflicting site policy; engines=${isolatedEngine?.name ?: "REALITY_CORE,SING_BOX"}. Core ${RealityCoreBridge.version()}\n"
                else "Reality JNI/TUN smoke passed: validation, TLS refusal, FD ownership, 8 SOCKS/stop cycles, callbacks, real VpnService, IPv4/IPv6 app TCP echo, include/exclude policies, live reload, quiet single notification and sing-box regression. Core ${RealityCoreBridge.version()}\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "Reality smoke failed: ${failure.javaClass.simpleName}: ${failure.message}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun runVpnSmoke() {
        val context = targetContext
        check(android.net.VpnService.prepare(context) == null) { "Grant ACTIVATE_VPN to the preview package before TUN smoke" }
        val routingStore = AppRoutingStore(context)
        val originalRouting = routingStore.loadPresets()
        val excludedRouting = AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED, setOf("${context.packageName}.test"),
            listOf(SiteRoutingRule("native.test", SiteRoutingTarget.VPN)))
        if (liveReloadOnly) routingStore.updatePresets { current -> current.copy(pending = null,
            presets = current.presets.filterNot { it.id in setOf("fixture-routing", "fixture-excluded", "invalid-routing") } + listOf(
                RoutingPreset("fixture-routing", "Fixture VPN", AppRoutingSettings()),
                RoutingPreset("fixture-excluded", "Fixture direct", excludedRouting),
                RoutingPreset("invalid-routing", "Fixture invalid", AppRoutingSettings(ipv6Enabled = true)))) }
        AppPreferencesStore(context).apply {
            dismissTutorial(); setAutoConnectEnabled(false); setUpdateChecksEnabled(false)
            setVpnEngine(VpnEngine.REALITY_CORE)
        }
        context.getSharedPreferences("app_preferences", 0).edit().putBoolean("notification_permission_requested", true).commit()
        val activity = startActivitySync(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val tlsFixture = TestTlsFixture(context)
        tlsFixture.serverSocket().use { listener ->
            val packets = AtomicInteger()
            val sitePackets = AtomicInteger()
            val acceptedPeers = AtomicInteger()
            val tlsCompletions = AtomicInteger()
            val fixtureFailure = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val acceptor = thread(isDaemon = true) {
                while (!listener.isClosed) runCatching {
                    val peer = listener.accept()
                    acceptedPeers.incrementAndGet()
                    thread(isDaemon = true) {
                        runCatching { peer.use { socket ->
                            socket.soTimeout = 7000
                            (socket as javax.net.ssl.SSLSocket).startHandshake()
                            tlsCompletions.incrementAndGet()
                            val input = java.io.DataInputStream(socket.getInputStream())
                            check(input.readUnsignedByte() == 0)
                            input.readFully(ByteArray(16))
                            input.readFully(ByteArray(input.readUnsignedByte()))
                            check(input.readUnsignedByte() == 1)
                            input.readUnsignedShort()
                            val type = input.readUnsignedByte()
                            val destination = ByteArray(when (type) { 1 -> 4; 2 -> input.readUnsignedByte(); 3 -> 16; else -> error("VLESS address") })
                            input.readFully(destination)
                            socket.getOutputStream().write(byteArrayOf(0, 0))
                            val payload = ByteArray(14)
                            input.readFully(payload)
                            val count = payload.size
                            if (count > 0) {
                                val text = String(payload, 0, count, Charsets.US_ASCII)
                                if (text == "nova-tun-smoke") {
                                    packets.incrementAndGet()
                                    if (type == 2 && String(destination, Charsets.US_ASCII) == "native.test") sitePackets.incrementAndGet()
                                }
                                socket.getOutputStream().write(payload, 0, count)
                                socket.getOutputStream().flush()
                            }
                        } }.onFailure { fixtureFailure.set(it.javaClass.simpleName + ": " + it.message) }
                    }
                }
            }
            try {
                val profile = ImportedProfile(name = "Synthetic TUN", protocol = "vless", server = "127.0.0.1", port = listener.localPort,
                    outboundJson = JSONObject().put("type", "vless").put("server", "127.0.0.1")
                        .put("server_port", listener.localPort).put("uuid", "11111111-1111-4111-8111-111111111111")
                        .put("tls", JSONObject().put("enabled", true).put("server_name", "localhost")
                            .put("certificate_path", tlsFixture.certificatePath)).toString())
                val config = SingBoxConfigFactory.build(profile, ownPackage = context.packageName)
                fun start(config: String, burst: Int = 1, presetId: String = "fixture-routing") {
                    val previousRevision = VpnRuntime.state.value.sessionRevision
                    var lastRequest = ""
                    repeat(burst) {
                        lastRequest = VpnUserRequests.gate.begin()
                        if (liveReloadOnly) routingStore.stage(presetId, lastRequest)
                        context.startForegroundService(android.content.Intent(context, NovaVpnService::class.java)
                            .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                            .putExtra(VpnUserRequests.EXTRA_REQUEST, lastRequest)
                            .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "Synthetic TUN")
                            .putExtra(NovaVpnService.EXTRA_PROFILE_ID, profile.id)
                            .putExtra(NovaVpnService.EXTRA_ROUTING_PRESET_ID, presetId))
                    }
                    waitUntil("startup") { (VpnRuntime.state.value.connected && VpnRuntime.state.value.sessionRevision > previousRevision &&
                        VpnRuntime.state.value.appliedConfiguration?.requestId == lastRequest) || VpnRuntime.state.value.error != null }
                    check(VpnRuntime.state.value.connected) { "VpnService failed: ${VpnRuntime.state.value.error}" }
                    val identity = VpnRuntime.state.value.appliedConfiguration ?: error("No service apply identity")
                    check(identity.profileId == profile.id && identity.routingPresetId == presetId) { "Applied identity differs from actual request" }
                    check(VpnApplyRequests.tracker.results.value[identity.requestId]?.status == ApplyStatus.APPLIED) { "No successful service acknowledgement" }
                    if (liveReloadOnly) check(routingStore.loadPresets().activeId == presetId && routingStore.loadPresets().pending == null) {
                        "Service acknowledgement did not atomically commit the active routing preset"
                    }
                }
                fun stop() {
                    VpnUserRequests.stop(context)
                    waitUntil("stop") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
                }
                fun checkHostnameBootstrap(expectedPackets: Int, hostname: String = "transport.test") {
                    fun vpnHandles(): List<Long> {
                        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
                        return manager.allNetworks.filter {
                            manager.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                        }.map { it.networkHandle }
                    }
                    val previousProbeHandle = lastProbeNetworkHandle
                    val beforeStart = vpnHandles()
                    fixtureFailure.set(null)
                    val peersBefore = acceptedPeers.get()
                    val tlsBefore = tlsCompletions.get()
                    TestDnsFixture(hostname).use { bootstrap ->
                        val hostnameProfile = profile.copy(server = hostname, outboundJson =
                            JSONObject(profile.outboundJson).put("server", hostname).toString())
                        val hostnameConfig = JSONObject(SingBoxConfigFactory.build(hostnameProfile,
                            AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf("${context.packageName}.test")), context.packageName))
                        hostnameConfig.getJSONObject("dns").put("bootstrap", "smoke-bootstrap")
                            .getJSONArray("servers").put(JSONObject().put("type", "udp").put("tag", "smoke-bootstrap")
                                .put("server", "127.0.0.1").put("server_port", bootstrap.port).put("detour", "direct"))
                        start(hostnameConfig.toString())
                        val registeredBeforeProbe = vpnHandles()
                        val bootstrapResult = routedClientResult()
                        val handles = "previousProbe=$previousProbeHandle beforeStart=$beforeStart registered=$registeredBeforeProbe probe=$lastProbeNetworkHandle after=${vpnHandles()}"
                        val stages = "${bootstrap.summary()} tcpAccepted=${acceptedPeers.get() - peersBefore} tlsCompleted=${tlsCompletions.get() - tlsBefore} $handles"
                        sendStatus(0, Bundle().apply { putString("stream", "Bootstrap stages: $stages\n") })
                        check(bootstrapResult == "ok" && packets.get() == expectedPackets && bootstrap.transportQueries.get() > 0) {
                            "Transport hostname bootstrap: result=$bootstrapResult packets=${packets.get()} queries=${bootstrap.transportQueries.get()} stages=$stages fixture=${fixtureFailure.get()} state=${VpnRuntime.state.value} diagnostics=${VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value)}"
                        }
                        stop()
                    }
                }
                if (hostnameBootstrapOnly) {
                    repeat(hostnameBootstrapCycles) { cycle ->
                        // Unique host bypasses the native host cache after every core restart.
                        checkHostnameBootstrap(cycle + 1, "transport-cycle-$cycle-${java.util.UUID.randomUUID()}.test")
                        sendStatus(0, Bundle().apply { putString("stream", "Bootstrap restart cycle ${cycle + 1}/$hostnameBootstrapCycles: PASS\n") })
                    }
                    return@use
                }
                // Compare the same independent UID before any VPN exists.
                routedClientResult(expectPhysical = true)
                val testPackage = "${context.packageName}.test"
                for (engine in isolatedEngine?.let { listOf(it) } ?: listOf(VpnEngine.REALITY_CORE, VpnEngine.SING_BOX)) {
                    AppPreferencesStore(context).setVpnEngine(engine)
                    if (liveReloadOnly) {
                        start(config)
                        verifyAutomaticDiagnosticOwnership(config, profile, "fixture-routing", engine)
                        start(config)
                        val transferPassword = "Synthetic-routing-transfer-2026"
                        val transfer = FullTransfer.read(FullTransfer.export(context, transferPassword), transferPassword)
                        val routesBeforeRestore = routingStore.loadPresets()
                        val profilesBeforeRestore = ProfileVault(context).loadSnapshot().profiles
                        val tokenBeforeRestore = VpnUserRequests.gate.snapshot()
                        val refused = runCatching { FullTransfer.restore(context, transfer) }.exceptionOrNull()
                        check(refused is IllegalStateException && refused.message.orEmpty().contains("Дождитесь полного отключения")) {
                            "Full restore was not rejected by the active service fence"
                        }
                        check(VpnUserRequests.gate.isCurrent(tokenBeforeRestore) && VpnRuntime.state.value.connected)
                        check(routingStore.loadPresets() == routesBeforeRestore && ProfileVault(context).loadSnapshot().profiles == profilesBeforeRestore)
                        sendStatus(0, Bundle().apply { putString("stream", "Full restore $engine: active service refused without intent/profile/routing changes PASS\n") })
                        start(config, burst = 20)
                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok") { "Burst reload damaged routes" }
                        val revision = VpnRuntime.state.value.sessionRevision
                        val invalidRequest = VpnUserRequests.gate.begin()
                        routingStore.stage("invalid-routing", invalidRequest)
                        context.startForegroundService(android.content.Intent(context, NovaVpnService::class.java)
                            .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, "{invalid-fixture")
                            .putExtra(VpnUserRequests.EXTRA_REQUEST, invalidRequest)
                            .putExtra(NovaVpnService.EXTRA_PROFILE_ID, "invalid-profile")
                            .putExtra(NovaVpnService.EXTRA_ROUTING_PRESET_ID, "invalid-routing")
                            .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "Invalid fixture"))
                        waitUntil("configuration rollback") { VpnRuntime.state.value.sessionRevision > revision || VpnRuntime.state.value.error != null }
                        check(VpnRuntime.state.value.connected && VpnRuntime.state.value.error == null)
                        val rollbackAck = VpnApplyRequests.tracker.results.value[invalidRequest]
                        check(rollbackAck?.status == ApplyStatus.ROLLED_BACK && rollbackAck.applied?.profileId == profile.id &&
                            rollbackAck.applied?.routingPresetId == "fixture-routing") { "Rollback acknowledged the failed routing profile" }
                        check(routingStore.loadPresets().activeId == "fixture-routing" && routingStore.loadPresets().pending == null) {
                            "Failed configuration changed the saved routing preset"
                        }
                        check(VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value).contains("configuration_rollback"))
                        check(routedClientResult() == "ok") { "Rollback lost routed traffic" }
                        repeat(3) {
                            VpnUserRequests.stop(context)
                            start(config)
                            check(routedClientResult() == "ok") { "Stop/start race lost routed traffic" }
                        }
                        start(SingBoxConfigFactory.build(profile, excludedRouting, context.packageName), presetId = "fixture-excluded")
                        check(routingStore.load() == excludedRouting) { "Committed rules differ from actual fixture" }
                        check(routedClientResult(expectPhysical = true) != "ok" && routedClientResult(ipv6 = true, expectPhysical = true) != "ok") {
                            "Live exclusion reused stale Builder filters"
                        }
                        check(routedClientResult(site = true, expectPhysical = true) != "ok") { "Live site policy captured an excluded app" }
                        start(config)
                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok") { "Restoring app policy lost TUN routing" }
                        stop()
                        val preparedBeforeStop = VpnUserRequests.gate.begin()
                        VpnUserRequests.stop(context)
                        check(!VpnUserRequests.start(context, config, "Stale preparation", preparedBeforeStop)) {
                            "Preparation completed after stop was accepted"
                        }
                        // Bypass the sender helper to simulate an already queued stale Intent.
                        context.startService(android.content.Intent(context, NovaVpnService::class.java)
                            .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                            .putExtra(VpnUserRequests.EXTRA_REQUEST, preparedBeforeStop))
                        Thread.sleep(500)
                        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting) { "Queued stale START revived VPN" }
                        start(config)
                        check(routedClientResult() == "ok") { "New intent after rejected stale START failed" }
                        stop()
                        sendStatus(0, Bundle().apply { putString("stream", "Live reload $engine: burst/rollback/stop-start/IPv4/IPv6 PASS\n") })
                        continue
                    }
                    if (revokeOnly) {
                        check(isIsolatedUsbTestPackage(context.packageName))
                        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
                            uiAutomation.executeShellCommand(command)).use { String(it.readBytes(), Charsets.UTF_8) }
                        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                        fun hasVpn() = connectivity.allNetworks.any { network ->
                            connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                        }
                        start(config)
                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok")
                        val oldRequest = VpnUserRequests.gate.snapshot()
                        try {
                            shell("cmd appops set ${context.packageName} ACTIVATE_VPN deny")
                            waitUntil("VPN consent revoked") { android.net.VpnService.prepare(context) != null }
                            waitUntil("revocation stops service and TUN") {
                                !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting && !hasVpn()
                            }
                            check(!VpnUserRequests.gate.isCurrent(oldRequest)) { "Revocation left stale user request valid" }
                            Thread.sleep(3000)
                            check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting && !hasVpn())
                        } finally {
                            shell("cmd appops set ${context.packageName} ACTIVATE_VPN allow")
                        }
                        waitUntil("test consent restored") { android.net.VpnService.prepare(context) == null }
                        Thread.sleep(3000)
                        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting && !hasVpn()) {
                            "Restoring consent restarted VPN without a user request"
                        }
                        start(config)
                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok")
                        stop()
                        sendStatus(0, Bundle().apply { putString("stream", "VPN revoke $engine: stopped, stale request cancelled, consent restore stays idle, explicit IPv4/IPv6 restart PASS\n") })
                        continue
                    }
                    if (dozeOnly) {
                        check(isIsolatedUsbTestPackage(context.packageName))
                        val power = context.getSystemService(android.os.PowerManager::class.java)
                        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
                            uiAutomation.executeShellCommand(command)).use { String(it.readBytes(), Charsets.UTF_8) }
                        fun handle() = connectivity.allNetworks.firstOrNull { network ->
                            connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                        }?.networkHandle
                        try {
                            check(!power.isDeviceIdleMode) { "Device already in idle mode before test" }
                            start(config)
                            check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok")
                            val originalHandle = handle()
                            check(originalHandle != null)
                            val originalRevision = VpnRuntime.state.value.sessionRevision
                            shell("dumpsys battery unplug")
                            shell("input keyevent 223")
                            shell("cmd deviceidle force-idle deep")
                            waitUntil("confirmed Doze") { power.isDeviceIdleMode && !power.isInteractive }
                            android.os.SystemClock.sleep(30_000)
                            check(power.isDeviceIdleMode && !power.isInteractive) { "Device left idle before observation ended" }
                            check(handle() == originalHandle && VpnRuntime.state.value.connected) { "Doze lost TUN" }
                            check(VpnRuntime.state.value.sessionRevision == originalRevision) { "Idle without traffic restarted the engine" }
                            sendStatus(0, Bundle().apply { putString("stream", "Doze $engine: 30 seconds deep idle, retained TUN, no restart PASS\n") })
                        } finally {
                            shell("cmd deviceidle unforce")
                            shell("dumpsys battery reset")
                            shell("input keyevent 224")
                            shell("wm dismiss-keyguard")
                        }
                        waitUntil("wake leaves Doze") { !power.isDeviceIdleMode && power.isInteractive }
                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok") { "Post-Doze routed traffic failed" }
                        sendStatus(0, Bundle().apply { putString("stream", "Doze $engine: post-wake IPv4/IPv6 PASS\n") })
                        stop()
                        continue
                    }
                    if (recoveryOnly) {
                        check(isIsolatedUsbTestPackage(context.packageName))
                        fun wifi(enabled: Boolean) {
                            ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
                                "svc wifi ${if (enabled) "enable" else "disable"}")).use { it.readBytes() }
                        }
                        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                        fun vpnHandle(): Long? = connectivity.allNetworks.firstOrNull { network ->
                            connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                        }?.networkHandle
                        try {
                            start(config)
                            waitUntil("VPN publication") { vpnHandle() != null }
                            val originalHandle = vpnHandle()
                            check(routedClientResult() == "ok")
                            repeat(recoveryCycles) { cycle ->
                                wifi(false)
                                waitUntil("waiting for physical network") { VpnRuntime.state.value.recoveryStatus == "waiting_network" }
                                check(vpnHandle() == originalHandle) { "Lost Android TUN while offline" }
                                val revision = VpnRuntime.state.value.sessionRevision
                                wifi(true)
                                waitUntil("native recovery") { VpnRuntime.state.value.connected &&
                                    VpnRuntime.state.value.recoveryStatus == null && VpnRuntime.state.value.sessionRevision > revision }
                                check(vpnHandle() == originalHandle) { "Recovery replaced the Android TUN" }
                                val probeStartRevision = VpnRuntime.state.value.sessionRevision
                                val recoveredIpv4 = routedClientResult()
                                val recoveredIpv6 = routedClientResult(ipv6 = true)
                                check(recoveredIpv4 == "ok" && recoveredIpv6 == "ok") {
                                    "Recovery dropped routed data: engine=$engine cycle=${cycle + 1}, " +
                                        "ipv4=$recoveredIpv4 ipv6=$recoveredIpv6 accepted=${acceptedPeers.get()} " +
                                        "tls=${tlsCompletions.get()} echoed=${packets.get()} fixture=${fixtureFailure.get()} " +
                                        "revisionBefore=$probeStartRevision revisionAfter=${VpnRuntime.state.value.sessionRevision} " +
                                        "recovery=${VpnRuntime.state.value.recoveryStatus}"
                                }
                                sendStatus(0, Bundle().apply { putString("stream", "Recovery $engine cycle=${cycle + 1}: retained TUN and IPv4/IPv6 echo PASS\n") })
                            }
                            wifi(false)
                            waitUntil("offline before manual stop") { VpnRuntime.state.value.recoveryStatus == "waiting_network" }
                            stop()
                            wifi(true)
                            android.os.SystemClock.sleep(1800)
                            check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting && vpnHandle() == null) {
                                "Manual stop was undone by a late network callback"
                            }
                            sendStatus(0, Bundle().apply { putString("stream", "Recovery $engine: manual stop cancellation PASS\n") })
                        } finally { wifi(true); stop() }
                        continue
                    }
                    sendStatus(0, Bundle().apply { putString("stream", "Bypass visibility engine=$engine\n") })
                    if (bypassVisibilityOnly) {
                        val before = packets.get()
                        start(config)
                        check(routedClientResult() == "ok" && packets.get() == before + 1) { "IPv4 TUN echo failed" }
                        check(routedClientResult(ipv6 = true) == "ok" && packets.get() == before + 2) { "IPv6 TUN echo failed" }
                        stop()
                    }
                    val beforeBypass = packets.get()
                    start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED,
                        setOf(testPackage), listOf(SiteRoutingRule("native.test", SiteRoutingTarget.VPN))), context.packageName))
                    check(routedClientResult(expectPhysical = true) != "ok" && packets.get() == beforeBypass) {
                        "Excluded application entered the synthetic VPN"
                    }
                    check(routedClientResult(ipv6 = true, expectPhysical = true) != "ok" && packets.get() == beforeBypass) {
                        "Excluded IPv6 application entered the synthetic VPN"
                    }
                    check(routedClientResult(site = true, expectPhysical = true) != "ok" && packets.get() == beforeBypass) {
                        "VPN site rule captured the excluded application"
                    }
                    stop()
                }
                if (liveReloadOnly) {
                    val password = "Synthetic-idle-transfer-2026"
                    val document = FullTransfer.read(FullTransfer.export(context, password), password)
                    val profilesBefore = ProfileVault(context).loadSnapshot().profiles
                    val routesBefore = routingStore.loadPresets()
                    val selectedBefore = SelectedProfileStore(context).load()
                    try {
                        waitUntil("service cleanup releases full restore fence") {
                            try { FullTransfer.restore(context, document); true }
                            catch (error: IllegalStateException) {
                                if (error.message.orEmpty().contains("Дождитесь полного отключения")) false else throw error
                            }
                        }
                        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
                        check(ProfileVault(context).loadSnapshot().profiles == profilesBefore && routingStore.loadPresets() == routesBefore)
                        sendStatus(0, Bundle().apply { putString("stream", "Full restore after both service lifecycles: idle import succeeds, profiles/routes preserved PASS\n") })
                    } finally { SelectedProfileStore(context).save(selectedBefore.orEmpty()) }
                }
                if (bypassVisibilityOnly || recoveryOnly || liveReloadOnly || dozeOnly || revokeOnly) return@use
                AppPreferencesStore(context).setVpnEngine(VpnEngine.REALITY_CORE)
                start(config, burst = 20)
                val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
                waitUntil("single published VPN notification") {
                    val count = notificationManager.activeNotifications.size
                    check(count <= 1) { "Duplicate VPN notifications: $count" }
                    count == 1
                }
                val notifications = notificationManager.activeNotifications
                check(notifications.size == 1) { "Expected one VPN notification, got ${notifications.size}" }
                val channel = context.getSystemService(android.app.NotificationManager::class.java)
                    .getNotificationChannel(notifications.single().notification.channelId)
                check(channel.importance <= android.app.NotificationManager.IMPORTANCE_LOW &&
                    channel.sound == null && !channel.shouldVibrate()) { "VPN notification is not silent/low priority" }
                val answer = routedClientResult()
                check(answer == "ok" && packets.get() == 1) {
                    "Traffic did not traverse native TUN/VLESS echo: response=${answer.take(100)}, packets=${packets.get()}, fixture=${fixtureFailure.get()}, " +
                        VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value)
                }
                check(routedClientResult(ipv6 = true) == "ok" && packets.get() == 2) { "IPv6 bypassed or failed TUN" }
                // A live request must complete and keep the selected routes working.
                start(config)
                check(routedClientResult() == "ok" && packets.get() == 3) { "Live reload damaged tunnel" }
                stop()
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf(testPackage)), context.packageName))
                check(routedClientResult() == "ok" && packets.get() == 4) {
                    "Included application did not traverse TUN: packets=${packets.get()} fixture=${fixtureFailure.get()} " +
                        VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value)
                }
                stop()
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED, setOf(testPackage)), context.packageName))
                check(routedClientResult(expectPhysical = true) != "ok" && packets.get() == 4) { "Excluded application unexpectedly traversed TUN" }
                stop()
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED,
                    sites = listOf(SiteRoutingRule("native.test", SiteRoutingTarget.VPN))), context.packageName))
                check(routedClientResult(site = true) == "ok" && packets.get() == 5 && sitePackets.get() == 1) {
                    "Site VPN exception did not preserve fake-IP domain routing"
                }
                stop()
                checkHostnameBootstrap(6)
                AppPreferencesStore(context).setVpnEngine(VpnEngine.SING_BOX)
                start(config)
                check(routedClientResult() == "ok" && packets.get() == 7) { "Default sing-box regression: TLS/TUN echo failed" }
                stop()
                AppPreferencesStore(context).setVpnEngine(VpnEngine.REALITY_CORE)
                val mixedStart = packets.get()
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED,
                    setOf(testPackage), listOf(SiteRoutingRule("native.test", SiteRoutingTarget.VPN))), context.packageName))
                check(routedClientResult() == "ok" && packets.get() == mixedStart + 1) {
                    "Native mixed routing did not resolve selected application's real Android UID"
                }
                check(routedClientResult(ipv6 = true) == "ok" && packets.get() == mixedStart + 2) {
                    "Native mixed routing did not resolve IPv6 connection owner"
                }
                stop()
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED,
                    setOf(testPackage), listOf(SiteRoutingRule("native.test", SiteRoutingTarget.VPN))), context.packageName))
                check(routedClientResult(expectPhysical = true) != "ok" && packets.get() == mixedStart + 2) {
                    "Native UID direct rule failed to exclude selected app"
                }
                check(routedClientResult(site = true, expectPhysical = true) != "ok" && packets.get() == mixedStart + 2) {
                    "A site rule captured an application excluded at the Android VPN boundary"
                }
                stop()
                AppPreferencesStore(context).setVpnEngine(VpnEngine.SING_BOX)
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED,
                    setOf(testPackage), listOf(SiteRoutingRule("native.test", SiteRoutingTarget.VPN))), context.packageName))
                check(routedClientResult(site = true, expectPhysical = true) != "ok" && packets.get() == mixedStart + 2) {
                    "sing-box site rule captured an excluded application"
                }
                stop()
            } finally {
                context.startService(android.content.Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP))
                waitUntil("final cleanup") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
                AppPreferencesStore(context).setVpnEngine(VpnEngine.SING_BOX)
                if (liveReloadOnly) routingStore.updatePresets { originalRouting.copy(pending = null) }
                runOnMainSync { activity.finish() }
            }
            listener.close()
            acceptor.join(1000)
        }
    }

    private fun runAutomaticServerSmoke() {
        val result = Bundle()
        var outcome = Activity.RESULT_CANCELED
        val context = targetContext
        val vault = ProfileVault(context)
        val routes = AppRoutingStore(context)
        val options = AutoServerSettingsStore(context)
        val previousProfiles = vault.loadSnapshot()
        val previousRoutes = routes.loadPresets()
        val previousOptions = options.snapshot()
        val previousSelection = SelectedProfileStore(context).load()
        val previousEngine = AppPreferencesStore(context).vpnEngine()
        fun await(label: String, seconds: Int = 20, condition: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + seconds * 1000L
            while (!condition()) {
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Timed out: $label" }
                Thread.sleep(100)
            }
        }
        try {
            check(isIsolatedUsbTestPackage(context.packageName))
            check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
            check(android.net.VpnService.prepare(context) == null)
            AppPreferencesStore(context).setVpnEngine(VpnEngine.SING_BOX)
            val good = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@127.0.0.1:38123?type=xhttp&path=%2Fnova-fixture&mode=packet-up#Auto-good").single().copy(id = "auto-good")
            val bad = good.copy(id = "auto-bad", name = "Auto-bad", port = 38125,
                outboundJson = JSONObject(good.outboundJson).put("server_port", 38125).toString())
            vault.save(listOf(bad, good))
            val retained = AppRoutingSettings(ipv6Enabled = true,
                sites = listOf(SiteRoutingRule("direct-smoke.invalid", SiteRoutingTarget.DIRECT)))
            routes.save(retained)
            val routingId = routes.loadPresets().activeId
            SelectedProfileStore(context).save(bad.id)
            options.save(true, setOf(bad.id, good.id))
            val token = VpnUserRequests.gate.begin()
            check(VpnUserRequests.start(context, PreparedVpnConfiguration.prepare(context, bad), token))
            await("initial synthetic tunnel") { VpnRuntime.state.value.connected }
            await("automatic failover after two distinct checks", 110) {
                VpnRuntime.state.value.appliedConfiguration?.profileId == good.id
            }
            await("automatic acknowledgement") { AutoServerRuntime.state.value.profileId == good.id }
            check(AutoServerRuntime.state.value.reason == AutoSwitchPolicy.Reason.CURRENT_FAILED)
            check(routes.load() == retained && routes.loadPresets().activeId == routingId)
            check(VpnRuntime.state.value.appliedConfiguration?.routingPresetId == routingId)
            check(routedClientResult() == "ok") { "Automatic failover IPv4 echo failed" }
            check(routedClientResult(ipv6 = true) == "ok") { "Automatic failover IPv6 echo failed" }
            AutoServerSettingsStore.pin(context)
            VpnUserRequests.stop(context)
            await("manual stop") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
            Thread.sleep(1500)
            check(!VpnRuntime.state.value.connected && !options.snapshot().enabled)
            result.putString("stream", "PASS automatic Android failover: two failed rounds, actual service acknowledgement, preserved routing, separate-UID IPv4/IPv6 TUN echo, manual stop/pin\n")
            outcome = Activity.RESULT_OK
        } catch (error: Throwable) {
            result.putString("stream", "FAIL automatic Android failover: ${error.javaClass.simpleName}: ${error.message}\n")
        } finally {
            runCatching { options.save(false) }
            runCatching { VpnUserRequests.stop(context) }
            runCatching { await("cleanup") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting } }
            vault.updateSnapshot { previousProfiles }
            routes.updatePresets { previousRoutes.copy(pending = null) }
            if (previousSelection != null) SelectedProfileStore(context).save(previousSelection) else SelectedProfileStore(context).clear()
            options.save(previousOptions.enabled, previousOptions.candidateIds)
            options.close()
            AppPreferencesStore(context).setVpnEngine(previousEngine)
        }
        finish(outcome, result)
    }

    private fun runXhttpSmoke() {
        val result = Bundle()
        var outcome = Activity.RESULT_CANCELED
        var activity: Activity? = null
        val context = targetContext
        val certificates = mutableListOf<java.io.File>()
        val traces = mutableListOf<java.io.File>()
        val bootstrapFixtures = mutableListOf<TestDnsFixture>()
        try {
            check(isIsolatedUsbTestPackage(context.packageName))
            check(android.net.VpnService.prepare(context) == null)
            AppPreferencesStore(context).apply {
                dismissTutorial(); setAutoConnectEnabled(false); setUpdateChecksEnabled(false); setVpnEngine(VpnEngine.SING_BOX)
            }
            activity = startActivitySync(android.content.Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val rootFiles = xhttpTlsManifest?.let { manifest -> listOf("upload", "download").associateWith { leg ->
                val ca = manifest.getJSONObject(leg).getString("ca")
                check(ca.length <= 8192 && ca.startsWith("-----BEGIN CERTIFICATE-----"))
                java.io.File(context.filesDir, "xhttp-$leg-${java.util.UUID.randomUUID()}.pem").also { file ->
                    certificates += file; file.writeText(ca, Charsets.UTF_8)
                }
            } }
            if (xhttpDownlinkOnly) check(BuildConfig.XHTTP_DOWNLOAD_SUPPORTED &&
                BuildConfig.LIBBOX_REVISION.startsWith("1.14.3-lx.23-nova-downlink.") &&
                io.nekohasekai.libbox.Libbox.version() == BuildConfig.LIBBOX_REVISION) {
                "Downlink smoke requires the exact checksum-pinned preview core"
            }
            val modes = if (xhttpTlsRefusal || xhttpRealityKeyRefusal) listOf("packet-up", "packet-up", "packet-up")
                else if (xhttpDownlinkOnly) listOf("packet-up", "stream-up", "auto") else listOf("packet-up", "stream-up", "stream-one", "auto")
            for ((caseIndex, mode) in modes.withIndex()) {
                val rejectDownloadIdentity = (xhttpTlsRefusal || xhttpRealityKeyRefusal) && caseIndex == 1
                val uploadHostname = "xup-${java.util.UUID.randomUUID()}.test"
                val downloadHostname = "xdown-${java.util.UUID.randomUUID()}.test"
                val bootstrapDns = xhttpBootstrapCase?.let {
                    TestDnsFixture(downloadHostname, "10.0.2.2",
                        if (it == "domain-domain") setOf(uploadHostname) else emptySet()).also(bootstrapFixtures::add)
                }
                // adb reverse tcp:38123 tcp:38123 exposes the synthetic Xray server.
                var profile = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@127.0.0.1:38123?type=xhttp&path=%2Fnova-fixture&mode=$mode#XHTTP-fixture").single()
                if (xhttpDownlinkOnly) {
                    val download = JSONObject().put("address", "127.0.0.1").put("port", 38123)
                        .put("network", "xhttp").put("xhttpSettings", JSONObject().put("path", "/nova-fixture"))
                    val encoded = java.net.URLEncoder.encode(download.toString(), "UTF-8")
                    profile = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@127.0.0.1:38123?type=xhttp&path=%2Fnova-fixture&mode=$mode&downloadSettings=$encoded#XHTTP-import-fixture").single()
                    check(JSONObject(profile.outboundJson).getJSONObject("transport").has("download_settings"))
                }
                xhttpTlsManifest?.let { manifest ->
                    fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
                    val upload = manifest.getJSONObject("upload")
                    val download = manifest.getJSONObject("download")
                    fun alpn(leg: JSONObject) = when (leg.getInt("version")) {
                        1 -> "http/1.1"
                        2 -> "h2"
                        3 -> "h3"
                        else -> error("Unsupported fixture HTTP version")
                    }
                    fun address(leg: JSONObject): String {
                        val literal = leg.optString("address", "127.0.0.1")
                        check(literal == "127.0.0.1" || literal == "10.0.2.2")
                        if (bootstrapDns != null) {
                            check(literal == "10.0.2.2")
                            if (leg === download) return downloadHostname
                            if (xhttpBootstrapCase == "domain-domain") return uploadHostname
                        }
                        return literal
                    }
                    fun security(leg: JSONObject) = leg.optString("security", "tls").also {
                        check(it == "tls" || it == "reality")
                        check(it != "reality" || leg.getInt("version") == 2)
                    }
                    val downSecurity = security(download)
                    val upSecurity = security(upload)
                    if (xhttpRealityKeyRefusal) check(downSecurity == "reality")
                    fun settings(leg: JSONObject) = JSONObject()
                        .put("serverName", leg.getString("server_name")).put("alpn", alpn(leg)).apply {
                            if (security(leg) == "reality") {
                                put("fingerprint", "chrome"); put("publicKey", leg.getString("public_key"))
                                put("shortId", leg.getString("short_id"))
                            } else {
                                put("minVersion", "1.2"); put("maxVersion", "1.3")
                            }
                        }
                    val downloadStream = JSONObject().put("address", address(download)).put("port", download.getInt("port"))
                        .put("network", "xhttp").put("security", downSecurity)
                        .put(if (downSecurity == "reality") "realitySettings" else "tlsSettings", settings(download))
                        .put("xhttpSettings", JSONObject().put("path", download.getString("path")).put("host", download.getString("server_name")))
                    val upReality = if (upSecurity == "reality")
                        "&fp=chrome&pbk=${encode(upload.getString("public_key"))}&sid=${encode(upload.getString("short_id"))}" else ""
                    profile = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@${address(upload)}:${upload.getInt("port")}" +
                        "?security=$upSecurity&sni=${encode(upload.getString("server_name"))}&alpn=${encode(alpn(upload))}$upReality" +
                        "&type=xhttp&host=${encode(upload.getString("server_name"))}&path=${encode(upload.getString("path"))}" +
                        "&mode=$mode&downloadSettings=${encode(downloadStream.toString())}#Synthetic-identity-fixture").single()
                    val outbound = JSONObject(profile.outboundJson)
                    if (upSecurity == "tls") outbound.getJSONObject("tls")
                        .put("certificate_path", rootFiles!!.getValue("upload").absolutePath)
                    val importedDownload = outbound.getJSONObject("transport").getJSONObject("download_settings")
                    val downloadTls = importedDownload.getJSONObject("tls")
                    check(downloadTls.getString("server_name") == download.getString("server_name"))
                    if (downSecurity == "tls") {
                        check(downloadTls.getString("min_version") == "1.2" && downloadTls.getString("max_version") == "1.3")
                        downloadTls.put("certificate_path", rootFiles!!.getValue("download").absolutePath)
                    } else {
                        check(downloadTls.getJSONObject("reality").getString("public_key") == download.getString("public_key"))
                    }
                    if (rejectDownloadIdentity) {
                        if (xhttpRealityKeyRefusal) {
                            val wrongKey = download.getString("wrong_public_key")
                            check(wrongKey != download.getString("public_key"))
                            downloadTls.getJSONObject("reality").put("public_key", wrongKey)
                        } else downloadTls.put("server_name", "wrong-download.invalid")
                    }
                    profile = profile.copy(outboundJson = outbound.toString())
                }
                val config = JSONObject(SingBoxConfigFactory.build(profile, ownPackage = context.packageName)).apply {
                    bootstrapDns?.let { fixture ->
                        val dns = getJSONObject("dns")
                        val servers = dns.getJSONArray("servers")
                        val index = (0 until servers.length()).single {
                            servers.getJSONObject(it).optString("tag") == "nova-bootstrap-dns"
                        }
                        servers.put(index, JSONObject().put("type", "udp").put("tag", "nova-bootstrap-dns")
                            .put("server", "127.0.0.1").put("server_port", fixture.port).put("detour", "direct"))
                    }
                    if (xhttpCoreTrace) {
                        val file = java.io.File(context.cacheDir, "synthetic-xhttp-$caseIndex-${java.util.UUID.randomUUID()}.log")
                        traces += file
                        put("log", JSONObject().put("level", "debug").put("output", file.absolutePath).put("timestamp", true))
                    }
                }.toString()
                context.startForegroundService(android.content.Intent(context, NovaVpnService::class.java)
                    .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                    .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "XHTTP $mode fixture"))
                waitUntil("XHTTP $mode start") { VpnRuntime.state.value.connected || VpnRuntime.state.value.error != null }
                check(VpnRuntime.state.value.connected) { VpnRuntime.state.value.error.orEmpty() }
                val first = routedClientResult(ipv6 = xhttpIpv6First)
                val second = routedClientResult(ipv6 = !xhttpIpv6First)
                val ipv4 = if (xhttpIpv6First) second else first
                val ipv6 = if (xhttpIpv6First) first else second
                if (rejectDownloadIdentity) {
                    check(ipv4 != "ok" && ipv6 != "ok") { "Incorrect download identity was accepted" }
                    sendStatus(0, Bundle().apply { putString("stream", if (xhttpRealityKeyRefusal) "XHTTP download Reality key refusal: IPv4/IPv6 blocked, no working fallback PASS\n" else "XHTTP download TLS identity refusal: IPv4/IPv6 blocked, no working fallback PASS\n") })
                } else {
                    check(ipv4 == "ok") { "XHTTP $mode IPv4 echo failed" }
                    check(ipv6 == "ok") { "XHTTP $mode IPv6 echo failed" }
                    sendStatus(0, Bundle().apply { putString("stream", "XHTTP $mode: separate-UID IPv4/IPv6 TUN echo PASS\n") })
                }
                bootstrapDns?.let { dns ->
                    check(dns.queriesFor(downloadHostname) > 0) { "Download did not use fresh bootstrap DNS" }
                    if (xhttpBootstrapCase == "domain-domain") check(dns.queriesFor(uploadHostname) > 0) {
                        "Upload did not use fresh bootstrap DNS"
                    }
                    sendStatus(0, Bundle().apply { putString("stream", "XHTTP bootstrap $xhttpBootstrapCase: download=${dns.queriesFor(downloadHostname)} upload=${dns.queriesFor(uploadHostname)} PASS\n") })
                }
                context.startService(android.content.Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP))
                waitUntil("XHTTP stop") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
            }
            result.putString("stream", if (xhttpRealityKeyRefusal)
                "XHTTP download Reality control/refusal/control passed: correct keys work before and after, wrong download key blocks IPv4/IPv6 without working fallback.\n"
                else if (xhttpTlsRefusal)
                "XHTTP download TLS control/refusal/control passed: correct identities work before and after, wrong download identity blocks IPv4/IPv6 without working fallback.\n"
                else if (xhttpDownlinkOnly)
                "XHTTP download_settings Android/Xray passed: imported packet-up/stream-up/auto, separate download pool, IPv4/IPv6, separate UID, real VpnService.\n"
                else "XHTTP Android/Xray interoperability passed: four modes, IPv4/IPv6, separate UID, real VpnService.\n")
            outcome = Activity.RESULT_OK
        } catch (error: Throwable) {
            result.putString("stream", "XHTTP smoke failed: ${error.javaClass.simpleName}: ${error.message}\n")
        } finally {
            runCatching { context.startService(android.content.Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP)) }
            runCatching { waitUntil("XHTTP final stop") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting } }
            activity?.let { window -> runOnMainSync { window.finish() } }
            traces.forEach { file ->
                try {
                    if (file.exists()) sendStatus(0, Bundle().apply {
                        putString("stream", "SYNTHETIC_CORE_TRACE ${file.name}\n" + file.readText().takeLast(32768) + "\nEND_SYNTHETIC_CORE_TRACE\n")
                    })
                } finally { file.delete() }
            }
            bootstrapFixtures.forEach { it.close() }
            certificates.forEach { it.delete() }
        }
        finish(outcome, result)
    }

    private fun verifyAutomaticDiagnosticOwnership(config: String, profile: ImportedProfile, preset: String, engine: VpnEngine) {
        val context = targetContext
        fun serviceOwner(): Long = (NovaVpnService::class.java.getDeclaredField("RUNTIME_OWNER").apply {
            isAccessible = true
        }.get(null) as java.util.concurrent.atomic.AtomicLong).get()
        val queue = NovaVpnService::class.java.getDeclaredField("NATIVE_QUEUE").apply { isAccessible = true }
            .get(null) as java.util.concurrent.ExecutorService
        for (scenario in listOf("apply", "rollback", "superseded", "stop")) {
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            queue.execute { entered.countDown(); check(release.await(30, java.util.concurrent.TimeUnit.SECONDS)) }
            check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val request = VpnUserRequests.gate.begin()
            val owner = serviceOwner()
            check(owner > 0)
            var pause: AutoCloseable? = null
            try {
                val submitted = if (scenario == "rollback") "{invalid-automatic-fixture" else config
                check(AutomaticProbePause.dispatch(request, owner) {
                    check(VpnUserRequests.start(context, PreparedVpnConfiguration(submitted, "Synthetic auto", profile.id, preset),
                        request, automatic = true))
                })
                waitUntil("automatic request queued") { VpnApplyRequests.tracker.results.value[request]?.status == ApplyStatus.PENDING }
                pause = AutomaticProbePause.acquire()
                check(request in AutomaticProbePause.pending.value)
                check(!AutomaticProbePause.dispatch("forbidden-diagnostic-$request", owner) { error("Automatic dispatch during diagnostic ownership") })
                var manual: String? = null
                if (scenario == "stop") {
                    VpnUserRequests.stop(context)
                    waitUntil("stop acknowledgement before native cleanup") {
                        VpnApplyRequests.tracker.results.value[request]?.status == ApplyStatus.CANCELLED
                    }
                    check(request in AutomaticProbePause.pending.value) { "STOP acknowledged before queue cleanup released diagnostic ownership" }
                } else if (scenario == "superseded") {
                    manual = VpnUserRequests.gate.begin()
                    check(VpnUserRequests.start(context, PreparedVpnConfiguration(config, "Synthetic manual", profile.id, preset), manual))
                    waitUntil("manual request supersedes automatic") {
                        VpnApplyRequests.tracker.results.value[request]?.status == ApplyStatus.SUPERSEDED
                    }
                    check(request in AutomaticProbePause.pending.value) { "Supersession released ownership before old native work drained" }
                }
                release.countDown()
                waitUntil("automatic ownership settled: $scenario") { request !in AutomaticProbePause.pending.value }
                when (scenario) {
                    "stop" -> waitUntil("native stop complete") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
                    "superseded" -> waitUntil("manual native apply") {
                        VpnApplyRequests.tracker.results.value[manual]?.status == ApplyStatus.APPLIED
                    }
                    else -> {
                        val expected = if (scenario == "rollback") ApplyStatus.ROLLED_BACK else ApplyStatus.APPLIED
                        check(VpnApplyRequests.tracker.results.value[request]?.status == expected) { "Ownership released before $expected" }
                    }
                }
                check(AutomaticProbePause.paused)
                if (scenario != "stop") check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok") {
                    "Automatic diagnostic fence damaged actual UID routes"
                }
                sendStatus(0, Bundle().apply { putString("stream", "PASS automatic/diagnostic ownership $engine/$scenario: native queue, terminal acknowledgement, retained UID routes\n") })
            } finally {
                release.countDown()
                pause?.close()
            }
        }
    }

    private fun waitUntil(label: String = "condition", condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 15000
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() < deadline) {
                "Timed out: $label. " + VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value)
            }
            android.os.SystemClock.sleep(100)
        }
    }

    private fun routedClientResult(ipv6: Boolean = false, site: Boolean = false, expectPhysical: Boolean = false): String {
        val context = targetContext
        val nonce = java.util.UUID.randomUUID().toString()
        val answer = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val physical = java.util.concurrent.atomic.AtomicBoolean(false)
        val networkSummary = java.util.concurrent.atomic.AtomicReference("")
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.getStringExtra("nonce") == nonce) {
                    physical.set(intent.getBooleanExtra("physicalDefault", false))
                    lastProbeNetworkHandle = intent.getLongExtra("activeNetworkHandle", 0)
                    networkSummary.set(intent.getStringExtra("networkSummary").orEmpty())
                    answer.set(intent.getStringExtra("result"))
                }
            }
        }
        val filter = android.content.IntentFilter("${context.packageName}.REALITY_SMOKE_RESULT")
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        else { @Suppress("DEPRECATION") context.registerReceiver(receiver, filter) }
        try {
            context.startActivity(android.content.Intent().setComponent(android.content.ComponentName(
                "${context.packageName}.test", RealityProbeClientActivity::class.java.name))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra("nonce", nonce).putExtra("ipv6", ipv6).putExtra("site", site)
                .putExtra("expectPhysical", expectPhysical).putExtra("useNetworkCallbacks", probeUseNetworkCallbacks))
            waitUntil("client result ipv6=$ipv6 site=$site") { answer.get() != null }
            sendStatus(0, Bundle().apply { putString("stream",
                "Separate UID: ipv6=$ipv6 site=$site expectPhysical=$expectPhysical result=${answer.get()} ${networkSummary.get()}\n") })
            check(!expectPhysical || physical.get()) { "Excluded UID still sees VPN as its default network" }
            return answer.get()!!
        } finally { context.unregisterReceiver(receiver) }
    }
}
