package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

internal data class RoutingPreset(val id: String, val name: String, val settings: AppRoutingSettings)
internal data class PendingRoutingChange(val requestId: String, val presetId: String,
    val settings: AppRoutingSettings, val processSession: String, val selection: RoutingSelectionChange? = null)

/** Active settings are unchanged until the service commits the staged snapshot. */
internal data class RoutingPresets(
    val activeId: String,
    val presets: List<RoutingPreset>,
    val pending: PendingRoutingChange? = null
) {
    val active: RoutingPreset get() = presets.single { it.id == activeId }
    val hasModeHistory: Boolean get()=(presets.map {it.settings}+listOfNotNull(pending?.settings)).any {it.history!=null}
    val referencedProfileIds: Set<String> get()=(presets.map {it.settings}+listOfNotNull(pending?.settings)).flatMap { settings ->
        listOfNotNull(settings.multiServer,settings.history?.savedAssignments).flatMap {it.let {multi ->
            MultiServerPolicy(multi.revision,multi.defaultProfileId,multi.rules,emptySet()).referencedProfileIds}}+listOfNotNull(settings.history?.single?.profileId)
    }.toSet()+setOfNotNull(pending?.selection?.profileId)

    fun withoutServerAssignments(): RoutingPresets=copy(pending=null,presets=presets.map {preset ->
        val settings=preset.settings
        preset.copy(settings=if(settings.multiServer!=null)settings.copy(mode=AppRoutingMode.EXCLUDE_SELECTED,packages=emptySet(),sites=emptyList(),multiServer=null,history=null)
            else settings.copy(history=null))
    }).also {it.validate()}

    fun stage(presetId: String, requestId: String, session: String, settings: AppRoutingSettings? = null, selection: RoutingSelectionChange? = null): RoutingPresets {
        val preset = presets.single { it.id == presetId }
        return copy(pending = PendingRoutingChange(requestId, presetId, settings ?: preset.settings, session,selection)).also { it.validate() }
    }

    fun commitPolicy(requestId: String,presetId: String,policy: com.nova.vpn.android.RoutePolicyIdentity?): RoutingPresets {
        val change=pending ?: error("Нет ожидающего переключения маршрутов")
        require(com.nova.vpn.android.RoutePolicyIdentity.capture(change.settings)==policy) { "Подтверждение относится к другой версии назначений серверов" }
        return commit(requestId,presetId)
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
        return JSONObject().put("schema", 3).put("activeId", activeId).put("presets", JSONArray().apply {
            presets.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("settings", settingsJson(it.settings))) }
        }).put("pending", pending?.let { JSONObject().put("requestId", it.requestId).put("presetId", it.presetId)
            .put("session", it.processSession).put("settings", settingsJson(it.settings)).put("selection",it.selection?.toJson()?:JSONObject.NULL) } ?: JSONObject.NULL)
    }

    companion object {
        fun migrate(settings: AppRoutingSettings) = RoutingPresets("my-routing", listOf(RoutingPreset("my-routing", "Мой профиль", settings)))
        fun fromJson(json: JSONObject): RoutingPresets {
            require(json.getInt("schema") in 1..3) { "Неподдерживаемая версия профилей маршрутизации" }
            val entries = json.getJSONArray("presets")
            require(entries.length() in 1..100)
            require(!json.has("pending")||json.isNull("pending")||json.get("pending") is JSONObject)
            val pending = json.optJSONObject("pending")?.let {
                require(!it.has("selection")||it.isNull("selection")||it.get("selection") is JSONObject)
                PendingRoutingChange(it.getString("requestId"), it.getString("presetId"),
                readSettings(it.getJSONObject("settings")), it.getString("session"),it.optJSONObject("selection")?.let(RoutingSelectionChange::fromJson)) }
            return RoutingPresets(json.getString("activeId"), List(entries.length()) {
                val item = entries.getJSONObject(it)
                RoutingPreset(item.getString("id"), item.getString("name"), readSettings(item.getJSONObject("settings")))
            }, pending).also { it.validate() }
        }

        internal fun validateSettings(settings: AppRoutingSettings) {
            settings.multiServer?.validate()
            require(settings.packages.size <= 2000 && settings.sites.size <= 10000)
            require(settings.packages.all { it.length in 3..255 && it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) })
            require(settings.sites.map { it.domain }.distinct().size == settings.sites.size)
            require(settings.sites.all { SiteDomain.normalize(it.domain) == it.domain })
        }

        internal fun settingsJson(settings: AppRoutingSettings): JSONObject = JSONObject().put("mode", settings.mode.name)
            .put("multiServer", settings.multiServer?.toJson() ?: JSONObject.NULL)
            .put("history",settings.history?.toJson()?:JSONObject.NULL)
            .put("packages", JSONArray(settings.packages.sorted())).put("ipv6", settings.ipv6Enabled)
            .put("sites", JSONArray().apply { settings.sites.forEach { put(JSONObject().put("domain", it.domain).put("target", it.target.name)) } })

        internal fun readSettings(json: JSONObject): AppRoutingSettings {
            listOf("multiServer","history").forEach {key->require(!json.has(key)||json.isNull(key)||json.get(key) is JSONObject)}
            val packages = json.getJSONArray("packages")
            val sites = json.getJSONArray("sites")
            require(packages.length()<=2000 && sites.length()<=10000)
            return AppRoutingSettings(AppRoutingMode.valueOf(json.getString("mode")), List(packages.length()) { packages.getString(it) }.toSet(),
                List(sites.length()) { val site = sites.getJSONObject(it); SiteRoutingRule(site.getString("domain"), SiteRoutingTarget.valueOf(site.getString("target"))) }, json.getBoolean("ipv6"), json.optJSONObject("multiServer")?.let(MultiServerSettings::fromJson),json.optJSONObject("history")?.let(MultiServerRoutingHistory::fromJson))
        }
    }
}
