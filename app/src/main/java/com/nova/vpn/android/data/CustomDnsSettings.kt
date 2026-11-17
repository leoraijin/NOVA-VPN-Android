package com.nova.vpn.android.data

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

data class CustomDnsServer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val domains: List<String> = emptyList()
) {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9-]{1,64}")) && name.trim().length in 1..64) { "Укажите название DNS" }
        endpoint()
        require(domains.size <= 1000 && domains.distinct().size == domains.size && domains.all { SiteDomain.normalize(it) == it }) { "Проверьте список сайтов" }
    }
    fun endpoint(): URI {
        val uri = URI(if ("://" in address) address else "udp://$address")
        require(uri.scheme in setOf("udp", "tcp", "tls", "https") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null && uri.port in -1..65535 && uri.port != 0 &&
            (uri.scheme == "https" || uri.rawQuery == null && uri.path.orEmpty().isEmpty())) { "Укажите IP DNS или адрес udp://, tcp://, tls://, https://" }
        return uri
    }
    fun toJson() = JSONObject().put("id", id).put("name", name).put("address", address).put("domains", JSONArray(domains))
    companion object {
        fun fromJson(json: JSONObject) = CustomDnsServer(json.getString("id"), json.getString("name"), json.getString("address"),
            json.getJSONArray("domains").let { array -> List(array.length()) { array.getString(it) } }).also { it.validate() }
    }
}

data class CustomDnsSettings(val servers: List<CustomDnsServer> = emptyList(), val selectedId: String? = null,
    val appServers: Map<String, String> = emptyMap()) {
    val selected: CustomDnsServer? get() = servers.singleOrNull { it.id == selectedId }
    fun validate() {
        require(servers.size <= 20 && servers.map { it.id }.distinct().size == servers.size)
        servers.forEach { it.validate() }
        require(appServers.size <= 2000 && appServers.all { (name, id) ->
            name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) && servers.any { it.id == id }
        }) { "Приложению назначен отсутствующий DNS" }
        require(selectedId == null || servers.any { it.id == selectedId }) { "Выберите существующий DNS" }
    }
    fun toJson() = JSONObject().put("servers", JSONArray().apply { servers.forEach { put(it.toJson()) } })
        .put("selectedId", selectedId ?: JSONObject.NULL)
        .put("appServers", JSONObject(appServers.toSortedMap()))
    companion object {
        fun fromJson(json: JSONObject): CustomDnsSettings = CustomDnsSettings(json.getJSONArray("servers").let { array ->
            require(array.length() <= 20)
            List(array.length()) { CustomDnsServer.fromJson(array.getJSONObject(it)) }
        }, if (json.isNull("selectedId")) null else json.getString("selectedId"), json.optJSONObject("appServers")?.let { assignments ->
            assignments.keys().asSequence().associateWith { assignments.getString(it) }
        }.orEmpty()).also { it.validate() }
    }
}

/** Compiles site and application DNS assignments into the captured sing-box policy. */
internal object CustomDnsConfig {
    const val OUTBOUND_PREFIX = "nova-custom-dns-direct"
    const val RESOLVER_PREFIX = "nova-custom-dns-resolver"

