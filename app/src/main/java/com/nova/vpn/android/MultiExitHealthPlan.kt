package com.nova.vpn.android

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

/** Authenticated loopback SOCKS channels with pinned DNS and outbound for each probe. */
internal class MultiExitHealthPlan private constructor(val config: String,val bindings: RouteExitBindings,
    val probes: Map<String,AndroidTrafficProbe>,val unsupported: Set<String>) {
    fun cancel() { probes.values.forEach { it.cancel() } }
    companion object {
        const val TAG="nova-multi-health"
        fun prepare(config: String,bindings: RouteExitBindings,policy: RoutePolicyIdentity, separateListeners: Boolean = false): MultiExitHealthPlan {
            bindings.validate(config,policy.profileIds)
            val root=JSONObject(config);val inbounds=root.getJSONArray("inbounds")
            require((0 until inbounds.length()).none { inbounds.getJSONObject(it).optString("tag").let { tag -> tag == TAG || tag.startsWith("$TAG-") } })
            val outbounds=root.getJSONArray("outbounds");val outs=(0 until outbounds.length()).map { outbounds.getJSONObject(it) }.associateBy { it.getString("tag") }
            val dns=root.getJSONObject("dns").getJSONArray("servers");val dnsItems=(0 until dns.length()).map { dns.getJSONObject(it) }
            require(dnsItems.map { it.getString("tag") }.distinct().size==dnsItems.size)
            val dnsByTag=dnsItems.associateBy { it.getString("tag") }
            val supported=bindings.entries.filterValues { outs.getValue(it.tag).getString("type") in setOf("vless","vmess","trojan","shadowsocks","hysteria","hysteria2","tuic","socks","http","anytls") && !outs.getValue(it.tag).has("detour") }
            val unsupported=bindings.entries.keys-supported.keys
            if(supported.isEmpty())return MultiExitHealthPlan(config,bindings,emptyMap(),unsupported)
            val groups = if (separateListeners) supported.toSortedMap().entries.map { listOf(it) }
                else listOf(supported.toSortedMap().entries.toList())
            val rules = JSONArray()
            val channels = linkedMapOf<String, Triple<Int, String, String>>()
            val reservations = mutableListOf<ServerSocket>()
            try {
                groups.forEachIndexed { index, entries ->
                    val tag = if (separateListeners) "$TAG-$index" else TAG
                    val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
                    reservations += socket
                    val port = socket.localPort
                    val users = JSONArray()
                    entries.forEach { (id, entry) ->
                        val resolver = requireNotNull(dnsByTag["dns-${entry.tag}"])
                        require(resolver.optString("type") == "https" && resolver.optString("detour") == entry.tag &&
                            resolver.optJSONObject("tls")?.optBoolean("enabled") == true &&
                            resolver.optJSONObject("tls")?.optBoolean("insecure") != true)
                        val user = UUID.randomUUID().toString()
                        val secret = UUID.randomUUID().toString()
                        channels[id] = Triple(port, user, secret)
                        users.put(JSONObject().put("username", user).put("password", secret))
                        fun selector() = JSONObject().put("inbound", JSONArray().put(tag)).apply {
                            if (!separateListeners) put("auth_user", JSONArray().put(user))
                        }
                        rules.put(selector().put("action", "resolve").put("server", "dns-${entry.tag}")
                            .put("strategy", "ipv4_only").put("disable_cache", true))
                        rules.put(selector().put("action", "route").put("outbound", entry.tag))
                    }
                    rules.put(JSONObject().put("inbound", JSONArray().put(tag)).put("action", "reject"))
                    inbounds.put(JSONObject().put("type", "socks").put("tag", tag).put("listen", "127.0.0.1")
                        .put("listen_port", port).put("users", users))
                }
            } finally {
                reservations.forEach { runCatching { it.close() } }
            }
            val route = root.getJSONObject("route")
            val original = route.getJSONArray("rules")
            repeat(original.length()) { rules.put(original.get(it)) }
            route.put("rules", rules)
            val generated = root.toString()
            val probes = channels.mapValues { (_, channel) ->
                AndroidTrafficProbe.channel(generated, channel.first, channel.second, channel.third)
            }

            return MultiExitHealthPlan(generated,bindings,java.util.Collections.unmodifiableMap(probes),unsupported.toSet())
        }
    }
}
