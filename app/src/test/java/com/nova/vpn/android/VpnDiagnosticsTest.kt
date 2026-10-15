package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.ImportedProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VpnDiagnosticsTest {
    @Before fun reset() { VpnDiagnostics.reset() }

    @Test fun reportContainsStagesButNoProfileOrLogSecrets() {
        val profile = ImportedProfile(name = "PRIVATE_NAME", protocol = "vless", server = "private.example", port = 443,
            outboundJson = """{"type":"vless","server":"private.example","uuid":"PRIVATE_UUID","password":"PRIVATE_PASSWORD","transport":{"type":"grpc","service_name":"PRIVATE_SERVICE"},"tls":{"enabled":true,"server_name":"PRIVATE_SNI","alpn":["h2"]}}""",
            subscriptionUrl = "https://private.example/PRIVATE_SUBSCRIPTION")
        VpnDiagnostics.record("probe_start")
        VpnDiagnostics.coreMessage("rpc error: code = Unavailable at private.example uuid=PRIVATE_UUID")
        val report = VpnDiagnostics.report(profile, AppRoutingSettings(), 35, VpnUiState(connected = true))
        assertTrue(report.contains("transport=grpc"))
        assertTrue(report.contains("core_grpc_unavailable"))
        for (secret in listOf("PRIVATE", "private.example", "uuid=", "https://")) assertFalse(report.contains(secret))
    }

    @Test fun eventsAreBoundedAndRawTextIsRejected() {
        repeat(100) { VpnDiagnostics.record("fixture_$it") }
        val report = VpnDiagnostics.report(null, AppRoutingSettings(), 35, VpnUiState())
        assertFalse(report.contains("fixture_0\n"))
        assertTrue(report.contains("fixture_99"))
        assertEquals(40, report.lineSequence().count { "s fixture_" in it })
        assertThrows(IllegalArgumentException::class.java) { VpnDiagnostics.record("raw password=secret host.example") }
    }

    @Test fun unknownNativeMessagesAreNotExportedVerbatim() {
        VpnDiagnostics.coreMessage("unexpected PRIVATE_TOKEN at secret.example")
        val report = VpnDiagnostics.report(null, AppRoutingSettings(), 35, VpnUiState())
        assertTrue(report.contains("unclassified_warning"))
        assertFalse(report.contains("PRIVATE_TOKEN"))
        assertFalse(report.contains("secret.example"))
    }
}
