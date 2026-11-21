package com.nova.vpn.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitProbeScheduleTest {
    @Test fun tenHealthyExitsStayBelowHourlyBackgroundBudget() {
        val hourMillis = 3_600_000L
        val counts = (0 until 10).map { index ->
            val period = exitProbeIntervalMillis(foreground = false, primary = index == 0)
            generateSequence(index * 2_000L) { it + period }
                .takeWhile { it < hourMillis }
                .count()
        }

        assertEquals(30, counts.first())
        assertEquals(List(9) { 6 }, counts.drop(1))
        assertTrue(counts.sum() <= 90)
    }

    @Test fun failedSecondaryRetriesBeforeItsNormalBackgroundInterval() {
        val history = ExitProbeHistory()
        assertEquals(600_000L, history.nextDelay(600_000L))
        org.junit.Assert.assertFalse(history.shouldConfirmFailure(0))
        history.complete(success = false)
        assertEquals(15_000L, history.nextDelay(600_000L))
        assertTrue(history.shouldConfirmFailure(15_000L))
        history.complete(success = false)
        assertEquals(45_000L, history.nextDelay(600_000L))
        org.junit.Assert.assertFalse(history.shouldConfirmFailure(60_000L))
        history.complete(success = false)
        assertEquals(120_000L, history.nextDelay(600_000L))
        assertEquals(60_000L, history.nextDelay(60_000L))
        assertTrue(history.shouldConfirmFailure(180_000L))
        history.complete(success = true)
        assertEquals(600_000L, history.nextDelay(600_000L))
        org.junit.Assert.assertFalse(history.shouldConfirmFailure(181_000L))
        history.complete(success = false)
        assertTrue(history.shouldConfirmFailure(196_000L))
    }

    @Test fun foregroundIntervalMatchesAcrossExits() {
        assertEquals(60_000L, exitProbeIntervalMillis(foreground = true, primary = true))
        assertEquals(60_000L, exitProbeIntervalMillis(foreground = true, primary = false))
    }
}
