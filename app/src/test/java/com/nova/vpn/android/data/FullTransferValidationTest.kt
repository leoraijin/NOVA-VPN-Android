package com.nova.vpn.android.data

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FullTransferValidationTest {
    private val profile = ImportedProfile(id = "synthetic", name = "Fixture", protocol = "vless", server = "example.test", port = 443,
        outboundJson = "{\"type\":\"vless\",\"server\":\"example.test\",\"server_port\":443,\"uuid\":\"11111111-1111-4111-8111-111111111111\"}")
    private fun snapshot() = JSONObject().put("app_preferences", JSONObject()).put("routing", JSONObject()).put("profile_selection", JSONObject())
    private fun document(native: JSONObject = snapshot()): JSONObject {
        native.put("profileDocument", ProfileDocument.encode(listOf(profile)))
        return JSONObject().put("format", "nova-full-v1").put("identity", IdentityState().toJson())
            .put("profiles", JSONArray().put(JSONObject().put("id", profile.id).put("name", profile.name).put("protocol", profile.protocol)
                .put("server", profile.server).put("port", profile.port).put("outboundJson", profile.outboundJson).put("fullConfigJson", JSONObject.NULL)))
            .put("platforms", JSONObject().put("android", native.toString())).put("dark", false).put("ipv6", false)
            .put("selectedId", profile.id).put("directDomains", JSONArray()).put("proxyDomains", JSONArray())
    }

    @Test fun transferDropsPendingWithoutMutatingLiveSnapshot() {
        val original = RoutingPresets.migrate(AppRoutingSettings()).stage("my-routing", "pending", "process", AppRoutingSettings(ipv6Enabled = true))
        val source = snapshot()
        source.getJSONObject("routing").put("routing_presets_v1", JSONObject().put("type", "string").put("value", original.toJson().toString()))
        val transferred = FullTransfer.transferSnapshot(source)
        val result = RoutingPresets.fromJson(JSONObject(transferred.getJSONObject("routing").getJSONObject("routing_presets_v1").getString("value")))
        assertNull(result.pending)
        assertFalse(result.active.settings.ipv6Enabled)
        assertNotNull(RoutingPresets.fromJson(JSONObject(source.getJSONObject("routing").getJSONObject("routing_presets_v1").getString("value"))).pending)
    }

    @Test fun unknownRoutingSchemaFailsDuringPrevalidation() {
        val source = snapshot()
        source.getJSONObject("routing").put("routing_presets_v1", JSONObject().put("type", "string")
            .put("value", RoutingPresets.migrate(AppRoutingSettings()).toJson().put("schema", 99).toString()))
        assertThrows(IllegalArgumentException::class.java) { FullTransfer.validateDocument(document(source)) }
    }

    @Test fun embeddedProfileDocumentIsValidatedBeforeRestore() {
        val data = document()
        val native = JSONObject(data.getJSONObject("platforms").getString("android")).put("profileDocument", "{broken")
        data.getJSONObject("platforms").put("android", native.toString())
        assertThrows(Exception::class.java) { FullTransfer.validateDocument(data) }
    }

    @Test fun sameIdWithDifferentCredentialsCannotSilentlyRestoreOldNativeKey() {
        val data = document()
        data.getJSONArray("profiles").getJSONObject(0).put("outboundJson", JSONObject(profile.outboundJson).put("uuid", "22222222-2222-4222-8222-222222222222").toString())
        assertThrows(IllegalArgumentException::class.java) { FullTransfer.validateDocument(data) }
    }

    @Test fun jsonWhitespaceAndKeyOrderDoNotCreateFalseConflicts() {
        val data = document()
        data.getJSONArray("profiles").getJSONObject(0).put("outboundJson", "{ \"uuid\":\"11111111-1111-4111-8111-111111111111\",\"server_port\":443,\"server\":\"example.test\",\"type\":\"vless\" }")
        FullTransfer.validateDocument(data)
    }

    @Test fun portableRetirementRemovesBothKindsOfOwnership() {
        val data = document()
        val profiles = data.getJSONArray("profiles")
        profiles.getJSONObject(0).put("retiredFromSubscription", true)
            .put("subscriptionUrl", "https://example.test/subscription")
        FullTransfer.validateDocument(data)
        val portable = FullTransfer.restoreProfiles(profiles, null)
        assertTrue(portable.profiles.single().retiredFromSubscription)
        assertTrue(portable.memberships.isEmpty())
        assertTrue(portable.manuallyOwned.isEmpty())

        val native = JSONObject(data.getJSONObject("platforms").getString("android"))
        val restored = FullTransfer.restoreProfiles(profiles, native)
        assertTrue(restored.profiles.single().retiredFromSubscription)
        assertTrue(restored.manuallyOwned.isEmpty())
    }

    @Test fun oldNativeRetirementCannotBeReopenedByPortableMetadata() {
        val data = document()
        val profiles = data.getJSONArray("profiles")
        val native = JSONObject(data.getJSONObject("platforms").getString("android"))
        val retired = ProfileSnapshot(profiles = listOf(profile)).withAvailability()
        native.put("profileDocument", ProfileDocument.encodeSnapshot(retired))
        assertTrue(FullTransfer.restoreProfiles(profiles, native).profiles.single().retiredFromSubscription)
        profiles.getJSONObject(0).put("retiredFromSubscription", false)
        assertTrue(FullTransfer.restoreProfiles(profiles, native).profiles.single().retiredFromSubscription)
        assertFalse(FullTransfer.restoreProfiles(profiles, null).profiles.single().retiredFromSubscription)
    }

    @Test fun retirementMetadataMustBeBoolean() {
        val data = document()
        data.getJSONArray("profiles").getJSONObject(0).put("retiredFromSubscription", "false")
        assertThrows(IllegalArgumentException::class.java) { FullTransfer.validateDocument(data) }
    }

    @Test fun encryptedWindowsFixtureKeepsRetiredServerUnavailable() {
        val fixture = System.getenv("NOVA_WINDOWS_RETIRED_TRANSFER") ?: return
        val data = FullTransfer.read(File(fixture).readText(), "synthetic-retirement-transfer")
        val restored = FullTransfer.restoreProfiles(data.getJSONArray("profiles"), null)
        assertEquals(setOf("fr", "us"), restored.profiles.map { it.id }.toSet())
        assertFalse(restored.profiles.single { it.id == "fr" }.retiredFromSubscription)
        assertTrue(restored.profiles.single { it.id == "us" }.retiredFromSubscription)
        println("Windows retirement fixture verified on Android")
    }

    @Test fun oldWindowsSnapshotSuppliesRetirementForUnmarkedProfile() {
        val data = document()
        val profiles = data.getJSONArray("profiles")
        val windows = JSONObject().put("Profiles", JSONArray().put(JSONObject().put("Id", profile.id)
            .put("RetiredFromSubscription", true))).toString()
        assertTrue(FullTransfer.restoreProfiles(profiles, null, windows).profiles.single().retiredFromSubscription)
        profiles.getJSONObject(0).put("retiredFromSubscription", false)
        assertFalse(FullTransfer.restoreProfiles(profiles, null, windows).profiles.single().retiredFromSubscription)
    }

    @Test fun legacyEncryptedTransferFixturesPreserveRetirement() {
        val fixture = System.getenv("NOVA_WINDOWS_LEGACY_TRANSFER") ?: return
        val password = "synthetic-retirement-transfer"
        val data = FullTransfer.read(File(fixture).readText(), password)
        val portable = data.getJSONArray("profiles")
        assertTrue((0 until portable.length()).all { portable.getJSONObject(it).isNull("retiredFromSubscription") })
        val restored = FullTransfer.restoreProfiles(portable, null, data.getJSONObject("platforms").getString("windows"))
        assertFalse(restored.profiles.single { it.id == "fr" }.retiredFromSubscription)
        assertTrue(restored.profiles.single { it.id == "us" }.retiredFromSubscription)
        println("Legacy Windows retirement fixture verified on Android")

        val output = System.getenv("NOVA_ANDROID_LEGACY_TRANSFER_OUTPUT") ?: return
        val outgoing = document()
        val rows = outgoing.getJSONArray("profiles")
        rows.put(JSONObject(rows.getJSONObject(0).toString()).put("id", "owned"))
        val snapshot = ProfileSnapshot(profiles = listOf(profile, profile.copy(id = "owned")),
            manuallyOwned = setOf("owned")).withAvailability()
        val native = JSONObject(outgoing.getJSONObject("platforms").getString("android"))
            .put("profileDocument", ProfileDocument.encodeSnapshot(snapshot))
        outgoing.getJSONObject("platforms").put("android", native.toString())
        FullTransfer.validateDocument(outgoing)
        val destination = File(output)
        check(destination.createNewFile())
        destination.writeText(IdentityTransfer.seal(outgoing.toString(), password))
    }

    @Test fun embeddedAndPortableProfileSetsMustMatch() {
        val data = document()
        data.getJSONArray("profiles").getJSONObject(0).put("id", "different")
        assertThrows(IllegalArgumentException::class.java) { FullTransfer.validateDocument(data) }
    }
}
