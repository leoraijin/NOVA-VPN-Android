package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch

class DiagnosticDnsProbeTest {
    private fun config(server: String) = """{"route":{"final":"proxy"},"outbounds":[{"tag":"proxy","server":"$server"}]}"""
    @Test fun literalDestinationsDoNotInventDnsQueries() {
        assertNull(DiagnosticDnsProbe.host(config("127.0.0.1")))
        assertNull(DiagnosticDnsProbe.host(config("2001:db8::1")))
        assertEquals("fixture.example", DiagnosticDnsProbe.host(config("fixture.example")))
        assertEquals(DiagnosticDnsResult.NOT_NEEDED, DiagnosticDnsProbe.check(null) { error("Unexpected DNS query") })
    }
    @Test fun boundedDnsReportsOnlyCategoriesNotExceptionSecrets() {
        assertEquals(DiagnosticDnsResult.READY, DiagnosticDnsProbe.check("fixture.example") { true })
        assertEquals(DiagnosticDnsResult.FAILED, DiagnosticDnsProbe.check("fixture.example") { throw IllegalStateException("PRIVATE_SECRET") })
        val blocked = CountDownLatch(1)
        try {
            assertEquals(DiagnosticDnsResult.TIMEOUT, DiagnosticDnsProbe.check("fixture.example", 20) { blocked.await(); true })
        } finally { blocked.countDown() }
    }
}
