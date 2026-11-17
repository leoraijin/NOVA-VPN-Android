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
    @Test fun rejectsMissingSelectionAndDuplicateIds() {
        assertTrue(runCatching { dns.copy(selectedId = "missing").validate() }.isFailure)
        assertTrue(runCatching { dns.copy(servers = listOf(server, server)).validate() }.isFailure)
        assertTrue(runCatching { server.copy(domains = listOf("https://example.org")).validate() }.isFailure)
    }
}
