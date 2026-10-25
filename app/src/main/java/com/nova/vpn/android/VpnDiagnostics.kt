package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.ImportedProfile
import org.json.JSONObject
import org.json.JSONArray

/** Bounded in-memory event log. Never stores native log text or profile values. */
internal object VpnDiagnostics {
    private var log = DiagnosticLog()
    private var androidClock = false
    private var networkGeneration = 0L
    private var transport = "other"
    private var engine = "sing_box"
    private var engineVersion = BuildConfig.LIBBOX_REVISION
    private var runtimeVersion = "unavailable"

    @Synchronized fun attachAndroidClock() {
        if (!androidClock) { log = DiagnosticLog(android.os.SystemClock::elapsedRealtime); androidClock = true }
    }

    @Synchronized fun networkChanged() { networkGeneration++; record("network_changed") }
    @Synchronized fun setTransport(value: String) { transport = value.takeIf { it in transports } ?: "other" }
    @Synchronized fun setTransportFromConfig(config: String) {
        val outbound = runCatching { outboundFromConfig(JSONObject(config)) }.getOrNull()
        setTransport(if (outbound == null) "other" else outbound.optJSONObject("transport")?.optString("type") ?: "tcp")
    }

    @Synchronized fun setEngine(reality: Boolean) {
        engine = if (reality) "reality_core" else "sing_box"
        engineVersion = if (reality) "0.1.0_994ce4ef_pr34_35" else BuildConfig.LIBBOX_REVISION
        runtimeVersion = "unavailable"
    }
    private val validCode = Regex("[a-z0-9_:=.-]{1,96}")

    @Synchronized fun reset() {
        log.reset()
        record("session_start")
    }

    @Synchronized fun record(code: String, metrics: Map<DiagnosticMetric, Long> = emptyMap()) {
        require(code.matches(validCode))
        val safe = DiagnosticCatalog.code(code)
        if (safe == "engine_started") runtimeVersion = runCatching {
            val value = if (engine == "reality_core") RealityCoreBridge.version() else io.nekohasekai.libbox.Libbox.version()
            value.takeIf { it.matches(Regex("[A-Za-z0-9._+ ()/-]{1,96}")) } ?: "unrecognized"
        }.getOrDefault("unavailable")
        log.append(DiagnosticCatalog.stage(safe), safe, engine, networkGeneration, transport, metrics)
    }

    @Synchronized fun advice(): DiagnosticAdvice = DiagnosticCatalog.advice(log.snapshot().lastOrNull {
        it.code.contains("failed") || it.code.contains("error") || it.code.startsWith("core_") ||
            it.code in setOf("vpn_permission_required", "configuration_rollback", "engine_config_unsupported", "engine_unavailable")
    })
    @Synchronized fun exportEvents(): String = DiagnosticEventCodec.encode(log.snapshot())

    @Synchronized fun snapshot(profile: ImportedProfile?, routing: AppRoutingSettings, sdk: Int, state: VpnUiState): DiagnosticSnapshot {
        val advice = if (state.connected && state.trafficVerified) DiagnosticAdvice("HTTPS через VPN подтверждён",
            "Последняя прикладная проверка прошла успешно. Подробности доступны в отчёте.", DiagnosticAction.CHECK_SERVERS, "Открыть проверку серверов") else advice()
        return DiagnosticSnapshot(report(profile, routing, sdk, state), reportJson(profile, routing, sdk, state), advice)
    }

    @Synchronized fun reportJson(profile: ImportedProfile?, routing: AppRoutingSettings, sdk: Int, state: VpnUiState): String = JSONObject()
        .put("schema", 1).put("app_version", BuildConfig.VERSION_NAME).put("app_code", BuildConfig.VERSION_CODE)
        .put("android_api", sdk).put("engine", engine).put("pinned_revision", engineVersion).put("runtime_version", runtimeVersion)
        .put("state", JSONObject().put("connected", state.connected).put("connecting", state.connecting)
            .put("traffic_verified", state.trafficVerified).put("traffic_check_failed", state.trafficCheckFailed))
        .put("routing", JSONObject().put("mode", routing.mode.name).put("ipv6", routing.ipv6Enabled)
            .put("app_rules", routing.packages.size).put("site_rules", routing.sites.size))
        .put("transport", safeTransport(profile))
        .put("events", JSONArray().apply { log.snapshot().forEach { put(it.toJson()) } }).toString(2)

