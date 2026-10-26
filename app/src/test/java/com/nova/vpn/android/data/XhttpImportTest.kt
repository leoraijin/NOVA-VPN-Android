package com.nova.vpn.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class XhttpImportTest {
    private fun downlink() = JSONObject("""{"network":"xhttp","address":"download.example","port":8443,"security":"tls","tlsSettings":{"serverName":"down.front","alpn":["h2"]},"xhttpSettings":{"path":"/down","xmux":{"maxConnections":2}}}""")

    @Test fun separateDownloadMapsEndpointTlsAndTransport() {
        val settings = JSONObject().put("mode", "stream-up").put("path", "/up").put("downloadSettings", downlink())
        val out = XhttpTransport.fromSettings(settings, true)
        val down = out.getJSONObject("download_settings")
        assertEquals("/up", out.getString("path"))
        assertEquals("download.example", down.getString("server"))
        assertEquals(8443, down.getInt("server_port"))
        assertEquals("down.front", down.getJSONObject("tls").getString("server_name"))
        assertEquals("h2", down.getJSONObject("tls").getJSONArray("alpn").getString(0))
        assertEquals("/down", down.getJSONObject("xhttp").getString("path"))
        assertEquals(2, down.getJSONObject("xhttp").getJSONObject("xmux").getInt("max_connections"))
        assertFalse(down.getJSONObject("xhttp").has("type"))
    }

    @Test fun queryDownloadOverridesExtraAndDoesNotInheritTls() {
        val down = downlink().put("security", "none")
        val out = XhttpTransport.fromQuery(mapOf("extra" to """{"downloadSettings":{"bad":true}}""", "downloadsettings" to down.toString()), true)
        assertFalse(out.getJSONObject("download_settings").has("tls"))
    }

    @Test fun rejectsUnsafeOrIncompatibleDownloadSettings() {
        val cases = listOf(
            JSONObject().put("mode", "stream-one").put("downloadSettings", downlink()),
            JSONObject().put("downloadSettings", downlink().put("port", 443.5)),
            JSONObject().put("downloadSettings", downlink().put("network", "tcp")),
            JSONObject().put("downloadSettings", downlink().put("sockopt", JSONObject().put("mark", 7))),
            JSONObject().put("downloadSettings", downlink().put("xhttpSettings", JSONObject().put("downloadSettings", downlink())))
        )
        cases.forEach { settings ->
            try { XhttpTransport.fromSettings(settings, true); fail("Invalid download settings accepted") }
            catch (_: IllegalArgumentException) { }
        }
        try { XhttpTransport.fromSettings(JSONObject().put("downloadSettings", downlink()), false); fail("Old core accepted download settings") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun malformedExtraCannotDiscardOtherServersAndNullDownloadSettingsIsAllowed() {
        val source = "$base?type=xhttp&extra=broken#Bad\n$base?type=xhttp&extra=%7B%22downloadSettings%22%3Anull%7D#Good"
        val result = ProfileLinkParser.parseReport(source)
        assertEquals(1, result.profiles.size)
        assertEquals("Good", result.profiles.single().name)
        assertEquals(1, result.warnings.size)
    }
    private val base = "vless://11111111-1111-4111-8111-111111111111@fixture.example:443"
    @Test fun xhttpModesSurviveRawAndBase64SubscriptionImport() {
        val text = listOf("auto", "packet-up", "stream-up", "stream-one").map { mode ->
            "$base?type=xhttp&security=tls&sni=front.example&host=cdn.example&path=%2Ftest&mode=$mode#$mode"
        }.joinToString("\n")
        for (source in listOf(text, java.util.Base64.getEncoder().encodeToString(text.toByteArray()))) {
            val result = ProfileLinkParser.parseReport(source)
            assertEquals(4, result.profiles.size); assertTrue(result.warnings.isEmpty())
            result.profiles.forEach { profile ->
                val out = JSONObject(profile.outboundJson)
                assertEquals("xhttp", out.getJSONObject("transport").getString("type"))
                assertEquals(profile.name, out.getJSONObject("transport").getString("mode"))
                assertEquals("/test", out.getJSONObject("transport").getString("path"))
                assertEquals("cdn.example", out.getJSONObject("transport").getString("host"))
                assertEquals("front.example", out.getJSONObject("tls").getString("server_name"))
            }
        }
    }
    @Test fun mixedUnsupportedLinkProducesVisibleWarningAndKeepsValidServers() {
        val result = ProfileLinkParser.parseReport("$base?type=xhttp#XHTTP\n$base?type=ws#WS\n$base?type=kcp#Unsupported")
        assertEquals(2, result.profiles.size)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().startsWith("Строка 3:"))
        assertFalse(result.warnings.single().contains("11111111"))
    }
    @Test fun xrayJsonArrayDoesNotLoseAllServersWhenOneEntryIsUnsupported() {
        fun config(network: String) = """{"remarks":"$network","outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"fixture.example","port":443,"users":[{"id":"11111111-1111-4111-8111-111111111111"}]}]},"streamSettings":{"network":"$network","xhttpSettings":{"path":"/fixture","mode":"packet-up"}}}]}"""
        val result = ProfileLinkParser.parseReport("[${config("xhttp")},${config("kcp")},${config("ws")}]")
        assertEquals(2, result.profiles.size)
        assertTrue(result.warnings.isNotEmpty())
        assertEquals("xhttp", JSONObject(result.profiles.first().outboundJson).getJSONObject("transport").getString("type"))
    }
    @Test fun splitHttpExtraPreservesPlacementPaddingAndXmux() {
        val extra = URLEncoder.encode("""{"xPaddingBytes":"200-300","sessionIDPlacement":"header","noGRPCHeader":true,"xmux":{"maxConnections":3}}""", "UTF-8")
        val out = JSONObject(ProfileLinkParser.parseText("$base?type=splithttp&extra=$extra&path=%2Fa%3Fb%3Dc#Fixture").single().outboundJson)
        val transport = out.getJSONObject("transport")
        assertEquals("200-300", transport.getString("x_padding_bytes"))
        assertEquals("header", transport.getString("session_placement"))
        assertTrue(transport.getBoolean("no_grpc_header"))
        assertEquals(3, transport.getJSONObject("xmux").getInt("max_connections"))
        assertEquals("/a?b=c", transport.getString("path"))
    }
}
