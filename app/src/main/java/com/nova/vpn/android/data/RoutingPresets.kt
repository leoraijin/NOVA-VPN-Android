package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

internal data class RoutingPreset(val id: String, val name: String, val settings: AppRoutingSettings)
internal data class PendingRoutingChange(val requestId: String, val presetId: String,
    val settings: AppRoutingSettings, val processSession: String)

/** Active settings are unchanged until the service commits the staged snapshot. */
internal data class RoutingPresets(
    val activeId: String,
    val presets: List<RoutingPreset>,
    val pending: PendingRoutingChange? = null
) {
    val active: RoutingPreset get() = presets.single { it.id == activeId }

    fun stage(presetId: String, requestId: String, session: String, settings: AppRoutingSettings? = null): RoutingPresets {
        val preset = presets.single { it.id == presetId }
        return copy(pending = PendingRoutingChange(requestId, presetId, settings ?: preset.settings, session)).also { it.validate() }
    }

    fun commit(requestId: String, presetId: String): RoutingPresets {
        val change = pending ?: error("Нет ожидающего переключения маршрутов")
        require(change.requestId == requestId && change.presetId == presetId) { "Устаревшее подтверждение маршрутов" }
        return copy(activeId = presetId, presets = presets.map {
            if (it.id == presetId) it.copy(settings = change.settings) else it
        }, pending = null).also { it.validate() }
    }

    fun recover(session: String) = if (pending != null && pending.processSession != session) copy(pending = null) else this

    fun validate() {
        require(presets.size in 1..100 && presets.map { it.id }.distinct().size == presets.size)
        require(presets.any { it.id == activeId })
        presets.forEach { require(it.id.length in 1..128 && it.name.trim().length in 1..48); validateSettings(it.settings) }
        pending?.let {
            require(it.requestId.length in 1..128 && it.processSession.length in 1..128 && presets.any { p -> p.id == it.presetId })
            validateSettings(it.settings)
        }
    }

    fun toJson(): JSONObject {
        validate()
        return JSONObject().put("schema", 1).put("activeId", activeId).put("presets", JSONArray().apply {
            presets.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("settings", settingsJson(it.settings))) }
        }).put("pending", pending?.let { JSONObject().put("requestId", it.requestId).put("presetId", it.presetId)
            .put("session", it.processSession).put("settings", settingsJson(it.settings)) } ?: JSONObject.NULL)
    }

    companion object {
        fun migrate(settings: AppRoutingSettings) = RoutingPresets("my-routing", listOf(RoutingPreset("my-routing", "Мой профиль", settings)))
        fun fromJson(json: JSONObject): RoutingPresets {
            require(json.getInt("schema") == 1) { "Неподдерживаемая версия профилей маршрутизации" }
            val entries = json.getJSONArray("presets")
            val pending = json.optJSONObject("pending")?.let { PendingRoutingChange(it.getString("requestId"), it.getString("presetId"),
                readSettings(it.getJSONObject("settings")), it.getString("session")) }
            return RoutingPresets(json.getString("activeId"), List(entries.length()) {
                val item = entries.getJSONObject(it)
                RoutingPreset(item.getString("id"), item.getString("name"), readSettings(item.getJSONObject("settings")))
            }, pending).also { it.validate() }
        }

        private fun validateSettings(settings: AppRoutingSettings) {
            require(settings.packages.size <= 2000 && settings.sites.size <= 10000)
            require(settings.packages.all { it.length in 3..255 && it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) })
            require(settings.sites.map { it.domain }.distinct().size == settings.sites.size)
            require(settings.sites.all { SiteDomain.normalize(it.domain) == it.domain })
        }

        private fun settingsJson(settings: AppRoutingSettings) = JSONObject().put("mode", settings.mode.name)
            .put("packages", JSONArray(settings.packages.sorted())).put("ipv6", settings.ipv6Enabled)
            .put("sites", JSONArray().apply { settings.sites.forEach { put(JSONObject().put("domain", it.domain).put("target", it.target.name)) } })

        private fun readSettings(json: JSONObject): AppRoutingSettings {
            val packages = json.getJSONArray("packages")
            val sites = json.getJSONArray("sites")
            return AppRoutingSettings(AppRoutingMode.valueOf(json.getString("mode")), List(packages.length()) { packages.getString(it) }.toSet(),
                List(sites.length()) { val site = sites.getJSONObject(it); SiteRoutingRule(site.getString("domain"), SiteRoutingTarget.valueOf(site.getString("target"))) }, json.getBoolean("ipv6"))
        }
    }
}
