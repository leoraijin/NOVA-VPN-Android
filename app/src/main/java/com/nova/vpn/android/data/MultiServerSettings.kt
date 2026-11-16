package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

/** Persisted editor intent; unresolved profile IDs are retained for explicit rebinding. */
data class MultiServerSettings(val revision: Long, val defaultProfileId: String, val rules: List<MultiServerRule>, val secondaryProfileId: String? = null,
    val secondaryPaused: Boolean = false, val additionalSlots: Map<Int, String> = emptyMap()) {
    val slots: Map<Int, String> get() = (additionalSlots + listOfNotNull(secondaryProfileId?.let { 2 to it })).toSortedMap()
    fun withSlot(number: Int, profileId: String?): MultiServerSettings {
        require(number in 2..10)
        require(profileId == null || profileId != defaultProfileId && slots.none { it.key != number && it.value == profileId }) {
            "Этот конфиг уже назначен другому VPN"
        }
        val old = slots[number]
        val nextRules = if (old == null) rules else rules.mapNotNull { rule ->
            if (rule.target.kind != ServerRouteKind.PROFILE || rule.target.profileId != old) rule
            else profileId?.let { rule.copy(target = ServerRouteTarget(ServerRouteKind.PROFILE, it)) }
        }
        return copy(secondaryProfileId = if (number == 2) profileId else secondaryProfileId,
            additionalSlots = if (number == 2) additionalSlots else if (profileId == null) additionalSlots - number else additionalSlots + (number to profileId),
            rules = nextRules).also { it.validate() }
    }
    internal fun effectiveRules(): List<MultiServerRule> = if (!secondaryPaused) rules else rules.map { rule ->
        if (rule.target.kind == ServerRouteKind.PROFILE)
            rule.copy(target = ServerRouteTarget(ServerRouteKind.DEFAULT))
        else rule
    }

    fun validate() {
        require(additionalSlots.keys.all { it in 3..10 })
        require(slots.values.all { it.isNotBlank() && it.length <= 128 && it != defaultProfileId })
        require(slots.values.distinct().size == slots.size) { "Выберите разные конфиги для каждого VPN" }
        require(secondaryProfileId == null || secondaryProfileId.isNotBlank() && secondaryProfileId != defaultProfileId); MultiServerPolicy(revision, defaultProfileId, rules, emptySet()) }
    fun toJson(): JSONObject {
        validate()
        return JSONObject().put("schema", 1).put("revision", revision).put("defaultProfileId", defaultProfileId)
            .put("secondaryProfileId", secondaryProfileId ?: JSONObject.NULL)
            .put("additionalSlots", JSONArray().apply { additionalSlots.toSortedMap().forEach { (slot, id) ->
                put(JSONObject().put("slot", slot).put("profileId", id))
            } })
            .apply { if (secondaryPaused) put("secondaryPaused", true) }
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
            }, if (json.isNull("secondaryProfileId")) null else json.getString("secondaryProfileId"), json.optBoolean("secondaryPaused"),
                json.optJSONArray("additionalSlots")?.let { slots ->
                    require(slots.length() <= 8)
                    val entries = List(slots.length()) { i -> slots.getJSONObject(i).let { it.getInt("slot") to it.getString("profileId") } }
                    require(entries.map { it.first }.distinct().size == entries.size)
                    entries.toMap()
                } ?: emptyMap()).let { settings ->
                if (json.has("secondaryProfileId")) settings else settings.copy(
                    secondaryProfileId = settings.rules.mapNotNull { it.target.profileId }
                        .filter { it != settings.defaultProfileId }.distinct().singleOrNull())
            }.also { it.validate() }
        }
    }
}
