package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XrayStreamTlsTest {
    private fun stream(settings: JSONObject, reality: Boolean = false) = JSONObject().put("security", if (reality) "reality" else "tls")
        .put(if (reality) "realitySettings" else "tlsSettings", settings)

    @Test fun currentRealityPasswordTakesPrecedenceAndLegacyKeyStillWorks() {
        val settings = JSONObject().put("password", "current-fixture").put("publicKey", "legacy-fixture").put("shortId", "abcd")
        val mapped = XrayStreamTls.fromStream(stream(settings, true), "fixture.invalid")!!
        assertEquals("current-fixture", mapped.getJSONObject("reality").getString("public_key"))
        assertEquals("chrome", mapped.getJSONObject("utls").getString("fingerprint"))
        settings.remove("password")
        assertEquals("legacy-fixture", XrayStreamTls.fromStream(stream(settings, true), "fixture.invalid")!!.getJSONObject("reality").getString("public_key"))
        settings.put("password", "   ")
        assertThrows(IllegalArgumentException::class.java) { XrayStreamTls.fromStream(stream(settings, true), "fixture.invalid") }
        settings.put("password", "current-fixture").put("publicKey", 42)
        assertThrows(IllegalArgumentException::class.java) { XrayStreamTls.fromStream(stream(settings, true), "fixture.invalid") }
    }

    @Test fun tlsVersionConstraintsAndAlpnArePreservedWithoutCoercion() {
        val settings = JSONObject().put("minVersion", "1.2").put("maxVersion", "1.3").put("alpn", JSONArray().put("h2"))
        val mapped = XrayStreamTls.fromStream(stream(settings), "fixture.invalid")!!
        assertEquals("1.2", mapped.getString("min_version")); assertEquals("1.3", mapped.getString("max_version"))
        assertEquals("h2", mapped.getJSONArray("alpn").getString(0))
        settings.put("alpn", "h2,http/1.1")
        assertEquals("http/1.1", XrayStreamTls.fromStream(stream(settings), "fixture.invalid")!!.getJSONArray("alpn").getString(1))
        for (invalid in listOf(JSONObject().put("minVersion", "1.3").put("maxVersion", "1.2"),
            JSONObject().put("alpn", JSONArray().put(42)), JSONObject().put("allowInsecure", "true"), JSONObject().put("serverName", 42))) {
            assertThrows(IllegalArgumentException::class.java) { XrayStreamTls.fromStream(stream(invalid), "fixture.invalid") }
        }
    }

    @Test fun significantUnimplementedProtectionIsNeverSilentlyDroppedOrEchoed() {
        for (field in listOf("pinnedPeerCertSha256", "verifyPeerCertByName", "echConfigList", "mldsa65Verify", "unknown-future-field")) {
            val settings = JSONObject().put("publicKey", "fixture-key").put(field, "PRIVATE_SECURITY_MARKER")
            val error = assertThrows(IllegalArgumentException::class.java) { XrayStreamTls.fromStream(stream(settings, true), "fixture.invalid") }
            assertFalse(error.message.orEmpty().contains("PRIVATE_SECURITY_MARKER"))
            assertFalse(error.message.orEmpty().contains("fixture.invalid"))
        }
        assertNotNull(XrayStreamTls.fromStream(stream(JSONObject().put("certificates", JSONArray()).put("disableSystemRoot", false)), "fixture.invalid"))
        for (invalid in listOf(JSONObject().put("pinnedPeerCertSha256", false), JSONObject().put("disableSystemRoot", ""), JSONObject().put("certificates", ""))) {
            assertThrows(IllegalArgumentException::class.java) { XrayStreamTls.fromStream(stream(invalid), "fixture.invalid") }
        }
    }

    @Test fun unsupportedRealityVerificationCannotDisappearFromAMixedSubscription() {
        val base = "vless://11111111-1111-4111-8111-111111111111@fixture.invalid:443?security=reality&pbk=fixture"
        val report = ProfileLinkParser.parseReport("$base&pqv=PRIVATE_PQV#Unsupported\n$base#Supported")
        assertEquals(1, report.profiles.size)
        assertEquals("Supported", report.profiles.single().name)
        assertEquals(1, report.warnings.size)
        assertFalse(report.warnings.single().contains("PRIVATE_PQV"))
    }

    @Test fun fullXraySubscriptionKeepsBothRealityKeySpellingsAndExplainsOnlyBadEntry() {
        fun config(name: String, settings: JSONObject) = JSONObject().put("remarks", name).put("outbounds", JSONArray().put(
            JSONObject().put("protocol", "vless").put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", "fixture.invalid").put("port", 443).put("users", JSONArray().put(
                    JSONObject().put("id", "11111111-1111-4111-8111-111111111111"))))))
                .put("streamSettings", stream(settings, true).put("network", "xhttp"))))
        val source = JSONArray().put(config("Current", JSONObject().put("password", "current-fixture")))
            .put(config("Legacy", JSONObject().put("publicKey", "legacy-fixture")))
            .put(config("Unsupported", JSONObject().put("password", "private-fixture").put("mldsa65Verify", "PRIVATE_MARKER")))
        val report = ProfileLinkParser.parseReport(source.toString())
        assertEquals(setOf("Current", "Legacy"), report.profiles.map { it.name }.toSet())
        assertEquals(setOf("current-fixture", "legacy-fixture"), report.profiles.map {
            JSONObject(it.outboundJson).getJSONObject("tls").getJSONObject("reality").getString("public_key")
        }.toSet())
        assertEquals(1, report.warnings.size)
        assertFalse(report.warnings.single().contains("PRIVATE_MARKER"))
        assertFalse(report.warnings.single().contains("private-fixture"))
    }

    @Test fun downloadUsesTheSameRealityAliasAndValidationAsUpload() {
        val down = stream(JSONObject().put("password", "download-fixture").put("shortId", "abcd"), true)
            .put("address", "down.invalid").put("port", 443).put("network", "xhttp")
        val transport = XhttpTransport.fromSettings(JSONObject().put("downloadSettings", down), true)
        assertEquals("download-fixture", transport.getJSONObject("download_settings").getJSONObject("tls").getJSONObject("reality").getString("public_key"))
        down.getJSONObject("realitySettings").put("mldsa65Verify", "PRIVATE_SECURITY_MARKER")
        assertThrows(IllegalArgumentException::class.java) { XhttpTransport.fromSettings(JSONObject().put("downloadSettings", down), true) }
    }
}
