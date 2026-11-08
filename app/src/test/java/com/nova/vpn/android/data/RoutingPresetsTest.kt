package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class RoutingPresetsTest {
    private val old = AppRoutingSettings(sites = listOf(SiteRoutingRule("example.org", SiteRoutingTarget.DIRECT)))
    private val next = AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf("org.example.game"), ipv6Enabled = true)
    private fun document() = RoutingPresets.migrate(old).let { it.copy(presets = it.presets + RoutingPreset("gaming", "Игры", next)) }

    @Test fun legacyMigrationPreservesAllRulesAndDoesNotInventTemplates() {
        val migrated = RoutingPresets.migrate(old)
        assertEquals(old, migrated.active.settings)
        assertEquals("Мой профиль", migrated.active.name)
        assertEquals(1, migrated.presets.size)
        assertEquals(migrated, RoutingPresets.fromJson(migrated.toJson()))
    }

    @Test fun pendingAndCommittedAreSeparateSnapshots() {
        val staged = document().stage("gaming", "request", "process")
        assertEquals(old, staged.active.settings)
        assertEquals(next, staged.pending!!.settings)
        val committed = RoutingPresets.fromJson(staged.toJson()).commit("request", "gaming")
        assertNull(committed.pending)
        assertEquals("gaming", committed.activeId)
        assertEquals(next, committed.active.settings)
    }

    @Test fun staleAcknowledgementCannotCommitNewerRules() {
        val staged = document().stage("gaming", "older", "process").stage("gaming", "newer", "process", next.copy(ipv6Enabled = false))
        assertThrows(IllegalArgumentException::class.java) { staged.commit("older", "gaming") }
        assertThrows(IllegalArgumentException::class.java) { staged.commit("newer", "my-routing") }
        assertFalse(staged.commit("newer", "gaming").active.settings.ipv6Enabled)
    }

    @Test fun interruptedProcessKeepsOldActiveRulesAndDiscardsUnconfirmedIntent() {
        val staged = document().stage("gaming", "request", "old-process")
        assertEquals(staged, staged.recover("old-process"))
        val recovered = RoutingPresets.fromJson(staged.toJson()).recover("new-process")
        assertNull(recovered.pending)
        assertEquals(old, recovered.active.settings)
    }

    @Test fun deleteAllClearsAssignmentsInActiveInactiveAndPendingPresets() {
        val assigned=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",listOf(
            MultiServerRule(packageName="org.example.app",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))))
        val before=RoutingPresets.migrate(assigned).let { it.copy(presets=it.presets+RoutingPreset("other","Other",assigned)) }
            .stage("other","request","process",assigned)
        val cleared=RoutingPresets.fromJson(before.toJson()).withoutServerAssignments()
        assertNull(cleared.pending)
        assertTrue(cleared.referencedProfileIds.isEmpty())
        assertTrue(cleared.presets.all { it.settings.multiServer==null && it.settings.mode==AppRoutingMode.EXCLUDE_SELECTED })
        assertEquals(setOf("fr","us"),before.referencedProfileIds)
        assertNotNull(before.pending)
    }
    @Test fun deleteAllPreservesUnrelatedLegacyRulesAndPresetNames() {
        val before=document()
        val cleared=before.withoutServerAssignments()
        assertEquals(before,cleared)
        assertEquals(cleared,RoutingPresets.fromJson(cleared.toJson()))
    }
    @Test fun clearingAssignmentsIsIdempotentAndPreservesIpv6Preference() {
        val original=RoutingPresets.migrate(AppRoutingSettings(ipv6Enabled=true,multiServer=MultiServerSettings(1,"fr",emptyList())))
        val cleared=original.withoutServerAssignments()
        assertEquals(cleared,cleared.withoutServerAssignments())
        assertTrue(cleared.active.settings.ipv6Enabled)
        assertEquals(original.activeId,cleared.activeId)
    }
    @Test fun stagedPolicyRequiresExactRevisionAndReferences() {
        val target=AppRoutingSettings(multiServer=MultiServerSettings(4,"fr",listOf(MultiServerRule(packageName="org.example.app",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))))
        val staged=RoutingPresets.migrate(old).stage("my-routing","request","session",target)
        assertThrows(IllegalArgumentException::class.java){staged.commitPolicy("request","my-routing",com.nova.vpn.android.RoutePolicyIdentity(3,setOf("fr","us")))}
        assertThrows(IllegalArgumentException::class.java){staged.commitPolicy("request","my-routing",com.nova.vpn.android.RoutePolicyIdentity(4,setOf("fr")))}
        assertEquals(old,staged.active.settings)
        val committed=staged.commitPolicy("request","my-routing",com.nova.vpn.android.RoutePolicyIdentity.capture(target))
        assertEquals(target,committed.active.settings)
        assertNull(committed.pending)
    }
    @Test fun missingIdentityCannotCommitAssignedRoutesAndLegacyCannotAcceptInventedIdentity() {
        val target=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",emptyList()))
        val staged=RoutingPresets.migrate(old).stage("my-routing","request","session",target)
        assertThrows(IllegalArgumentException::class.java){staged.commitPolicy("request","my-routing",null)}
        val legacy=RoutingPresets.migrate(old).stage("my-routing","legacy","session",next)
        assertThrows(IllegalArgumentException::class.java){legacy.commitPolicy("legacy","my-routing",com.nova.vpn.android.RoutePolicyIdentity(1,setOf("fr")))}
        assertEquals(next,legacy.commitPolicy("legacy","my-routing",null).active.settings)
    }
    @Test fun sameRevisionAndProfilesCannotCommitDifferentStagedRules() {
        val rule=MultiServerRule(packageName="org.example.a",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us"))
        val target=AppRoutingSettings(multiServer=MultiServerSettings(4,"fr",listOf(rule)))
        val changed=target.copy(multiServer=target.multiServer!!.copy(rules=listOf(rule.copy(packageName="org.example.b"))))
        val staged=RoutingPresets.migrate(old).stage("my-routing","request","session",target)
        assertThrows(IllegalArgumentException::class.java){staged.commitPolicy("request","my-routing",com.nova.vpn.android.RoutePolicyIdentity.capture(changed))}
        assertEquals(old,staged.active.settings)
        assertEquals(target,staged.commitPolicy("request","my-routing",com.nova.vpn.android.RoutePolicyIdentity.capture(target)).active.settings)
    }
    @Test fun corruptOrUnknownDocumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { RoutingPresets.fromJson(document().toJson().put("schema", 4)) }
        assertThrows(IllegalArgumentException::class.java) { RoutingPresets.fromJson(document().toJson().put("activeId", "missing")) }
        assertThrows(IllegalArgumentException::class.java) { document().copy(presets = document().presets + document().presets.first()).toJson() }
        assertThrows(IllegalArgumentException::class.java) { document().stage("gaming", "request", "process", next.copy(sites = listOf(SiteRoutingRule("https://example.org")))) }
    }
}
