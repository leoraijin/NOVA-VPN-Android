package com.nova.vpn.android

/** Keep virtual packets within a broadly supported Android network MTU. */
internal fun normalizeAndroidTunMtu(requestedMtu: Int): Int = requestedMtu.coerceIn(1280, 1500)