    fun coreMessage(message: String) = record("core_" + coreCode(message))

    internal fun coreCode(message: String): String {
        val text = message.lowercase()
        return when {
            "no such host" in text || "server misbehaving" in text -> "dns_lookup_failed"
            "dns" in text && ("failed" in text || "timeout" in text) -> "dns_exchange_failed"
            "x509" in text || "certificate verification" in text || "invalid peer certificate" in text -> "tls_certificate_failed"
            "alpn" in text || "no application protocol" in text -> "tls_alpn_failed"
            "handshake" in text && ("failed" in text || "error" in text) -> "tls_handshake_failed"
            ("grpc" in text || "rpc" in text) && ("unimplemented" in text || "404" in text) -> "grpc_service_not_found"
            Regex("\\b404\\b").containsMatchIn(text) -> "http_not_found"
            Regex("\\b401\\b").containsMatchIn(text) || "unauthenticated" in text -> "remote_unauthorized"
            Regex("\\b403\\b").containsMatchIn(text) || "permissiondenied" in text -> "remote_forbidden"
            "unavailable" in text && ("rpc" in text || "grpc" in text) -> "grpc_unavailable"
            "http2" in text || "goaway" in text -> "http2_transport_error"
            "deadline exceeded" in text || "timeout" in text || "timed out" in text -> "timeout"
            "connection refused" in text -> "connection_refused"
            "connection reset" in text -> "connection_reset"
            "unreachable" in text || "no route to host" in text -> "network_unreachable"
            "eof" in text || "closed" in text -> "connection_closed"
            "canceled" in text || "cancelled" in text -> "cancelled"
            else -> "unclassified_warning"
        }
    }

    @Synchronized fun report(profile: ImportedProfile?, routing: AppRoutingSettings, sdk: Int, state: VpnUiState): String {
        val outbound = safeOutbound(profile)
        val tls = outbound?.optJSONObject("tls")
        fun safeChoice(value: String?, allowed: Set<String>) = value?.takeIf { it in allowed } ?: "other"
        return buildString {
            appendLine("NOVA VPN diagnostic ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("android_api=$sdk debug=${BuildConfig.DEBUG}")
            appendLine("engine=$engine engine_version=$engineVersion runtime_version=$runtimeVersion")
            appendLine("connected=${state.connected} connecting=${state.connecting} verified=${state.trafficVerified} check_failed=${state.trafficCheckFailed}")
            appendLine("protocol=${safeChoice(outbound?.optString("type"), setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "socks", "selector", "urltest"))}")
            appendLine("transport=${safeTransport(profile)}")
            appendLine("tls=${tls?.optBoolean("enabled") == true} sni_present=${!tls?.optString("server_name").isNullOrBlank()} alpn_present=${(tls?.optJSONArray("alpn")?.length() ?: 0) > 0}")
            appendLine("full_json=${profile?.fullConfigJson != null} ipv6_dns=${routing.ipv6Enabled} routing=${routing.mode.name} app_rules=${routing.packages.size} site_rules=${routing.sites.size}")
            appendLine("Events (no keys, hostnames, IPs or native log text):")
            log.snapshot().forEach { appendLine("${it.elapsedMillis / 1000}s ${it.code} stage=${it.stage.name.lowercase()} network_generation=${it.networkGeneration}") }
        }
    }

    private val transports = setOf("tcp", "ws", "grpc", "http", "httpupgrade", "quic", "xhttp")
    private fun safeTransport(profile: ImportedProfile?): String {
        val outbound = safeOutbound(profile) ?: return transport
        val value = outbound.optJSONObject("transport")?.optString("type") ?: "tcp"
        return value.takeIf { it in transports } ?: "other"
    }
    private fun outboundFromConfig(config: JSONObject): JSONObject? {
        val all = config.optJSONArray("outbounds") ?: return null
        val finalTag = config.optJSONObject("route")?.optString("final")
        return (0 until all.length()).mapNotNull(all::optJSONObject).firstOrNull { it.optString("tag") == finalTag } ?: all.optJSONObject(0)
    }
    private fun safeOutbound(profile: ImportedProfile?): JSONObject? = runCatching {
            if (profile?.fullConfigJson != null) {
                outboundFromConfig(JSONObject(profile.fullConfigJson))
            } else profile?.let { JSONObject(it.outboundJson) }
        }.getOrNull()
}
