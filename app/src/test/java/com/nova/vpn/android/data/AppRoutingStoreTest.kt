package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppRoutingStoreTest {
    @Test fun ipv6CompatibilityDefaultAndOptInPersistAlongsideRoutes() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(AppRoutingStore(context).load().ipv6Enabled)
        val selected = AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf("org.example.browser"),
            listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT)), ipv6Enabled = true)
        AppRoutingStore(context).save(selected)
        assertEquals(selected, AppRoutingStore(context).load())
        AppRoutingStore(context).save(selected.copy(ipv6Enabled = false))
        assertEquals(selected.copy(ipv6Enabled = false), AppRoutingStore(context).load())
    }
}
