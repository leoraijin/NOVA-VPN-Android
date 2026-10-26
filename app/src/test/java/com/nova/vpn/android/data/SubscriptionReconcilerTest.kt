package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test

class SubscriptionReconcilerTest {
    private fun node(id: String, key: String = "key", port: Int = 443) = ImportedProfile(id = id,
        name = "Server", protocol = "vless", server = "example.test", port = port,
        outboundJson = """{"type":"vless","server":"example.test","server_port":$port,"uuid":"$key"}""")
    private fun catalog(profiles: List<ImportedProfile>, vararg membership: Pair<String, String>) =
        SubscriptionCatalog(profiles, membership.map { SubscriptionMembership(it.first, it.second) }.toSet(), emptySet())

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
