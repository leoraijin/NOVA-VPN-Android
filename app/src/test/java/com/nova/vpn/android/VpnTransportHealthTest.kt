package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnTransportHealthTest {
    @Test
    fun certificateMismatchIsReportedWithoutLeakingHostnames() {
        val warning = VpnTransportHealth.warningFor(
            "x509: certificate is valid for unrelated.example, not vpn.example"
        )

        assertEquals(
            "VPN-сервер не прошёл проверку подлинности. Проверьте актуальность профиля у провайдера.",
            warning
        )
        assertNull(warning?.takeIf { "vpn.example" in it || "unrelated.example" in it })
    }

    @Test
    fun expiredCertificateAndTimeoutAreReported() {
        assertEquals(
            "Сертификат VPN-сервера просрочен. Обновите профиль у провайдера.",
            VpnTransportHealth.warningFor("x509: certificate has expired")
        )
        assertEquals(
            "VPN-сервер не отвечает. Проверьте подключение или выберите другой профиль.",
            VpnTransportHealth.warningFor("context deadline exceeded")
        )
    }

    @Test
    fun refusedTcpConnectionIsNotMisreportedAsCredentialRejection() {
        assertEquals(
            "Не удалось подключиться к адресу или порту VPN-сервера. Узел может быть временно недоступен, либо адрес/порт в профиле устарел.",
            VpnTransportHealth.warningFor("dial tcp 203.0.113.1:443: connect: connection refused")
        )
    }

    @Test
    fun unrelatedLogMessagesDoNotChangeUiHealth() {
        assertNull(VpnTransportHealth.warningFor("sing-box started (0.09s)"))
    }
}
