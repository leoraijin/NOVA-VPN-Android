package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassPolicyTest {
    @Test fun olderDevicesUseOpaqueMaterial() {
        for (sdk in listOf(24, 26, 30)) assertEquals(0, glassTier(sdk, false, true))
    }
    @Test fun android12UsesBlur() {
        for (sdk in listOf(31, 32)) assertEquals(1, glassTier(sdk, false, true))
    }
    @Test fun newerDevicesSupportRefraction() {
        for (sdk in listOf(33, 34, 35)) assertEquals(2, glassTier(sdk, false, true))
    }
    @Test fun lowMemoryAndReducedEffectsAlwaysUseOpaqueMaterial() {
        for (sdk in listOf(24, 31, 35)) {
            assertEquals(0, glassTier(sdk, true, true))
            assertEquals(0, glassTier(sdk, false, false))
        }
    }
}
