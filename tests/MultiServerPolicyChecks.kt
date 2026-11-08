package com.nova.vpn.android.data

object MultiServerPolicyChecks {
    @JvmStatic fun main(args: Array<String>) {
        var count = 0
        fun verify(value: Boolean) { check(value) { "Failed assertion ${count + 1}" }; count++ }
        val app = "com.openai.chatgpt"
        val rules = mutableListOf(
            MultiServerRule(packageName=app,target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")),
            MultiServerRule(domain="example.com",target=ServerRouteTarget(ServerRouteKind.PROFILE,"de")),
            MultiServerRule(domain="chat.example.com",includeSubdomains=false,target=ServerRouteTarget(ServerRouteKind.PROFILE,"jp")),
            MultiServerRule(domain="blocked.example.com",target=ServerRouteTarget(ServerRouteKind.BLOCK)))
        val ids = mutableSetOf("fr","us","de","jp")
        val p = MultiServerPolicy(1,"fr",rules,ids)
        verify(p.decide(app,null).effective.profileId=="us")
        verify(p.decide(app,"example.com").effective.profileId=="de")
        verify(p.decide(app,"chat.example.com").effective.profileId=="jp")
        verify(p.decide(app,"a.chat.example.com").effective.profileId=="de")
        verify(p.decide(app,"blocked.example.com").effective.kind==ServerRouteKind.BLOCK)
        verify(p.decide(null,"notexample.com").effective.profileId=="fr")
        verify(p.decide(null,null,false).reason=="unknown-owner")
        verify(p.decide(null,"example.com",false).effective.profileId=="de")
        rules.clear(); ids.clear()
        verify(p.decide(app,null).effective.profileId=="us")
        verify(p.referencedProfileIds.size==4)
        val missing = MultiServerPolicy(2,"fr",listOf(MultiServerRule(packageName=app,target=ServerRouteTarget(ServerRouteKind.PROFILE,"gone"))),setOf("fr"))
        verify(missing.decide(app,null).reason=="missing-profile")
        verify(missing.decide(app,null).effective.kind==ServerRouteKind.BLOCK)
        verify(missing.decide(null,null).effective.profileId=="fr")
        verify(runCatching { MultiServerPolicy(0,"fr",listOf(
            MultiServerRule(packageName=app,target=ServerRouteTarget(ServerRouteKind.DIRECT)),
            MultiServerRule(packageName=app,target=ServerRouteTarget(ServerRouteKind.DEFAULT))),setOf("fr")) }.isFailure)
        verify(p.decide(app," EXAMPLE.COM. ").effective.profileId=="de")
        verify(runCatching { MultiServerRule(domain="https://example.com",target=ServerRouteTarget(ServerRouteKind.DIRECT)) }.isFailure)
        val saved = MultiServerSettings(3,"fr",listOf(MultiServerRule(packageName=app,target=ServerRouteTarget(ServerRouteKind.PROFILE,"missing"))))
        verify(MultiServerSettings.fromJson(saved.toJson()) == saved)
        val settings = AppRoutingSettings(multiServer=saved)
        val document = RoutingPresets.migrate(settings)
        verify(RoutingPresets.fromJson(document.toJson()).active.settings == settings)
        val staged = RoutingPresets.migrate(AppRoutingSettings()).stage("my-routing","req","session",settings)
        val decoded = RoutingPresets.fromJson(staged.toJson())
        verify(decoded.active.settings.multiServer == null)
        verify(decoded.commit("req","my-routing").active.settings.multiServer == saved)
        verify(decoded.recover("different-session").active.settings.multiServer == null)
        val legacy = RoutingPresets.migrate(AppRoutingSettings()).toJson().put("schema",1)
        verify(RoutingPresets.fromJson(legacy).active.settings.multiServer == null)
        verify(runCatching { MultiServerSettings.fromJson(saved.toJson().put("schema",2)) }.isFailure)
        fun outbound() = org.json.JSONObject().put("type","vless").put("server","example.net").put("server_port",443).put("uuid","test")
        val base = org.json.JSONObject().put("inbounds",org.json.JSONArray().put(org.json.JSONObject().put("type","tun").put("include_package",org.json.JSONArray().put("old.app"))))
        val assignments = MultiServerSettings(4,"fr",listOf(MultiServerRule(packageName=app,target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")),MultiServerRule(domain="example.com",target=ServerRouteTarget(ServerRouteKind.PROFILE,"fr"))))
        val inputs = mapOf("fr" to outbound(),"us" to outbound())
        val config = MultiServerConfigFactory.build(base,assignments,inputs,"com.nova.vpn",false,"/data/user/0/com.nova.vpn/files/multi-cache.db")
        verify(config.getJSONArray("outbounds").length()==3)
        verify(!base.getJSONArray("inbounds").getJSONObject(0).has("exclude_package"))
        verify(!inputs.getValue("us").has("tag"))
        verify(!config.getJSONArray("inbounds").getJSONObject(0).has("include_package"))
        val routes = config.getJSONObject("route").getJSONArray("rules")
        val domainIndex = (0 until routes.length()).first { routes.getJSONObject(it).has("domain") }
        val appIndex = (0 until routes.length()).first { routes.getJSONObject(it).has("package_name") }
        verify(domainIndex < appIndex)
        verify(routes.getJSONObject(domainIndex).getString("action")=="resolve")
        verify(routes.getJSONObject(domainIndex+1).getString("action")=="route")
        verify(config.getJSONObject("experimental").getJSONObject("cache_file").getBoolean("store_fakeip"))
        val missingConfig = MultiServerConfigFactory.build(base,saved,mapOf("fr" to outbound()),"com.nova.vpn",true,"/private/cache.db")
        val missingRules = missingConfig.getJSONObject("route").getJSONArray("rules")
        verify((0 until missingRules.length()).any { missingRules.getJSONObject(it).has("package_name") && missingRules.getJSONObject(it).getString("action")=="reject" })
        verify(runCatching { MultiServerConfigFactory.build(base,assignments,mapOf("fr" to outbound().put("detour","other")),"com.nova.vpn",true,"/private/cache.db") }.isFailure)
        fun profile(id: String) = ImportedProfile(id=id,name=id,protocol="vless",server="example.net",port=443,
            outboundJson="""{"type":"vless","server":"example.net","server_port":443,"uuid":"11111111-1111-4111-8111-111111111111"}""")
        val fr = profile("fr"); val us = profile("us")
        val actual = org.json.JSONObject(SingBoxConfigFactory.build(fr,AppRoutingSettings(multiServer=assignments),"com.nova.vpn",listOf(fr,us),"/private/cache.db"))
        verify(actual.getJSONArray("outbounds").length()==3)
        verify(runCatching { SingBoxConfigFactory.build(fr,AppRoutingSettings(multiServer=assignments),"com.nova.vpn") }.isFailure)
        verify(runCatching { SingBoxConfigFactory.build(fr,AppRoutingSettings(packages=setOf(app),multiServer=assignments),"com.nova.vpn",listOf(fr,us),"/private/cache.db") }.isFailure)
        verify(runCatching { SingBoxConfigFactory.build(fr,AppRoutingSettings(multiServer=assignments),"com.nova.vpn",listOf(fr,us.copy(fullConfigJson="{}")),"/private/cache.db") }.isFailure)
        verify(runCatching { SingBoxConfigFactory.build(fr,AppRoutingSettings(multiServer=assignments),"com.nova.vpn",listOf(fr,fr),"/private/cache.db") }.isFailure)
        val legacyConfig = org.json.JSONObject(SingBoxConfigFactory.build(fr))
        verify(legacyConfig.getJSONObject("route").getString("final")=="proxy")
        val referenceDocument = RoutingPresets.migrate(AppRoutingSettings()).let { document ->
            document.copy(presets=document.presets + RoutingPreset("assigned","Assigned",AppRoutingSettings(multiServer=assignments)))
        }.stage("my-routing","pending","session",AppRoutingSettings(multiServer=saved))
        verify(referenceDocument.referencedProfileIds == setOf("fr","us","missing"))
        val incoming = profile("next").copy(server="next.example.net",outboundJson=org.json.JSONObject(profile("next").outboundJson).put("server","next.example.net").toString())
        val catalog = SubscriptionCatalog(listOf(us),setOf(SubscriptionMembership("us","source")),emptySet())
        val reconciled = SubscriptionReconciler.reconcile(catalog,"source",listOf(incoming),true,referenceDocument.referencedProfileIds)
        verify(reconciled.catalog.profiles.any { it.id=="us" })
        verify(SubscriptionMembership("us","source") !in reconciled.catalog.memberships)
        val detached = SubscriptionReconciler.reconcile(catalog,"source",listOf(incoming),true,emptySet())
        verify(detached.catalog.profiles.none { it.id=="us" })
        println("PASS $count policy, persistence, config, adapter and retention assertions")
    }
}