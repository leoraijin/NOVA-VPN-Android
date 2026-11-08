package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

/** Persisted editor intent; unresolved profile IDs are retained for explicit rebinding. */
data class MultiServerSettings(val revision: Long, val defaultProfileId: String, val rules: List<MultiServerRule>, val secondaryProfileId: String? = null) {
    fun validate() { require(secondaryProfileId == null || secondaryProfileId.isNotBlank() && secondaryProfileId != defaultProfileId); MultiServerPolicy(revision, defaultProfileId, rules, emptySet()) }
    fun toJson(): JSONObject {
        validate()
        return JSONObject().put("schema", 1).put("revision", revision).put("defaultProfileId", defaultProfileId)
            .put("secondaryProfileId", secondaryProfileId ?: JSONObject.NULL)
            .put("rules", JSONArray().apply { rules.forEach { rule ->
                put(JSONObject().put("package", rule.packageName ?: JSONObject.NULL)
                    .put("domain", rule.domain ?: JSONObject.NULL).put("includeSubdomains", rule.includeSubdomains)
                    .put("target", rule.target.kind.name).put("profileId", rule.target.profileId ?: JSONObject.NULL))
            } })
    }
    companion object {
        fun fromJson(json: JSONObject): MultiServerSettings {
            require(json.getInt("schema") == 1) { "Неподдерживаемая версия назначений серверов" }
            val source = json.getJSONArray("rules")
            require(source.length() <= 12000)
            return MultiServerSettings(json.getLong("revision"), json.getString("defaultProfileId"), List(source.length()) { index ->
                val rule = source.getJSONObject(index)
                fun nullable(key: String): String? = if (rule.isNull(key)) null else rule.getString(key)
                MultiServerRule(nullable("package"), nullable("domain"), rule.getBoolean("includeSubdomains"),
                    ServerRouteTarget(ServerRouteKind.valueOf(rule.getString("target")), nullable("profileId")))
            }, if (json.isNull("secondaryProfileId")) null else json.getString("secondaryProfileId")).let { settings ->
                if (json.has("secondaryProfileId")) settings else settings.copy(
                    secondaryProfileId = settings.rules.mapNotNull { it.target.profileId }
                        .filter { it != settings.defaultProfileId }.distinct().singleOrNull())
            }.also { it.validate() }
        }
    }
}