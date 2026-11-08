package com.nova.vpn.android

import org.json.JSONArray
import org.json.JSONObject

/** Immutable non-secret identity of the captured routing policy. */
class RoutePolicyIdentity(val revision: Long, profiles: Set<String>, val digest: String? = null) {
    val profileIds: Set<String> = java.util.Collections.unmodifiableSet(LinkedHashSet(profiles))
    init { require(revision>=0 && profiles.size in 1..2000 && profiles.all { it.length in 1..128 }); require(digest==null||digest.matches(Regex("[a-f0-9]{64}"))) }
    override fun equals(other: Any?) = other is RoutePolicyIdentity && revision==other.revision && profileIds==other.profileIds && digest==other.digest
    override fun hashCode() = 31*(31*revision.hashCode()+profileIds.hashCode())+(digest?.hashCode()?:0)
    fun encode(): String = JSONObject().put("schema",if(digest==null)1 else 2).put("revision",revision)
        .put("profiles",JSONArray(profileIds.sorted())).apply { if(digest!=null)put("digest",digest) }.toString()
    companion object {
        fun capture(settings: com.nova.vpn.android.data.AppRoutingSettings): RoutePolicyIdentity? = settings.multiServer?.let { multi ->
            multi.validate()
            // Arrays have explicit order, unlike JSONObject maps. Include all traffic-affecting settings.
            val rules=JSONArray().apply { multi.rules.forEach { rule -> put(JSONArray()
                .put(rule.packageName?:JSONObject.NULL).put(rule.domain?:JSONObject.NULL).put(rule.includeSubdomains)
                .put(rule.target.kind.name).put(rule.target.profileId?:JSONObject.NULL)) } }
            val sites=JSONArray().apply { settings.sites.sortedWith(compareBy({it.domain},{it.target.name})).forEach { put(JSONArray().put(it.domain).put(it.target.name)) } }
            val canonical=JSONArray().put(1).put(multi.revision).put(multi.defaultProfileId).put(settings.mode.name)
                .put(settings.ipv6Enabled).put(JSONArray(settings.packages.sorted())).put(sites).put(rules)
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toString().toByteArray(Charsets.UTF_8))
                .joinToString(""){"%02x".format(java.util.Locale.ROOT,it.toInt() and 255)}
            RoutePolicyIdentity(multi.revision,com.nova.vpn.android.data.MultiServerPolicy(multi.revision,multi.defaultProfileId,multi.rules,emptySet()).referencedProfileIds,digest)
        }
        fun decode(value: String?): RoutePolicyIdentity? {
            if(value==null)return null
            require(value.length<=300000)
            val json=JSONObject(value);val schema=json.getInt("schema");require(schema==1||schema==2);require(schema!=2||json.has("digest"))
            val ids=json.getJSONArray("profiles");require(ids.length() in 1..2000)
            val list=List(ids.length()){ids.getString(it)};require(list.distinct().size==list.size)
            return RoutePolicyIdentity(json.getLong("revision"),list.toSet(),if(schema==2)json.getString("digest") else null)
        }
    }
}