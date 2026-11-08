package com.nova.vpn.android
import org.junit.Assert.*
import org.junit.Test

class CatalogRefreshBackoffTest {
    private val key=CatalogRefreshKey("active",1)
    @Test fun boundedExponentialRetryAndNewCatalogOrUserSessionBypassesOldFailure() {
        val retry=CatalogRefreshBackoff();var now=100L
        for(delay in listOf(5000L,10000L,20000L,30000L,30000L)) {
            assertTrue(retry.ready(key,now));retry.failed(now)
            assertFalse(retry.ready(key,now+delay-1));now+=delay
            assertTrue(retry.ready(key,now))
        }
        retry.failed(now)
        assertTrue(retry.ready(key.copy(catalogRevision=2),now))
        retry.failed(now)
        assertTrue(retry.ready(key.copy(requestId="new-user-session",catalogRevision=2),now))
    }
    @Test fun successResetsDelayAndOverflowCannotCauseImmediateRetry() {
        val retry=CatalogRefreshBackoff();assertTrue(retry.ready(key,0));retry.failed(0)
        retry.succeeded();assertTrue(retry.ready(key,1));retry.failed(1)
        assertFalse(retry.ready(key,5000));assertTrue(retry.ready(key,5001))
        retry.failed(Long.MAX_VALUE-1)
        assertFalse(retry.ready(key,Long.MAX_VALUE-1));assertTrue(retry.ready(key,Long.MAX_VALUE))
    }
    @Test fun onlySettledAcknowledgementsCanResolveAnAutomaticCatalogUpdate() {
        val tracker = ConfigurationApplyTracker()
        val gate = AutomaticOperationGate()
        val request = AppliedConfiguration("catalog")
        val owner = 1L
        fun outcome(current: Boolean = true): CatalogRefreshOutcome = CatalogRefreshOutcome.from(
            tracker.results.value[request.requestId]?.status, gate.belongsTo(request.requestId, owner), current)

        assertTrue(gate.dispatch(request.requestId, owner) { tracker.accepted(request) })
        assertEquals(CatalogRefreshOutcome.WAITING, outcome())
        tracker.applied(request)
        assertEquals(CatalogRefreshOutcome.WAITING, outcome())
        gate.settled(request.requestId)
        assertEquals(CatalogRefreshOutcome.APPLIED, outcome())

        assertTrue(gate.dispatch(request.requestId, owner) { tracker.accepted(request) })
        tracker.applied(AppliedConfiguration("previous"), rollbackFor = request.requestId)
        assertEquals(CatalogRefreshOutcome.WAITING, outcome())
        gate.settled(request.requestId)
        assertEquals(CatalogRefreshOutcome.REJECTED, outcome())
        assertEquals(CatalogRefreshOutcome.CANCELLED, outcome(current = false))

        assertTrue(gate.dispatch(request.requestId, owner) { tracker.accepted(request) })
        tracker.stopped()
        gate.settled(request.requestId)
        assertEquals(CatalogRefreshOutcome.CANCELLED, outcome())
        assertEquals(CatalogRefreshOutcome.REJECTED, CatalogRefreshOutcome.from(ApplyStatus.FAILED, false, true))
        assertEquals(CatalogRefreshOutcome.CANCELLED, CatalogRefreshOutcome.from(ApplyStatus.SUPERSEDED, false, true))
    }
    @Test fun diagnosticEventsAreRecognizedAndShowActualActiveSnapshotCaveat() {
        assertEquals("catalog_refresh_failed",DiagnosticCatalog.code("catalog_refresh_failed"))
        assertEquals("catalog_refresh_submitted",DiagnosticCatalog.code("catalog_refresh_submitted"))
        assertEquals(DiagnosticStage.ROUTING,DiagnosticCatalog.stage("catalog_refresh_failed"))
        val advice=DiagnosticCatalog.advice(DiagnosticEvent(0,DiagnosticStage.ROUTING,"catalog_refresh_failed","sing_box",0,"tcp",emptyMap()))
        assertEquals(DiagnosticAction.ROUTING,advice.action)
        assertTrue(advice.detail.contains("остаётся прежним"))
        assertTrue(advice.detail.contains("повторит попытку"))
    }
}
