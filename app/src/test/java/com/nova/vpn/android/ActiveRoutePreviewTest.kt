package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.junit.Assert.*
import org.junit.Test

class ActiveRoutePreviewTest {
    @Test fun usesAcknowledgedRulesAndOnlyAvailableExits() {
        val fr = ImportedProfile(id = "fr", name = "FR", protocol = "socks", server = "127.0.0.1", port = 1080,
            outboundJson = """{"type":"socks","server":"127.0.0.1","server_port":1080}""", retiredFromSubscription = true)
        val us = fr.copy(id = "us", name = "US", retiredFromSubscription = false)
        val multi = MultiServerSettings(1, "fr", listOf(
            MultiServerRule(packageName = "example.chat", target = ServerRouteTarget(ServerRouteKind.PROFILE, "us")),
            MultiServerRule(domain = "site.example", target = ServerRouteTarget(ServerRouteKind.PROFILE, "us")),
            MultiServerRule(domain = "direct.example", target = ServerRouteTarget(ServerRouteKind.DIRECT))
        ))
        val settings = AppRoutingSettings(multiServer = multi, ipv6Enabled = false)
        val config = SingBoxConfigFactory.build(fr, settings, "com.nova.fixture", listOf(fr, us), "/tmp/active-preview.db")
        val identity = requireNotNull(RoutePolicyIdentity.capture(settings))
        val exits = RouteExitBindings.capture(multi, listOf(fr, us), config)
        val preview = requireNotNull(ActiveRoutePreview.capture(settings, identity, exits, config))

        assertEquals("us", preview.explain("203.0.113.9", "example.chat").profileId)
        assertEquals("us", preview.explain("child.site.example", "example.other").profileId)
        val conflicting = preview.explain("direct.example", "example.chat", listOf("example.chat", "example.other"))
        assertNull(conflicting.profileId)
        assertTrue(conflicting.text.contains("общим UID"))
        val unknown = preview.explain("203.0.113.9", "example.chat", emptyList())
        assertNull(unknown.profileId)
        assertTrue(unknown.text.contains("приложение неизвестно"))
        assertEquals("Напрямую · правило сайта", preview.explain("direct.example", "example.chat").text)
        assertEquals("Блокируется · назначенный сервер недоступен", preview.explain("203.0.113.9", "example.other").text)
        assertEquals("Блокируется · приложение неизвестно", preview.explain("203.0.113.9", "").text)
        assertTrue(preview.explain("198.19.1.2", "example.chat").text.contains("FakeIP"))
        assertTrue(preview.explain("fc00::23", "example.chat").text.contains("FakeIP"))
        assertTrue(preview.explain("2606:4700::1111", "example.chat").text.contains("IPv6"))
        assertNull(ActiveRoutePreview.capture(settings.copy(ipv6Enabled = true), identity, exits, config))
        assertNull(ActiveRoutePreview.capture(settings.copy(multiServer = multi.copy(rules = emptyList())), identity, exits, config))

        VpnRuntime.setConnected(AppliedConfiguration("test", "fr", "test-preset", identity), preview)
        try {
            assertSame(preview, VpnRuntime.state.value.activeRoutePreview)
            VpnRuntime.setRecovery(waiting = true, attempt = 0, tunPresent = true)
            assertNull(VpnRuntime.state.value.activeRoutePreview)
        } finally {
            VpnRuntime.setDisconnected()
        }
    }
}
