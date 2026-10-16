package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Android adaptation for the native sing-box parser based on core commit 51a811.
 *
 * This is not native validation: the caller must validate [Prepared.config] with the core
 * before opening the VPN. Unknown fields are deliberately retained for that validation.
 *
 * Supported plan: one generated TUN, VLESS/Trojan plus direct/block/DNS outbounds,
 * and either generated application routing or native-supported site routing. The
 * Android Builder must apply only the nonempty application list in [Prepared].
 * Package and site/custom rules cannot be combined without changing their priority.
 * Include mode with no selected apps moves the terminal direct catch-all to route.final;
 * an allow-list containing only this VPN app is rejected (an empty Builder list means all apps).
 *
 * Limitations: custom TUN layouts and system DNS are rejected. Custom resolver fields
 * and unsupported DNS rule options remain for native rejection. Managed sites use
 * site-specific fake-IP DNS pairs; custom site policies and existing fake-IP tables
 * are rejected. This requires the core's per-rule DNS strategy support. There is no
 * real-IP reverse mapping: cached real addresses, application DoH and literal IPs
 * cannot recover the site name through this mechanism. DNS strategy is the only
 * available IPv6 policy signal in this API; full JSON does not encode the UI switch.
 * Both generated TUN addresses remain
 * present so the service can capture IPv6 rather than leak it outside the VPN.
 *
 * Native external-FD integration must resolve proxy/DoH hostnames through the explicit
 * nova-bootstrap-dns server (protected UDP/direct), not the final remote DNS server:
 * resolving a proxy through DNS detoured to that proxy would recurse at startup.
 */
object RealityConfigFactory {
    data class Prepared(
        val config: String,
        val includedPackages: List<String>,
        val excludedPackages: List<String>,
        val ipv6Enabled: Boolean
    )

    fun prepare(config: String, ownPackage: String): Prepared = try {
        require(PACKAGE.matches(ownPackage)) { "Некорректные параметры VPN-приложения." }
        prepareJson(JSONObject(config), ownPackage)
    } catch (_: JSONException) {
        // org.json errors can include input fragments, credentials and server names.
        throw IllegalArgumentException("Некорректная конфигурация Reality Core.")
    }

    private fun prepareJson(root: JSONObject, ownPackage: String): Prepared {
        val outbounds = objects(root.getJSONArray("outbounds"))
        require(outbounds.isNotEmpty() && outbounds.all { it.opt("type") in PROTOCOLS }) {
            "Протокол подключения не поддерживается Reality Core. Выберите sing-box."
        }
        outbounds.forEach {
            require(!it.optBoolean("allow_insecure") && it.optJSONObject("tls")?.optBoolean("insecure") != true) {
                "Reality Core требует проверки подлинности TLS. Выберите корректный профиль."
            }
            removeBootstrapResolver(it, "domain_resolver")
        }
        val route = root.getJSONObject("route")
        removeBootstrapResolver(route, "default_domain_resolver")

        val inbounds = objects(root.getJSONArray("inbounds"))
        val tuns = inbounds.filter { it.opt("type") == "tun" }
        require(tuns.size == 1) { "Настройки VPN-интерфейса не поддерживаются Reality Core." }
        prepareTun(tuns.single(), ownPackage)

        val dns = root.getJSONObject("dns")
        prepareDns(dns, outbounds)
        val (included, excluded) = preparePackages(route, dns, outbounds, ownPackage)
        prepareSiteDns(route, dns, outbounds)
        normalizeTerminalDirectRoute(route, dns, outbounds)
        // SingBoxConfigFactory always emits a DNS strategy for managed DNS. Full JSON
        // cannot convey the UI's IPv6 switch; only an explicit dual-stack strategy opts in.
        val ipv6 = dns.opt("strategy") in setOf("prefer_ipv4", "prefer_ipv6", "ipv6_only")
        return Prepared(root.toString(), included, excluded, ipv6)
    }

