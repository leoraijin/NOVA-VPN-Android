package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.junit.Assert.*
import org.junit.Test

class ActiveCatalogSessionTest {
    private val nodes=ExitHealthFixtures.profiles
    private val settings=AppRoutingSettings(multiServer=MultiServerSettings(7,"fr",listOf(
        MultiServerRule(packageName="example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))))
    private val config=ExitHealthFixtures.config()
    private val identity=AppliedConfiguration("active","fr","routes",RoutePolicyIdentity.capture(settings))
    private val session=ActiveCatalogSession(identity,RouteExitBindings.capture(settings.multiServer!!,nodes,config),RouteCommitBinding.capture(settings,"fr",config))
    private val document=RoutingPresets("routes",listOf(RoutingPreset("routes","Routes",settings)))
    @Test fun exactCommittedIntentRequiredAndUserPendingAlwaysWins() {
        assertTrue(session.matches(document))
        assertFalse(session.matches(document.stage("routes","new-user-request","process")))
        assertFalse(session.matches(document.copy(presets=listOf(document.active.copy(settings=settings.copy(ipv6Enabled=true))))))
        val another=document.active.copy(id="different")
        assertFalse(session.matches(RoutingPresets(another.id,listOf(another))))
        assertFalse(session.copy(identity=identity.copy(profileId="us")).matches(document))
    }
    @Test fun cosmeticAndUnrelatedCatalogChangesDoNotRefreshButOwnershipAndCredentialsDo() {
        assertFalse(session.needsRefresh(nodes.reversed().map {it.copy(name="renamed")}))
        assertFalse(session.needsRefresh(nodes+nodes.first().copy(id="unrelated")))
        assertTrue(session.needsRefresh(nodes.map {it.copy(retiredFromSubscription=true)}))
        assertTrue(session.needsRefresh(nodes.drop(1)))
        assertTrue(session.needsRefresh(nodes.mapIndexed {i,p->if(i==0)p.copy(outboundJson=p.outboundJson+" ") else p}))
        assertThrows(IllegalArgumentException::class.java) {session.needsRefresh(nodes+nodes.first())}
    }
    @Test fun MissingExitBecomingAvailableRequiresRefreshEvenWithSamePolicyRevision() {
        val retired=nodes.map {if(it.id=="us")it.copy(retiredFromSubscription=true) else it}
        val captured=RouteExitBindings("a".repeat(64),session.exits.entries.filterKeys {it!="us"})
        val previous=session.copy(exits=captured)
        assertFalse(previous.needsRefresh(retired))
        assertTrue(previous.needsRefresh(nodes))
    }
}
