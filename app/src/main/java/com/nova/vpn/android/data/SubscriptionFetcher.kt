package com.nova.vpn.android.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Fetches user-supplied HTTPS subscription URLs without persisting or logging the URL. */
object SubscriptionFetcher {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 15_000

    fun fetchIfUrl(input: String): String {
        val text = input.trim()
        val uri = runCatching { URI(text) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return input
        require(scheme == "https") { "Ссылка на подписку должна использовать HTTPS." }
        require(uri.host != null && uri.userInfo == null && (uri.port == -1 || uri.port in 1..65535)) {
            "Некорректная HTTPS-ссылка на подписку."
        }
        require(text.length <= 4096) { "Ссылка на подписку слишком длинная." }
        val response = download(uri, "NOVA-VPN-Android")
        // Some subscription panels select a client-specific format from User-Agent.
        // Retry only when the first response is the known unusable 0.0.0.0:1 placeholder.
        return if (isUnsupportedPlaceholder(response)) {
            download(uri, "v2raytun/android")
        } else response
    }

    private fun isUnsupportedPlaceholder(body: String): Boolean = runCatching {
        val raw = body.trim()
        val decoded = if (raw.startsWith("vless://")) raw else {
            val padded = raw.replace('-', '+').replace('_', '/') + "=".repeat((4 - raw.length % 4) % 4)
            runCatching { String(java.util.Base64.getMimeDecoder().decode(padded), Charsets.UTF_8) }
                .getOrDefault(raw).trim()
        }
        val link = decoded.lineSequence().firstOrNull { it.trimStart().startsWith("vless://") }
            ?.trim()?.substringBefore('#') ?: return@runCatching false
        val uri = URI(link)
        uri.host == "0.0.0.0" && uri.port == 1
    }.getOrDefault(false)

    private fun download(initialUri: URI, userAgent: String): String {
        var current = initialUri
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            require(current.scheme.equals("https", ignoreCase = true)) {
                "Сервер перенаправил подписку на небезопасное соединение."
            }
            val connection = current.toURL().openConnection() as? HttpsURLConnection
                ?: throw IOException("Не удалось открыть защищённое соединение.")
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "text/plain, application/json, */*;q=0.5")
                connection.setRequestProperty("User-Agent", userAgent)

                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    if (redirectIndex == MAX_REDIRECTS) throw IOException("Слишком много перенаправлений подписки.")
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Сервер вернул перенаправление без адреса.")
                    current = current.resolve(location)
                    require(current.scheme.equals("https", ignoreCase = true) && current.host != null) {
                        "Сервер перенаправил подписку на небезопасный адрес."
                    }
                    return@repeat
                }
                if (status !in 200..299) throw IOException("Не удалось скачать подписку (HTTP $status).")
                val declaredLength = connection.contentLengthLong
                if (declaredLength > MAX_BYTES) throw IOException("Файл подписки превышает 4 МБ.")
                val contentType = connection.contentType.orEmpty().lowercase()
                if (contentType.contains("text/html")) throw IOException("Вместо подписки сервер вернул HTML-страницу.")

                val bytes = connection.inputStream.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_BYTES) throw IOException("Файл подписки превышает 4 МБ.")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Не удалось загрузить подписку.")
    }

    private val REDIRECT_CODES = setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308
    )
}
