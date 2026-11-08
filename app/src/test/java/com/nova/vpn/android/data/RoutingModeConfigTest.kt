package com.nova.vpn.android.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutingModeConfigTest {
    private fun profile(id:String,port:Int)=ImportedProfile(id=id,name=id,protocol="socks",server="127.0.0.1",port=port,
        outboundJson=JSONObject().put("type","socks").put("server","127.0.0.1").put("server_port",port).put("version","5").toString())
    @Test fun restoringProducesOriginalNativeJsonAndResumingProducesSavedMultiJson() {
        val fr=profile("fr",18001);val us=profile("us",18002);val own="com.nova.vpn.android"
        val old=AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED,setOf("org.example.chat"),listOf(SiteRoutingRule("example.org",SiteRoutingTarget.DIRECT)),true)
        val rules=listOf(MultiServerRule(packageName="org.example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")),
            MultiServerRule(domain="example.org",includeSubdomains=true,target=ServerRouteTarget(ServerRouteKind.DIRECT)))
        val enabled=RoutingModeTransitions.enable(old,"us","fr",rules,false)
        val disabled=RoutingModeTransitions.disable(enabled)
        val original=SingBoxConfigFactory.build(us,old,own)
        val restored=SingBoxConfigFactory.build(us,disabled.settings,own)
        assertEquals(original,restored)
        val multi=SingBoxConfigFactory.build(fr,enabled,own,listOf(fr,us),"/private/synthetic-mode-dns.db")
        val saved=disabled.settings.history!!.savedAssignments
        val resumed=RoutingModeTransitions.enable(disabled.settings,"us",saved.defaultProfileId,saved.rules,false)
        val afterResume=SingBoxConfigFactory.build(fr,resumed,own,listOf(fr,us),"/private/synthetic-mode-dns.db")
        assertEquals(multi,afterResume);assertFalse(restored.contains("history"));assertEquals("us",disabled.selection!!.profileId)
        System.getenv("NOVA_ROUTING_MODE_FIXTURE_DIRECTORY")?.let {value->
            val directory=File(value);check(!directory.exists()&&directory.mkdirs())
            mapOf("original" to original,"enabled" to multi,"restored" to restored,"resumed" to afterResume).forEach {(name,json)->File(directory,"synthetic-mode-$name.json").writeText(json)}
        }
    }

    @Test fun explicitLocalSiteRoutesRetainHostnames() {
        val fr = profile("fr", 18001)
        val us = profile("us", 18002)
        val rules = listOf(
            MultiServerRule(domain = "pinned.local", includeSubdomains = true, target = ServerRouteTarget(ServerRouteKind.PROFILE, "us")),
            MultiServerRule(domain = "direct.pinned.local", includeSubdomains = false, target = ServerRouteTarget(ServerRouteKind.DIRECT))
        )
        val routing = AppRoutingSettings(multiServer = MultiServerSettings(revision = 1, defaultProfileId = "fr", rules = rules))
        val config = JSONObject(SingBoxConfigFactory.build(fr, routing, "com.nova.vpn.android", listOf(fr, us), "/private/test-dns.db"))
        val dnsRules = config.getJSONObject("dns").getJSONArray("rules")
        val local = dnsRules.getJSONObject(0)
        assertEquals("and", local.getString("mode"))
        assertEquals("nova-bootstrap-dns", local.getString("server"))
        val matches = local.getJSONArray("rules")
        assertEquals(".local", matches.getJSONObject(0).getJSONArray("domain_suffix").getString(0))
        val excluded = matches.getJSONObject(1)
        assertTrue(excluded.getBoolean("invert"))
        val domains = excluded.getJSONArray("domain")
        assertEquals(setOf("pinned.local", "direct.pinned.local"), (0 until domains.length()).map { domains.getString(it) }.toSet())
        assertEquals(".pinned.local", excluded.getJSONArray("domain_suffix").getString(0))
        val routes = config.getJSONObject("route").getJSONArray("rules")
        val directResolve = (0 until routes.length()).map { routes.getJSONObject(it) }.first {
            it.optString("action") == "resolve" && it.optJSONArray("domain")?.optString(0) == "direct.pinned.local"
        }
        assertEquals("nova-bootstrap-dns", directResolve.getString("server"))
    }

    @Test fun forwardedDnsUsesSiteAndPackageAssignmentsBeforeDefault() {
        val fr = profile("fr", 18001)
        val us = profile("us", 18002)
        val rules = listOf(
            MultiServerRule(packageName = "org.example.chat", target = ServerRouteTarget(ServerRouteKind.PROFILE, "us")),
            MultiServerRule(packageName = "org.example.blocked", target = ServerRouteTarget(ServerRouteKind.BLOCK)),
            MultiServerRule(packageName = "org.example.missing", target = ServerRouteTarget(ServerRouteKind.PROFILE, "missing")),
            MultiServerRule(domain = "site.example.test", target = ServerRouteTarget(ServerRouteKind.PROFILE, "fr"))
        )
        val routing = AppRoutingSettings(multiServer = MultiServerSettings(revision = 1, defaultProfileId = "fr", rules = rules))
        val config = JSONObject(SingBoxConfigFactory.build(fr, routing, "com.nova.vpn.android", listOf(fr, us), "/private/test-dns.db"))
        val dns = config.getJSONObject("dns")
        val entries = dns.getJSONArray("rules")
        val ordered = (0 until entries.length()).map { entries.getJSONObject(it) }
        val blocked = ordered.indexOfFirst { it.optJSONArray("package_name")?.optString(0) == "org.example.blocked" }
        val fakeIp = ordered.indexOfFirst { it.optString("server") == "nova-fakeip" }
        val site = ordered.indexOfFirst { it.optJSONArray("domain")?.optString(0) == "site.example.test" }
        val app = ordered.indexOfFirst { it.optJSONArray("package_name")?.optString(0) == "org.example.chat" }
        val missing = ordered.indexOfFirst { it.optJSONArray("package_name")?.optString(0) == "org.example.missing" }
        val unknown = ordered.indexOfFirst { it.has("package_name_regex") }
        assertTrue(blocked in 0 until fakeIp && fakeIp < site && site < app && site < missing && app < unknown && missing < unknown)
        assertEquals("reject", ordered[blocked].getString("action"))
        assertEquals("dns-nova-exit-1", ordered[app].getString("server"))
        assertEquals("dns-nova-exit-0", ordered[site].getString("server"))
        assertEquals("reject", ordered[missing].getString("action"))
        assertTrue(ordered[unknown].getBoolean("invert"))
        assertEquals("reject", ordered[unknown].getString("action"))
        assertEquals("dns-nova-exit-0", dns.getString("final"))
        System.getenv("NOVA_FORWARDED_DNS_FIXTURE")?.let { path ->
            val fixture = File(path)
            check(fixture.createNewFile()) { "A fresh forwarded DNS fixture path is required" }
            fixture.writeText(config.toString())
        }
    }

    @Test fun domainBlockPrecedesLocalDnsAndFakeIp() {
        val fr = profile("fr", 18001)
        val rules = listOf(
            MultiServerRule(domain = "blocked.local", includeSubdomains = true, target = ServerRouteTarget(ServerRouteKind.BLOCK)),
            MultiServerRule(domain = "blocked.example.test", target = ServerRouteTarget(ServerRouteKind.BLOCK))
        )
        val routing = AppRoutingSettings(multiServer = MultiServerSettings(revision = 1, defaultProfileId = "fr", rules = rules))
        val config = JSONObject(SingBoxConfigFactory.build(fr, routing, "com.nova.vpn.android", listOf(fr), "/private/test-dns.db"))
        val dnsRules = config.getJSONObject("dns").getJSONArray("rules")
        val ordered = (0 until dnsRules.length()).map { dnsRules.getJSONObject(it) }
        val local = ordered.indexOfFirst { it.optString("server") == "nova-bootstrap-dns" }
        val fakeIp = ordered.indexOfFirst { it.optString("server") == "nova-fakeip" }
        for (domain in listOf("blocked.local", "blocked.example.test")) {
            val index = ordered.indexOfFirst { it.optJSONArray("domain")?.optString(0) == domain }
            assertTrue(index >= 0 && index < local && index < fakeIp)
            assertEquals("reject", ordered[index].getString("action"))
            if (domain == "blocked.local")
                assertEquals(".blocked.local", ordered[index].getJSONArray("domain_suffix").getString(0))
        }
    }
}
