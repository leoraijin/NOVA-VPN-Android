package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RouteCommitBindingTest {
    private val original=AppRoutingSettings(packages=setOf("org.example.app"),ipv6Enabled=true)
    private val enabled=RoutingModeTransitions.enable(original,"old-id","fr",emptyList(),false)
    @Test fun roundtripContainsOnlyHashesAndCurrentId() {
        val binding=RouteCommitBinding.capture(enabled,"fr","synthetic-private-config")
        assertEquals(binding,RouteCommitBinding.decode(binding.encode()));binding.validateConfig("synthetic-private-config")
        assertFalse(binding.encode().contains("synthetic-private-config"));assertFalse(binding.encode().contains("org.example.app"));assertFalse(binding.encode().contains("old-id"))
    }
    @Test fun changedHistoryCannotCommitButDoesNotInvalidateTrafficOnlyRecovery() {
        val binding=RouteCommitBinding.capture(enabled,"fr","cfg")
        val changed=enabled.copy(history=enabled.history!!.withProfile("different-old-id"))
        assertFalse(binding.matches(changed,"fr"));assertTrue(binding.matchesTraffic(changed,"fr"))
    }
    @Test fun ordinaryRouteAndProfileChangesCannotBorrowAnotherBinding() {
        val binding=RouteCommitBinding.capture(original,"us","cfg")
        assertTrue(binding.matches(original,"us"));assertFalse(binding.matches(original,"fr"))
        assertFalse(binding.matchesTraffic(original.copy(ipv6Enabled=false),"us"))
        assertFalse(binding.matchesTraffic(original.copy(packages=setOf("org.example.other")),"us"))
        assertThrows(IllegalArgumentException::class.java){binding.validateConfig("changed-config")}
    }
    @Test fun nativeAdmissionRequiresTheExactStagedOrActiveTrafficSnapshot() {
        val document = RoutingPresets.migrate(original)
        val candidate = AppliedConfiguration("new", "fr", document.activeId, RoutePolicyIdentity.capture(enabled))
        val candidateBinding = RouteCommitBinding.capture(enabled, "fr", "cfg")
        val staged = document.stage(document.activeId, "new", "session", enabled, RoutingSelectionChange("fr"))
        RouteCommitBinding.requireCurrent(staged, candidate, candidateBinding, allowStaged = true)
        assertThrows(StaleRoutingSnapshot::class.java) {
            RouteCommitBinding.requireCurrent(staged, candidate, candidateBinding, allowStaged = false)
        }
        assertThrows(StaleRoutingSnapshot::class.java) {
            RouteCommitBinding.requireCurrent(staged, candidate.copy(requestId = "superseded"), candidateBinding, allowStaged = true)
        }
        val altered = enabled.copy(history = enabled.history!!.withProfile("another-old-id"))
        val stalePending = staged.copy(pending = staged.pending!!.copy(settings = altered))
        assertThrows(StaleRoutingSnapshot::class.java) {
            RouteCommitBinding.requireCurrent(stalePending, candidate, candidateBinding, allowStaged = true)
        }
        val active = RoutingPresets.migrate(altered)
        RouteCommitBinding.requireCurrent(active, candidate, candidateBinding, allowStaged = false)
        assertThrows(StaleRoutingSnapshot::class.java) {
            val changed = RoutingPresets.migrate(altered.copy(ipv6Enabled = !altered.ipv6Enabled))
            RouteCommitBinding.requireCurrent(changed, candidate, candidateBinding, allowStaged = false)
        }
        val old = AppliedConfiguration("old", "us", document.activeId)
        val oldBinding = RouteCommitBinding.capture(original, "us", "legacy")
        RouteCommitBinding.requireCurrent(staged, old, oldBinding, allowStaged = false)
        assertThrows(StaleRoutingSnapshot::class.java) {
            RouteCommitBinding.requireCurrent(active, old, oldBinding, allowStaged = false)
        }
        RouteCommitBinding.requireCurrent(active, AppliedConfiguration("tokenless"), null, allowStaged = false)
    }

    @Test fun malformedBindingAndUnknownSchemaFailClosed() {
        val json=JSONObject(RouteCommitBinding.capture(enabled,"fr","cfg").encode())
        assertThrows(IllegalArgumentException::class.java){RouteCommitBinding.decode(JSONObject(json.toString()).put("schema",2).toString())}
        assertThrows(IllegalArgumentException::class.java){RouteCommitBinding.decode(JSONObject(json.toString()).put("settings","bad").toString())}
        assertThrows(IllegalArgumentException::class.java){RouteCommitBinding.decode(" ".repeat(1025))}
        assertNull(RouteCommitBinding.decode(null))
    }
}
