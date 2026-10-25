package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class ConfigurationApplyTrackerTest {
    private val first = AppliedConfiguration("request-a", "server-a", "routing-a")
    private val second = AppliedConfiguration("request-b", "server-b", "routing-b")

    @Test fun acceptanceIsNotApplicationAndSuccessIncludesActualIdentity() {
        val tracker = ConfigurationApplyTracker()
        tracker.accepted(first)
        assertEquals(ApplyStatus.PENDING, tracker.results.value[first.requestId]!!.status)
        assertNull(tracker.results.value[first.requestId]!!.applied)
        assertTrue(tracker.applied(first))
        assertEquals(first, tracker.results.value[first.requestId]!!.applied)
    }

    @Test fun rollbackIsAcknowledgedOnlyAfterPreviousConfigurationStarts() {
        val tracker = ConfigurationApplyTracker()
        tracker.accepted(first); tracker.applied(first)
        tracker.accepted(second)
        assertEquals(ApplyStatus.PENDING, tracker.results.value[second.requestId]!!.status)
        assertTrue(tracker.applied(first, rollbackFor = second.requestId))
        assertEquals(ConfigurationApplyResult(second, ApplyStatus.ROLLED_BACK, first), tracker.results.value[second.requestId])
    }

    @Test fun lateSuccessAndRollbackCannotAcknowledgeANewerIntent() {
        val tracker = ConfigurationApplyTracker()
        tracker.accepted(first); tracker.accepted(second)
        assertEquals(ApplyStatus.SUPERSEDED, tracker.results.value[first.requestId]!!.status)
        assertFalse(tracker.applied(first))
        assertFalse(tracker.applied(first, first.requestId))
        assertEquals(ApplyStatus.PENDING, tracker.results.value[second.requestId]!!.status)
        tracker.stopped()
        assertEquals(ApplyStatus.CANCELLED, tracker.results.value[second.requestId]!!.status)
        assertFalse(tracker.applied(second))
    }

    @Test fun failureCannotBecomeASuccessWithoutANewAcceptedRequest() {
        val tracker = ConfigurationApplyTracker()
        tracker.accepted(first); tracker.failed(first.requestId)
        assertEquals(ApplyStatus.FAILED, tracker.results.value[first.requestId]!!.status)
        assertFalse(tracker.applied(first))
        tracker.accepted(second); tracker.failed(first.requestId)
        assertTrue(tracker.applied(second))
    }

    @Test fun retainedHistoryIsBounded() {
        val tracker = ConfigurationApplyTracker()
        repeat(100) { tracker.accepted(AppliedConfiguration("request-$it")) }
        assertEquals(32, tracker.results.value.size)
        assertEquals(ApplyStatus.PENDING, tracker.results.value["request-99"]!!.status)
        assertFalse(tracker.results.value.containsKey("request-0"))
    }
}
