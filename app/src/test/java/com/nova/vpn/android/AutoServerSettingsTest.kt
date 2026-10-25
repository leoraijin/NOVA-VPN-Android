package com.nova.vpn.android

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutoServerSettingsTest {
    @Test fun settingsRoundTripIncludesExactCandidateScopeAndRevision() {
        val options = AutoServerOptions(true, setOf("node-b", "node-a"), "revision-1")
        assertEquals(options, AutoServerOptions.decode(options.encode()))
        assertEquals(options.copy(enabled = false), AutoServerOptions.decode(options.copy(enabled = false).encode()))
    }

    @Test fun malformedOrEmptyEnabledScopeFailsClosed() {
        for (value in listOf(null, "broken", "{}", """{"schema":1,"enabled":true,"candidates":[],"revision":"v"}""",
            """{"schema":2,"enabled":true,"candidates":["a"],"revision":"v"}""")) {
            assertFalse(AutoServerOptions.decode(value).enabled)
        }
        val invalid = JSONObject(AutoServerOptions(true, setOf("a"), "v").encode()).put("revision", "")
        assertFalse(AutoServerOptions.decode(invalid.toString()).enabled)
    }

    @Test fun destroyedControllerCannotOverwriteNewServiceStatus() {
        val old = AutoServerRuntime.claim()
        AutoServerRuntime.publish(old, AutoServerState(checking = true))
        val current = AutoServerRuntime.claim()
        AutoServerRuntime.publish(current, AutoServerState(appliedRequest = "new", profileId = "b"))
        AutoServerRuntime.publish(old, AutoServerState())
        assertEquals("new", AutoServerRuntime.state.value.appliedRequest)
        assertEquals("b", AutoServerRuntime.state.value.profileId)
    }
}
