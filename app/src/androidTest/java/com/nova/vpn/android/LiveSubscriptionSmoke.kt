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

/** Opt-in real-provider assessment. Input is ephemeral; reports contain no profile fields. */
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
        fun await(timeout: Long = 35000, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!condition()) { check(SystemClock.elapsedRealtime() < end); SystemClock.sleep(100) }
        }
        val preferences = AppPreferencesStore(context)
        check(!preferences.autoConnectEnabled())
        AutoServerSettingsStore(context).use { check(!it.snapshot().enabled) }
        val originalEngine = preferences.vpnEngine()
        val vault = ProfileVault(context)
        val original = vault.loadSnapshot()
        var activity: android.app.Activity? = null
        var phase = "fetch"
        try {
            // No HWID, compatibility identity or extra authorization headers.
            val body = SubscriptionFetcher.fetchIfUrl(url)
            phase = "parse"
            val parsed = ProfileLinkParser.parseReport(body)
            report("IMPORT profiles=${parsed.profiles.size} warnings=${parsed.warnings.size}")
            phase = "encrypted_store"
            val imported = SubscriptionRepository(context).importReport(url, false, parsed)
            check(vault.loadSnapshot() == imported)
            report("IMPORT encrypted roundtrip PASS")
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            phase = "direct_baseline"
            for (endpoint in listOf("cloudflare", "google")) report("DIRECT $endpoint ${probe(context, endpoint, true)}")
            val routing = AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf(context.packageName + ".test"))
            var successfulEngines = 0
            for (engine in VpnEngine.entries) {
                preferences.setVpnEngine(engine)
                var tried = 0; var successes = 0; var unsupported = 0
                for ((index, profile) in parsed.profiles.withIndex()) {
                    if (tried >= 3 || successes > 0) break
                    val config = try {
                        SingBoxConfigFactory.build(profile, routing, context.packageName).also {
                            if (engine == VpnEngine.REALITY_CORE) RealityConfigFactory.prepare(it, context.packageName) { pkg -> context.packageManager.getApplicationInfo(pkg, 0).uid }
                        }
                    } catch (_: Exception) { unsupported++; continue }
                    tried++
                    phase = "start"
                    try {
                        val request = VpnUserRequests.gate.begin()
                        context.startForegroundService(Intent(context, NovaVpnService::class.java)
                            .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                            .putExtra(VpnUserRequests.EXTRA_REQUEST, request)
                            .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "Live test node ${index + 1}")
                            .putExtra(NovaVpnService.EXTRA_PROFILE_ID, profile.id))
                        await { (VpnRuntime.state.value.connected && VpnRuntime.state.value.appliedConfiguration?.requestId == request) || VpnRuntime.state.value.error != null }
                        check(VpnRuntime.state.value.connected)
                        phase = "https"
                        var passed = 0
                        for (endpoint in listOf("cloudflare", "google")) {
                            val result = probe(context, endpoint, false)
                            if (result.startsWith("ok vpn=true")) passed++
                            report("NODE ${index + 1} engine=${engine.id} $endpoint $result")
                        }
                        if (passed > 0) successes++
                        report("NODE ${index + 1} engine=${engine.id} https=$passed/2")
                    } catch (error: Exception) { report("NODE ${index + 1} engine=${engine.id} FAIL phase=$phase type=${error.javaClass.simpleName}") }
                    finally { VpnUserRequests.stop(context); await { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting } }
                }
                if (successes > 0) successfulEngines++
                report("ENGINE ${engine.id} tried=$tried successful=$successes unsupported=$unsupported")
            }
            check(successfulEngines == VpnEngine.entries.size)
        } catch (error: Exception) { report("ASSESSMENT FAIL phase=$phase type=${error.javaClass.simpleName}"); throw error }
        finally {
            VpnUserRequests.stop(context)
            await { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
            vault.updateSnapshot { original }
            preferences.setVpnEngine(originalEngine)
            activity?.let { current -> instrumentation.runOnMainSync { current.finish() } }
            report("CLEANUP test VPN stopped; original test vault and engine restored")
        }
    }

    private fun probe(context: Context, endpoint: String, physical: Boolean): String {
        val nonce = UUID.randomUUID().toString()
        val ready = CountDownLatch(1)
        var result = "probe_timeout"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra("nonce") == nonce) {
                    result = "${intent.getStringExtra("outcome")} vpn=${intent.getBooleanExtra("vpn", false)} elapsedMs=${intent.getLongExtra("elapsed", 0)}"
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
            check(ready.await(35000, TimeUnit.MILLISECONDS))
            result
        } finally { context.unregisterReceiver(receiver) }
    }
}
