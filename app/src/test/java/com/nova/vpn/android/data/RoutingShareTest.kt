package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutingShareTest {
    private val first=ImportedProfile(id="first",name="France",protocol="socks",server="one.test",port=1080,
        outboundJson="""{"type":"socks","server":"one.test","password":"SECRET_ONE"}""",subscriptionUrl="https://sub.test/SECRET_SUB")
    private val second=first.copy(id="second",name="USA",server="two.test",subscriptionUrl=null,
        outboundJson="""{"type":"socks","server":"two.test","password":"SECRET_TWO"}""")
    private val multi=MultiServerSettings(8,"first",listOf(
        MultiServerRule(packageName="com.example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"second")),
        MultiServerRule(domain="example.org",target=ServerRouteTarget(ServerRouteKind.DIRECT))),"second")
    private val settings=AppRoutingSettings(sites=listOf(SiteRoutingRule("example.net",SiteRoutingTarget.DIRECT)),multiServer=multi,
        history=MultiServerRoutingHistory(SingleServerRouteSnapshot.capture(AppRoutingSettings(),"first"),multi))
    private val routes=RoutingPresets("source",listOf(RoutingPreset("source","Мой профиль",settings)))
    private val current=RoutingPresets.migrate(AppRoutingSettings())
    private fun pack(keys: Boolean)=RoutingShare.decode(RoutingShare.document(routes,listOf(first,second),"first",keys))

    @Test fun routingOnlyContainsNeitherKeysNorSubscriptions() {
        val doc=RoutingShare.document(routes,listOf(first,second),"first",false)
        val text=doc.toString()
        assertFalse(doc.has("profiles"));assertTrue(doc.isNull("selectedId"))
        listOf("SECRET_ONE","SECRET_TWO","SECRET_SUB","outbound","fullConfig","subscriptionUrl").forEach { assertFalse(text.contains(it)) }
        assertEquals(setOf("first","second"),RoutingShare.read(text,"").references)
    }
    @Test fun keysRequireEncryptionAndPassword() {
        val doc=RoutingShare.document(routes,listOf(first,second),"first",true)
        assertThrows(IllegalArgumentException::class.java) { RoutingShare.read(doc.toString(),"") }
        val encrypted=IdentityTransfer.seal(doc.toString(),"example-password")
        assertFalse(encrypted.contains("SECRET_ONE"))
        val restored=RoutingShare.read(encrypted,"example-password")
        assertEquals(2,restored.profiles.size);assertTrue(restored.profiles.all { it.subscriptionUrl==null })
        assertThrows(IllegalArgumentException::class.java) { RoutingShare.read(encrypted,"wrong-password") }
    }
    @Test fun routeOnlyRejectsEmbeddedProfiles() {
        val doc=RoutingShare.document(routes,listOf(first,second),null,false).put("profiles",JSONArray())
        assertThrows(IllegalArgumentException::class.java) { RoutingShare.decode(doc) }
    }
    @Test fun pendingEditIsNeverExported() {
        val pending=routes.stage("source","request","session",AppRoutingSettings())
        val result=RoutingShare.decode(RoutingShare.document(pending,listOf(first,second),null,false))
        assertNull(result.routing.pending);assertEquals(multi,result.routing.active.settings.multiServer)
    }
    @Test fun routingOnlyMapsSitesAppsAndHistoryWithoutChangingKeys() {
        val localFirst=first.copy(id="local-a",subscriptionUrl=null)
        val localSecond=second.copy(id="local-b")
        val catalog=ProfileSnapshot.migrate(listOf(localFirst,localSecond))
        val plan=RoutingShare.plan(pack(false),current,catalog,mapOf("first" to "local-a","second" to "local-b"))
        assertEquals(catalog.withAvailability(),plan.profiles)
        assertEquals(current.active,plan.routing.presets.first())
        assertEquals(2,plan.routing.presets.size);assertNotEquals(current.activeId,plan.routing.activeId)
        val active=plan.routing.active.settings
        assertEquals("local-a",active.multiServer!!.defaultProfileId)
        assertEquals("local-b",active.multiServer.secondaryProfileId)
        assertEquals("local-b",active.multiServer.rules.first().target.profileId)
        assertEquals("local-a",active.history!!.single!!.profileId)
        assertEquals(settings.sites,active.sites);assertNull(plan.selection)
    }
    @Test fun missingMappingNeverFallsBackToAnotherVpn() {
        assertThrows(IllegalArgumentException::class.java) { RoutingShare.plan(pack(false),current,ProfileSnapshot(),emptyMap()) }
    }
    @Test fun keysAreAddedWithNewIdsAndAllAssignmentsFollowThem() {
        val occupied=first.copy(outboundJson="""{"type":"socks","server":"existing.test"}""",subscriptionUrl=null)
        val plan=RoutingShare.plan(pack(true),current,ProfileSnapshot.migrate(listOf(occupied)),emptyMap())
        assertEquals(3,plan.profiles.profiles.size)
        assertEquals(occupied,plan.profiles.profiles.first())
        val imported=plan.profiles.profiles.single { it.configurationKey()==first.configurationKey() }
        assertNotEquals("first",imported.id)
        assertEquals(imported.id,plan.selection!!.profileId)
        assertEquals(imported.id,plan.routing.active.settings.multiServer!!.defaultProfileId)
    }
    @Test fun sameConfigurationKeepsLocalIdAndDoesNotDuplicate() {
        val local=first.copy(id="existing",subscriptionUrl=null)
        val plan=RoutingShare.plan(pack(true),current,ProfileSnapshot.migrate(listOf(local)),emptyMap())
        assertEquals(2,plan.profiles.profiles.size)
        assertEquals("existing",plan.routing.active.settings.multiServer!!.defaultProfileId)
    }
    @Test fun explicitlyImportedRetiredKeyCanBeReusedWithCorrectId() {
        val retired=first.copy(id="retired",subscriptionUrl=null,retiredFromSubscription=true)
        val plan=RoutingShare.plan(pack(true),current,ProfileSnapshot(profiles=listOf(retired)),emptyMap())
        assertEquals("retired",plan.routing.active.settings.multiServer!!.defaultProfileId)
        assertFalse(plan.profiles.profiles.single { it.id=="retired" }.retiredFromSubscription)
    }
    @Test fun mappingBothVpnRolesToSameServerIsRejected() {
        val catalog=ProfileSnapshot.migrate(listOf(first.copy(subscriptionUrl=null)))
        assertThrows(IllegalArgumentException::class.java) { RoutingShare.plan(pack(false),current,catalog,mapOf("first" to "first","second" to "first")) }
    }
    @Test fun missingReferenceLabelsAndDuplicateKeysAreRejected() {
        val doc=RoutingShare.document(routes,listOf(first,second),"first",true)
        doc.getJSONObject("labels").remove("first")
        assertThrows(IllegalArgumentException::class.java) { RoutingShare.decode(doc) }
        val duplicate=RoutingShare.document(routes,listOf(first,first),null,true)
        assertThrows(IllegalArgumentException::class.java) { RoutingShare.decode(duplicate) }
    }
    @Test fun localPendingSwitchCannotBeOverwritten() {
        assertThrows(IllegalArgumentException::class.java) {
            RoutingShare.plan(pack(true),current.stage(current.activeId,"request","session"),ProfileSnapshot(),emptyMap())
        }
    }
}
