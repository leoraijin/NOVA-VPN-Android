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

data class CustomDnsSettings(val servers: List<CustomDnsServer> = emptyList(), val selectedId: String? = null) {
    val selected: CustomDnsServer? get() = servers.singleOrNull { it.id == selectedId }
    fun validate() {
        require(servers.size <= 20 && servers.map { it.id }.distinct().size == servers.size)
        servers.forEach { it.validate() }
        require(selectedId == null || servers.any { it.id == selectedId }) { "Выберите существующий DNS" }
    }
    fun toJson() = JSONObject().put("servers", JSONArray().apply { servers.forEach { put(it.toJson()) } })
        .put("selectedId", selectedId ?: JSONObject.NULL)
    companion object {
        fun fromJson(json: JSONObject): CustomDnsSettings = CustomDnsSettings(json.getJSONArray("servers").let { array ->
            require(array.length() <= 20)
            List(array.length()) { CustomDnsServer.fromJson(array.getJSONObject(it)) }
        }, if (json.isNull("selectedId")) null else json.getString("selectedId")).also { it.validate() }
    }
}

/** Applies split DNS to the selected sites, including resolving FakeIP names at dial time. */
internal object CustomDnsConfig {
    fun apply(config: JSONObject, settings: CustomDnsSettings, ownPackage: String?): JSONObject {
        settings.validate()
        val server = settings.selected?.takeIf { it.domains.isNotEmpty() } ?: return config
        val uri = server.endpoint()
        val route = config.getJSONObject("route")
        val outbounds = config.getJSONArray("outbounds")
        val direct = "nova-custom-dns-direct"
        val tag = "nova-custom-dns-resolver"
        require((0 until outbounds.length()).none { outbounds.getJSONObject(it).optString("tag") == direct }) { "Занят тег пользовательского DNS" }
        outbounds.put(JSONObject().put("type", "direct").put("tag", direct))
        val dns = config.getJSONObject("dns")
        val dnsServers = dns.getJSONArray("servers")
        require((0 until dnsServers.length()).none { dnsServers.getJSONObject(it).optString("tag") == tag }) { "Занят тег пользовательского DNS" }
        // DNS dial fields without detour use the protected direct connection.
        val resolver = JSONObject().put("type", uri.scheme).put("tag", tag).put("server", uri.host.removeSurrounding("[", "]"))
        if (uri.port != -1) resolver.put("server_port", uri.port)
        if (uri.scheme in setOf("https", "tls")) resolver.put("tls", JSONObject().put("enabled", true).put("server_name", uri.host.removeSurrounding("[", "]")))
        if (uri.scheme == "https") resolver.put("path", (uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/dns-query") + (uri.rawQuery?.let { "?$it" } ?: ""))
        route.opt("default_domain_resolver")?.let { resolver.put("domain_resolver", it) }
        dns.getJSONArray("servers").put(resolver)
        dns.put("reverse_mapping", true)
        fun match() = JSONObject().put("domain", JSONArray(server.domains)).put("domain_suffix", JSONArray(server.domains.map { ".$it" }))
        val dnsRules = dns.optJSONArray("rules") ?: JSONArray()
        val existing = route.optJSONArray("rules") ?: JSONArray()
        // Preserve block, sniff and DNS hijack decisions ahead of the site overrides.
        val rules = JSONArray()
        val tail = JSONArray()
        for (i in 0 until existing.length()) {
            val rule = existing.getJSONObject(i)
            val protocolRule = rule.optString("action") in setOf("sniff", "hijack-dns")
            val explicitBlock = rule.optString("action") == "reject" &&
                (rule.has("domain") || rule.has("domain_suffix") || rule.has("ip_version") ||
                    rule.optJSONArray("package_name")?.optString(0) == SharedUidExitGuard.CONFLICT_PACKAGE)
            if (protocolRule || explicitBlock) rules.put(rule) else tail.put(rule)
        }
        rules.put(match().put("action", "resolve").put("server", tag).put("strategy",
            if ((0 until existing.length()).any { existing.getJSONObject(it).optInt("ip_version") == 6 && existing.getJSONObject(it).optString("action") == "reject" } || dns.optString("strategy") == "ipv4_only") "ipv4_only" else "prefer_ipv4"))
        rules.put(match().put("action", "route").put("outbound", direct))
        for (i in 0 until tail.length()) rules.put(tail.getJSONObject(i))
        route.put("rules", rules)
        val priorities = JSONArray()
        for (i in 0 until dnsRules.length()) if (dnsRules.getJSONObject(i).optString("action") == "reject") priorities.put(dnsRules.getJSONObject(i))
        priorities.put(match().put("action", "route").put("server", tag))
        for (i in 0 until dnsRules.length()) if (dnsRules.getJSONObject(i).optString("action") != "reject") priorities.put(dnsRules.getJSONObject(i))
        dns.put("rules", priorities)
        config.getJSONArray("inbounds").let { inbounds ->
            for (i in 0 until inbounds.length()) if (inbounds.getJSONObject(i).optString("type") == "tun") {
                val tun = inbounds.getJSONObject(i)
                tun.remove("include_package")
                tun.put("exclude_package", JSONArray(listOfNotNull(ownPackage)))
            }
        }
        return config
    }
}
