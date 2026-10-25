package com.nova.vpn.android

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class DiagnosticEventCodecTest {
    @Test fun metadataRoundTripRetainsStageAndNumbers() {
        val event = DiagnosticEvent(123, DiagnosticStage.INTERNET, "probe_success", "sing_box", 4, "xhttp", mapOf(DiagnosticMetric.RESPONSE_MS to 32))
        assertEquals(listOf(event), DiagnosticEventCodec.decode(DiagnosticEventCodec.encode(listOf(event))))
    }
    @Test fun untrustedFieldsAndOversizedPayloadCannotEnterReport() {
        val event = DiagnosticEvent(1, DiagnosticStage.INTERNET, "probe_success", "sing_box", 0, "tcp", emptyMap()).toJson()
        event.put("code", "private_lowercase_token").put("engine", "SECRET_ENGINE").put("transport", "SECRET_TRANSPORT").put("uuid", "SECRET_UUID")
        val decoded = DiagnosticEventCodec.decode(JSONArray().put(event).toString()).single()
        val safe = DiagnosticEventCodec.encode(listOf(decoded))
        assertFalse(safe.contains("SECRET")); assertFalse(safe.contains("private_lowercase_token"))
        assertEquals(DiagnosticStage.UNKNOWN, decoded.stage)
        assertTrue(DiagnosticEventCodec.decode("x".repeat(32_769)).isEmpty())
        assertTrue(DiagnosticEventCodec.decode("broken").isEmpty())
        event.put("elapsed_ms", -1)
        assertTrue(DiagnosticEventCodec.decode(JSONArray().put(event).toString()).isEmpty())
    }
}
