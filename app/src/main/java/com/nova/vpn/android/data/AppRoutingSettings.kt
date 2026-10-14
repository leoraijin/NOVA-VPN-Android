package com.nova.vpn.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppRoutingStore(context: Context) {
    private val prefs = context.getSharedPreferences("routing", Context.MODE_PRIVATE)

    fun load(): AppRoutingSettings = AppRoutingSettings(
        mode = runCatching { AppRoutingMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(AppRoutingMode.EXCLUDE_SELECTED),
        packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet(),
        sites = readSites()
    )

    fun save(settings: AppRoutingSettings) {
        check(prefs.edit()
            .putString(KEY_MODE, settings.mode.name)
            .putStringSet(KEY_PACKAGES, settings.packages.toSet())
            .putString(KEY_SITES, JSONArray().apply {
                settings.sites.distinctBy { it.domain }.forEach { rule ->
                    put(JSONObject().put("domain", rule.domain).put("target", rule.target.name))
                }
            }.toString())
            .commit()) { "Не удалось сохранить настройки маршрутизации" }
    }

    private fun readSites(): List<SiteRoutingRule> = runCatching {
        val source = prefs.getString(KEY_SITES, null) ?: return emptyList()
        val array = JSONArray(source)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val domain = SiteDomain.normalize(item.optString("domain")) ?: return@mapNotNull null
            val target = runCatching { SiteRoutingTarget.valueOf(item.optString("target")) }
                .getOrDefault(SiteRoutingTarget.VPN)
            SiteRoutingRule(domain, target)
        }.distinctBy { it.domain }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_PACKAGES = "packages"
        const val KEY_SITES = "sites"
    }
}