    private fun normalizeTerminalDirectRoute(route: JSONObject, dns: JSONObject, outbounds: List<JSONObject>) {
        val rules = route.optJSONArray("rules") ?: return
        val last = rules.optJSONObject(rules.length() - 1) ?: return
        if (keys(last) != setOf("action", "outbound") || last.opt("action") != "route") return
        val tag = last.opt("outbound") as? String ?: return
        if (tag.isBlank()) return
        val target = outbounds.singleOrNull { it.opt("tag") == tag } ?: return
        if (target.opt("type") != "direct" || keys(target) != setOf("type", "tag")) return
        if (route.opt("final") != tag) {
            // Native DNS inherits route.final when detour is omitted. Changing it
            // must never silently move an upstream from the proxy to direct.
            require(objects(dns.getJSONArray("servers")).all { server ->
                val fake = if (server.has("address")) server.opt("address") == "fakeip"
                    else server.opt("type") == "fakeip"
                fake || (server.opt("detour") as? String)?.isNotBlank() == true
            }) { "Невозможно безопасно изменить маршрут DNS без явно заданного подключения. Выберите sing-box." }
        }
        // Native rejects unconditional rules. Only a terminal plain direct route
        // is equivalent to final; earlier explicit site/probe exceptions stay intact.
        rules.remove(rules.length() - 1)
        route.put("final", tag)
    }

    /** Pair fake answers for applications with real resolution for native outbound lookup.
     * Dns.policy(name, false) skips each fake rule and selects its unchanged successor.
     * Router.route restores either fake address family before matching the site routes.
     */
    private fun prepareSiteDns(route: JSONObject, dns: JSONObject, outbounds: List<JSONObject>) {
        val routes = route.optJSONArray("rules")?.let(::objects).orEmpty()
        val originals = dns.optJSONArray("rules")?.let(::objects).orEmpty()
        val siteKeys = setOf("domain", "domain_suffix", "domain_keyword", "domain_regex", "geosite", "rule_set")
        if (routes.none { keys(it).any(siteKeys::contains) } &&
            originals.none { keys(it).any(siteKeys::contains) }) return

        val error = "Правила сайтов не поддерживаются Reality Core. Выберите sing-box."
        val servers = objects(dns.getJSONArray("servers"))
        require(!dns.has("fakeip") && servers.none {
            it.opt("tag") == SITE_FAKEIP || it.opt("type") == "fakeip" || it.opt("address") == "fakeip"
        }) { "Конфликт настроек DNS для правил сайтов. Выберите sing-box." }
        require(servers.map { it.opt("tag") }.distinct().size == servers.size &&
            servers.any { it.opt("tag") == dns.opt("final") } && originals.isNotEmpty()) { error }

        // Only the generator's ordered route plan is safe to certify: optional probe,
        // DNS hijack, sniff, exact/suffix site pairs, optional direct catch-all.
        val plan = routes.toMutableList()
        if (plan.firstOrNull()?.let { isProbe(it, route.opt("final")) } == true) plan.removeAt(0)
        require(plan.size >= 2 && isDnsHijack(plan[0]) &&
            keys(plan[1]) == setOf("action") && plan[1].opt("action") == "sniff") { error }
        val sitePlan = plan.drop(2).toMutableList()
        val directTags = outbounds.filter { it.opt("type") == "direct" && !it.has("detour") }
            .map { it.opt("tag") }.filterIsInstance<String>().toSet()
        if (sitePlan.lastOrNull()?.let {
                keys(it) == setOf("action", "outbound") && it.opt("action") == "route" &&
                    it.opt("outbound") in directTags
            } == true) sitePlan.removeAt(sitePlan.lastIndex)
        require(sitePlan.size == originals.size * 2) { error }

        val domains = mutableSetOf<String>()
        val paired = JSONArray()
        originals.forEachIndexed { index, original ->
            val allowed = setOf("domain", "domain_suffix", "action", "server")
            require(keys(original) == allowed || keys(original) == allowed + "strategy") { error }
            val names = strings(original.getJSONArray("domain"))
            require(names.size == 1 && SiteDomain.normalize(names.single()) == names.single() &&
                domains.add(names.single()) && original.opt("action") == "route" &&
                (!original.has("strategy") || original.opt("strategy") == "prefer_ipv4") &&
                strings(original.getJSONArray("domain_suffix")) == listOf(".${names.single()}")) { error }
            val server = servers.singleOrNull { it.opt("tag") == original.opt("server") }
            require(server != null && (original.opt("server") == BOOTSTRAP ||
                original.opt("server") == dns.opt("final"))) { error }
            val exact = sitePlan[index * 2]
            val suffix = sitePlan[index * 2 + 1]
            require(keys(exact) == setOf("domain", "action", "outbound") &&
                keys(suffix) == setOf("domain_suffix", "action", "outbound") &&
                exact.opt("action") == "route" && suffix.opt("action") == "route" &&
                exact.opt("outbound") == suffix.opt("outbound") &&
                strings(exact.getJSONArray("domain")) == names &&
                strings(suffix.getJSONArray("domain_suffix")) == listOf(".${names.single()}")) { error }
            val target = exact.opt("outbound")
            require(if (original.opt("server") == BOOTSTRAP) {
                target in directTags && server.opt("detour") == target
            } else {
                outbounds.any { it.opt("tag") == target && it.opt("type") in setOf("vless", "trojan") } &&
                    server.opt("detour") == target
            }) { error }

            // Copy the complete validated condition and strategy, changing only server.
            paired.put(JSONObject(original.toString()).put("server", SITE_FAKEIP))
            paired.put(original)
        }
        dns.getJSONArray("servers").put(JSONObject().put("type", "fakeip").put("tag", SITE_FAKEIP)
            .put("inet4_range", "198.18.0.0/15").put("inet6_range", "fc00::/18"))
        dns.put("rules", paired)
        // dns.final and every route remain unchanged: non-site names get ordinary DNS.
    }

