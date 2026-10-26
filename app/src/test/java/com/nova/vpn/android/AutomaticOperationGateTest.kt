package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AutomaticOperationGateTest {
    @Test fun foregroundOwnershipBlocksNewSubmissionsButWaitsForAlreadyDispatchedWork() {
        val gate = AutomaticOperationGate()
        assertTrue(gate.dispatch("pending", 1) {})
        val pause = gate.acquire()
        try {
            assertEquals(setOf("pending"), gate.pending.value)
            assertFalse(gate.dispatch("later", 1) { error("dispatch while diagnostics open") })
            assertTrue(gate.belongsTo("pending", 1))
            gate.settled("pending")
            assertTrue(gate.pending.value.isEmpty())
            assertTrue(gate.paused)
        } finally { pause.close(); pause.close() }
        assertFalse(gate.paused)
        assertTrue(gate.dispatch("after-close", 1) {})
    }

    @Test fun oldServiceCleanupCannotReleaseANewServiceRequestAndSubmissionFailureDoesNotLeak() {
        val gate = AutomaticOperationGate()
        gate.dispatch("old", 1) {}; gate.dispatch("new", 2) {}
        assertEquals(setOf("old"), gate.requestsFor(1))
        gate.serviceClosed(1)
        assertEquals(setOf("new"), gate.pending.value)
        assertFalse(gate.belongsTo("new", 1))
        assertTrue(gate.belongsTo("new", 2))
        assertThrows(IllegalStateException::class.java) { gate.dispatch("failed", 2) { error("synthetic binder failure") } }
        assertEquals(setOf("new"), gate.pending.value)
        gate.settled("new"); gate.settled("new")
        assertTrue(gate.pending.value.isEmpty())
    }

    @Test fun acquiringDiagnosticsCannotSlipBetweenRegistrationAndSubmission() {
        val gate = AutomaticOperationGate()
        val submitting = CountDownLatch(1)
        val finishSubmission = CountDownLatch(1)
        val acquiring = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val dispatch = pool.submit<Boolean> { gate.dispatch("request", 1) {
                submitting.countDown()
                check(finishSubmission.await(5, TimeUnit.SECONDS))
            } }
            check(submitting.await(5, TimeUnit.SECONDS))
            val pause = pool.submit<AutoCloseable> { acquiring.countDown(); gate.acquire() }
            check(acquiring.await(5, TimeUnit.SECONDS))
            assertFalse(pause.isDone)
            finishSubmission.countDown()
            assertTrue(dispatch.get(5, TimeUnit.SECONDS))
            pause.get(5, TimeUnit.SECONDS).use {
                assertEquals(setOf("request"), gate.pending.value)
                assertFalse(gate.dispatch("later", 1) { error("late automatic start") })
            }
        } finally { finishSubmission.countDown(); pool.shutdownNow() }
    }
}
