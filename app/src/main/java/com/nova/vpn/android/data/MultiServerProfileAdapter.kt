package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Extracts only self-contained server imports; never drops a private routing/DNS policy. */
internal object MultiServerProfileAdapter {
    private const val COMPLEX = "JSON содержит собственные DNS, маршруты или зависимости. Их объединение пока не поддерживается."
    private val protocols = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "hysteria2", "tuic", "socks", "http", "anytls")

    // Cipher registry of the pinned sing-shadowsocks2 core.
    private val shadowsocksMethods = setOf(
        "none", "aes-128-gcm", "aes-192-gcm", "aes-256-gcm",
        "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305",
        "aes-128-ctr", "aes-192-ctr", "aes-256-ctr",
        "aes-128-cfb", "aes-192-cfb", "aes-256-cfb", "rc4-md5", "chacha20-ietf", "xchacha20"
    )
    private val shadowsocks2022KeyLengths = mapOf(
        "2022-blake3-aes-128-gcm" to 16, "2022-blake3-aes-256-gcm" to 32,
        "2022-blake3-chacha20-poly1305" to 32
    )

    fun prepare(profile: ImportedProfile): ImportedProfile {
        require(!profile.retiredFromSubscription) { "Сервер удалён из подписки" }
        val outbound = profile.fullConfigJson?.let { text ->
            val root = JSONObject(text)
            require(root.keys().asSequence().all { it in setOf("outbounds", "route", "log", "inbounds") }) { COMPLEX }
            if (root.has("inbounds")) require(root.optJSONArray("inbounds")?.length() == 0) { COMPLEX }
            val outbounds = root.getJSONArray("outbounds")
            require(outbounds.length() == 1) { COMPLEX }
            val selected = outbounds.getJSONObject(0)
            if (root.has("route")) {
                val route = root.getJSONObject("route")
                require(route.keys().asSequence().all { it in setOf("final", "rules") }) { COMPLEX }
                if (route.has("rules")) require(route.optJSONArray("rules")?.length() == 0) { COMPLEX }
                if (route.has("final")) require(route.opt("final") is String &&
                    route.getString("final").isNotBlank() && route.getString("final") == selected.optString("tag")) { COMPLEX }
            }
            selected
        } ?: JSONObject(profile.outboundJson)
        require(outbound.optString("type") in protocols) { "Протокол несовместим с назначением сервера" }
        require(outbound.optString("server").isNotBlank() && outbound.optInt("server_port") in 1..65535) { "Некорректный адрес сервера" }
        when (outbound.optString("type")) {
            "tuic" -> validateTuic(outbound)
            "shadowsocks" -> validateShadowsocks(outbound)
        }
        require(!outbound.has("detour") && !outbound.has("domain_resolver")) { COMPLEX }
        // Copy only in memory. The stored import and its identity/fingerprint remain unchanged.
        return profile.copy(outboundJson = outbound.toString(), fullConfigJson = null)
    }

    private fun validateShadowsocks(outbound: JSONObject) {
        val method = outbound.opt("method") as? String
        val keyLength = shadowsocks2022KeyLengths[method]
        require(method in shadowsocksMethods || keyLength != null) { "Неизвестный шифр Shadowsocks" }
        val password = outbound.opt("password").takeUnless { it == JSONObject.NULL } ?: ""
        require(password is String && (method == "none" || password.isNotEmpty())) { "Некорректный пароль Shadowsocks" }
        validateShadowsocksPlugin(outbound)
        if (keyLength == null) return
        val keys = password.split(':')
        require(method != "2022-blake3-chacha20-poly1305" || keys.size == 1) {
            "Цепочка ключей Shadowsocks 2022 требует шифр AES"
        }
        for (key in keys) {
            // Go StdEncoding ignores CR/LF but requires padding. Decode only for validation.
            val encoded = key.replace("\r", "").replace("\n", "")
            val size = if (encoded.length % 4 == 0) try {
                Base64.getDecoder().decode(encoded).size
            } catch (_: IllegalArgumentException) { null } else null
            require(size == keyLength) { "Некорректный ключ Shadowsocks 2022" }
        }
    }

    private fun validateShadowsocksPlugin(outbound: JSONObject) {
        fun optionText(key: String): String = when (val value = outbound.opt(key)) {
            null, JSONObject.NULL -> ""
            is String -> value
            is JSONObject -> {
                require(value.length() == 0) { "Некорректные параметры плагина Shadowsocks" }
                ""
            }
            is JSONArray -> {
                require(value.length() == 0) { "Некорректные параметры плагина Shadowsocks" }
                ""
            }
            else -> throw IllegalArgumentException("Некорректные параметры плагина Shadowsocks")
        }
        val plugin = optionText("plugin")
        val encoded = optionText("plugin_opts")
        if (plugin.isEmpty()) return
        require(plugin in setOf("obfs-local", "v2ray-plugin")) { "Неизвестный плагин Shadowsocks" }
        val options = parseSip003Options(encoded)
        if (plugin == "obfs-local") {
            require((options["obfs"] ?: "http") in setOf("http", "tls")) { "Неизвестный режим obfs-local" }
            return
        }
        when (options["mode"] ?: "websocket") {
            "websocket" -> options["mux"]?.let {
                require(it.matches(Regex("[+-]?[0-9]+")) && it.toLongOrNull() != null) { "Некорректное значение mux в v2ray-plugin" }
            }
            "quic" -> require("tls" in options) { "Режим QUIC в v2ray-plugin требует TLS" }
            else -> throw IllegalArgumentException("Неизвестный режим v2ray-plugin")
        }
    }

    /** SIP003 keeps the first value for repeated keys and allows a trailing semicolon. */
    private fun parseSip003Options(encoded: String): Map<String, String> {
        var index = 0
        fun token(stopAtEquals: Boolean): String = buildString {
            while (index < encoded.length) {
                val next = encoded[index]
                if (next == ';' || stopAtEquals && next == '=') break
                index++
                if (next == '\\') {
                    require(index < encoded.length) { "Незавершённая последовательность в параметрах плагина Shadowsocks" }
                    append(encoded[index++])
                } else {
                    append(next)
                }
            }
        }
        val options = mutableMapOf<String, String>()
        while (index < encoded.length) {
            val key = token(stopAtEquals = true)
            require(key.isNotEmpty()) { "Пустой ключ в параметрах плагина Shadowsocks" }
            val value = if (encoded.getOrNull(index) == '=') {
                index++
                token(stopAtEquals = false)
            } else "1"
            options.putIfAbsent(key, value)
            if (index < encoded.length) index++
        }
        return options
    }

    private fun validateTuic(outbound: JSONObject) {
        require(outbound.optJSONObject("tls")?.opt("enabled") == true) { "Для TUIC требуется включённый TLS" }
        val mode = outbound.opt("udp_relay_mode").takeUnless { it == JSONObject.NULL } ?: ""
        require(mode in setOf("", "native", "quic")) { "Неизвестный режим UDP для TUIC" }
        val stream = outbound.opt("udp_over_stream").takeUnless { it == JSONObject.NULL }
        require(stream == null || stream is Boolean) { "Некорректный параметр UDP over stream для TUIC" }
        require(stream != true || mode == "") { "Режим UDP и UDP over stream несовместимы для TUIC" }
        val congestion = outbound.opt("congestion_control").takeUnless { it == JSONObject.NULL } ?: ""
        require(congestion in setOf("", "cubic", "new_reno", "bbr")) { "Неизвестный алгоритм контроля перегрузки TUIC" }

        // VLESS and VMess accept arbitrary string IDs. TUIC uses gofrs/uuid text forms.
        val text = outbound.opt("uuid") as? String
        val plain = when (text?.length) {
            32, 36 -> text
            34, 38 -> text.takeIf { it.startsWith("{") && it.endsWith("}") }?.substring(1, text.length - 1)
            41, 45 -> text.takeIf { it.startsWith("urn:uuid:") }?.substring(9)
            else -> null
        }
        require(plain != null && (plain.matches(Regex("[a-fA-F0-9]{32}")) ||
            plain.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")))) {
            "Некорректный UUID TUIC"
        }
    }

    fun problem(profile: ImportedProfile): String? = try { prepare(profile); null }
        catch (error: IllegalArgumentException) { error.message ?: COMPLEX }
        catch (_: org.json.JSONException) { "Некорректный JSON сервера" }
}
