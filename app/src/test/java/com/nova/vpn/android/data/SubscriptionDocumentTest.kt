package com.nova.vpn.android.data

import com.nova.vpn.android.subscriptionGroups
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionDocumentTest {
    private val node = ImportedProfile(id = "node", name = "Node", protocol = "vless", server = "test.example", port = 443,
        outboundJson = """{"type":"vless","uuid":"test-key"}""", subscriptionUrl = "https://provider.test/TOKEN")

    @Test fun legacyArrayAndVersionOneMigrateWithoutDroppingNodes() {
        val profiles = JSONArray().put(node.toJson())
        for (text in listOf(profiles.toString(), JSONObject().put("schema", 1).put("profiles", profiles).toString())) {
            val state = ProfileDocument.decodeSnapshot(text)
            assertEquals(listOf(node), state.profiles)
            assertEquals(node.subscriptionUrl, state.subscriptions.single().url)
            assertEquals(setOf(SubscriptionMembership("node", state.subscriptions.single().id)), state.memberships)
            assertFalse(state.subscriptions.single().sendDeviceId)
            assertEquals(state, ProfileDocument.decodeSnapshot(ProfileDocument.encodeSnapshot(state)))
        }
    }

    @Test fun favoriteWritesRetainAllSubscriptionMetadataAndManyToManyLinks() {
        val first = Subscription(id = "a", name = "A", url = node.subscriptionUrl!!, etag = "secret-tag", intervalHours = 6,
            sendDeviceId = true, lastSuccess = 1234, usedBytes = 100, totalBytes = 1000)
        val second = Subscription(id = "b", name = "B", url = "https://provider.test/OTHER_TOKEN")
        val state = ProfileSnapshot(listOf(node), listOf(first, second), setOf(SubscriptionMembership("node", "a"), SubscriptionMembership("node", "b")))
        val next = state.withProfiles(listOf(node.copy(userSettings = ProfileUserSettings(favorite = true))))
        assertEquals(state.subscriptions, next.subscriptions)
        assertEquals(state.memberships, next.memberships)
        assertEquals(next, ProfileDocument.decodeSnapshot(ProfileDocument.encodeSnapshot(next)))
        val groups = subscriptionGroups(next, next.profiles)
        assertEquals(setOf("A", "B"), groups.keys)
        assertFalse(groups.keys.any { "TOKEN" in it || "secret-tag" in it })
    }

    @Test fun danglingMembershipAndDuplicateSourceAreRejected() {
        val source = Subscription(id = "a", name = "A", url = node.subscriptionUrl!!)
        assertThrows(IllegalArgumentException::class.java) {
            ProfileDocument.encodeSnapshot(ProfileSnapshot(listOf(node), listOf(source), setOf(SubscriptionMembership("missing", "a"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProfileDocument.encodeSnapshot(ProfileSnapshot(listOf(node), listOf(source, source.copy(id = "b"))))
        }
    }

    @Test fun manuallyImportingASubscriptionNodeAddsIndependentOwnership() {
        val state = ProfileSnapshot.migrate(listOf(node))
        val next = state.importManual(listOf(node.copy(id = "imported", subscriptionUrl = null)))
        assertEquals(listOf(node), next.profiles)
        assertTrue(node.id in next.manuallyOwned)
        assertEquals(state.memberships, next.memberships)
    }

    @Test fun usageMissingAndOverflowDoNotBecomeZero() {
        assertEquals(SubscriptionUsage(null, null, null), SubscriptionUsage.parse(null))
        assertEquals(SubscriptionUsage(30, 100, 1700000000), SubscriptionUsage.parse("upload=10; download=20; total=100; expire=1700000000"))
        assertNull(SubscriptionUsage.parse("upload=9223372036854775807; download=10").used)
        assertNull(SubscriptionUsage.parse("upload=10; download=-1").used)
        assertNull(SubscriptionUsage.parse("expire=0").expires)
    }
}
