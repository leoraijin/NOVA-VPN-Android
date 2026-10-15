package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfacePublicationGateTest {
    @Test fun repeatedCapabilitiesDoNotRepublishButNetworkHandoverDoes() {
        val gate = InterfacePublicationGate()
        var updates = 0
        repeat(10) { gate.publish(1, "wlan0", 4) { updates++ } }
        assertEquals(1, updates)
        gate.publish(2, "rmnet0", 5) { updates++ }
        gate.publish(null, "", 0) { updates++ }
        gate.publish(3, "rmnet0", 5) { updates++ }
        assertEquals(4, updates)
    }

    @Test fun lateInterfaceAndFailedPublicationAreRetried() {
        val gate = InterfacePublicationGate()
        var updates = 0
        gate.publish(1, "", 0) { updates++ }
        runCatching { gate.publish(1, "wlan0", 4) { error("listener not ready") } }
        gate.publish(1, "wlan0", 4) { updates++ }
        assertEquals(2, updates)
    }
}
