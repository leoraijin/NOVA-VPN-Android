package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.VpnEngine
import org.junit.Assert.*
import org.junit.Test

class AutoSwitchPolicyTest {
    @Test fun engineChangeResetsFailureHistoryAndRejectsMeasurementsFromAnotherEngine() {
        val policy = AutoSwitchPolicy().apply { setEnabled(true) }
        fun data(at: Long, engine: VpnEngine) = mapOf(a.id to result(a, at, null).copy(engine = engine),
            b.id to result(b, at, 30).copy(engine = engine))
        assertNull(policy.propose(a, listOf(b), data(start, VpnEngine.SING_BOX), "wifi", start))
        assertNull(policy.propose(a, listOf(b), data(start + 1, VpnEngine.SING_BOX), "wifi", start + 1, VpnEngine.REALITY_CORE))
        assertNull(policy.propose(a, listOf(b), data(start + 2, VpnEngine.REALITY_CORE), "wifi", start + 2, VpnEngine.REALITY_CORE))
        assertNotNull(policy.propose(a, listOf(b), data(start + 3, VpnEngine.REALITY_CORE), "wifi", start + 3, VpnEngine.REALITY_CORE))
    }

    @Test fun networkChangeInvalidatesAnOutstandingDecision() {
        val policy = AutoSwitchPolicy().apply { setEnabled(true) }
        fun data(time: Long, network: String) = mapOf("a" to result(a, time, null, network), "b" to result(b, time, 20, network))
        policy.propose(a, listOf(b), data(start, "wifi"), "wifi", start)
        val decision = policy.propose(a, listOf(b), data(start + 1, "wifi"), "wifi", start + 1)!!
        assertNull(policy.propose(a, listOf(b), data(start + 2, "mobile"), "mobile", start + 2))
        assertFalse(policy.complete(decision, true, start + 2))
        assertNotNull(policy.propose(a, listOf(b), data(start + 3, "mobile"), "mobile", start + 3))
    }

    @Test fun threeConfirmedSwitchesLimitFurtherAttemptsWithinWindow() {
        val policy = AutoSwitchPolicy().apply { setEnabled(true) }
        var current = a
        var time = start
        repeat(3) {
            val next = if (current.id == "a") b else a
            fun data(at: Long) = mapOf(current.id to result(current, at, null), next.id to result(next, at, 30))
            assertNull(policy.propose(current, listOf(next), data(time), "wifi", time))
            time++
            val decision = policy.propose(current, listOf(next), data(time), "wifi", time)!!
            assertTrue(policy.complete(decision, true, time))
            current = next
            time++
        }
        val next = if (current.id == "a") b else a
        fun data(at: Long) = mapOf(current.id to result(current, at, null), next.id to result(next, at, 30))
        assertNull(policy.propose(current, listOf(next), data(time), "wifi", time))
        assertNull(policy.propose(current, listOf(next), data(time + 1), "wifi", time + 1))
        time += 901_000_000_000L
        assertNull(policy.propose(current, listOf(next), data(time), "wifi", time))
        assertNotNull(policy.propose(current, listOf(next), data(time + 1), "wifi", time + 1))
    }
    private val a = ImportedProfile("a", "A", "vless", "fixture.example", 443, "a")
    private val b = a.copy(id = "b", outboundJson = "b")
    private val start = 1_000_000_000_000L
    private fun result(p: ImportedProfile, now: Long, millis: Int? = 100, network: String = "wifi") =
        ServerCheckResult(if (millis == null) CheckOutcome.FAILED else CheckOutcome.SUCCESS, millis,
            successes = if (millis == null) 0 else 6, attempts = 6, networkSignature = network,
            configurationSignature = ServerSelectionPolicy.configurationSignature(p), measuredAtNanos = now)

    @Test fun optInDwellThresholdAndAcknowledgementAreRequired() {
        val policy = AutoSwitchPolicy()
        fun data(time: Long, candidate: Int = 50) = mapOf("a" to result(a, time), "b" to result(b, time, candidate))
        assertNull(policy.propose(a, listOf(a, b), data(start), "wifi", start))
        policy.setEnabled(true)
        assertNull(policy.propose(a, listOf(a, b), data(start), "wifi", start))
        val later = start + 61_000_000_000L
        assertNull(policy.propose(a, listOf(a, b), data(later, 85), "wifi", later))
        val decision = policy.propose(a, listOf(a, b), data(later), "wifi", later)!!
        assertEquals(AutoSwitchPolicy.Reason.BETTER_RESPONSE, decision.reason)
        assertNull(policy.propose(a, listOf(a, b), data(later), "wifi", later))
        assertTrue(policy.complete(decision, true, later))
        assertFalse(policy.complete(decision, true, later))
    }

    @Test fun twoDistinctFailuresNeededAndNetworkLossNeverChooses() {
        val policy = AutoSwitchPolicy().apply { setEnabled(true) }
        val data = mapOf("a" to result(a, start, null), "b" to result(b, start, 50))
        assertNull(policy.propose(a, listOf(b), data, null, start))
        assertNull(policy.propose(a, listOf(b), data, "wifi", start))
        assertNull(policy.propose(a, listOf(b), data, "wifi", start + 1))
        val second = start + 10_000_000_000L
        val next = mapOf("a" to result(a, second, null), "b" to result(b, second, 50))
        assertEquals(AutoSwitchPolicy.Reason.CURRENT_FAILED, policy.propose(a, listOf(b), next, "wifi", second)!!.reason)
    }

    @Test fun failedApplyBacksOffAndManualPinInvalidatesPendingDecision() {
        val policy = AutoSwitchPolicy().apply { setEnabled(true) }
        fun fail(time: Long) = mapOf("a" to result(a, time, null), "b" to result(b, time, 30))
        policy.propose(a, listOf(b), fail(start), "wifi", start)
        val time = start + 1_000_000_000L
        val decision = policy.propose(a, listOf(b), fail(time), "wifi", time)!!
        assertTrue(policy.complete(decision, false, time))
        assertNull(policy.propose(a, listOf(b), fail(time + 1), "wifi", time + 1))
        val later = time + 31_000_000_000L
        val retry = policy.propose(a, listOf(b), fail(later), "wifi", later)!!
        policy.manualSelection()
        assertFalse(policy.complete(retry, true, later))
        assertNull(policy.propose(a, listOf(b), fail(later), "wifi", later))
    }
}
