package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeExitIsolationTest {
    @Test fun isolatesOnlyAnExactRejectedExitAndRecompilesItsRoutes() {
        fun profile(id: String, version: String) = ImportedProfile(id = id, name = id, protocol = "socks",
            server = "127.0.0.1", port = 1080, outboundJson = JSONObject().put("type", "socks")
                .put("server", "127.0.0.1").put("server_port", 1080).put("version", version).toString())
        val fr = profile("fr", "5")
        val us = profile("us", "fixture-invalid")
        val profiles = listOf(fr, us)
        val multi = MultiServerSettings(7, fr.id, listOf(
            MultiServerRule(packageName = "example.chat", target = ServerRouteTarget(ServerRouteKind.PROFILE, us.id)),
            MultiServerRule(domain = "keep.example.test", target = ServerRouteTarget(ServerRouteKind.PROFILE, fr.id))))
        val settings = AppRoutingSettings(multiServer = multi)
        val config = SingBoxConfigFactory.build(fr, settings, "com.nova.fixture", profiles, "/private/native-fixture.db")
        val bindings = RouteExitBindings.capture(multi, profiles, config)
        val checked = mutableListOf<String>()
        val result = requireNotNull(NativeExitIsolation.rebuild(config, bindings, settings, "com.nova.fixture", { true }) {
            checked.add(it)
            !it.contains("fixture-invalid")
        })
        assertEquals(4, checked.size)
        assertTrue(checked.all { !JSONObject(it).has("inbounds") })
        assertEquals(setOf(fr.id), result.bindings.entries.keys)
        assertEquals(mapOf(us.id to RouteExitBindings.profileHash(us)), result.bindings.quarantined)
        result.bindings.validateCatalog(profiles, setOf(fr.id, us.id))
        assertThrows(IllegalArgumentException::class.java) {
            result.bindings.validateCatalog(listOf(fr, profile(us.id, "5")), setOf(fr.id, us.id))
        }
        assertThrows(IllegalStateException::class.java) { result.bindings.encode() }
        assertFalse(result.config.contains("fixture-invalid"))
        val rules = JSONObject(result.config).getJSONObject("route").getJSONArray("rules")
        val ruleList = (0 until rules.length()).map(rules::getJSONObject)
        assertTrue(ruleList.any { it.optJSONArray("package_name")?.optString(0) == "example.chat" && it.optString("action") == "reject" })
        assertTrue(ruleList.any { it.optJSONArray("domain")?.optString(0) == "keep.example.test" && it.optString("outbound") == "nova-exit-0" })
        assertNull(NativeExitIsolation.rebuild(config, bindings, settings, "com.nova.fixture", { true }) { false })
        assertNull(NativeExitIsolation.rebuild(config, bindings, settings, "com.nova.fixture", { true }) { true })
        assertNull(NativeExitIsolation.rebuild(config, bindings, settings, "com.nova.fixture", { false }) {
            fail("Obsolete request must not check an exit"); false
        })
        System.getenv("NOVA_NATIVE_ISOLATION_FIXTURES")?.let { path ->
            val directory = java.io.File(path, "small")
            check(directory.mkdirs())
            checked.forEachIndexed { index, probe -> java.io.File(directory, "probe-$index.json").writeText(probe) }
            java.io.File(directory, "original.json").writeText(config)
            java.io.File(directory, "isolated.json").writeText(result.config)
        }
    }

    @Test fun largeCatalogUsesBoundedGroupChecksAndNeverCommitsAnIncompleteSearch() {
        val badIndex = 777
        val profiles = (0 until 1024).map { index ->
            val id = "server-" + index.toString().padStart(4, '0')
            val version = if (index == badIndex) "fixture-invalid" else "5"
            ImportedProfile(id = id, name = id, protocol = "socks", server = "127.0.0.1", port = 1080,
                outboundJson = JSONObject().put("type", "socks").put("server", "127.0.0.1")
                    .put("server_port", 1080).put("version", version).toString())
        }
        val primary = profiles.first()
        val multi = MultiServerSettings(9, primary.id, profiles.drop(1).mapIndexed { index, profile ->
            MultiServerRule(domain = "route-$index.example.test", target = ServerRouteTarget(ServerRouteKind.PROFILE, profile.id))
        })
        val settings = AppRoutingSettings(multiServer = multi)
        val config = SingBoxConfigFactory.build(primary, settings, "com.nova.fixture", profiles, "/private/native-fixture.db")
        val bindings = RouteExitBindings.capture(multi, profiles, config)
        val checked = mutableListOf<String>()
        val result = requireNotNull(NativeExitIsolation.rebuild(config, bindings, settings, "com.nova.fixture", { true }) {
            checked.add(it)
            !it.contains("fixture-invalid")
        })
        assertEquals(22, checked.size)
        assertEquals(setOf(profiles[badIndex].id), result.bindings.quarantined.keys)
        assertEquals(1023, result.bindings.entries.size)
        result.bindings.validateCatalog(profiles, multi.rules.mapNotNull { it.target.profileId }.toSet() + primary.id)

        var attempts = 0
        assertNull(NativeExitIsolation.rebuild(config, bindings, settings, "com.nova.fixture", { true }) {
            ++attempts == 1
        })
        assertEquals(32, attempts)

        // A failure requiring more than one outbound must not be blamed on a healthy singleton.
        assertNull(NativeExitIsolation.rebuild(config, bindings, settings, "com.nova.fixture", { true }) {
            JSONObject(it).getJSONArray("outbounds").length() == 1
        })
        System.getenv("NOVA_NATIVE_ISOLATION_FIXTURES")?.let { path ->
            val directory = java.io.File(path, "large")
            check(directory.mkdirs())
            checked.forEachIndexed { index, probe -> java.io.File(directory, "probe-$index.json").writeText(probe) }
            java.io.File(directory, "original.json").writeText(config)
            java.io.File(directory, "isolated.json").writeText(result.config)
        }
    }
}
