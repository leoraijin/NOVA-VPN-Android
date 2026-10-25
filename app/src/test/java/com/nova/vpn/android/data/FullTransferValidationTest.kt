package com.nova.vpn.android.data

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

    @Test fun embeddedAndPortableProfileSetsMustMatch() {
        val data = document()
        data.getJSONArray("profiles").getJSONObject(0).put("id", "different")
        assertThrows(IllegalArgumentException::class.java) { FullTransfer.validateDocument(data) }
    }
}
