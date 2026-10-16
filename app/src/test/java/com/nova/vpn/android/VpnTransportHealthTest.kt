package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnTransportHealthTest {
    @Test fun grpcTlsAndDnsErrorsProduceOnlySafeGuidance() {
        val grpc = VpnTransportHealth.warningFor("v2ray-grpc: unexpected status: 404 secret.example")!!
        assertTrue("gRPC-запросов" in grpc)
        assertTrue("само по себе не означает отключение VPN" in grpc)
        val tls = VpnTransportHealth.warningFor("TLS: no application protocol at secret.example")!!
        assertTrue("ALPN" in tls)
        val dns = VpnTransportHealth.warningFor("dns: exchange failed for secret.example")!!
        assertTrue("DNS" in dns)
        listOf(grpc, tls, dns).forEach { assertFalse(it.contains("secret.example")) }
    }
    @Test
    fun certificateMismatchIsReportedWithoutLeakingHostnames() {
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
    fun expiredCertificateAndTimeoutAreReported() {
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
    fun refusedTcpConnectionIsNotMisreportedAsCredentialRejection() {
        assertEquals(
            "Одно из соединений получило отказ (connection refused). Это может относиться к отдельному адресу назначения и не означает, что VPN отключён.",
            VpnTransportHealth.warningFor("dial tcp 203.0.113.1:443: connect: connection refused")
        )
    }

    @Test
    fun unrelatedLogMessagesDoNotChangeUiHealth() {
        assertNull(VpnTransportHealth.warningFor("sing-box started (0.09s)"))
    }
}
