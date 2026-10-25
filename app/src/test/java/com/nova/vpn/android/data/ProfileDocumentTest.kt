package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileDocumentTest {
    private val profile = ImportedProfile(id = "stable", name = "Server", protocol = "vless",
        server = "example.test", port = 443, outboundJson = """{"type":"vless","uuid":"test"}""",
        subscriptionUrl = "https://example.test/subscription", latencyMillis = 42)

    @Test fun legacyMigrationRetainsIdentityConfigurationAndProvenance() {
        val legacy = JSONArray().put(profile.toJson()).toString()
        val loaded = ProfileDocument.decode(legacy)
        assertEquals(listOf(profile), loaded)
        val migrated = ProfileDocument.encode(loaded)
        assertEquals(ProfileDocument.SCHEMA, JSONObject(migrated).getInt("schema"))
        assertEquals(loaded, ProfileDocument.decode(migrated))
    }

    @Test fun futureSchemaAndCorruptPayloadFailInsteadOfReturningAnEmptyList() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileDocument.decode("""{"schema":999,"profiles":[]}""")
        }
        assertThrows(Exception::class.java) { ProfileDocument.decode("{broken") }
        assertThrows(Exception::class.java) { ProfileDocument.decode("""{"schema":1}""") }
    }

    @Test fun duplicateIdentityIsRejectedBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) { ProfileDocument.encode(listOf(profile, profile)) }
        val legacy = JSONArray().put(profile.toJson()).put(profile.toJson()).toString()
        assertThrows(IllegalArgumentException::class.java) { ProfileDocument.decode(legacy) }
    }
}