    private fun prepareTun(tun: JSONObject, ownPackage: String) {
        require(keys(tun).all { it in TUN_KEYS } && tun.opt("tag") == "nova-tun" &&
            strings(tun.getJSONArray("address")) == listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126") &&
            tun.opt("mtu") == 1500) { "Настройки VPN-интерфейса не поддерживаются Reality Core." }
        val generated = mapOf("interface_name" to "NOVA", "auto_route" to true,
            "strict_route" to true, "stack" to "gvisor")
        generated.forEach { (key, value) ->
            require(!tun.has(key) || tun.opt(key) == value) { "Настройки VPN-интерфейса не поддерживаются Reality Core." }
            tun.remove(key)
        }
        if (tun.has("exclude_package")) {
            require(strings(tun.getJSONArray("exclude_package")) == listOf(ownPackage)) {
                "Правила приложений VPN-интерфейса не поддерживаются Reality Core."
            }
            tun.remove("exclude_package")
        }
    }

    private fun removeBootstrapResolver(obj: JSONObject, key: String) {
        val resolver = obj.optJSONObject(key) ?: return
        if (keys(resolver) == setOf("server", "strategy") &&
            resolver.opt("server") == BOOTSTRAP && resolver.opt("strategy") == "prefer_ipv4") {
            obj.remove(key)
        }
    }

    private fun prepareDns(dns: JSONObject, outbounds: List<JSONObject>) {
        val servers = objects(dns.getJSONArray("servers"))
        servers.forEach { server ->
            if (server.opt("tag") == BOOTSTRAP && server.opt("type") == "local" &&
                keys(server) == setOf("type", "tag")) {
                // A literal UDP destination through a real direct outbound reaches the
                // core's net_protect::udp_bind, never Android's looping system resolver.
                val direct = outbounds.firstOrNull {
                    it.opt("type") == "direct" && keys(it) == setOf("type", "tag") &&
                        it.opt("tag") is String && it.getString("tag").isNotBlank() &&
                        outbounds.count { other -> other.opt("tag") == it.opt("tag") } == 1
                } ?: throw IllegalArgumentException("Для запуска DNS требуется прямое подключение без дополнительных настроек.")
                server.put("type", "udp").put("server", "1.1.1.1").put("server_port", 53)
                    .put("detour", direct.getString("tag"))
            } else {
                require(server.opt("type") != "local" && server.opt("address") != "local") {
                    "Системный DNS не поддерживается Reality Core в режиме Android VPN."
                }
            }
            val tls = server.optJSONObject("tls")
            if (server.opt("type") == "https" && server.opt("tag") == "remote-dns" &&
                server.opt("server") == "1.1.1.1" && server.opt("path") == "/dns-query" &&
                server.opt("detour") is String &&
                keys(server) == setOf("type", "tag", "server", "path", "tls", "detour") &&
                tls != null && keys(tls) == setOf("enabled", "server_name") &&
                tls.opt("enabled") == true && tls.opt("server_name") == "cloudflare-dns.com") {
                // Native typed DNS ignores server_name. The legacy URL preserves the
                // certificate identity and SNI; hostname bootstrap is protected by core.
                listOf("type", "server", "path", "tls").forEach(server::remove)
                server.put("address", "https://cloudflare-dns.com/dns-query")
            } else if (tls != null) {
                require((!tls.has("server_name") || tls.opt("server_name") == server.opt("server")) &&
                    (!tls.has("enabled") || tls.opt("enabled") == true)) {
                    "Настройки защиты DNS не поддерживаются Reality Core. Выберите sing-box."
                }
            }
        }
    }

