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

    fun fetchIfUrl(input: String, deviceId: String? = null): String =
        fetchWith(input, deviceId, ::download)

    internal data class Response(val body: String, val hwidMissing: Boolean = false, val deviceLimit: Boolean = false,
        val status: Int = 200, val etag: String? = null, val lastModified: String? = null, val userInfo: String? = null,
        val requestAgent: String? = null)

    internal class FetchFailure(val status: Int, val retryAfterMillis: Long = 0) : IOException("Ошибка обновления подписки (HTTP $status).")

    internal val FORMAT_AGENTS = setOf("NOVA-VPN-Android", "v2raytun/android")

    internal fun fetchSubscription(url: String, deviceId: String?, etag: String?, lastModified: String?,
        identity: SubscriptionIdentity? = null, preferredAgent: String? = null,
        open: (URI) -> HttpsURLConnection = { uri -> uri.toURL().openConnection() as? HttpsURLConnection
            ?: throw IOException("Не удалось открыть защищённое соединение.") }): Response {
        identity?.validate()
        return requireNotNull(fetchResponseWith(url, deviceId, preferredAgent, identity?.userAgent == null) { uri, agent, providedDeviceId ->
            // Legacy caches have no format marker. Negotiate once with fresh bodies;
            // conditional validators must belong to the selected representation.
            download(uri, agent, providedDeviceId, etag.takeIf { preferredAgent == agent },
                lastModified.takeIf { preferredAgent == agent }, identity, open)
        }) { "Нужна HTTPS-ссылка на подписку" }
    }

    internal fun fetchWith(input: String, deviceId: String?, request: (URI, String, String?) -> Response): String =
        fetchResponseWith(input, deviceId, request = request)?.body ?: input

    internal fun fetchResponseWith(input: String, deviceId: String?, preferredAgent: String? = null,
        negotiate: Boolean = true, request: (URI, String, String?) -> Response): Response? {
        val text = input.trim()
        val uri = runCatching { URI(text) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        require(scheme == "https") { "Ссылка на подписку должна использовать HTTPS." }
        require(uri.host != null && uri.userInfo == null && (uri.port == -1 || uri.port in 1..65535)) {
            "Некорректная HTTPS-ссылка на подписку."
        }
        require(text.length <= 4096) { "Ссылка на подписку слишком длинная." }
        require(deviceId == null || deviceId.matches(Regex("[a-zA-Z0-9=-]{10,64}"))) {
            "Некорректный идентификатор устройства подписки."
        }
        require(preferredAgent == null || preferredAgent in FORMAT_AGENTS)
        fun checked(agent: String): Response {
            val response = request(uri, agent, deviceId)
            require(!response.hwidMissing) {
                if (deviceId == null) "Провайдер требует HWID. Включите ниже передачу идентификатора устройства и повторите импорт."
                else "Провайдер не принял идентификатор устройства. Обратитесь в поддержку подписки."
            }
            require(!response.deviceLimit) {
                "Достигнут лимит устройств подписки. Удалите старое устройство в кабинете провайдера или обратитесь в поддержку."
            }
            return response.copy(requestAgent = agent)
        }
        val response = checked(preferredAgent ?: "NOVA-VPN-Android")
        if (response.status == 304) return response
        if (isUnsupportedPlaceholder(response.body) && response.requestAgent != "v2raytun/android" && negotiate) {
            return checked("v2raytun/android")
        }
        // Panels can return a short URI list for an unknown client and their full
        // Xray catalog for a compatible client, without an unsupported-app marker.
        // Negotiate only before the representation has been saved with its validators.
        if (preferredAgent != null || !negotiate) return response
        fun usable(body: String) = runCatching { ProfileLinkParser.parseReport(body).profiles.size }.getOrDefault(0)
        val currentCount = usable(response.body)
        if (currentCount == 0) return response
        val compatible = runCatching { checked("v2raytun/android") }.getOrNull() ?: return response
        return if (compatible.status == 200 && usable(compatible.body) > currentCount) compatible else response
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

    private fun download(initialUri: URI, userAgent: String, deviceId: String?): Response = download(initialUri, userAgent, deviceId, null, null)

    internal fun download(initialUri: URI, userAgent: String, deviceId: String?, etag: String?, lastModified: String?, identity: SubscriptionIdentity? = null,
        open: (URI) -> HttpsURLConnection = { uri -> uri.toURL().openConnection() as? HttpsURLConnection
            ?: throw IOException("Не удалось открыть защищённое соединение.") }): Response {
        var current = initialUri
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            require(current.scheme.equals("https", ignoreCase = true) && current.host != null && current.userInfo == null &&
                (current.port == -1 || current.port in 1..65535)) {
                "Сервер перенаправил подписку на небезопасное соединение."
            }
            val connection = open(current)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "text/plain, application/json, */*;q=0.5")
                connection.setRequestProperty("User-Agent", userAgent)
                val sameOrigin = current.host.equals(initialUri.host, ignoreCase = true) &&
                    (if (current.port == -1) 443 else current.port) == (if (initialUri.port == -1) 443 else initialUri.port)
                if (sameOrigin) {
                    identity?.requestHeaders()?.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    etag?.takeIf { it.length <= 1024 && '\r' !in it && '\n' !in it }?.let { connection.setRequestProperty("If-None-Match", it) }
                    lastModified?.takeIf { it.length <= 128 && '\r' !in it && '\n' !in it }?.let { connection.setRequestProperty("If-Modified-Since", it) }
                }
                // A redirect to another origin must not receive this provider's identifier.
                if (deviceId != null && sameOrigin) {
                    connection.setRequestProperty("x-hwid", deviceId)
                    connection.setRequestProperty("x-device-os", "Android")
                }

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
                val hwidMissing = connection.getHeaderField("x-hwid-not-supported").equals("true", ignoreCase = true)
                val deviceLimit = !hwidMissing && (
                    connection.getHeaderField("x-hwid-max-devices-reached").equals("true", ignoreCase = true) ||
                    connection.getHeaderField("x-hwid-limit").equals("true", ignoreCase = true))
                if (hwidMissing || deviceLimit) return Response("", hwidMissing, deviceLimit)
                fun header(name: String, limit: Int) = connection.getHeaderField(name)?.takeIf { it.length <= limit && '\r' !in it && '\n' !in it }
                if (status == 304) return Response("", status = status, etag = header("ETag", 1024),
                    lastModified = header("Last-Modified", 128), userInfo = header("subscription-userinfo", 2048))
                if (status !in 200..299) {
                    val delay = header("Retry-After", 128)?.let { value ->
                        value.toLongOrNull()?.coerceIn(0, 86400)?.times(1000) ?: runCatching {
                            (java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toInstant().toEpochMilli() - System.currentTimeMillis()).coerceIn(0, 86400000)
                        }.getOrDefault(0)
                    } ?: 0
                    throw FetchFailure(status, delay)
                }
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
                return Response(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"), status = status,
                    etag = header("ETag", 1024), lastModified = header("Last-Modified", 128), userInfo = header("subscription-userinfo", 2048))
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
