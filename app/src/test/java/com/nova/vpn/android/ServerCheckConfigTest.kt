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

    @Test fun recoveryChecksOnlyThePinnedExitWithItsOwnResolver() {
        val active = """{
            "inbounds":[{"type":"tun"},{"type":"socks","tag":"old-health"}],
            "outbounds":[{"type":"direct","tag":"direct"},{"type":"vless","tag":"one"},{"type":"trojan","tag":"two"}],
            "route":{"final":"one","default_domain_resolver":"bootstrap","rules":[{"action":"reject"}]},
            "dns":{"servers":[{"type":"local","tag":"bootstrap"},{"type":"fakeip","tag":"fake"},
                {"type":"https","tag":"dns-one","detour":"one"},{"type":"https","tag":"dns-two","detour":"two"}],
                "rules":[{"server":"fake"}],"final":"dns-one"},
            "experimental":{"cache_file":{"enabled":true}}
        }"""
        val recovery = ServerCheckConfig.forRecovery(active, "two", "dns-two")!!
        val json = JSONObject(recovery.config)
        assertEquals(0, json.getJSONArray("inbounds").length())
        assertEquals(2, json.getJSONArray("outbounds").length())
        assertEquals("two", json.getJSONObject("route").getString("final"))
        assertEquals("bootstrap", json.getJSONObject("route").getString("default_domain_resolver"))
        val dns = json.getJSONObject("dns")
        assertEquals("dns-two", dns.getString("final"))
        assertEquals(2, dns.getJSONArray("servers").length())
        assertFalse(dns.has("rules"))
        assertFalse(json.has("experimental"))
        val probe = AndroidTrafficProbe.prepare(recovery.config, recovery.resolver)!!
        val rules = JSONObject(probe.config).getJSONObject("route").getJSONArray("rules")
        assertEquals("dns-two", rules.getJSONObject(0).getString("server"))
        assertEquals("two", rules.getJSONObject(1).getString("outbound"))
        assertNull(ServerCheckConfig.forRecovery(active, "two", "missing"))
        assertNull(ServerCheckConfig.forRecovery(active, "direct"))
    }

    @Test fun recoveryPreservesAnAdaptedNativeBootstrap() {
        val active = ServerCheckConfig.build(profile(), VpnEngine.REALITY_CORE)!!
        val recovery = ServerCheckConfig.forRecovery(active, "proxy")!!
        val dns = JSONObject(recovery.config).getJSONObject("dns")
        assertEquals("nova-bootstrap-dns", dns.getString("bootstrap"))
        val servers = dns.getJSONArray("servers")
        assertTrue((0 until servers.length()).map { servers.getJSONObject(it) }.any {
            it.optString("tag") == "nova-bootstrap-dns" && it.optString("detour") == "direct"
        })
    }

    @Test fun cancelledProbeCannotBecomeSuccessfulOrRemainWaiting() {
        val probe = AndroidTrafficProbe.prepare(ServerCheckConfig.build(profile())!!)!!
        probe.cancel()
        val started = System.nanoTime()
        assertFalse(probe.check(500))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1000)
    }
}