    private fun preparePackages(
        route: JSONObject, dns: JSONObject, outbounds: List<JSONObject>, ownPackage: String
    ): Pair<List<String>, List<String>> {
        val rules = route.optJSONArray("rules")?.let(::objects).orEmpty()
        val packages = rules.filter { it.has("package_name") }
        if (packages.isEmpty()) {
            require(!containsInvert(route) && !containsInvert(dns)) { "Инвертированные правила не поддерживаются Reality Core." }
            return emptyList<String>() to listOf(ownPackage)
        }
        // Android app selection happens before any native routing. Only the generated
        // DNS hijack and the optional local health probe may accompany package mode.
        val others = rules.filterNot { it.has("package_name") }
        require(others.all { isDnsHijack(it) || isProbe(it, route.opt("final")) } &&
            (dns.optJSONArray("rules")?.length() ?: 0) == 0 && !containsInvert(dns) &&
            rules.takeLast(packages.size) == packages) {
            "Reality Core не поддерживает совместные правила приложений и сайтов или другие дополнительные правила. Выберите sing-box."
        }
        val names = strings(packages.first().getJSONArray("package_name"))
        require(names.isNotEmpty() && names == names.distinct().sorted() && names.all(PACKAGE::matches)) {
            "Список приложений не поддерживается Reality Core."
        }
        val directTags = outbounds.filter { it.opt("type") == "direct" && !it.has("detour") }
            .map { it.opt("tag") }.filterIsInstance<String>().toSet()
        val final = route.opt("final")
        val included: List<String>
        val excluded: List<String>
        when {
            packages.size == 1 && isPackageRule(packages[0], names, false) &&
                packages[0].opt("outbound") in directTags -> {
                included = emptyList()
                excluded = (names + ownPackage).distinct().sorted()
            }
            packages.size == 2 && isPackageRule(packages[0], names, false) &&
                isPackageRule(packages[1], names, true) && packages[0].opt("outbound") == final &&
                outbounds.any { it.opt("tag") == final && it.opt("type") in setOf("vless", "trojan") } &&
                packages[1].opt("outbound") in directTags -> {
                included = names.filterNot { it == ownPackage }
                // An empty Builder allow-list captures every app, the opposite policy.
                require(included.isNotEmpty()) { "Выберите для VPN хотя бы одно приложение, кроме самого VPN-приложения." }
                excluded = emptyList()
            }
            else -> throw IllegalArgumentException("Правила приложений не поддерживаются Reality Core. Выберите sing-box.")
        }
        route.put("rules", JSONArray().apply { others.forEach { put(it) } })
        return included to excluded
    }

    private fun isPackageRule(rule: JSONObject, names: List<String>, invert: Boolean): Boolean =
        keys(rule) == (if (invert) PACKAGE_RULE_KEYS + "invert" else PACKAGE_RULE_KEYS) &&
            rule.opt("action") == "route" && rule.opt("outbound") is String &&
            strings(rule.getJSONArray("package_name")) == names &&
            (!invert || rule.opt("invert") == true)

    private fun isDnsHijack(rule: JSONObject): Boolean =
        keys(rule) == setOf("protocol", "action") && rule.opt("action") == "hijack-dns" &&
            rule.optJSONArray("protocol")?.let(::strings) == listOf("dns")

    private fun isProbe(rule: JSONObject, final: Any?): Boolean =
        keys(rule) == setOf("inbound", "action", "outbound") && rule.opt("action") == "route" &&
            rule.opt("outbound") == final &&
            rule.optJSONArray("inbound")?.let(::strings) == listOf("nova-local-health")

    private fun containsInvert(value: Any?): Boolean = when (value) {
        is JSONObject -> value.has("invert") || keys(value).any { containsInvert(value.opt(it)) }
        is JSONArray -> (0 until value.length()).any { containsInvert(value.opt(it)) }
        else -> false
    }

    private fun keys(obj: JSONObject): Set<String> = obj.keys().asSequence().toSet()
    private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map(array::getJSONObject)
    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map {
        require(array.get(it) is String) { "Некорректный список в конфигурации Reality Core." }
        array.getString(it)
    }

    private const val BOOTSTRAP = "nova-bootstrap-dns"
    private const val SITE_FAKEIP = "nova-site-fakeip"
    private val PROTOCOLS = setOf("vless", "trojan", "direct", "block", "dns")
    private val PACKAGE = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val PACKAGE_RULE_KEYS = setOf("package_name", "action", "outbound")
    private val TUN_KEYS = setOf("type", "tag", "address", "mtu", "interface_name", "auto_route",
        "strict_route", "stack", "exclude_package")
}
