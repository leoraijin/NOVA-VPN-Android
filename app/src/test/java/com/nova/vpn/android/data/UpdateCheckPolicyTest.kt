package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckPolicyTest {
    @Test fun firstEntryChecksImmediately() {
        assertTrue(UpdateCheckPolicy.isDue(0, null, "0.2.32", 1000))
    }

    @Test fun newAppVersionTriggersFreshCheckImmediately() {
        assertTrue(UpdateCheckPolicy.isDue(9_000_000, "0.2.31", "0.2.32", 9_000_001))
        assertFalse(UpdateCheckPolicy.isDue(9_000_000, "0.2.32", "0.2.32", 9_000_001))
    }

    @Test fun reopeningBeforeSixHoursDoesNotCheck() {
        val last = 1_000_000L
        assertFalse(UpdateCheckPolicy.isDue(last, "0.2.32", "0.2.32", last))
        assertFalse(UpdateCheckPolicy.isDue(last, "0.2.32", "0.2.32", last + UpdateCheckPolicy.INTERVAL_MS - 1))
        assertTrue(UpdateCheckPolicy.isDue(last, "0.2.32", "0.2.32", last + UpdateCheckPolicy.INTERVAL_MS))
    }

    @Test fun clockRollbackDoesNotSuppressUpdatesIndefinitely() {
        assertTrue(UpdateCheckPolicy.isDue(2000, "0.2.32", "0.2.32", 1000))
    }
}
