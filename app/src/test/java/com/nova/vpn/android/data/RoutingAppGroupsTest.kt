package com.nova.vpn.android.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RoutingAppGroupsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun install(name: String,uid: Int,label: String) {
        val info=ApplicationInfo().apply { packageName=name;this.uid=uid;nonLocalizedLabel=label }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName=name;applicationInfo=info })
    }
    private val a="org.example.game"
    private val b="org.example.background"
    private val other="org.example.other"
    @Test fun nonLaunchableSiblingAppearsInTheSameSearchableGroup() {
        install(a,14001,"Game");install(b,14001,"Background")
        val groups=loadRoutingAppGroups(context.packageManager,context.packageName,listOf(AppListEntry(a,"Old label")),emptySet())
        assertEquals(1,groups.size)
        assertEquals(setOf(a,b),groups.single().packages)
        assertNull(groups.single().unavailableReason)
        assertEquals(listOf(b),filterAppList(groups.single().members,"background").map { it.packageName })
        assertEquals("Game",groups.single().members.single { it.packageName==a }.label)
    }
    @Test fun groupAssignmentAndRemovalPreserveSitesAndOtherApplications() {
        install(a,14001,"Game");install(b,14001,"Background");install(other,14002,"Other")
        val group=loadRoutingAppGroups(context.packageManager,context.packageName,listOf(AppListEntry(a,"Game")),emptySet()).single()
        val site=MultiServerRule(domain="example.com",target=ServerRouteTarget(ServerRouteKind.DIRECT))
        val unrelated=MultiServerRule(packageName=other,target=ServerRouteTarget(ServerRouteKind.PROFILE,"fr"))
        val before=listOf(site,unrelated,MultiServerRule(packageName=a,target=ServerRouteTarget(ServerRouteKind.PROFILE,"fr")))
        val after=assignRoutingApps(before,group.packages,ServerRouteTarget(ServerRouteKind.PROFILE,"us"))
        SharedUidRouting.validate(MultiServerSettings(1,"fr",after),{context.packageManager.getApplicationInfo(it,0).uid},{context.packageManager.getPackagesForUid(it)!!.toSet()})
        val policy=MultiServerPolicy(1,"fr",after,setOf("fr","us"))
        assertEquals("us",policy.decide(b,null).effective.profileId)
        assertEquals(ServerRouteKind.DIRECT,policy.decide(a,"example.com").effective.kind)
        assertEquals("fr",policy.decide(other,null).effective.profileId)
        assertEquals(listOf(site,unrelated),assignRoutingApps(after,group.packages,null))
        assertEquals(3,before.size)
    }
    @Test fun missingAssignedPackageRemainsRemovableAndCannotBeAssigned() {
        val group=loadRoutingAppGroups(context.packageManager,context.packageName,emptyList(),setOf(a)).single()
        assertEquals(setOf(a),group.packages)
        assertNotNull(group.unavailableReason)
        assertTrue(assignRoutingApps(listOf(MultiServerRule(packageName=a,target=ServerRouteTarget(ServerRouteKind.DIRECT))),group.packages,null).isEmpty())
    }
    @Test fun vpnSharedGroupIsVisibleButUnavailable() {
        val own=context.packageManager.getApplicationInfo(context.packageName,0).uid
        install(a,own,"Linked app")
        val group=loadRoutingAppGroups(context.packageManager,context.packageName,listOf(AppListEntry(a,"Linked")),emptySet()).single()
        assertTrue(context.packageName in group.packages)
        assertNotNull(group.unavailableReason)
    }
    @Test fun reloadDiscoversNewSiblingAndReplacesStaleLabels() {
        install(a,14001,"Game")
        val initial=loadRoutingAppGroups(context.packageManager,context.packageName,listOf(AppListEntry(a,"Game")),emptySet()).single()
        install(b,14001,"Background")
        val reloaded=loadRoutingAppGroups(context.packageManager,context.packageName,listOf(AppListEntry(a,"Stale")),emptySet()).single()
        assertEquals(setOf(a),initial.packages)
        assertEquals(setOf(a,b),reloaded.packages)
        assertEquals("Game",reloaded.members.single { it.packageName==a }.label)
    }
}
