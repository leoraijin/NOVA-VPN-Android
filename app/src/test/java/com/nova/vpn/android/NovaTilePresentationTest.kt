package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class NovaTilePresentationTest {
    @Test fun unconfirmedIsStillActiveAndNotAnError() {
        val tile = NovaTilePresentation.from(VpnUiState(connected = true, trafficCheckFailed = true), "Poland")
        assertTrue(tile.active)
        assertTrue(tile.subtitle.contains("не подтверждена"))
        assertFalse(tile.subtitle.contains("Ошибка"))
    }
    @Test fun verifiedTrafficHasBrandAndServer() {
        val tile = NovaTilePresentation.from(VpnUiState(connected = true, trafficVerified = true), "Poland")
        assertTrue(tile.active)
        assertEquals("VPN включён · Poland", tile.subtitle)
        assertTrue(tile.description.startsWith("NOVA VPN."))
    }
    @Test fun disconnectedAndMissingProfileAreInactive() {
        assertEquals("VPN выключен · Poland", NovaTilePresentation.from(VpnUiState(), "Poland").subtitle)
        assertEquals("Добавьте конфигурацию", NovaTilePresentation.from(VpnUiState(), null).subtitle)
        assertFalse(NovaTilePresentation.from(VpnUiState(), "Poland").active)
    }
    @Test fun connectingAndFatalErrorHaveDifferentStates() {
        assertTrue(NovaTilePresentation.from(VpnUiState(connecting = true), "Poland").active)
        val failed = NovaTilePresentation.from(VpnUiState(error = "failed"), "Poland")
        assertFalse(failed.active)
        assertTrue(failed.subtitle.startsWith("Ошибка подключения"))
    }
}
