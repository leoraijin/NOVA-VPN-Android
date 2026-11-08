package com.nova.vpn.android
import com.nova.vpn.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuarantinedExitBindingsTest {
    private val nodes=ExitHealthFixtures.profiles
    private val broken=nodes.map {if(it.id=="us")it.copy(outboundJson=JSONObject(it.outboundJson).put("type","unsupported-fixture").toString()) else it}
    private val settings=AppRoutingSettings(ipv6Enabled=true,multiServer=MultiServerSettings(8,"fr",listOf(
        MultiServerRule(packageName="example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")),
        MultiServerRule(domain="example.com",target=ServerRouteTarget(ServerRouteKind.PROFILE,"fr")))))
    private fun build(catalog:List<ImportedProfile>,blocked:Set<String>)=SingBoxConfigFactory.build(catalog.first {it.id=="fr"},settings,"com.nova.fixture",catalog,"/tmp/private-fixture-dns.db",blocked)
    @Test fun automaticQuarantineRejectsOnlyBrokenAppAndStrictManualBuildStillRefuses() {
        assertThrows(IllegalArgumentException::class.java) {build(broken,emptySet())}
        val config=build(broken,setOf("us"));val binding=RouteExitBindings.capture(settings.multiServer!!,broken,config,setOf("us"))
        assertEquals(setOf("fr"),binding.entries.keys);assertEquals(setOf("us"),binding.quarantined.keys)
        binding.validateCatalog(broken,setOf("fr","us"))
        val rules=JSONObject(config).getJSONObject("route").getJSONArray("rules")
        val app = (0 until rules.length()).map(rules::getJSONObject).filter { rule ->
            val packageName = rule.optJSONArray("package_name")?.optString(0)
            packageName != null && packageName != SharedUidExitGuard.CONFLICT_PACKAGE
        }
        assertEquals(1, app.size)
        assertEquals("example.chat", app.single().getJSONArray("package_name").getString(0))
        assertEquals("reject", app.single().getString("action"))
        assertEquals(binding.entries.getValue("fr").tag,JSONObject(config).getJSONObject("route").getString("final"))
        assertFalse(config.contains("unsupported-fixture"))
    }
    @Test fun codecCarriesBlockedExactFingerprintAndRepairInvalidatesCapturedQuarantine() {
        val config=build(broken,setOf("us"));val binding=RouteExitBindings.capture(settings.multiServer!!,broken,config,setOf("us"))
        val decoded=RouteExitBindings.decode(binding.encode())!!
        assertEquals(2,JSONObject(binding.encode()).getInt("schema"));assertEquals(binding.quarantined,decoded.quarantined)
        assertFalse(binding.encode().contains("unsupported-fixture"))
        assertThrows(UnsupportedOperationException::class.java) {(decoded.quarantined as MutableMap<String,String>).clear()}
        decoded.validate(config,setOf("fr","us"));decoded.validateCatalog(broken,setOf("fr","us"))
        assertThrows(IllegalArgumentException::class.java) {decoded.validateCatalog(nodes,setOf("fr","us"))}
        assertThrows(IllegalArgumentException::class.java) {decoded.validate(config,setOf("fr"))}
        val malformed=JSONObject(binding.encode());malformed.getJSONArray("quarantined").put(malformed.getJSONArray("quarantined").get(0))
        assertThrows(IllegalArgumentException::class.java) {RouteExitBindings.decode(malformed.toString())}
    }
    @Test fun healthyOrUnreferencedProfilesCannotBeQuarantinedAndLegacyCodecStillWorks() {
        assertThrows(IllegalArgumentException::class.java) {build(nodes,setOf("us"))}
        assertThrows(IllegalArgumentException::class.java) {build(broken,setOf("missing"))}
        val config=build(nodes,emptySet());val valid=RouteExitBindings.capture(settings.multiServer!!,nodes,config)
        assertEquals(1,JSONObject(valid.encode()).getInt("schema"));assertTrue(RouteExitBindings.decode(valid.encode())!!.quarantined.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {RouteExitBindings.capture(settings.multiServer!!,nodes,config,setOf("us"))}
        assertThrows(IllegalArgumentException::class.java) {RouteExitBindings(valid.configSha256,valid.entries,mapOf("fr" to "a".repeat(64)))}
    }
    @Test fun allUnsupportedExitsKeepTunRejectDefaultAndReportBlockedRatherThanProbeUnsupported() {
        val bad=broken.map {it.copy(outboundJson=JSONObject(it.outboundJson).put("type","unsupported-fixture").toString())}
        val config=build(bad,setOf("fr","us"));val bindings=RouteExitBindings.capture(settings.multiServer!!,bad,config,setOf("fr","us"))
        assertTrue(bindings.entries.isEmpty());assertEquals(setOf("fr","us"),bindings.quarantined.keys)
        val rules=JSONObject(config).getJSONObject("route").getJSONArray("rules")
        assertEquals("reject",rules.getJSONObject(rules.length()-1).getString("action"))
        val policy=RoutePolicyIdentity.capture(settings)!!
        val state=ExitHealthReducer.begin(VpnUiState(connected=true,appliedConfiguration=AppliedConfiguration("active","fr","routes",policy)),bindings,policy,emptySet())
        assertTrue(state.exitChecks.values.all {it.status==ExitCheckStatus.INCOMPATIBLE && it.profileSha256!=null})
        assertTrue(state.trafficCheckFailed);assertFalse(state.trafficVerified);assertFalse(state.trafficChecking)
        val plan=MultiExitHealthPlan.prepare(config,bindings,policy);assertTrue(plan.probes.isEmpty())
    }
}
