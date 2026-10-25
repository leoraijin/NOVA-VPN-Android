package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class ProbeTargetTest {
    private fun response(target: ProbeTarget, text: String): Boolean = target.readResponse(text.byteInputStream()) { }

    @Test fun requiresExpectedHttpsResponseAndCompleteHeaders() {
        assertTrue(response(ProbeTarget.GOOGLE, "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n"))
        for (text in listOf("HTTP/1.1 200 OK\r\n\r\n", "HTTP/1.1 302 Redirect\r\n\r\n", "HTTP/1.1 204 No Content\r\n")) {
            assertFalse(response(ProbeTarget.GOOGLE, text))
        }
        assertTrue(response(ProbeTarget.CLOUDFLARE, "HTTP/1.1 200 OK\r\n\r\nip=203.0.113.1\n"))
        assertFalse(response(ProbeTarget.CLOUDFLARE, "HTTP/1.1 200 OK\r\nip=header\r\n\r\n"))
    }

    @Test fun hostileResponseCannotAllocateUnboundedLineOrHeaders() {
        for (text in listOf("x".repeat(100_000), "HTTP/1.1 204 No Content\r\n" + "x: y\r\n".repeat(2000))) {
            try { response(ProbeTarget.GOOGLE, text); fail("Oversized response accepted") }
            catch (_: IllegalStateException) { }
        }
    }

    @Test fun googleDnsIsDelegatedToProxy() {
        val destination = ProbeTarget.GOOGLE.socksDestination()
        assertEquals(3, destination[3].toInt())
        assertEquals("www.gstatic.com", destination.copyOfRange(5, destination.size - 2).toString(Charsets.US_ASCII))
        assertEquals(1, ProbeTarget.CLOUDFLARE.socksDestination()[3].toInt())
    }

    @Test fun endpointFailureDoesNotDiscardOtherSuccessfulSamples() {
        assertEquals(ProbeAssessment(30, 3, 6), ProbeAssessment.from(listOf(20, null, 70, null, 30, null)))
        assertEquals(15, ProbeAssessment.from(listOf(10, 20)).millis)
        assertNull(ProbeAssessment.from(listOf(null, 1, null)).millis)
        assertEquals(Int.MAX_VALUE, ProbeAssessment.from(listOf(Int.MAX_VALUE, Int.MAX_VALUE)).millis)
    }
}
