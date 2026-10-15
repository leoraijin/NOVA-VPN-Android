package com.nova.vpn.android

/** Core logs may describe a destination request, not the VPN endpoint. Do not infer tunnel health from them. */
internal object VpnTransportHealth {
    fun warningFor(logMessage: String): String? {
        val message = logMessage.lowercase()
        return when {
            "no application protocol" in message || "alpn" in message ->
                "Один из запросов завершился ошибкой TLS/ALPN. Это сообщение само по себе не означает отключение VPN."
            "grpc" in message && ("unimplemented" in message || "unexpected http status" in message || "unexpected status" in message || "404" in message) ->
                "Один из gRPC-запросов не был принят. Это сообщение само по себе не означает отключение VPN."
            "dns" in message && ("timeout" in message || "exchange failed" in message || "no such host" in message) ->
                "Не удалось разрешить DNS-имя для одного из запросов. Другие запросы через VPN могут работать."
            "x509: certificate has expired" in message || "certificate is expired" in message ->
                "Один из TLS-запросов отклонён из-за просроченного сертификата. По этому сообщению нельзя определить, относится ли сертификат к VPN-серверу или к адресу назначения."
            "x509: certificate is valid for" in message || "certificate verification failed" in message ->
                "Один из TLS-запросов не прошёл проверку сертификата. Это сообщение не подтверждает неисправность всего VPN-подключения."
            "context deadline exceeded" in message || "i/o timeout" in message ->
                "Истекло время ожидания одного из запросов. VPN может продолжать работать; это не подтверждение отключения туннеля."
            "connection refused" in message ->
                "Одно из соединений получило отказ (connection refused). Это может относиться к отдельному адресу назначения и не означает, что VPN отключён."
            else -> null
        }
    }
}
