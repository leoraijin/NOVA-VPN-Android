package com.nova.vpn.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppRoutingStore(context: Context) {
    private val recoveryContext = context.applicationContext
    private val prefs = context.getSharedPreferences("routing", Context.MODE_PRIVATE)

    fun load(): AppRoutingSettings = loadPresets().active.settings

    internal fun loadPresets(): RoutingPresets = synchronized(LOCK) { readPresets() }

    private fun readPresets(): RoutingPresets {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        return prefs.getString(KEY_PRESETS, null)?.let {
        RoutingPresets.fromJson(JSONObject(it)).recover(PROCESS_SESSION)
    } ?: RoutingPresets.migrate(loadLegacy())
    }

    private fun loadLegacy(): AppRoutingSettings = AppRoutingSettings(
        mode = runCatching { AppRoutingMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(AppRoutingMode.EXCLUDE_SELECTED),
        packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet(),
        sites = readSites(),
        ipv6Enabled = prefs.getBoolean("ipv6_enabled", false)
    )

    fun save(settings: AppRoutingSettings) = synchronized(LOCK) {
        val document = readPresets()
        check(document.pending == null) { "Дождитесь применения маршрутов" }
        persist(document.copy(presets = document.presets.map {
            if (it.id == document.activeId) it.copy(settings = settings) else it
        }))
    }

    internal fun updatePresets(transform: (RoutingPresets) -> RoutingPresets): RoutingPresets = synchronized(LOCK) {
        val next = transform(readPresets())
        persist(next)
        next
    }

    internal fun stage(presetId: String, requestId: String, settings: AppRoutingSettings? = null) =
        updatePresets { it.stage(presetId, requestId, PROCESS_SESSION, settings) }

    internal fun commitPending(requestId: String, presetId: String): Boolean = synchronized(LOCK) {
        val current = readPresets()
        if (current.pending?.requestId != requestId) return@synchronized false
        persist(current.commit(requestId, presetId))
        true
    }

    internal fun cancelPending(requestId: String) = synchronized(LOCK) {
        val current = readPresets()
        if (current.pending?.requestId == requestId) persist(current.copy(pending = null))
    }

    internal fun cancelPendingExcept(requestId: String?) = synchronized(LOCK) {
        val current = readPresets()
        if (current.pending != null && current.pending.requestId != requestId) persist(current.copy(pending = null))
    }

    private fun persist(document: RoutingPresets) {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        document.validate()
        val settings = document.active.settings
        check(prefs.edit().putString(KEY_PRESETS, document.toJson().toString())
            .putString(KEY_MODE, settings.mode.name)
            .putBoolean("ipv6_enabled", settings.ipv6Enabled)
            .putStringSet(KEY_PACKAGES, settings.packages.toSet())
            .putString(KEY_SITES, JSONArray().apply {
                settings.sites.distinctBy { it.domain }.forEach { rule ->
                    put(JSONObject().put("domain", rule.domain).put("target", rule.target.name))
                }
            }.toString())
            .commit()) { "Не удалось сохранить настройки маршрутизации" }
        revision.value = revision.value + 1
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
        val LOCK = ProfileVault.lock
        val PROCESS_SESSION = java.util.UUID.randomUUID().toString()
        val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
        const val KEY_PRESETS = "routing_presets_v1"
        const val KEY_MODE = "mode"
        const val KEY_PACKAGES = "packages"
        const val KEY_SITES = "sites"
    }

    internal val changes: kotlinx.coroutines.flow.StateFlow<Long> get() = revision
}
