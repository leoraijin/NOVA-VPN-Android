package com.nova.vpn.android

/** Exact disposable package allowlist: never authorize the stable app or arbitrary suffixes. */
internal fun isIsolatedUsbTestPackage(packageName: String): Boolean =
    BuildConfig.REALITY_PREVIEW && packageName in setOf(
        "com.nova.vpn.android.usbtest.realitypreview",
        "com.nova.vpn.android.usbtest.completionpreview",
    )
