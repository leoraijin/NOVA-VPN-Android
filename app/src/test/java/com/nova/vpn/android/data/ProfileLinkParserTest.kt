package com.nova.vpn.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLinkParserTest {
    @Test fun acceptsJsonArrayOfXrayConfigurationsFromSubscriptionPanels() {
        val config = """{"remarks":"Fixture #1","outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"vpn.example","port":443,"users":[{"id":"11111111-1111-1111-1111-111111111111","encryption":"none","flow":"xtls-rprx-vision"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"front.example","fingerprint":"firefox","publicKey":"fixture-public-key","shortId":"abcd"}}},{"protocol":"freedom","settings":{}},{"protocol":"blackhole","settings":{}}]}"""
        for (source in listOf("[$config]", java.util.Base64.getEncoder().encodeToString("[$config]".toByteArray()))) {
            val profile = ProfileLinkParser.parseText(source).single()
            assertEquals("Fixture #1", profile.name)
            assertEquals("vless", profile.protocol)
            val outbound = org.json.JSONObject(profile.outboundJson)
            assertEquals("front.example", outbound.getJSONObject("tls").getString("server_name"))
            assertEquals("xtls-rprx-vision", outbound.getString("flow"))
            assertEquals("firefox", outbound.getJSONObject("tls").getJSONObject("utls").getString("fingerprint"))
            assertEquals("fixture-public-key", outbound.getJSONObject("tls").getJSONObject("reality").getString("public_key"))
        }
    }

    @Test fun configArraysKeepDistinctServersAndDeduplicateEqualConfigurations() {
        val config = """{"outbounds":[{"type":"vless","tag":"proxy","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"}]}"""
        val other = config.replace("vpn.example", "other.example")
        val profiles = ProfileLinkParser.parseText("[$config,$other,$config]")
        assertEquals(2, profiles.size)
        assertEquals("sing-box", profiles.first().protocol)
    }

    @Test fun configArraysMayContainShareLinksButNeverRecursiveJsonStrings() {
        val link = "vless://11111111-1111-1111-1111-111111111111@vpn.example:443#Fixture"
        assertEquals("Fixture", ProfileLinkParser.parseText(org.json.JSONArray().put(link).toString()).single().name)
        for (source in listOf("[]", "[null]", "[42]", "[[]]", "[{}]", "[", org.json.JSONArray().put("[]").toString())) {
            assertTrue(runCatching { ProfileLinkParser.parseText(source) }.isFailure)
        }
    }

    @Test fun jsonSubscriptionArraysEnforceImportBounds() {
        val oversized = org.json.JSONArray()
        repeat(2001) { oversized.put(org.json.JSONObject()) }
        assertTrue(runCatching { ProfileLinkParser.parseText(oversized.toString()) }.isFailure)
        val users = org.json.JSONArray()
        repeat(2001) { users.put(org.json.JSONObject().put("id", "11111111-1111-1111-1111-111111111111")) }
        val server = org.json.JSONObject().put("address", "vpn.example").put("port", 443).put("users", users)
        val outbound = org.json.JSONObject().put("protocol", "vless")
            .put("settings", org.json.JSONObject().put("vnext", org.json.JSONArray().put(server)))
        val config = org.json.JSONObject().put("outbounds", org.json.JSONArray().put(outbound))
        val result = runCatching { ProfileLinkParser.parseText(org.json.JSONArray().put(config).toString()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("слишком много"))
    }

    @Test fun subscriptionLabelsMayContainLiteralHashes() {
        val label = "🇳🇱 Netherlands #1 · резерв #2"
        val link = "vless://11111111-1111-1111-1111-111111111111@vpn.example:443?security=tls&sni=edge.example#$label"
        for (text in listOf(link, java.util.Base64.getEncoder().encodeToString(link.toByteArray(Charsets.UTF_8)))) {
            val profile = ProfileLinkParser.parseText(text).single()
            assertEquals(label, profile.name)
            assertEquals("vpn.example", profile.server)
            val outbound = org.json.JSONObject(profile.outboundJson)
            assertEquals("edge.example", outbound.getJSONObject("tls").getString("server_name"))
        }
    }

    @Test fun encodedAndLiteralLabelHashesAreNotDoubleEncoded() {
        val profile = ProfileLinkParser.parseText(
            "trojan://secret@vpn.example:443#Server%20%231%20#2"
        ).single()
        assertEquals("Server #1 #2", profile.name)
    }

    @Test fun grpcShareLinksPreserveServiceNameAlpnAndTlsIdentity() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443?security=tls&type=grpc&serviceName=edge%2Fservice&alpn=h2%2Chttp%2F1.1&sni="
        ).single()
        val outbound = org.json.JSONObject(profile.outboundJson)
        assertEquals("edge/service", outbound.getJSONObject("transport").getString("service_name"))
        assertEquals("vpn.example", outbound.getJSONObject("tls").getString("server_name"))
        assertEquals("h2", outbound.getJSONObject("tls").getJSONArray("alpn").getString(0))
        assertEquals("http/1.1", outbound.getJSONObject("tls").getJSONArray("alpn").getString(1))
        val runtime = org.json.JSONObject(SingBoxConfigFactory.build(profile))
            .getJSONArray("outbounds").getJSONObject(0)
        assertEquals(outbound.getJSONObject("transport").toString(), runtime.getJSONObject("transport").toString())
        assertEquals("h2", runtime.getJSONObject("tls").getJSONArray("alpn").getString(0))
    }

    @Test fun vmessGrpcImportPreservesTransportAndTls() {
        val json = """{"add":"vpn.example","port":443,"id":"11111111-1111-1111-1111-111111111111","net":"grpc","path":"edge-service","tls":"tls","alpn":"h2"}"""
        val encoded = java.util.Base64.getEncoder().encodeToString(json.toByteArray())
        val outbound = org.json.JSONObject(ProfileLinkParser.parseText("vmess://$encoded").single().outboundJson)
        assertEquals("grpc", outbound.getJSONObject("transport").getString("type"))
        assertEquals("edge-service", outbound.getJSONObject("transport").getString("service_name"))
        assertEquals("vpn.example", outbound.getJSONObject("tls").getString("server_name"))
        assertEquals("h2", outbound.getJSONObject("tls").getJSONArray("alpn").getString(0))
    }

    @Test
    fun keepsDistinctFullSingBoxConfigsWhenImportingMoreThanOne() {
        val socks = ProfileLinkParser.parseText(
            """{"outbounds":[{"type":"socks","tag":"proxy","server":"127.0.0.1","server_port":2080}],"route":{"final":"proxy"}}"""
        ).single()
        val vless = ProfileLinkParser.parseText(
            """{"outbounds":[{"type":"vless","tag":"proxy","server":"127.0.0.1","server_port":2081,"uuid":"11111111-1111-1111-1111-111111111111"}],"route":{"final":"proxy"}}"""
        ).single()

        assertEquals("{}", socks.outboundJson)
        assertEquals("{}", vless.outboundJson)
        assertEquals(2, ImportedProfile.distinctByConfiguration(listOf(socks, vless, socks)).size)
    }

    @Test
    fun parsesVlessAndKeepsDisplayName() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443?security=tls&sni=edge.example#Home"
        ).single()

        assertEquals("Home", profile.name)
        assertEquals("vless", profile.protocol)
        assertEquals("vpn.example", profile.server)
        assertEquals(443, profile.port)
        assertTrue(profile.outboundJson.contains("\"server_name\":\"edge.example\""))
    }

    @Test
    fun realityVlessDefaultsSniAndUtlFingerprintLikeDesktopClient() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443?security=reality&pbk=public-key&sid=abcd"
        ).single()
        val tls = org.json.JSONObject(profile.outboundJson).getJSONObject("tls")

        assertEquals("vpn.example", tls.getString("server_name"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        assertTrue(tls.getJSONObject("utls").getBoolean("enabled"))
        assertTrue(tls.getJSONObject("reality").getBoolean("enabled"))
    }

    @Test
    fun realityVlessHonorsExplicitSniAndFingerprint() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443?security=reality&sni=edge.example&fp=firefox&pbk=public-key&sid=abcd"
        ).single()
        val tls = org.json.JSONObject(profile.outboundJson).getJSONObject("tls")

        assertEquals("edge.example", tls.getString("server_name"))
        assertEquals("firefox", tls.getJSONObject("utls").getString("fingerprint"))
    }

    @Test
    fun parsesQrLinksWithBomUnicodeNamesSpacesAndLiteralPercent() {
        val profile = ProfileLinkParser.parseText(
            "\uFEFFvless://11111111-1111-1111-1111-111111111111@vpn.example:443#Сервер 100% [основной]"
        ).single()

        assertEquals("Сервер 100% [основной]", profile.name)
        assertEquals("vpn.example", profile.server)
    }

    @Test
    fun parsesTrojanCredentialsAndTlsServerName() {
        val profile = ProfileLinkParser.parseText(
            "trojan://pa%3Ass@trojan.example:443?sni=edge.example#Work"
        ).single()

        assertEquals("Work", profile.name)
        assertEquals("trojan", profile.protocol)
        assertTrue(profile.outboundJson.contains("\"password\":\"pa:ss\""))
        assertTrue(profile.outboundJson.contains("\"server_name\":\"edge.example\""))
    }

    @Test
    fun parsesHysteria2AliasAndObfuscation() {
        val profile = ProfileLinkParser.parseText(
            "hy2://secret@hy2.example:8443?sni=edge.example&obfs=salamander&obfs-password=hidden#Fast"
        ).single()

        assertEquals("hysteria2", profile.protocol)
        assertEquals(8443, profile.port)
        assertTrue(profile.outboundJson.contains("\"type\":\"salamander\""))
        assertTrue(profile.outboundJson.contains("\"password\":\"hidden\""))
    }

    @Test
    fun parsesTuicUuidPasswordAndCongestionControl() {
        val profile = ProfileLinkParser.parseText(
            "tuic://11111111-1111-1111-1111-111111111111:secret@tuic.example:443?sni=edge.example&congestion_control=bbr"
        ).single()

        assertEquals("tuic", profile.protocol)
        assertTrue(profile.outboundJson.contains("\"uuid\":\"11111111-1111-1111-1111-111111111111\""))
        assertTrue(profile.outboundJson.contains("\"password\":\"secret\""))
        assertTrue(profile.outboundJson.contains("\"congestion_control\":\"bbr\""))
    }

    @Test
    fun parsesVmessBase64JsonWithWebSocketTls() {
        val profile = ProfileLinkParser.parseText(
            "vmess://eyJ2IjoiMiIsInBzIjoiVm1lc3MgdGVzdCIsImFkZCI6InZtZXNzLmV4YW1wbGUiLCJwb3J0IjoiNDQzIiwiaWQiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJhaWQiOiIwIiwibmV0Ijoid3MiLCJ0eXBlIjoibm9uZSIsImhvc3QiOiJjZG4uZXhhbXBsZSIsInBhdGgiOiIvd3MiLCJ0bHMiOiJ0bHMiLCJzbmkiOiJjZG4uZXhhbXBsZSJ9"
        ).single()

        assertEquals("Vmess test", profile.name)
        assertEquals("vmess", profile.protocol)
        assertTrue(!profile.outboundJson.contains("_nova_name"))
        assertTrue(profile.outboundJson.contains("\"type\":\"ws\""))
        assertTrue(profile.outboundJson.contains("\"server_name\":\"cdn.example\""))
    }

    @Test
    fun parsesFullConfigOnlyWhenItHasUsableOutbounds() {
        val profile = ProfileLinkParser.parseText(
            """{"outbounds":[{"type":"vless","tag":"proxy","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"}]}"""
        ).single()

        assertEquals("sing-box", profile.protocol)
        assertTrue(profile.fullConfigJson!!.contains("\"outbounds\""))
    }

    @Test
    fun parsesPrettyPrintedFullConfigAsOneProfile() {
        val profile = ProfileLinkParser.parseText(
            """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "proxy",
                  "server": "vpn.example",
                  "server_port": 443,
                  "uuid": "11111111-1111-1111-1111-111111111111"
                }
              ]
            }
            """.trimIndent()
        ).single()

        assertEquals("sing-box", profile.protocol)
        assertTrue(profile.fullConfigJson!!.contains("\"server\":\"vpn.example\""))
    }

    @Test
    fun parsesShadowsocksSIP002Base64Credentials() {
        val profile = ProfileLinkParser.parseText(
            "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpzZWNyZXQ@ss.example:8388#Office"
        ).single()

        assertEquals("Office", profile.name)
        assertEquals("shadowsocks", profile.protocol)
        assertEquals("ss.example", profile.server)
        assertEquals(8388, profile.port)
        assertTrue(!profile.outboundJson.contains("_nova_name"))
        assertTrue(profile.outboundJson.contains("\"method\":\"chacha20-ietf-poly1305\""))
    }

    @Test
    fun parsesShadowsocksLegacyBase64WholePayload() {
        val profile = ProfileLinkParser.parseText(
            "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpzZWNyZXRAc3MuZXhhbXBsZTo4Mzg4"
        ).single()

        assertEquals("shadowsocks", profile.protocol)
        assertEquals("ss.example", profile.server)
        assertEquals(8388, profile.port)
        assertTrue(profile.outboundJson.contains("\"password\":\"secret\""))
    }

    @Test
    fun leavesInlineProxyLinksForTheParser() {
        val link = "vless://11111111-1111-1111-1111-111111111111@vpn.example:443"
        assertEquals(link, SubscriptionFetcher.fetchIfUrl(link))
    }

    @Test
    fun rejectsInsecureSubscriptionUrls() {
        val result = runCatching { SubscriptionFetcher.fetchIfUrl("http://example.com/subscription") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTPS"))
    }

    @Test
    fun rejectsSubscriptionUrlsWithEmbeddedCredentials() {
        val result = runCatching { SubscriptionFetcher.fetchIfUrl("https://user:secret@example.com/subscription") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Некорректная"))
    }

    @Test
    fun rejectsInboundOnlyConfig() {
        val result = runCatching {
            ProfileLinkParser.parseText("""{"inbounds":[{"type":"tun"}]}""")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("outbounds"))
    }

    @Test
    fun generatedDnsUsesConfiguredFullConfigOutbound() {
        val profile = ProfileLinkParser.parseText(
            """{"route":{"final":"edge-out"},"outbounds":[{"type":"vless","tag":"edge-out","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"}]}"""
        ).single()

        val config = org.json.JSONObject(
            SingBoxConfigFactory.build(profile, ownPackage = "com.nova.vpn.android")
        )
        val dnsServer = config.getJSONObject("dns").getJSONArray("servers").getJSONObject(0)
        assertEquals("edge-out", dnsServer.getString("detour"))
    }

    @Test
    fun generatedProfileSendsDefaultTrafficAndDnsThroughTheProxy() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443"
        ).single()
        val config = org.json.JSONObject(SingBoxConfigFactory.build(profile))

        assertEquals("proxy", config.getJSONObject("route").getString("final"))
        assertEquals(
            "proxy",
            config.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("detour")
        )
    }

    @Test
    fun fullConfigAppRoutingExcludesPackagesAtAndroidBoundaryAndKeepsOtherTunOptions() {
        val profile = ProfileLinkParser.parseText(
            """{"inbounds":[{"type":"tun","tag":"custom-tun","stack":"system","include_package":["old.app"]}],"outbounds":[{"type":"vless","tag":"edge-out","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"}],"route":{"final":"edge-out"}}"""
        ).single()
        val config = org.json.JSONObject(
            SingBoxConfigFactory.build(
                profile,
                AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED, setOf("org.example.direct")),
                "com.nova.vpn.android"
            )
        )
        val tun = config.getJSONArray("inbounds").getJSONObject(0)

        assertEquals("custom-tun", tun.getString("tag"))
        assertEquals("gvisor", tun.getString("stack"))
        assertEquals("com.nova.vpn.android", tun.getJSONArray("exclude_package").getString(0))
        assertEquals("org.example.direct", tun.getJSONArray("exclude_package").getString(1))
        assertTrue(!tun.has("include_package"))
        assertEquals("edge-out", config.getJSONObject("route").getString("final"))
        val appRule = config.getJSONObject("route").getJSONArray("rules").getJSONObject(1)
        assertEquals("org.example.direct", appRule.getJSONArray("package_name").getString(0))
        assertEquals("direct", appRule.getString("outbound"))
    }

    @Test
    fun rejectsFullConfigWithMissingFinalOutbound() {
        val profile = ProfileLinkParser.parseText(
            """{"route":{"final":"missing-tag"},"outbounds":[{"type":"vless","tag":"edge-out","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"}]}"""
        ).single()

        val result = runCatching { SingBoxConfigFactory.build(profile) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("missing-tag"))
    }

    @Test
    fun includeSelectedAppsLimitsAndroidVpnToSelectedPackages() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443"
        ).single()
        val config = org.json.JSONObject(
            SingBoxConfigFactory.build(
                profile,
                AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf("org.example.browser")),
                "com.nova.vpn.android"
            )
        )
        val tun = config.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("org.example.browser", tun.getJSONArray("include_package").getString(0))
        assertTrue(!tun.has("exclude_package"))
        val rules = config.getJSONObject("route").getJSONArray("rules")
        assertEquals("hijack-dns", rules.getJSONObject(0).getString("action"))
        assertEquals("proxy", rules.getJSONObject(1).getString("outbound"))
        assertEquals("org.example.browser", rules.getJSONObject(1).getJSONArray("package_name").getString(0))
        assertTrue(rules.getJSONObject(2).getBoolean("invert"))
        assertEquals("direct", rules.getJSONObject(2).getString("outbound"))
    }

    @Test
    fun excludeSelectedAppsLeavesOtherAppsOnVpn() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443"
        ).single()
        val config = org.json.JSONObject(
            SingBoxConfigFactory.build(
                profile,
                AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED, setOf("org.example.direct")),
                "com.nova.vpn.android"
            )
        )
        val tun = config.getJSONArray("inbounds").getJSONObject(0)

        assertEquals("com.nova.vpn.android", tun.getJSONArray("exclude_package").getString(0))
        assertEquals("org.example.direct", tun.getJSONArray("exclude_package").getString(1))
        assertTrue(!tun.has("include_package"))
        val rule = config.getJSONObject("route").getJSONArray("rules").getJSONObject(1)
        assertEquals("org.example.direct", rule.getJSONArray("package_name").getString(0))
        assertEquals("direct", rule.getString("outbound"))
    }

    @Test
    fun siteRulesPrecedeAppRulesAndRouteApexAndSubdomainsThroughVpn() {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443"
        ).single()
        val config = org.json.JSONObject(
            SingBoxConfigFactory.build(
                profile,
                AppRoutingSettings(
                    AppRoutingMode.EXCLUDE_SELECTED,
                    setOf("org.example.browser"),
                    listOf(SiteRoutingRule("youtube.com", SiteRoutingTarget.VPN))
                )
            )
        )
        val rules = config.getJSONObject("route").getJSONArray("rules")

        assertEquals("hijack-dns", rules.getJSONObject(0).getString("action"))
        assertEquals("sniff", rules.getJSONObject(1).getString("action"))
        assertEquals("youtube.com", rules.getJSONObject(2).getJSONArray("domain").getString(0))
        assertEquals("proxy", rules.getJSONObject(2).getString("outbound"))
        assertEquals(".youtube.com", rules.getJSONObject(3).getJSONArray("domain_suffix").getString(0))
        assertEquals("org.example.browser", rules.getJSONObject(4).getJSONArray("package_name").getString(0))
        assertEquals("direct", rules.getJSONObject(4).getString("outbound"))
    }

    @Test
    fun directSiteRuleHasDirectOutboundEvenWhenImportedConfigDoesNot() {
        val profile = ProfileLinkParser.parseText(
            """{"route":{"final":"edge-out"},"outbounds":[{"type":"vless","tag":"edge-out","server":"vpn.example","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111"}]}"""
        ).single()
        val config = org.json.JSONObject(
            SingBoxConfigFactory.build(
                profile,
                AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT)))
            )
        )
        val outbounds = config.getJSONArray("outbounds")
        val rules = config.getJSONObject("route").getJSONArray("rules")

        assertTrue((0 until outbounds.length()).any { outbounds.getJSONObject(it).optString("type") == "direct" })
        assertEquals("direct", rules.getJSONObject(2).getString("outbound"))
    }
}
