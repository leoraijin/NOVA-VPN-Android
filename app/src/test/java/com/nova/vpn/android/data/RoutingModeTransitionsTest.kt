package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class RoutingModeTransitionsTest {
    private val old=AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED,setOf("org.example.chat"),listOf(SiteRoutingRule("example.org",SiteRoutingTarget.DIRECT)),true)
    private fun rules()=listOf(MultiServerRule(packageName="org.example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))
    private fun enabled()=RoutingModeTransitions.enable(old,"old-us","fr",rules(),false)
    @Test fun enableCapturesCompletePreviousRulesAndSelection() {
        val next=enabled();assertEquals(old,next.history!!.single!!.settings());assertEquals("old-us",next.history.single!!.profileId)
        assertEquals("fr",next.multiServer!!.defaultProfileId);assertFalse(next.ipv6Enabled);assertTrue(next.packages.isEmpty());assertTrue(next.sites.isEmpty())
        assertEquals(setOf("org.example.chat"),old.packages)
    }
    @Test fun activeEditsKeepOriginalSnapshotAndLatestAssignments() {
        val first=enabled();val second=RoutingModeTransitions.enable(first,"other","us",emptyList(),true)
        assertEquals(first.history!!.single,second.history!!.single);assertEquals(2,second.multiServer!!.revision)
        val restored=RoutingModeTransitions.disable(second)
        assertEquals(old,restored.settings.copy(history=null));assertEquals("old-us",restored.selection!!.profileId)
        assertEquals(second.multiServer,restored.settings.history!!.savedAssignments)
    }
    @Test fun reenableCapturesEditsMadeWhileOrdinaryModeWasActive() {
        val off=RoutingModeTransitions.disable(enabled()).settings.copy(packages=setOf("org.example.new"),ipv6Enabled=false)
        val next=RoutingModeTransitions.enable(off,"jp","fr",rules(),true)
        val restored=RoutingModeTransitions.disable(next)
        assertEquals(off.copy(history=null),restored.settings.copy(history=null));assertEquals("jp",restored.selection!!.profileId)
    }
    @Test fun snapshotsAndDormantAssignmentsCannotBeMutatedThroughAliases() {
        val packages=linkedSetOf("org.example.chat");val sites=mutableListOf(SiteRoutingRule("example.org"));val sourceRules=rules().toMutableList()
        val next=RoutingModeTransitions.enable(old.copy(packages=packages,sites=sites),"us","fr",sourceRules,false)
        packages.clear();sites.clear();sourceRules.clear()
        assertEquals(1,next.history!!.single!!.packages.size);assertEquals(1,next.history.single!!.sites.size);assertEquals(1,next.history.savedAssignments.rules.size)
        assertThrows(UnsupportedOperationException::class.java){(next.history.single!!.packages as MutableSet<String>).clear()}
        assertThrows(UnsupportedOperationException::class.java){(next.history.savedAssignments.rules as MutableList<MultiServerRule>).clear()}
    }
    @Test fun oldEnabledDocumentDoesNotInventPreexistingRules() {
        val source=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",rules()))
        assertNull(source.history)
        val off=RoutingModeTransitions.disable(source)
        assertEquals("fr",off.selection!!.profileId);assertNull(off.settings.multiServer);assertEquals(source.multiServer,off.settings.history!!.savedAssignments)
    }
    @Test fun historyRoundtripsInActiveInactiveAndPendingPresets() {
        val active=enabled();val off=RoutingModeTransitions.disable(active)
        val doc=RoutingPresets.migrate(active).let {it.copy(presets=it.presets+RoutingPreset("other","Other",off.settings))}
            .stage("my-routing","request","process",off.settings,off.selection)
        val decoded=RoutingPresets.fromJson(doc.toJson())
        assertEquals(3,doc.toJson().getInt("schema"));assertEquals(doc,decoded)
        assertEquals(setOf("fr","us","old-us"),decoded.referencedProfileIds)
        assertEquals(active,decoded.recover("different-process").active.settings);assertNull(decoded.recover("different-process").pending)
    }
    @Test fun forgetReleasesOnlyHistoryAndDeleteAllClearsDormantReferences() {
        val off=RoutingModeTransitions.disable(enabled()).settings
        assertEquals(old,RoutingModeTransitions.forget(off).settings)
        assertThrows(IllegalArgumentException::class.java){RoutingModeTransitions.forget(enabled())}
        val cleared=RoutingPresets.migrate(off).withoutServerAssignments()
        assertEquals(old,cleared.active.settings);assertTrue(cleared.referencedProfileIds.isEmpty());assertNull(cleared.active.settings.history)
    }
    @Test fun selectionAfterOrdinaryManualChoiceCanBeRememberedWithoutRewritingSavedAssignments() {
        val off=RoutingModeTransitions.disable(enabled()).settings;val moved=off.history!!.withProfile("jp")
        assertEquals("jp",moved.single!!.profileId);assertEquals(off.history.savedAssignments,moved.savedAssignments)
        assertEquals("old-us",off.history.single!!.profileId)
    }
    @Test fun corruptNestedHistoryAndUnsupportedSchemasAreRejected() {
        val json=enabled().history!!.toJson();val single=json.getJSONObject("single")
        assertThrows(IllegalArgumentException::class.java){MultiServerRoutingHistory.fromJson(JSONObject(json.toString()).put("schema",9))}
        single.put("history","nested-data")
        assertThrows(IllegalArgumentException::class.java){MultiServerRoutingHistory.fromJson(json)}
        val settings=RoutingPresets.settingsJson(old).put("history","invalid")
        assertThrows(IllegalArgumentException::class.java){RoutingPresets.readSettings(settings)}
        assertThrows(IllegalArgumentException::class.java){RoutingSelectionChange(" ")}
    }
    @Test fun oldRouteSchemasRemainReadableWithoutCreatingHistory() {
        for(schema in 1..2) {
            val oldDoc=RoutingPresets.migrate(old).toJson().put("schema",schema)
            val read=RoutingPresets.fromJson(oldDoc);assertEquals(old,read.active.settings);assertNull(read.active.settings.history)
        }
    }
    @Test fun emptyIncludeModeStillRequiresReviewOfPreviouslyDirectTraffic() {
        assertTrue(RoutingModeTransitions.needsMigrationReview(AppRoutingSettings(mode=AppRoutingMode.INCLUDE_SELECTED)))
        assertFalse(RoutingModeTransitions.needsMigrationReview(AppRoutingSettings()))
        assertFalse(RoutingModeTransitions.needsMigrationReview(old,resuming=true))
        assertFalse(RoutingModeTransitions.needsMigrationReview(enabled()))
    }
}
