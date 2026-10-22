package com.nova.vpn.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The OS boundary matters: a direct outbound alone still captures an app in TUN. */
class AndroidAppBypassTest {
    private val own = "com.nova.vpn.android"
    private val browser = "org.example.browser"
    private fun config(settings: AppRoutingSettings): JSONObject {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443"
        ).single()
        return JSONObject(SingBoxConfigFactory.build(profile, settings, own))
    }

    @Test fun excludedApplicationBypassesTunEvenWhenVpnSiteRuleMatches() {
        val settings = AppRoutingSettings(packages = setOf(browser, ""),
            sites = listOf(SiteRoutingRule("example.org", SiteRoutingTarget.VPN)))
        val source = config(settings)
        val tun = source.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("[\"$own\",\"$browser\"]", tun.getJSONArray("exclude_package").toString())
        assertFalse(tun.has("include_package"))
        val native = RealityConfigFactory.prepare(source.toString(), own) { 10001 }
        assertEquals(listOf(own, browser), native.excludedPackages)
        assertTrue(native.includedPackages.isEmpty())
        assertFalse(JSONObject(native.config).getJSONArray("inbounds").getJSONObject(0).has("exclude_package"))
    }

    @Test fun includeModeKeepsOtherApplicationsOutsideAndroidVpnInBothEngines() {
        val source = config(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf(browser, own),
            listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT))))
        val tun = source.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("[\"$browser\"]", tun.getJSONArray("include_package").toString())
        assertFalse(tun.has("exclude_package"))
        // Use a selection without NOVA for the native UID adapter.
        val nativeSource = config(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf(browser),
            listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT))))
        val native = RealityConfigFactory.prepare(nativeSource.toString(), own) { 10001 }
        assertEquals(listOf(browser), native.includedPackages)
        assertTrue(native.excludedPackages.isEmpty())
    }

    @Test fun mismatchedNativeFiltersCannotSilentlyCaptureBypassedApps() {
        val source = config(AppRoutingSettings(packages = setOf(browser)))
        source.getJSONArray("inbounds").getJSONObject(0).put("exclude_package", org.json.JSONArray().put(own))
        assertThrows(IllegalArgumentException::class.java) {
            RealityConfigFactory.prepare(source.toString(), own)
        }
    }

    @Test fun malformedNativeFiltersAreRejectedInsteadOfIgnored() {
        for (key in listOf("include_package", "exclude_package")) {
            val source = config(AppRoutingSettings())
            source.getJSONArray("inbounds").getJSONObject(0).put(key, browser)
            assertThrows(IllegalArgumentException::class.java) {
                RealityConfigFactory.prepare(source.toString(), own)
            }
        }
    }
}
