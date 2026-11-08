package com.nova.vpn.android.data

import com.nova.vpn.android.RouteExitBindings
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MultiServerProfileAdapterTest {
    private fun outbound() = JSONObject().put("type","socks").put("tag","original")
        .put("server","127.0.0.1").put("server_port",18002).put("version","5").put("username","fixture-user").put("password","fixture-secret")
    private fun tuic(uuid: String = "11111111-1111-4111-8111-111111111111") = JSONObject()
        .put("type", "tuic").put("server", "127.0.0.1").put("server_port", 18002).put("uuid", uuid)
        .put("password", "fixture-secret").put("tls", JSONObject().put("enabled", true).put("server_name", "localhost"))

    private fun raw(root:JSONObject=JSONObject().put("outbounds",JSONArray().put(outbound()))) = ImportedProfile(
        id="us",name="US",protocol="sing-box",server="",port=0,outboundJson="{}",fullConfigJson=root.toString())
    private fun fullRoot() = JSONObject().put("outbounds",JSONArray().put(outbound()))
        .put("route",JSONObject().put("final","original").put("rules",JSONArray())).put("inbounds",JSONArray())
    private fun refused(profile:ImportedProfile) { assertNotNull(MultiServerProfileAdapter.problem(profile)) }

    @Test fun singleServerJsonIsExtractedWithoutMutatingSavedIdentityOrCredentials() {
        val p=raw(fullRoot());val before=p.configurationKey();val result=MultiServerProfileAdapter.prepare(p)
        assertNull(result.fullConfigJson);assertEquals(outbound().toString(),result.outboundJson)
        assertEquals(p.id,result.id);assertEquals(before,p.configurationKey());assertNotNull(p.fullConfigJson)
        assertNull(MultiServerProfileAdapter.problem(p))
    }
    @Test fun nonEmptyPolicySectionsAreNeverSilentlyLost() {
        listOf("dns","experimental","endpoints","certificate","ntp").forEach { key -> refused(raw(fullRoot().put(key,JSONObject()))) }
        refused(raw(fullRoot().getJSONObject("route").put("rules",JSONArray().put(JSONObject().put("action","reject"))).let { fullRoot().put("route",it) }))
        refused(raw(fullRoot().put("inbounds",JSONArray().put(JSONObject().put("type","socks")))))
        refused(raw(fullRoot().put("route",JSONObject().put("auto_detect_interface",true))))
    }
    @Test fun selectedExitMustBeUnambiguousAndTaggedCorrectly() {
        refused(raw(fullRoot().put("route",JSONObject().put("final","other"))))
        refused(raw(fullRoot().put("route",JSONObject().put("final",JSONObject.NULL))))
        refused(raw(fullRoot().put("outbounds",JSONArray().put(outbound()).put(outbound().put("tag","second")))))
        refused(raw(fullRoot().put("outbounds",JSONArray())))
    }
    @Test fun dependentResolversAndDetoursAreRefusedBeforeComposition() {
        listOf("detour","domain_resolver").forEach { key ->
            val o=outbound().put(key,"private-dependency")
            refused(raw(JSONObject().put("outbounds",JSONArray().put(o))))
            refused(raw().copy(fullConfigJson=null,outboundJson=o.toString()))
        }
    }
    @Test fun malformedAndRetiredImportsCannotBeAssigned() {
        refused(raw().copy(fullConfigJson="{"));refused(raw().copy(retiredFromSubscription=true))
        refused(raw(fullRoot().put("route",true)));refused(raw(fullRoot().put("inbounds",true)))
        refused(raw(JSONObject().put("outbounds",JSONArray().put(outbound().put("type","urltest")))))
    }
    @Test fun fullJsonAndEquivalentLinkProduceIdenticalCombinedConfigurationAndBindings() {
        val p=raw(fullRoot());val ordinary=MultiServerProfileAdapter.prepare(p)
        val fr=ordinary.copy(id="fr",name="FR",outboundJson=outbound().put("server_port",18001).toString())
        val settings=MultiServerSettings(revision=0,defaultProfileId="fr",rules=listOf(MultiServerRule(packageName="org.example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us"))))
        val routing=AppRoutingSettings(multiServer=settings)
        fun build(list:List<ImportedProfile>)=SingBoxConfigFactory.build(fr,routing,"com.nova.vpn.android",list,"/private/raw-test.db")
        val json=build(listOf(fr,p));assertEquals(build(listOf(fr,ordinary)),json)
        val bindings=RouteExitBindings.capture(settings,listOf(fr,p),json)
        assertEquals(RouteExitBindings.profileHash(p),bindings.entries.getValue("us").profileSha256)
        val outs=JSONObject(json).getJSONArray("outbounds")
        val us=(0 until outs.length()).map(outs::getJSONObject).single {it.getString("tag")==bindings.entries.getValue("us").tag}
        assertEquals("fixture-secret",us.getString("password"));assertEquals(18002,us.getInt("server_port"))
        System.getenv("NOVA_RAW_JSON_FIXTURE_DIRECTORY")?.let { path ->
            val dir=java.io.File(path);check(!dir.exists()&&dir.mkdirs());java.io.File(dir,"combined.json").writeText(json)
        }
    }
    @Test fun tuicUuidFormsMatchPinnedNativeParserWithoutChangingSavedCredentials() {
        val uuid="11111111-1111-4111-8111-111111111111"
        val compact=uuid.replace("-","")
        for(id in listOf(uuid,uuid.uppercase(),compact,"{$uuid}","{$compact}","urn:uuid:$uuid","urn:uuid:$compact")) {
            val p=raw().copy(fullConfigJson=null,outboundJson=tuic(id).toString())
            assertNull(MultiServerProfileAdapter.problem(p))
            assertEquals(id,JSONObject(MultiServerProfileAdapter.prepare(p).outboundJson).getString("uuid"))
        }
        for(id in listOf("not-a-uuid","1-1-1-1-1","URN:UUID:$uuid"," $uuid",uuid+" ","["+uuid+"]","g".repeat(32)))
            refused(raw().copy(fullConfigJson=null,outboundJson=tuic(id).toString()))
        refused(raw().copy(fullConfigJson=null,outboundJson=tuic().put("uuid",JSONObject.NULL).toString()))
    }
    @Test fun vlessAndVmessStringIdsRemainCompatibleInsteadOfBeingTreatedAsMalformedUuid() {
        for(protocol in listOf("vless","vmess")) {
            val p=raw().copy(fullConfigJson=null,outboundJson=outbound().put("type",protocol).put("uuid","arbitrary-fixture-id").toString())
            assertNull(MultiServerProfileAdapter.problem(p))
            assertEquals("arbitrary-fixture-id",JSONObject(MultiServerProfileAdapter.prepare(p).outboundJson).getString("uuid"))
        }
    }
    @Test fun malformedTuicUuidCanBeQuarantinedWithoutDiscardingHealthyDefault() {
        val fr=raw().copy(id="fr",fullConfigJson=null,outboundJson=outbound().toString())
        val broken=fr.copy(id="us",outboundJson=tuic("invalid-fixture-id").toString())
        val settings=MultiServerSettings(0,"fr",listOf(MultiServerRule(packageName="example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us"))))
        val config=SingBoxConfigFactory.build(fr,AppRoutingSettings(multiServer=settings),"com.nova.fixture",listOf(fr,broken),"/tmp/private-dns.db",setOf("us"))
        val binding=RouteExitBindings.capture(settings,listOf(fr,broken),config,setOf("us"))
        assertEquals(setOf("fr"),binding.entries.keys);assertEquals(setOf("us"),binding.quarantined.keys)
        binding.validateCatalog(listOf(fr,broken),setOf("fr","us"))
        assertFalse(config.contains("invalid-fixture-id"))
    }
    @Test fun tuicChecksTlsRelayAndCongestionWithoutRewritingOptions() {
        val valid = listOf(tuic(), tuic().put("udp_relay_mode", "quic").put("congestion_control", "bbr"),
            tuic().put("udp_relay_mode", "native").put("congestion_control", "new_reno"),
            tuic().put("udp_over_stream", true).put("congestion_control", "cubic"),
            tuic().put("udp_relay_mode", JSONObject.NULL).put("udp_over_stream", JSONObject.NULL))
        for (options in valid) {
            val profile = raw().copy(fullConfigJson = null, outboundJson = options.toString())
            assertEquals(options.toString(), MultiServerProfileAdapter.prepare(profile).outboundJson)
        }
        val absentTls = tuic().also { it.remove("tls") }
        val invalid = listOf(absentTls, tuic().put("tls", JSONObject().put("enabled", false)),
            tuic().put("tls", JSONObject().put("enabled", "true")),
            tuic().put("udp_relay_mode", "Native"), tuic().put("udp_over_stream", true).put("udp_relay_mode", "quic"),
            tuic().put("udp_over_stream", "true"), tuic().put("congestion_control", "reno"))
        for (options in invalid) refused(raw().copy(fullConfigJson = null, outboundJson = options.toString()))
    }

    @Test fun badTuicOptionsQuarantineOnlyTheirExitAndRepairRestoresSameBinding() {
        val fr = raw().copy(id = "fr", fullConfigJson = null, outboundJson = outbound().toString())
        val good = raw().copy(fullConfigJson = null, outboundJson = tuic().put("udp_relay_mode", "quic").toString())
        val bad = good.copy(outboundJson = JSONObject(good.outboundJson).put("udp_over_stream", true).toString())
        val settings = MultiServerSettings(0, fr.id, listOf(
            MultiServerRule(packageName = "example.chat", target = ServerRouteTarget(ServerRouteKind.PROFILE, good.id))))
        fun build(exit: ImportedProfile): Pair<String, RouteExitBindings> {
            val profiles = listOf(fr, exit)
            val quarantine = profiles.filter { MultiServerProfileAdapter.problem(it) != null }.map { it.id }.toSet()
            val config = SingBoxConfigFactory.build(fr, AppRoutingSettings(multiServer = settings), "com.nova.fixture",
                profiles, "/private/tuic-fixture.db", quarantine)
            return config to RouteExitBindings.capture(settings, profiles, config, quarantine)
        }
        val (badConfig, badBindings) = build(bad)
        assertEquals(setOf(fr.id), badBindings.entries.keys)
        assertEquals(setOf(good.id), badBindings.quarantined.keys)
        badBindings.validateCatalog(listOf(fr, bad), setOf(fr.id, good.id))
        assertFalse(badConfig.contains("\"type\":\"tuic\""))
        val (goodConfig, goodBindings) = build(good)
        assertEquals(setOf(fr.id, good.id), goodBindings.entries.keys)
        assertTrue(goodBindings.quarantined.isEmpty())
        assertEquals(RouteExitBindings.profileHash(good), goodBindings.entries.getValue(good.id).profileSha256)

        System.getenv("NOVA_TUIC_OPTIONS_FIXTURE_DIR")?.let { path ->
            val directory = java.io.File(path)
            check(directory.mkdir())
            java.io.File(directory, "combined-quarantined.json").writeText(badConfig)
            java.io.File(directory, "combined-valid.json").writeText(goodConfig)
        }
    }

    @Test fun shadowsocksMethodAndKeyValidationIsolatesInvalidAssignedServer() {
        val key16 = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { (it + 1).toByte() })
        val key32 = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
        fun profile(method: String, password: Any) = raw().copy(fullConfigJson = null, outboundJson = JSONObject()
            .put("type", "shadowsocks").put("server", "127.0.0.1").put("server_port", 18003)
            .put("method", method).put("password", password).toString())
        val accepted = listOf(profile("none", JSONObject.NULL), profile("aes-128-gcm", "fixture"),
            profile("aes-256-cfb", "fixture"), profile("rc4-md5", "fixture"),
            profile("2022-blake3-aes-128-gcm", key16), profile("2022-blake3-aes-128-gcm", key16.take(4) + "\r\n" + key16.drop(4)),
            profile("2022-blake3-aes-256-gcm", "$key32:$key32"), profile("2022-blake3-chacha20-poly1305", key32))
        accepted.forEach { assertEquals(it.outboundJson, MultiServerProfileAdapter.prepare(it).outboundJson) }
        val invalid = listOf(profile("plain", ""), profile("", "fixture"), profile("unsupported-fixture", "fixture"),
            profile("aes-128-gcm", ""), profile("aes-128-gcm", 123), profile("aes-256-cfb", ""),
            profile("2022-blake3-aes-128-gcm", ""), profile("2022-blake3-aes-128-gcm", "invalid!"),
            profile("2022-blake3-aes-128-gcm", key32), profile("2022-blake3-aes-128-gcm", key16.trimEnd('=')),
            profile("2022-blake3-aes-128-gcm", "$key16:"), profile("2022-blake3-chacha20-poly1305", "$key32:$key32"))
        invalid.forEach(::refused)
        val fr = raw().copy(id = "fr", fullConfigJson = null, outboundJson = outbound().toString())
        val settings = MultiServerSettings(0, fr.id, listOf(
            MultiServerRule(domain = "chat.example.test", target = ServerRouteTarget(ServerRouteKind.PROFILE, "us"))))
        val broken = invalid.first()
        val repaired = accepted.last()
        fun build(exit: ImportedProfile, quarantine: Set<String>): Pair<String, RouteExitBindings> {
            val profiles = listOf(fr, exit)
            val config = SingBoxConfigFactory.build(fr, AppRoutingSettings(multiServer = settings), "com.nova.fixture",
                profiles, "/private/ss-fixture.db", quarantine)
            return config to RouteExitBindings.capture(settings, profiles, config, quarantine)
        }
        val (blocked, oldBindings) = build(broken, setOf("us"))
        assertEquals(setOf("fr"), oldBindings.entries.keys)
        assertEquals(setOf("us"), oldBindings.quarantined.keys)
        assertThrows(IllegalArgumentException::class.java) { oldBindings.validateCatalog(listOf(fr, repaired), setOf("fr", "us")) }
        val (healthy, newBindings) = build(repaired, emptySet())
        assertEquals(setOf("fr", "us"), newBindings.entries.keys)
        fun siteRule(config: String): JSONObject {
            val rules = JSONObject(config).getJSONObject("route").getJSONArray("rules")
            return (0 until rules.length()).map(rules::getJSONObject).first {
                it.optJSONArray("domain")?.optString(0) == "chat.example.test" && it.optString("action") in setOf("reject", "route")
            }
        }
        assertEquals("reject", siteRule(blocked).getString("action"))
        assertEquals(newBindings.entries.getValue("us").tag, siteRule(healthy).getString("outbound"))
        System.getenv("NOVA_SS_VALIDATION_FIXTURE_DIR")?.let { path ->
            val directory = java.io.File(path)
            check(directory.mkdir())
            java.io.File(directory, "combined-quarantined.json").writeText(blocked)
            java.io.File(directory, "combined-restored.json").writeText(healthy)
        }
    }

    @Test fun shadowsocksPluginOptionsMatchPinnedCoreAndPreserveEscapes() {
        fun profile(plugin: Any, options: Any) = raw().copy(fullConfigJson = null, outboundJson = JSONObject()
            .put("type", "shadowsocks").put("server", "127.0.0.1").put("server_port", 18003)
            .put("method", "aes-128-gcm").put("password", "fixture")
            .put("plugin", plugin).put("plugin_opts", options).toString())

        val accepted = listOf(
            profile("", ";"), profile(JSONObject.NULL, JSONObject.NULL),
            profile(JSONObject(), JSONArray()), profile("obfs-local", JSONObject()),
            profile("obfs-local", ""), profile("obfs-local", "obfs=tls"),
            profile("obfs-local", "obfs=http;obfs=fixture;"),
            profile("obfs-local", """o\bfs=t\ls;obfs-host=example.test\;extra\=value"""),
            profile("v2ray-plugin", ""), profile("v2ray-plugin", "mode=websocket;mux=+2"),
            profile("v2ray-plugin", "mux=-1"), profile("v2ray-plugin", "mode=quic;tls;mux=not-parsed"),
            profile("v2ray-plugin", "mode=quic;tls=")
        )
        accepted.forEach { assertEquals(it.outboundJson, MultiServerProfileAdapter.prepare(it).outboundJson) }

        val invalid = listOf(
            profile("unsupported-fixture", ""), profile(false, ""), profile("obfs-local", JSONObject().put("obfs", "tls")),
            profile("obfs-local", JSONArray().put("obfs=tls")),
            profile("obfs-local", "obfs=fixture"), profile("obfs-local", "obfs="),
            profile("obfs-local", "obfs=fixture;obfs=http"), profile("obfs-local", ";obfs=http"),
            profile("obfs-local", "obfs=http;=a"), profile("obfs-local", "obfs=http;;"),
            profile("obfs-local", "obfs=http\\"),
            profile("v2ray-plugin", "mux=bad"), profile("v2ray-plugin", "mux=9223372036854775808"),
            profile("v2ray-plugin", "mux="), profile("v2ray-plugin", "mode=quic"),
            profile("v2ray-plugin", "mode=grpc"), profile("v2ray-plugin", "mode=")
        )
        invalid.forEach(::refused)
        val healthy = raw().copy(id = "fr", fullConfigJson = null, outboundJson = outbound().toString())
        val settings = MultiServerSettings(0, healthy.id, listOf(
            MultiServerRule(packageName = "example.chat", target = ServerRouteTarget(ServerRouteKind.PROFILE, "us"))))
        val profiles = listOf(healthy, invalid.first())
        val config = SingBoxConfigFactory.build(healthy, AppRoutingSettings(multiServer = settings),
            "com.nova.fixture", profiles, "/private/ss-plugin-fixture.db", setOf("us"))
        val bindings = RouteExitBindings.capture(settings, profiles, config, setOf("us"))
        assertEquals(setOf("fr"), bindings.entries.keys)
        assertEquals(setOf("us"), bindings.quarantined.keys)
        assertFalse(config.contains("unsupported-fixture"))
        assertThrows(IllegalArgumentException::class.java) {
            bindings.validateCatalog(listOf(healthy, accepted.last()), setOf("fr", "us"))
        }
    }

    @Test fun standaloneXhttpTransportAndTlsArePreservedForNativeValidation() {
        val o=outbound().put("type","vless").put("uuid","11111111-1111-1111-1111-111111111111")
            .put("transport",JSONObject().put("type","xhttp").put("path","/fixture").put("mode","auto"))
            .put("tls",JSONObject().put("enabled",true).put("server_name","example.org"))
        val prepared=MultiServerProfileAdapter.prepare(raw(JSONObject().put("outbounds",JSONArray().put(o))))
        assertEquals(o.toString(),prepared.outboundJson)
        // Preservation is not a claim that every selected native core supports this transport.
    }
}

