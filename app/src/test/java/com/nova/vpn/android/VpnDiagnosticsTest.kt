package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.ImportedProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VpnDiagnosticsTest {
    @Before fun reset() { VpnDiagnostics.reset() }

    @Test fun httpFailuresDoNotInventGrpcTransport() {
        assertEquals("http_not_found", VpnDiagnostics.coreCode("xhttp response status 404"))
        assertEquals("http_not_found", VpnDiagnostics.coreCode("HTTP status 404"))
        assertEquals("grpc_service_not_found", VpnDiagnostics.coreCode("grpc: status 404"))
        assertEquals("remote_unauthorized", VpnDiagnostics.coreCode("HTTP status 401"))
        assertEquals("unclassified_warning", VpnDiagnostics.coreCode("request id 14041"))
        assertEquals("unclassified_warning", VpnDiagnostics.coreCode("request id 14031"))
    }

    @Test fun xhttpReportIdentifiesPinnedCoreWithoutSecrets() {
        VpnDiagnostics.setEngine(false)
        val profile = ImportedProfile(name = "PRIVATE_NAME", protocol = "vless", server = "PRIVATE_HOST", port = 443,
            outboundJson = """{"type":"vless","uuid":"PRIVATE_UUID","transport":{"type":"xhttp","path":"PRIVATE_PATH"}}""")
        val report = VpnDiagnostics.report(profile, AppRoutingSettings(), 35, VpnUiState())
        assertTrue(report.contains("transport=xhttp"))
        assertTrue(report.contains("engine_version=${BuildConfig.LIBBOX_REVISION}"))
        assertFalse(report.contains("libbox_1.14.1"))
        assertFalse(report.contains("PRIVATE"))
    }

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
        repeat(100) { VpnDiagnostics.record("recovery_retry_${it % 30 + 1}") }
        val report = VpnDiagnostics.report(null, AppRoutingSettings(), 35, VpnUiState())
        assertTrue(report.contains("recovery_retry_10"))
        assertEquals(40, report.lineSequence().count { "s recovery_retry_" in it })
        assertThrows(IllegalArgumentException::class.java) { VpnDiagnostics.record("raw password=secret host.example") }
    }

    @Test fun unknownNativeMessagesAreNotExportedVerbatim() {
        VpnDiagnostics.coreMessage("unexpected PRIVATE_TOKEN at secret.example")
        val report = VpnDiagnostics.report(null, AppRoutingSettings(), 35, VpnUiState())
        assertTrue(report.contains("unclassified_warning"))
        assertFalse(report.contains("PRIVATE_TOKEN"))
        assertFalse(report.contains("secret.example"))
    }

    @Test fun rustlsCertificateErrorsAreClassifiedWithoutNativeText() {
        VpnDiagnostics.setEngine(true)
        VpnDiagnostics.coreMessage("invalid peer certificate: UnknownIssuer at PRIVATE_HOST")
        val report = VpnDiagnostics.report(null, AppRoutingSettings(), 35, VpnUiState())
        assertTrue(report.contains("engine=reality_core"))
        assertTrue(report.contains("core_tls_certificate_failed"))
        assertFalse(report.contains("PRIVATE_HOST"))
        VpnDiagnostics.setEngine(false)
    }
}
