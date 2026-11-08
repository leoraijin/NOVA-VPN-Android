package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

/** Compiles captured assignments into one TUN config. Native check is still required. */
object MultiServerConfigFactory {
    fun build(base: JSONObject, settings: MultiServerSettings, profileOutbounds: Map<String, JSONObject>,
        ownPackage: String, ipv6: Boolean, cachePath: String): JSONObject {
        settings.validate()
        require(ownPackage.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")))
        require(cachePath.startsWith("/") && !cachePath.split('/').contains(".."))
        require(settings.rules.none { it.packageName == ownPackage }) { "VPN-приложение нельзя направлять через себя" }
        val config = JSONObject(base.toString())
        val inbounds = config.getJSONArray("inbounds")
        require(inbounds.length() == 1 && inbounds.getJSONObject(0).getString("type") == "tun")
        val tun = inbounds.getJSONObject(0)
        tun.remove("include_package")
        tun.put("exclude_package", JSONArray().put(ownPackage))
        // Both families must enter TUN; disabled IPv6 is rejected in the route rules.
        tun.put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
        val referenced = MultiServerPolicy(settings.revision, settings.defaultProfileId, settings.rules, profileOutbounds.keys).referencedProfileIds.sorted()
        val tags = referenced.filter { it in profileOutbounds }.mapIndexed { index, id -> id to "nova-exit-$index" }.toMap()
        val outbounds = JSONArray().put(JSONObject().put("type", "direct").put("tag", "nova-direct").put("domain_resolver", "nova-bootstrap-dns"))
        tags.forEach { (id, tag) ->
            val outbound = JSONObject(profileOutbounds.getValue(id).toString())
            require(outbound.optString("type") in setOf("vless","vmess","trojan","shadowsocks","hysteria","hysteria2","tuic","socks","http","anytls")) { "Профиль несовместим с назначением сервера" }
            require(outbound.optString("server").isNotBlank() && outbound.optInt("server_port") in 1..65535)
            require(!outbound.has("detour")) { "Зависимый outbound требует отдельной поддержки" }
            outbound.put("tag", tag).put("domain_resolver", "nova-bootstrap-dns")
            outbounds.put(outbound)
        }
        config.put("outbounds", outbounds)
        val servers = JSONArray().put(JSONObject().put("type","local").put("tag","nova-bootstrap-dns"))
            .put(JSONObject().put("type","fakeip").put("tag","nova-fakeip").put("inet4_range","198.19.0.0/16").put("inet6_range","fc00::/18"))
        fun resolver(tag: String) = JSONObject().put("type","https").put("tag","dns-$tag")
            .put("server","1.1.1.1").put("path","/dns-query").put("detour",tag)
            .put("tls",JSONObject().put("enabled",true).put("server_name","cloudflare-dns.com"))
        servers.put(resolver("nova-direct"))
        tags.values.forEach { servers.put(resolver(it)) }
        val hasAppAssignments = settings.rules.any { it.packageName != null }
        val routeRules = JSONArray()
        if (hasAppAssignments) routeRules.put(
            JSONObject().put("package_name", JSONArray().put(SharedUidExitGuard.CONFLICT_PACKAGE)).put("action", "reject"))
        // Preserve the original FakeIP hostname when an encrypted ClientHello has a different outer name.
        routeRules.put(JSONObject().put("domain_regex", JSONArray().put(".+")).put("port", JSONArray().put(53))
            .put("action", "sniff").put("sniffer", JSONArray().put("dns")))
            .put(JSONObject().put("domain_regex", JSONArray().put(".+")).put("invert", true).put("action", "sniff"))
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        if (!ipv6) routeRules.put(JSONObject().put("ip_version",6).put("action","reject"))
        fun selector(rule: MultiServerRule): JSONObject = JSONObject().apply {
            if (rule.packageName != null) put("package_name",JSONArray().put(rule.packageName)) else {
                put("domain",JSONArray().put(rule.domain))
                if (rule.includeSubdomains) put("domain_suffix",JSONArray().put(".${rule.domain}"))
            }
        }
        val ordered = settings.rules.sortedWith(compareBy<MultiServerRule> {
            if (it.target.kind == ServerRouteKind.BLOCK) 0 else if (it.domain != null) 1 else 2
        }.thenByDescending { it.domain?.length ?: 0 }.thenBy { it.includeSubdomains })
        val dnsRules = JSONArray()
        // Explicit blocks precede local DNS and FakeIP allocation.
        ordered.filter { it.target.kind == ServerRouteKind.BLOCK }.forEach { rule ->
            dnsRules.put(selector(rule).put("action", "reject"))
        }
        fun usesLocalDns(rule: MultiServerRule) = rule.domain?.endsWith(".local") == true
        val localMatch = JSONObject().put("domain_suffix", JSONArray().put(".local"))
        val assigned = ordered.filter { usesLocalDns(it) && it.target.kind != ServerRouteKind.BLOCK }
        // Retain the hostname via FakeIP for sites with an explicit route.
        val localRule = if (assigned.isEmpty()) localMatch else {
            val excluded = JSONObject().put("domain", JSONArray(assigned.map { it.domain })).put("invert", true)
            val suffixes = assigned.filter { it.includeSubdomains }.map { ".${it.domain}" }
            if (suffixes.isNotEmpty()) excluded.put("domain_suffix", JSONArray(suffixes))
            JSONObject().put("type", "logical").put("mode", "and").put("rules", JSONArray().put(localMatch).put(excluded))
        }
        dnsRules.put(localRule.put("action", "route").put("server", "nova-bootstrap-dns"))
        dnsRules.put(JSONObject().put("query_type",JSONArray().put("HTTPS").put("SVCB")).put("action","predefined").put("rcode","NOERROR"))
        if (!ipv6) dnsRules.put(JSONObject().put("query_type","AAAA").put("action","predefined").put("rcode","NOERROR"))
        dnsRules.put(JSONObject().put("query_type",JSONArray().put("A").put("AAAA")).put("action","route").put("server","nova-fakeip"))
        fun exit(target: ServerRouteTarget): String? = when (target.kind) {
            ServerRouteKind.BLOCK -> null
            ServerRouteKind.DIRECT -> "nova-direct"
            ServerRouteKind.PROFILE -> tags[target.profileId]
            ServerRouteKind.DEFAULT -> tags[settings.defaultProfileId]
        }
        fun appendRoute(match: JSONObject, target: ServerRouteTarget, resolver: String? = null) {
            val tag = exit(target)
            if (tag == null) routeRules.put(JSONObject(match.toString()).put("action","reject")) else {
                routeRules.put(JSONObject(match.toString()).put("action","resolve").put("server",resolver ?: "dns-$tag").put("strategy",if (ipv6) "prefer_ipv4" else "ipv4_only"))
                routeRules.put(JSONObject(match.toString()).put("action","route").put("outbound",tag))
            }
        }
        ordered.forEach { rule ->
            val resolver = "nova-bootstrap-dns".takeIf { usesLocalDns(rule) && rule.target.kind == ServerRouteKind.DIRECT }
            appendRoute(selector(rule),rule.target,resolver)
            // A/AAAA use FakeIP above. Forwarded queries use site and package priority.
            if (rule.target.kind != ServerRouteKind.BLOCK) {
                val tag = exit(rule.target)
                dnsRules.put(selector(rule).apply {
                    if (tag == null) put("action","reject") else put("action","route").put("server",resolver ?: "dns-$tag")
                })
            }
        }
        if (hasAppAssignments) {
            val unknownOwner = JSONObject()
                .put("package_name_regex", JSONArray().put(".+")).put("invert", true).put("action", "reject")
            routeRules.put(unknownOwner)
            dnsRules.put(JSONObject(unknownOwner.toString()))
        }
        appendRoute(JSONObject(),ServerRouteTarget(ServerRouteKind.DEFAULT))
        val defaultTag = tags[settings.defaultProfileId]
        if (defaultTag == null) dnsRules.put(JSONObject().put("action","reject"))
        config.put("route",JSONObject().put("auto_detect_interface",true).put("default_domain_resolver","nova-bootstrap-dns")
            .put("rules",routeRules).put("final",defaultTag ?: "nova-direct"))
        config.put("dns",JSONObject().put("servers",servers).put("rules",dnsRules)
            .put("final",defaultTag?.let { "dns-$it" } ?: "nova-bootstrap-dns").put("independent_cache",true))
        val experimental = config.optJSONObject("experimental") ?: JSONObject()
        experimental.put("cache_file",JSONObject().put("enabled",true).put("path",cachePath).put("store_fakeip",true))
        config.put("experimental",experimental)
        return config
    }
}
