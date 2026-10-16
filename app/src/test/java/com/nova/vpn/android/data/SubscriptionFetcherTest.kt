package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.util.Base64

class SubscriptionFetcherTest {
    private val url = "https://subscription.example/sub/test"
    private val id = "11111111-1111-1111-1111-111111111111"

    @Test fun missingDeviceIdExplainsOptInAndDoesNotPretendImportWorked() {
        var calls = 0
        val result = runCatching { SubscriptionFetcher.fetchWith(url, null) { _, _, actual ->
            calls++; assertNull(actual)
            SubscriptionFetcher.Response("", hwidMissing = true, deviceLimit = true)
        } }
        assertEquals(1, calls)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Включите"))
    }

    @Test fun deviceLimitHasDistinctMessageAndNoRetry() {
        var calls = 0
        val result = runCatching { SubscriptionFetcher.fetchWith(url, id) { _, _, actual ->
            calls++; assertEquals(id, actual)
            SubscriptionFetcher.Response("", deviceLimit = true)
        } }
        assertEquals(1, calls)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("лимит устройств"))
    }

    @Test fun compatibilityRetryKeepsSameDeviceIdentity() {
        val agents = mutableListOf<String>()
        val body = SubscriptionFetcher.fetchWith(url, id) { _, agent, actual ->
            agents += agent; assertEquals(id, actual)
            SubscriptionFetcher.Response(if (agents.size == 1)
                Base64.getEncoder().encodeToString("vless://fixture@0.0.0.0:1#status".toByteArray())
                else "real response")
        }
        assertEquals("real response", body)
        assertEquals(listOf("NOVA-VPN-Android", "v2raytun/android"), agents)
    }

    @Test fun normalSubscriptionNeedsNoDeviceIdOrRetry() {
        var calls = 0
        assertEquals("response", SubscriptionFetcher.fetchWith(url, null) { _, _, actual ->
            calls++; assertNull(actual); SubscriptionFetcher.Response("response")
        })
        assertEquals(1, calls)
    }

    @Test fun noNetworkForLocalImportOrInvalidIdentity() {
        assertEquals("vless://fixture", SubscriptionFetcher.fetchWith("vless://fixture", null) { _, _, _ -> error("network") })
        for (identity in listOf("short", "bad\r\nheader", "ю".repeat(32), "a".repeat(65))) {
            assertTrue(runCatching { SubscriptionFetcher.fetchWith(url, identity) { _, _, _ -> error("network") } }
                .exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun rejectedIdentityDoesNotSuggestEnablingItAgain() {
        val result = runCatching { SubscriptionFetcher.fetchWith(url, id) { _, _, _ ->
            SubscriptionFetcher.Response("", hwidMissing = true)
        } }
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("не принял"))
    }

    @Test fun identityStablePerOriginAndNotPerSubscriptionOrHardware() {
        val first = SubscriptionDeviceIdentity.derive(id, URI(url))
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertEquals(first, SubscriptionDeviceIdentity.derive(id, URI("https://SUBSCRIPTION.example:443/other")))
        assertNotEquals(first, SubscriptionDeviceIdentity.derive(id, URI("https://other.example/sub/test")))
        assertNotEquals(first, SubscriptionDeviceIdentity.derive(id, URI("https://subscription.example:8443/sub/test")))
        assertNotEquals(first, SubscriptionDeviceIdentity.derive("other-seed", URI(url)))
    }
}
