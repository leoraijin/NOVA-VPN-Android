package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckPolicyTest {
    @Test fun firstEntryChecksImmediately() {
        assertTrue(UpdateCheckPolicy.isDue(0, 1000))
    }

    @Test fun reopeningBeforeSixHoursDoesNotCheck() {
        val last = 1_000_000L
        assertFalse(UpdateCheckPolicy.isDue(last, last))
        assertFalse(UpdateCheckPolicy.isDue(last, last + UpdateCheckPolicy.INTERVAL_MS - 1))
        assertTrue(UpdateCheckPolicy.isDue(last, last + UpdateCheckPolicy.INTERVAL_MS))
    }

    @Test fun clockRollbackDoesNotSuppressUpdatesIndefinitely() {
        assertTrue(UpdateCheckPolicy.isDue(2000, 1000))
    }
}
