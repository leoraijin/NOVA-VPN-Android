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

    @Test fun foregroundIntervalMatchesAcrossExits() {
        assertEquals(60_000L, exitProbeIntervalMillis(foreground = true, primary = true))
        assertEquals(60_000L, exitProbeIntervalMillis(foreground = true, primary = false))
    }
}
