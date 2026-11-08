package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.ImportedProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VpnDiagnosticsTest {
    @Before fun reset() { VpnDiagnostics.reset() }

    @Test fun unresolvedCatalogFailureOverridesVerifiedHttpsAndSubmittedAttemptDoesNotClearIt() {
        val state=VpnUiState(connected=true,trafficVerified=true)
        VpnDiagnostics.record("catalog_refresh_failed")
        VpnDiagnostics.record("catalog_refresh_submitted")
        val snapshot=VpnDiagnostics.snapshot(null,AppRoutingSettings(),35,state)
        assertEquals("Не удалось обновить активные маршруты",snapshot.advice.title)
        assertEquals(DiagnosticAction.ROUTING,snapshot.advice.action)
        assertTrue(snapshot.advice.detail.contains("остаётся прежним"))
    }
    @Test fun validatedCurrentCatalogClearsOnlyCatalogWarningAndNewFailureRestoresIt() {
        VpnDiagnostics.record("catalog_refresh_failed")
        VpnDiagnostics.record("catalog_refresh_current")
        assertNotEquals("Не удалось обновить активные маршруты",VpnDiagnostics.advice().title)
        val state=VpnUiState(connected=true,trafficVerified=true)
        assertEquals("HTTPS через VPN подтверждён",VpnDiagnostics.snapshot(null,AppRoutingSettings(),35,state).advice.title)
        VpnDiagnostics.record("catalog_refresh_failed")
        assertEquals(DiagnosticAction.ROUTING,VpnDiagnostics.snapshot(null,AppRoutingSettings(),35,state).advice.action)
        VpnDiagnostics.reset()
        assertEquals("HTTPS через VPN подтверждён",VpnDiagnostics.snapshot(null,AppRoutingSettings(),35,state).advice.title)
    }
    @Test fun resolvedCatalogFailureDoesNotHideAnotherUnresolvedDiagnostic() {
        VpnDiagnostics.record("catalog_refresh_failed")
        VpnDiagnostics.record("vpn_permission_required")
        VpnDiagnostics.record("catalog_refresh_current")
        assertEquals(DiagnosticAction.VPN_PERMISSION,VpnDiagnostics.advice().action)
        assertEquals("catalog_refresh_current",DiagnosticCatalog.code("catalog_refresh_current"))
    }
    @Test fun httpFailuresDoNotInventGrpcTransport() {
        assertEquals("http_not_found", VpnDiagnostics.coreCode("xhttp response status 404"))
        assertEquals("http_not_found", VpnDiagnostics.coreCode("HTTP status 404"))
        assertEquals("grpc_service_not_found", VpnDiagnostics.coreCode("grpc: status 404"))
        assertEquals("remote_unauthorized", VpnDiagnostics.coreCode("HTTP status 401"))
        assertEquals("unclassified_warning", VpnDiagnostics.coreCode("request id 14041"))
        assertEquals("unclassified_warning", VpnDiagnostics.coreCode("request id 14031"))
    }

    @Test fun hostnamesPathsAndRequestIdsDoNotInventGrpcFailures() {
        assertEquals("http_not_found", VpnDiagnostics.coreCode("xhttp response 404 from https://rpc.example/path"))
        assertEquals("http_not_found", VpnDiagnostics.coreCode("HTTP status 404 from https://grpc.example/path"))
        assertEquals("http_not_found", VpnDiagnostics.coreCode("HTTP status 404 path=/rpc/private"))
        assertEquals("unclassified_warning", VpnDiagnostics.coreCode("grpc request id 14041"))
        assertEquals("unclassified_warning", VpnDiagnostics.coreCode("unavailable host=rpc.example"))
        assertEquals("grpc_service_not_found", VpnDiagnostics.coreCode("rpc error: code = Unimplemented desc = unknown service"))
        assertEquals("grpc_unavailable", VpnDiagnostics.coreCode("rpc error: code = Unavailable"))
        assertEquals("grpc_service_not_found", VpnDiagnostics.coreCode("transport/grpc: status 404"))
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
