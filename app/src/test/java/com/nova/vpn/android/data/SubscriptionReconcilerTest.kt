package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test

class SubscriptionReconcilerTest {
    private fun node(id: String, key: String = "key", port: Int = 443) = ImportedProfile(id = id,
        name = "Server", protocol = "vless", server = "example.test", port = port,
        outboundJson = """{"type":"vless","server":"example.test","server_port":$port,"uuid":"$key"}""")
    private fun catalog(profiles: List<ImportedProfile>, vararg membership: Pair<String, String>) =
        SubscriptionCatalog(profiles, membership.map { SubscriptionMembership(it.first, it.second) }.toSet(), emptySet())

    @Test fun appliedOldExitSurvivesRefreshEvenWhenSavedAssignmentsNoLongerReferenceIt() {
        val old=node("old",port=443)
        val incoming=node("new",port=8443)
        val document=RoutingPresets("ordinary",listOf(RoutingPreset("ordinary","Changed",AppRoutingSettings())))
        val policy=com.nova.vpn.android.RoutePolicyIdentity(1,setOf("old","another-active-exit"))
        val applied=com.nova.vpn.android.AppliedConfiguration("running","old","previous",policy)
        val before=catalog(listOf(old),"old" to "A")
        val retained=ProfileRoutingReferences.collect(document,applied)
        val next=SubscriptionReconciler.reconcile(before,"A",listOf(incoming),true,retained)
        assertEquals(setOf("old","new"),next.catalog.profiles.map {it.id}.toSet())
        assertFalse(next.catalog.memberships.any {it.profileId=="old"})
        assertFalse("old" in next.catalog.manuallyOwned)
        val projected=ProfileSnapshot(profiles=next.catalog.profiles,memberships=next.catalog.memberships,
            manuallyOwned=next.catalog.manuallyOwned).withAvailability()
        assertTrue(projected.profiles.single {it.id=="old"}.retiredFromSubscription)
        assertFalse(projected.profiles.single {it.id=="new"}.retiredFromSubscription)
        val withoutActive=SubscriptionReconciler.reconcile(before,"A",listOf(incoming),true,
            ProfileRoutingReferences.collect(document,null))
        assertEquals(listOf("new"),withoutActive.catalog.profiles.map {it.id})
    }
    @Test fun detachedActiveRecordIsReclaimedAfterReleaseOnlyOnCompleteRefresh() {
        val old=node("old",port=443);val current=node("current",port=8443)
        val retained=SubscriptionReconciler.reconcile(catalog(listOf(old),"old" to "A"),"A",listOf(current),true,setOf("old"))
        assertEquals(setOf("old","current"),retained.catalog.profiles.map {it.id}.toSet())
        assertFalse(retained.catalog.memberships.any {it.profileId=="old"})
        val partial=SubscriptionReconciler.reconcile(retained.catalog,"A",listOf(current),false)
        assertTrue(partial.catalog.profiles.any {it.id=="old"});assertEquals(0,partial.removed)
        val released=SubscriptionReconciler.reconcile(partial.catalog,"A",listOf(current),true)
        assertEquals(listOf("current"),released.catalog.profiles.map {it.id});assertEquals(1,released.removed)
    }
    @Test fun detachedRecordSurvivesWhileAnyRouteManualOrOtherSourceStillOwnsIt() {
        val old=node("old",port=443);val current=node("current",port=8443)
        val detached=catalog(listOf(old,current),"current" to "A")
        val routeRetained=SubscriptionReconciler.reconcile(detached,"A",listOf(current),true,setOf("old"))
        assertTrue(routeRetained.catalog.profiles.any {it.id=="old"});assertEquals(0,routeRetained.removed)
        val manual=SubscriptionReconciler.reconcile(detached.copy(manuallyOwned=setOf("old")),"A",listOf(current),true)
        assertTrue(manual.catalog.profiles.any {it.id=="old"});assertTrue("old" in manual.catalog.manuallyOwned)
        val shared=SubscriptionReconciler.reconcile(detached.copy(memberships=detached.memberships+SubscriptionMembership("old","B")),"A",listOf(current),true)
        assertTrue(shared.catalog.profiles.any {it.id=="old"});assertTrue(SubscriptionMembership("old","B") in shared.catalog.memberships)
    }
    @Test fun subscriptionRetainsActualOrdinaryExitDuringSelectionChangeWithoutBlockingExplicitManualDeletion() {
        val old=node("old",port=443);val selected=node("selected",port=8443)
        val document=RoutingPresets("ordinary",listOf(RoutingPreset("ordinary","Ordinary",AppRoutingSettings())))
        val applied=com.nova.vpn.android.AppliedConfiguration("running","old","ordinary")
        val retained=ProfileRoutingReferences.forSubscription(document,"selected",applied)
        assertEquals(setOf("old","selected"),retained)
        val next=SubscriptionReconciler.reconcile(catalog(listOf(old),"old" to "A"),"A",listOf(selected),true,retained)
        assertTrue(next.catalog.profiles.any {it.id=="old"})
        // An explicit user deletion in ordinary mode still follows the existing stop/switch UI flow.
        ProfileDeletionGuard.requireAllowed("old",document,applied)
        val released=SubscriptionReconciler.reconcile(next.catalog,"A",listOf(selected),true,
            ProfileRoutingReferences.forSubscription(document,"selected",null))
        assertEquals(listOf("selected"),released.catalog.profiles.map {it.id})
    }
    @Test fun nestedWebsocketHostAndActualServerPreventIdentityDonationFromDisplayMetadata() {
        fun ws(id:String,key:String,host:String,server:String="actual.example.test")=node(id,key).copy(server="display.example.test",
            outboundJson=org.json.JSONObject(node(id,key).outboundJson).put("server",server)
                .put("transport",org.json.JSONObject().put("type","ws").put("path","/fixture").put("headers",org.json.JSONObject().put("Host",host))).toString())
        val old=ws("bound","old","a.example.test")
        for(changed in listOf(ws("new","new","b.example.test"),ws("new","new","a.example.test","different.example.test"))) {
            val result=SubscriptionReconciler.reconcile(catalog(listOf(old),"bound" to "A"),"A",listOf(changed),true,setOf("bound"))
            assertEquals(old.outboundJson,result.catalog.profiles.single {it.id=="bound"}.outboundJson)
            assertTrue(result.catalog.profiles.any {it.id=="new"})
            assertFalse(result.catalog.memberships.any {it.profileId=="bound"})
        }
    }
    @Test fun partialResponseCannotEstablishUniqueRotationButFullResponseStillCan() {
        val old=node("bound","old").copy(userSettings=ProfileUserSettings(favorite=true),latencyMillis=25)
        val current=catalog(listOf(old),"bound" to "A")
        val partial=SubscriptionReconciler.reconcile(current,"A",listOf(node("new","new")),false,setOf("bound"))
        assertEquals(setOf("bound","new"),partial.catalog.profiles.map {it.id}.toSet())
        assertEquals(old.outboundJson,partial.catalog.profiles.single {it.id=="bound"}.outboundJson)
        assertFalse(partial.catalog.profiles.single {it.id=="new"}.userSettings.favorite)
        val full=SubscriptionReconciler.reconcile(current,"A",listOf(node("new","new")),true)
        assertEquals("bound",full.catalog.profiles.single().id);assertTrue(full.catalog.profiles.single().userSettings.favorite)
        assertNull(full.catalog.profiles.single().latencyMillis)
    }
    @Test fun exactConnectionKeepsAssignedIdRegardlessOfRowOrderAndReturnedRetiredLatencyExpires() {
        val old=node("bound","old").copy(latencyMillis=25)
        for(rows in listOf(listOf(node("rotated","new"),node("exact","old")),listOf(node("exact","old"),node("rotated","new")))) {
            val result=SubscriptionReconciler.reconcile(catalog(listOf(old),"bound" to "A"),"A",rows,true,setOf("bound"))
            assertEquals(old.outboundJson,result.catalog.profiles.single {it.id=="bound"}.outboundJson)
            assertTrue(result.catalog.profiles.any {it.id=="rotated"})
        }
        val retired=old.copy(retiredFromSubscription=true)
        val returned=SubscriptionReconciler.reconcile(catalog(listOf(retired)),"A",listOf(node("incoming","old")),true,setOf("bound"))
        assertEquals("bound",returned.catalog.profiles.single().id);assertNull(returned.catalog.profiles.single().latencyMillis)
        assertTrue(SubscriptionMembership("bound","A") in returned.catalog.memberships)
    }
    @Test fun opaqueDependentOutboundCannotRotateUsingPlaceholderNetworkFields() {
        val old=node("bound","old").copy(outboundJson=org.json.JSONObject(node("bound","old").outboundJson).put("detour","private-chain").toString())
        val incoming=old.copy(id="new",outboundJson=org.json.JSONObject(old.outboundJson).put("uuid","new").toString())
        val result=SubscriptionReconciler.reconcile(catalog(listOf(old),"bound" to "A"),"A",listOf(incoming),true,setOf("bound"))
        assertEquals(old.outboundJson,result.catalog.profiles.single {it.id=="bound"}.outboundJson)
        assertTrue(result.catalog.profiles.any {it.id=="new"})
    }
    @Test fun reorderedJsonExactMatchSurvivesAmbiguousEndpointAndKeepsAssignedIdentity() {
        val old=node("bound","old").copy(userSettings=ProfileUserSettings(favorite=true))
        val reordered=node("incoming","old").copy(outboundJson="""{"uuid":"old","server_port":443,"tag":"provider label","server":"example.test","type":"vless"}""")
        val rotated=node("rotated","new")
        for(rows in listOf(listOf(rotated,reordered),listOf(reordered,rotated))) {
            val result=SubscriptionReconciler.reconcile(catalog(listOf(old),"bound" to "A"),"A",rows,true,setOf("bound"))
            assertEquals(setOf("bound","rotated"),result.catalog.profiles.map {it.id}.toSet())
            val assigned=result.catalog.profiles.single {it.id=="bound"}
            assertEquals("old",org.json.JSONObject(assigned.outboundJson).getString("uuid"))
            assertTrue(assigned.userSettings.favorite)
            assertTrue(SubscriptionMembership("bound","A") in result.catalog.memberships)
        }
    }
    @Test fun equivalentJsonRowsDeduplicateWithoutCollapsingDifferentCredentials() {
        val old=node("bound","old")
        val reordered=node("duplicate","old").copy(outboundJson="""{"uuid":"old","server_port":443,"server":"example.test","type":"vless"}""")
        val result=SubscriptionReconciler.reconcile(catalog(listOf(old),"bound" to "A"),"A",
            listOf(reordered,node("same","old"),node("different","new")),false)
        assertEquals(setOf("bound","different"),result.catalog.profiles.map {it.id}.toSet())
        assertEquals(1,result.added)
        assertEquals(2,result.catalog.memberships.size)
    }

