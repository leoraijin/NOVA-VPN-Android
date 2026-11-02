package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XrayHysteria2Test {
    private fun config(): JSONObject = JSONObject("""{"remarks":"Fixture HY2","outbounds":[{"protocol":"hysteria","settings":{"version":2,"address":"fixture.example","port":443},"streamSettings":{"network":"hysteria","security":"tls","tlsSettings":{"serverName":"tls.example","alpn":["h3"]},"hysteriaSettings":{"version":2,"auth":"synthetic:user:password"}}},{"protocol":"freedom","settings":{}}]}""")
    private fun stream(root: JSONObject) = root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")

    @Test fun importsXrayHysteriaWithExactAuthenticationAndTls() {
        val parsed = ProfileLinkParser.parseReport(config().toString())
        assertTrue(parsed.warnings.isEmpty())
        val profile = parsed.profiles.single()
        assertEquals("hysteria2", profile.protocol)
        val out = JSONObject(profile.outboundJson)
        assertEquals("synthetic:user:password", out.getString("password"))
        assertEquals("tls.example", out.getJSONObject("tls").getString("server_name"))
        assertEquals("h3", out.getJSONObject("tls").getJSONArray("alpn").getString(0))
        assertFalse(out.getJSONObject("tls").optBoolean("insecure"))
    }

    @Test fun exportedDefaultQuicDebugFlagDoesNotDropABbrProfile() {
        val root = config()
        stream(root).put("finalmask", JSONObject("""{"quicParams":{"congestion":"bbr","debug":false}}"""))
        assertEquals("hysteria2", ProfileLinkParser.parseText(root.toString()).single().protocol)
    }

    @Test fun preservesBrutalBandwidthAndPortHopping() {
        val root = config()
        stream(root).put("finalmask", JSONObject("""{"quicParams":{"congestion":"brutal","brutalUp":"100mbps","brutalDown":"250mbps","udpHop":{"ports":"10000-20000,30000","interval":"30"}}}"""))
        val out = JSONObject(ProfileLinkParser.parseText(root.toString()).single().outboundJson)
        assertEquals(100, out.getInt("up_mbps")); assertEquals(250, out.getInt("down_mbps"))
        assertEquals("10000:20000", out.getJSONArray("server_ports").getString(0))
        assertEquals("30s", out.getString("hop_interval"))
    }

    @Test fun malformedOrUnsupportedHysteriaIsNotSilentlyDowngraded() {
        for (kind in listOf("version", "auth", "tls", "mask", "maskType", "quicType", "hopType")) {
            val root = config(); val stream = stream(root)
            when (kind) {
                "version" -> stream.getJSONObject("hysteriaSettings").put("version", 1)
                "auth" -> stream.getJSONObject("hysteriaSettings").remove("auth")
                "tls" -> stream.put("security", "none")
                "maskType" -> stream.put("finalmask", "invalid")
                "quicType" -> stream.put("finalmask", JSONObject().put("quicParams", "invalid"))
                "hopType" -> stream.put("finalmask", JSONObject().put("quicParams", JSONObject().put("udpHop", "invalid")))
                else -> stream.put("finalmask", JSONObject().put("udp", JSONArray().put(JSONObject().put("type", "custom"))))
            }
            assertThrows(IllegalArgumentException::class.java) { ProfileLinkParser.parseText(root.toString()) }
        }
    }

    @Test fun allJsonEntriesAreImportedAndExactDuplicateEndpointsAreStillDeduplicated() {
        val first = config(); val second = config()
        second.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings").put("port", 8443)
        val parsed = ProfileLinkParser.parseReport(JSONArray().put(first).put(second).put(first).toString())
        assertEquals(2, parsed.profiles.size)
        assertTrue(parsed.warnings.isEmpty())
    }
}