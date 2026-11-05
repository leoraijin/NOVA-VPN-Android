package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class DiagnosticDnsProbeTest {
    @Test fun timedOutUninterruptibleLookupDoesNotAccumulateWorkers() {
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val started = AtomicInteger()
        try {
            assertEquals(DiagnosticDnsResult.TIMEOUT, DiagnosticDnsProbe.check("fixture.example", 40) {
                started.incrementAndGet()
                try {
                    while (release.count > 0) {
                        try { release.await() } catch (_: InterruptedException) { /* Model native resolver ignoring interruption. */ }
                    }
                    true
                } finally { finished.countDown() }
            })
            repeat(10) { assertEquals(DiagnosticDnsResult.BUSY, DiagnosticDnsProbe.check("other.example", 10) { started.incrementAndGet(); true }) }
            assertEquals("A timed-out native lookup must not admit more DNS workers", 1, started.get())
        } finally {
            release.countDown()
            assertTrue("Own fake DNS worker exits after release", finished.await(1, TimeUnit.SECONDS))
        }
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        var next: DiagnosticDnsResult
        do { next = DiagnosticDnsProbe.check("recovered.example", 20) { true }; if (next == DiagnosticDnsResult.BUSY) Thread.yield() }
        while (next == DiagnosticDnsResult.BUSY && System.nanoTime() < until)
        assertEquals("New attempts resume after actual worker exit", DiagnosticDnsResult.READY, next)
    }
    @Test fun alreadyInterruptedCallerDoesNotStartLookup() {
        Thread.currentThread().interrupt()
        try {
            assertEquals(DiagnosticDnsResult.CANCELLED, DiagnosticDnsProbe.check("fixture.example") { error("Cancelled caller must not start DNS") })
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
    @Test fun interruptWhileWaitingPreservesCancellationAndNativeWorkerBound() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val result = AtomicReference<DiagnosticDnsResult>()
        val interrupted = AtomicBoolean()
        val caller = Thread {
            result.set(DiagnosticDnsProbe.check("fixture.example", 5000) {
                entered.countDown()
                try {
                    while (release.count > 0) {
                        try { release.await() } catch (_: InterruptedException) { }
                    }
                    true
                } finally { exited.countDown() }
            })
            interrupted.set(Thread.currentThread().isInterrupted)
        }
        try {
            caller.start()
            assertTrue("Fake native lookup started", entered.await(2, TimeUnit.SECONDS))
            caller.interrupt()
            caller.join(2000)
            assertFalse("Cancelled observer exits without waiting for native DNS", caller.isAlive)
            assertEquals(DiagnosticDnsResult.CANCELLED, result.get())
            assertTrue("Caller interrupt status is preserved", interrupted.get())
            assertEquals(DiagnosticDnsResult.BUSY, DiagnosticDnsProbe.check("other.example", 10) { error("Native lookup still owns the slot") })
        } finally {
            release.countDown()
            assertTrue(exited.await(2, TimeUnit.SECONDS))
            caller.join(2000)
        }
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        var next: DiagnosticDnsResult
        do { next = DiagnosticDnsProbe.check("recovered.example", 20) { true }; if (next == DiagnosticDnsResult.BUSY) Thread.yield() }
        while (next == DiagnosticDnsResult.BUSY && System.nanoTime() < until)
        assertEquals(DiagnosticDnsResult.READY, next)
    }
    @Test fun resourceStatusesRemainSafeTypedDiagnostics() {
        for (result in listOf(DiagnosticDnsResult.BUSY, DiagnosticDnsResult.CANCELLED)) {
            val event = DiagnosticEvent(1, DiagnosticCatalog.stage(result.code), result.code, "sing_box", 2, "tcp", emptyMap())
            assertEquals(DiagnosticStage.DNS, event.stage)
            assertEquals(listOf(event), DiagnosticEventCodec.decode(DiagnosticEventCodec.encode(listOf(event))))
            assertFalse(DiagnosticCatalog.advice(event).title.startsWith("Не удалось"))
        }
    }
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
