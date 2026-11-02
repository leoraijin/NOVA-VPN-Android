package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SubscriptionFormatNegotiationTest {
    private val url = "https://fixture.example/sub"
    private val first = "vless://11111111-1111-4111-8111-111111111111@one.example:443?security=tls#One"
    private val second = "vless://11111111-1111-4111-8111-111111111111@two.example:443?security=tls#Two"

    @Test fun largerCompatibleCatalogKeepsItsOwnValidatorsAndUsage() {
        val calls = mutableListOf<String>()
        val selected = SubscriptionFetcher.fetchResponseWith(url, null) { _, agent, _ ->
            calls += agent
            if (agent == "NOVA-VPN-Android") SubscriptionFetcher.Response(first, etag = "short-etag", userInfo = "download=1")
            else SubscriptionFetcher.Response("$first\n$second", etag = "full-etag", lastModified = "full-date", userInfo = "download=2")
        }!!
        assertEquals(listOf("NOVA-VPN-Android", "v2raytun/android"), calls)
        assertEquals(2, ProfileLinkParser.parseText(selected.body).size)
        assertEquals("v2raytun/android", selected.requestAgent)
        assertEquals("full-etag", selected.etag)
        assertEquals("full-date", selected.lastModified)
        assertEquals("download=2", selected.userInfo)
    }

    @Test fun smallerInvalidOrFailedAlternativeKeepsTheFirstResponse() {
        for (alternative in listOf("short", "invalid", "failed", "limited")) {
            val selected = SubscriptionFetcher.fetchResponseWith(url, null) { _, agent, _ ->
                if (agent == "NOVA-VPN-Android") SubscriptionFetcher.Response("$first\n$second", etag = "retained")
                else when (alternative) {
                    "failed" -> throw IOException("fixture")
                    "limited" -> SubscriptionFetcher.Response("", deviceLimit = true)
                    "invalid" -> SubscriptionFetcher.Response("not a subscription")
                    else -> SubscriptionFetcher.Response(first, etag = "wrong")
                }
            }!!
            assertEquals("retained", selected.etag)
            assertEquals("NOVA-VPN-Android", selected.requestAgent)
        }
    }

    @Test fun savedRepresentationDoesNotNegotiateAgainAndRetains304() {
        val calls = mutableListOf<String>()
        val selected = SubscriptionFetcher.fetchResponseWith(url, null, preferredAgent = "v2raytun/android") { _, agent, _ ->
            calls += agent
            SubscriptionFetcher.Response("", status = 304, etag = "same")
        }!!
        assertEquals(listOf("v2raytun/android"), calls)
        assertEquals(304, selected.status)
        assertEquals("v2raytun/android", selected.requestAgent)
    }

    @Test fun explicitIdentityFormatIsNeverOverridden() {
        var calls = 0
        SubscriptionFetcher.fetchResponseWith(url, null, negotiate = false) { _, _, _ -> calls++; SubscriptionFetcher.Response(first) }
        assertEquals(1, calls)
    }

    @Test fun representationMarkerRoundTripsAndLegacyDocumentsRemainReadable() {
        val legacy = Subscription(name = "Fixture", url = url).toJson().apply { remove("formatAgent") }
        assertNull(Subscription.fromJson(legacy).formatAgent)
        val source = Subscription(name = "Fixture", url = url, formatAgent = "v2raytun/android", etag = "paired")
        assertEquals(source, Subscription.fromJson(source.toJson()))
        assertThrows(IllegalArgumentException::class.java) { source.copy(formatAgent = "invalid\r\nheader").validate() }
    }
}