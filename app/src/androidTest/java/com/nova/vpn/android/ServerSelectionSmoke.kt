package com.nova.vpn.android

import android.app.Instrumentation
import com.nova.vpn.android.data.ProfileLinkParser
import com.nova.vpn.android.data.AppPreferencesStore
import com.nova.vpn.android.data.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicReference

/** Explicit external HTTPS smoke: only public control URLs, no user profiles or keys. */
internal object ServerSelectionSmoke {
    fun run(instrumentation: Instrumentation, expectedSuccesses: Int, engine: VpnEngine = VpnEngine.SING_BOX, caBase64: String? = null) {
        check(expectedSuccesses == 3 || expectedSuccesses == 6)
        val context = instrumentation.targetContext
        check(context.packageName == "com.nova.vpn.android.usbtest.realitypreview")
        val preferences = AppPreferencesStore(context)
        val previousEngine = preferences.vpnEngine()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val checks = ServerChecks(context, scope)
        val selected = AtomicReference<String?>()
        val transport = if (engine == VpnEngine.REALITY_CORE) "tcp" else "xhttp&path=%2Fnova-fixture&mode=packet-up"
        val parsed = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@127.0.0.1:38123?type=$transport#HTTPS-fixture").single()
        val certificate = caBase64?.let {
            check(it.length <= 16_384)
            java.io.File(context.filesDir, "probe-ca-${java.util.UUID.randomUUID()}.pem").apply {
                writeBytes(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            }
        }
        val profile = if (certificate == null) parsed else parsed.copy(outboundJson = org.json.JSONObject(parsed.outboundJson)
            .put("tls", org.json.JSONObject().put("enabled", true).put("server_name", "localhost")
                .put("certificate_path", certificate.absolutePath)).toString())
        try {
            preferences.setVpnEngine(engine)
            instrumentation.runOnMainSync { checks.start(listOf(profile), diagnostic = true) { selected.set(it.id); true } }
            val deadline = System.nanoTime() + 40_000_000_000L
            var observedProgress = false
            while (checks.state.value.running && System.nanoTime() < deadline) {
                if (checks.state.value.progressEvents.isNotEmpty()) observedProgress = true
                Thread.sleep(25)
            }
            val state = checks.state.value
            check(!state.running && state.completed == 1) { "Checker did not finish: ${state.message}" }
            val result = state.results.getValue(profile.id)
            check(result.engine == engine && result.events.all { it.engine == engine.id }) { "Measurement engine identity mismatch" }
            check(result.outcome == CheckOutcome.SUCCESS && result.millis != null) { "HTTPS checks failed: ${result.outcome}; events=${result.events.joinToString { it.code }}" }
            check(observedProgress) { "No stage progress while checker was running" }
            check(result.attempts == 6 && result.successes == expectedSuccesses) { "Unexpected probe counts: ${result.successes}/${result.attempts}" }
            check(!result.networkSignature.isNullOrBlank())
            check(result.configurationSignature == ServerSelectionPolicy.configurationSignature(profile))
            check(selected.get() == profile.id) { "Verified server was not selected" }
            check(result.events.any { it.code == "probe_proxy_connected" }) { "Missing proxy connection stage across IPC" }
            check(result.events.any { it.code == "probe_target_tls_ready" }) { "Missing verified target TLS stage across IPC" }
            check(result.events.any { it.code == "probe_success" }) { "Missing HTTPS success stage across IPC" }
            val safeEvents = DiagnosticEventCodec.encode(result.events)
            check(!safeEvents.contains("11111111-1111-4111-8111-111111111111") && !safeEvents.contains("127.0.0.1") && !safeEvents.contains("www.gstatic.com"))
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS isolated native HTTPS selection engine=${engine.id}: ${result.successes}/${result.attempts}, median=${result.millis}ms, TLS verification, network/config/engine scope\n") })
            selected.set(null)
            instrumentation.runOnMainSync {
                checks.start(listOf(profile)) { selected.set(it.id); true }
                checks.cancel()
            }
            Thread.sleep(200)
            check(selected.get() == null && !checks.state.value.running) { "Cancelled selection was applied" }
            if (engine == VpnEngine.REALITY_CORE) {
                check(certificate != null) { "Reality HTTPS test requires its verified TLS fixture" }
                val wrongIdentity = profile.copy(id = "wrong-tls-identity-fixture", outboundJson = org.json.JSONObject(profile.outboundJson).apply {
                    getJSONObject("tls").put("server_name", "mismatched-probe.invalid")
                }.toString())
                instrumentation.runOnMainSync { checks.start(listOf(wrongIdentity), diagnostic = true) { selected.set(it.id); true } }
                val tlsDeadline = System.nanoTime() + 40_000_000_000L
                while (checks.state.value.running && System.nanoTime() < tlsDeadline) Thread.sleep(25)
                val tlsFailure = checks.state.value.results[wrongIdentity.id]
                check(!checks.state.value.running && tlsFailure?.outcome == CheckOutcome.FAILED && tlsFailure.attempts == 6 && tlsFailure.successes == 0) {
                    "Incorrect server TLS identity was not rejected"
                }
                check(selected.get() == null && tlsFailure.events.any { it.code == "core_tls_certificate_failed" })
                check(!DiagnosticEventCodec.encode(tlsFailure.events).contains("mismatched-probe.invalid"))
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS Reality verified TLS refusal: wrong server identity, zero successes, safe classified events, no selection\n") })
                val malformed = profile.copy(id = "unsupported-probe-fixture", outboundJson = org.json.JSONObject(profile.outboundJson)
                    .put("transport", org.json.JSONObject().put("type", "unsupported-fixture")).toString())
                instrumentation.runOnMainSync { checks.start(listOf(malformed), diagnostic = true) { selected.set(it.id); true } }
                val unsupportedDeadline = System.nanoTime() + 20_000_000_000L
                while (checks.state.value.running && System.nanoTime() < unsupportedDeadline) Thread.sleep(25)
                val rejected = checks.state.value.results[malformed.id]
                check(!checks.state.value.running && rejected?.outcome == CheckOutcome.UNSUPPORTED && rejected.attempts == 0) {
                    "Native incompatible configuration was not rejected: ${rejected?.outcome}"
                }
                check(selected.get() == null && rejected.events.any { it.code == "engine_config_unsupported" })
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS Reality native incompatible transport: unsupported, zero HTTPS attempts, no selection\n") })
            }
        } finally {
            instrumentation.runOnMainSync { checks.cancel(); scope.cancel() }
            preferences.setVpnEngine(previousEngine)
            certificate?.delete()
        }
    }
}