    @Test fun maximumCatalogRefreshKeepsEveryAssignedIdAcrossReorderedAmbiguousRows() {
        val old=(0 until 2000).map {index->node("bound-$index","key-$index").copy(userSettings=ProfileUserSettings(favorite=index%2==0))}
        val current=SubscriptionCatalog(old,old.map {SubscriptionMembership(it.id,"A")}.toSet(),emptySet())
        val incoming=(1999 downTo 0).map {index->node("incoming-$index","key-$index").copy(
            outboundJson="""{"uuid":"key-$index","server_port":443,"server":"example.test","type":"vless"}""")}
        val result=SubscriptionReconciler.reconcile(current,"A",incoming,true,old.map {it.id}.toSet())
        assertEquals(old.map {it.id}.toSet(),result.catalog.profiles.map {it.id}.toSet())
        assertEquals(current.memberships,result.catalog.memberships)
        assertEquals(0,result.added);assertEquals(0,result.removed)
        result.catalog.profiles.forEach {profile->
            val index=profile.id.removePrefix("bound-").toInt()
            assertEquals("key-$index",org.json.JSONObject(profile.outboundJson).getString("uuid"))
            assertEquals(index%2==0,profile.userSettings.favorite)
        }
    }
    @Test fun ambiguousSavedSemanticIdentityDoesNotDonateAnArbitraryAssignedId() {
        val first=node("first","same")
        val second=node("second","same").copy(outboundJson="""{"uuid":"same","server_port":443,"server":"example.test","type":"vless"}""")
        val before=catalog(listOf(first,second),"first" to "A","second" to "A")
        val result=SubscriptionReconciler.reconcile(before,"A",listOf(node("new","same")),true,setOf("first","second"))
        assertEquals(setOf("first","second","new"),result.catalog.profiles.map {it.id}.toSet())
        assertEquals(setOf(SubscriptionMembership("new","A")),result.catalog.memberships)
    }

