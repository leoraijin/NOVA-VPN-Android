package com.nova.vpn.android

internal enum class DiagnosticAction { CHECK_SERVERS, SUBSCRIPTIONS, SETTINGS, VPN_PERMISSION, NETWORK_SETTINGS, ROUTING }
internal data class DiagnosticAdvice(val title: String, val detail: String, val action: DiagnosticAction, val actionLabel: String)

internal object DiagnosticCatalog {
    private val fixed = setOf("session_start", "engine_sing_box", "engine_reality_core", "engine_setup", "config_validation",
        "engine_started", "engine_config_unsupported", "engine_unavailable", "tunnel_active", "startup_failed", "configuration_rollback", "network_monitor_failed",
        "recovery_wait_network", "recovery_network_changed", "underlay_interface_not_ready", "reality_revision_994ce4ef",
        "reality_protect_failed", "reality_terminal_failure", "probe_start", "probe_success", "probe_invalid_response",
        "auto_check_failed", "auto_switch_limited", "auto_server_applied", "auto_server_not_applied", "network_changed",
        "vpn_permission_required", "vpn_permission_granted", "service_start", "underlay_ready", "underlay_missing",
        "probe_proxy_connected", "probe_target_tls_ready", "probe_proxy_connect_started", "probe_target_tls_started", "probe_https_response_started",
        "dns_system_lookup_started", "dns_system_lookup_not_needed", "dns_system_lookup_ready", "dns_system_lookup_failed", "dns_system_lookup_timeout")
    private val core = setOf("dns_lookup_failed", "dns_exchange_failed", "tls_certificate_failed", "tls_alpn_failed", "tls_handshake_failed",
        "grpc_service_not_found", "http_not_found", "remote_unauthorized", "remote_forbidden", "grpc_unavailable",
        "http2_transport_error", "timeout", "connection_refused", "connection_reset", "network_unreachable",
        "connection_closed", "cancelled", "unclassified_warning")
    fun code(value: String): String = when {
        value in fixed -> value
        value.startsWith("core_") && value.removePrefix("core_") in core -> value
        value.matches(Regex("recovery_retry_(?:[1-9]|[12][0-9]|30)")) -> value
        value.matches(Regex("probe_socks_reply_[1-8]")) -> value
        value.matches(Regex("probe_failed_(local_socket|socks_auth|proxy_connect|target_tls|https_response)_(timeout|tls_error|connect_error|io_error|protocol_error)")) -> value
        value.matches(Regex("underlay_(wifi|cellular|ethernet|other|none)")) -> value
        else -> "unclassified_event"
    }
    fun stage(code: String): DiagnosticStage = when {
        code.startsWith("vpn_permission_") -> DiagnosticStage.PERMISSION
        "dns_" in code -> DiagnosticStage.DNS
        "unauthorized" in code || "forbidden" in code -> DiagnosticStage.AUTHORIZATION
        "socks_reply_" in code -> DiagnosticStage.SERVER
        "tls_" in code || "target_tls" in code -> DiagnosticStage.TLS
        code.startsWith("auto_") -> DiagnosticStage.AUTOMATIC
        code.startsWith("underlay_") || code.startsWith("network_") || code.startsWith("recovery_") -> DiagnosticStage.NETWORK
        code.startsWith("probe_") && ("https_response" in code || code in setOf("probe_success", "probe_invalid_response")) -> DiagnosticStage.INTERNET
        "http" in code || "grpc" in code -> DiagnosticStage.TRANSPORT
        "connection_" in code || "proxy_connect" in code -> DiagnosticStage.SERVER
        code == "configuration_rollback" -> DiagnosticStage.ROUTING
        code.startsWith("engine_") || code in setOf("session_start", "startup_failed", "config_validation", "tunnel_active", "service_start") -> DiagnosticStage.SERVICE
        else -> DiagnosticStage.UNKNOWN
    }
    fun advice(event: DiagnosticEvent?): DiagnosticAdvice = when {
        event?.code in setOf("engine_config_unsupported", "engine_unavailable") -> DiagnosticAdvice("Профиль несовместим с выбранным движком",
            "Проверьте доступность ядра, выберите совместимый движок или обновите подписку.", DiagnosticAction.SETTINGS, "Открыть настройки")
        else -> adviceForStage(event)
    }
    private fun adviceForStage(event: DiagnosticEvent?): DiagnosticAdvice = when (event?.stage) {
        DiagnosticStage.PERMISSION -> DiagnosticAdvice("Нужно разрешение Android", "Разрешите создание VPN при подключении.", DiagnosticAction.VPN_PERMISSION, "Открыть подключение")
        DiagnosticStage.NETWORK -> DiagnosticAdvice("Проверьте подключение к интернету", "Физическая сеть может быть недоступна или ещё не готова.", DiagnosticAction.NETWORK_SETTINGS, "Настройки сети")
        DiagnosticStage.DNS -> DiagnosticAdvice("Не удалось выполнить DNS-запрос", "Повторите проверку; при повторении ошибки проверьте профиль и сеть.", DiagnosticAction.CHECK_SERVERS, "Открыть проверку серверов")
        DiagnosticStage.TLS -> DiagnosticAdvice("Ошибка защищённого соединения", "Проверьте актуальность подписки и параметры TLS/Reality. Точная причина зависит от кода события.", DiagnosticAction.SUBSCRIPTIONS, "Открыть подписки")
        DiagnosticStage.AUTHORIZATION -> DiagnosticAdvice("Сервер отклонил доступ", "Обновите подписку и проверьте срок её действия.", DiagnosticAction.SUBSCRIPTIONS, "Открыть подписки")
        DiagnosticStage.TRANSPORT -> DiagnosticAdvice("Ошибка транспорта", "Проверьте настройки транспорта или выберите другой сервер.", DiagnosticAction.CHECK_SERVERS, "Открыть проверку серверов")
        DiagnosticStage.ROUTING -> DiagnosticAdvice("Конфигурация была восстановлена", "Новый вариант не применился. Проверьте активные правила.", DiagnosticAction.ROUTING, "Открыть маршруты")
        DiagnosticStage.SERVICE -> DiagnosticAdvice("Проверьте запуск VPN", "Если подключение не работает, проверьте профиль и совместимость движка.", DiagnosticAction.SETTINGS, "Открыть настройки")
        else -> DiagnosticAdvice("Проверка подключения", "По имеющимся данным точная причина может быть неизвестна. Проверьте выбранный сервер.", DiagnosticAction.CHECK_SERVERS, "Открыть проверку серверов")
    }
}
