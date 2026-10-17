package com.nova.vpn.android
import org.junit.Assert.assertEquals
import org.junit.Test
class ResponseLatencyTest {
    @Test fun setupAndBodyTimeAreExcluded() {
        var nanos = 900_000_000L // TLS/SOCKS already took 900ms.
        val timer = ResponseLatency { nanos }
        nanos += 42_000_000L
        val headers = timer.millis()
        nanos += 500_000_000L // Validation/body must not change captured result.
        assertEquals(42, headers)
    }
    @Test fun medianIgnoresOneSpikeWithoutScaling() {
        assertEquals(43, ResponseLatency.median(listOf(42, 900, 43)))
    }
    @Test(expected = IllegalArgumentException::class) fun failedSampleIsNotSuccessful() {
        ResponseLatency.median(listOf(42, -1, 43))
    }
    @Test fun subMillisecondRemainsPositive() {
        assertEquals(1, ResponseLatency { 10L }.millis())
    }
}
