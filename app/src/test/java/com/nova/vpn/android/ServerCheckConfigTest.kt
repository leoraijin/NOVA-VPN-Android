package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.VpnEngine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerCheckConfigTest {
    @Test fun realityProbeUsesAdaptedDnsAndKeepsOnlyAuthenticatedProxyListener() {
        val config = ServerCheckConfig.build(profile(), VpnEngine.REALITY_CORE)!!
        val json = JSONObject(config)
        assertEquals(0, json.getJSONArray("inbounds").length())
        assertEquals("proxy", json.getJSONObject("route").getString("final"))
        assertEquals("nova-bootstrap-dns", json.getJSONObject("dns").getString("bootstrap"))
        assertFalse(json.getJSONObject("route").has("rules"))
        val check = AndroidTrafficProbe.prepare(config)!!
        val inbound = JSONObject(check.config).getJSONArray("inbounds").getJSONObject(0)
        assertEquals("socks", inbound.getString("type"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(1, inbound.getJSONArray("users").length())
    }

    @Test fun realityDoesNotCertifyUnsupportedProtocolsOrInsecureTlsUsingSingBox() {
        assertNotNull(ServerCheckConfig.build(profile("vmess")))
        assertNull(runCatching { ServerCheckConfig.build(profile("vmess"), VpnEngine.REALITY_CORE) }.getOrNull())
        val insecure = profile().copy(outboundJson = """{"type":"vless","server":"example.invalid","server_port":443,"uuid":"00000000-0000-4000-8000-000000000001","tls":{"enabled":true,"insecure":true}}""")
        assertNull(runCatching { ServerCheckConfig.build(insecure, VpnEngine.REALITY_CORE) }.getOrNull())
    }

    private fun profile(type: String = "vless") = ImportedProfile(name = "Synthetic", protocol = type,
        server = "example.invalid", port = 443, outboundJson = """{"type":"$type","tag":"proxy","server":"example.invalid","server_port":443,"uuid":"00000000-0000-4000-8000-000000000001"}""")

    @Test fun manualCheckUsesOnlyAuthenticatedLoopbackAndDoesNotCreateTunOrTimers() {
        val config = ServerCheckConfig.build(profile())!!
        val json = JSONObject(config)
        assertEquals(0, json.getJSONArray("inbounds").length())
        assertFalse(config.contains("urltest"))
        assertFalse(config.contains("auto_route"))
        assertFalse(json.has("experimental"))
        val checked = AndroidTrafficProbe.prepare(config)!!
        val listener = JSONObject(checked.config).getJSONArray("inbounds").getJSONObject(0)
        assertEquals("127.0.0.1", listener.getString("listen"))
        assertEquals("socks", listener.getString("type"))
        assertEquals(1, listener.getJSONArray("users").length())
    }

    @Test fun complexDocumentsAndUnsupportedAdaptersStayUnverified() {
        assertNull(ServerCheckConfig.build(profile().copy(fullConfigJson = """{"outbounds":[{"type":"urltest","tag":"selector"}]}""")))
        assertNull(ServerCheckConfig.build(profile("direct")))
    }

    @Test fun cancelledProbeCannotBecomeSuccessfulOrRemainWaiting() {
        val probe = AndroidTrafficProbe.prepare(ServerCheckConfig.build(profile())!!)!!
        probe.cancel()
        val started = System.nanoTime()
        assertFalse(probe.check(500))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1000)
    }
}
