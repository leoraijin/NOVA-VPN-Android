package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

/** Xray splits Hysteria2 endpoint/auth between protocol and transport settings. */
internal object XrayHysteria2 {
    fun from(settings: JSONObject, stream: JSONObject): JSONObject {
        val transport = stream.optJSONObject("hysteriaSettings") ?: throw IllegalArgumentException("Отсутствуют настройки Hysteria 2")
        fun version(value: JSONObject) = !value.has("version") || value.opt("version") is Number &&
            (value.opt("version") as Number).toDouble() == 2.0
        require(version(settings) && version(transport)) { "Поддерживается только Hysteria версии 2" }
        require(stream.optString("method").ifBlank { stream.optString("network") }.lowercase() == "hysteria") {
            "Hysteria 2 требует транспорт hysteria"
        }
        val address = settings.optString("address")
        val port = settings.opt("port")
        require(address.isNotBlank() && port is Number && port.toDouble() == port.toInt().toDouble() && port.toInt() in 1..65535) {
            "Hysteria 2 требует корректные адрес и порт"
        }
        val auth = transport.opt("auth")
        require(auth is String && auth.isNotEmpty()) { "Hysteria 2 требует пароль auth" }
        val tls = XrayStreamTls.fromStream(stream, address)
        require(tls != null && tls.optBoolean("enabled") && !tls.has("reality")) { "Hysteria 2 требует TLS" }
        // QUIC uses its own handshake; Xray's TCP uTLS fingerprint is inapplicable here.
        tls.remove("utls")
        val out = JSONObject().put("type", "hysteria2").put("tag", "proxy")
            .put("server", address).put("server_port", port.toInt()).put("password", auth).put("tls", tls)
        require(!stream.has("finalmask") || stream.isNull("finalmask") || stream.opt("finalmask") is JSONObject) { "Некорректная маскировка Hysteria 2" }
        stream.optJSONObject("finalmask")?.let { mask ->
            require(mask.keys().asSequence().all { it in setOf("udp", "tcp", "quicParams") || mask.isNull(it) }) { "Маскировка Hysteria 2 пока не поддержана" }
            for (field in listOf("udp", "tcp")) {
                require(!mask.has(field) || mask.isNull(field) || mask.optJSONArray(field)?.length() == 0) { "Маскировка Hysteria 2 пока не поддержана" }
            }
            require(!mask.has("quicParams") || mask.isNull("quicParams") || mask.opt("quicParams") is JSONObject) { "Некорректные параметры QUIC Hysteria 2" }
            mask.optJSONObject("quicParams")?.let { quic ->
                require(quic.keys().asSequence().all { it in setOf("congestion", "brutalUp", "brutalDown", "udpHop", "debug") || quic.isNull(it) }) {
                    "Параметры QUIC Hysteria 2 пока не поддержаны"
                }
                require(!quic.has("debug") || quic.isNull("debug") || quic.opt("debug") == false) { "Отладочный режим QUIC не переносится в рабочие профили" }
                val congestion = quic.optString("congestion").lowercase().ifBlank { "bbr" }
                require(congestion in setOf("bbr", "brutal")) { "Алгоритм перегрузки Hysteria 2 пока не поддержан" }
                if (congestion == "brutal") {
                    out.put("up_mbps", mbps(quic.optString("brutalUp")))
                    out.put("down_mbps", mbps(quic.optString("brutalDown")))
                }
                require(!quic.has("udpHop") || quic.isNull("udpHop") || quic.opt("udpHop") is JSONObject) { "Некорректные настройки смены портов Hysteria 2" }
                quic.optJSONObject("udpHop")?.let { hop ->
                    val ports = hop.optString("ports").split(',').map(String::trim).filter(String::isNotEmpty)
                    require(ports.isNotEmpty() && ports.all { range ->
                        val bounds = range.split('-'); bounds.size in 1..2 && bounds.all { it.toIntOrNull() in 1..65535 } &&
                            (bounds.size == 1 || bounds[0].toInt() <= bounds[1].toInt())
                    }) { "Некорректные порты Hysteria 2" }
                    out.put("server_ports", JSONArray(ports.map { it.replace('-', ':') }))
                    hop.optString("interval").takeIf(String::isNotBlank)?.let { interval ->
                        require(interval.toIntOrNull()?.let { it > 0 } == true) { "Некорректный интервал Hysteria 2" }
                        out.put("hop_interval", "${interval}s")
                    }
                }
            }
        }
        return out
    }

    private fun mbps(value: String): Int {
        val match = Regex("(?i)^([0-9]+)\\s*(?:mbps|mbit/s|m)$").matchEntire(value.trim())
        return match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Некорректная скорость Brutal Hysteria 2")
    }
}