package com.nova.vpn.android.data

/** No background timer: automatic checks run on app entry, at most once per six hours. */
internal object UpdateCheckPolicy {
    const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun isDue(lastAttempt: Long, now: Long): Boolean =
        lastAttempt <= 0 || now < lastAttempt || now - lastAttempt >= INTERVAL_MS
}
