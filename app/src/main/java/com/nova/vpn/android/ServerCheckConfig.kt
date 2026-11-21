package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.SingBoxConfigFactory
import com.nova.vpn.android.data.RealityConfigFactory
import com.nova.vpn.android.data.VpnEngine
import org.json.JSONArray
import org.json.JSONObject

/** A temporary proxy-only configuration. Never creates a TUN or runs a URL-test group. */
internal object ServerCheckConfig {
    class RecoveryCheck(val config: String, val resolver: String?)

    /** Use the running engine's adapted settings. Keep only the assigned proxy and its DNS graph. */
    fun forRecovery(runtimeConfig: String, tag: String, resolver: String? = null): RecoveryCheck? {
        val runtime = JSONObject(runtimeConfig)
        val activeRoute = runtime.getJSONObject("route")
        val outbounds = runtime.getJSONArray("outbounds").objects()
        val exit = outbounds.singleOrNull {
            it.optString("tag") == tag && !it.has("detour") &&
                AndroidTrafficProbe.supports(it.optString("type"), managed = resolver != null)
        } ?: return null
        val direct = outbounds.filter { it.optString("type") == "direct" && !it.has("detour") }
        val dns = runtime.optJSONObject("dns")
        val root = JSONObject()
            .put("log", JSONObject().put("disabled", true))
            .put("inbounds", JSONArray())
            .put("outbounds", JSONArray(listOf(exit) + direct))
        if (dns != null) {
            val keptTags = (listOf(exit) + direct).map { it.getString("tag") }.toSet()
            val servers = dns.getJSONArray("servers").objects().filter {
                it.optString("type") != "fakeip" && it.optString("address") != "fakeip" &&
                    it.optString("detour").let { detour -> detour.isEmpty() || detour in keptTags }
            }
            val final = resolver ?: dns.optString("final")
            if (servers.isEmpty() || final.isNotEmpty() && servers.none { it.optString("tag") == final }) return null
            dns.remove("rules")
            dns.remove("fakeip")
            dns.put("servers", JSONArray(servers))
            if (final.isNotEmpty()) dns.put("final", final)
            root.put("dns", dns)
        } else if (resolver != null) return null
        root.put("route", JSONObject().put("auto_detect_interface", true).put("final", tag).apply {
            if (activeRoute.has("default_domain_resolver")) put("default_domain_resolver", activeRoute.get("default_domain_resolver"))
        })
        return root.toString().takeIf { it.length <= 250_000 }?.let { RecoveryCheck(it, resolver) }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    fun build(profile: ImportedProfile, engine: VpnEngine = VpnEngine.SING_BOX, ownPackage: String = "com.nova.vpn.android"): String? {
        // Full documents can contain selectors, rulesets and providers with their own timers.
        if (profile.fullConfigJson != null) return null
        val generated = SingBoxConfigFactory.build(profile)
        // Run the same adaptation as the VPN before removing its TUN. The probe
        // must not certify a profile with a different engine's parser or DNS schema.
        val config = JSONObject(if (engine == VpnEngine.REALITY_CORE)
            RealityConfigFactory.prepare(generated, ownPackage).config else generated)
        val outbounds = config.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
            .firstOrNull { it.optString("tag") == "proxy" } ?: return null
        if (proxy.optString("type") !in setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic")) return null
        config.put("inbounds", JSONArray())
        config.put("log", JSONObject().put("disabled", true))
        config.remove("experimental")
        config.remove("endpoints")
        config.put("route", JSONObject().put("auto_detect_interface", true).put("final", "proxy"))
        return config.toString().takeIf { it.length <= 250_000 }
    }
}
