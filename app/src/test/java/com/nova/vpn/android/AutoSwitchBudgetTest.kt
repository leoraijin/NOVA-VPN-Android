package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class AutoSwitchBudgetTest {
    @Test fun reservationsSurviveRecreationBeforeAcknowledgement() {
        var budget = AutoSwitchBudget.decode(null, 4, 1000)
        repeat(3) { index ->
            budget = budget.reserve("request-$index", 1000L + index)!!
            budget = AutoSwitchBudget.decode(budget.encode(), 4, 1000L + index)
        }
        assertNull(budget.reserve("fourth", 1004))
        assertNotNull(AutoSwitchBudget.decode(budget.encode(), 4, 901_003).reserve("after-window", 901_003))
    }

    @Test fun rollbackReleasesSlotButPreservesBackoffAcrossRecreation() {
        val original = AutoSwitchBudget(4, 1000).reserve("failed", 1000)!!
        val failed = original.finish("failed", false, 1001)
        val restored = AutoSwitchBudget.decode(failed.encode(), 4, 2000)
        assertTrue(restored.events.isEmpty())
        assertNull(restored.reserve("retry", 2000))
        assertNotNull(restored.reserve("retry", 31_002))
        assertEquals(restored, restored.finish("other-request", false, 2000))
    }

    @Test fun confirmedApplyUsesAcknowledgementTimeAndCannotDoubleCount() {
        val budget = AutoSwitchBudget(4, 1000).reserve("done", 1000)!!.finish("done", true, 1500)
        assertEquals(listOf(AutoSwitchEvent("done", 1500)), budget.events)
        assertEquals(1, budget.finish("done", true, 1600).events.size)
        assertNull(budget.reserve("done", 1501))
    }

    @Test fun bootChangeDoesNotMixClocksAndInvalidDataEnforcesCooldown() {
        val previous = AutoSwitchBudget(4, 500_000).reserve("old-boot", 500_000)!!
        val rebooted = AutoSwitchBudget.decode(previous.encode(), 5, 100)
        assertTrue(rebooted.events.isEmpty())
        assertNotNull(rebooted.reserve("new-boot", 100))
        for (value in listOf("broken", "{}", previous.encode())) {
            val quarantined = AutoSwitchBudget.decode(value, 4, 100)
            assertNull(quarantined.reserve("blocked", 100))
            val repaired = AutoSwitchBudget.decode(quarantined.encode(), 4, 500)
            assertEquals(quarantined.retryAtMillis, repaired.retryAtMillis)
            assertNotNull(repaired.reserve("after-cooldown", 900_101))
        }
    }
}
