package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreparedVpnConfigurationTest {
    @Test fun exitBindingsTravelWithPreparedConfigAndLegacyResultCannotCertifyGroup() {
        val context=RuntimeEnvironment.getApplication();val config=ExitHealthFixtures.config();val bindings=ExitHealthFixtures.bindings(config=config);val policy=ExitHealthFixtures.policy
        val prepared=PreparedVpnConfiguration(config,"Fixture","fr","routes",policy,bindings)
        val token=VpnUserRequests.gate.begin();assertTrue(VpnUserRequests.start(context,prepared,token))
        val command=org.robolectric.Shadows.shadowOf(context).nextStartedService
        assertFalse(command.hasExtra(NovaVpnService.EXTRA_CONFIG))
        val intent=requireNotNull(VpnUserRequests.consumeStart(command))
        val decoded=RouteExitBindings.decode(intent.getStringExtra(VpnUserRequests.EXTRA_EXITS))!!;decoded.validate(config,policy.profileIds)
        assertEquals(bindings.entries,decoded.entries)
        try {
            VpnRuntime.setConnected(AppliedConfiguration(token,"fr","routes",policy));VpnRuntime.beginExitChecks(bindings,policy,emptySet())
            val before=VpnRuntime.state.value;VpnRuntime.setTrafficResult(true);assertEquals(before,VpnRuntime.state.value)
            VpnRuntime.setRecovery(false,1,true);assertTrue(VpnRuntime.state.value.exitChecks.isEmpty());assertFalse(VpnRuntime.state.value.trafficVerified)
        } finally { VpnRuntime.setDisconnected() }
    }
    @Test fun untouchedConnectionRetainsItsSessionRevisionAndCompletedChecks() {
        val policy = ExitHealthFixtures.policy
        val identity = AppliedConfiguration("active", "fr", "routes", policy)
        val bindings = ExitHealthFixtures.bindings()
        try {
            VpnRuntime.setConnected(identity)
            VpnRuntime.beginExitChecks(bindings, policy, emptySet())
            val checking = VpnRuntime.state.value
            assertThrows(IllegalStateException::class.java) { VpnRuntime.restoreUntouchedConnection(checking) }
            bindings.entries.forEach { (id, entry) ->
                assertTrue(VpnRuntime.setExitChecking(checking.sessionRevision, id, entry.profileSha256))
                VpnRuntime.setExitResult(checking.sessionRevision, id, entry.profileSha256, true, 24)
            }
            val original = VpnRuntime.state.value
            assertTrue(original.trafficVerified)
            VpnRuntime.setConnecting()
            VpnRuntime.restoreUntouchedConnection(original)
            assertEquals(original, VpnRuntime.state.value)
        } finally { VpnRuntime.setDisconnected() }
    }
    @Test fun selectedPrimaryUsesManualMultiDefaultEvenWhenLegacyChoiceIsMissing() {
        val fr=ImportedProfile(id="fr",name="FR",protocol="socks",server="127.0.0.1",port=1,outboundJson="{}")
        val us=fr.copy(id="us",name="US")
        val multi=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",emptyList()))
        assertEquals(fr,PreparedVpnConfiguration.selectedPrimary(listOf(fr,us),multi,"old-missing"))
        val missing = requireNotNull(PreparedVpnConfiguration.selectedPrimary(listOf(us),multi,"us"))
        assertEquals("fr", missing.id)
        assertTrue(missing.retiredFromSubscription)
        assertNull(PreparedVpnConfiguration.selectedPrimary(listOf(fr, fr, us), multi, "us"))
        assertEquals(fr.copy(retiredFromSubscription=true),PreparedVpnConfiguration.selectedPrimary(listOf(fr.copy(retiredFromSubscription=true),us),multi,"us"))
    }
    @Test fun retiredMultiDefaultBlocksDefaultWithoutDisablingIndependentAppExit() {
        val fr=ImportedProfile(id="fr",name="FR",protocol="socks",server="127.0.0.1",port=1080,
            outboundJson="{\"type\":\"socks\",\"server\":\"127.0.0.1\",\"server_port\":1080}",retiredFromSubscription=true)
        val us=fr.copy(id="us",name="US",retiredFromSubscription=false)
        val settings=AppRoutingSettings(multiServer=MultiServerSettings(3,"fr",listOf(
            MultiServerRule(packageName="example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))))
        val primary = requireNotNull(PreparedVpnConfiguration.primaryForRoutingEdit(listOf(fr, us), settings, "fr"))
        assertNull(PreparedVpnConfiguration.primaryForRoutingEdit(listOf(fr, us), AppRoutingSettings(), "fr"))
        assertEquals("fr", PreparedVpnConfiguration.primaryForRoutingEdit(listOf(us), settings, "fr")?.id)
        assertNull(PreparedVpnConfiguration.primaryForRoutingEdit(listOf(us), settings, "new-missing"))
        assertEquals(us, PreparedVpnConfiguration.primaryForRoutingEdit(listOf(fr, us), settings, "us"))
        assertEquals("fr",primary.id)
        val config=SingBoxConfigFactory.build(primary,settings,"com.nova.fixture",listOf(fr,us),"/tmp/private-fixture-dns.db")
        val bindings=RouteExitBindings.capture(settings.multiServer!!,listOf(fr,us),config)
        assertEquals(setOf("us"),bindings.entries.keys)
        bindings.validateCatalog(listOf(fr,us),setOf("fr","us"))
        val route=org.json.JSONObject(config).getJSONObject("route")
        assertFalse(route.optString("final").startsWith("nova-exit-"))
        val rules=route.getJSONArray("rules")
        assertEquals("reject",rules.getJSONObject(rules.length()-1).getString("action"))
        assertEquals(setOf("action"),rules.getJSONObject(rules.length()-1).keys().asSequence().toSet())
        val dnsRules=org.json.JSONObject(config).getJSONObject("dns").getJSONArray("rules")
        assertEquals("reject",dnsRules.getJSONObject(dnsRules.length()-1).getString("action"))
        assertTrue((0 until rules.length()).map {rules.getJSONObject(it)}.any {
            it.optJSONArray("package_name")?.toString()?.contains("example.chat")==true && it.optString("outbound")==bindings.entries.getValue("us").tag })
        assertNull(PreparedVpnConfiguration.selectedPrimary(listOf(fr,us),AppRoutingSettings(),"fr"))
    }
    @Test fun missingSavedDefaultPreservesAssignedAppAndSiteUntilExactServerReturns() {
        val fr = ImportedProfile(id = "fr", name = "FR", protocol = "socks", server = "127.0.0.1", port = 1080,
            outboundJson = """{"type":"socks","server":"127.0.0.1","server_port":1080}""")
        val us = fr.copy(id = "us", name = "US", port = 1081, outboundJson = fr.outboundJson.replace("1080", "1081"))
        val unrelated = fr.copy(id = "other", name = "Other")
        val target = ServerRouteTarget(ServerRouteKind.PROFILE, "us")
        val settings = AppRoutingSettings(multiServer = MultiServerSettings(3, "fr", listOf(
            MultiServerRule(packageName = "example.chat", target = target),
            MultiServerRule(domain = "example.com", target = target))))
        val catalog = listOf(us, unrelated)
        val multi = requireNotNull(settings.multiServer)
        fun compile(profiles: List<ImportedProfile>): Pair<String, RouteExitBindings> {
            val primary = requireNotNull(PreparedVpnConfiguration.selectedPrimary(profiles, settings, "other"))
            assertEquals("fr", primary.id)
            val config = SingBoxConfigFactory.build(primary, settings, "com.nova.fixture", profiles, "/tmp/private-fixture-dns.db")
            return config to RouteExitBindings.capture(multi, profiles, config)
        }
        assertTrue(compile(emptyList()).second.entries.isEmpty())
        val (config, bindings) = compile(catalog)
        assertEquals(setOf("us"), bindings.entries.keys)
        val tag = bindings.entries.getValue("us").tag
        val root = org.json.JSONObject(config)
        val route = root.getJSONObject("route")
        val entries = route.getJSONArray("rules").let { rules -> (0 until rules.length()).map(rules::getJSONObject) }
        assertTrue(entries.any { it.optJSONArray("package_name")?.optString(0) == "example.chat" && it.optString("outbound") == tag })
        assertTrue(entries.any { it.optJSONArray("domain")?.optString(0) == "example.com" && it.optString("outbound") == tag })
        assertEquals(setOf("action"), entries.last().keys().asSequence().toSet())
        assertEquals("reject", entries.last().getString("action"))
        val dns = root.getJSONObject("dns").getJSONArray("rules")
        assertEquals("reject", dns.getJSONObject(dns.length() - 1).getString("action"))
        bindings.validateCatalog(catalog, setOf("fr", "us"))

        assertThrows(IllegalArgumentException::class.java) { bindings.validateCatalog(catalog + fr, setOf("fr", "us")) }
        val (restoredConfig, restoredBindings) = compile(catalog + fr)
        assertEquals(setOf("fr", "us"), restoredBindings.entries.keys)
        val restoredRoute = org.json.JSONObject(restoredConfig).getJSONObject("route")
        assertEquals(restoredBindings.entries.getValue("fr").tag, restoredRoute.getString("final"))
    }

    @Test fun allRetiredProfilesCompileToRejectWithoutReadingTheirInvalidCredentials() {
        val fr=ImportedProfile(id="fr",name="FR",protocol="unsupported",server="retired.invalid",port=0,
            outboundJson="not-json-retired-secret",retiredFromSubscription=true)
        val settings=AppRoutingSettings(ipv6Enabled=true,multiServer=MultiServerSettings(4,"fr",listOf(
            MultiServerRule(packageName="example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")),
            MultiServerRule(domain="example.com",target=ServerRouteTarget(ServerRouteKind.PROFILE,"fr")))))
        val catalog=listOf(fr,fr.copy(id="us"))
        val anchor=requireNotNull(PreparedVpnConfiguration.selectedPrimary(catalog,settings,"us"))
        val config=SingBoxConfigFactory.build(anchor,settings,"com.nova.fixture",catalog,"/tmp/private-fixture-dns.db")
        assertFalse(config.contains("retired.invalid"));assertFalse(config.contains("retired-secret"))
        val bindings=RouteExitBindings.capture(settings.multiServer!!,catalog,config)
        assertTrue(bindings.entries.isEmpty());bindings.validateCatalog(catalog,setOf("fr","us"))
        val root=org.json.JSONObject(config)
        assertEquals(1,root.getJSONArray("inbounds").length())
        assertEquals("tun",root.getJSONArray("inbounds").getJSONObject(0).getString("type"))
        val outs=root.getJSONArray("outbounds")
        assertEquals(1,outs.length());assertEquals("nova-direct",outs.getJSONObject(0).getString("tag"))
        val rules=root.getJSONObject("route").getJSONArray("rules")
        assertEquals("reject",rules.getJSONObject(rules.length()-1).getString("action"))
        assertEquals(setOf("action"),rules.getJSONObject(rules.length()-1).keys().asSequence().toSet())
        assertTrue((0 until rules.length()).map {rules.getJSONObject(it)}.filter {it.has("package_name") || it.has("domain")}.all {it.optString("action")=="reject"})
        val dns=root.getJSONObject("dns").getJSONArray("rules")
        assertEquals("reject",dns.getJSONObject(dns.length()-1).getString("action"))
        val health=MultiExitHealthPlan.prepare(config,bindings,RoutePolicyIdentity.capture(settings)!!)
        assertTrue(health.probes.isEmpty());assertEquals(config,health.config)
    }
    @Test fun selectedPrimaryRefusesMissingOrClearedOrdinaryChoice() {
        val fr=ImportedProfile(id="fr",name="FR",protocol="socks",server="127.0.0.1",port=1,outboundJson="{}")
        assertNull(PreparedVpnConfiguration.selectedPrimary(listOf(fr),AppRoutingSettings(),"missing"))
        assertNull(PreparedVpnConfiguration.selectedPrimary(listOf(fr),AppRoutingSettings(),null))
        assertEquals(fr,PreparedVpnConfiguration.selectedPrimary(listOf(fr),AppRoutingSettings(),"fr"))
    }
    @Test fun exactCommitBindingTravelsWithPreparedConfig() {
        val context=RuntimeEnvironment.getApplication();val settings=AppRoutingSettings(ipv6Enabled=true)
        val binding=RouteCommitBinding.capture(settings,"us","native-config")
        val prepared=PreparedVpnConfiguration("native-config","Fixture","us","routes",commitBinding=binding)
        val token=VpnUserRequests.gate.begin();assertTrue(VpnUserRequests.start(context,prepared,token))
        val command=org.robolectric.Shadows.shadowOf(context).nextStartedService
        assertFalse(command.hasExtra(NovaVpnService.EXTRA_CONFIG))
        val intent=requireNotNull(VpnUserRequests.consumeStart(command))
        val received=requireNotNull(RouteCommitBinding.decode(intent.getStringExtra(VpnUserRequests.EXTRA_COMMIT_BINDING)))
        received.validateConfig("native-config");assertTrue(received.matches(settings,"us"))
    }
    @Test fun preparedPolicyIdentityTravelsWithTheServiceIntent() {
        val context=RuntimeEnvironment.getApplication()
        val policy=RoutePolicyIdentity(7,setOf("fr","us"))
        val prepared=PreparedVpnConfiguration("synthetic-config","Fixture","fr","routes",policy)
        val token=VpnUserRequests.gate.begin()
        assertTrue(VpnUserRequests.start(context,prepared,token))
        val command=org.robolectric.Shadows.shadowOf(context).nextStartedService
        assertFalse(command.hasExtra(NovaVpnService.EXTRA_CONFIG))
        val intent=requireNotNull(VpnUserRequests.consumeStart(command))
        assertNotNull(intent)
        assertEquals(policy,RoutePolicyIdentity.decode(intent.getStringExtra(VpnUserRequests.EXTRA_POLICY)))
        assertEquals("fr",intent.getStringExtra(NovaVpnService.EXTRA_PROFILE_ID))
        assertEquals("routes",intent.getStringExtra(NovaVpnService.EXTRA_ROUTING_PRESET_ID))
        assertEquals(token,intent.getStringExtra(VpnUserRequests.EXTRA_REQUEST))
        assertFalse(intent.getStringExtra(VpnUserRequests.EXTRA_POLICY)!!.contains("synthetic-config"))
    }
    @Test fun configurationAndIdentityRemainBoundAfterActivePresetChanges() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("routing", 0).edit().clear().commit()
        val store = AppRoutingStore(context)
        store.save(AppRoutingSettings(packages = setOf("org.example.old")))
        val profile = ImportedProfile(id = "synthetic", name = "Fixture", protocol = "vless", server = "example.test", port = 443,
            outboundJson = "{\"type\":\"vless\",\"server\":\"example.test\",\"server_port\":443,\"uuid\":\"11111111-1111-4111-8111-111111111111\"}")
        val prepared = PreparedVpnConfiguration.prepare(context, profile)
        store.updatePresets { it.copy(activeId = "other", presets = it.presets + RoutingPreset("other", "Other", AppRoutingSettings(packages = setOf("org.example.new")))) }
        assertEquals("my-routing", prepared.routingPresetId)
        assertEquals("synthetic", prepared.profileId)
        assertTrue(prepared.config.contains("org.example.old"))
        assertFalse(prepared.config.contains("org.example.new"))
        assertEquals("other", PreparedVpnConfiguration.prepare(context, profile).routingPresetId)
    }
}

