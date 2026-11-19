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
        assertEquals(setOf("example.direct", "example.sibling"), guard.androidDirectCandidates)
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

    @Test fun siteOverridesAndPerAppDnsKeepTheRequiredAppsInside() {
        val vpn1 = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@first.example:443").single()
        val vpn2 = vpn1.copy(id = "second")
        val direct = "org.example.direct"
        val dnsApp = "org.example.dns"
        val directRules = listOf(direct, dnsApp).map {
            MultiServerRule(packageName = it, target = ServerRouteTarget(ServerRouteKind.DIRECT))
        }
        val resolver = CustomDnsServer("custom", "Custom", "https://dns.example/dns-query", listOf("site.example"))
        val perAppDns = CustomDnsSettings(listOf(resolver), appServers = mapOf(dnsApp to resolver.id))
        fun candidates(site: ServerRouteTarget? = null, dns: CustomDnsSettings = perAppDns,
                       paused: Boolean = false, missing: Set<String> = emptySet()): Set<String> {
            val rules = directRules + listOfNotNull(site?.let { MultiServerRule(domain = "media.example", target = it) })
            val multi = MultiServerSettings(1, vpn1.id, rules, vpn2.id, secondaryPaused = paused)
            val config = SingBoxConfigFactory.build(vpn1, AppRoutingSettings(multiServer = multi, customDns = dns),
                "com.nova.fixture", listOf(vpn1, vpn2), "/tmp/direct-policy.db", unavailablePackages = missing)
            return SharedUidExitGuard.capture(config)?.androidDirectCandidates.orEmpty()
        }

        assertEquals(setOf(direct), candidates())
        assertEquals(setOf(direct), candidates(paused = true))
        assertEquals(setOf(direct), candidates(ServerRouteTarget(ServerRouteKind.DIRECT)))
        assertTrue(candidates(missing = setOf(direct)).isEmpty())
        assertTrue(candidates(ServerRouteTarget(ServerRouteKind.PROFILE, vpn2.id)).isEmpty())
        assertTrue(candidates(ServerRouteTarget(ServerRouteKind.BLOCK)).isEmpty())
        assertTrue(candidates(dns = perAppDns.copy(selectedId = resolver.id)).isEmpty())
    }
}
