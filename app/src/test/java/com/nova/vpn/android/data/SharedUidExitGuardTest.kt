package com.nova.vpn.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SharedUidExitGuardTest {
    @Test fun installedGroupChangesAreCheckedAgainstEffectiveCompiledExits() {
        val default = ImportedProfile(id = "fr", name = "FR", protocol = "socks", server = "127.0.0.1", port = 1080,
            outboundJson = """{"type":"socks","server":"127.0.0.1","server_port":1080}""")
        val rules = listOf(
            MultiServerRule(packageName = "example.direct", target = ServerRouteTarget(ServerRouteKind.DIRECT)),
            MultiServerRule(packageName = "example.sibling", target = ServerRouteTarget(ServerRouteKind.DIRECT)),
            MultiServerRule(packageName = "example.missing", target = ServerRouteTarget(ServerRouteKind.PROFILE, "gone")),
            MultiServerRule(packageName = "example.default", target = ServerRouteTarget(ServerRouteKind.PROFILE, "fr")),
            MultiServerRule(domain = "site.example", target = ServerRouteTarget(ServerRouteKind.DIRECT))
        )
        val multi = MultiServerSettings(1, "fr", rules)
        fun compile(settings: MultiServerSettings, profile: ImportedProfile = default) = SingBoxConfigFactory.build(
            profile, AppRoutingSettings(multiServer = settings), "com.nova.fixture", listOf(profile), "/tmp/uid-guard.db")
        val config = compile(multi)
        val compiledRules = JSONObject(config).getJSONObject("route").getJSONArray("rules")
        assertEquals(SharedUidExitGuard.CONFLICT_PACKAGE,
            compiledRules.getJSONObject(0).getJSONArray("package_name").getString(0))
        assertEquals("reject", compiledRules.getJSONObject(0).getString("action"))
        assertEquals("dns", compiledRules.getJSONObject(1).getJSONArray("sniffer").getString(0))
        assertEquals(53, compiledRules.getJSONObject(1).getJSONArray("port").getInt(0))
        assertTrue(compiledRules.getJSONObject(2).getBoolean("invert"))
        assertEquals(".+", compiledRules.getJSONObject(2).getJSONArray("domain_regex").getString(0))
        assertEquals("hijack-dns", compiledRules.getJSONObject(3).getString("action"))

        val guard = requireNotNull(SharedUidExitGuard.capture(config))
        assertFalse(guard.conflicts(listOf("example.direct", "example.sibling")))
        assertFalse(guard.conflicts(listOf("example.default", "example.new")))
        assertFalse(guard.conflicts(listOf("example.direct")))
        assertTrue(guard.conflicts(listOf("example.direct", "example.new")))
        assertTrue(guard.conflicts(listOf("example.new", "example.direct")))
        assertTrue(guard.conflicts(listOf("example.missing", "example.new")))
        assertFalse(requireNotNull(SharedUidExitGuard.capture(compile(multi, default.copy(retiredFromSubscription = true))))
            .conflicts(listOf("example.missing", "example.new")))
        assertNull(SharedUidExitGuard.capture(compile(multi.copy(rules = rules.filter { it.domain != null }))))
    }
}
