package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.SingBoxConfigFactory
import org.json.JSONArray
import org.json.JSONObject

/** A temporary proxy-only configuration. Never creates a TUN or runs a URL-test group. */
internal object ServerCheckConfig {
    fun build(profile: ImportedProfile): String? {
        // Full documents can contain selectors, rulesets and providers with their own timers.
        if (profile.fullConfigJson != null) return null
        val config = JSONObject(SingBoxConfigFactory.build(profile))
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
