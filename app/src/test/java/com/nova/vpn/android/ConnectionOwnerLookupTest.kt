package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class ConnectionOwnerLookupTest {
    @Test fun originalIpv4AndIpv6TuplesReachResolverUnchanged() {
        for ((source, destination) in listOf("172.19.0.2:12345" to "198.18.0.8:443",
            "[fdfe:dcba:9876::2]:12345" to "[fc00::8]:443")) {
            for (protocol in listOf(6, 17)) {
                var calls = 0
                assertEquals(10123L, ConnectionOwnerLookup.resolve(protocol, source, destination) { p, s, d ->
                    calls++
                    assertEquals(protocol, p)
                    assertEquals(12345, s.port)
                    assertEquals(443, d.port)
                    assertFalse(s.isUnresolved)
                    assertEquals(if (source.startsWith("[")) 16 else 4, s.address.address.size)
                    10123
                })
                assertEquals(1, calls)
            }
        }
    }

    @Test fun unknownOwnerExceptionsAndInvalidTuplesFailClosedWithoutLookup() {
        assertEquals(-1L, ConnectionOwnerLookup.resolve(6, "127.0.0.1:12", "127.0.0.2:443") { _, _, _ -> -1 })
        assertEquals(-1L, ConnectionOwnerLookup.resolve(6, "127.0.0.1:12", "127.0.0.2:443") { _, _, _ ->
            throw SecurityException("fixture")
        })
        for (invalid in listOf("example.org:443", "localhost:443", "127.1:443", "[example.org]:443",
            "[::1]", "[::1]:0", "1.2.3.999:443", "127.0.0.1:65536", "[::1%wlan0]:443", "1.2.3.4:-1")) {
            assertEquals(-1L, ConnectionOwnerLookup.resolve(6, invalid, "127.0.0.2:443") { _, _, _ ->
                fail("Malformed tuple must not reach OS lookup"); 42
            })
        }
        assertEquals(-1L, ConnectionOwnerLookup.resolve(1, "127.0.0.1:12", "127.0.0.2:443") { _, _, _ ->
            fail("Unsupported protocol"); 42
        })
    }

    @Test fun ownerIsNotCachedAcrossNewFlows() {
        var uid = 10001
        repeat(2) {
            assertEquals(uid.toLong(), ConnectionOwnerLookup.resolve(17, "127.0.0.1:1234", "127.0.0.2:443") { _, _, _ -> uid })
            uid++
        }
    }
}
