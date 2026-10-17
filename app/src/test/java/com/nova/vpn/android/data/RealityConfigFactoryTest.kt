package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RealityConfigFactoryTest {
    private val own = "com.nova.vpn.android"
    private val apps = setOf("org.example.beta", "org.example.alpha")
    private fun generated(settings: AppRoutingSettings = AppRoutingSettings()): JSONObject {
        val profile = ProfileLinkParser.parseText(
            "vless://11111111-1111-1111-1111-111111111111@vpn.example:443" +
                "?security=reality&sni=front.example&pbk=fixture&sid=abcd&fp=chrome&type=tcp&flow=xtls-rprx-vision"
        ).single()
        return JSONObject(SingBoxConfigFactory.build(profile, settings, own))
    }
    private fun prepare(json: JSONObject) = RealityConfigFactory.prepare(json.toString(), own)
    private fun dns(json: JSONObject) = json.getJSONObject("dns")
    private fun route(json: JSONObject) = json.getJSONObject("route")
    private fun tun(json: JSONObject) = json.getJSONArray("inbounds").getJSONObject(0)
    private fun proxy(json: JSONObject) = json.getJSONArray("outbounds").getJSONObject(0)
    private fun servers(json: JSONObject) = dns(json).getJSONArray("servers")
    private fun rules(json: JSONObject) = route(json).getJSONArray("rules")
    private fun rejected(json: JSONObject) {
        assertThrows(IllegalArgumentException::class.java) { prepare(json) }
    }

    @Test fun generatedRealityPreservesTransportSecretsAndKeepsMetadataOutOfJson() {
        val source = generated()
        val original = source.toString()
        val tls = proxy(source).getJSONObject("tls").toString()
        val result = prepare(source)
        val output = JSONObject(result.config)
        assertEquals(tls, proxy(output).getJSONObject("tls").toString())
        assertEquals("xtls-rprx-vision", proxy(output).getString("flow"))
        assertEquals("11111111-1111-1111-1111-111111111111", proxy(output).getString("uuid"))
        assertFalse(proxy(output).has("domain_resolver"))
        assertFalse(route(output).has("default_domain_resolver"))
        assertEquals(source.keySet(), output.keySet())
        assertEquals(emptyList<String>(), result.includedPackages)
        assertEquals(listOf(own), result.excludedPackages)
        assertFalse(result.ipv6Enabled)
        assertEquals(original, source.toString())
        assertEquals(listOf("type", "tag", "address", "mtu").toSet(), tun(output).keySet())
        assertEquals(tun(source).getJSONArray("address").toString(), tun(output).getJSONArray("address").toString())
    }

    @Test fun generatedDnsUsesProtectedLiteralBootstrapAndValidatedDohHostname() {
        val result = JSONObject(prepare(generated()).config)
        val remote = servers(result).getJSONObject(0)
        assertEquals("https://cloudflare-dns.com/dns-query", remote.getString("address"))
        assertEquals("proxy", remote.getString("detour"))
        assertEquals(setOf("tag", "address", "detour"), remote.keySet())
        val bootstrap = servers(result).getJSONObject(1)
        assertEquals("udp", bootstrap.getString("type"))
        assertEquals("1.1.1.1", bootstrap.getString("server"))
        assertEquals(53, bootstrap.getInt("server_port"))
        assertEquals("direct", bootstrap.getString("detour"))
        assertEquals("nova-bootstrap-dns", bootstrap.getString("tag"))
        assertEquals("nova-bootstrap-dns", dns(result).getString("bootstrap"))
        assertFalse(result.toString().contains("insecure"))
    }

    @Test fun explicitBootstrapChoiceIsPreservedForNativeValidation() {
        val source = generated()
        dns(source).put("bootstrap", "custom-bootstrap")
        servers(source).put(JSONObject().put("type", "udp").put("tag", "custom-bootstrap")
            .put("server", "9.9.9.9").put("detour", "direct"))
        val output = JSONObject(prepare(source).config)
        assertEquals("custom-bootstrap", dns(output).getString("bootstrap"))
        assertFalse(dns(source).has("fakeip"))
    }

    @Test fun bootstrapIsNotInventedWhenNoGeneratedServerExists() {
        val source = generated()
        servers(source).remove(1)
        val output = JSONObject(prepare(source).config)
        assertFalse(dns(output).has("bootstrap"))
    }

    @Test fun bootstrapUsesActualDirectTagWithoutInventingOutbound() {
        val source = generated()
        source.getJSONArray("outbounds").getJSONObject(1).put("tag", "physical")
        val output = JSONObject(prepare(source).config)
        assertEquals("physical", servers(output).getJSONObject(1).getString("detour"))
        assertEquals(2, output.getJSONArray("outbounds").length())
    }

    @Test fun bootstrapRejectsDetouredDecoratedMissingOrAmbiguousDirect() {
        for (key in listOf("detour", "fragment", "domain_resolver")) {
            val source = generated()
            source.getJSONArray("outbounds").getJSONObject(1).put(key, "custom")
            rejected(source)
        }
        val missing = generated()
        missing.getJSONArray("outbounds").remove(1)
        rejected(missing)
        val duplicate = generated()
        duplicate.getJSONArray("outbounds").put(JSONObject().put("type", "block").put("tag", "direct"))
        rejected(duplicate)
    }

    @Test fun excludeSelectedMovesOnlyGeneratedPackageRuleToBuilder() {
        val result = prepare(generated(AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED, apps)))
        assertEquals(emptyList<String>(), result.includedPackages)
        assertEquals((apps + own).sorted(), result.excludedPackages)
        assertEquals(1, rules(JSONObject(result.config)).length())
        assertEquals("hijack-dns", rules(JSONObject(result.config)).getJSONObject(0).getString("action"))
    }

    @Test fun includeSelectedMovesMatchingPositiveAndInvertedPairToBuilder() {
        val result = prepare(generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, apps + own)))
        assertEquals(apps.sorted(), result.includedPackages)
        assertEquals(emptyList<String>(), result.excludedPackages)
        assertFalse(result.config.contains("package_name"))
        assertFalse(result.config.contains("invert"))
        assertEquals(1, rules(JSONObject(result.config)).length())
    }

    @Test fun includeOnlyOwnApplicationFailsInsteadOfCapturingAllApps() {
        rejected(generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf(own))))
    }

    @Test fun includeEmptyMovesDirectCatchAllToFinalAndPreservesDnsHijack() {
        val result = prepare(generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED)))
        assertTrue(result.includedPackages.isEmpty())
        assertEquals(listOf(own), result.excludedPackages)
        val rules = rules(JSONObject(result.config))
        assertEquals("hijack-dns", rules.getJSONObject(0).getString("action"))
        assertEquals(1, rules.length())
        assertEquals("direct", route(JSONObject(result.config)).getString("final"))
    }

    @Test fun vpnSiteExceptionWithDirectCatchAllGetsFakeDnsBeforeOriginalRule() {
        val source = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, sites =
            listOf(SiteRoutingRule("example.org", SiteRoutingTarget.VPN))))
        assertSiteDnsPairs(source)
        val output = JSONObject(prepare(source).config)
        assertEquals("direct", route(output).getString("final"))
        assertEquals(4, rules(output).length())
        assertEquals("proxy", rules(output).getJSONObject(2).getString("outbound"))
        assertEquals("proxy", rules(output).getJSONObject(3).getString("outbound"))
    }

    @Test fun mixedPoliciesWithoutTrustedUidResolverAreRejectedInBothModes() {
        AppRoutingMode.entries.forEach { mode ->
            SiteRoutingTarget.entries.forEach { target ->
                rejected(generated(AppRoutingSettings(mode, apps,
                    listOf(SiteRoutingRule("example.org", target)))))
            }
        }
    }

    @Test fun mixedSiteAndAppPoliciesKeepPriorityAndCaptureAllApplications() {
        AppRoutingMode.entries.forEach { mode ->
            SiteRoutingTarget.entries.forEach { target ->
                val source = generated(AppRoutingSettings(mode, apps,
                    listOf(SiteRoutingRule("example.org", target))))
                val snapshot = source.toString()
                val lookups = mutableListOf<String>()
                val result = RealityConfigFactory.prepare(snapshot, own) { name ->
                    lookups += name
                    if (name.endsWith("alpha")) 10001 else 10002
                }
                assertTrue(result.includedPackages.isEmpty())
                assertEquals(listOf(own), result.excludedPackages)
                assertEquals(apps.sorted(), lookups)
                val output = JSONObject(result.config)
                assertEquals("sniff", rules(output).getJSONObject(1).getString("action"))
                for (index in 2..3) {
                    assertEquals(rules(source).getJSONObject(index).toString(), rules(output).getJSONObject(index).toString())
                }
                val first = rules(output).getJSONObject(4)
                assertFalse(first.has("package_name"))
                assertEquals("[10001,10002]", first.getJSONArray("user_id").toString())
                assertEquals(if (mode == AppRoutingMode.INCLUDE_SELECTED) "proxy" else "direct", first.getString("outbound"))
                if (mode == AppRoutingMode.INCLUDE_SELECTED) {
                    val inverted = rules(output).getJSONObject(5)
                    assertTrue(inverted.getBoolean("invert"))
                    assertEquals("direct", inverted.getString("outbound"))
                }
                assertEquals(2, dns(output).getJSONArray("rules").length())
                assertEquals("nova-site-fakeip", dns(output).getJSONArray("rules").getJSONObject(0).getString("server"))
                assertEquals(snapshot, source.toString())
            }
        }
    }

    @Test fun mixedUidResolutionCannotSilentlyDropMissingOrOwnApplications() {
        val source = generated(AppRoutingSettings(packages = apps, sites = listOf(SiteRoutingRule("example.org"))))
        assertThrows(IllegalArgumentException::class.java) {
            RealityConfigFactory.prepare(source.toString(), own) { -1 }
        }
        val self = generated(AppRoutingSettings(packages = setOf(own), sites = listOf(SiteRoutingRule("example.org"))))
        assertThrows(IllegalArgumentException::class.java) {
            RealityConfigFactory.prepare(self.toString(), own) { 10001 }
        }
    }

    @Test fun dnsOnlySiteRulesCannotBeCombinedWithPackages() {
        val source = generated(AppRoutingSettings(packages = apps))
        dns(source).put("rules", JSONArray().put(JSONObject().put("domain", JSONArray().put("example.org"))
            .put("server", "remote-dns")))
        rejected(source)
    }

    @Test fun siteRulesWithoutPackagesKeepRoutesAndPairOriginalDnsRules() {
        val source = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org", SiteRoutingTarget.VPN))))
        assertSiteDnsPairs(source)
        val output = JSONObject(prepare(source).config)
        assertEquals(rules(source).toString(), rules(output).toString())
        assertEquals(dns(source).getJSONArray("rules").getJSONObject(0).toString(),
            dns(output).getJSONArray("rules").getJSONObject(1).toString())
    }

    private fun assertSiteDnsPairs(source: JSONObject) {
        val snapshot = source.toString()
        val originals = dns(source).getJSONArray("rules")
        val output = JSONObject(prepare(source).config)
        val pairs = dns(output).getJSONArray("rules")
        assertEquals(originals.length() * 2, pairs.length())
        repeat(originals.length()) { index ->
            val original = originals.getJSONObject(index)
            val fake = pairs.getJSONObject(index * 2)
            assertEquals("nova-site-fakeip", fake.getString("server"))
            assertEquals(original.toString(), pairs.getJSONObject(index * 2 + 1).toString())
            assertEquals(JSONObject(original.toString()).put("server", "nova-site-fakeip").toString(), fake.toString())
        }
        assertEquals(dns(source).get("final"), dns(output).get("final"))
        val expectedRoutes = JSONArray(rules(source).toString())
        val terminal = expectedRoutes.optJSONObject(expectedRoutes.length() - 1)
        if (terminal != null && terminal.keySet() == setOf("action", "outbound") &&
            terminal.opt("action") == "route" && terminal.opt("outbound") == "direct") {
            expectedRoutes.remove(expectedRoutes.length() - 1)
            assertEquals("direct", route(output).getString("final"))
        }
        assertEquals(expectedRoutes.toString(), rules(output).toString())
        assertEquals(snapshot, source.toString())
        val fake = servers(output).getJSONObject(servers(output).length() - 1)
        assertEquals(setOf("type", "tag", "inet4_range", "inet6_range"), fake.keySet())
        assertEquals("fakeip", fake.getString("type"))
        assertEquals("nova-site-fakeip", fake.getString("tag"))
        assertEquals("198.18.0.0/15", fake.getString("inet4_range"))
        assertEquals("fc00::/18", fake.getString("inet6_range"))
        assertEquals(servers(source).length() + 1, servers(output).length())
    }

    @Test fun mixedNestedSitesKeepOriginalOrderingStrategiesAndRealDnsTargets() {
        val source = generated(AppRoutingSettings(sites = listOf(
            SiteRoutingRule("example.org", SiteRoutingTarget.VPN),
            SiteRoutingRule("private.example.org", SiteRoutingTarget.DIRECT),
            SiteRoutingRule("deep.private.example.org", SiteRoutingTarget.VPN)
        )))
        assertSiteDnsPairs(source)
        val pairs = dns(JSONObject(prepare(source).config)).getJSONArray("rules")
        assertEquals("nova-bootstrap-dns", pairs.getJSONObject(1).getString("server"))
        assertEquals("prefer_ipv4", pairs.getJSONObject(0).getString("strategy"))
        assertEquals("prefer_ipv4", pairs.getJSONObject(1).getString("strategy"))
        assertEquals("remote-dns", pairs.getJSONObject(3).getString("server"))
        assertFalse(pairs.getJSONObject(2).has("strategy"))
    }

    @Test fun vpnSiteExceptionWithDirectFinalIsAllowedOnlyWithMatchedDnsPlan() {
        val source = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
        route(source).put("final", "direct")
        assertSiteDnsPairs(source)
        assertEquals("direct", route(JSONObject(prepare(source).config)).getString("final"))
        dns(source).put("rules", JSONArray())
        rejected(source)
    }

    @Test fun siteFakeDnsKeepsIpv6CaptureAndInheritedOrPerRuleStrategy() {
        for (ipv6 in listOf(false, true)) {
            val source = generated(AppRoutingSettings(ipv6Enabled = ipv6, sites = listOf(
                SiteRoutingRule("vpn.example.org"), SiteRoutingRule("direct.example.org", SiteRoutingTarget.DIRECT))))
            val result = prepare(source)
            val output = JSONObject(result.config)
            assertEquals(ipv6, result.ipv6Enabled)
            assertEquals(tun(source).getJSONArray("address").toString(), tun(output).getJSONArray("address").toString())
            assertTrue(tun(output).getJSONArray("address").toString().contains("fdfe:dcba:9876::1/126"))
            assertEquals(if (ipv6) "prefer_ipv4" else "ipv4_only", dns(output).getString("strategy"))
            assertSiteDnsPairs(source)
        }
    }

    @Test fun siteFakeDnsRejectsReservedTagAndExistingFakeIpTables() {
        val collisions = listOf(
            JSONObject("""{"type":"udp","tag":"nova-site-fakeip","server":"9.9.9.9"}"""),
            JSONObject("""{"type":"fakeip","tag":"custom-fake","inet4_range":"198.19.0.0/16"}"""),
            JSONObject("""{"tag":"custom-fake","address":"fakeip"}""")
        )
        collisions.forEach { server ->
            val source = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
            servers(source).put(server)
            rejected(source)
        }
        val source = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
        dns(source).put("fakeip", JSONObject().put("enabled", false))
        rejected(source)
    }

    @Test fun siteFakeDnsRejectsCustomDnsConditionsStrategiesAndUnmatchedRoutes() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("domain_keyword", JSONArray().put("example")) },
            { it.put("domain_suffix", JSONArray().put(".other.org")) },
            { it.put("domain", JSONArray().put("example.org").put("other.org")) },
            { it.put("strategy", "ipv6_only") },
            { it.put("action", "reject") },
            { it.put("server", "custom-dns") },
            { it.put("disable_cache", true) }
        )
        changes.forEach { change ->
            val source = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
            change(dns(source).getJSONArray("rules").getJSONObject(0))
            rejected(source)
        }
        val target = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
        rules(target).getJSONObject(2).put("outbound", "direct")
        rules(target).getJSONObject(3).put("outbound", "direct")
        rejected(target)
        val suffix = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
        rules(suffix).getJSONObject(3).put("domain_suffix", JSONArray().put(".other.org"))
        rejected(suffix)
    }

    @Test fun siteFakeDnsRejectsCustomPrioritiesDuplicateRulesAndAmbiguousServers() {
        val source = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
        rules(source).put(JSONObject().put("domain_keyword", JSONArray().put("custom"))
            .put("action", "route").put("outbound", "proxy"))
        rejected(source)
        val duplicate = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
        dns(duplicate).getJSONArray("rules").put(JSONObject(dns(duplicate).getJSONArray("rules").getJSONObject(0).toString()))
        rejected(duplicate)
        val ambiguous = generated(AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org"))))
        servers(ambiguous).put(JSONObject(servers(ambiguous).getJSONObject(0).toString()))
        rejected(ambiguous)
    }

    @Test fun nonSiteConfigDoesNotAcquireFakeServerOrChangeDnsFinal() {
        val source = generated()
        val output = JSONObject(prepare(source).config)
        assertEquals(servers(source).length(), servers(output).length())
        assertEquals(dns(source).get("final"), dns(output).get("final"))
        assertEquals(0, dns(output).getJSONArray("rules").length())
        assertFalse(output.toString().contains("nova-site-fakeip"))
    }

    @Test fun probeSocksAndItsRuleSurviveApplicationConversion() {
        val source = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, apps))
        val socks = JSONObject("""{"type":"socks","tag":"nova-local-health","listen":"127.0.0.1","listen_port":23456,"users":[{"username":"fixture","password":"private"}]}""")
        source.getJSONArray("inbounds").put(socks)
        val probe = JSONObject("""{"inbound":["nova-local-health"],"action":"route","outbound":"proxy"}""")
        val rules = JSONArray().put(probe)
        val original = rules(source)
        repeat(original.length()) { rules.put(original.get(it)) }
        route(source).put("rules", rules)
        val output = JSONObject(prepare(source).config)
        assertEquals(socks.toString(), output.getJSONArray("inbounds").getJSONObject(1).toString())
        assertEquals(probe.toString(), rules(output).getJSONObject(0).toString())
        assertEquals(2, rules(output).length())
    }

    @Test fun explicitProxyHealthProbeSurvivesDirectFinalNormalizationWithAndWithoutSites() {
        for (sites in listOf(emptyList(), listOf(SiteRoutingRule("example.org")))) {
            val source = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, sites = sites))
            val socks = JSONObject("""{"type":"socks","tag":"nova-local-health","listen":"127.0.0.1","listen_port":23456,"users":[{"username":"fixture","password":"private"}]}""")
            source.getJSONArray("inbounds").put(socks)
            val probe = JSONObject("""{"inbound":["nova-local-health"],"action":"route","outbound":"proxy"}""")
            val originals = rules(source)
            route(source).put("rules", JSONArray().put(probe).apply {
                repeat(originals.length()) { put(originals.get(it)) }
            })
            if (sites.isNotEmpty()) assertSiteDnsPairs(source)
            val output = JSONObject(prepare(source).config)
            assertEquals("direct", route(output).getString("final"))
            assertEquals(probe.toString(), rules(output).getJSONObject(0).toString())
            assertEquals(socks.toString(), output.getJSONArray("inbounds").getJSONObject(1).toString())
            assertEquals(originals.length(), rules(output).length())
            assertEquals("hijack-dns", rules(output).getJSONObject(1).getString("action"))
        }
    }

    @Test fun terminalNormalizationImplicitDnsDetourRejected() {
        for (legacy in listOf(false, true)) {
            for (detour in listOf<Any?>(null, JSONObject.NULL, "")) {
                val source = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED))
                val upstream = if (legacy) JSONObject().put("tag", "custom-dns")
                    .put("address", "https://private.example/dns-query")
                else JSONObject().put("type", "udp").put("tag", "custom-dns").put("server", "9.9.9.9")
                if (detour != null) upstream.put("detour", detour)
                servers(source).put(upstream)
                val snapshot = source.toString()
                val error = assertThrows(IllegalArgumentException::class.java) { prepare(source) }
                assertEquals("Невозможно безопасно изменить маршрут DNS без явно заданного подключения. Выберите sing-box.", error.message)
                assertNull(error.cause)
                assertEquals(snapshot, source.toString())
            }
        }
    }

    @Test fun terminalNormalizationGeneratedDnsStillWorksWithAndWithoutSiteFakeIp() {
        for (sites in listOf(emptyList(), listOf(SiteRoutingRule("example.org")))) {
            val source = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, sites = sites))
            val output = JSONObject(prepare(source).config)
            assertEquals("direct", route(output).getString("final"))
            assertEquals("proxy", servers(output).getJSONObject(0).getString("detour"))
            assertEquals("direct", servers(output).getJSONObject(1).getString("detour"))
            assertEquals("remote-dns", dns(output).getString("final"))
            if (sites.isNotEmpty()) {
                assertSiteDnsPairs(source)
                assertEquals("fakeip", servers(output).getJSONObject(2).getString("type"))
                assertFalse(servers(output).getJSONObject(2).has("detour"))
            }
        }
    }

    @Test fun terminalNormalizationUnchangedDirectFinalKeepsImplicitDnsDetour() {
        val source = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED))
        route(source).put("final", "direct")
        // Legacy URL needs no typed DoH SNI adaptation; exercise only the
        // unchanged-final branch with an intentionally implicit DNS detour.
        servers(source).put(0, JSONObject().put("tag", "remote-dns")
            .put("address", "https://cloudflare-dns.com/dns-query"))
        val output = JSONObject(prepare(source).config)
        assertEquals("direct", route(output).getString("final"))
        assertEquals(1, rules(output).length())
        assertEquals("https://cloudflare-dns.com/dns-query", servers(output).getJSONObject(0).getString("address"))
        assertFalse(servers(output).getJSONObject(0).has("detour"))
    }

    @Test fun normalizationLeavesNonterminalAndNonDirectUnconditionalRulesForNativeRejection() {
        val source = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED))
        rules(source).put(JSONObject().put("protocol", JSONArray().put("dns")).put("action", "hijack-dns"))
        val output = JSONObject(prepare(source).config)
        assertEquals(rules(source).toString(), rules(output).toString())
        assertEquals("proxy", route(output).getString("final"))
        for (target in listOf("proxy", "decorated-direct")) {
            val other = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED))
            other.getJSONArray("outbounds").put(JSONObject().put("type", "direct")
                .put("tag", "decorated-direct").put("fragment", JSONObject()))
            rules(other).getJSONObject(1).put("outbound", target)
            val prepared = JSONObject(prepare(other).config)
            assertEquals(rules(other).toString(), rules(prepared).toString())
            assertEquals("proxy", route(prepared).getString("final"))
        }
        val extra = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED))
        rules(extra).getJSONObject(1).put("custom_option", true)
        assertEquals(rules(extra).toString(), rules(JSONObject(prepare(extra).config)).toString())
    }

    @Test fun rejectsNonGeneratedPackageAndInvertShapes() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("invert", false) }, { it.put("invert", true) },
            { it.put("domain", JSONArray().put("example.org")) }, { it.put("action", "reject") },
            { it.put("outbound", "proxy") }, { it.put("package_name", JSONArray()) },
            { it.put("package_name", JSONArray().put(42)) },
            { it.put("package_name", JSONArray().put("org.example.alpha").put("org.example.alpha")) },
            { it.put("package_name", JSONArray().put("invalid/package")) }
        )
        mutations.forEach { mutate ->
            val source = generated(AppRoutingSettings(packages = apps))
            mutate(rules(source).getJSONObject(1))
            rejected(source)
        }
        val unmatched = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, apps))
        rules(unmatched).getJSONObject(2).put("package_name", JSONArray().put("org.other.app"))
        rejected(unmatched)
        val reordered = generated(AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, apps))
        val pair = rules(reordered)
        val first = pair.get(1)
        pair.put(1, pair.get(2)).put(2, first)
        rejected(reordered)
    }

    @Test fun rejectsArbitraryInvertEvenFalseAndNested() {
        for (invert in listOf(true, false, "true")) {
            val source = generated()
            rules(source).put(JSONObject().put("action", "route").put("outbound", "direct").put("invert", invert))
            rejected(source)
        }
        val source = generated()
        rules(source).put(JSONObject().put("type", "logical").put("rules", JSONArray()
            .put(JSONObject().put("invert", true))))
        rejected(source)
    }

    @Test fun rejectsCustomRulesAlongsidePackagesRatherThanChangingPriority() {
        val source = generated(AppRoutingSettings(packages = apps))
        rules(source).put(JSONObject().put("ip_cidr", JSONArray().put("10.0.0.0/8"))
            .put("action", "route").put("outbound", "direct"))
        rejected(source)
    }

    @Test fun onlyGeneratedTunOptionsMayBeRemoved() {
        mapOf<String, Any>("auto_route" to false, "strict_route" to false, "stack" to "system",
            "interface_name" to "custom", "exclude_package" to JSONArray().put("org.other.app"),
            "mtu" to 1400, "tag" to "custom", "address" to JSONArray().put("10.0.0.1/24"))
            .forEach { (key, value) -> val source = generated(); tun(source).put(key, value); rejected(source) }
    }

    @Test fun rejectsAllUnrepresentedTunRoutesAddressesAndIgnoredPlatformOptions() {
        listOf("inet4_address", "inet6_address", "route_address", "route_exclude_address",
            "inet4_route_address", "inet6_route_address", "inet4_route_exclude_address",
            "inet6_route_exclude_address", "include_package", "include_uid", "exclude_uid",
            "include_interface", "exclude_interface", "platform", "gso", "endpoint_independent_nat",
            "auto_redirect", "unknown_option").forEach { key ->
            val source = generated(); tun(source).put(key, JSONArray()); rejected(source)
        }
        val source = generated()
        source.getJSONArray("inbounds").put(JSONObject(tun(source).toString()))
        rejected(source)
    }

    @Test fun onlyExactGeneratedResolversAreStripped() {
        for (resolver in listOf<Any>("custom", JSONObject("""{"server":"custom","strategy":"prefer_ipv4"}"""),
            JSONObject("""{"server":"nova-bootstrap-dns","strategy":"ipv4_only"}"""),
            JSONObject("""{"server":"nova-bootstrap-dns","strategy":"prefer_ipv4","extra":true}"""))) {
            val source = generated()
            route(source).put("default_domain_resolver", resolver)
            proxy(source).put("domain_resolver", resolver)
            val output = JSONObject(prepare(source).config)
            assertEquals(resolver.toString(), route(output).get("default_domain_resolver").toString())
            assertEquals(resolver.toString(), proxy(output).get("domain_resolver").toString())
        }
    }

    @Test fun customDnsIsNeverReplacedByTagOrSilentlyLosesTlsIdentity() {
        val changed = generated()
        servers(changed).getJSONObject(0).getJSONObject("tls").put("server_name", "custom.example")
        rejected(changed)
        val extra = generated()
        servers(extra).getJSONObject(0).put("headers", JSONObject().put("Host", "custom.example"))
        rejected(extra)
        val local = generated()
        servers(local).getJSONObject(1).put("extra", true)
        rejected(local)
        val unknownLocal = generated()
        servers(unknownLocal).put(JSONObject().put("tag", "custom").put("address", "local"))
        rejected(unknownLocal)
        val legacy = generated()
        servers(legacy).put(0, JSONObject().put("tag", "remote-dns").put("address", "https://dns.example/query")
            .put("detour", "proxy").put("custom_field", "preserve"))
        val output = JSONObject(prepare(legacy).config)
        assertEquals(servers(legacy).getJSONObject(0).toString(), servers(output).getJSONObject(0).toString())
    }

    @Test fun neverTurnsOffTlsValidation() {
        val source = generated()
        proxy(source).getJSONObject("tls").put("insecure", true)
        rejected(source)
        val outboundInsecure = generated()
        proxy(outboundInsecure).put("allow_insecure", true)
        rejected(outboundInsecure)
        val dnsInsecure = generated()
        servers(dnsInsecure).getJSONObject(0).getJSONObject("tls").put("insecure", true)
        rejected(dnsInsecure)
    }

    @Test fun unsupportedProtocolsFailBeforeNativeWithoutEchoingProfileData() {
        listOf("vmess", "shadowsocks", "hysteria2", "tuic", "selector", "urltest", "fallback", "PRIVATE_SECRET").forEach { type ->
            val source = generated(); proxy(source).put("type", type)
            val error = assertThrows(IllegalArgumentException::class.java) { prepare(source) }
            assertEquals("Протокол подключения не поддерживается Reality Core. Выберите sing-box.", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun allWhitelistedProtocolsAreRetained() {
        for (type in listOf("vless", "trojan", "direct", "block", "dns")) {
            val source = generated()
            source.getJSONArray("outbounds").put(JSONObject().put("type", type).put("tag", "extra"))
            val output = JSONObject(prepare(source).config)
            assertEquals(type, output.getJSONArray("outbounds").getJSONObject(2).getString("type"))
        }
    }

    @Test fun unknownTopRouteAndOutboundFieldsRemainForNativeFailClosedValidation() {
        val source = generated().put("arbitrary_top", JSONObject().put("secret", "fixture"))
        route(source).put("arbitrary_route", true)
        proxy(source).put("arbitrary_outbound", true)
        val output = JSONObject(prepare(source).config)
        assertEquals(source.getJSONObject("arbitrary_top").toString(), output.getJSONObject("arbitrary_top").toString())
        assertTrue(route(output).getBoolean("arbitrary_route"))
        assertTrue(proxy(output).getBoolean("arbitrary_outbound"))
    }

    @Test fun ipv6ComesFromDnsPolicyNotAlwaysPresentGeneratedTunAddress() {
        assertFalse(prepare(generated()).ipv6Enabled)
        assertTrue(prepare(generated(AppRoutingSettings(ipv6Enabled = true))).ipv6Enabled)
        for (strategy in listOf("prefer_ipv6", "ipv6_only")) {
            val source = generated(); dns(source).put("strategy", strategy)
            assertTrue(prepare(source).ipv6Enabled)
        }
        val unspecified = generated(); dns(unspecified).remove("strategy")
        assertFalse(prepare(unspecified).ipv6Enabled)
    }

    @Test fun malformedJsonHasSanitizedMessageAndNoCause() {
        for (config in listOf("{PRIVATE_SECRET", "[]", "{\"outbounds\":\"PRIVATE_SECRET\"}")) {
            val error = assertThrows(IllegalArgumentException::class.java) { RealityConfigFactory.prepare(config, own) }
            assertEquals("Некорректная конфигурация Reality Core.", error.message)
            assertNull(error.cause)
        }
    }
}
