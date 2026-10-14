package com.nova.vpn.android

/** Maps sing-box transport failures to safe, actionable UI text without exposing node data. */
internal object VpnTransportHealth {
    fun warningFor(logMessage: String): String? {
        val message = logMessage.lowercase()
        return when {
            "x509: certificate has expired" in message || "certificate is expired" in message ->
                "Сертификат VPN-сервера просрочен. Обновите профиль у провайдера."
            "x509: certificate is valid for" in message || "certificate verification failed" in message ->
                "VPN-сервер не прошёл проверку подлинности. Проверьте актуальность профиля у провайдера."
            "context deadline exceeded" in message || "i/o timeout" in message ->
                "VPN-сервер не отвечает. Проверьте подключение или выберите другой профиль."
            "connection refused" in message ->
                "Не удалось подключиться к адресу или порту VPN-сервера. Узел может быть временно недоступен, либо адрес/порт в профиле устарел."
            else -> null
        }
    }
}
