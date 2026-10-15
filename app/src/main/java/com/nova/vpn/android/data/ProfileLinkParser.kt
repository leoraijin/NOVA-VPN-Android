package com.nova.vpn.android.data

import org.json.JSONObject
import org.json.JSONArray
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Parses common sing-box-compatible share links without logging or exposing credentials. */
object ProfileLinkParser {
    private const val MAX_INPUT_BYTES = 4 * 1024 * 1024
    private const val MAX_PROFILES = 2000

    fun parseText(text: String): List<ImportedProfile> {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_INPUT_BYTES) {
            "Импорт превышает ограничение 4 МБ"
        }
        // QR readers and editors often prefix copied text with a UTF-8 BOM.
        val decoded = decodeSubscription(text.trim().removePrefix("\uFEFF")).trim().removePrefix("\uFEFF")
        val normalized = decoded.trim()
        if (normalized.startsWith("{")) {
            return listOf(parseFullConfig(normalized))
        }
        val lines = decoded.lineSequence().map { it.trim().removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        require(lines.size <= MAX_PROFILES) { "В импорте слишком много профилей" }
        val profiles = mutableListOf<ImportedProfile>()
        val failures = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            try {
                profiles += parseLink(line)
            } catch (error: IllegalArgumentException) {
                failures += "Строка ${index + 1}: ${error.message ?: "неподдерживаемый формат"}"
            }
        }
        require(profiles.isNotEmpty()) {
            failures.firstOrNull() ?: "Не найдено поддерживаемых VPN-конфигураций"
        }
        return ImportedProfile.distinctByConfiguration(profiles)
    }

    private fun parseFullConfig(text: String): ImportedProfile {
        val json = try { JSONObject(text) } catch (_: Exception) {
            throw IllegalArgumentException("Некорректный sing-box JSON")
        }
        val outbounds = json.optJSONArray("outbounds")
        require(outbounds != null && outbounds.length() > 0) {
            "В sing-box JSON должен быть хотя бы один исходящий профиль (outbounds)"
        }
        require((0 until outbounds.length()).all { index ->
            outbounds.optJSONObject(index)?.optString("type").orEmpty().isNotBlank()
        }) {
            "Каждый профиль outbounds должен быть JSON-объектом с указанным type"
        }
        return ImportedProfile(
            name = "sing-box конфигурация",
            protocol = "sing-box",
            server = "",
            port = 0,
            outboundJson = "{}",
            fullConfigJson = json.toString()
        )
    }

    private fun parseLink(link: String): ImportedProfile {
        val uri = parseShareUri(link)
        val protocol = uri.scheme?.lowercase()
            ?: throw IllegalArgumentException("в ссылке нет протокола")
        val outbound = when (protocol) {
            "vless" -> vless(uri)
            "trojan" -> trojan(uri)
            "hysteria2", "hy2" -> hysteria2(uri)
            "tuic" -> tuic(uri)
            "vmess" -> vmess(link)
            "ss" -> shadowsocks(link)
            else -> throw IllegalArgumentException("протокол $protocol пока не поддержан")
        }
        val server = outbound.optString("server")
        val port = outbound.optInt("server_port")
        val fragment = uri.rawFragment?.let(::decodeComponent).orEmpty()
        val name = fragment.ifBlank { outbound.optString("_nova_name") }
            .ifBlank { "$protocol · $server" }
        // Import metadata is for the app UI only and is not part of the sing-box schema.
        outbound.remove("_nova_name")
        return ImportedProfile(
            name = name.take(96),
            protocol = outbound.getString("type"),
            server = server,
            port = port,
            outboundJson = outbound.toString()
        )
    }

    /**
     * QR labels are frequently copied as raw Unicode or with spaces, even though share-link
     * formats require those characters to be percent-encoded. Escape only such characters
     * (and stray percent signs), preserving URI separators and already-valid escapes.
     */
    private fun parseShareUri(link: String): URI {
        val authorityStart = link.indexOf("://").takeIf { it >= 0 }?.plus(3)
        val authorityEnd = authorityStart?.let { start ->
            listOf(link.indexOf('/', start), link.indexOf('?', start), link.indexOf('#', start))
                .filter { it >= 0 }
                .minOrNull()
                ?: link.length
        }
        val hostStart = if (authorityStart != null && authorityEnd != null) {
            (link.lastIndexOf('@', authorityEnd - 1).takeIf { it >= authorityStart } ?: (authorityStart - 1)) + 1
        } else -1
        val ipv6Host = if (hostStart >= 0 && authorityEnd != null) {
            link.substring(hostStart, authorityEnd).let { it.startsWith("[") && it.contains(']') }
        } else false
        val normalized = buildString(link.length) {
            var index = 0
            while (index < link.length) {
                val codePoint = link.codePointAt(index)
                when {
                    codePoint == '%'.code -> {
                        val hasEscape = index + 2 < link.length &&
                            link[index + 1].digitToIntOrNull(16) != null &&
                            link[index + 2].digitToIntOrNull(16) != null
                        if (hasEscape) append('%') else append("%25")
                    }
                    Character.isWhitespace(codePoint) -> append("%20")
                    codePoint < 0x20 || codePoint == 0x7F ->
                        throw IllegalArgumentException("ссылка содержит управляющий символ")
                    codePoint > 0x7E || codePoint in URI_ASCII_CHARS_TO_ESCAPE ||
                        (codePoint == '['.code || codePoint == ']'.code) &&
                        !(ipv6Host && index in hostStart until (authorityEnd ?: hostStart)) ->
                        append(percentEncode(link.substring(index, index + Character.charCount(codePoint))))
                    else -> appendCodePoint(codePoint)
                }
                index += Character.charCount(codePoint)
                if (codePoint == '%'.code && index < link.length && normalizedEscapeAt(link, index)) {
                    // Keep a valid %HH triplet intact.
                    append(link.substring(index, index + 2))
                    index += 2
                }
            }
        }
        return try {
            URI(normalized)
        } catch (error: URISyntaxException) {
            val reason = when {
                error.reason.orEmpty().contains("escape", ignoreCase = true) -> "неверное URL-кодирование"
                error.reason.orEmpty().contains("character", ignoreCase = true) -> "недопустимый символ"
                else -> "ошибка формата URI"
            }
            throw IllegalArgumentException("некорректная ссылка: $reason")
        }
    }

    private fun percentEncode(value: String): String = buildString {
        value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
            append("%%%02X".format(byte.toInt() and 0xFF))
        }
    }

    private fun normalizedEscapeAt(value: String, index: Int): Boolean =
        index + 1 < value.length && value[index].digitToIntOrNull(16) != null &&
            value[index + 1].digitToIntOrNull(16) != null

    private val URI_ASCII_CHARS_TO_ESCAPE = setOf(34, 60, 62, 92, 94, 96, 123, 124, 125)

    private fun vless(uri: URI): JSONObject {
        val query = query(uri)
        val out = baseOutbound("vless", uri).put("uuid", decodeComponent(uri.rawUserInfo.orEmpty()))
        query["flow"]?.takeIf(String::isNotBlank)?.let { out.put("flow", it) }
        addTls(out, query, serverNameFallback = uri.host.orEmpty())
        addTransport(out, query)
        return out
    }

    private fun trojan(uri: URI): JSONObject {
        val query = query(uri)
        val out = baseOutbound("trojan", uri).put("password", decodeComponent(uri.rawUserInfo.orEmpty()))
        addTls(out, query, defaultEnabled = true, serverNameFallback = uri.host.orEmpty())
        addTransport(out, query)
        return out
    }

    private fun hysteria2(uri: URI): JSONObject {
        val query = query(uri)
        val out = baseOutbound("hysteria2", uri)
            .put("password", decodeComponent(uri.rawUserInfo.orEmpty()))
        addTls(out, query, defaultEnabled = true)
        query["obfs"]?.takeIf(String::isNotBlank)?.let { obfs ->
            require(obfs == "salamander") { "поддерживается только obfs salamander" }
            out.put("obfs", JSONObject().put("type", obfs).put("password", query["obfs-password"].orEmpty()))
        }
        query["up"]?.toIntOrNull()?.takeIf { it > 0 }?.let { out.put("up_mbps", it) }
        query["down"]?.toIntOrNull()?.takeIf { it > 0 }?.let { out.put("down_mbps", it) }
        return out
    }

    private fun tuic(uri: URI): JSONObject {
        val query = query(uri)
        val credential = decodeComponent(uri.rawUserInfo.orEmpty())
        val split = credential.indexOf(':')
        require(split > 0) { "TUIC требует UUID и пароль" }
        val out = baseOutbound("tuic", uri)
            .put("uuid", credential.substring(0, split))
            .put("password", credential.substring(split + 1))
            .put("congestion_control", query["congestion_control"] ?: "bbr")
        addTls(out, query, defaultEnabled = true)
        return out
    }

    private fun vmess(link: String): JSONObject {
        val encoded = link.substringAfter("vmess://", "").substringBefore('#')
        val payload = decodeBase64(encoded)
        val source = try { JSONObject(payload) } catch (_: Exception) {
            throw IllegalArgumentException("Некорректный VMess JSON")
        }
        val server = source.optString("add")
        val port = source.optInt("port")
        require(server.isNotBlank() && port in 1..65535 && source.optString("id").isNotBlank()) {
            "в VMess не заполнен сервер, порт или UUID"
        }
        val out = JSONObject()
            .put("type", "vmess")
            .put("tag", "proxy")
            .put("server", server)
            .put("server_port", port)
            .put("uuid", source.getString("id"))
            .put("alter_id", source.optInt("aid", 0))
        source.optString("ps").takeIf(String::isNotBlank)?.let { out.put("_nova_name", it) }
        if (source.optString("tls").equals("tls", true)) {
            out.put("tls", JSONObject().put("enabled", true)
                .put("server_name", source.optString("sni").ifBlank { source.optString("host").ifBlank { server } })
                .put("insecure", source.optBoolean("allowInsecure", false)))
            source.optString("alpn").split(',').map(String::trim).filter(String::isNotBlank)
                .takeIf { it.isNotEmpty() }?.let { out.getJSONObject("tls").put("alpn", JSONArray(it)) }
        }
        val network = source.optString("net", "tcp").lowercase()
        if (network == "ws") {
            out.put("transport", JSONObject().put("type", "ws")
                .put("path", source.optString("path", "/"))
                .put("headers", JSONObject().put("Host", source.optString("host"))))
        } else if (network == "grpc") {
            out.put("transport", JSONObject().put("type", "grpc")
                .put("service_name", source.optString("serviceName").ifBlank { source.optString("path") }))
        } else if (network != "tcp") {
            throw IllegalArgumentException("VMess транспорт $network требует отдельной проверки")
        }
        return out
    }

    private fun shadowsocks(link: String): JSONObject {
        val body = link.removePrefix("ss://")
        val fragmentAt = body.indexOf('#')
        val label = if (fragmentAt >= 0) decodeComponent(body.substring(fragmentAt + 1)) else ""
        val withoutLabel = if (fragmentAt >= 0) body.substring(0, fragmentAt) else body
        val decodedWhole = runCatching { decodeBase64(withoutLabel) }.getOrNull()
            ?.takeIf { it.substringBeforeLast('@', "").contains(':') && '@' in it }
        val normalized = decodedWhole ?: withoutLabel
        val at = normalized.lastIndexOf('@')
        require(at > 0) { "SS-ссылка должна содержать method:password@server:port" }
        val userInfo = decodeMaybeBase64(normalized.substring(0, at))
        val split = userInfo.indexOf(':')
        require(split > 0) { "в SS-ссылке не найдена пара method:password" }
        val endpoint = URI("ss://${normalized.substring(at + 1)}")
        val out = JSONObject()
            .put("type", "shadowsocks")
            .put("tag", "proxy")
            .put("server", endpoint.host ?: throw IllegalArgumentException("в SS-ссылке отсутствует сервер"))
            .put("server_port", endpoint.port)
            .put("method", userInfo.substring(0, split))
            .put("password", userInfo.substring(split + 1))
        require(endpoint.port in 1..65535)
        if (label.isNotBlank()) out.put("_nova_name", label)
        return out
    }

    private fun baseOutbound(type: String, uri: URI): JSONObject {
        val server = uri.host ?: throw IllegalArgumentException("в ссылке отсутствует адрес сервера")
        require(uri.port in 1..65535) { "в ссылке указан неверный порт" }
        return JSONObject().put("type", type).put("tag", "proxy")
            .put("server", server).put("server_port", uri.port)
    }

    private fun addTls(
        out: JSONObject,
        query: Map<String, String>,
        defaultEnabled: Boolean = false,
        serverNameFallback: String = ""
    ) {
        val security = query["security"]?.lowercase().orEmpty()
        val enabled = defaultEnabled || security == "tls" || security == "reality" ||
            query["sni"] != null || query["servername"] != null
        if (!enabled) return
        val tls = JSONObject().put("enabled", true)
            .put("server_name", sequenceOf(query["sni"], query["servername"], query["peer"], serverNameFallback)
                .firstOrNull { !it.isNullOrBlank() }.orEmpty())
        query["alpn"]?.split(',')?.map(String::trim)?.filter(String::isNotBlank)
            ?.takeIf { it.isNotEmpty() }?.let { tls.put("alpn", JSONArray(it)) }
        val insecure = query["allowinsecure"] == "1" || query["insecure"] == "1"
        if (insecure) tls.put("insecure", true)
        if (security == "reality") {
            val publicKey = query["pbk"] ?: query["publickey"]
            require(!publicKey.isNullOrBlank()) { "Reality-ссылка не содержит pbk" }
            val fingerprint = query["fp"]?.takeIf(String::isNotBlank) ?: "chrome"
            tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", fingerprint))
            tls.put("reality", JSONObject().put("enabled", true)
                .put("public_key", publicKey).put("short_id", query["sid"].orEmpty()))
        } else {
            query["fp"]?.takeIf(String::isNotBlank)?.let {
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
            }
        }
        out.put("tls", tls)
    }

    private fun addTransport(out: JSONObject, query: Map<String, String>) {
        when (val type = query["type"]?.lowercase() ?: "tcp") {
            "tcp", "" -> Unit
            "ws" -> {
                val headers = JSONObject()
                query["host"]?.takeIf(String::isNotBlank)?.let { headers.put("Host", it) }
                out.put("transport", JSONObject().put("type", "ws")
                    .put("path", query["path"] ?: "/").put("headers", headers))
            }
            "grpc" -> out.put("transport", JSONObject().put("type", "grpc")
                .put("service_name", query["serviceName"] ?: query["servicename"] ?: query["path"].orEmpty()))
            else -> throw IllegalArgumentException("транспорт $type пока не поддержан")
        }
    }

    private fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty()
        .split('&').filter(String::isNotBlank).associate { pair ->
            val index = pair.indexOf('=')
            val key = if (index < 0) pair else pair.substring(0, index)
            val value = if (index < 0) "" else pair.substring(index + 1)
            decodeComponent(key).lowercase() to decodeComponent(value)
        }

    private fun decodeSubscription(text: String): String {
        val normalized = text.trim()
        if (normalized.startsWith("{") || normalized.contains("://")) return normalized
        val decoded = runCatching { decodeBase64(normalized) }.getOrNull()
        return decoded?.takeIf { it.contains("://") || it.trimStart().startsWith("{") } ?: normalized
    }

    private fun decodeMaybeBase64(value: String): String {
        val decodedComponent = decodeComponent(value)
        if (':' in decodedComponent) return decodedComponent
        return runCatching { decodeBase64(value) }.getOrDefault(decodedComponent)
    }

    private fun decodeBase64(value: String): String {
        val trimmed = value.trim().replace('-', '+').replace('_', '/')
        val padded = trimmed + "=".repeat((4 - trimmed.length % 4) % 4)
        return try {
            String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            String(Base64.getMimeDecoder().decode(padded), StandardCharsets.UTF_8)
        }
    }

    private fun decodeComponent(value: String): String = URLDecoder.decode(
        value.replace("+", "%2B"), StandardCharsets.UTF_8.name()
    )
}
