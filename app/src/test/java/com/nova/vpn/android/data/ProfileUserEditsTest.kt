package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test

class ProfileUserEditsTest {
    private fun node(id: String, order: Long = 0) = ImportedProfile(id, "Provider $id", "vless", "fixture.example", 443,
        """{"uuid":"$id"}""", userSettings = ProfileUserSettings(favorite = true, order = order))

    @Test fun personalNameDoesNotChangeProviderIdentityOrCredentials() {
        val original = node("a")
        val renamed = ProfileUserEdits.rename(listOf(original), "a", "  My server  ").single()
        assertEquals("My server", renamed.displayName)
        assertEquals(original.name, renamed.name)
        assertEquals(original.configurationKey(), renamed.configurationKey())
        assertEquals(original.id, renamed.id)
        assertTrue(renamed.userSettings.favorite)
        assertEquals(original, ProfileUserEdits.rename(listOf(renamed), "a", " ").single())
    }

    @Test fun reorderHandlesTiesExtremesAndRetainsStorageOrder() {
        val nodes = listOf(node("a", Long.MAX_VALUE), node("b", Long.MIN_VALUE), node("c", Long.MAX_VALUE))
        val moved = ProfileUserEdits.move(nodes, "c", -1)
        assertEquals(nodes.map { it.id }, moved.map { it.id })
        assertEquals(listOf("b", "c", "a"), moved.sortedBy { it.userSettings.order }.map { it.id })
        moved.zip(nodes).forEach { (after, before) ->
            assertEquals(before.configurationKey(), after.configurationKey())
            assertEquals(before.userSettings.favorite, after.userSettings.favorite)
        }
        assertEquals(moved, ProfileUserEdits.move(moved, "b", -1))
        assertEquals(moved, ProfileUserEdits.move(moved, "deleted", 1))
    }

    @Test fun invalidNamesCannotEnterStorage() {
        for (name in listOf("x".repeat(97), "a\nb", "a\u0000b")) {
            try { ProfileUserEdits.rename(listOf(node("a")), "a", name); fail("Invalid name accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
