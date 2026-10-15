package com.nova.vpn.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VpnDnsRoutingTest {
    @Test fun savedLegacyRealityOutboundGetsDefaultsAtConnectionWithoutReimport() {
        val saved = ImportedProfile(name = "Legacy", protocol = "vless", server = "vpn.example", port = 443,
            outboundJson = """{"type":"vless","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111","tls":{"enabled":true,"reality":{"enabled":true,"public_key":"fixture"}}}""")
        val config = JSONObject(SingBoxConfigFactory.build(saved))
        val tls = config.getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls")
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        assertTrue(tls.getJSONObject("utls").getBoolean("enabled"))
        assertEquals("vpn.example", tls.getString("server_name"))
        assertFalse(JSONObject(saved.outboundJson).getJSONObject("tls").has("utls"))
    }

    @Test fun fullRealityConfigKeepsExplicitFingerprintAndSni() {
        val saved = ProfileLinkParser.parseText("""{"outbounds":[{"tag":"edge","type":"vless","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111","tls":{"enabled":true,"server_name":"front.example","utls":{"enabled":true,"fingerprint":"firefox"},"reality":{"enabled":true,"public_key":"fixture"}}}],"route":{"final":"edge"}}""").single()
        val tls = JSONObject(SingBoxConfigFactory.build(saved)).getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls")
        assertEquals("firefox", tls.getJSONObject("utls").getString("fingerprint"))
        assertEquals("front.example", tls.getString("server_name"))
    }

    private fun profile() = ProfileLinkParser.parseText(
        "vless://11111111-1111-1111-1111-111111111111@vpn.example:443"
    ).single()

    @Test fun dnsInterceptPrecedesEvenCatchAllAppBypass() {
        val config = JSONObject(SingBoxConfigFactory.build(profile(), AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED)))
        val rules = config.getJSONObject("route").getJSONArray("rules")
        assertEquals("hijack-dns", rules.getJSONObject(0).getString("action"))
        assertEquals("direct", rules.getJSONObject(1).getString("outbound"))
        assertEquals(1500, config.getJSONArray("inbounds").getJSONObject(0).getInt("mtu"))
        assertEquals("gvisor", config.getJSONArray("inbounds").getJSONObject(0).getString("stack"))
    }

    @Test fun bypassSiteUsesPhysicalDnsBeforeImportedDnsRules() {
        val config = JSONObject(SingBoxConfigFactory.build(profile(), AppRoutingSettings(
            sites = listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT))
        )))
        val dns = config.getJSONObject("dns")
        val rule = dns.getJSONArray("rules").getJSONObject(0)
        assertEquals("nova-bootstrap-dns", rule.getString("server"))
        assertEquals("example.org", rule.getJSONArray("domain").getString(0))
        assertEquals(".example.org", rule.getJSONArray("domain_suffix").getString(0))
        assertTrue(dns.getBoolean("reverse_mapping"))
        assertEquals("nova-bootstrap-dns", config.getJSONObject("route").getJSONObject("default_domain_resolver").getString("server"))
    }

    @Test fun generatedConfigCanBeImportedAgainWithoutDuplicateBootstrap() {
        val first = SingBoxConfigFactory.build(profile())
        val imported = ProfileLinkParser.parseText(first).single()
        val config = JSONObject(SingBoxConfigFactory.build(imported))
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        assertEquals(1, (0 until servers.length()).count {
            servers.getJSONObject(it).optString("tag") == "nova-bootstrap-dns"
        })
    }

    @Test fun proxyHostnameUsesBootstrapDnsToAvoidDnsThroughProxyDeadlock() {
        val config = JSONObject(SingBoxConfigFactory.build(profile()))
        val proxy = config.getJSONArray("outbounds").getJSONObject(0)

        assertEquals("nova-bootstrap-dns", proxy.getJSONObject("domain_resolver").getString("server"))
        assertEquals("prefer_ipv4", proxy.getJSONObject("domain_resolver").getString("strategy"))
        assertEquals("nova-bootstrap-dns", config.getJSONObject("route").getJSONObject("default_domain_resolver").getString("server"))
    }

    @Test fun fullConfigMainOutboundGetsBootstrapResolverButKeepsExplicitResolver() {
        val imported = ProfileLinkParser.parseText(
            """{"outbounds":[{"type":"vless","tag":"edge-out","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"}],"route":{"final":"edge-out"}}"""
        ).single()
        val generated = JSONObject(SingBoxConfigFactory.build(imported))
        assertEquals(
            "nova-bootstrap-dns",
            generated.getJSONArray("outbounds").getJSONObject(0).getJSONObject("domain_resolver").getString("server")
        )

        val importedWithResolver = ProfileLinkParser.parseText(
            """{"outbounds":[{"type":"vless","tag":"edge-out","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111","domain_resolver":"custom-dns"}],"route":{"final":"edge-out"}}"""
        ).single()
        val preserved = JSONObject(SingBoxConfigFactory.build(importedWithResolver))
        assertEquals("custom-dns", preserved.getJSONArray("outbounds").getJSONObject(0).getString("domain_resolver"))
    }

    @Test fun detouredDirectOutboundIsNotReusedForBypass() {
        val imported = ProfileLinkParser.parseText("""{"outbounds":[{"type":"vless","tag":"proxy","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"},{"type":"direct","tag":"direct","detour":"proxy"}],"route":{"final":"proxy"}}""").single()
        val config = JSONObject(SingBoxConfigFactory.build(imported, AppRoutingSettings(
            sites = listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT))
        )))
        assertEquals("direct-1", config.getJSONObject("route").getJSONArray("rules").getJSONObject(2).getString("outbound"))
    }

    @Test fun compatibilityDnsKeepsBootstrapAndBypassDualStackAndAllowsIpv6OptIn() {
        val config = JSONObject(SingBoxConfigFactory.build(profile(), AppRoutingSettings(
            sites = listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT)))))
        val dns = config.getJSONObject("dns")
        assertEquals("ipv4_only", dns.getString("strategy"))
        assertEquals("prefer_ipv4", dns.getJSONArray("rules").getJSONObject(0).getString("strategy"))
        assertEquals("prefer_ipv4", config.getJSONObject("route").getJSONObject("default_domain_resolver").getString("strategy"))
        val enabled = JSONObject(SingBoxConfigFactory.build(profile(), AppRoutingSettings(ipv6Enabled = true)))
        assertEquals("prefer_ipv4", enabled.getJSONObject("dns").getString("strategy"))
    }

    @Test fun customDnsStrategyFromFullJsonIsPreserved() {
        val generated = JSONObject(SingBoxConfigFactory.build(profile()))
        generated.getJSONObject("dns").put("strategy", "prefer_ipv6")
        val imported = ProfileLinkParser.parseText(generated.toString()).single()
        val rebuilt = JSONObject(SingBoxConfigFactory.build(imported))
        assertEquals("prefer_ipv6", rebuilt.getJSONObject("dns").getString("strategy"))
    }
}