    @Test fun sharedNodeSurvivesRemovalFromOneSubscription() {
        val old = node("old")
        val other = node("other", port = 8443)
        val result = SubscriptionReconciler.reconcile(catalog(listOf(old), "old" to "A", "old" to "B"), "A", listOf(other), true)
        assertEquals(setOf("old", "other"), result.catalog.profiles.map { it.id }.toSet())
        assertTrue(SubscriptionMembership("old", "B") in result.catalog.memberships)
        assertFalse(SubscriptionMembership("old", "A") in result.catalog.memberships)
    }

    @Test fun unambiguousRotationKeepsIdentityAndUserMetadataButInvalidatesLatency() {
        val old = node("old").copy(userSettings = ProfileUserSettings(true, "My server", 5), latencyMillis = 30)
        val result = SubscriptionReconciler.reconcile(catalog(listOf(old), "old" to "A"), "A", listOf(node("new", "rotated")), true)
        val next = result.catalog.profiles.single()
        assertEquals("old", next.id)
        assertEquals(old.userSettings, next.userSettings)
        assertNull(next.latencyMillis)
        assertEquals(1, result.updated)
    }

    @Test fun partialResponseCannotDeleteOldNodes() {
        val old = node("old")
        val result = SubscriptionReconciler.reconcile(catalog(listOf(old), "old" to "A"), "A", listOf(node("new", port = 8443)), false)
        assertEquals(2, result.catalog.profiles.size)
        assertEquals(0, result.removed)
        assertTrue(result.partial)
    }

