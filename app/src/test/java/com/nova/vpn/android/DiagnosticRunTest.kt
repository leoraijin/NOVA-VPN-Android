package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class DiagnosticRunTest {
    @Test fun unsupportedEngineKeepsItsIdentityAndOffersEngineSettings() {
        for (code in listOf("engine_config_unsupported", "engine_unavailable")) {
            val event = DiagnosticEvent(1, DiagnosticStage.SERVICE, code, "reality_core", 0, "tcp", emptyMap())
            val snapshot = DiagnosticSnapshot("Safe report", "{}", DiagnosticCatalog.advice(null)).withProbe(
                ServerCheckResult(CheckOutcome.UNSUPPORTED, events = listOf(event), engine = com.nova.vpn.android.data.VpnEngine.REALITY_CORE))
            assertEquals(DiagnosticAction.SETTINGS, snapshot.advice.action)
            val check = org.json.JSONObject(snapshot.json).getJSONObject("isolated_check")
            assertEquals("reality_core", check.getString("engine"))
            assertEquals(code, check.getJSONArray("events").getJSONObject(0).getString("code"))
            assertEquals("unsupported", check.getString("outcome"))
        }
    }

    @Test fun foregroundPauseIsReferenceCountedAndIdempotent() {
        val first = AutomaticProbePause.acquire()
        val second = AutomaticProbePause.acquire()
        try {
            assertTrue(AutomaticProbePause.paused)
            first.close(); first.close()
            assertTrue(AutomaticProbePause.paused)
        } finally { first.close(); second.close() }
        assertFalse(AutomaticProbePause.paused)
    }
    @Test fun probeSnapshotContainsSafeMetadataOnly() {
        val advice = DiagnosticCatalog.advice(null)
        val event = DiagnosticEvent(1, DiagnosticStage.UNKNOWN, "private_token", "PRIVATE_ENGINE", 0, "PRIVATE_TRANSPORT", emptyMap())
        val snapshot = DiagnosticSnapshot("Safe report", "{}", advice).withProbe(ServerCheckResult(CheckOutcome.FAILED, events = listOf(event)))
        assertFalse(snapshot.json.contains("PRIVATE"))
        assertFalse(snapshot.text.contains("private_token"))
        assertTrue(snapshot.json.contains("isolated_check"))
    }
}
