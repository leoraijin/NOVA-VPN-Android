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

    @Test fun corruptOrUnknownDocumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { RoutingPresets.fromJson(document().toJson().put("schema", 2)) }
        assertThrows(IllegalArgumentException::class.java) { RoutingPresets.fromJson(document().toJson().put("activeId", "missing")) }
        assertThrows(IllegalArgumentException::class.java) { document().copy(presets = document().presets + document().presets.first()).toJson() }
        assertThrows(IllegalArgumentException::class.java) { document().stage("gaming", "request", "process", next.copy(sites = listOf(SiteRoutingRule("https://example.org")))) }
    }
}
