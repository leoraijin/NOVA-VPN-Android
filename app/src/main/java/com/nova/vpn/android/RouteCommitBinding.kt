package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.RoutingPresets
import org.json.JSONArray
import org.json.JSONObject

internal class StaleRoutingSnapshot : IllegalStateException("Профиль маршрутизации изменился. Повторите подключение.")

/** Content hashes only; binds metadata/history and chosen ID to the exact pre-health native config. */
internal data class RouteCommitBinding(val settingsSha256: String,val profileId: String,val configSha256: String,val trafficSha256: String) {
    init {require(profileId.isNotBlank()&&profileId.length<=128);require(listOf(settingsSha256,configSha256,trafficSha256).all {it.matches(Regex("[a-f0-9]{64}"))})}
    fun validateConfig(config: String) {require(hash(config)==configSha256) {"Изменился конфиг подтверждения маршрутов"}}
    fun matches(settings: AppRoutingSettings,id: String?)=id==profileId&&settingsSha256==settingsHash(settings)
    fun matchesTraffic(settings: AppRoutingSettings,id: String?)=id==profileId&&trafficSha256==settingsHash(settings.copy(history=null))
    fun encode()=JSONObject().put("schema",1).put("settings",settingsSha256).put("profile",profileId).put("config",configSha256).put("traffic",trafficSha256).toString()
    companion object {
        fun requireCurrent(document: RoutingPresets, identity: AppliedConfiguration, binding: RouteCommitBinding?, allowStaged: Boolean) {
            if (identity.policy == null && binding == null) return
            val pending = document.pending?.takeIf { allowStaged && it.requestId == identity.requestId }
            val preset = document.active
            val valid = if (pending != null) {
                pending.presetId == identity.routingPresetId &&
                    RoutePolicyIdentity.capture(pending.settings) == identity.policy &&
                    (binding == null || binding.matches(pending.settings, identity.profileId)) &&
                    (pending.selection == null || pending.selection.profileId != null && pending.selection.profileId == identity.profileId)
            } else {
                preset.id == identity.routingPresetId &&
                    RoutePolicyIdentity.capture(preset.settings) == identity.policy &&
                    (binding == null || binding.matchesTraffic(preset.settings, identity.profileId))
            }
            if (!valid) throw StaleRoutingSnapshot()
        }

        fun capture(settings: AppRoutingSettings,id: String,config: String)=RouteCommitBinding(settingsHash(settings),id,hash(config),settingsHash(settings.copy(history=null)))
        fun decode(value: String?): RouteCommitBinding? {
            if(value==null)return null
            require(value.length<=1024);val json=JSONObject(value);require(json.getInt("schema")==1)
            return RouteCommitBinding(json.getString("settings"),json.getString("profile"),json.getString("config"),json.getString("traffic"))
        }
        private fun settingsHash(settings: AppRoutingSettings): String {RoutingPresets.validateSettings(settings);return hash(canonical(RoutingPresets.settingsJson(settings)))}
        private fun canonical(value: Any?): String=when(value) {
            is JSONObject->value.keys().asSequence().sorted().joinToString(prefix="{",postfix="}"){JSONObject.quote(it)+":"+canonical(value.get(it))}
            is JSONArray->(0 until value.length()).joinToString(prefix="[",postfix="]"){canonical(value.get(it))}
            is String->JSONObject.quote(value)
            else->value.toString()
        }
        private fun hash(value: String)=java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(java.util.Locale.ROOT,it.toInt() and 255)}
    }
}
