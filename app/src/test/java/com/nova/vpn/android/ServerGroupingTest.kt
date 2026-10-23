package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import org.junit.Assert.*
import org.junit.Test

class ServerGroupingTest {
    @Test fun reimportKeepsSelectedIdAndAddsSubscriptionWithoutDuplicatingServer() {
        val saved = profile().copy(latencyMillis = 45)
        val incoming = profile().copy(subscriptionUrl = "https://panel.example/subscription-token")
        val merged = ImportedProfile.merge(listOf(saved), listOf(incoming)).single()
        assertEquals(saved.id, merged.id)
        assertEquals(45, merged.latencyMillis)
        assertEquals(incoming.subscriptionUrl, merged.subscriptionUrl)
    }
    private fun profile(name: String = "🇩🇪 Germany", transport: String = "xhttp") = ImportedProfile(
        name = name, protocol = "vless", server = "fixture.example", port = 443,
        outboundJson = """{"type":"vless","transport":{"type":"$transport"}}""")
    @Test fun groupsByActualTransportNotLabel() {
        assertEquals("XHTTP", profileGroup(profile(name = "WS server"), ServerGrouping.TRANSPORT) { null })
        assertEquals("VLESS", profileGroup(profile(), ServerGrouping.PROTOCOL) { null })
    }
    @Test fun unknownCountryStaysVisibleAndSubscriptionTokensAreNotDisplayed() {
        assertEquals("Страна не указана", profileGroup(profile(), ServerGrouping.COUNTRY) { null })
        assertEquals("Германия", profileGroup(profile(), ServerGrouping.COUNTRY) { "Германия" })
        assertEquals("panel.example", profileGroup(profile().copy(subscriptionUrl = "https://panel.example/private-token"), ServerGrouping.SUBSCRIPTION) { null })
        assertEquals("Добавлены вручную", profileGroup(profile(), ServerGrouping.SUBSCRIPTION) { null })
    }
}
