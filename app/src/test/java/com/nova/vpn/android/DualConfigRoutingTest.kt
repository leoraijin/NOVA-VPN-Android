package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DualConfigRoutingTest {
    private fun settings(): AppRoutingSettings {
        val original = AppRoutingSettings(packages = setOf("example.direct"))
        val enabled = RoutingModeTransitions.enable(original, "first", "first", listOf(
            MultiServerRule(packageName = "example.app", target = ServerRouteTarget(ServerRouteKind.DIRECT)),
            MultiServerRule(domain = "example.org", target = ServerRouteTarget(ServerRouteKind.DEFAULT))
        ), false)
        return selectDualServer(enabled, "second", true)
    }

    @Test fun secondSelectionSurvivesSerializationWithoutAnyAssignedTraffic() {
        val value = requireNotNull(settings().multiServer)
        assertEquals(value, MultiServerSettings.fromJson(JSONObject(value.toJson().toString())))
        assertEquals("second", value.secondaryProfileId)
    }

    @Test fun changingSecondMovesItsAppAndSiteRulesTogether() {
        val baseline = settings()
        val second = ServerRouteTarget(ServerRouteKind.PROFILE, "second")
        val active = updateDualSettings(baseline, baseline.multiServer!!.copy(rules = listOf(
            MultiServerRule(packageName = "example.app", target = second),
            MultiServerRule(domain = "example.org", target = second))))
        val changed = selectDualServer(active, "third", true)
        assertTrue(changed.multiServer!!.rules.all { it.target.profileId == "third" })
        assertEquals(changed.multiServer, changed.history!!.savedAssignments)
        assertEquals("first", changed.multiServer.defaultProfileId)
    }

    @Test fun sharedUidAssignmentReplacesConflictingAppRulesOnly() {
        val baseline = settings()
        val names = setOf("example.app", "example.sibling")
        val target = ServerRouteTarget(ServerRouteKind.PROFILE, "second")
        val rules = assignRoutingApps(baseline.multiServer!!.rules, names, target)
        assertEquals(2, rules.count { it.packageName in names })
        assertTrue(rules.filter { it.packageName in names }.all { it.target == target })
        assertEquals(1, rules.count { it.domain == "example.org" })
        val reverted = assignRoutingApps(rules, names, ServerRouteTarget(ServerRouteKind.DEFAULT))
        assertTrue(reverted.filter { it.packageName in names }.all { it.target.kind == ServerRouteKind.DEFAULT })
    }

    @Test fun pausedSecondExitUsesPrimaryForAppsAndSitesWithoutChangingSavedRules() {
        val baseline = settings()
        val second = ServerRouteTarget(ServerRouteKind.PROFILE, "second")
        val rules = baseline.multiServer!!.rules + listOf(
            MultiServerRule(packageName = "example.second", target = second),
            MultiServerRule(domain = "second.example.org", target = second))
        val enabled = updateDualSettings(baseline, baseline.multiServer.copy(rules = rules))
        val paused = updateDualSettings(enabled, enabled.multiServer!!.copy(secondaryPaused = true))
        val saved = MultiServerSettings.fromJson(paused.multiServer!!.toJson())
        assertTrue(saved.secondaryPaused)
        assertEquals(rules, saved.rules)
        assertEquals("second", saved.secondaryProfileId)
        assertEquals(setOf("first"), RoutePolicyIdentity.capture(paused)!!.profileIds)
        val first = ImportedProfile(id = "first", name = "Primary", protocol = "socks",
            server = "127.0.0.1", port = 1080,
            outboundJson = """{"type":"socks","server":"127.0.0.1","server_port":1080}""")
        val config = JSONObject(SingBoxConfigFactory.build(first, paused, "com.nova.vpn.android",
            listOf(first), "/fixtures/paused-dns.db"))
        val route = config.getJSONObject("route")
        val entries = route.getJSONArray("rules")
        val primaryTag = route.getString("final")
        val rulesInOrder = (0 until entries.length()).map(entries::getJSONObject)
        assertFalse(rulesInOrder.any {
            it.optString("action") == "resolve" && !it.has("domain") && !it.has("package_name")
        })
        assertFalse(rulesInOrder.any {
            it.optString("action") == "resolve" && it.optJSONArray("package_name")?.optString(0) == "example.second"
        })
        for ((selector, value) in listOf("domain" to "second.example.org", "package_name" to "example.second")) {
            val rule = (0 until entries.length()).map(entries::getJSONObject).single {
                it.optJSONArray(selector)?.optString(0) == value && it.optString("action") == "route"
            }
            assertEquals(primaryTag, rule.getString("outbound"))
        }
        val resumed = updateDualSettings(paused, saved.copy(secondaryPaused = false))
        assertEquals(rules, resumed.multiServer!!.rules)
        assertEquals(setOf("first", "second"), RoutePolicyIdentity.capture(resumed)!!.profileIds)
    }

    @Test fun disablingRestoresOriginalAndRetainsSecondSelection() {
        val active = settings()
        val disabled = RoutingModeTransitions.disable(active).settings
        assertNull(disabled.multiServer)
        assertEquals(setOf("example.direct"), disabled.packages)
        assertEquals("second", disabled.history!!.savedAssignments.secondaryProfileId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sameConfigCannotOccupyBothSlots() { selectDualServer(settings(), "first", true) }

    @Test fun legacySinglePinnedExitBecomesSecondWithoutChangingRules() {
        val json = MultiServerSettings(1, "first", listOf(MultiServerRule(
            packageName = "example.app", target = ServerRouteTarget(ServerRouteKind.PROFILE, "second")))).toJson()
        json.remove("secondaryProfileId")
        val read = MultiServerSettings.fromJson(json)
        assertEquals("second", read.secondaryProfileId)
        assertEquals("second", read.rules.single().target.profileId)
    }
}

