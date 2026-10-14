package com.nova.vpn.android.data

import android.util.Log
import com.nova.vpn.android.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Produces the minimum full-tunnel sing-box config for a single selected profile. */
object SingBoxConfigFactory {
    fun build(profile: ImportedProfile, routing: AppRoutingSettings = AppRoutingSettings(), ownPackage: String? = null): String {
        val config = profile.fullConfigJson?.let {
            ensureVpnInbound(JSONObject(it), routing, ownPackage)
        } ?: run {
            val outbound = JSONObject(profile.outboundJson).put("tag", PROXY_TAG)
            ensureVpnInbound(defaultConfig(outbound), routing, ownPackage)
        }
        if (BuildConfig.DEBUG) logSafeOutboundSummary(config)
        return config.toString()
    }

    /** Report only non-secret transport metadata; never log credentials or Reality keys. */
    private fun logSafeOutboundSummary(config: JSONObject) {
        val outbound = config.optJSONArray("outbounds")?.let { outbounds ->
            (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
                .firstOrNull { it.optString("tag") == PROXY_TAG }
        } ?: return
        val tls = outbound.optJSONObject("tls")
        val reality = tls?.optJSONObject("reality")
        val transport = outbound.optJSONObject("transport")
        runCatching { Log.i("NovaConfig", "proxy metadata type=${outbound.optString("type")}, " +
            "tls=${tls?.optBoolean("enabled") == true}, sniPresent=${!tls?.optString("server_name").isNullOrBlank()}, " +
            "utls=${tls?.optJSONObject("utls")?.optBoolean("enabled") == true}, " +
            "reality=${reality?.optBoolean("enabled") == true}, " +
            "realityKeyPresent=${!reality?.optString("public_key").isNullOrBlank()}, " +
            "shortIdLength=${reality?.optString("short_id").orEmpty().length}, " +
            "flowPresent=${!outbound.optString("flow").isNullOrBlank()}, " +
            "transport=${transport?.optString("type") ?: "tcp"}") }
    }

    private fun defaultConfig(outbound: JSONObject): JSONObject {
        val config = JSONObject()
            .put("log", JSONObject().put("level", if (BuildConfig.DEBUG) "debug" else "warn"))
            .put("dns", defaultDns(PROXY_TAG))
            .put("inbounds", JSONArray().put(JSONObject()
                .put("type", "tun")
                .put("tag", "nova-tun")
                .put("interface_name", "NOVA")
                .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
                .put("mtu", 1500)
                .put("auto_route", true)
                .put("strict_route", true)
                .put("stack", "gvisor")))
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("type", "direct").put("tag", DIRECT_TAG)))
            .put("route", JSONObject()
                .put("auto_detect_interface", true)
                .put("final", PROXY_TAG))
        return config
    }

    private fun ensureVpnInbound(config: JSONObject, routing: AppRoutingSettings, ownPackage: String?): JSONObject {
        val outbounds = config.optJSONArray("outbounds")
            ?: throw IllegalArgumentException("В sing-box JSON отсутствует список outbounds")
        require(outbounds.length() > 0) { "В sing-box JSON нет исходящего подключения" }

        // Saved entries contain the outbound JSON from their original import.
        // Parser fixes alone therefore do not repair existing profiles.
        var realityDefaults = 0
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            val tls = outbound.optJSONObject("tls") ?: continue
            if (tls.optJSONObject("reality")?.optBoolean("enabled") != true) continue
            var changed = false
            if (!tls.has("utls")) {
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome"))
                changed = true
            } else {
                tls.optJSONObject("utls")?.takeIf { it.optBoolean("enabled") && it.optString("fingerprint").isBlank() }?.let {
                    it.put("fingerprint", "chrome")
                    changed = true
                }
            }
            if (tls.optString("server_name").isBlank() && outbound.optString("server").isNotBlank()) {
                tls.put("server_name", outbound.getString("server").removeSurrounding("[", "]"))
                changed = true
            }
            if (changed) realityDefaults++
        }
        if (BuildConfig.DEBUG) runCatching { Log.i("NovaConfig", "runtimeRealityDefaults=$realityDefaults") }

        val inbounds = config.optJSONArray("inbounds") ?: JSONArray()
        val hasTun = (0 until inbounds.length()).any { index ->
            inbounds.optJSONObject(index)?.optString("type") == "tun"
        }
        var novaTun: JSONObject? = null
        if (!hasTun) {
            novaTun = JSONObject()
                .put("type", "tun")
                .put("tag", "nova-tun")
                .put("interface_name", "NOVA")
                .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
                .put("mtu", 1500)
                .put("auto_route", true)
                .put("strict_route", true)
                .put("stack", "gvisor")
            inbounds.put(novaTun)
        } else {
            novaTun = (0 until inbounds.length()).mapNotNull(inbounds::optJSONObject)
                .firstOrNull { it.optString("type") == "tun" }
        }
        novaTun?.let {
            it.put("mtu", 1500)
            it.put("auto_route", true)
            // Use the Android-tested userspace stack even when an imported desktop
            // config carries a system/mixed stack that can drop TCP on Android 15.
            it.put("stack", "gvisor")
            if (!it.has("address")) it.put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
            configureApps(it, ownPackage)
        }
        config.put("inbounds", inbounds)

        val route = config.optJSONObject("route") ?: JSONObject()
        if (BuildConfig.DEBUG) {
            config.optJSONObject("log")?.put("level", "debug")
        }
        route.put("auto_detect_interface", true)
        if (route.optString("final").isBlank()) {
            val defaultOutbound = (0 until outbounds.length())
                .mapNotNull(outbounds::optJSONObject)
                .firstOrNull { it.optString("type") !in setOf("direct", "block", "dns") }
                ?: throw IllegalArgumentException("Не найден прокси-outbound для маршрута по умолчанию")
            val defaultTag = defaultOutbound.optString("tag").takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("У основного outbounds отсутствует tag")
            route.put("final", defaultTag)
        }
        val finalTag = route.optString("final")
        require((0 until outbounds.length()).any { index ->
            outbounds.optJSONObject(index)?.optString("tag") == finalTag
        }) { "Маршрут по умолчанию ссылается на отсутствующий outbound: $finalTag" }

        // Resolve the proxy server through the physical/bootstrap DNS path. If its
        // hostname is resolved by the final remote DNS server, that DNS request
        // itself needs the proxy and creates a startup deadlock. The Windows client
        // applies this explicitly to its proxy outbound as well.
        val finalOutbound = (0 until outbounds.length())
            .mapNotNull(outbounds::optJSONObject)
            .firstOrNull { it.optString("tag") == finalTag }
        finalOutbound?.let { outbound ->
            val server = outbound.optString("server").trim().removeSurrounding("[", "]")
            if (server.isNotBlank() && !isIpLiteral(server) && outbound.optString("domain_resolver").isBlank()) {
                outbound.put("domain_resolver", BOOTSTRAP_DNS_TAG)
            }
        }

        val directTag = ensureDirectOutbound(outbounds)
        configureRoutingRules(route, routing, finalTag, directTag)
        config.put("route", route)
        if (!config.has("dns")) {
            config.put("dns", defaultDns(finalTag))
        }
        configureDns(config.getJSONObject("dns"), routing)
        route.put("default_domain_resolver", BOOTSTRAP_DNS_TAG)
        outbounds.getJSONObject((0 until outbounds.length()).first {
            outbounds.getJSONObject(it).optString("tag") == directTag
        }).put("domain_resolver", BOOTSTRAP_DNS_TAG)
        return config
    }

    private fun isIpLiteral(host: String): Boolean =
        host.contains(':') || IPV4_LITERAL.matches(host)

    /** Keep user app policy inside sing-box so earlier site rules can override browser routing. */
    private fun configureApps(tun: JSONObject, ownPackage: String?) {
        tun.remove("include_package")
        tun.remove("exclude_package")
        if (!ownPackage.isNullOrBlank()) {
            tun.put("exclude_package", JSONArray().put(ownPackage))
        }
    }

    private fun configureRoutingRules(route: JSONObject, routing: AppRoutingSettings, vpnTag: String, directTag: String) {
        val existingRules = route.optJSONArray("rules") ?: JSONArray()
        val prioritized = JSONArray()
        // DNS is a protocol decision, not an application route. Sending the
        // virtual DNS address to the proxy creates a connected-but-offline VPN.
        prioritized.put(JSONObject().put("protocol", JSONArray().put("dns")).put("action", "hijack-dns"))
        if (routing.sites.isNotEmpty()) {
            // Sniff obtains TLS SNI / HTTP Host when DNS metadata is unavailable.
            prioritized.put(JSONObject().put("action", "sniff"))
            routing.sites.sortedWith(compareBy<SiteRoutingRule> { it.target != SiteRoutingTarget.DIRECT }
                .thenByDescending { it.domain.length }).distinctBy { it.domain }.forEach { site ->
                val domain = SiteDomain.normalize(site.domain) ?: return@forEach
                val outbound = if (site.target == SiteRoutingTarget.DIRECT) directTag else vpnTag
                prioritized.put(JSONObject()
                    .put("domain", JSONArray().put(domain))
                    .put("action", "route")
                    .put("outbound", outbound))
                prioritized.put(JSONObject()
                    .put("domain_suffix", JSONArray().put(".$domain"))
                    .put("action", "route")
                    .put("outbound", outbound))
            }
        }

        val selectedPackages = routing.packages.filter(String::isNotBlank).distinct().sorted()
        when (routing.mode) {
            AppRoutingMode.EXCLUDE_SELECTED -> if (selectedPackages.isNotEmpty()) {
                prioritized.put(packageRule(selectedPackages, directTag))
            }
            AppRoutingMode.INCLUDE_SELECTED -> {
                if (selectedPackages.isNotEmpty()) {
                    prioritized.put(packageRule(selectedPackages, vpnTag))
                    prioritized.put(packageRule(selectedPackages, directTag).put("invert", true))
                } else {
                    // With no selected apps, all app traffic should bypass the VPN.
                    prioritized.put(JSONObject().put("action", "route").put("outbound", directTag))
                }
            }
        }
        for (index in 0 until existingRules.length()) {
            existingRules.optJSONObject(index)?.let(prioritized::put)
        }
        if (prioritized.length() > 0) route.put("rules", prioritized)
    }

    private fun packageRule(packages: List<String>, outbound: String): JSONObject = JSONObject()
        .put("package_name", JSONArray().apply { packages.forEach(::put) })
        .put("action", "route")
        .put("outbound", outbound)

    private fun ensureDirectOutbound(outbounds: JSONArray): String {
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("type") == "direct" && outbound.optString("detour").isBlank()) {
                return outbound.optString("tag").takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("У direct-outbound отсутствует tag")
            }
        }

        var tag = DIRECT_TAG
        var suffix = 1
        while ((0 until outbounds.length()).any { index ->
                outbounds.optJSONObject(index)?.optString("tag") == tag
            }) {
            tag = "$DIRECT_TAG-$suffix"
            suffix++
        }
        outbounds.put(JSONObject().put("type", "direct").put("tag", tag))
        return tag
    }

    private fun defaultDns(detourTag: String): JSONObject = JSONObject()
        .put("servers", JSONArray().put(JSONObject()
            .put("type", "https")
            .put("tag", "remote-dns")
            .put("server", "1.1.1.1")
            .put("path", "/dns-query")
            .put("tls", JSONObject().put("enabled", true).put("server_name", "cloudflare-dns.com"))
            .put("detour", detourTag)))
        .put("final", "remote-dns")
        .put("strategy", "prefer_ipv4")

    private fun configureDns(dns: JSONObject, routing: AppRoutingSettings) {
        val importedServers = dns.optJSONArray("servers") ?: JSONArray()
        val servers = JSONArray()
        for (index in 0 until importedServers.length()) {
            importedServers.optJSONObject(index)?.takeUnless {
                it.optString("tag") == BOOTSTRAP_DNS_TAG
            }?.let(servers::put)
        }
        servers.put(JSONObject().put("type", "local").put("tag", BOOTSTRAP_DNS_TAG))
        dns.put("servers", servers).put("reverse_mapping", true).put("independent_cache", true)
        val remoteTag = dns.optString("final").takeIf { it.isNotBlank() && it != BOOTSTRAP_DNS_TAG }
            ?: (0 until servers.length()).mapNotNull(servers::optJSONObject)
                .firstOrNull { it.optString("tag") != BOOTSTRAP_DNS_TAG }?.optString("tag")
        val existing = dns.optJSONArray("rules") ?: JSONArray()
        val prioritized = JSONArray()
        routing.sites.sortedWith(compareBy<SiteRoutingRule> { it.target != SiteRoutingTarget.DIRECT }
            .thenByDescending { it.domain.length }).distinctBy { it.domain }.forEach { site ->
            val domain = SiteDomain.normalize(site.domain) ?: return@forEach
            val server = if (site.target == SiteRoutingTarget.DIRECT) BOOTSTRAP_DNS_TAG else remoteTag
            if (!server.isNullOrBlank()) prioritized.put(JSONObject()
                .put("domain", JSONArray().put(domain)).put("domain_suffix", JSONArray().put(".$domain"))
                .put("action", "route").put("server", server))
        }
        for (index in 0 until existing.length()) existing.optJSONObject(index)?.let(prioritized::put)
        dns.put("rules", prioritized)
    }

    private const val PROXY_TAG = "proxy"
    private const val DIRECT_TAG = "direct"
    private val IPV4_LITERAL = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
    private const val BOOTSTRAP_DNS_TAG = "nova-bootstrap-dns"
}
