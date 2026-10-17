package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

/** These tests run against the optimized main-client variant used for publication. */
class MainReleaseCompatibilityTest {
    @Test fun mainPackageAndUpdaterAreNotPreview() {
        assertEquals("com.nova.vpn.android", BuildConfig.APPLICATION_ID)
        assertFalse(BuildConfig.REALITY_PREVIEW)
        assertFalse(BuildConfig.VERSION_NAME.contains("preview"))
        assertTrue(BuildConfig.VERSION_CODE > 50)
    }
}
