package com.nova.vpn.android

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

/** One authenticated loopback SOCKS listener, with a distinct user and pinned DNS/exit for each probe. */
internal class MultiExitHealthPlan private constructor(val config: String,val bindings: RouteExitBindings,
    val probes: Map<String,AndroidTrafficProbe>,val unsupported: Set<String>) {
    fun cancel() { probes.values.forEach { it.cancel() } }
    companion object {
        const val TAG="nova-multi-health"
        fun prepare(config: String,bindings: RouteExitBindings,policy: RoutePolicyIdentity): MultiExitHealthPlan {
            bindings.validate(config,policy.profileIds)
            val root=JSONObject(config);val inbounds=root.getJSONArray("inbounds")
            require((0 until inbounds.length()).none { inbounds.getJSONObject(it).optString("tag")==TAG })
            val outbounds=root.getJSONArray("outbounds");val outs=(0 until outbounds.length()).map { outbounds.getJSONObject(it) }.associateBy { it.getString("tag") }
            val dns=root.getJSONObject("dns").getJSONArray("servers");val dnsItems=(0 until dns.length()).map { dns.getJSONObject(it) }
            require(dnsItems.map { it.getString("tag") }.distinct().size==dnsItems.size)
            val dnsByTag=dnsItems.associateBy { it.getString("tag") }
            val supported=bindings.entries.filterValues { outs.getValue(it.tag).getString("type") in setOf("vless","vmess","trojan","shadowsocks","hysteria","hysteria2","tuic","socks","http","anytls") && !outs.getValue(it.tag).has("detour") }
            val unsupported=bindings.entries.keys-supported.keys
            if(supported.isEmpty())return MultiExitHealthPlan(config,bindings,emptyMap(),unsupported)
            val port=ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { it.localPort }
            val users=JSONArray();val rules=JSONArray();val credentials=linkedMapOf<String,Pair<String,String>>()
            supported.toSortedMap().forEach { (id,entry)->
                val resolver=requireNotNull(dnsByTag["dns-${entry.tag}"])
                require(resolver.optString("type")=="https" && resolver.optString("detour")==entry.tag &&
                    resolver.optJSONObject("tls")?.optBoolean("enabled")==true && resolver.optJSONObject("tls")?.optBoolean("insecure")!=true)
                val user=UUID.randomUUID().toString();val secret=UUID.randomUUID().toString();credentials[id]=user to secret
                users.put(JSONObject().put("username",user).put("password",secret))
                fun selector()=JSONObject().put("inbound",JSONArray().put(TAG)).put("auth_user",JSONArray().put(user))
                rules.put(selector().put("action","resolve").put("server","dns-${entry.tag}").put("strategy","ipv4_only").put("disable_cache",true))
                rules.put(selector().put("action","route").put("outbound",entry.tag))
            }
            // No fall-through from an internal probe listener to an app/site/default route.
            rules.put(JSONObject().put("inbound",JSONArray().put(TAG)).put("action","reject"))
            val route=root.getJSONObject("route");val original=route.getJSONArray("rules")
            repeat(original.length()){rules.put(original.get(it))};route.put("rules",rules)
            inbounds.put(JSONObject().put("type","socks").put("tag",TAG).put("listen","127.0.0.1").put("listen_port",port).put("users",users))
            val generated=root.toString()
            val probes=credentials.mapValues { (_,pair)->AndroidTrafficProbe.channel(generated,port,pair.first,pair.second) }
            return MultiExitHealthPlan(generated,bindings,java.util.Collections.unmodifiableMap(probes),unsupported.toSet())
        }
    }
}
