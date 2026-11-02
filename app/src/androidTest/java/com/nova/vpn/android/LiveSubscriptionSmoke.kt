package com.nova.vpn.android

import android.app.Instrumentation
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import com.nova.vpn.android.data.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in real TUN assessment. One-use URL; only aggregate outcomes leave the device. */
internal object LiveSubscriptionSmoke {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        check(android.net.VpnService.prepare(context) == null)
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val input = File(context.getExternalFilesDir(null), "live-subscription-input.txt")
        check(input.isFile && input.length() in 1..4096)
        val url = try { input.readText().trim() } finally { check(input.delete()) }
        check(java.net.URI(url).scheme == "https")
        fun report(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", text + "\n") })
        fun await(timeout: Long = 35_000, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!condition()) { check(SystemClock.elapsedRealtime() < end); SystemClock.sleep(100) }
        }
        val preferences = AppPreferencesStore(context)
        val originalAutoConnect = preferences.autoConnectEnabled()
        val originalUpdates = preferences.updateChecksEnabled()
        val originalEngine = preferences.vpnEngine()
        val auto = AutoServerSettingsStore(context).use { it.snapshot() }
        val vault = ProfileVault(context)
        val original = vault.loadSnapshot()
        val originalSelected = SelectedProfileStore(context).load()
        val routesBefore = AppRoutingStore(context).loadPresets()
        var activity: android.app.Activity? = null
        var phase = "fetch"
        fun stop() { VpnUserRequests.stop(context); await { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting } }
        fun start(profile: ImportedProfile, routing: AppRoutingSettings) {
            val config = SingBoxConfigFactory.build(profile, routing, context.packageName)
            val request = VpnUserRequests.gate.begin()
            context.startForegroundService(Intent(context, NovaVpnService::class.java)
                .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                .putExtra(VpnUserRequests.EXTRA_REQUEST, request)
                .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "Live subscription test")
                .putExtra(NovaVpnService.EXTRA_PROFILE_ID, profile.id))
            await { (VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.requestId == request) || VpnRuntime.state.value.error != null }
            check(VpnRuntime.state.value.connected)
        }
        try {
            preferences.setAutoConnectEnabled(false); preferences.setUpdateChecksEnabled(false)
            AutoServerSettingsStore(context).use { it.save(false, auto.candidateIds) }
            val response = SubscriptionFetcher.fetchSubscription(url, SubscriptionDeviceIdentity(context).forUrl(url), null, null)
            val parsed = ProfileLinkParser.parseReport(response.body)
            report("IMPORT selectedFormat=${response.requestAgent} profiles=${parsed.profiles.size} warnings=${parsed.warnings.size}")
            phase = "encrypted_store"
            vault.updateSnapshot { ProfileSnapshot() }
            val imported = SubscriptionRepository(context).importReport(url, true, parsed)
            check(vault.loadSnapshot() == imported)
            SelectedProfileStore(context).save(parsed.profiles.first().id)
            report("IMPORT encrypted roundtrip PASS")
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            phase = "direct_baseline"
            val baseline = probe(context, "trace4", true)
            report("DIRECT trace4 ${baseline.safe()}")
            check(baseline.ok && baseline.ip != null && !baseline.vpn)
            val testPackage = context.packageName + ".test"
            val routing = AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf(testPackage))
            val groups = parsed.profiles.withIndex().groupBy { if (it.value.protocol == "hysteria2") "HYSTERIA2" else profileTransport(it.value) }
            var successfulEngines = 0
            for (engine in VpnEngine.entries) {
                preferences.setVpnEngine(engine)
                var engineSucceeded = false
                var routingWinner: ImportedProfile? = null
                for ((transport, candidates) in groups) {
                    if (engine == VpnEngine.REALITY_CORE && transport == "HYSTERIA2") continue
                    var tried = 0; var successes = 0
                    for ((index, profile) in candidates.take(3)) {
                        tried++; phase = "start"
                        report("NODE ${index + 1} engine=${engine.id} transport=$transport START")
                        try {
                            start(profile, routing)
                            phase = "https"
                            val trace = probe(context, "trace4", false)
                            val dnsHttps = probe(context, "google", false)
                            if (!trace.ok || !dnsHttps.ok) {
                                val literal = probe(context, "trace-ip", false)
                                report("NODE ${index + 1} engine=${engine.id} literalIpHttps=${literal.safe()} literalRemoteExit=${literal.ok && literal.ip != null && literal.ip != baseline.ip}")
                            }
                            val remoteExit = trace.ok && trace.vpn && trace.ip != null && trace.ip != baseline.ip
                            report("NODE ${index + 1} engine=${engine.id} trace4=${trace.safe()} remoteExit=$remoteExit google=${dnsHttps.safe()}")
                            if (remoteExit && dnsHttps.ok && dnsHttps.vpn) {
                                successes++; engineSucceeded = true
                                if (routingWinner == null) routingWinner = profile
                            }
                        } catch (error: Exception) { report("NODE ${index + 1} engine=${engine.id} FAIL phase=$phase type=${error.javaClass.simpleName}") }
                        finally { stop() }
                        if (successes > 0) break
                    }
                    report("TRANSPORT engine=${engine.id} type=$transport tried=$tried successful=$successes")
                }
                routingWinner?.let { winner ->
                    phase = "routing_site_direct"
                    try {
                        start(winner, routing.copy(sites = listOf(SiteRoutingRule("ipv4.icanhazip.com", SiteRoutingTarget.DIRECT))))
                        val directSite = probe(context, "trace4", false)
                        check(directSite.ok && directSite.vpn && directSite.ip == baseline.ip)
                        report("ROUTING engine=${engine.id} selected UID uses TUN; direct-site public IP equals baseline PASS")
                    } finally { stop() }
                    phase = "routing_unselected_uid"
                    // Capture only a different isolated fixture UID: all ordinary phone apps stay direct.
                    try {
                        val otherTest = "com.nova.vpn.android.identitypreview.test"
                        context.packageManager.getApplicationInfo(otherTest, 0)
                        start(winner, routing.copy(packages = setOf(otherTest), sites = listOf(SiteRoutingRule("ipv4.icanhazip.com", SiteRoutingTarget.VPN))))
                        val bypass = probe(context, "trace4", true)
                        check(bypass.ok && !bypass.vpn && bypass.ip == baseline.ip)
                        report("ROUTING engine=${engine.id} unselected UID remains physical despite VPN site rule PASS")
                    } finally { stop() }
                }
                if (engineSucceeded) successfulEngines++
            }
            phase = "after_stop"
            val restored = probe(context, "trace4", true)
            check(restored.ok && !restored.vpn && restored.ip == baseline.ip)
            check(AppRoutingStore(context).loadPresets() == routesBefore)
            report("ROUTING after STOP: physical route restored; saved routing unchanged PASS")
            check(successfulEngines == VpnEngine.entries.size)
        } catch (error: Exception) { report("ASSESSMENT FAIL phase=$phase type=${error.javaClass.simpleName}"); throw error }
        finally {
            val cleanupStop = runCatching { stop() }
            activity?.let { current -> instrumentation.runOnMainSync { current.finish() } }
            synchronized(ProfileVault.lock) { vault.restoreSnapshot(original); ProfileVault.invalidatePendingUpdates() }
            if (originalSelected == null) SelectedProfileStore(context).clear() else SelectedProfileStore(context).save(originalSelected)
            preferences.setVpnEngine(originalEngine); preferences.setAutoConnectEnabled(originalAutoConnect); preferences.setUpdateChecksEnabled(originalUpdates)
            AutoServerSettingsStore(context).use { it.save(auto.enabled, auto.candidateIds) }
            SubscriptionScheduling.request(context, original.subscriptions, force = true)
            report("CLEANUP stopped=${cleanupStop.isSuccess}; test catalog, selection and preferences restored")
            cleanupStop.getOrThrow()
        }
    }

    private data class Result(val outcome: String, val vpn: Boolean, val elapsed: Long, val ip: String?) {
        val ok get() = outcome == "ok"
        fun safe() = "$outcome vpn=$vpn elapsedMs=$elapsed"
    }
    private fun probe(context: Context, endpoint: String, physical: Boolean): Result {
        val nonce = UUID.randomUUID().toString()
        val ready = CountDownLatch(1)
        var result = Result("probe_timeout", false, 0, null)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra("nonce") == nonce) {
                    result = Result(intent.getStringExtra("outcome").orEmpty(), intent.getBooleanExtra("vpn", false),
                        intent.getLongExtra("elapsed", 0), intent.getStringExtra("exitIp"))
                    ready.countDown()
                }
            }
        }
        val filter = IntentFilter(context.packageName + ".LIVE_HTTPS_RESULT")
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else { @Suppress("DEPRECATION") context.registerReceiver(receiver, filter) }
        return try {
            context.startActivity(Intent().setComponent(ComponentName(context.packageName + ".test", LiveHttpsProbeActivity::class.java.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra("nonce", nonce).putExtra("physical", physical).putExtra("endpoint", endpoint))
            check(ready.await(35_000, TimeUnit.MILLISECONDS))
            result
        } finally { context.unregisterReceiver(receiver) }
    }
}