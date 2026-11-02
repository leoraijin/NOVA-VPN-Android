package com.nova.vpn.android.data

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

class SubscriptionHttpTest {
    private class Connection(url: URL, private val code: Int = 200, private val headers: Map<String, String> = emptyMap(), private val body: String = "body") : HttpsURLConnection(url) {
        var closed = false
        var bodyRead = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        override fun getContentType() = getHeaderField("Content-Type")
        override fun getContentLengthLong() = body.toByteArray().size.toLong()
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray()).also { bodyRead = true }
        override fun getCipherSuite() = "fixture"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test fun choosingAFullerFormatDoesNotReuseLegacyConditionalValidators() {
        val url = URL("https://provider.test/sub")
        val node = "vless://11111111-1111-4111-8111-111111111111@fixture.example:443#Fixture"
        val first = Connection(url, headers = mapOf("ETag" to "short-tag"), body = node)
        val second = Connection(url, headers = mapOf("ETag" to "full-tag"), body = "$node\n${node.replace(":443", ":8443")}")
        var calls = 0
        val selected = SubscriptionFetcher.fetchSubscription(url.toString(), null, "legacy-tag", "legacy-date", open = { if (calls++ == 0) first else second })
        assertEquals("full-tag", selected.etag)
        for (connection in listOf(first, second)) {
            assertNull(connection.getRequestProperty("If-None-Match"))
            assertNull(connection.getRequestProperty("If-Modified-Since"))
        }
    }

    @Test fun fallbackCannotSendThePreviousFormatsValidatorsAndSavedFormatRetains304() {
        val url = URL("https://provider.test/sub")
        val placeholder = Connection(url, body = "vless://11111111-1111-4111-8111-111111111111@0.0.0.0:1#Status")
        val compatible = Connection(url, headers = mapOf("ETag" to "compatible-tag"), body = "vless://11111111-1111-4111-8111-111111111111@fixture.example:443#Fixture")
        var calls = 0
        val selected = SubscriptionFetcher.fetchSubscription(url.toString(), null, "nova-tag", "nova-date", preferredAgent = "NOVA-VPN-Android", open = { if (calls++ == 0) placeholder else compatible })
        assertEquals("nova-tag", placeholder.getRequestProperty("If-None-Match"))
        assertNull(compatible.getRequestProperty("If-None-Match"))
        assertNull(compatible.getRequestProperty("If-Modified-Since"))
        assertEquals("v2raytun/android", selected.requestAgent)
        val notModified = Connection(url, 304, mapOf("ETag" to "compatible-tag"))
        val cached = SubscriptionFetcher.fetchSubscription(url.toString(), null, "compatible-tag", null, preferredAgent = selected.requestAgent, open = { notModified })
        assertEquals(304, cached.status)
        assertEquals("v2raytun/android", notModified.getRequestProperty("User-Agent"))
        assertEquals("compatible-tag", notModified.getRequestProperty("If-None-Match"))
        assertFalse(notModified.bodyRead)
    }

    @Test fun crossOriginRedirectDoesNotReceiveIdentityOrConditionalHeaders() {
        val first = Connection(URL("https://provider.test/sub/TOKEN"), 302, mapOf("Location" to "https://other.test/list"))
        val second = Connection(URL("https://other.test/list"))
        val identity = SubscriptionIdentity(hwid = "PRIVATE_HWID", userAgent = "Private-UA", headers = mapOf("Authorization" to "Bearer PRIVATE_TOKEN"))
        val response = SubscriptionFetcher.download(first.url.toURI(), "NOVA", null, "PRIVATE_ETAG", "Wed, 21 Oct 2015 07:28:00 GMT", identity) {
            if (it.host == "provider.test") first else second
        }
        assertEquals("body", response.body)
        assertEquals("Bearer PRIVATE_TOKEN", first.getRequestProperty("Authorization"))
        assertEquals("PRIVATE_ETAG", first.getRequestProperty("If-None-Match"))
        for (header in listOf("Authorization", "X-HWID", "If-None-Match", "If-Modified-Since")) assertNull(second.getRequestProperty(header))
        assertEquals("NOVA", second.getRequestProperty("User-Agent"))
        assertTrue(first.closed && second.closed)
    }

    @Test fun defaultHttpsPortIsTheSameOriginAndRetainsDeviceConsent() {
        val first = Connection(URL("https://provider.test/sub"), 302, mapOf("Location" to "https://provider.test:443/next"))
        val second = Connection(URL("https://provider.test:443/next"))
        var count = 0
        SubscriptionFetcher.download(first.url.toURI(), "NOVA", "1234567890", "tag", null) { if (count++ == 0) first else second }
        assertEquals("1234567890", second.getRequestProperty("x-hwid"))
        assertEquals("tag", second.getRequestProperty("If-None-Match"))
    }

    @Test fun untrustedRedirectTargetsAreRejectedBeforeOpeningThem() {
        for (location in listOf("http://provider.test/sub", "https://user:password@provider.test/sub")) {
            val first = Connection(URL("https://provider.test/sub"), 302, mapOf("Location" to location))
            var opened = 0
            assertThrows(IllegalArgumentException::class.java) {
                SubscriptionFetcher.download(first.url.toURI(), "NOVA", null, null, null) { opened++; first }
            }
            assertEquals(1, opened)
            assertTrue(first.closed)
        }
    }

    @Test fun notModifiedDoesNotReadBodyAndRateLimitUsesSafeTypedError() {
        val cached = Connection(URL("https://provider.test/sub"), 304, mapOf("ETag" to "new-tag"))
        val response = SubscriptionFetcher.download(cached.url.toURI(), "NOVA", null, "tag", null, open = { cached })
        assertEquals(304, response.status)
        assertFalse(cached.bodyRead)
        val limited = Connection(cached.url, 429, mapOf("Retry-After" to "120"))
        val error = assertThrows(SubscriptionFetcher.FetchFailure::class.java) {
            SubscriptionFetcher.download(limited.url.toURI(), "NOVA", null, null, null, open = { limited })
        }
        assertEquals(120000L, error.retryAfterMillis)
        assertEquals(429, error.status)
        assertFalse(error.message.orEmpty().contains("provider.test"))
        assertTrue(limited.closed)
    }
}
