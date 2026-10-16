package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.ImportedProfile
import org.json.JSONObject

/** Bounded in-memory event log. Never stores native log text or profile values. */
internal object VpnDiagnostics {
    private val events = ArrayDeque<String>()
    private var started = System.nanoTime()
    private var engine = "sing_box"
    private var engineVersion = "libbox_1.14.1"

    @Synchronized fun setEngine(reality: Boolean) {
        engine = if (reality) "reality_core" else "sing_box"
        engineVersion = if (reality) "0.1.0_51a811f1_nova" else "libbox_1.14.1"
    }
    private val validCode = Regex("[a-z0-9_:=.-]{1,96}")

    @Synchronized fun reset() {
        events.clear()
        started = System.nanoTime()
        record("session_start")
    }

    @Synchronized fun record(code: String) {
        require(code.matches(validCode))
        val event = "${(System.nanoTime() - started) / 1_000_000_000}s $code"
        if (events.lastOrNull() == event) return
        if (events.size == 40) events.removeFirst()
        events.addLast(event)
    }

    fun coreMessage(message: String) = record("core_" + coreCode(message))

    internal fun coreCode(message: String): String {
        val text = message.lowercase()
        return when {
            "no such host" in text || "server misbehaving" in text -> "dns_lookup_failed"
            "dns" in text && ("failed" in text || "timeout" in text) -> "dns_exchange_failed"
            "x509" in text || "certificate verification" in text || "invalid peer certificate" in text -> "tls_certificate_failed"
            "alpn" in text || "no application protocol" in text -> "tls_alpn_failed"
            "handshake" in text && ("failed" in text || "error" in text) -> "tls_handshake_failed"
            "unimplemented" in text || "404" in text -> "grpc_service_not_found"
            "403" in text || "permissiondenied" in text -> "remote_forbidden"
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
        val outbound = runCatching {
            if (profile?.fullConfigJson != null) {
                val config = JSONObject(profile.fullConfigJson)
                val all = config.getJSONArray("outbounds")
                val finalTag = config.optJSONObject("route")?.optString("final")
                (0 until all.length()).mapNotNull(all::optJSONObject)
                    .firstOrNull { it.optString("tag") == finalTag } ?: all.optJSONObject(0)
            } else profile?.let { JSONObject(it.outboundJson) }
        }.getOrNull()
        val tls = outbound?.optJSONObject("tls")
        fun safeChoice(value: String?, allowed: Set<String>) = value?.takeIf { it in allowed } ?: "other"
        return buildString {
            appendLine("NOVA VPN diagnostic ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("android_api=$sdk debug=${BuildConfig.DEBUG}")
            appendLine("engine=$engine engine_version=$engineVersion")
            appendLine("connected=${state.connected} connecting=${state.connecting} verified=${state.trafficVerified} check_failed=${state.trafficCheckFailed}")
            appendLine("protocol=${safeChoice(outbound?.optString("type"), setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "socks", "selector", "urltest"))}")
            appendLine("transport=${safeChoice(outbound?.optJSONObject("transport")?.optString("type") ?: "tcp", setOf("tcp", "ws", "grpc", "http", "httpupgrade", "quic"))}")
            appendLine("tls=${tls?.optBoolean("enabled") == true} sni_present=${!tls?.optString("server_name").isNullOrBlank()} alpn_present=${(tls?.optJSONArray("alpn")?.length() ?: 0) > 0}")
            appendLine("full_json=${profile?.fullConfigJson != null} ipv6_dns=${routing.ipv6Enabled} routing=${routing.mode.name} app_rules=${routing.packages.size} site_rules=${routing.sites.size}")
            appendLine("Events (no keys, hostnames, IPs or native log text):")
            events.forEach { appendLine(it) }
        }
    }
}
