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
    private var lastHttp3Diagnostic=""
    private var multiTuicQuarantineOnly=false
    private var multiNativeQuarantineOnly=false
    private var multiQuarantineOnly=false
    private var multiActiveCatalogOnly=false
    private var multiMissingDefaultOnly=false
    private var multiRetiredDefaultOnly=false
    private var multiUnknownOwnerIpv6Only=false
    private var multiUnknownOwnerOnly=false
    private var multiQuicSiteOnly=false
    private var multiQuicOnly=false
    private var http3PhysicalOnly=false
    private var multiVisibleFirstOnly=false
    private val visibleFirstProofs=AtomicInteger()
    private var multiXudpBurstOnly=false
    private var multiXudpOnly=false
    private var multiUdpOnly=false
    private var multiDirectQuicOnly=false
    private var multiDirectTraceOnly=false
    private var lastTraceIpHash=""
    private var physicalTraceIpHash=""
    private var multiDirectHost:String?=null
    private var multiDirectPort=0
    private var multiPostWriteFaultOnly=false
    private var multiSaveFaultOnly=false
    private var multiQueuedCatalogOnly=false
    private var multiStaleCatalogOnly=false
    private var multiCancelOnly=false
    private var multiRollbackOnly=false
    private var multiLiveReloadOnly=false
    private var multiConcurrentOnly=false
    private var multiExitOnly=false
    private var multiProcessPhase: String? = null
    private var multiWifiRecoveryOnly = false
    private var multiCellularHandover = false
    private var multiXhttpOnly = false
    private var multiTxtDnsOnly = false
    private var multiSharedUidOnly = false
    private val sharedUidFirst = "com.nova.vpn.routingprobe.shared.first"
    private val sharedUidSecond = "com.nova.vpn.routingprobe.shared.second"
    private var startAdmissionOnly=false
    private var startTransportOnly = false
    private var deleteAllStoreOnly = false
    private var deleteAllOnly = false
    private var xrayReferencePhase: String? = null
    private var xrayReferencePort = 18089
    private var xrayReferenceDirectory: String? = null
    private var liveSubscriptionAudit = false
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
    private var xhttpLanHost: String? = null
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
    private var physicalNetworkMonitorOnly = false
    private var diagnosticsUiOnly = false
    private var transferCrashPhase: String? = null
    private var subscriptionSchedulePhase: String? = null
    private var serverLibraryStyle: String? = null
    private var serverLibraryFontScale: Float? = null
    private var serverLibraryTheme = ThemeMode.DARK
    private var diagnosticsTheme = ThemeMode.LIGHT
    override fun onCreate(arguments: Bundle?) {
        multiQuarantineOnly=arguments?.getString("multiQuarantineOnly")=="true"
        multiTuicQuarantineOnly=arguments?.getString("multiTuicQuarantineOnly")=="true"
        check(!multiTuicQuarantineOnly || multiQuarantineOnly)
        multiNativeQuarantineOnly=arguments?.getString("multiNativeQuarantineOnly")=="true"
        check(!multiNativeQuarantineOnly || multiQuarantineOnly && !multiTuicQuarantineOnly)
        multiActiveCatalogOnly=arguments?.getString("multiActiveCatalogOnly")=="true"
        multiMissingDefaultOnly=arguments?.getString("multiMissingDefaultOnly")=="true"
        check(!multiQuarantineOnly || multiActiveCatalogOnly)
        check(!multiMissingDefaultOnly || multiActiveCatalogOnly && !multiQuarantineOnly)
        multiRetiredDefaultOnly=arguments?.getString("multiRetiredDefaultOnly")=="true"
        multiUnknownOwnerOnly=arguments?.getString("multiUnknownOwnerOnly")=="true"
        multiUnknownOwnerIpv6Only=arguments?.getString("multiUnknownOwnerIpv6Only")=="true"
        check(!multiUnknownOwnerIpv6Only || multiUnknownOwnerOnly)
        multiQuicSiteOnly=arguments?.getString("multiQuicSiteOnly")=="true"
        multiQuicOnly=arguments?.getString("multiQuicOnly")=="true"
        check(!multiQuicSiteOnly || multiQuicOnly)
        http3PhysicalOnly=arguments?.getString("http3PhysicalOnly")=="true"
        multiVisibleFirstOnly=arguments?.getString("multiVisibleFirstOnly")=="true"
        multiXudpBurstOnly=arguments?.getString("multiXudpBurstOnly")=="true"
        multiXudpOnly=arguments?.getString("multiXudpOnly")=="true"
        check(!multiXudpBurstOnly || multiXudpOnly)
        multiUdpOnly=arguments?.getString("multiUdpOnly")=="true"
        check(!multiXudpOnly || multiUdpOnly)
        multiDirectQuicOnly=arguments?.getString("multiDirectQuicOnly")=="true"
        check(!multiDirectQuicOnly || (multiQuicOnly && !multiQuicSiteOnly))
        multiDirectTraceOnly=arguments?.getString("multiDirectTraceOnly")=="true"
        multiDirectHost=arguments?.getString("multiDirectHost")
        multiDirectPort=arguments?.getString("multiDirectPort")?.toIntOrNull()?:0
        if(multiDirectHost!=null)check(multiDirectHost!!.matches(Regex("192[.]168[.]0[.]([1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])")) && multiDirectPort in 1024..65535)
        multiPostWriteFaultOnly=arguments?.getString("multiPostWriteFaultOnly")=="true"
        multiSaveFaultOnly=arguments?.getString("multiSaveFaultOnly")=="true"
        multiQueuedCatalogOnly=arguments?.getString("multiQueuedCatalogOnly")=="true"
        multiStaleCatalogOnly=arguments?.getString("multiStaleCatalogOnly")=="true"
        multiCancelOnly=arguments?.getString("multiCancelOnly")=="true"
        multiRollbackOnly=arguments?.getString("multiRollbackOnly")=="true"
        multiLiveReloadOnly=arguments?.getString("multiLiveReloadOnly")=="true"
        multiConcurrentOnly=arguments?.getString("multiConcurrentOnly")=="true"
        multiExitOnly=arguments?.getString("multiExitOnly")=="true"
        multiProcessPhase=arguments?.getString("multiProcessPhase")
        multiWifiRecoveryOnly = arguments?.getString("multiWifiRecoveryOnly") == "true"
        multiCellularHandover = arguments?.getString("multiCellularHandover") == "true"
        multiXhttpOnly = arguments?.getString("multiXhttpOnly") == "true"
        multiTxtDnsOnly = arguments?.getString("multiTxtDnsOnly") == "true"
        multiSharedUidOnly = arguments?.getString("multiSharedUidOnly") == "true"
        startAdmissionOnly=arguments?.getString("startAdmissionOnly")=="true"
        startTransportOnly = arguments?.getString("startTransportOnly") == "true"
        serverLibraryFontScale = arguments?.getString("serverLibraryFontScale")?.let { value ->
            value.toFloat().also { require(it == 1f || it == 2f) }
        }
        physicalNetworkMonitorOnly = arguments?.getString("physicalNetworkMonitorOnly") == "true"
        deleteAllStoreOnly = arguments?.getString("deleteAllStoreOnly") == "true"
        deleteAllOnly = arguments?.getString("deleteAllOnly") == "true"
        xrayReferencePhase = arguments?.getString("xrayReferencePhase")
        xrayReferenceDirectory = arguments?.getString("xrayReferenceDirectory")
        xrayReferencePort = arguments?.getString("xrayReferencePort")?.toInt() ?: 18089
        check(xrayReferencePhase == null || xrayReferencePhase in setOf("prepare", "probe", "cleanup", "stage-tcp", "stage-grpc"))
        liveSubscriptionAudit = arguments?.getString("liveSubscriptionAudit") == "true"
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
        xhttpLanHost = arguments?.getString("xhttpLanHost")?.also { host ->
            val parts = host.split('.').map { it.toIntOrNull() ?: -1 }
            check(xhttpDownlinkOnly && parts.size == 4 && parts.all { it in 0..255 } &&
                parts.joinToString(".") == host &&
                (parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) ||
                    (parts[0] == 192 && parts[1] == 168)))
        }
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
        if(http3PhysicalOnly) {
            val result=Bundle()
            try {
                check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
                result.putString("stream","PASS physical HTTP3 "+Http3PhysicalSmoke.run(this)+"\n")
                finish(Activity.RESULT_OK,result)
            } catch(error:Throwable) {
                result.putString("stream","FAIL physical HTTP3: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED,result)
            }
            return
        }
        if(startTransportOnly || startAdmissionOnly) {
            val result=Bundle()
            try { if(startAdmissionOnly)StartTransportSmoke.rejectUnownedAutomatic(this) else StartTransportSmoke.run(this);result.putString("stream",if(startAdmissionOnly)"PASS production start refusal\n" else "PASS isolated start transport\n");finish(Activity.RESULT_OK,result) }
            catch(error:Throwable) {result.putString("stream","FAIL start transport: ${error.message}\n");finish(Activity.RESULT_CANCELED,result)}
            return
        }

        xrayReferencePhase?.let { phase ->
            val result = Bundle()
            try {
                LiveXrayReferenceSmoke.run(this, phase, xrayReferencePort, xrayReferenceDirectory)
                result.putString("stream", "PASS reference phase completed; inspect individual outcomes\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL reference phase: ${error.javaClass.simpleName}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (deleteAllOnly || deleteAllStoreOnly) {
            val result = Bundle()
            try {
                if (deleteAllStoreOnly) DeleteAllProfilesSmoke.runStore(this) else DeleteAllProfilesSmoke.run(this)
                result.putString("stream", if (deleteAllStoreOnly)
                    "PASS delete all store: encrypted persistence, late HTTP 200/304/401, generation boundary, corruption guard, idempotence, preserved settings\n"
                    else "PASS delete all configurations: cancel, encrypted persistence, late refresh, request invalidation, preserved settings, corruption guard, reopen\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL delete all configurations: ${error.javaClass.simpleName}: ${error.message}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (liveSubscriptionAudit) {
            val result = Bundle()
            try {
                LiveSubscriptionAuditSmoke.run(this)
                result.putString("stream", "PASS live subscription audit completed; inspect aggregate outcomes\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL live subscription audit: ${error.javaClass.simpleName}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
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
                ServerLibraryUiSmoke.run(this, style, serverLibraryTheme, serverLibraryFontScale)
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
                result.putString("stream", "FAIL transfer crash phase $phase: ${error.javaClass.simpleName}: ${error.message}\n${error.stackTraceToString().lineSequence().take(8).joinToString("\n")}\n")
                finish(Activity.RESULT_CANCELED, result)
            }
            return
        }
        if (physicalNetworkMonitorOnly) {
            val result = Bundle()
            try {
                PhysicalNetworkMonitorSmoke.run(targetContext)
                result.putString("stream", "PASS physical monitor lifecycle: close-before-start, duplicate start, 40 registration races\n")
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("stream", "FAIL physical monitor lifecycle: ${error.javaClass.simpleName}: ${error.message}\n")
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
            check(multiProcessPhase == null || (multiProcessPhase in setOf("cut", "resume") &&
                multiExitOnly && !multiConcurrentOnly && !multiLiveReloadOnly && !multiWifiRecoveryOnly &&
                !multiUdpOnly && !multiQuicOnly && isolatedEngine == VpnEngine.SING_BOX &&
                targetContext.packageName == "com.nova.vpn.android.usbtest.completionpreview"))
            check(!(multiExitOnly && dozeOnly) || (!multiWifiRecoveryOnly && !multiConcurrentOnly &&
                !multiLiveReloadOnly && !multiUdpOnly && !multiQuicOnly && multiProcessPhase == null &&
                isolatedEngine == VpnEngine.SING_BOX &&
                targetContext.packageName == "com.nova.vpn.android.usbtest.completionpreview"))
            check(!multiCellularHandover || multiWifiRecoveryOnly)
            check(!multiWifiRecoveryOnly || (multiExitOnly && !multiConcurrentOnly &&
                !multiLiveReloadOnly && isolatedEngine == VpnEngine.SING_BOX))
            check(!multiSharedUidOnly || (multiExitOnly && !multiConcurrentOnly && !multiLiveReloadOnly &&
                !multiWifiRecoveryOnly && !multiXhttpOnly && isolatedEngine == VpnEngine.SING_BOX))
            check(!multiXhttpOnly || (multiExitOnly && !multiConcurrentOnly && !multiLiveReloadOnly &&
                !multiWifiRecoveryOnly && isolatedEngine == VpnEngine.SING_BOX))
            check(!multiNativeQuarantineOnly || (multiExitOnly && !multiConcurrentOnly && !multiLiveReloadOnly &&
                !multiWifiRecoveryOnly && !multiUdpOnly && !multiQuicOnly && isolatedEngine == VpnEngine.SING_BOX &&
                targetContext.packageName == "com.nova.vpn.android.usbtest.completionpreview"))
            check(!multiMissingDefaultOnly || (multiExitOnly && !multiConcurrentOnly && !multiLiveReloadOnly &&
                !multiWifiRecoveryOnly && !multiXhttpOnly && !multiTxtDnsOnly && !multiSharedUidOnly &&
                !multiQuicOnly && !multiUdpOnly && isolatedEngine == VpnEngine.SING_BOX &&
                targetContext.packageName == "com.nova.vpn.android.usbtest.completionpreview"))
            check(!multiTxtDnsOnly || (multiExitOnly && !multiConcurrentOnly && !multiLiveReloadOnly &&
                !multiWifiRecoveryOnly && !multiXhttpOnly && !multiSharedUidOnly && isolatedEngine == VpnEngine.SING_BOX &&
                targetContext.packageName == "com.nova.vpn.android.usbtest.completionpreview"))
            check(!(recoveryOnly || multiWifiRecoveryOnly) || allowWifiToggle) { "Recovery Wi-Fi test requires a wired device and explicit allowWifiToggle=true; TCP ADB may disconnect." }
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
            if (multiProcessPhase != null) result.putString("stack", failure.stackTraceToString().lineSequence().take(8).joinToString("\n"))
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun runVpnSmoke() {
        val context = targetContext
        check(android.net.VpnService.prepare(context) == null) { "Grant ACTIVATE_VPN to the preview package before TUN smoke" }
        val routingStore = AppRoutingStore(context)
        val processPhase = multiProcessPhase
        val fixtureForeground = if (processPhase != null || (multiExitOnly && dozeOnly)) startActivitySync(
            android.content.Intent(context, StorageTestActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ) else null
        val processReceipt = java.io.File(context.filesDir, "active-tun-process-cut.json")
        val processBackup = java.io.File(context.filesDir, "active-tun-process-original.nova")
        val previousCut = if (processPhase == "resume") JSONObject(processReceipt.readText()) else null
        if (processPhase == "cut") {
            check(!processReceipt.exists() && !processBackup.exists())
            java.io.FileOutputStream(processBackup).use {
                it.write(FullTransfer.export(context, "Synthetic-active-tun-process-2026").toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
        } else if (previousCut != null) check(processBackup.exists())
        val originalRouting = routingStore.loadPresets()
        val originalSelected=SelectedProfileStore(context).load()
        val originalProfiles=if(multiExitOnly)ProfileVault(context).loadSnapshot() else null
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
        val activity = fixtureForeground ?: startActivitySync(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        if(multiExitOnly)runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val tlsFixture = TestTlsFixture(context)
        tlsFixture.serverSocket(previousCut?.getInt("firstPort") ?: 0).use { listener ->
            val firstDoh=if(multiExitOnly)TestDohFixture(tlsFixture,multiQuicSiteOnly,"first".takeIf { multiTxtDnsOnly }) else null
            val secondDoh=if(multiExitOnly)TestDohFixture(tlsFixture,multiQuicSiteOnly,"second".takeIf { multiTxtDnsOnly }) else null
            val quicUploads=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
            val quicDownloads=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
            val quicActive=AtomicInteger()
            val xudpDatagrams=AtomicInteger();val xudpKeeps=AtomicInteger()
            val udpCommands=java.util.concurrent.ConcurrentHashMap<Int,AtomicInteger>()
            val udpExitPackets=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
            val traceExitPackets=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
            val dnsExitPackets=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
            val secondListener=if(multiExitOnly)tlsFixture.serverSocket(previousCut?.getInt("secondPort") ?: 0) else null
            val endpointPackets=java.util.concurrent.ConcurrentHashMap<Int,AtomicInteger>()
            val pairBarrier=java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch?>(null)
            val exitPackets=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
            val packets = AtomicInteger()
            val sitePackets = AtomicInteger()
            val acceptedPeers = AtomicInteger()
            val tlsCompletions = AtomicInteger()
            val fixtureFailure = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val acceptors = listOfNotNull(listener,secondListener).map { endpoint -> thread(isDaemon = true) {
                while (!endpoint.isClosed) runCatching {
                    val peer = endpoint.accept()
                    acceptedPeers.incrementAndGet()
                    thread(isDaemon = true) {
                        runCatching { peer.use peerUse@{ socket ->
                            socket.soTimeout = 7000
                            (socket as javax.net.ssl.SSLSocket).startHandshake()
                            tlsCompletions.incrementAndGet()
                            val input = java.io.DataInputStream(socket.getInputStream())
                            check(input.readUnsignedByte() == 0)
                            val exitUuid=ByteArray(16);input.readFully(exitUuid)
                            val exitKey=exitUuid.joinToString(""){"%02x".format(it.toInt() and 255)}
                            input.readFully(ByteArray(input.readUnsignedByte()))
                            val vlessCommand=input.readUnsignedByte();check(vlessCommand==1 || (multiUdpOnly && vlessCommand==2) || ((multiXudpOnly || multiQuicOnly) && vlessCommand==3))
                            if(vlessCommand==3 && multiQuicOnly) {
                                check(exitKey==if(endpoint===listener)"11111111111141118111111111111111" else "22222222222242228222222222222222") {"QUIC UUID/endpoint pair differs"}
                                quicActive.incrementAndGet()
                                try {
                                    TestQuicRelay.run(context,socket,input,
                                        {quicUploads.computeIfAbsent(exitKey){AtomicInteger()}.incrementAndGet()},
                                        {quicDownloads.computeIfAbsent(exitKey){AtomicInteger()}.incrementAndGet()})
                                } catch(closed:java.io.EOFException) { /* Native closes completed UDP flow on stop/reload. */ }
                                finally {quicActive.decrementAndGet()}
                                return@peerUse
                            }
                            if(vlessCommand==3) {
                                repeat(if(multiXudpBurstOnly)3 else 1) { index ->
                                    val headerSize=input.readUnsignedShort();check(headerSize in 8..264)
                                    val header=ByteArray(headerSize);input.readFully(header)
                                    val frame=java.io.DataInputStream(java.io.ByteArrayInputStream(header))
                                    check(frame.readUnsignedShort()==0 && frame.readUnsignedByte()==(if(index==0)1 else 2) && frame.readUnsignedByte()==1 && frame.readUnsignedByte()==2)
                                    val port=frame.readUnsignedShort();val family=frame.readUnsignedByte()
                                    val address=ByteArray(when(family){1->4;3->16;else->error("XUDP fixture requires resolved address")});frame.readFully(address)
                                    check(frame.available()==0 && port==4433 && (family==1 && address.contentEquals(byteArrayOf(198.toByte(),51,100,7)) || family==3 && address.contentEquals(java.net.InetAddress.getByName("2001:db8::7").address)))
                                    val size=input.readUnsignedShort();check(size==14)
                                    val payload=ByteArray(size);input.readFully(payload)
                                    val expected=if(multiXudpBurstOnly)"nova-udp-%05d".format(java.util.Locale.ROOT,index) else "nova-udp-smoke"
                                    check(String(payload,Charsets.US_ASCII)==expected)
                                    if(index==0) {
                                        pairBarrier.get()?.let { barrier ->
                                            sendStatus(0,Bundle().apply{putString("stream","Concurrent XUDP arrival UUID=$exitKey port=${endpoint.localPort} family=$family\n")})
                                            barrier.countDown();check(barrier.await(10,java.util.concurrent.TimeUnit.SECONDS)) {"XUDP peers did not overlap"}
                                        }
                                        udpCommands.computeIfAbsent(3){AtomicInteger()}.incrementAndGet()
                                        udpExitPackets.computeIfAbsent(exitKey){AtomicInteger()}.incrementAndGet()
                                    } else xudpKeeps.incrementAndGet()
                                    xudpDatagrams.incrementAndGet()
                                    sendStatus(0,Bundle().apply{putString("stream","XUDP datagram UUID=$exitKey index=$index family=$family received\n")})
                                    java.io.DataOutputStream(socket.getOutputStream()).apply {
                                        if(index==0)write(byteArrayOf(0,0))
                                        writeShort(4);writeShort(0);writeByte(2);writeByte(1);writeShort(size);write(payload);flush()
                                        sendStatus(0,Bundle().apply{putString("stream","XUDP datagram UUID=$exitKey index=$index family=$family echoed\n")})
                                    }
                                }
                                return@peerUse
                            }
                            val destinationPort=input.readUnsignedShort()
                            val type = input.readUnsignedByte()
                            val destination = ByteArray(when (type) { 1 -> 4; 2 -> input.readUnsignedByte(); 3 -> 16; else -> error("VLESS address") })
                            input.readFully(destination)
                            if(multiDirectTraceOnly && type==1 && destination.contentEquals(byteArrayOf(1,1,1,1)) && destinationPort==443)traceExitPackets.computeIfAbsent(exitKey){AtomicInteger()}.incrementAndGet()
                            socket.getOutputStream().write(byteArrayOf(0, 0))
                            if(vlessCommand==2) {
                                udpCommands.computeIfAbsent(2){AtomicInteger()}.incrementAndGet()
                                check(destinationPort==4433 && (type==1 && destination.contentEquals(byteArrayOf(198.toByte(),51,100,7)) || type==3 && destination.contentEquals(java.net.InetAddress.getByName("2001:db8::7").address)))
                                val size=input.readUnsignedShort();check(size==14)
                                val payload=ByteArray(size);input.readFully(payload);check(String(payload,Charsets.US_ASCII)=="nova-udp-smoke")
                                pairBarrier.get()?.let { barrier ->
                                    sendStatus(0,Bundle().apply{putString("stream","Concurrent UDP arrival UUID=$exitKey port=${endpoint.localPort} family=$type\n")})
                                    barrier.countDown();check(barrier.await(10,java.util.concurrent.TimeUnit.SECONDS)) {"UDP peers did not overlap"}
                                }
                                udpExitPackets.computeIfAbsent(exitKey){AtomicInteger()}.incrementAndGet()
                                java.io.DataOutputStream(socket.getOutputStream()).apply { writeShort(size);write(payload);flush() }
                                return@peerUse
                            }
                            if(multiExitOnly && type==1 && destination.contentEquals(byteArrayOf(203.toByte(),0,113,53)) && destinationPort==443) {
                                val doh=if(endpoint===listener)requireNotNull(firstDoh) else requireNotNull(secondDoh)
                                dnsExitPackets.computeIfAbsent(exitKey){AtomicInteger()}.incrementAndGet()
                                java.net.Socket("127.0.0.1",doh.port).use { upstream ->
                                    upstream.soTimeout=7000
                                    val upload=thread(isDaemon=true) {runCatching{input.copyTo(upstream.getOutputStream());upstream.shutdownOutput()}}
                                    try {upstream.getInputStream().copyTo(socket.getOutputStream())}
                                    finally {upstream.close();upload.join(1000)}
                                }
                                return@peerUse
                            }
                            val payload = ByteArray(14)
                            input.readFully(payload)
                            val count = payload.size
                            if (count > 0) {
                                val text = String(payload, 0, count, Charsets.US_ASCII)
                                if (text == "nova-tun-smoke") {
                                    pairBarrier.get()?.let {barrier->
                                        sendStatus(0,Bundle().apply{putString("stream","Concurrent arrival UUID=$exitKey port=${endpoint.localPort} elapsed=${android.os.SystemClock.elapsedRealtime()}\n")})
                                        barrier.countDown();check(barrier.await(10,java.util.concurrent.TimeUnit.SECONDS)){"Concurrent peers did not overlap; remaining=${barrier.count}"}
                                    }
                                    packets.incrementAndGet()
                                    endpointPackets.computeIfAbsent(endpoint.localPort){AtomicInteger()}.incrementAndGet()
                                    exitPackets.computeIfAbsent(exitKey){AtomicInteger()}.incrementAndGet()
                                    if (type == 2 && String(destination, Charsets.US_ASCII) == "native.test") sitePackets.incrementAndGet()
                                }
                                socket.getOutputStream().write(payload, 0, count)
                                socket.getOutputStream().flush()
                            }
                        } }.onFailure { fixtureFailure.set(it.javaClass.simpleName + ": " + it.message) }
                    }
                }
            } }
            try {
                val profile = ImportedProfile(id = if (processPhase != null) "fixture-process-first" else java.util.UUID.randomUUID().toString(),
                    name = "Synthetic TUN", protocol = "vless", server = "127.0.0.1", port = listener.localPort,
                    outboundJson = JSONObject().put("type", "vless").put("server", "127.0.0.1")
                        .put("server_port", listener.localPort).put("uuid", "11111111-1111-4111-8111-111111111111").apply { if(multiUdpOnly && !multiXudpOnly)put("packet_encoding","") }
                        .put("tls", JSONObject().put("enabled", true).put("server_name", "localhost")
                            .put("certificate_path", tlsFixture.certificatePath)).toString())
                val config = SingBoxConfigFactory.build(profile, ownPackage = context.packageName)
                fun start(config: String, burst: Int = 1, presetId: String = "fixture-routing", prepared:PreparedVpnConfiguration?=null,preparedSettings:AppRoutingSettings?=null, stagePrepared: Boolean = true) {
                    val previousRevision = VpnRuntime.state.value.sessionRevision
                    var lastRequest = ""
                    repeat(burst) {
                        lastRequest = VpnUserRequests.gate.begin()
                        if (liveReloadOnly || (prepared!=null && stagePrepared)) routingStore.stage(presetId, lastRequest,settings=preparedSettings,selection=prepared?.let{RoutingSelectionChange(it.profileId)})
                        check(if(prepared!=null)VpnUserRequests.start(context,prepared,lastRequest) else
                            VpnUserRequests.start(context,config,"Synthetic TUN",lastRequest,profileId=profile.id,routingPresetId=presetId))
                    }
                    waitUntil("startup") { (VpnRuntime.state.value.connected && VpnRuntime.state.value.sessionRevision > previousRevision &&
                        VpnRuntime.state.value.appliedConfiguration?.requestId == lastRequest) || (VpnRuntime.state.value.error != null && VpnApplyRequests.tracker.results.value[lastRequest]?.status==ApplyStatus.FAILED) }
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
                if(multiDirectHost!=null)check(routedClientResult(expectPhysical=true,lanFixture=true)=="ok") {"LAN physical control failed; do not infer DIRECT from a failed connection"}
                if(multiDirectTraceOnly) {
                    check(routedClientResult(expectPhysical=true,externalTrace=true)=="https-ok") {"External TLS physical control failed"}
                    physicalTraceIpHash=lastTraceIpHash;check(physicalTraceIpHash.matches(Regex("[a-f0-9]{64}")))
                }
                val testPackage = "${context.packageName}.test"
                if(multiExitOnly) {
                    check(isolatedEngine==VpnEngine.SING_BOX)
                    AppPreferencesStore(context).setVpnEngine(VpnEngine.SING_BOX)
                    val second=profile.copy(id=if (processPhase != null) "fixture-process-second" else "fixture-second-exit",name="Second authenticated exit",port=requireNotNull(secondListener).localPort,
                        outboundJson=JSONObject(profile.outboundJson).put("server_port",secondListener.localPort).put("uuid","22222222-2222-4222-8222-222222222222").toString())
                    if (previousCut == null) {
                        ProfileVault(context).updateSnapshot { current -> current.importManual(listOf(profile,second)) }
                    } else {
                        val stored = ProfileVault(context).load().associateBy { it.id }
                        check(stored[profile.id] == profile && stored[second.id] == second)
                    }
                    fun count(key:String)=exitPackets[key]?.get()?:0
                    val firstKey="11111111111141118111111111111111"
                    val secondKey="22222222222242228222222222222222"
                    fun withFixtureDoh(json: String) = JSONObject(json).apply {
                        val servers = getJSONObject("dns").getJSONArray("servers")
                        for (index in 0 until servers.length()) {
                            val server = servers.getJSONObject(index)
                            val tag = server.getString("tag")
                            if (multiTxtDnsOnly && tag == "nova-bootstrap-dns") {
                                server.put("type", "hosts")
                                continue
                            }
                            val directFixture = multiTxtDnsOnly && tag == "dns-nova-direct"
                            if (!directFixture && !tag.startsWith("dns-nova-exit-")) continue
                            server.put("server", if (directFixture) "127.0.0.1" else "203.0.113.53")
                                .put("server_port", if (directFixture) requireNotNull(firstDoh).port else 443)
                                .put("tls", JSONObject().put("enabled", true).put("server_name", "localhost")
                                    .put("certificate_path", tlsFixture.certificatePath).put("alpn", org.json.JSONArray().put("http/1.1")))
                        }
                    }.toString()

                    if (multiTxtDnsOnly) {
                        val companion = "com.nova.vpn.routingprobe"
                        val pm = context.packageManager
                        val uids = listOf(context.packageName, testPackage, companion).map { pm.getApplicationInfo(it, 0).uid }
                        check(uids.distinct().size == uids.size) { "DNS probes must have distinct UIDs" }
                        val resolverFirst = requireNotNull(firstDoh)
                        val resolverSecond = requireNotNull(secondDoh)
                        val preset = "fixture-multi-txt"
                        val site = "probe-${java.util.UUID.randomUUID()}.native.test"
                        val blockSite = "probe-${java.util.UUID.randomUUID()}.native.test"
                        var revision = 100L
                        fun applyDnsPolicy(appTarget: ServerRouteTarget) {
                            val settings = AppRoutingSettings(ipv6Enabled = true,
                                multiServer = MultiServerSettings(revision++, profile.id, listOf(
                                    MultiServerRule(packageName = testPackage, target = appTarget),
                                    MultiServerRule(domain = site, target = ServerRouteTarget(ServerRouteKind.PROFILE, profile.id)),
                                    MultiServerRule(domain = blockSite, target = ServerRouteTarget(ServerRouteKind.PROFILE, second.id))
                                )))
                            val prepared = PreparedVpnConfiguration.prepare(context, profile, settings, preset)
                            val config = withFixtureDoh(prepared.config)
                            val bound = prepared.copy(config = config,
                                exits = RouteExitBindings.capture(requireNotNull(settings.multiServer), ProfileVault(context).load(), config),
                                commitBinding = RouteCommitBinding.capture(settings, profile.id, config))
                            routingStore.updatePresets { current ->
                                if (current.presets.any { it.id == preset }) current
                                else current.copy(presets = current.presets + RoutingPreset(preset, "Synthetic DNS split", settings))
                            }
                            start(config, presetId = preset, prepared = bound, preparedSettings = settings)
                            check(VpnRuntime.state.value.appliedConfiguration?.policy == bound.policy)
                        }
                        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                        fun vpnHandle() = connectivity.allNetworks.singleOrNull { network ->
                            connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                        }?.networkHandle
                        fun trial(client: String, host: String, expected: Set<String>, firstDelta: Int = 0, secondDelta: Int = 0) {
                            val beforeFirst = resolverFirst.queriesFor(host)
                            val beforeSecond = resolverSecond.queriesFor(host)
                            val handle = checkNotNull(vpnHandle()) { "Expected one fixture VPN" }
                            val answer = routedClientResult(siteHost = host, clientPackage = client, txt = true)
                            check(answer in expected && lastProbeNetworkHandle == handle && vpnHandle() == handle) { "Unexpected TXT result: $answer" }
                            check(resolverFirst.queriesFor(host) - beforeFirst == firstDelta &&
                                resolverSecond.queriesFor(host) - beforeSecond == secondDelta) { "Unexpected DNS exit for $host" }
                        }

                        applyDnsPolicy(ServerRouteTarget(ServerRouteKind.PROFILE, second.id))
                        val shared = "probe-${java.util.UUID.randomUUID()}.native.test"
                        trial(testPackage, shared, setOf("dns-txt-second"), secondDelta = 1)
                        trial(companion, shared, setOf("dns-txt-first"), firstDelta = 1)
                        trial(testPackage, site, setOf("dns-txt-first"), firstDelta = 1)
                        listener.close()
                        trial(companion, "probe-${java.util.UUID.randomUUID()}.native.test",
                            setOf("dns-rcode-2", "EOFException@dns-txt", "SocketTimeoutException@dns-txt", "SocketException@dns-txt"))
                        trial(testPackage, "probe-${java.util.UUID.randomUUID()}.native.test", setOf("dns-txt-second"), secondDelta = 1)
                        stop()

                        applyDnsPolicy(ServerRouteTarget(ServerRouteKind.BLOCK))
                        trial(testPackage, blockSite, setOf("dns-rcode-5"))
                        stop()

                        applyDnsPolicy(ServerRouteTarget(ServerRouteKind.PROFILE, "missing-dns-exit"))
                        trial(testPackage, "probe-${java.util.UUID.randomUUID()}.native.test", setOf("dns-rcode-5"))
                        stop()
                        sendStatus(0, Bundle().apply { putString("stream", "Native multi TXT DNS per UID, site override and no fallback PASS\n") })
                        return@use
                    }

                    if (multiXhttpOnly) {
                        // The existing synthetic Xray echo fixture is exposed through a checked ADB reverse.
                        val xhttp = ProfileLinkParser.parseText(
                            "vless://11111111-1111-4111-8111-111111111111@127.0.0.1:38123?type=xhttp&path=%2Fnova-fixture&mode=packet-up#Multi-XHTTP-fixture"
                        ).single()
                        ProfileVault(context).updateSnapshot { it.importManual(listOf(xhttp)) }
                        val site = "probe-${java.util.UUID.randomUUID()}.native.test"
                        val settings = AppRoutingSettings(ipv6Enabled = true, multiServer = MultiServerSettings(100, profile.id, listOf(
                            MultiServerRule(packageName = testPackage, target = ServerRouteTarget(ServerRouteKind.PROFILE, xhttp.id)),
                            MultiServerRule(domain = site, target = ServerRouteTarget(ServerRouteKind.PROFILE, profile.id))
                        )))
                        val preset = "fixture-multi-xhttp"
                        val prepared = PreparedVpnConfiguration.prepare(context, profile, settings, preset)
                        check(requireNotNull(prepared.exits).entries.keys == setOf(profile.id, xhttp.id))
                        val config = withFixtureDoh(prepared.config)
                        val bound = prepared.copy(config = config,
                            exits = RouteExitBindings.capture(requireNotNull(settings.multiServer), ProfileVault(context).load(), config),
                            commitBinding = RouteCommitBinding.capture(settings, profile.id, config))
                        routingStore.updatePresets { current ->
                            current.copy(presets = current.presets + RoutingPreset(preset, "Synthetic XHTTP split", settings))
                        }
                        start(config, presetId = preset, prepared = bound, preparedSettings = settings)
                        val applied = requireNotNull(VpnRuntime.state.value.appliedConfiguration)
                        check(applied.policy == bound.policy)
                        val plainBefore = count(firstKey)
                        val unusedBefore = count(secondKey)
                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok") { "Assigned XHTTP TCP echo failed" }
                        check(count(firstKey) == plainBefore && count(secondKey) == unusedBefore) { "XHTTP app used a plain fixture exit" }
                        val plainDns = requireNotNull(firstDoh)
                        check(plainDns.queriesFor(site) == 0)
                        check(routedClientResult(site = true, siteHost = site) == "ok") { "Plain site override failed" }
                        check(count(firstKey) == plainBefore + 1 && count(secondKey) == unusedBefore && plainDns.queriesFor(site) > 0)
                        check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration == applied)
                        stop()
                        sendStatus(0, Bundle().apply { putString("stream", "Native multi XHTTP IPv4 IPv6 app and plain VLESS site/DNS override PASS\n") })
                        return@use
                    }
                    if (multiSharedUidOnly) {
                        val pm = context.packageManager
                        fun uidOf(name: String) = runCatching { pm.getApplicationInfo(name, 0).uid }.getOrNull()
                        val sharedUid = requireNotNull(uidOf(sharedUidFirst))
                        check(sharedUid != android.os.Process.myUid() && sharedUid != uidOf(testPackage))
                        fun awaitMembers(expected: Set<String>, phase: String) {
                            val deadline = android.os.SystemClock.elapsedRealtime() + 120_000
                            while (pm.getPackagesForUid(sharedUid)?.toSet() != expected) {
                                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Shared UID membership did not reach $phase" }
                                android.os.SystemClock.sleep(100)
                            }
                        }
                        check(uidOf(sharedUidSecond) == null && pm.getPackagesForUid(sharedUid)?.toSet() == setOf(sharedUidFirst))
                        val site = "probe-${java.util.UUID.randomUUID()}.native.test"
                        val settings = AppRoutingSettings(ipv6Enabled = true, multiServer = MultiServerSettings(100, profile.id, listOf(
                            MultiServerRule(packageName = sharedUidFirst, target = ServerRouteTarget(ServerRouteKind.PROFILE, second.id)),
                            MultiServerRule(domain = site, target = ServerRouteTarget(ServerRouteKind.PROFILE, second.id))
                        )))
                        val preset = "fixture-shared-uid"
                        val prepared = PreparedVpnConfiguration.prepare(context, profile, settings, preset)
                        val config = withFixtureDoh(prepared.config)
                        val bound = prepared.copy(config = config,
                            exits = RouteExitBindings.capture(requireNotNull(settings.multiServer), ProfileVault(context).load(), config),
                            commitBinding = RouteCommitBinding.capture(settings, profile.id, config))
                        routingStore.updatePresets { current ->
                            current.copy(presets = current.presets + RoutingPreset(preset, "Synthetic shared UID", settings))
                        }
                        start(config, presetId = preset, prepared = bound, preparedSettings = settings)
                        val active = VpnRuntime.state.value.appliedConfiguration
                        val revision = VpnRuntime.state.value.sessionRevision
                        fun unchanged() = VpnRuntime.state.value.let {
                            it.connected && it.appliedConfiguration == active && it.sessionRevision == revision
                        }
                        val firstBefore = count(firstKey)
                        val secondBefore = count(secondKey)
                        check(routedClientResult(clientPackage = sharedUidFirst) == "ok")
                        val vpnHandle = lastProbeNetworkHandle
                        check(vpnHandle != 0L && count(firstKey) == firstBefore && count(secondKey) == secondBefore + 1)
                        sendStatus(0, Bundle().apply { putString("stream", "SHARED_UID_READY install second\n") })
                        awaitMembers(setOf(sharedUidFirst, sharedUidSecond), "installed")
                        check(uidOf(sharedUidSecond) == sharedUid && unchanged())
                        for (client in listOf(sharedUidFirst, sharedUidSecond)) {
                            for (ipv6 in listOf(false, true)) {
                                val result = routedClientResult(ipv6 = ipv6, clientPackage = client)
                                check(result != "ok" && result != "NetworkNotReady" && lastProbeNetworkHandle == vpnHandle)
                            }
                        }
                        check(routedClientResult(site = true, siteHost = site, clientPackage = sharedUidFirst)
                            .let { it != "ok" && it != "NetworkNotReady" } && lastProbeNetworkHandle == vpnHandle)
                        check(count(firstKey) == firstBefore && count(secondKey) == secondBefore + 1)
                        check(routedClientResult() == "ok" && lastProbeNetworkHandle == vpnHandle)
                        check(count(firstKey) == firstBefore + 1 && count(secondKey) == secondBefore + 1 && unchanged())
                        sendStatus(0, Bundle().apply { putString("stream", "SHARED_UID_READY uninstall second\n") })
                        awaitMembers(setOf(sharedUidFirst), "removed")
                        check(uidOf(sharedUidSecond) == null && unchanged())
                        check(routedClientResult(clientPackage = sharedUidFirst) == "ok" && lastProbeNetworkHandle == vpnHandle)
                        check(count(firstKey) == firstBefore + 1 && count(secondKey) == secondBefore + 2 && unchanged())
                        stop()
                        sendStatus(0, Bundle().apply { putString("stream", "Native shared UID install conflicts blocked and removal restores exact exit PASS\n") })
                        return@use
                    }
                    if (processPhase != null) {
                        val site = previousCut?.getString("resumeSite") ?: "probe-${java.util.UUID.randomUUID()}.native.test"
                        val resumeSite = previousCut?.getString("resumeSite") ?: "probe-${java.util.UUID.randomUUID()}.native.test"
                        val settings = if (previousCut == null) AppRoutingSettings(ipv6Enabled = true,
                            multiServer = MultiServerSettings(1, profile.id, listOf(
                                MultiServerRule(packageName = testPackage, target = ServerRouteTarget(ServerRouteKind.PROFILE, second.id)),
                                MultiServerRule(domain = site, target = ServerRouteTarget(ServerRouteKind.PROFILE, profile.id)),
                                MultiServerRule(domain = resumeSite, target = ServerRouteTarget(ServerRouteKind.PROFILE, profile.id)))))
                            else routingStore.loadPresets().active.settings
                        val preset = "fixture-process-routing"
                        val policy = requireNotNull(RoutePolicyIdentity.capture(settings))
                        if (previousCut == null) {
                            routingStore.updatePresets { current ->
                                check(current.presets.none { it.id == preset })
                                current.copy(presets = current.presets + RoutingPreset(preset, "Process-cut fixture", settings))
                            }
                        } else {
                            check(routingStore.loadPresets().activeId == preset && routingStore.loadPresets().pending == null)
                            check(SelectedProfileStore(context).load() == profile.id)
                            check(policy.encode() == previousCut.getString("policy"))
                        }
                        val saved = if (previousCut == null) PreparedVpnConfiguration.prepare(context, profile, settings, preset)
                            else PreparedVpnConfiguration.prepareSelected(context)
                        val native = withFixtureDoh(saved.config)
                        val prepared = PreparedVpnConfiguration(native, "Synthetic TUN", profile.id, preset, policy,
                            RouteExitBindings.capture(requireNotNull(settings.multiServer), ProfileVault(context).load(), native),
                            RouteCommitBinding.capture(settings, profile.id, native))
                        start(native, presetId = preset, prepared = prepared, preparedSettings = settings, stagePrepared = previousCut == null)
                        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
                        val tun = requireNotNull(cm.allNetworks.singleOrNull { network ->
                            cm.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                        }).networkHandle
                        if (previousCut != null) check(tun != previousCut.getLong("tunHandle"))
                        check(routedClientResult() == "ok" && lastProbeNetworkHandle == tun)
                        check(routedClientResult(ipv6 = true) == "ok" && lastProbeNetworkHandle == tun)
                        check(count(firstKey) == 0 && count(secondKey) == 2)
                        check(routedClientResult(site = true, siteHost = site) == "ok" && lastProbeNetworkHandle == tun)
                        check(count(firstKey) == 1 && count(secondKey) == 2) {
                            "Process fixture wrong site exit: first=${count(firstKey)} second=${count(secondKey)}"
                        }
                        val siteDnsFirst = requireNotNull(firstDoh).queriesFor(site)
                        val siteDnsSecond = requireNotNull(secondDoh).queriesFor(site)
                        check(siteDnsFirst > 0 && siteDnsSecond == 0) {
                            "Process fixture wrong site DNS: first=$siteDnsFirst second=$siteDnsSecond"
                        }
                        val committed = routingStore.loadPresets()
                        check(committed.activeId == preset && committed.pending == null) {
                            "Process fixture routing preset did not settle"
                        }
                        check(RoutePolicyIdentity.capture(committed.active.settings) == policy &&
                            SelectedProfileStore(context).load() == profile.id) {
                            "Process fixture committed route or selection differs"
                        }
                        val pid = android.os.Process.myPid()
                        if (previousCut == null) {
                            val receipt = JSONObject().put("pid", pid).put("tunHandle", tun)
                                .put("firstPort", listener.localPort).put("secondPort", requireNotNull(secondListener).localPort)
                                .put("resumeSite", resumeSite).put("policy", policy.encode())
                            java.io.FileOutputStream(processReceipt).use {
                                it.write(receipt.toString().toByteArray(Charsets.UTF_8))
                                it.fd.sync()
                            }
                            android.os.Process.killProcess(pid)
                            error("Physical process cut failed")
                        }
                        check(pid != previousCut.getInt("pid"))
                        stop()
                        sendStatus(0, Bundle().apply { putString("stream", "Native active process cut restored exact saved app/site/DNS exits PASS\n") })
                        return@use
                    }
                    val companion="com.nova.vpn.routingprobe"
                    if(multiConcurrentOnly) {
                        val pm=context.packageManager
                        check(pm.getApplicationInfo(companion,0).uid!=pm.getApplicationInfo(testPackage,0).uid) {"Probe UIDs must differ"}
                    }
                    val policyCases=mutableListOf(false to second.id,false to profile.id,true to profile.id,false to "blocked-app",false to "missing-app")
                    if(multiQuicSiteOnly)policyCases.add(4,false to "blocked-quic-site")
                    for((useDefault,target) in policyCases) {
                        val blockQuicSite=target=="blocked-quic-site"
                        val missingApp=target=="missing-app"
                        val blockApp=target=="blocked-app"
                        val caseName=if(blockQuicSite)"site-block" else if(missingApp)"missing-app" else if(blockApp)"app-block" else if(useDefault)"default" else if(target==second.id)"app-second" else "app-first"
                        sendStatus(0,Bundle().apply{putString("stream","Native multi case $caseName START\n")})
                        val udpSiteHost="probe-${java.util.UUID.randomUUID()}.native.test"
                        val siteHost="probe-${java.util.UUID.randomUUID()}.native.test"
                        val recoverySites = List(if (multiCellularHandover) 2 else if (multiWifiRecoveryOnly || dozeOnly) 1 else 0) {
                            "probe-${java.util.UUID.randomUUID()}.native.test"
                        }
                        val blockedHost="probe-${java.util.UUID.randomUUID()}.native.test"
                        val beforeFirst=count(firstKey);val beforeSecond=count(secondKey)
                        val firstPortBefore=endpointPackets[listener.localPort]?.get()?:0
                        val secondPortBefore=endpointPackets[secondListener.localPort]?.get()?:0
                        if(missingApp) {
                            ProfileVault(context).updateSnapshot{it.copy(manuallyOwned=it.manuallyOwned-second.id)}
                            check(ProfileVault(context).load().single{it.id==second.id}.retiredFromSubscription) {"Lost ownership did not retire profile"}
                        }
                        val settings=AppRoutingSettings(ipv6Enabled=true,multiServer=MultiServerSettings(100,profile.id,listOfNotNull(
                            if(multiConcurrentOnly)MultiServerRule(packageName=companion,target=ServerRouteTarget(ServerRouteKind.PROFILE,profile.id)) else null,
                            if(useDefault)null else MultiServerRule(packageName=testPackage,target=if(blockApp)ServerRouteTarget(ServerRouteKind.BLOCK) else ServerRouteTarget(ServerRouteKind.PROFILE,if(missingApp || blockQuicSite)second.id else target)),
                            MultiServerRule(domain=siteHost,target=ServerRouteTarget(ServerRouteKind.PROFILE,if(missingApp || target==second.id)profile.id else second.id)),
                            if(multiUdpOnly)MultiServerRule(domain=udpSiteHost,target=ServerRouteTarget(ServerRouteKind.PROFILE,if(missingApp || target==second.id)profile.id else second.id)) else null,
                            if(multiQuicSiteOnly)MultiServerRule(domain="one.one.one.one",target=if(blockQuicSite)ServerRouteTarget(ServerRouteKind.BLOCK) else ServerRouteTarget(ServerRouteKind.PROFILE,if(missingApp || target==second.id)profile.id else second.id)) else null,
                            MultiServerRule(domain=blockedHost,target=ServerRouteTarget(ServerRouteKind.BLOCK))) +
                            recoverySites.map { MultiServerRule(domain=it,target=ServerRouteTarget(ServerRouteKind.PROFILE,profile.id)) }))
                        check(settings.multiServer!!.rules.any{it.packageName==testPackage} != useDefault) {"Default case accidentally contains app assignment"}
                        val catalogPrepared=PreparedVpnConfiguration.prepare(context,profile,settings,"fixture-multi")
                        check(catalogPrepared.profileId==profile.id && catalogPrepared.policy==RoutePolicyIdentity.capture(settings)) {"Catalog preparation identity differs"}
                        val generatedConfig=catalogPrepared.config
                        // Controlled HTTPS endpoint remains tunneled through each original detour.
                        val nativeConfig = withFixtureDoh(generatedConfig)
                        val policy=requireNotNull(RoutePolicyIdentity.capture(settings))
                        val prepared=PreparedVpnConfiguration(nativeConfig,"Synthetic TUN",profile.id,"fixture-multi",
                            policy,RouteExitBindings.capture(requireNotNull(settings.multiServer),ProfileVault(context).load(),nativeConfig),
                            RouteCommitBinding.capture(settings,profile.id,nativeConfig))
                        if(routingStore.loadPresets().presets.none{it.id=="fixture-multi"})routingStore.updatePresets { current ->
                            current.copy(presets=current.presets+RoutingPreset("fixture-multi","Synthetic multi",settings)) }
                        if(multiLiveReloadOnly && caseName!="app-second")check(VpnRuntime.state.value.connected) {"Previous session unexpectedly disconnected"}
                        start(nativeConfig,presetId="fixture-multi",prepared=prepared,preparedSettings=settings)
                        check(VpnRuntime.state.value.appliedConfiguration?.policy==policy){"Prepared policy was not applied"}
                        check(routingStore.loadPresets().activeId=="fixture-multi" && routingStore.loadPresets().pending==null)
                        check(SelectedProfileStore(context).load()==profile.id){"Prepared selection was not committed"}
                        if(multiQuicOnly) {
                            check(!multiConcurrentOnly)
                            waitUntil("previous QUIC relay cleanup") { quicActive.get()==0 }
                            val upFirst=quicUploads[firstKey]?.get()?:0;val upSecond=quicUploads[secondKey]?.get()?:0
                            val downFirst=quicDownloads[firstKey]?.get()?:0;val downSecond=quicDownloads[secondKey]?.get()?:0
                            val dnsFirst=requireNotNull(firstDoh).queriesFor("one.one.one.one");val dnsSecond=requireNotNull(secondDoh).queriesFor("one.one.one.one")
                            val allowed=!blockApp && !blockQuicSite && (!missingApp || multiQuicSiteOnly)
                            val routeFirst=if(multiQuicSiteOnly)missingApp || target==second.id else target==profile.id
                            check((routedClientResult(http3=true)=="h3-ok")==allowed) {"Actual HTTP3 app decision differs for $caseName"}
                            val proof=JSONObject(lastHttp3Diagnostic.substringAfter("HTTP3 not verified: "))
                            check(proof.getBoolean("sameDefaultNetwork") && !proof.getBoolean("physicalExpected"))
                            if(allowed)check(proof.getString("protocol")=="h3" && proof.getInt("status")==200 && proof.getBoolean("serverReportsHttp3") && !proof.getBoolean("cached"))
                            else check(!proof.isNull("networkErrorCode") && proof.getInt("networkErrorCode") in setOf(5,6,7,8,10) && !proof.isNull("error")) {"Denial is not a verified connection error: $proof"}
                            if(allowed && routeFirst) {
                                check((quicUploads[firstKey]?.get()?:0)>upFirst && (quicDownloads[firstKey]?.get()?:0)>downFirst)
                                check((quicUploads[secondKey]?.get()?:0)==upSecond && (quicDownloads[secondKey]?.get()?:0)==downSecond)
                            } else if(allowed) {
                                check((quicUploads[secondKey]?.get()?:0)>upSecond && (quicDownloads[secondKey]?.get()?:0)>downSecond)
                                check((quicUploads[firstKey]?.get()?:0)==upFirst && (quicDownloads[firstKey]?.get()?:0)==downFirst)
                            } else check((quicUploads[firstKey]?.get()?:0)==upFirst && (quicUploads[secondKey]?.get()?:0)==upSecond &&
                                (quicDownloads[firstKey]?.get()?:0)==downFirst && (quicDownloads[secondKey]?.get()?:0)==downSecond)
                            if(multiQuicSiteOnly) {
                                check(proof.getString("host")=="one.one.one.one")
                                val a=requireNotNull(firstDoh).queriesFor("one.one.one.one")-dnsFirst
                                val b=requireNotNull(secondDoh).queriesFor("one.one.one.one")-dnsSecond
                                if(allowed && routeFirst)check(a>0 && b==0) else if(allowed)check(b>0 && a==0) else check(a==0 && b==0)
                                sendStatus(0,Bundle().apply{putString("stream","QUIC DNS counters case=$caseName first=$a second=$b\n")})
                            }
                            check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==policy)
                            sendStatus(0,Bundle().apply{putString("stream","QUIC counters case=$caseName upFirst=${(quicUploads[firstKey]?.get()?:0)-upFirst} upSecond=${(quicUploads[secondKey]?.get()?:0)-upSecond} downFirst=${(quicDownloads[firstKey]?.get()?:0)-downFirst} downSecond=${(quicDownloads[secondKey]?.get()?:0)-downSecond}\n")})
                            sendStatus(0,Bundle().apply{putString("stream","Native actual HTTP3 ${if(multiQuicSiteOnly)"site" else "app"} case $caseName PASS\n")})
                        }
                        if(blockQuicSite) {
                            val a=count(firstKey);val b=count(secondKey)
                            check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok" && count(firstKey)==a && count(secondKey)==b+2) {"Site block also blocked the allowed app"}
                            if(!multiLiveReloadOnly)stop()
                            sendStatus(0,Bundle().apply{putString("stream","Native multi case site-block PASS\n")})
                            continue
                        }
                        if(multiUdpOnly) {
                            fun u(id:String)=udpExitPackets[id]?.get()?:0
                            val a=u(firstKey);val b=u(secondKey)
                            for(v6 in listOf(false,true))check((routedClientResult(ipv6=v6,udp=true)=="udp-ok")==(!blockApp && !missingApp)) {"UDP app decision differs for $caseName ipv6=$v6"}
                            check(u(firstKey)==a+(if(!blockApp && !missingApp && target==profile.id)2 else 0))
                            check(u(secondKey)==b+(if(!blockApp && !missingApp && target==second.id)2 else 0))
                            val c=u(firstKey);val d=u(secondKey)
                            check((routedClientResult(site=true,siteHost=udpSiteHost,udp=true)=="udp-ok")==!blockApp)
                            val siteFirst=missingApp || target==second.id
                            check(u(firstKey)==c+(if(!blockApp && siteFirst)1 else 0))
                            check(u(secondKey)==d+(if(!blockApp && !siteFirst)1 else 0))
                            val e=u(firstKey);val f=u(secondKey)
                            check(routedClientResult(site=true,siteHost=blockedHost,udp=true)!="udp-ok" && u(firstKey)==e && u(secondKey)==f)
                            check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==policy)
                            sendStatus(0,Bundle().apply{putString("stream","Native UDP case $caseName PASS\n")})
                        }
                        if(missingApp) {
                            check(prepared.exits?.entries?.containsKey(second.id)==false) {"Retired profile retained native exit"}
                            check(routedClientResult()!="ok" && routedClientResult(ipv6=true)!="ok") {"Unavailable app profile fell back"}
                            check(count(firstKey)==beforeFirst && count(secondKey)==beforeSecond) {"Unavailable assignment emitted echo through another exit"}
                            check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==beforeFirst+1) {"Independent available site rule stopped working"}
                            if(!multiLiveReloadOnly)stop()
                            sendStatus(0,Bundle().apply{putString("stream","Native multi case missing-app PASS\n")})
                            continue
                        }
                        if(blockApp) {
                            val active=VpnRuntime.state.value.appliedConfiguration?.requestId
                            check(routedClientResult()!="ok" && routedClientResult(ipv6=true)!="ok" &&
                                routedClientResult(site=true,siteHost=siteHost)!="ok") {"Blocked app reached data endpoint"}
                            check(count(firstKey)==beforeFirst && count(secondKey)==beforeSecond) {"Blocked app emitted echo data"}
                            check(requireNotNull(firstDoh).queriesFor(siteHost)==0 && requireNotNull(secondDoh).queriesFor(siteHost)==0) {"Blocked app caused DNS on VPN exit"}
                            check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.requestId==active)
                            if(!multiLiveReloadOnly)stop()
                            sendStatus(0,Bundle().apply{putString("stream","Native multi case app-block PASS\n")})
                            continue
                        }
                        check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                        check(count(firstKey)-beforeFirst==if(target==profile.id)2 else 0)
                        check(count(secondKey)-beforeSecond==if(target==second.id)2 else 0)
                        check((endpointPackets[listener.localPort]?.get()?:0)-firstPortBefore==if(target==profile.id)2 else 0)
                        check((endpointPackets[secondListener.localPort]?.get()?:0)-secondPortBefore==if(target==second.id)2 else 0)
                        val siteFirstBefore=count(firstKey);val siteSecondBefore=count(secondKey)
                        check(requireNotNull(firstDoh).queriesFor(siteHost)==0 && requireNotNull(secondDoh).queriesFor(siteHost)==0)
                        check(routedClientResult(site=true,siteHost=siteHost)=="ok") {"Site override echo failed"}
                        check(count(firstKey)-siteFirstBefore==if(target==second.id)1 else 0) {"Site override chose wrong first exit"}
                        check(count(secondKey)-siteSecondBefore==if(target==profile.id)1 else 0) {"Site override chose wrong second exit"}
                        check((if(target==second.id)requireNotNull(firstDoh) else requireNotNull(secondDoh)).queriesFor(siteHost)>0) {"No HTTPS DNS response through site exit"}
                        check((dnsExitPackets[if(target==second.id)firstKey else secondKey]?.get()?:0)>0) {"No tunneled DNS on site exit"}
                        check((if(target==second.id)requireNotNull(secondDoh) else requireNotNull(firstDoh)).queriesFor(siteHost)==0) {"Fresh site DNS reached wrong exit"}
                        val blockedFirstBefore=count(firstKey);val blockedSecondBefore=count(secondKey)
                        val activeRequest=VpnRuntime.state.value.appliedConfiguration?.requestId
                        check(routedClientResult(site=true,siteHost=blockedHost)!="ok") {"Blocked site reached echo"}
                        check(count(firstKey)==blockedFirstBefore && count(secondKey)==blockedSecondBefore) {"Blocked site emitted data through exit"}
                        check(requireNotNull(firstDoh).queriesFor(blockedHost)==0 && requireNotNull(secondDoh).queriesFor(blockedHost)==0) {"Blocked site reached DoH exit"}
                        check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.requestId==activeRequest) {"Blocked request disrupted active session"}
                        if ((multiWifiRecoveryOnly || dozeOnly) && target == second.id && !useDefault) {
                            val active = requireNotNull(VpnRuntime.state.value.appliedConfiguration)
                            val committed = routingStore.loadPresets().active
                            val selection = SelectedProfileStore(context).load()
                            fun verifyRecoveryRoutes(freshSite: String) {
                                check(VpnRuntime.state.value.appliedConfiguration == active)
                                check(routingStore.loadPresets().active == committed && SelectedProfileStore(context).load() == selection)
                                val firstBefore = count(firstKey)
                                val secondBefore = count(secondKey)
                                check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok")
                                check(count(firstKey) == firstBefore && count(secondKey) == secondBefore + 2) {
                                    "Recovered IPv4/IPv6 app traffic left its assigned exit"
                                }
                                check(firstDoh.queriesFor(freshSite) == 0 && secondDoh.queriesFor(freshSite) == 0)
                                check(routedClientResult(site = true, siteHost = freshSite) == "ok")
                                check(count(firstKey) == firstBefore + 1 && count(secondKey) == secondBefore + 2) {
                                    "Recovered site traffic did not override the application exit"
                                }
                                check(firstDoh.queriesFor(freshSite) > 0 && secondDoh.queriesFor(freshSite) == 0)
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration == active)
                            }
                            if (multiCellularHandover) {
                                verifyMultiExitCellularHandover { stage -> verifyRecoveryRoutes(recoverySites[stage]) }
                                sendStatus(0, Bundle().apply { putString("stream", "Native multi Wi-Fi cellular Wi-Fi retained TUN and exact app/site/DNS exits PASS\n") })
                            } else if (dozeOnly) {
                                verifyRetainedTunInDoze()
                                verifyRecoveryRoutes(recoverySites.single())
                                sendStatus(0, Bundle().apply { putString("stream", "Native multi deep idle retained TUN and exact post-wake app/site/DNS exits PASS\n") })
                            } else {
                                recoverMultiExitOverWifi()
                                verifyRecoveryRoutes(recoverySites.single())
                                sendStatus(0, Bundle().apply { putString("stream", "Native multi Wi-Fi recovery retained TUN and exact app/site/DNS exits PASS\n") })
                            }
                        }
                        if(multiActiveCatalogOnly && target==second.id && !useDefault) {
                            check(!multiConcurrentOnly && !multiQuicOnly && !multiUdpOnly)
                            val savedCatalog=ProfileVault(context).loadSnapshot()
                            val originalRoutes=routingStore.loadPresets().active
                            val originalSelection=SelectedProfileStore(context).load()
                            val initial=requireNotNull(VpnRuntime.state.value.appliedConfiguration)
                            fun awaitRefresh(previous:AppliedConfiguration):AppliedConfiguration {
                                waitUntil("automatic catalog refresh") {
                                    val now=VpnRuntime.state.value
                                    now.connected && now.appliedConfiguration?.requestId!=previous.requestId &&
                                        now.appliedConfiguration?.policy==policy &&
                                        VpnApplyRequests.tracker.results.value[now.appliedConfiguration?.requestId]?.status==ApplyStatus.APPLIED
                                }
                                check(routingStore.loadPresets().active==originalRoutes && SelectedProfileStore(context).load()==originalSelection)
                                return requireNotNull(VpnRuntime.state.value.appliedConfiguration)
                            }
                            try {
                                ProfileVault(context).updateSnapshot { it.copy(profiles=it.profiles.map {p->if(p.id==second.id)p.copy(name="Cosmetic rename") else p}) }
                                android.os.SystemClock.sleep(1600)
                                check(VpnRuntime.state.value.appliedConfiguration==initial) {"Cosmetic catalog update reloaded native configuration"}
                                val pendingToken=VpnUserRequests.gate.begin()
                                routingStore.stage("fixture-multi",pendingToken,settings=settings,selection=RoutingSelectionChange(profile.id))
                                try {
                                    ProfileVault(context).updateSnapshot {it.copy(manuallyOwned=it.manuallyOwned-second.id)}
                                    android.os.SystemClock.sleep(1600)
                                    check(VpnRuntime.state.value.appliedConfiguration==initial && routingStore.loadPresets().pending?.requestId==pendingToken) {"Catalog refresh replaced pending manual apply"}
                                } finally {routingStore.cancelPending(pendingToken)}
                                val retired=awaitRefresh(initial)
                                val a=count(firstKey);val b=count(secondKey)
                                check(routedClientResult()!="ok" && routedClientResult(ipv6=true)!="ok")
                                check(count(firstKey)==a && count(secondKey)==b) {"Retired app route fell back to available default"}
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration==retired)
                                sendStatus(0,Bundle().apply{putString("stream","Native automatic catalog retirement blocks app IPv4 IPv6 and preserves pending manual PASS\n")})
                                ProfileVault(context).restoreSnapshot(savedCatalog)
                                var restored=awaitRefresh(retired)
                                check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                                check(count(firstKey)==a && count(secondKey)==b+2)
                                android.os.SystemClock.sleep(1600)
                                check(VpnRuntime.state.value.appliedConfiguration==restored) {"Catalog refresh entered reload loop"}
                                sendStatus(0,Bundle().apply{putString("stream","Native automatic catalog restored exact app exit without reload loop PASS\n")})
                                if (multiMissingDefaultOnly) {
                                    check(context.packageManager.getApplicationInfo(companion, 0).uid !=
                                        context.packageManager.getApplicationInfo(testPackage, 0).uid)
                                    check(originalSelection == profile.id)
                                    val vault = ProfileVault(context)
                                    vault.updateSnapshot { it.withProfiles(it.profiles.filterNot { item -> item.id == profile.id }) }
                                    check(vault.load().none { it.id == profile.id })
                                    val absent = awaitRefresh(restored)
                                    check(absent.profileId == profile.id && routingStore.loadPresets().pending == null)
                                    waitUntil("missing default shown as unavailable") {
                                        VpnRuntime.state.value.exitChecks[profile.id]?.status == ExitCheckStatus.UNAVAILABLE
                                    }
                                    val firstBefore = count(firstKey)
                                    val secondBefore = count(secondKey)
                                    fun checkMissingTraffic() {
                                        check(routedClientResult(clientPackage = companion) != "ok")
                                        check(routedClientResult(clientPackage = companion, ipv6 = true) != "ok")
                                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok")
                                        check(count(firstKey) == firstBefore) { "Missing default leaked to its previous exit" }
                                    }
                                    checkMissingTraffic()
                                    check(count(secondKey) == secondBefore + 2) { "Default traffic leaked to the assigned app exit" }
                                    check(VpnRuntime.state.value.appliedConfiguration == absent)
                                    sendStatus(0, Bundle().apply { putString("stream", "Native missing default catalog refresh blocks unassigned IPv4 IPv6 and preserves assigned app PASS\n") })

                                    stop()
                                    val resumed = PreparedVpnConfiguration.prepareSelected(context)
                                    check(resumed.profileId == profile.id && resumed.policy == policy)
                                    check(resumed.exits!!.entries.keys == setOf(second.id))
                                    start(resumed.config, presetId = "fixture-multi", prepared = resumed, preparedSettings = settings)
                                    val resumedIdentity = requireNotNull(VpnRuntime.state.value.appliedConfiguration)
                                    checkMissingTraffic()
                                    check(count(secondKey) == secondBefore + 4)
                                    sendStatus(0, Bundle().apply { putString("stream", "Native saved missing default resumes without changing app assignment PASS\n") })

                                    vault.restoreSnapshot(savedCatalog)
                                    restored = awaitRefresh(resumedIdentity)
                                    check(restored.profileId == profile.id)
                                    check(routedClientResult(clientPackage = companion) == "ok")
                                    check(routedClientResult(clientPackage = companion, ipv6 = true) == "ok")
                                    check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok")
                                    check(count(firstKey) == firstBefore + 2 && count(secondKey) == secondBefore + 6)
                                    check(VpnRuntime.state.value.appliedConfiguration == restored && routingStore.loadPresets().pending == null)
                                    sendStatus(0, Bundle().apply { putString("stream", "Native exact returning default restores unassigned IPv4 IPv6 after resume PASS\n") })
                                }
                                if(multiQuarantineOnly) {
                                    fun incompatible(p:ImportedProfile):ImportedProfile = if(multiNativeQuarantineOnly)p.copy(protocol="socks",
                                        outboundJson=JSONObject().put("type","socks").put("server","127.0.0.1")
                                            .put("server_port",p.port).put("version","fixture-invalid").toString())
                                        else if(multiTuicQuarantineOnly)p.copy(protocol="tuic",
                                        outboundJson=JSONObject().put("type","tuic").put("server","127.0.0.1").put("server_port",p.port)
                                            .put("uuid","invalid-fixture-id").put("password","fixture-only")
                                            .put("tls",JSONObject().put("enabled",true).put("server_name","localhost")).toString())
                                        else p.copy(outboundJson=JSONObject(p.outboundJson).put("type","unsupported-test-only").toString())
                                    if (multiNativeQuarantineOnly) {
                                        val rejected = incompatible(second)
                                        check(MultiServerProfileAdapter.problem(rejected) == null)
                                        fun probe(version: String): String = JSONObject()
                                            .put("outbounds", JSONArray().put(JSONObject(rejected.outboundJson)
                                                .put("tag", "fixture").put("version", version)))
                                            .put("route", JSONObject().put("final", "fixture")).toString()
                                        io.nekohasekai.libbox.Libbox.checkConfig(probe("5"))
                                        check(runCatching { io.nekohasekai.libbox.Libbox.checkConfig(probe("fixture-invalid")) }.isFailure)
                                        sendStatus(0, Bundle().apply { putString("stream", "Native Android core rejects SOCKS fixture accepted by adapter PASS\n") })
                                    }
                                    if(multiTuicQuarantineOnly) {
                                        val u="11111111-1111-4111-8111-111111111111";val compact=u.replace("-","")
                                        val cases=listOf("canonical" to u,"compact" to compact,"urn" to "urn:uuid:$u","braced" to "{$u}","bracedcompact" to "{$compact}","invalid" to "not-a-uuid","short" to "1-1-1-1-1","upperurn" to "URN:UUID:$u")
                                        for((name,value) in cases) {
                                            val outbound=JSONObject().put("type","tuic").put("tag","fixture").put("server","127.0.0.1").put("server_port",443).put("uuid",value)
                                                .put("password","fixture-only").put("tls",JSONObject().put("enabled",true).put("server_name","localhost"))
                                            val config=JSONObject().put("outbounds",JSONArray().put(outbound)).put("route",JSONObject().put("final","fixture")).toString()
                                            val accepted=runCatching {io.nekohasekai.libbox.Libbox.checkConfig(config)}.isSuccess
                                            check(accepted==(name !in setOf("invalid","short","upperurn"))) {"Android TUIC UUID grammar differs: $name accepted=$accepted"}
                                            sendStatus(0,Bundle().apply {putString("stream","Android TUIC UUID check case=$name accepted=$accepted\n")})
                                        }
                                        sendStatus(0,Bundle().apply {putString("stream","Native Android TUIC UUID syntax controls PASS\n")})
                                    }

                                    val companionUid=context.packageManager.getApplicationInfo(companion,0).uid
                                    check(companionUid!=context.packageManager.getApplicationInfo(testPackage,0).uid && companionUid!=android.os.Process.myUid())
                                    val rapidQueue=NovaVpnService::class.java.getDeclaredField("NATIVE_QUEUE").apply {isAccessible=true}.get(null) as java.util.concurrent.ExecutorService
                                    val rapidEntered=java.util.concurrent.CountDownLatch(1);val rapidRelease=java.util.concurrent.CountDownLatch(1)
                                    rapidQueue.execute {rapidEntered.countDown();check(rapidRelease.await(30,java.util.concurrent.TimeUnit.SECONDS))}
                                    check(rapidEntered.await(5,java.util.concurrent.TimeUnit.SECONDS))
                                    var admitted:String?=null
                                    try {
                                        ProfileVault(context).updateSnapshot {it.copy(profiles=it.profiles.map {p->if(p.id==second.id)
                                            incompatible(p) else p})}
                                        waitUntil("first rapid catalog request admitted") {
                                            val pending=routingStore.loadPresets().pending
                                            pending!=null && VpnApplyRequests.tracker.results.value[pending.requestId]?.status==ApplyStatus.PENDING
                                        }
                                        admitted=requireNotNull(routingStore.loadPresets().pending).requestId
                                        val oldFirst=count(firstKey);val oldSecond=count(secondKey)
                                        check(routedClientResult()=="ok" && count(firstKey)==oldFirst && count(secondKey)==oldSecond+1) {"Queued refresh stopped old native engine before applying"}
                                        ProfileVault(context).updateSnapshot {it.copy(profiles=it.profiles.map {p->if(p.id==second.id)
                                            p.copy(outboundJson=JSONObject(p.outboundJson).put("connect_timeout","7s").toString()) else p})}
                                    } finally {rapidRelease.countDown()}
                                    restored=awaitRefresh(restored)
                                    check(restored.requestId==admitted) {"Rapid catalog update replaced request rather than revalidating it"}
                                    val rapidEvents=JSONObject(VpnDiagnostics.reportJson(null,settings,android.os.Build.VERSION.SDK_INT,VpnRuntime.state.value)).getJSONArray("events")
                                    check((0 until rapidEvents.length()).any {rapidEvents.getJSONObject(it).getString("code")=="catalog_refresh_reprepared"})
                                    check((0 until rapidEvents.length()).none {rapidEvents.getJSONObject(it).getString("code")=="configuration_rollback"})
                                    sendStatus(0,Bundle().apply {putString("stream","Native admitted stale catalog automatically reprepared same request latest generation PASS\n")})
                                    waitUntil("incompatible exit status") {VpnRuntime.state.value.exitChecks[second.id]?.status==ExitCheckStatus.INCOMPATIBLE}
                                    val fingerprint=RouteExitBindings.profileHash(ProfileVault(context).load().single {it.id==second.id})
                                    check(VpnRuntime.state.value.exitChecks.getValue(second.id).profileSha256==fingerprint)
                                    stop()
                                    val resumed=PreparedVpnConfiguration.prepareSelected(context)
                                    check(resumed.routingPresetId==originalRoutes.id && resumed.profileId==originalSelection && resumed.policy==policy)
                                    val resumedExits = requireNotNull(resumed.exits)
                                    if (multiNativeQuarantineOnly) {
                                        check(resumedExits.quarantined.isEmpty() && resumedExits.entries.keys == setOf(profile.id, second.id))
                                        check(routingStore.loadPresets().pending == null)
                                        val resumeToken = VpnUserRequests.gate.begin()
                                        check(VpnUserRequests.start(context, resumed, resumeToken))
                                        waitUntil("saved native-only quarantine resumes without staged edits") {
                                            VpnRuntime.state.value.connected &&
                                                VpnRuntime.state.value.appliedConfiguration?.requestId == resumeToken &&
                                                VpnApplyRequests.tracker.results.value[resumeToken]?.status == ApplyStatus.APPLIED
                                        }
                                    } else {
                                        check(resumedExits.quarantined.keys == setOf(second.id) && resumedExits.entries.keys == setOf(profile.id))
                                        start(resumed.config,presetId="fixture-multi",prepared=resumed,preparedSettings=settings)
                                    }
                                    restored=requireNotNull(VpnRuntime.state.value.appliedConfiguration)
                                    waitUntil("resumed incompatible exit status") {VpnRuntime.state.value.exitChecks[second.id]?.status==ExitCheckStatus.INCOMPATIBLE}
                                    check(routingStore.loadPresets().active==originalRoutes && SelectedProfileStore(context).load()==originalSelection)
                                    sendStatus(0,Bundle().apply {putString("stream","Native saved assignments resume quarantined exit after disconnect PASS\n")})
                                    val healthyBefore=count(firstKey);val blockedBefore=count(secondKey)
                                    check(routedClientResult()!="ok" && routedClientResult(ipv6=true)!="ok")
                                    check(count(firstKey)==healthyBefore && count(secondKey)==blockedBefore) {"Quarantined app fell back or used stale config"}
                                    check(routedClientResult(clientPackage=companion)=="ok" && routedClientResult(clientPackage=companion,ipv6=true)=="ok")
                                    check(count(firstKey)==healthyBefore+2 && count(secondKey)==blockedBefore) {"Healthy default exit failed during quarantine"}
                                    val quarantinedIdentity=restored
                                    android.os.SystemClock.sleep(1600)
                                    check(VpnRuntime.state.value.appliedConfiguration==quarantinedIdentity) {"Quarantine caused reload loop"}
                                    sendStatus(0,Bundle().apply {putString("stream","Native incompatible app exit quarantined IPv4 IPv6 healthy distinct UID default PASS\n")})
                                    ProfileVault(context).updateSnapshot {it.copy(profiles=it.profiles.map {p->if(p.id==profile.id)
                                        incompatible(p) else p})}
                                    restored=awaitRefresh(restored)
                                    waitUntil("all incompatible exit status") {VpnRuntime.state.value.exitChecks.size==2 && VpnRuntime.state.value.exitChecks.values.all {it.status==ExitCheckStatus.INCOMPATIBLE}}
                                    val blockedState=VpnRuntime.state.value
                                    check(blockedState.connected && !blockedState.trafficVerified && blockedState.trafficCheckFailed && !blockedState.trafficChecking)
                                    check(routedClientResult()!="ok" && routedClientResult(ipv6=true)!="ok")
                                    check(routedClientResult(clientPackage=companion)!="ok" && routedClientResult(clientPackage=companion,ipv6=true)!="ok")
                                    check(count(firstKey)==healthyBefore+2 && count(secondKey)==blockedBefore) {"All quarantined config used a stale exit or fallback"}
                                    check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration==restored)
                                    sendStatus(0,Bundle().apply {putString("stream","Native all incompatible exits keep TUN block both distinct UIDs IPv4 IPv6 PASS\n")})
                                    ProfileVault(context).updateSnapshot {it.copy(profiles=it.profiles.map {p->if(p.id==profile.id)profile else p})}
                                    restored=awaitRefresh(restored)
                                    waitUntil("partially repaired exit status") {VpnRuntime.state.value.exitChecks[second.id]?.status==ExitCheckStatus.INCOMPATIBLE && VpnRuntime.state.value.exitChecks[profile.id]?.status!=ExitCheckStatus.INCOMPATIBLE}
                                    check(routedClientResult()!="ok" && routedClientResult(ipv6=true)!="ok")
                                    check(routedClientResult(clientPackage=companion)=="ok" && routedClientResult(clientPackage=companion,ipv6=true)=="ok")
                                    check(count(firstKey)==healthyBefore+4 && count(secondKey)==blockedBefore)
                                    sendStatus(0,Bundle().apply {putString("stream","Native partial quarantine repair restores only matching default exit PASS\n")})
                                    ProfileVault(context).restoreSnapshot(savedCatalog)
                                    restored=awaitRefresh(restored)
                                    check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                                    check(count(firstKey)==healthyBefore+4 && count(secondKey)==blockedBefore+2)
                                    waitUntil("repaired exit status") {VpnRuntime.state.value.exitChecks[second.id]?.status!=ExitCheckStatus.INCOMPATIBLE}
                                    sendStatus(0,Bundle().apply {putString("stream","Native repaired quarantined exit restores exact app server PASS\n")})
                                    if(multiTuicQuarantineOnly)sendStatus(0,Bundle().apply {putString("stream","Native malformed TUIC UUID isolated quarantine and exact repair PASS\n")})
                                    if (multiNativeQuarantineOnly) sendStatus(0, Bundle().apply {
                                        putString("stream", "Native-only SOCKS failure isolated on automatic update and saved resume with exact repair PASS\n")
                                    })
                                }
                                val queue=NovaVpnService::class.java.getDeclaredField("NATIVE_QUEUE").apply {isAccessible=true}.get(null) as java.util.concurrent.ExecutorService
                                val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
                                queue.execute {entered.countDown();check(release.await(30,java.util.concurrent.TimeUnit.SECONDS))}
                                check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
                                var automaticToken:String?=null
                                var stopToken:String?=null
                                try {
                                    ProfileVault(context).updateSnapshot {it.copy(manuallyOwned=it.manuallyOwned-second.id)}
                                    waitUntil("automatic catalog command admitted behind native queue") {
                                        val pending=routingStore.loadPresets().pending
                                        pending!=null && pending.requestId!=restored.requestId &&
                                            VpnApplyRequests.tracker.results.value[pending.requestId]?.status==ApplyStatus.PENDING
                                    }
                                    automaticToken=requireNotNull(routingStore.loadPresets().pending).requestId
                                    check(automaticToken in AutomaticProbePause.pending.value)
                                    VpnUserRequests.stop(context)
                                    stopToken=VpnUserRequests.gate.snapshot()
                                    waitUntil("STOP cancels admitted catalog refresh") {
                                        VpnApplyRequests.tracker.results.value[automaticToken]?.status==ApplyStatus.CANCELLED && routingStore.loadPresets().pending==null
                                    }
                                } finally {release.countDown()}
                                waitUntil("catalog STOP native cleanup") {!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting && AutomaticProbePause.pending.value.isEmpty()}
                                val drained=java.util.concurrent.CountDownLatch(1);queue.execute {drained.countDown()};check(drained.await(10,java.util.concurrent.TimeUnit.SECONDS))
                                ProfileVault(context).restoreSnapshot(savedCatalog)
                                android.os.SystemClock.sleep(1800)
                                check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting && VpnUserRequests.gate.snapshot()==stopToken)
                                check(VpnApplyRequests.tracker.results.value[automaticToken]?.status==ApplyStatus.CANCELLED)
                                check(routingStore.loadPresets().active==originalRoutes && routingStore.loadPresets().pending==null && SelectedProfileStore(context).load()==originalSelection)
                                val manager=context.getSystemService(android.net.ConnectivityManager::class.java)
                                check(manager.allNetworks.none {manager.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)==true}) {"Catalog refresh recreated TUN after STOP"}
                                sendStatus(0,Bundle().apply{putString("stream","Native admitted automatic catalog STOP cancellation and no resurrection PASS\n")})
                            } finally {ProfileVault(context).restoreSnapshot(savedCatalog)}
                            start(nativeConfig,presetId="fixture-multi",prepared=prepared,preparedSettings=settings)
                        }
                        if(multiRetiredDefaultOnly && target==second.id && !useDefault) {
                            check(!multiConcurrentOnly && !multiQuicOnly && !multiUdpOnly)
                            val savedCatalog=ProfileVault(context).loadSnapshot()
                            try {
                                ProfileVault(context).updateSnapshot { it.copy(manuallyOwned=it.manuallyOwned-profile.id) }
                                val catalog=ProfileVault(context).load()
                                check(catalog.single {it.id==profile.id}.retiredFromSubscription)
                                for(retirementCase in 0..2) {
                                    val appPinned=retirementCase==0
                                    val allRetired=retirementCase==2
                                    if(allRetired)ProfileVault(context).updateSnapshot { it.copy(manuallyOwned=it.manuallyOwned-second.id) }
                                    val currentCatalog=ProfileVault(context).load()
                                    val retiredSettings=settings.copy(multiServer=settings.multiServer!!.copy(revision=108L+retirementCase,
                                        rules=settings.multiServer!!.rules.mapNotNull { rule ->
                                            when {
                                                rule.packageName==testPackage && !appPinned -> null
                                                rule.domain==siteHost -> rule.copy(target=ServerRouteTarget(ServerRouteKind.PROFILE,second.id))
                                                else -> rule
                                            }
                                        }))
                                    val anchor=requireNotNull(PreparedVpnConfiguration.selectedPrimary(currentCatalog,retiredSettings,second.id))
                                    check(anchor.id==profile.id && anchor.retiredFromSubscription)
                                    val generated=PreparedVpnConfiguration.prepare(context,anchor,retiredSettings,"fixture-multi")
                                    check(generated.exits!!.entries.keys==if(allRetired)emptySet<String>() else setOf(second.id))
                                    val config=JSONObject(generated.config).apply {
                                        val servers=getJSONObject("dns").getJSONArray("servers")
                                        for(i in 0 until servers.length()) {
                                            val server=servers.getJSONObject(i)
                                            if(server.getString("tag").startsWith("dns-nova-exit-"))server.put("server","203.0.113.53").put("server_port",443)
                                                .put("tls",JSONObject().put("enabled",true).put("server_name","localhost").put("certificate_path",tlsFixture.certificatePath).put("alpn",JSONArray().put("http/1.1")))
                                        }
                                    }.toString()
                                    val candidate=generated.copy(config=config,exits=RouteExitBindings.capture(retiredSettings.multiServer!!,currentCatalog,config),
                                        commitBinding=RouteCommitBinding.capture(retiredSettings,profile.id,config))
                                    start(config,presetId="fixture-multi",prepared=candidate,preparedSettings=retiredSettings)
                                    val a=count(firstKey);val b=count(secondKey)
                                    check((routedClientResult()=="ok")==appPinned && (routedClientResult(ipv6=true)=="ok")==appPinned)
                                    check(count(firstKey)==a && count(secondKey)==b+(if(appPinned)2 else 0)) {"Retired default escaped or independent app exit failed"}
                                    val dnsBefore=requireNotNull(firstDoh).requests.get()
                                    check((routedClientResult(site=true,siteHost=siteHost)=="ok")==!allRetired && count(firstKey)==a && count(secondKey)==b+(if(appPinned)3 else if(allRetired)0 else 1))
                                    check(firstDoh.requests.get()==dnsBefore) {"Retired default still emitted DNS"}
                                    check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==RoutePolicyIdentity.capture(retiredSettings))
                                    sendStatus(0,Bundle().apply{putString("stream",if(allRetired)"Native all retired exits IPv4 IPv6 site blocked PASS\n" else "Native retired default appPinned=$appPinned IPv4 IPv6 site exit isolation PASS\n")})
                                }
                            } finally {ProfileVault(context).restoreSnapshot(savedCatalog)}
                            start(nativeConfig,presetId="fixture-multi",prepared=prepared,preparedSettings=settings)
                            val a=count(firstKey);val b=count(secondKey)
                            check(routedClientResult()=="ok" && count(firstKey)==a && count(secondKey)==b+1)
                            sendStatus(0,Bundle().apply{putString("stream","Native retired default restored original policy PASS\n")})
                        }
                        if(multiUnknownOwnerOnly && target==second.id && !useDefault) {
                            check(multiUdpOnly && multiXudpOnly && !multiConcurrentOnly)
                            val audit=UnknownOwnerObservations.audit
                            val cursor=audit.cursor()
                            val owner=(NovaVpnService::class.java.getDeclaredField("RUNTIME_OWNER").apply{isAccessible=true}.get(null) as java.util.concurrent.atomic.AtomicLong).get()
                            check(owner>0)
                            val nonce=java.util.UUID.randomUUID().toString()
                            val reply=java.util.concurrent.atomic.AtomicReference<android.content.Intent?>(null)
                            val expectedUid=context.packageManager.getApplicationInfo(testPackage,0).uid
                            val receiver=object:android.content.BroadcastReceiver(){
                                override fun onReceive(c:android.content.Context?,intent:android.content.Intent?) {
                                    if(intent?.getStringExtra("nonce")==nonce)reply.set(intent)
                                }
                            }
                            val filter=android.content.IntentFilter(context.packageName+".CLOSED_UDP_RESULT")
                            if(android.os.Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,android.content.Context.RECEIVER_EXPORTED)
                            else {@Suppress("DEPRECATION") context.registerReceiver(receiver,filter)}
                            val a=udpExitPackets[firstKey]?.get()?:0;val b=udpExitPackets[secondKey]?.get()?:0
                            try {
                                context.startActivity(android.content.Intent().setComponent(android.content.ComponentName(testPackage,ClosedUdpProbeActivity::class.java.name))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra("nonce",nonce).putExtra("ipv6",multiUnknownOwnerIpv6Only))
                                waitUntil("closed socket proof") {reply.get()!=null}
                                val proof=requireNotNull(reply.get())
                                check(proof.getIntExtra("clientUid",-1)==expectedUid && expectedUid!=android.os.Process.myUid())
                                check(!proof.hasExtra("failure") && proof.getBooleanExtra("sent",false) && proof.getBooleanExtra("closed",false) && proof.getBooleanExtra("vpnDefault",false))
                                check(proof.getBooleanExtra("ipv6",false)==multiUnknownOwnerIpv6Only)
                                val hash=audit.fingerprint(17,requireNotNull(proof.getStringExtra("source")),proof.getIntExtra("sourcePort",0),if(multiUnknownOwnerIpv6Only)"2001:db8::7" else "198.51.100.7",4433)
                                waitUntil("actual unknown UDP owner observation") {audit.snapshot(owner).any {it.sequence>cursor && it.tupleFingerprint==hash && it.reason==UnknownOwnerAudit.Reason.UID_UNAVAILABLE}}
                                val record=audit.snapshot(owner).last {it.sequence>cursor && it.tupleFingerprint==hash}
                                android.os.SystemClock.sleep(500)
                                check((udpExitPackets[firstKey]?.get()?:0)==a && (udpExitPackets[secondKey]?.get()?:0)==b) {"Unknown owner reached a VPN exit"}
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==policy)
                                val first=count(firstKey);val secondBefore=count(secondKey)
                                check(routedClientResult()=="ok" && count(firstKey)==first && count(secondKey)==secondBefore+1)
                                sendStatus(0,Bundle().apply{putString("stream","Native closed UDP unknown owner rejected PASS ipv6=$multiUnknownOwnerIpv6Only sequence=${record.sequence} fingerprint=$hash\n")})
                            } finally {context.unregisterReceiver(receiver)}
                        }
                        if(multiDirectHost!=null && target==second.id && !useDefault) {
                            val a=count(firstKey);val b=count(secondKey)
                            check(routedClientResult(lanFixture=true)=="ok" && count(secondKey)==b+1 && count(firstKey)==a) {"LAN control was not captured by app VPN rule"}
                            val directSettings=settings.copy(multiServer=settings.multiServer!!.copy(revision=107,
                                rules=settings.multiServer!!.rules.map { rule -> if(rule.packageName==testPackage)
                                    rule.copy(target=ServerRouteTarget(ServerRouteKind.DIRECT)) else rule }))
                            val generated=PreparedVpnConfiguration.prepare(context,profile,directSettings,"fixture-multi")
                            val directConfig=JSONObject(generated.config).apply {
                                val servers=getJSONObject("dns").getJSONArray("servers")
                                for(i in 0 until servers.length()) {
                                    val server=servers.getJSONObject(i)
                                    if(server.getString("tag").startsWith("dns-nova-exit-"))server.put("server","203.0.113.53").put("server_port",443)
                                        .put("tls",JSONObject().put("enabled",true).put("server_name","localhost").put("certificate_path",tlsFixture.certificatePath).put("alpn",JSONArray().put("http/1.1")))
                                }
                            }.toString()
                            val directPrepared=generated.copy(config=directConfig,
                                exits=RouteExitBindings.capture(directSettings.multiServer!!,ProfileVault(context).load(),directConfig),
                                commitBinding=RouteCommitBinding.capture(directSettings,profile.id,directConfig))
                            start(directConfig,presetId="fixture-multi",prepared=directPrepared,preparedSettings=directSettings)
                            val c=count(firstKey);val d=count(secondKey)
                            check(routedClientResult(lanFixture=true)=="ok" && count(firstKey)==c && count(secondKey)==d) {"DIRECT app used VPN fixture"}
                            check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==c+1 && count(secondKey)==d) {"Site override stopped using VPN while app DIRECT"}
                            start(nativeConfig,presetId="fixture-multi",prepared=prepared,preparedSettings=settings)
                            val e=count(firstKey);val f=count(secondKey)
                            check(routedClientResult(lanFixture=true)=="ok" && count(firstKey)==e && count(secondKey)==f+1) {"Restored app VPN rule did not capture LAN endpoint"}
                            sendStatus(0,Bundle().apply{putString("stream","Native DIRECT positive LAN endpoint and site VPN override PASS\n")})
                        }
                        if((multiDirectTraceOnly || multiDirectQuicOnly) && target==second.id && !useDefault) {
                            val oldTrace=traceExitPackets[secondKey]?.get()?:0
                            if(multiDirectTraceOnly)check(routedClientResult(externalTrace=true)!="https-ok" && (traceExitPackets[secondKey]?.get()?:0)==oldTrace+1) {"External TLS control bypassed app VPN"}
                            val directSettings=settings.copy(multiServer=settings.multiServer!!.copy(revision=107,
                                rules=settings.multiServer!!.rules.map { rule -> if(rule.packageName==testPackage)
                                    rule.copy(target=ServerRouteTarget(ServerRouteKind.DIRECT)) else rule }))
                            val generated=PreparedVpnConfiguration.prepare(context,profile,directSettings,"fixture-multi")
                            val directConfig=JSONObject(generated.config).apply {
                                val servers=getJSONObject("dns").getJSONArray("servers")
                                for(i in 0 until servers.length()) {
                                    val server=servers.getJSONObject(i)
                                    if(server.getString("tag").startsWith("dns-nova-exit-"))server.put("server","203.0.113.53").put("server_port",443)
                                        .put("tls",JSONObject().put("enabled",true).put("server_name","localhost").put("certificate_path",tlsFixture.certificatePath).put("alpn",JSONArray().put("http/1.1")))
                                }
                            }.toString()
                            val directPrepared=generated.copy(config=directConfig,
                                exits=RouteExitBindings.capture(directSettings.multiServer!!,ProfileVault(context).load(),directConfig),
                                commitBinding=RouteCommitBinding.capture(directSettings,profile.id,directConfig))
                            start(directConfig,presetId="fixture-multi",prepared=directPrepared,preparedSettings=directSettings)
                            val c=count(firstKey);val d=count(secondKey)
                            val beforeTrace=traceExitPackets.values.sumOf { it.get() }
                            if(multiDirectTraceOnly)check(routedClientResult(externalTrace=true)=="https-ok" && lastTraceIpHash==physicalTraceIpHash && traceExitPackets.values.sumOf { it.get() }==beforeTrace) {"DIRECT external TLS not verified through physical egress"}
                            if(multiDirectQuicOnly) {
                                waitUntil("DIRECT prior QUIC cleanup") { quicActive.get()==0 }
                                val beforeUp=quicUploads.values.sumOf { it.get() };val beforeDown=quicDownloads.values.sumOf { it.get() }
                                check(routedClientResult(http3=true)=="h3-ok") {"DIRECT real HTTP3 failed: $lastHttp3Diagnostic"}
                                val proof=JSONObject(lastHttp3Diagnostic.substringAfter("HTTP3 not verified: "))
                                check(proof.getString("protocol")=="h3" && proof.getInt("status")==200 && proof.getBoolean("serverReportsHttp3") && !proof.getBoolean("cached") && proof.getBoolean("sameDefaultNetwork") && !proof.getBoolean("physicalExpected"))
                                Thread.sleep(500)
                                check(quicUploads.values.sumOf { it.get() }==beforeUp && quicDownloads.values.sumOf { it.get() }==beforeDown && quicActive.get()==0) {"DIRECT HTTP3 used VPN relay"}
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==RoutePolicyIdentity.capture(directSettings))
                                sendStatus(0,Bundle().apply{putString("stream","DIRECT HTTP3 proof=$proof uploads=0 downloads=0\n")})
                            }
                            check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==c+1 && count(secondKey)==d) {"Site override stopped using VPN while app DIRECT"}
                            start(nativeConfig,presetId="fixture-multi",prepared=prepared,preparedSettings=settings)
                            val restoredTrace=traceExitPackets[secondKey]?.get()?:0
                            if(multiDirectTraceOnly) {
                                check(routedClientResult(externalTrace=true)!="https-ok" && (traceExitPackets[secondKey]?.get()?:0)==restoredTrace+1) {"Restored app VPN did not capture external TLS"}
                                sendStatus(0,Bundle().apply{putString("stream","Native DIRECT positive external TLS and site VPN override PASS\n")})
                            }
                            if(multiDirectQuicOnly) {
                                val up=quicUploads[secondKey]?.get()?:0;val down=quicDownloads[secondKey]?.get()?:0
                                val wrongUp=quicUploads[firstKey]?.get()?:0;val wrongDown=quicDownloads[firstKey]?.get()?:0
                                check(routedClientResult(http3=true)=="h3-ok") {"Restored VPN HTTP3 failed: $lastHttp3Diagnostic"}
                                check((quicUploads[secondKey]?.get()?:0)>up && (quicDownloads[secondKey]?.get()?:0)>down && (quicUploads[firstKey]?.get()?:0)==wrongUp && (quicDownloads[firstKey]?.get()?:0)==wrongDown)
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==policy)
                                sendStatus(0,Bundle().apply{putString("stream","Native DIRECT positive real HTTP3 and restored VPN capture PASS\n")})
                            }
                        }
                        if(multiStaleCatalogOnly && target==second.id && !useDefault) {
                            val routesBefore=routingStore.loadPresets()
                            val selectionBefore=SelectedProfileStore(context).load()
                            val appliedBefore=VpnRuntime.state.value.appliedConfiguration
                            val revisionBefore=VpnRuntime.state.value.sessionRevision
                            val catalogBefore=ProfileVault(context).loadSnapshot()
                            val staleRequest=VpnUserRequests.gate.begin()
                            routingStore.stage("fixture-multi",staleRequest,settings=settings,selection=RoutingSelectionChange(profile.id))
                            try {
                                ProfileVault(context).updateSnapshot { it.copy(manuallyOwned=it.manuallyOwned-second.id) }
                                check(ProfileVault(context).load().single { it.id==second.id }.retiredFromSubscription)
                                check(VpnUserRequests.start(context,prepared,staleRequest))
                                waitUntil("stale catalog service rejection") {
                                    VpnApplyRequests.tracker.results.value[staleRequest]?.status==ApplyStatus.FAILED
                                }
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration==appliedBefore &&
                                    VpnRuntime.state.value.sessionRevision==revisionBefore) {"Stale catalog command changed active session"}
                                check(routingStore.loadPresets()==routesBefore && SelectedProfileStore(context).load()==selectionBefore) {"Stale catalog command committed routes"}
                            } finally {
                                ProfileVault(context).restoreSnapshot(catalogBefore)
                            }
                            val a=count(firstKey); val b=count(secondKey)
                            check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                            check(count(secondKey)==b+2 && count(firstKey)==a)
                            check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==a+1)
                            sendStatus(0,Bundle().apply{putString("stream","Native stale catalog admission rejected PASS\n")})
                        }
                        if(multiQueuedCatalogOnly && target==second.id && !useDefault) {
                            val routesBefore=routingStore.loadPresets()
                            val selectionBefore=SelectedProfileStore(context).load()
                            val catalogBefore=ProfileVault(context).loadSnapshot()
                            val third=second.copy(id="fixture-queued-exit",name="Queued synthetic exit",
                                outboundJson=JSONObject(second.outboundJson).put("uuid","33333333-3333-4333-8333-333333333333").toString())
                            val release=java.util.concurrent.CountDownLatch(1)
                            try {
                                ProfileVault(context).updateSnapshot { it.copy(profiles=it.profiles+third,manuallyOwned=it.manuallyOwned+third.id) }
                                val queuedSettings=settings.copy(multiServer=settings.multiServer!!.copy(revision=104,
                                    rules=settings.multiServer!!.rules.map { rule -> if(rule.packageName==testPackage)
                                        rule.copy(target=ServerRouteTarget(ServerRouteKind.PROFILE,third.id)) else rule }))
                                val queuedPrepared=PreparedVpnConfiguration.prepare(context,profile,queuedSettings,"fixture-multi")
                                check(queuedPrepared.exits!!.entries.containsKey(third.id))
                                val queue=NovaVpnService::class.java.getDeclaredField("NATIVE_QUEUE").apply { isAccessible=true }
                                    .get(null) as java.util.concurrent.ExecutorService
                                val entered=java.util.concurrent.CountDownLatch(1)
                                queue.execute { entered.countDown(); check(release.await(30,java.util.concurrent.TimeUnit.SECONDS)) }
                                check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
                                val queuedRequest=VpnUserRequests.gate.begin()
                                try {
                                    routingStore.stage("fixture-multi",queuedRequest,settings=queuedSettings,selection=RoutingSelectionChange(profile.id))
                                    check(VpnUserRequests.start(context,queuedPrepared,queuedRequest))
                                    waitUntil("queued catalog command accepted") { VpnApplyRequests.tracker.results.value[queuedRequest]?.status==ApplyStatus.PENDING }
                                    runOnMainSync { } // onStartCommand must finish admission before the catalog mutation.
                                    check(VpnApplyRequests.tracker.results.value[queuedRequest]?.status==ApplyStatus.PENDING)
                                    ProfileVault(context).updateSnapshot { it.copy(manuallyOwned=it.manuallyOwned-third.id) }
                                    check(ProfileVault(context).load().single { it.id==third.id }.retiredFromSubscription)
                                    check(VpnApplyRequests.tracker.results.value[queuedRequest]?.status==ApplyStatus.PENDING)
                                } finally { release.countDown() }
                                waitUntil("queued stale catalog rollback") { VpnApplyRequests.tracker.results.value[queuedRequest]?.status in setOf(ApplyStatus.ROLLED_BACK,ApplyStatus.FAILED,ApplyStatus.APPLIED) }
                                check(VpnApplyRequests.tracker.results.value[queuedRequest]?.status==ApplyStatus.ROLLED_BACK) {"Queued stale catalog was not rolled back"}
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==policy)
                                check(routingStore.loadPresets()==routesBefore && SelectedProfileStore(context).load()==selectionBefore)
                                val a=count(firstKey);val b=count(secondKey)
                                check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                                check(count(secondKey)==b+2 && count(firstKey)==a)
                                check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==a+1)
                                sendStatus(0,Bundle().apply{putString("stream","Native queued stale catalog rollback PASS\n")})
                            } finally {
                                release.countDown()
                                ProfileVault(context).restoreSnapshot(catalogBefore)
                            }
                        }
                        if(multiSaveFaultOnly && target==second.id && !useDefault) {
                            val routesBefore=routingStore.loadPresets()
                            val selectionBefore=SelectedProfileStore(context).load()
                            val journal=java.io.File(context.filesDir,"restore-pending.vault")
                            val obstruction=java.io.File(context.filesDir,"restore-pending.vault.new")
                            check(!journal.exists() && !java.io.File(journal.path+".bak").exists() && !obstruction.exists())
                            val candidateSettings=settings.copy(multiServer=settings.multiServer!!.copy(revision=105,
                                rules=settings.multiServer!!.rules.map { rule -> if(rule.packageName==testPackage)
                                    rule.copy(target=ServerRouteTarget(ServerRouteKind.PROFILE,profile.id)) else rule }))
                            val candidate=PreparedVpnConfiguration.prepare(context,profile,candidateSettings,"fixture-multi")
                            check(candidate.config!=nativeConfig)
                            val request=VpnUserRequests.gate.begin()
                            routingStore.stage("fixture-multi",request,settings=candidateSettings,selection=RoutingSelectionChange(profile.id))
                            check(obstruction.mkdir()) {"Could not create isolated journal write obstruction"}
                            try {
                                check(VpnUserRequests.start(context,candidate,request))
                                waitUntil("native save fault rollback") { VpnApplyRequests.tracker.results.value[request]?.status in setOf(ApplyStatus.ROLLED_BACK,ApplyStatus.FAILED,ApplyStatus.APPLIED) }
                                check(VpnApplyRequests.tracker.results.value[request]?.status==ApplyStatus.ROLLED_BACK) {"Native save fault did not roll back: ${VpnApplyRequests.tracker.results.value[request]?.status}"}
                                val events=VpnDiagnostics.exportEvents()
                                check(Regex("engine_started").findAll(events).count()>=2 && events.contains("startup_failed") && events.contains("configuration_rollback")) {"Missing actual native start/failure/rollback diagnostics: $events"}
                                check(RestoreRecoveryState.problem.value==null && !journal.exists())
                                check(routingStore.loadPresets()==routesBefore && SelectedProfileStore(context).load()==selectionBefore)
                                check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==policy)
                                val a=count(firstKey);val b=count(secondKey)
                                check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                                check(count(secondKey)==b+2 && count(firstKey)==a)
                                check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==a+1)
                                sendStatus(0,Bundle().apply{putString("stream","Native journal write failure rollback PASS\n")})
                            } finally {
                                check(!obstruction.exists() || (obstruction.isDirectory && obstruction.listFiles()!!.isEmpty() && obstruction.delete()))
                                FullTransfer.recoverPending(context)
                            }
                        }
                        if(multiPostWriteFaultOnly && target==second.id && !useDefault) {
                            val routesBefore=routingStore.loadPresets()
                            val selectionBefore=SelectedProfileStore(context).load()
                            val journal=java.io.File(context.filesDir,"restore-pending.vault")
                            val directory=java.io.File(context.dataDir,"shared_prefs")
                            val originalMode=android.system.Os.stat(directory.path).st_mode and 0xfff
                            check(!journal.exists())
                            val next=settings.copy(multiServer=settings.multiServer!!.copy(revision=106,
                                rules=settings.multiServer!!.rules.map { rule -> if(rule.packageName==testPackage)
                                    rule.copy(target=ServerRouteTarget(ServerRouteKind.PROFILE,profile.id)) else rule }))
                            val candidate=PreparedVpnConfiguration.prepare(context,profile,next,"fixture-multi")
                            val request=VpnUserRequests.gate.begin()
                            routingStore.stage("fixture-multi",request,settings=next,selection=RoutingSelectionChange(profile.id))
                            try {
                                // Keep search/write, deny directory read: preference commit can rename
                                // files, but the following journal-clear directory fsync cannot open it.
                                android.system.Os.chmod(directory.path,0xc0) // 0300, only this disposable package.
                                check(runCatching { android.system.Os.open(directory.path,android.system.OsConstants.O_RDONLY,0).also { android.system.Os.close(it) } }.exceptionOrNull() is android.system.ErrnoException)
                                check(VpnUserRequests.start(context,candidate,request))
                                waitUntil("native post-write failure") { VpnApplyRequests.tracker.results.value[request]?.status in setOf(ApplyStatus.ROLLED_BACK,ApplyStatus.FAILED,ApplyStatus.APPLIED) }
                                check(VpnApplyRequests.tracker.results.value[request]?.status==ApplyStatus.FAILED) {"Persistent directory fsync fault was not reported as failed"}
                                check(android.system.Os.stat(directory.path).st_mode and 0x1ff==0xc0)
                                check(RestoreRecoveryState.problem.value==RestoreProblem.STORAGE && journal.exists()) {"Unfinished durable recovery was lost"}
                                val preimage=synchronized(ProfileVault.lock) { requireNotNull(TransferRestoreJournal(context).read()) }
                                check(preimage.getInt("schema")==2)
                                val previous=RoutingPresets.fromJson(JSONObject(preimage.getJSONObject("routing").getJSONObject("routing_presets_v1").getString("value")))
                                check(previous.copy(pending=null)==routesBefore && previous.pending?.requestId==request)
                                val events=VpnDiagnostics.exportEvents()
                                check(events.contains("engine_started") && events.contains("startup_failed")) {"Native config did not reach commit"}
                                check(!VpnRuntime.state.value.connected) {"Uncommitted native config reported connected"}
                            } finally {
                                android.system.Os.chmod(directory.path,originalMode)
                                FullTransfer.recoverPending(context)
                            }
                            routingStore.cancelPending(request)
                            check(RestoreRecoveryState.problem.value==null && !journal.exists())
                            check(routingStore.loadPresets()==routesBefore && SelectedProfileStore(context).load()==selectionBefore)
                            start(nativeConfig,presetId="fixture-multi",prepared=prepared,preparedSettings=settings)
                            val a=count(firstKey);val b=count(secondKey)
                            check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                            check(count(secondKey)==b+2 && count(firstKey)==a)
                            check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==a+1)
                            sendStatus(0,Bundle().apply{putString("stream","Native post-write fsync failure recovery PASS\n")})
                        }
                        if(multiCancelOnly && target==second.id && !useDefault) {
                            val beforeCancel=routingStore.loadPresets()
                            val beforeSelection=SelectedProfileStore(context).load()
                            val appliedBefore=VpnRuntime.state.value.appliedConfiguration
                            val stale=VpnUserRequests.gate.begin()
                            val cancelledSettings=settings.copy(multiServer=settings.multiServer!!.copy(revision=102))
                            routingStore.stage("fixture-multi",stale,settings=cancelledSettings,selection=RoutingSelectionChange(second.id))
                            VpnUserRequests.gate.begin() // User cancels this preparation, without stopping the active VPN.
                            routingStore.cancelPending(stale)
                            check(!VpnUserRequests.start(context,prepared,stale)) {"Cancelled preparation was dispatched"}
                            check(routingStore.loadPresets()==beforeCancel && SelectedProfileStore(context).load()==beforeSelection)
                            check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration==appliedBefore)
                            val firstBefore=count(firstKey);val secondBefore=count(secondKey)
                            check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok")
                            check(count(secondKey)==secondBefore+2 && count(firstKey)==firstBefore) {"Cancelled preparation changed app exit"}
                            check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==firstBefore+1)
                            sendStatus(0,Bundle().apply{putString("stream","Native multi cancelled preparation PASS\n")})
                        }
                        if(multiRollbackOnly && target==second.id && !useDefault) {
                            waitUntil("previous tunnel health checks settled", timeoutMillis = 25000L) {
                                !VpnRuntime.state.value.trafficChecking
                            }
                            val previousUi = VpnRuntime.state.value
                            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                            val previousHandle = connectivity.allNetworks.single {
                                connectivity.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                            }.networkHandle
                            check(previousUi.connected && previousHandle != 0L)
                            val routesBefore=routingStore.loadPresets()
                            val selectionBefore=SelectedProfileStore(context).load()
                            val badSettings=settings.copy(multiServer=settings.multiServer!!.copy(revision=101))
                            val badConfig=JSONObject(nativeConfig).apply {
                                getJSONObject("route").getJSONArray("rules").put(JSONObject().put("action","invalid-fixture-action"))
                            }.toString()
                            val badRequest=VpnUserRequests.gate.begin()
                            routingStore.stage("fixture-multi",badRequest,settings=badSettings,selection=RoutingSelectionChange(profile.id))
                            val badPrepared=PreparedVpnConfiguration(badConfig,"Invalid native routing fixture",profile.id,"fixture-multi",
                                RoutePolicyIdentity.capture(badSettings),RouteExitBindings.capture(badSettings.multiServer!!,listOf(profile,second),badConfig),
                                RouteCommitBinding.capture(badSettings,profile.id,badConfig))
                            check(VpnUserRequests.start(context,badPrepared,badRequest))
                            waitUntil("multi native rollback acknowledgement") {
                                VpnApplyRequests.tracker.results.value[badRequest]?.status in setOf(ApplyStatus.ROLLED_BACK,ApplyStatus.FAILED)
                            }
                            check(VpnApplyRequests.tracker.results.value[badRequest]?.status==ApplyStatus.ROLLED_BACK) {"Native rejection did not rollback"}
                            check(VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.policy==policy)
                            check(routingStore.loadPresets()==routesBefore && SelectedProfileStore(context).load()==selectionBefore) {"Rejected candidate altered committed routes/selection"}
                            check(VpnRuntime.state.value.sessionRevision == previousUi.sessionRevision &&
                                VpnRuntime.state.value.exitChecks == previousUi.exitChecks) {"Rejected candidate restarted the previous tunnel or cleared its checks"}
                            val rollbackFirst=count(firstKey);val rollbackSecond=count(secondKey)
                            check(routedClientResult()=="ok" && routedClientResult(ipv6=true)=="ok") {"Rollback lost app traffic"}
                            check(count(secondKey)==rollbackSecond+2 && count(firstKey)==rollbackFirst)
                            check(lastProbeNetworkHandle == previousHandle) {"Rejected candidate replaced the VPN network handle"}
                            check(routedClientResult(site=true,siteHost=siteHost)=="ok" && count(firstKey)==rollbackFirst+1) {"Rollback lost site override"}
                            sendStatus(0,Bundle().apply{putString("stream","Native multi rejected candidate rollback PASS\n")})
                        }
                        if(multiConcurrentOnly && multiUdpOnly && target==second.id && !useDefault) {
                            for(family in listOf(false,true)) {
                                val a=udpExitPackets[firstKey]?.get()?:0;val b=udpExitPackets[secondKey]?.get()?:0
                                val active=VpnRuntime.state.value.appliedConfiguration
                                pairBarrier.set(java.util.concurrent.CountDownLatch(2))
                                val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
                                try {
                                    val one=pool.submit<String>{routedClientResult(ipv6=family,udp=true)}
                                    val two=pool.submit<String>{routedClientResult(ipv6=family,udp=true,clientPackage=companion)}
                                    check(one.get(25,java.util.concurrent.TimeUnit.SECONDS)=="udp-ok" && two.get(25,java.util.concurrent.TimeUnit.SECONDS)=="udp-ok") {"Concurrent UDP echo failed"}
                                    check((udpExitPackets[firstKey]?.get()?:0)==a+1 && (udpExitPackets[secondKey]?.get()?:0)==b+1)
                                    check(pairBarrier.get()?.count==0L && VpnRuntime.state.value.appliedConfiguration==active)
                                    sendStatus(0,Bundle().apply{putString("stream","Native concurrent UDP distinct UID exits ipv6=$family PASS\n")})
                                } finally {
                                    pairBarrier.set(null);pool.shutdownNow();check(pool.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS))
                                }
                            }
                        }
                        if(multiConcurrentOnly && target==second.id && !useDefault) {
                          for(pairIpv6 in listOf(false,true)) {
                            val a=count(firstKey);val b=count(secondKey)
                            val firstEndpointBefore=endpointPackets[listener.localPort]?.get()?:0
                            val secondEndpointBefore=endpointPackets[secondListener.localPort]?.get()?:0
                            pairBarrier.set(java.util.concurrent.CountDownLatch(2))
                            val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
                            try {
                                val one=pool.submit<String>{routedClientResult(ipv6=pairIpv6)}
                                val two=pool.submit<String>{routedClientResult(ipv6=pairIpv6,clientPackage=companion)}
                                val firstResult=one.get(25,java.util.concurrent.TimeUnit.SECONDS);val secondResult=two.get(25,java.util.concurrent.TimeUnit.SECONDS)
                                check(firstResult=="ok" && secondResult=="ok") {"Concurrent responses first=$firstResult second=$secondResult fixture=${fixtureFailure.get()} barrierRemaining=${pairBarrier.get()?.count}"}
                                check(count(firstKey)==a+1 && count(secondKey)==b+1) {"Concurrent UID exits differ"}
                                check((endpointPackets[listener.localPort]?.get()?:0)==firstEndpointBefore+1 &&
                                    (endpointPackets[secondListener.localPort]?.get()?:0)==secondEndpointBefore+1) {"Concurrent endpoint counts differ"}
                                check(VpnRuntime.state.value.appliedConfiguration?.requestId==activeRequest)
                                sendStatus(0,Bundle().apply{putString("stream","Native concurrent distinct UID exits ipv6=$pairIpv6 PASS\n")})
                            } finally {pairBarrier.set(null);pool.shutdownNow()}
                          }
                        }
                        if(!multiLiveReloadOnly)stop()
                        sendStatus(0,Bundle().apply{putString("stream","Native multi case $caseName PASS\n")})
                    }
                    if(multiLiveReloadOnly) {
                        stop()
                        sendStatus(0,Bundle().apply{putString("stream","Native multi connected reload sequence PASS\n")})
                    }
                    if(multiVisibleFirstOnly) {
                        check(visibleFirstProofs.get()>0)
                        sendStatus(0,Bundle().apply{putString("stream","Native visible first client verified PASS\n")})
                    }
                    if(multiXudpOnly) {
                        val expected=if(multiConcurrentOnly)14 else 10
                        check((udpCommands[3]?.get()?:0)==expected && (udpCommands[2]?.get()?:0)==0) {"Default XUDP was not used for every successful datagram"}
                        sendStatus(0,Bundle().apply{putString("stream","Native default XUDP transport PASS\n")})
                        if(multiXudpBurstOnly) {
                            check(xudpDatagrams.get()==expected*3 && xudpKeeps.get()==expected*2)
                            sendStatus(0,Bundle().apply{putString("stream","Native XUDP three ordered datagrams per session PASS\n")})
                        }
                    }
                    sendStatus(0,Bundle().apply{putString("stream","PASS native authenticated per-app exit selection IPv4/IPv6; two profiles, distinct TLS fixture ports, site overrides app, blocked site denied, catalog preparation, default fallback, app block, missing profile blocked\n")})
                    return@use
                }

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
                        check(VpnUserRequests.start(context,"{invalid-fixture","Invalid fixture",invalidRequest,
                            profileId="invalid-profile",routingPresetId="invalid-routing"))
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
                        start(config)
                        check(routedClientResult() == "ok" && routedClientResult(ipv6 = true) == "ok")
                        verifyRetainedTunInDoze()
                        sendStatus(0, Bundle().apply { putString("stream", "Doze $engine: 30 seconds deep idle, retained TUN, no restart PASS\n") })
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
                if (liveReloadOnly || multiExitOnly) routingStore.updatePresets { originalRouting.copy(pending = null) }
                if(multiExitOnly)SelectedProfileStore(context).let{if(originalSelected==null)it.clear() else it.save(originalSelected)}
                if(originalProfiles!=null) {
                    ProfileVault(context).updateSnapshot{originalProfiles}
                    check(ProfileVault(context).loadSnapshot()==originalProfiles) {"Fixture catalog restore differs"}
                }
                runOnMainSync { activity.finish() }
                firstDoh?.close();secondDoh?.close()
                secondListener?.close()
                listener.close()
                acceptors.forEach{it.join(1000)}
            }
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
        // Test-only watchdog: preserve evidence even if native close or a main-thread
        // callback blocks before Instrumentation.finish can return its result.
        val stage = java.util.concurrent.atomic.AtomicReference("entry")
        val stageSince = java.util.concurrent.atomic.AtomicLong(android.os.SystemClock.elapsedRealtime())
        fun progress(value: String) {
            stage.set(value)
            stageSince.set(android.os.SystemClock.elapsedRealtime())
            android.util.Log.i("NOVA_XHTTP_SMOKE", "stage=$value")
            sendStatus(0, Bundle().apply { putString("stream", "XHTTP_STAGE $value\n") })
        }
        val watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "xhttp-smoke-watchdog").apply { isDaemon = true }
        }
        watchdog.scheduleWithFixedDelay({
            val elapsed = android.os.SystemClock.elapsedRealtime() - stageSince.get()
            if (elapsed >= 30000) {
                android.util.Log.e("NOVA_XHTTP_SMOKE", "STALLED stage=${stage.get()} elapsedMs=$elapsed")
                Thread.getAllStackTraces().forEach { (thread, stack) ->
                    android.util.Log.e("NOVA_XHTTP_SMOKE", "THREAD ${thread.name} state=${thread.state}")
                    stack.take(48).forEach { frame -> android.util.Log.e("NOVA_XHTTP_SMOKE", "  at $frame") }
                }
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS)
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
            progress("activity-start")
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
                progress("case-$caseIndex-$mode-prepare")
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
                        check(literal == "127.0.0.1" || literal == "10.0.2.2" || literal == xhttpLanHost)
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
                progress("case-$caseIndex-start-service")
                context.startForegroundService(android.content.Intent(context, NovaVpnService::class.java)
                    .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                    .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "XHTTP $mode fixture"))
                waitUntil("XHTTP $mode start") { VpnRuntime.state.value.connected || VpnRuntime.state.value.error != null }
                check(VpnRuntime.state.value.connected) { VpnRuntime.state.value.error.orEmpty() }
                progress("case-$caseIndex-first-probe")
                val first = routedClientResult(ipv6 = xhttpIpv6First)
                progress("case-$caseIndex-second-probe")
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
                progress("case-$caseIndex-stop-service")
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
            android.util.Log.e("NOVA_XHTTP_SMOKE", "Failure at ${stage.get()}", error)
            result.putString("stream", "XHTTP smoke failed: ${error.javaClass.simpleName}: ${error.message}\n")
        } finally {
            progress("final-stop-service")
            runCatching { context.startService(android.content.Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP)) }
            runCatching { waitUntil("XHTTP final stop") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting } }
            progress("finish-activity")
            activity?.let { window -> runOnMainSync { window.finish() } }
            progress("collect-traces")
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
        progress("finish-instrumentation")
        watchdog.shutdownNow()
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

    private fun verifyRetainedTunInDoze() {
        val context = targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        val power = context.getSystemService(android.os.PowerManager::class.java)
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        fun handle() = connectivity.allNetworks.singleOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        }?.networkHandle
        check(!power.isDeviceIdleMode && power.isInteractive) { "Device already asleep before test" }
        val originalHandle = requireNotNull(handle())
        val originalRevision = VpnRuntime.state.value.sessionRevision
        fun checkSameTun() {
            check(handle() == originalHandle && VpnRuntime.state.value.connected) { "Doze lost TUN" }
            check(VpnRuntime.state.value.sessionRevision == originalRevision) { "Idle restarted the engine" }
        }
        try {
            shell("dumpsys battery unplug")
            shell("input keyevent 223")
            shell("cmd deviceidle force-idle deep")
            waitUntil("confirmed Doze") { power.isDeviceIdleMode && !power.isInteractive }
            repeat(30) {
                android.os.SystemClock.sleep(1_000)
                check(power.isDeviceIdleMode && !power.isInteractive) { "Device left deep idle during observation" }
                checkSameTun()
            }
        } finally {
            shell("cmd deviceidle unforce")
            shell("dumpsys battery reset")
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
        }
        waitUntil("wake leaves Doze") { !power.isDeviceIdleMode && power.isInteractive }
        checkSameTun()
    }

    private fun setFixtureWifi(enabled: Boolean) {
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
            if (enabled) "svc wifi enable" else "svc wifi disable")).use { it.readBytes() }
    }

    private fun verifyMultiExitCellularHandover(probe: (Int) -> Unit) {
        val cm = targetContext.getSystemService(android.net.ConnectivityManager::class.java)
        val wifi = android.net.NetworkCapabilities.TRANSPORT_WIFI
        val cellular = android.net.NetworkCapabilities.TRANSPORT_CELLULAR
        val vpn = android.net.NetworkCapabilities.TRANSPORT_VPN
        fun vpnNetwork() = cm.allNetworks.singleOrNull { cm.getNetworkCapabilities(it)?.hasTransport(vpn) == true }
        fun validatedPhysical(transport: Int) = cm.allNetworks.any {
            cm.getNetworkCapabilities(it)?.let { caps ->
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    caps.hasTransport(transport)
            } == true
        }
        fun vpnUses(network: android.net.Network, transport: Int, excluded: Int): Boolean =
            cm.getNetworkCapabilities(network)?.let {
                it.hasTransport(vpn) && it.hasTransport(transport) && !it.hasTransport(excluded)
            } == true

        val original = requireNotNull(vpnNetwork())
        check(validatedPhysical(wifi) && vpnUses(original, wifi, cellular)) {
            "Start cellular handover on a validated Wi-Fi underlay selected by the VPN"
        }
        fun awaitHandover(transport: Int, excluded: Int, fromRevision: Long, label: String) {
            waitUntil(label, timeoutMillis = 90000L) {
                check(vpnNetwork() == original) { "VPN network changed during $label" }
                val state = VpnRuntime.state.value
                validatedPhysical(transport) && vpnUses(original, transport, excluded) &&
                    state.connected && state.recoveryStatus == null && state.sessionRevision > fromRevision
            }
        }

        var cellularRevision: Long
        try {
            val wifiRevision = VpnRuntime.state.value.sessionRevision
            setFixtureWifi(false)
            awaitHandover(cellular, wifi, wifiRevision, "validated mobile VPN underlay")
            probe(0)
            check(vpnNetwork() == original && vpnUses(original, cellular, wifi))
            cellularRevision = VpnRuntime.state.value.sessionRevision
        } finally {
            setFixtureWifi(true)
            waitUntil("restore validated test device Wi-Fi", timeoutMillis = 90000L) { validatedPhysical(wifi) }
        }
        awaitHandover(wifi, cellular, cellularRevision, "validated Wi-Fi VPN underlay")
        probe(1)
        check(vpnNetwork() == original && vpnUses(original, wifi, cellular))
    }

    private fun recoverMultiExitOverWifi() {
        val connectivity = targetContext.getSystemService(android.net.ConnectivityManager::class.java)
        fun vpnHandle() = connectivity.allNetworks.singleOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        }?.networkHandle
        fun physicalTransports() = connectivity.allNetworks.mapNotNull { network ->
            connectivity.getNetworkCapabilities(network)?.takeIf { caps ->
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }
        }
        fun isWifi(caps: android.net.NetworkCapabilities) =
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        val physical = physicalTransports()
        check(physical.isNotEmpty() && physical.all(::isWifi)) {
            "The wired test device must have Wi-Fi as its only internet underlay"
        }
        val originalHandle = requireNotNull(vpnHandle())
        val beforeLoss = VpnRuntime.state.value.sessionRevision
        try {
            setFixtureWifi(false)
            waitUntil("multi-exit VPN waiting for physical network") {
                physicalTransports().none(::isWifi) && VpnRuntime.state.value.recoveryStatus == "waiting_network"
            }
            check(vpnHandle() == originalHandle) { "Multi-exit VPN lost its TUN while offline" }
        } finally {
            setFixtureWifi(true)
            waitUntil("restore test device Wi-Fi") { physicalTransports().any(::isWifi) }
        }
        waitUntil("multi-exit VPN recovered over Wi-Fi") {
            val state = VpnRuntime.state.value
            state.connected && state.recoveryStatus == null && state.sessionRevision > beforeLoss
        }
        check(vpnHandle() == originalHandle) { "Multi-exit VPN replaced its TUN during Wi-Fi recovery" }
    }

    private fun waitUntil(label: String = "condition", timeoutMillis: Long = 15000L, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMillis
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() < deadline) {
                "Timed out: $label. " + VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value)
            }
            android.os.SystemClock.sleep(100)
        }
    }

    private fun routedClientResult(ipv6: Boolean = false, site: Boolean = false, expectPhysical: Boolean = false, siteHost:String="native.test",clientPackage:String?=null,lanFixture:Boolean=false,externalTrace:Boolean=false,udp:Boolean=false,http3:Boolean=false,txt:Boolean=false): String {
        val context = targetContext
        check(!http3 || (!multiConcurrentOnly && clientPackage==null))
        check(!externalTrace || !multiConcurrentOnly)
        check(!txt || (multiTxtDnsOnly && clientPackage != null && !multiConcurrentOnly && !expectPhysical))
        lastHttp3Diagnostic=""
        lastTraceIpHash=""
        check(!lanFixture || (multiDirectHost!=null && !multiConcurrentOnly))
        val client=clientPackage?:"${context.packageName}.test"
        check(client in setOf("${context.packageName}.test","com.nova.vpn.routingprobe") ||
            multiSharedUidOnly && (client == sharedUidFirst || client == sharedUidSecond))
        val visibleFirst=multiVisibleFirstOnly && client=="${context.packageName}.test"
        val expectedClientUid=if(clientPackage!=null || multiConcurrentOnly || externalTrace || lanFixture || udp || http3)context.packageManager.getApplicationInfo(client,0).uid else null
        val nonce = java.util.UUID.randomUUID().toString()
        val answer = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val physical = java.util.concurrent.atomic.AtomicBoolean(false)
        val networkSummary = java.util.concurrent.atomic.AtomicReference("")
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.getStringExtra("nonce") == nonce) {
                    if(expectedClientUid!=null && intent.getIntExtra("clientUid",-1)!=expectedClientUid) {answer.set("Wrong response UID");return}
                    intent.getStringExtra("probeStage")?.let {stage->
                        sendStatus(0,Bundle().apply{putString("stream","Client stage package=$client uid=$expectedClientUid stage=$stage elapsed=${intent.getLongExtra("elapsed",0)} launch=${intent.getLongExtra("launchElapsed",0)}\n")})
                        return
                    }
                    if(visibleFirst) {
                        if(!intent.getBooleanExtra("probeVisible",false)) {answer.set("Client window was not focused");return}
                        visibleFirstProofs.incrementAndGet()
                    }
                    physical.set(intent.getBooleanExtra("physicalDefault", false))
                    lastProbeNetworkHandle = intent.getLongExtra("activeNetworkHandle", 0)
                    networkSummary.set(intent.getStringExtra("networkSummary").orEmpty())
                    if(http3) {
                        lastHttp3Diagnostic=intent.getStringExtra("http3Diagnostic").orEmpty()
                        sendStatus(0,Bundle().apply{putString("stream","HTTP3 diagnostic: "+lastHttp3Diagnostic.take(1500)+"\n")})
                    }
                    lastTraceIpHash=intent.getStringExtra("traceIpHash").orEmpty()
                    answer.set(intent.getStringExtra("result"))
                }
            }
        }
        val filter = android.content.IntentFilter("${context.packageName}.REALITY_SMOKE_RESULT")
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        else { @Suppress("DEPRECATION") context.registerReceiver(receiver, filter) }
        try {
            if(multiConcurrentOnly && !visibleFirst) {
                check(context.packageName=="com.nova.vpn.android.usbtest.completionpreview")
                check(siteHost=="native.test" || siteHost.matches(Regex("probe-[a-f0-9-]+[.]native[.]test")))
                val launch=android.os.SystemClock.elapsedRealtime()
                val command="am start-foreground-service -n $client/com.nova.vpn.android.RoutingProbeService"+
                    " --es nonce $nonce --ez ipv6 $ipv6 --ez site $site --es siteHost $siteHost --ez udpProbe $udp --ei udpPackets ${if(multiXudpBurstOnly)3 else 1}"+
                    " --ez keepTestScreenAwake true --ez concurrentProbe true --el launchElapsed $launch"+
                    " --ez expectPhysical $expectPhysical --ez useNetworkCallbacks $probeUseNetworkCallbacks"
                val descriptor=uiAutomation.executeShellCommand(command)
                val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
                try {
                    val output=pool.submit<String>{android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use{it.readText()}}.get(8,java.util.concurrent.TimeUnit.SECONDS)
                    check(output.contains("Starting service:") && !output.contains("Error:")) {"Companion shell launch refused: ${output.take(400)}"}
                    sendStatus(0,Bundle().apply{putString("stream","Test client shell launch package=$client issued=$launch returned=${android.os.SystemClock.elapsedRealtime()}\n")})
                } finally {runCatching{descriptor.close()};pool.shutdownNow();check(pool.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS)){"Shell output worker did not stop"}}
            } else context.startActivity(android.content.Intent().setComponent(android.content.ComponentName(
                client, RealityProbeClientActivity::class.java.name))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra("txtProbe",txt).putExtra("http3Site",multiQuicSiteOnly).putExtra("http3Probe",http3).putExtra("requireVisibleProbe",visibleFirst).putExtra("udpPackets",if(multiXudpBurstOnly)3 else 1).putExtra("udpProbe",udp).putExtra("externalTlsTrace",externalTrace).putExtra("lanFixtureHost",if(lanFixture)multiDirectHost else "").putExtra("lanFixturePort",multiDirectPort).putExtra("nonce", nonce).putExtra("ipv6", ipv6).putExtra("site", site).putExtra("siteHost",siteHost).putExtra("keepTestScreenAwake",multiExitOnly).putExtra("concurrentProbe",multiConcurrentOnly).putExtra("launchElapsed",android.os.SystemClock.elapsedRealtime())
                .putExtra("expectPhysical", expectPhysical).putExtra("useNetworkCallbacks", probeUseNetworkCallbacks))
            waitUntil("client result ipv6=$ipv6 site=$site") { answer.get() != null }
            check(answer.get()!="Wrong response UID" && answer.get()!="Client window was not focused") {"Probe precondition failed: ${answer.get()}"}
            sendStatus(0, Bundle().apply { putString("stream",
                "Separate UID: client=$client uid=$expectedClientUid visibleFirst=$visibleFirst ipv6=$ipv6 site=$site lanFixture=$lanFixture externalTrace=$externalTrace udp=$udp http3=$http3 expectPhysical=$expectPhysical result=${answer.get()} ${networkSummary.get()}\n") })
            check(!expectPhysical || physical.get()) { "Excluded UID still sees VPN as its default network" }
            return answer.get()!!
        } finally { context.unregisterReceiver(receiver) }
    }
}
