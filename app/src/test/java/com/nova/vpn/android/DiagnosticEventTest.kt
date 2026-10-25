package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.ImportedProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagnosticEventTest {
    @Test fun logExpiresAfterSleepAndBoundsCountAndMetrics() {
        var time = 1000L
        val log = DiagnosticLog { time }
        repeat(60) { log.append(DiagnosticStage.INTERNET, "fixture_$it", "sing_box", 2, "xhttp",
            mapOf(DiagnosticMetric.RESPONSE_MS to Long.MAX_VALUE)) }
        assertEquals(40, log.snapshot().size)
        assertEquals(1_000_000_000L, log.snapshot().last().metrics[DiagnosticMetric.RESPONSE_MS])
        time += 1_800_001
        assertTrue(log.snapshot().isEmpty())
    }

    @Test fun rawLowercaseTokensAndUnknownFieldsCannotEnterExport() {
        VpnDiagnostics.reset()
        VpnDiagnostics.record("private_token_abcdef123456")
        VpnDiagnostics.coreMessage("xhttp 404 private_token_abcdef123456 host=secret.example")
        val profile = ImportedProfile(name = "PRIVATE_NAME", protocol = "vless", server = "secret.example", port = 443,
            outboundJson = """{"type":"vless","uuid":"PRIVATE_UUID","transport":{"type":"xhttp","path":"PRIVATE_PATH"},"tls":{"server_name":"PRIVATE_SNI"}}""",
            subscriptionUrl = "https://secret.example/PRIVATE_TOKEN")
        val json = VpnDiagnostics.reportJson(profile, AppRoutingSettings(), 35, VpnUiState(error = "PRIVATE_ERROR"))
        for (secret in listOf("PRIVATE", "private_token_abcdef123456", "secret.example", "https://")) assertFalse(json.contains(secret))
        val events = JSONObject(json).getJSONArray("events")
        assertEquals("unclassified_event", events.getJSONObject(1).getString("code"))
        assertEquals("transport", events.getJSONObject(2).getString("stage"))
        assertEquals("http_not_found", events.getJSONObject(2).getString("code").removePrefix("core_"))
    }

    @Test fun tlsAndPermissionAdviceProvideSpecificActions() {
        fun event(code: String) = DiagnosticEvent(0, DiagnosticCatalog.stage(code), code, "sing_box", 0, "xhttp", emptyMap())
        assertEquals(DiagnosticAction.VPN_PERMISSION, DiagnosticCatalog.advice(event("vpn_permission_required")).action)
        assertEquals(DiagnosticAction.SUBSCRIPTIONS, DiagnosticCatalog.advice(event("core_tls_certificate_failed")).action)
        assertEquals(DiagnosticStage.TRANSPORT, DiagnosticCatalog.stage("core_http_not_found"))
        assertEquals(DiagnosticStage.AUTHORIZATION, DiagnosticCatalog.stage("core_remote_unauthorized"))
    }
}
