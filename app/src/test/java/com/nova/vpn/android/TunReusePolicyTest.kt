package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class TunReusePolicyTest {
    @Test fun serverAndSiteChangesKeepDeviceButPackageRulesDoNot() {
        val first = listOf("mtu:1500", "address:172.19.0.1/30", "route:0.0.0.0/0", "dns:172.19.0.2", "exclude:com.nova.vpn:10042")
        for (change in listOf("mtu:1400", "route:10.0.0.0/8", "dns:192.0.2.1", "exclude:com.example.other:10043")) {
            assertNotEquals(TunReusePolicy.singBox(first), TunReusePolicy.singBox(first + change))
        }
    }

    @Test fun reorderedAndDuplicateRoutesDoNotCreateANewDevice() {
        val first = listOf("mtu:1500", "route:0.0.0.0/0", "route:::/0")
        assertEquals(TunReusePolicy.singBox(first), TunReusePolicy.singBox(first.reversed() + "route:0.0.0.0/0"))
    }

    @Test fun dnsAndPrimaryAddressPriorityChangesRecreateTheDevice() {
        val first = listOf("dns:0:192.0.2.1", "dns:1:192.0.2.2", "address:0:172.19.0.1/30", "address:1:172.20.0.1/30")
        assertNotEquals(TunReusePolicy.singBox(first), TunReusePolicy.singBox(listOf(
            "dns:0:192.0.2.2", "dns:1:192.0.2.1", "address:0:172.19.0.1/30", "address:1:172.20.0.1/30")))
        assertNotEquals(TunReusePolicy.singBox(first), TunReusePolicy.singBox(listOf(
            "dns:0:192.0.2.1", "dns:1:192.0.2.2", "address:0:172.20.0.1/30", "address:1:172.19.0.1/30")))
    }

    @Test fun realityUsesValidatedFiltersRatherThanUntrustedRawTunFields() {
        val uid: (String) -> Int = { it.hashCode() }
        assertEquals(TunReusePolicy.reality(listOf("a", "b"), emptyList(), uid), TunReusePolicy.reality(listOf("b", "a"), emptyList(), uid))
        assertNotEquals(TunReusePolicy.reality(listOf("a"), emptyList(), uid), TunReusePolicy.reality(emptyList(), listOf("a"), uid))
        assertNotEquals(TunReusePolicy.reality(emptyList(), listOf("a"), uid), TunReusePolicy.reality(emptyList(), listOf("b"), uid))
        assertNotEquals(TunReusePolicy.reality(emptyList(), listOf("a")) { 10001 }, TunReusePolicy.reality(emptyList(), listOf("a")) { 10002 })
    }
}
