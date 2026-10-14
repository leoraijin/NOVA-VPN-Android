package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class ConnectionHealthReducerTest {
    @Test fun failedRequestDoesNotInvalidateVerifiedVpn() {
        val before = VpnUiState(connected = true, trafficVerified = true)
        val after = ConnectionHealthReducer.transportNotice(before, "connection refused")
        assertTrue(after.connected)
        assertTrue(after.trafficVerified)
        assertNull(after.warning)
        assertEquals("connection refused", after.transportNotice)
    }

    @Test fun controlAddressFailureMeansUnknownNotDisconnected() {
        val after = ConnectionHealthReducer.probeResult(VpnUiState(connected = true, trafficChecking = true), false)
        assertTrue(after.connected)
        assertTrue(after.trafficCheckFailed)
        assertFalse(after.trafficChecking)
        assertFalse(after.trafficVerified)
        assertNull(after.warning)
    }

    @Test fun successClearsPreviousCheckFailure() {
        val after = ConnectionHealthReducer.probeResult(VpnUiState(connected = true, trafficCheckFailed = true), true)
        assertTrue(after.trafficVerified)
        assertFalse(after.trafficCheckFailed)
        assertNull(after.warning)
    }

    @Test fun lateLogOrProbeCannotReconnectStoppedTunnel() {
        val disconnected = VpnUiState()
        assertEquals(disconnected, ConnectionHealthReducer.transportNotice(disconnected, "error"))
        assertEquals(disconnected, ConnectionHealthReducer.probeResult(disconnected, true))
    }
}
