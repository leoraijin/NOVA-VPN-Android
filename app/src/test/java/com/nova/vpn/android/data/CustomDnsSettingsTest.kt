package com.nova.vpn.android.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomDnsSettingsTest {
    private val server = CustomDnsServer(id = "fixture", name = "DNS", address = "https://dns.google/dns-query", domains = listOf("example.org"))
    private val dns = CustomDnsSettings(listOf(server), server.id)
    private fun profile() = ImportedProfile(id = "first", name = "Fixture", protocol = "socks", server = "127.0.0.1", port = 18001,
        outboundJson = """{"type":"socks","server":"127.0.0.1","server_port":18001,"version":"5"}""")
    private fun config(settings: AppRoutingSettings) = JSONObject(SingBoxConfigFactory.build(profile(), settings, "com.nova.vpn.android"))
    private fun items(root: JSONObject, section: String, field: String) = root.getJSONObject(section).getJSONArray(field).let { array -> List(array.length()) { array.getJSONObject(it) } }

    @Test fun acceptsSupportedTransportsAndRejectsInvalidAddresses() {
        listOf("9.9.9.9", "udp://[2001:4860:4860::8888]:53", "tcp://9.9.9.9:5353", "tls://dns.google", "https://dns.google/dns-query?fixture=1").forEach { server.copy(address = it).validate() }
        listOf("ftp://dns.google", "https://user:pass@dns.google/dns-query", "udp://9.9.9.9:0", "udp://9.9.9.9:99999", "udp://9.9.9.9/wrong", "https://dns.google/#fragment").forEach {
            assertTrue(it, runCatching { server.copy(address = it).validate() }.isFailure)
        }
    }
    @Test fun oldSavedSettingsLoadWithNoCustomDns() {
        val old = RoutingPresets.settingsJson(AppRoutingSettings()).apply { remove("customDns") }
        assertEquals(CustomDnsSettings(), RoutingPresets.readSettings(old).customDns)
        assertEquals(dns, RoutingPresets.readSettings(RoutingPresets.settingsJson(AppRoutingSettings(customDns = dns))).customDns)
    }
    @Test fun inactiveSelectionRestoresExactNativeConfig() {
        val base = config(AppRoutingSettings()).toString()
        assertEquals(base, config(AppRoutingSettings(customDns = dns.copy(selectedId = null))).toString())
        assertEquals(base, config(AppRoutingSettings(customDns = dns.copy(servers = listOf(server.copy(domains = emptyList()))))).toString())
    }
    @Test fun selectedSitesResolveAndDialDirectWithTheirOwnResolver() {
        val settings = AppRoutingSettings(packages = setOf("org.example.direct"), customDns = dns)
        val root = config(settings)
        val servers = items(root, "dns", "servers")
        val resolver = servers.single { it.optString("tag") == "nova-custom-dns-resolver" }
        assertEquals("https", resolver.getString("type"))
        assertEquals("dns.google", resolver.getString("server"))
        assertFalse(resolver.has("detour"))
        assertEquals("/dns-query", resolver.getString("path"))
        assertTrue(resolver.getJSONObject("tls").getBoolean("enabled"))
        val rules = items(root, "route", "rules")
        val resolve = rules.indexOfFirst { it.optString("server") == resolver.getString("tag") }
        assertTrue(resolve >= 0)
        assertEquals("route", rules[resolve + 1].getString("action"))
        assertEquals("nova-custom-dns-direct", rules[resolve + 1].getString("outbound"))
        assertEquals(".example.org", rules[resolve].getJSONArray("domain_suffix").getString(0))
        assertTrue(rules.any { it.optJSONArray("package_name")?.optString(0) == "org.example.direct" })
        val tun = root.getJSONArray("inbounds").getJSONObject(0)
        assertFalse(tun.has("include_package"))
        assertEquals(listOf("com.nova.vpn.android"), List(tun.getJSONArray("exclude_package").length()) { tun.getJSONArray("exclude_package").getString(it) })
        System.getenv("NOVA_DNS_FIXTURE_DIRECTORY")?.let { directory -> File(directory).mkdirs(); File(directory, "custom-dns-single.json").writeText(root.toString()) }
    }
    @Test fun multiModeRetainsBlocksFakeIpAndExitIdentities() {
        val rules = listOf(MultiServerRule(domain = "blocked.example", target = ServerRouteTarget(ServerRouteKind.BLOCK)),
            MultiServerRule(packageName = "org.example.chat", target = ServerRouteTarget(ServerRouteKind.PROFILE, "first")))
        val settings = MultiServerSettings(revision = 1, defaultProfileId = "first", rules = rules)
        val root = JSONObject(SingBoxConfigFactory.build(profile(), AppRoutingSettings(multiServer = settings, customDns = dns), "com.nova.vpn.android", listOf(profile()), "/private/fixture-dns.db"))
        assertTrue(items(root, "dns", "servers").any { it.optString("type") == "fakeip" })
        val routes = items(root, "route", "rules")
        val custom = routes.indexOfFirst { it.optString("outbound") == "nova-custom-dns-direct" }
        assertTrue(routes.indexOfFirst { it.optString("action") == "reject" && it.has("domain") } < custom)
        assertEquals(rules, settings.rules)
        System.getenv("NOVA_DNS_FIXTURE_DIRECTORY")?.let { directory -> File(directory).mkdirs(); File(directory, "custom-dns-multi.json").writeText(root.toString()) }
    }
    @Test fun dnsSurvivesMultiModeTransitions() {
        val initial = AppRoutingSettings(customDns = dns)
        val multi = RoutingModeTransitions.enable(initial, "first", "first", emptyList(), false)
        val changedDns = dns.copy(servers = listOf(server.copy(address = "9.9.9.9")))
        val restored = RoutingModeTransitions.disable(multi.copy(customDns = changedDns)).settings
        assertEquals(changedDns, restored.customDns)
    }
    @Test fun appAssignmentReplacesVpnAndPersistsAcrossModeChanges() {
        val app = "org.example.chat"
        val partner = "org.example.sibling"
        val original = AppRoutingSettings(customDns = dns)
        val multi = RoutingModeTransitions.enable(original, "first", "first", listOf(
            MultiServerRule(packageName = app, target = ServerRouteTarget(ServerRouteKind.DEFAULT))
        ), false)
        val changed = assignDnsApps(multi, setOf(app, partner), server.id)
        assertTrue(changed.multiServer!!.rules.none { it.packageName == app })
        assertEquals(mapOf(app to server.id, partner to server.id), changed.customDns.appServers)
        assertEquals(changed, RoutingPresets.readSettings(RoutingPresets.settingsJson(changed)))
        assertEquals(changed.customDns, RoutingModeTransitions.disable(changed).settings.customDns)
        assertTrue(clearDnsApps(changed, setOf(app, partner)).customDns.appServers.isEmpty())
    }
    @Test fun eachAppUsesItsOwnDnsBeforeFakeIpAndGuardRejectsDifferentResolversOnOneUid() {
        val first = "org.example.chat"
        val second = "org.example.browser"
        val third = "org.example.direct"
        val other = server.copy(id = "other", address = "9.9.9.9")
        val appDns = CustomDnsSettings(listOf(server, other), appServers = mapOf(first to server.id, second to other.id))
        val multi = MultiServerSettings(1, "first", listOf(MultiServerRule(domain = "blocked.example", target = ServerRouteTarget(ServerRouteKind.BLOCK))))
        val root = JSONObject(SingBoxConfigFactory.build(profile(), AppRoutingSettings(multiServer = multi, customDns = appDns),
            "com.nova.vpn.android", listOf(profile()), "/private/fixture-dns.db"))
        val dnsRules = items(root, "dns", "rules")
        val appRules = dnsRules.filter { it.has("package_name") && it.optString("action") == "route" }
        assertEquals(listOf(first, second), appRules.map { it.getJSONArray("package_name").getString(0) })
        assertEquals(2, appRules.map { it.getString("server") }.distinct().size)
        assertTrue(dnsRules.indexOf(appRules.last()) < dnsRules.indexOfFirst { it.optString("server") == "nova-fakeip" })
        val guard = requireNotNull(SharedUidExitGuard.capture(root.toString()))
        assertTrue(guard.conflicts(listOf(first, second)))
        assertTrue(guard.conflicts(listOf(first, third)))
        assertFalse(guard.conflicts(listOf(first, first)))
        System.getenv("NOVA_DNS_FIXTURE_DIRECTORY")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "custom-dns-apps-multi.json").writeText(root.toString())
        }
    }
    @Test fun dnsAppJoinsIncludedPackagesWithoutCapturingOtherApps() {
        val app = "org.example.chat"
        val proxyApp = "org.example.proxy"
        val settings = AppRoutingSettings(mode = AppRoutingMode.INCLUDE_SELECTED, packages = setOf(proxyApp),
            customDns = dns.copy(selectedId = null, appServers = mapOf(app to server.id)))
        val root = config(settings)
        val included = root.getJSONArray("inbounds").getJSONObject(0).getJSONArray("include_package")
        assertEquals(setOf(app, proxyApp), (0 until included.length()).map(included::getString).toSet())
        assertTrue(items(root, "route", "rules").any { it.optString("action") == "sniff" })
        System.getenv("NOVA_DNS_FIXTURE_DIRECTORY")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "custom-dns-apps-single.json").writeText(root.toString())
        }
    }
    @Test fun rejectsMissingSelectionAndDuplicateIds() {
        assertTrue(runCatching { dns.copy(selectedId = "missing").validate() }.isFailure)
        assertTrue(runCatching { dns.copy(servers = listOf(server, server)).validate() }.isFailure)
        assertTrue(runCatching { server.copy(domains = listOf("https://example.org")).validate() }.isFailure)
    }
}
