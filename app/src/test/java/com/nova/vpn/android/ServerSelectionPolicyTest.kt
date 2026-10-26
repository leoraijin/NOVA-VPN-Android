package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.VpnEngine
import org.junit.Assert.*
import org.junit.Test

class ServerSelectionPolicyTest {
    @Test fun measurementForAnotherEngineCannotWinEvenWhenItsLatencyIsLower() {
        val b = a.copy(id = "b")
        val data = mapOf(a.id to result(a, 1), b.id to result(b, 80).copy(engine = VpnEngine.REALITY_CORE))
        assertEquals(a, ServerSelectionPolicy.best(listOf(a, b), data, "wifi", now, VpnEngine.SING_BOX))
        assertEquals(b, ServerSelectionPolicy.best(listOf(a, b), data, "wifi", now, VpnEngine.REALITY_CORE))
        assertFalse(ServerSelectionPolicy.eligible(a, data[a.id], "wifi", now, VpnEngine.REALITY_CORE))
    }

    private val now = 1_000_000_000_000L
    private val a = ImportedProfile("a", "A", "vless", "fixture.example", 443, """{"uuid":"old"}""")
    private fun result(profile: ImportedProfile, millis: Int = 40, successes: Int = 6) = ServerCheckResult(CheckOutcome.SUCCESS, millis,
        successes = successes, attempts = 6, networkSignature = "wifi", configurationSignature = ServerSelectionPolicy.configurationSignature(profile), measuredAtNanos = now)

    @Test fun rejectsStaleNetworkConfigAndUnverifiedResults() {
        val measured = result(a)
        assertTrue(ServerSelectionPolicy.eligible(a, measured, "wifi", now))
        assertFalse(ServerSelectionPolicy.eligible(a, measured, "mobile", now))
        assertFalse(ServerSelectionPolicy.eligible(a.copy(outboundJson = "rotated"), measured, "wifi", now))
        assertFalse(ServerSelectionPolicy.eligible(a, measured, "wifi", now + 301_000_000_000))
        assertFalse(ServerSelectionPolicy.eligible(a, measured, "wifi", now - 1))
        assertFalse(ServerSelectionPolicy.eligible(a, result(a, successes = 1), "wifi", now))
        assertFalse(ServerSelectionPolicy.eligible(a, measured.copy(outcome = CheckOutcome.FAILED), "wifi", now))
        assertFalse(ServerSelectionPolicy.eligible(a, measured, null, now))
    }

    @Test fun reliabilityPrecedesLatencyAndMetadataDoesNotInvalidateMeasurement() {
        val b = a.copy(id = "b")
        assertEquals(b, ServerSelectionPolicy.best(listOf(a, b), mapOf("a" to result(a, 1, 3), "b" to result(b, 80, 6)), "wifi", now))
        assertEquals(a, ServerSelectionPolicy.best(listOf(a, b), mapOf("a" to result(a, 20), "b" to result(b, 80)), "wifi", now))
        assertTrue(ServerSelectionPolicy.eligible(a.copy(name = "Renamed"), result(a), "wifi", now))
    }
}
