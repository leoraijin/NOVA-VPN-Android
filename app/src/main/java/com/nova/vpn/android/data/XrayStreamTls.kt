package com.nova.vpn.android.data

import org.json.JSONObject

internal object XrayStreamTls {
    fun fromStream(stream: JSONObject, server: String): JSONObject? {
        val security = text(stream, "security").lowercase()
        if (security.isBlank() || security == "none") return null
        require(security == "tls" || security == "reality") { "Защита Xray пока не поддержана" }
        val field = if (security == "reality") "realitySettings" else "tlsSettings"
        require(!stream.has(field) || stream.isNull(field) || stream.opt(field) is JSONObject) { "Некорректные параметры защиты Xray" }
        val settings = stream.optJSONObject(field) ?: JSONObject()
        val supported = common + if (security == "reality") reality else emptySet()
        settings.keys().forEach { key ->
            if (key !in supported) require(key in defaultOnly && emptyDefault(key, settings.opt(key))) {
                unsupportedReason(key)
            }
        }
        val tls = JSONObject().put("enabled", true)
            .put("server_name", text(settings, "serverName").ifBlank { server })
        if (settings.has("alpn") && !settings.isNull("alpn")) {
            val value = settings.opt("alpn")
            // Xray StringList accepts both an array and a comma-separated string.
            val alpn = if (value is String) org.json.JSONArray(value.split(',')) else settings.optJSONArray("alpn")
            require(alpn != null && (0 until alpn.length()).all { alpn.opt(it) is String }) { "Некорректный ALPN Xray" }
            tls.put("alpn", alpn)
        }
        if (settings.has("allowInsecure") && !settings.isNull("allowInsecure")) {
            require(settings.opt("allowInsecure") is Boolean) { "Некорректный параметр проверки TLS Xray" }
            if (settings.getBoolean("allowInsecure")) tls.put("insecure", true)
        }
        val versions = listOf("1.0", "1.1", "1.2", "1.3")
        val minimum = text(settings, "minVersion")
        val maximum = text(settings, "maxVersion")
        for ((value, target) in listOf(minimum to "min_version", maximum to "max_version")) {
            require(value.isEmpty() || value in versions) { "Некорректная версия TLS Xray" }
            if (value.isNotEmpty()) tls.put(target, value)
        }
        require(minimum.isEmpty() || maximum.isEmpty() || versions.indexOf(minimum) <= versions.indexOf(maximum)) { "Некорректный диапазон версий TLS Xray" }
        text(settings, "fingerprint").takeIf(String::isNotBlank)?.let {
            tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
        }
        if (security == "reality") {
            // Pinned Xray gives its current password spelling precedence over publicKey.
            val password = text(settings, "password")
            val legacyKey = text(settings, "publicKey")
            val key = password.ifEmpty { legacyKey }
            require(key.isNotBlank()) { "Xray Reality не содержит publicKey" }
            if (!tls.has("utls")) tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome"))
            tls.put("reality", JSONObject().put("enabled", true)
                .put("public_key", key).put("short_id", text(settings, "shortId")))
            require(!settings.has("show") || settings.isNull("show") || settings.opt("show") is Boolean) { "Некорректные параметры Reality" }
            // The native client's default spider path is supported; custom probing is not.
            require(text(settings, "spiderX") in setOf("", "/")) { "Параметры обхода Reality пока не поддержаны" }
        }
        return tls
    }
    private fun text(value: JSONObject, key: String): String {
        if (!value.has(key) || value.isNull(key)) return ""
        require(value.opt(key) is String) { "Некорректные параметры защиты Xray" }
        return value.getString(key).also {
            require(it.isEmpty() || it.isNotBlank()) { "Некорректные параметры защиты Xray" }
        }
    }
    private fun unsupportedReason(key: String) = when (key) {
        "mldsa65Verify", "mldsa65Seed" -> "Проверка ML-DSA Reality пока не поддержана этой сборкой клиента"
        "pinnedPeerCertSha256", "verifyPeerCertByName" -> "Закрепление сертификата и дополнительные проверки имени TLS пока не поддержаны при импорте Xray"
        "certificates", "disableSystemRoot", "useSystemCA" -> "Пользовательские настройки доверия сертификатам Xray пока не поддержаны при импорте"
        "echConfigList", "echServerKeys", "echSockopt" -> "Параметры ECH Xray пока не поддержаны при импорте"
        else -> "TLS/Reality содержит неподдерживаемые параметры защиты"
    }
    private fun emptyDefault(key: String, value: Any?): Boolean {
        if (value == null || value == JSONObject.NULL) return true
        return when (key) {
            in stringDefaults -> value is String && value.isEmpty()
            in booleanDefaults -> value == false
            in arrayDefaults -> value is org.json.JSONArray && value.length() == 0
            "xver", "maxTimeDiff" -> value is Number && value.toDouble() == 0.0
            "echSockopt" -> value is JSONObject && value.length() == 0
            "limitFallbackUpload", "limitFallbackDownload" -> value is JSONObject && value.keys().asSequence().all {
                it in setOf("AfterBytes", "BytesPerSec", "BurstBytesPerSec", "afterBytes", "bytesPerSec", "burstBytesPerSec") &&
                    value.opt(it) is Number && (value.opt(it) as Number).toDouble() == 0.0
            }
            else -> false
        }
    }
    private val common = setOf("serverName", "alpn", "allowInsecure", "fingerprint", "minVersion", "maxVersion")
    private val reality = setOf("password", "publicKey", "shortId", "show", "spiderX")
    // Known Xray fields may be present at their inert exported defaults. Nondefault
    // values must not silently lose pinning, trust roots, ECH or Reality verification.
    private val stringDefaults = setOf("cipherSuites", "masterKeyLog", "pinnedPeerCertSha256", "verifyPeerCertByName",
        "echServerKeys", "echConfigList", "type", "privateKey", "minClientVer", "maxClientVer", "mldsa65Seed", "mldsa65Verify")
    private val booleanDefaults = setOf("enableSessionResumption", "disableSystemRoot", "rejectUnknownSni", "useSystemCA")
    private val arrayDefaults = setOf("certificates", "curvePreferences", "serverNames", "shortIds")
    private val defaultOnly = stringDefaults + booleanDefaults + arrayDefaults + setOf("target", "dest", "xver", "maxTimeDiff",
        "echSockopt", "limitFallbackUpload", "limitFallbackDownload")
}
