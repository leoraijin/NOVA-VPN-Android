package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MultiVpnSlotsTest {
    private fun ten(): MultiServerSettings {
        var value = MultiServerSettings(1, "p1", emptyList())
        for (n in 2..10) value = value.withSlot(n, "p$n")
        return value.copy(rules = (2..10).flatMap { n -> listOf(
            MultiServerRule(domain = "vpn$n.example.org", target = ServerRouteTarget(ServerRouteKind.PROFILE, "p$n")),
            MultiServerRule(packageName = "example.app$n", target = ServerRouteTarget(ServerRouteKind.PROFILE, "p$n"))) })
    }
    private fun profile(n: Int) = ImportedProfile(id = "p$n", name = "VPN-$n", protocol = "vless", server = "127.0.0.1", port = 10000+n,
        outboundJson = """{"type":"vless","uuid":"11111111-1111-4111-8111-111111111111","server":"127.0.0.1","server_port":${10000+n}}""")

    @Test fun tenExitsPersistAndProduceIndependentRoutes() {
        val multi = ten()
        assertEquals(multi, MultiServerSettings.fromJson(JSONObject(multi.toJson().toString())))
        val profiles = (1..10).map(::profile)
        val settings = AppRoutingSettings(multiServer = multi)
        val config = JSONObject(SingBoxConfigFactory.build(profiles.first(), settings, "com.nova.vpn.android", profiles, "/fixtures/ten.db"))
        val exits = config.getJSONArray("outbounds")
        assertEquals(10, (0 until exits.length()).map(exits::getJSONObject).count { it.optString("type") == "vless" })
        val native = RealityConfigFactory.prepare(config.toString(), "com.nova.vpn.android",
            { pkg -> 10000 + pkg.substringAfterLast("app").toInt() }, managedDual = true)
        val nativeExits = JSONObject(native.config).getJSONArray("outbounds")
        assertEquals(10, (0 until nativeExits.length()).map(nativeExits::getJSONObject).count { it.optString("type") == "vless" })
        val policy = MultiServerPolicy(1, "p1", multi.effectiveRules(), profiles.map { it.id }.toSet())
        for(n in 2..10) {
            assertEquals("p$n", policy.decide("example.app$n", null).effective.profileId)
            assertEquals("p$n", policy.decide(null, "vpn$n.example.org").effective.profileId)
        }
        val identity = RoutePolicyIdentity.capture(settings)!!
        assertEquals(identity, RoutePolicyIdentity.decode(identity.encode()))
        assertEquals(multi.slots, identity.vpnSlots)
    }
    @Test fun pauseRestartResumePreservesAllRulesAndUsesOnlyPrimary() {
        val original = ten()
        val paused = MultiServerSettings.fromJson(original.copy(secondaryPaused = true).toJson())
        assertEquals(original.rules, paused.rules)
        val primary = profile(1)
        val settings = AppRoutingSettings(multiServer = paused)
        val config = JSONObject(SingBoxConfigFactory.build(primary, settings, "com.nova.vpn.android", listOf(primary), "/fixtures/paused-ten.db"))
        val exits = config.getJSONArray("outbounds")
        assertEquals(1, (0 until exits.length()).map(exits::getJSONObject).count { it.optString("type") == "vless" })
        val policy = MultiServerPolicy(1, "p1", paused.effectiveRules(), setOf("p1"))
        for(n in 2..10) {
            assertEquals("p1", policy.decide("example.app$n", null).effective.profileId)
            assertEquals("p1", policy.decide(null, "vpn$n.example.org").effective.profileId)
        }
        assertEquals(original, paused.copy(secondaryPaused = false))
    }
    @Test fun removingMiddleSlotKeepsNumbersAndOtherRoutes() {
        val original = ten()
        val removed = original.withSlot(5, null)
        assertFalse(5 in removed.slots)
        assertEquals("p6", removed.slots[6])
        assertEquals(original.rules.filter { it.target.profileId != "p5" }, removed.rules)
        assertEquals(18, original.rules.size)
        val reused = removed.withSlot(5, "new5")
        assertTrue(reused.rules.none { it.target.profileId in listOf("p5", "new5") })
        val changed = original.withSlot(8, "new8")
        assertEquals(2, changed.rules.count { it.target.profileId == "new8" })
        assertEquals(original.rules.filter { it.target.profileId != "p8" }, changed.rules.filter { it.target.profileId != "new8" })
    }
    @Test fun shareAndAdvancedEditorKeepUnusedSlots() {
        val multi = ten().copy(rules = emptyList())
        assertEquals((1..10).map { "p$it" }.toSet(), RoutingShare.references(multi.toJson()))
        val settings = AppRoutingSettings(multiServer = multi)
        val edited = RoutingModeTransitions.enable(settings, "p1", "p1", multi.rules, false)
        assertEquals(multi.slots, edited.multiServer!!.slots)
    }
    @Test(expected = IllegalArgumentException::class) fun eleventhIsRejected() { ten().withSlot(11, "p11") }
    @Test(expected = IllegalArgumentException::class) fun duplicateIsRejected() { ten().withSlot(5, "p8") }
    @Test(expected = IllegalArgumentException::class) fun primaryCannotBecomeAdditional() { ten().withSlot(5, "p1") }
}
