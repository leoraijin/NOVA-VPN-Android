package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class UserRequestGateTest {
    @Test fun restoreRejectsActiveOrClosingServicesWithoutInvalidatingCurrentIntent() {
        val gate = UserRequestGate()
        val current = gate.snapshot()
        gate.retainService(1); gate.retainService(2); gate.releaseService(1)
        assertThrows(IllegalStateException::class.java) { gate.restoreWhenIdle({ true }) { error("write during service lifetime") } }
        assertTrue(gate.isCurrent(current))
        gate.releaseService(2)
        assertThrows(IllegalStateException::class.java) { gate.restoreWhenIdle({ false }) { error("write while connected") } }
        assertTrue(gate.isCurrent(current))
        gate.restoreWhenIdle({ true }) { assertFalse(gate.isCurrent(current)) }
    }

    @Test fun aNewStartCannotInterleaveWithRestoreOrItsRollback() {
        val gate = UserRequestGate()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val starterReady = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val stage = java.util.concurrent.atomic.AtomicInteger()
        try {
            val restoring = pool.submit {
                assertThrows(IllegalStateException::class.java) {
                    gate.restoreWhenIdle({ true }) {
                        entered.countDown()
                        check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        stage.set(2) // The transaction restored its old data before propagating failure.
                        throw IllegalStateException("synthetic write failure")
                    }
                }
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val start = pool.submit<String> { starterReady.countDown(); gate.begin().also { assertEquals(2, stage.get()) } }
            assertTrue(starterReady.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertThrows(java.util.concurrent.TimeoutException::class.java) { start.get(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
            release.countDown()
            restoring.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(gate.isCurrent(start.get(5, java.util.concurrent.TimeUnit.SECONDS)))
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun latePreparationAndConsentCannotOverrideStop() {
        val gate = UserRequestGate()
        val preparing = gate.begin()
        val stop = gate.begin()
        assertFalse(gate.accept(preparing) { error("late start") })
        assertTrue(gate.accept(stop) { })
    }

    @Test fun newerStartSupersedesAQueuedStopAcrossEntryPoints() {
        val gate = UserRequestGate()
        val tile = gate.begin()
        val stop = gate.begin()
        val screen = gate.begin()
        assertFalse(gate.isCurrent(tile))
        assertFalse(gate.accept(stop) { error("stale stop") })
        assertTrue(gate.accept(screen) { })
    }

    @Test fun processRecreationDoesNotAcceptOldQueuedRequests() {
        val old = UserRequestGate().begin()
        assertFalse(UserRequestGate().isCurrent(old))
    }
}