    fun apply(config: JSONObject, settings: CustomDnsSettings, ownPackage: String?, unavailablePackages: Set<String> = emptySet()): JSONObject {
        settings.validate()
        val appServers = settings.appServers.filterKeys { it !in unavailablePackages }
        require(ownPackage == null || ownPackage !in appServers) { "NOVA VPN нельзя направить через собственный DNS-маршрут" }
        val appsByServer = appServers.entries.groupBy({ it.value }, { it.key })
        val active = settings.servers.filter { server ->
            server.id in appsByServer || server.id == settings.selectedId && server.domains.isNotEmpty()
        }
        if (active.isEmpty()) return config
        val route = config.getJSONObject("route")
        val dns = config.getJSONObject("dns")
        val outbounds = config.getJSONArray("outbounds")
        val dnsServers = dns.getJSONArray("servers")
        val tags = active.mapIndexed { index, server ->
            server.id to (if (index == 0) OUTBOUND_PREFIX else "$OUTBOUND_PREFIX-${server.id}")
        }.toMap()
        val resolvers = active.mapIndexed { index, server ->
            server.id to (if (index == 0) RESOLVER_PREFIX else "$RESOLVER_PREFIX-${server.id}")
        }.toMap()
        for (server in active) {
            val direct = tags.getValue(server.id)
            val tag = resolvers.getValue(server.id)
            require((0 until outbounds.length()).none { outbounds.getJSONObject(it).optString("tag") == direct } &&
                (0 until dnsServers.length()).none { dnsServers.getJSONObject(it).optString("tag") == tag }) { "Занят тег пользовательского DNS" }
            outbounds.put(JSONObject().put("type", "direct").put("tag", direct))
            val uri = server.endpoint()
            val host = uri.host.removeSurrounding("[", "]")
            // An omitted detour gives DNS its protected physical-network connection.
            val resolver = JSONObject().put("type", uri.scheme).put("tag", tag).put("server", host)
            if (uri.port != -1) resolver.put("server_port", uri.port)
            if (uri.scheme in setOf("https", "tls")) resolver.put("tls", JSONObject().put("enabled", true).put("server_name", host))
            if (uri.scheme == "https") resolver.put("path", (uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/dns-query") +
                (uri.rawQuery?.let { "?$it" } ?: ""))
            route.opt("default_domain_resolver")?.let { resolver.put("domain_resolver", it) }
            dnsServers.put(resolver)
        }
        dns.put("reverse_mapping", true)
        val existing = route.optJSONArray("rules") ?: JSONArray()
        val ipv4Only = dns.optString("strategy") == "ipv4_only" || (0 until existing.length()).any {
            existing.getJSONObject(it).optInt("ip_version") == 6 && existing.getJSONObject(it).optString("action") == "reject"
        }
        val siteServer = settings.selected?.takeIf { it.domains.isNotEmpty() }
        fun siteMatch(server: CustomDnsServer) = JSONObject().put("domain", JSONArray(server.domains))
            .put("domain_suffix", JSONArray(server.domains.map { ".$it" }))
        fun appMatch(server: CustomDnsServer) = JSONObject().put("package_name", JSONArray(appsByServer.getValue(server.id).sorted()))
        val rules = JSONArray()
        val siteRules = JSONArray()
        val tail = JSONArray()
        if (appServers.isNotEmpty()) rules.put(JSONObject().put("package_name", JSONArray().put(SharedUidExitGuard.CONFLICT_PACKAGE)).put("action", "reject"))
        for (i in 0 until existing.length()) {
            val rule = existing.getJSONObject(i)
            if (rule.optJSONArray("package_name")?.optString(0) == SharedUidExitGuard.CONFLICT_PACKAGE) {
                if (appServers.isEmpty()) rules.put(rule)
                continue
            }
            val protocolRule = rule.optString("action") in setOf("sniff", "hijack-dns")
            val block = rule.optString("action") == "reject" && (hasSiteMatch(rule) || rule.has("ip_version"))
            if (protocolRule || block) rules.put(rule)
            else if (hasSiteMatch(rule)) siteRules.put(rule)
            else {
                val names = rule.optJSONArray("package_name")
                if (names != null && !rule.optBoolean("invert")) {
                    val remaining = (0 until names.length()).map(names::getString).filterNot { it in appServers }
                    if (remaining.isEmpty()) continue
                    rule.put("package_name", JSONArray(remaining))
                }
                tail.put(rule)
            }
        }
        if ((appServers.isNotEmpty() || siteServer != null) && (0 until existing.length()).none { existing.getJSONObject(it).optString("action") == "sniff" }) {
            rules.put(JSONObject().put("action", "sniff"))
        }
        fun append(match: JSONObject, server: CustomDnsServer) {
            rules.put(JSONObject(match.toString()).put("action", "resolve").put("server", resolvers.getValue(server.id))
                .put("strategy", if (ipv4Only) "ipv4_only" else "prefer_ipv4"))
            rules.put(JSONObject(match.toString()).put("action", "route").put("outbound", tags.getValue(server.id)))
        }
        siteServer?.let { append(siteMatch(it), it) }
        for (i in 0 until siteRules.length()) rules.put(siteRules.getJSONObject(i))
        for (server in active.filter { it.id in appsByServer }) append(appMatch(server), server)
        for (i in 0 until tail.length()) rules.put(tail.getJSONObject(i))
        route.put("rules", rules)

        val oldDnsRules = dns.optJSONArray("rules") ?: JSONArray()
        val dnsHead = JSONArray()
        val dnsTail = JSONArray()
        for (i in 0 until oldDnsRules.length()) {
            val rule = oldDnsRules.getJSONObject(i)
            val block = rule.optString("action") == "reject" && hasSiteMatch(rule)
            if (block || rule.optString("action") == "predefined") dnsHead.put(rule)
            else dnsTail.put(rule)
        }
        siteServer?.let {
            dnsHead.put(siteMatch(it).put("action", "route").put("server", resolvers.getValue(it.id)))
        }
        for (i in 0 until dnsTail.length()) if (hasSiteMatch(dnsTail.getJSONObject(i))) dnsHead.put(dnsTail.getJSONObject(i))
        // Real A/AAAA answers for app DNS must precede the multi-VPN FakeIP allocator.
        for (server in active.filter { it.id in appsByServer }) dnsHead.put(appMatch(server).put("action", "route").put("server", resolvers.getValue(server.id)))
        for (i in 0 until dnsTail.length()) if (!hasSiteMatch(dnsTail.getJSONObject(i))) dnsHead.put(dnsTail.getJSONObject(i))
        dns.put("rules", dnsHead)
        config.getJSONArray("inbounds").let { inbounds ->
            for (i in 0 until inbounds.length()) if (inbounds.getJSONObject(i).optString("type") == "tun") {
                val tun = inbounds.getJSONObject(i)
                if (siteServer != null) {
                    tun.remove("include_package")
                    tun.put("exclude_package", JSONArray(listOfNotNull(ownPackage)))
                } else {
                    val included = tun.optJSONArray("include_package")
                    val excluded = tun.optJSONArray("exclude_package")
                    if (included != null) {
                        val packages = (0 until included.length()).map(included::getString) + appServers.keys
                        tun.put("include_package", JSONArray(packages.distinct()))
                    } else if (excluded != null) {
                        tun.put("exclude_package", JSONArray((0 until excluded.length()).map(excluded::getString).filterNot { it in appServers }))
                    }
                }
            }
        }
        return config
    }
    private fun hasSiteMatch(rule: JSONObject) = listOf("domain", "domain_suffix", "domain_regex", "domain_keyword", "rule_set").any(rule::has)
}

/** Replaces the previous application route for the complete shared-UID group. */
internal fun assignDnsApps(settings: AppRoutingSettings, packages: Set<String>, serverId: String): AppRoutingSettings {
    require(packages.isNotEmpty() && settings.customDns.servers.any { it.id == serverId })
    val dns = settings.customDns.copy(appServers = settings.customDns.appServers + packages.associateWith { serverId })
    val multi = settings.multiServer?.let { it.copy(revision = it.revision + 1, rules = assignRoutingApps(it.rules, packages, null)) }
    return settings.copy(packages = settings.packages - packages, customDns = dns, multiServer = multi,
        history = if (multi != null) settings.history?.let { MultiServerRoutingHistory(it.single, multi) } else settings.history)
}
internal fun clearDnsApps(settings: AppRoutingSettings, packages: Set<String>) =
    settings.copy(customDns = settings.customDns.copy(appServers = settings.customDns.appServers - packages))
