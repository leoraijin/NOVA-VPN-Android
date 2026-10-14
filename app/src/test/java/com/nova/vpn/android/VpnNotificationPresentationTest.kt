package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnNotificationPresentationTest {
    @Test fun identifiesNovaAndTheActiveProfile() {
        val notification = VpnNotificationPresentation.create("  Польша - Вроцлав  ", "VPN подключён")
        assertEquals("NOVA VPN · Польша - Вроцлав", notification.title)
        assertEquals("VPN подключён", notification.text)
    }

    @Test fun fallsBackForMissingNameAndBoundsUserSuppliedName() {
        assertEquals("NOVA VPN · Выбранная конфигурация", VpnNotificationPresentation.create("  ", "VPN работает").title)
        assertEquals(48, VpnNotificationPresentation.create("x".repeat(80), "VPN работает").title.removePrefix("NOVA VPN · ").length)
    }
}

