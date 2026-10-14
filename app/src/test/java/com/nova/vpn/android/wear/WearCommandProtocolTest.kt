package com.nova.vpn.android.wear

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WearCommandProtocolTest {
    @Test fun legacyAndNewCommandsAreAccepted() {
        assertEquals("", WearCommandProtocol.requestId(byteArrayOf()))
        val id = "2f24b9da-42ee-4db6-a8a6-4cbb7eb1261e"
        assertEquals(id, WearCommandProtocol.requestId(id.toByteArray()))
    }
    @Test fun malformedAndOversizedCommandsAreRejected() {
        assertNull(WearCommandProtocol.requestId("not-an-id".toByteArray()))
        assertNull(WearCommandProtocol.requestId(ByteArray(65)))
    }
    @Test fun replyEchoesRequestAndExplainsForegroundRestriction() {
        val reply = JSONObject(WearCommandProtocol.reply("id", "foreground_required").toString(Charsets.UTF_8))
        assertEquals("id", reply.getString("requestId"))
        assertEquals("foreground_required", reply.getString("state"))
        assertEquals(2, reply.length())
    }
}