    @Test fun rotatingOneSourceCannotOverwriteAnotherSourcesCredentials() {
        val old = node("shared", "old-key")
        val result = SubscriptionReconciler.reconcile(catalog(listOf(old), "shared" to "A", "shared" to "B"),
            "A", listOf(node("new", "new-key")), true)
        assertEquals(old.outboundJson, result.catalog.profiles.first { it.id == "shared" }.outboundJson)
        assertEquals(setOf(SubscriptionMembership("shared", "B"), SubscriptionMembership("new", "A")), result.catalog.memberships)
    }

    @Test fun ambiguousEndpointsDoNotInheritFavorites() {
        val old = node("old").copy(userSettings = ProfileUserSettings(favorite = true))
        val result = SubscriptionReconciler.reconcile(catalog(listOf(old), "old" to "A"), "A",
            listOf(node("one", "new1"), node("two", "new2")), true)
        assertEquals(setOf("one", "two"), result.catalog.profiles.map { it.id }.toSet())
        assertTrue(result.catalog.profiles.none { it.userSettings.favorite })
    }

    @Test fun manuallyOwnedAndActiveNodesRemainAfterDetaching() {
        val old = node("old")
        val current = catalog(listOf(old), "old" to "A").copy(manuallyOwned = setOf("old"))
        val manual = SubscriptionReconciler.reconcile(current, "A", listOf(node("new", port = 8443)), true)
        assertTrue(manual.catalog.profiles.any { it.id == "old" })
        val active = SubscriptionReconciler.reconcile(current.copy(manuallyOwned = emptySet()), "A",
            listOf(node("new", port = 8443)), true, retainIds = setOf("old"))
        assertTrue(active.catalog.profiles.any { it.id == "old" })
    }

    @Test fun emptyResponseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionReconciler.reconcile(catalog(listOf(node("old")), "old" to "A"), "A", emptyList(), true)
        }
    }
}
