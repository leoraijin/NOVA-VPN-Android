package com.nova.vpn.android

internal fun exitProbeIntervalMillis(foreground: Boolean, primary: Boolean): Long = when {
    foreground -> 60_000L
    primary -> 120_000L
    else -> 600_000L
}

/** Owned by a single scheduled exit check. Failed exits retry sooner than healthy background exits. */
internal class ExitProbeHistory {
    var failures = 0
        private set
    private var lastConfirmation: Long? = null

    fun complete(success: Boolean) {
        failures = if (success) 0 else (failures + 1).coerceAtMost(3)
        if (success) lastConfirmation = null
    }

    fun shouldConfirmFailure(now: Long): Boolean {
        if (failures == 0 || lastConfirmation?.let { now - it < 120_000L } == true) return false
        lastConfirmation = now
        return true
    }

    fun nextDelay(healthyInterval: Long): Long = minOf(healthyInterval, when (failures) {
        1 -> 15_000L
        2 -> 45_000L
        3 -> 120_000L
        else -> healthyInterval
    })
}
