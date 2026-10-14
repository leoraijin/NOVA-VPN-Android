package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidTunMtuTest {
    @Test
    fun `caps oversized jumbo mtu for Android VPN interface`() {
        assertEquals(1500, normalizeAndroidTunMtu(9000))
    }

    @Test
    fun `keeps standard mtu unchanged`() {
        assertEquals(1420, normalizeAndroidTunMtu(1420))
    }

    @Test
    fun `keeps mtu at IPv6 minimum`() {
        assertEquals(1280, normalizeAndroidTunMtu(900))
    }
}
