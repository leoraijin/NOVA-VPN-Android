package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnTransportHealthTest {
    @Test fun grpcTlsAndDnsErrorsProduceOnlySafePerRequestGuidance() {
        val grpc = VpnTransportHealth.warningFor("v2ray-grpc: unexpected status: 404 secret.example")!!
        assertTrue("gRPC-запросов" in grpc)
        assertTrue("само по себе не означает отключение VPN" in grpc)
        val tls = VpnTransportHealth.warningFor("TLS: no application protocol at secret.example")!!
        assertTrue("ALPN" in tls)
        assertTrue("Один из запросов" in tls)
        assertTrue("само по себе не означает отключение VPN" in tls)
        val dns = VpnTransportHealth.warningFor("dns: exchange failed for secret.example")!!
        assertTrue("DNS" in dns)
        assertTrue("для одного из запросов" in dns)
        assertTrue("Другие запросы через VPN могут работать" in dns)
        listOf(grpc, tls, dns).forEach { assertFalse(it.contains("secret.example")) }
    }
    @Test
    fun certificateMismatchDoesNotImplyTunnelFailureOrLeakHostnames() {
        val warning = VpnTransportHealth.warningFor(
            "x509: certificate is valid for unrelated.example, not vpn.example"
        )

        assertEquals(
            "Один из TLS-запросов не прошёл проверку сертификата. Это сообщение не подтверждает неисправность всего VPN-подключения.",
            warning
        )
        assertNull(warning?.takeIf { "vpn.example" in it || "unrelated.example" in it })
    }

    @Test
    fun expiredCertificateAndTimeoutAreReportedAsIndividualRequestFailures() {
        assertEquals(
            "Один из TLS-запросов отклонён из-за просроченного сертификата. По этому сообщению нельзя определить, относится ли сертификат к VPN-серверу или к адресу назначения.",
            VpnTransportHealth.warningFor("x509: certificate has expired")
        )
        assertEquals(
            "Истекло время ожидания одного из запросов. VPN может продолжать работать; это не подтверждение отключения туннеля.",
            VpnTransportHealth.warningFor("context deadline exceeded")
        )
    }

    @Test
    fun refusedTcpConnectionDoesNotImplyCredentialRejectionOrVpnDisconnection() {
        val warning = VpnTransportHealth.warningFor("dial tcp 203.0.113.1:443: connect: connection refused")!!
        assertEquals(
            "Одно из соединений получило отказ (connection refused). Это может относиться к отдельному адресу назначения и не означает, что VPN отключён.",
            warning
        )
        assertFalse("203.0.113.1" in warning)
        assertFalse("443" in warning)
        assertFalse("профил" in warning)
        assertFalse("подлинност" in warning)
    }

    @Test
    fun unrelatedLogMessagesDoNotChangeUiHealth() {
        assertNull(VpnTransportHealth.warningFor("sing-box started (0.09s)"))
    }
}
