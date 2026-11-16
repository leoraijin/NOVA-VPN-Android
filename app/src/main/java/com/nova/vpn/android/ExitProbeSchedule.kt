package com.nova.vpn.android

internal fun exitProbeIntervalMillis(foreground: Boolean, primary: Boolean): Long = when {
    foreground -> 60_000L
    primary -> 120_000L
    else -> 600_000L
}
