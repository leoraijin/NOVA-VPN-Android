package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.json.JSONArray
import org.json.JSONObject

/** Local immutable identity map. Only hashes and IDs cross the service boundary. */
internal class RouteExitBindings(val configSha256: String, entries: Map<String, Entry>, quarantined:Map<String,String> = emptyMap(),
    nativeRejected: Set<String> = emptySet()) {
    data class Entry(val tag: String,val profileSha256: String)
    val entries: Map<String,Entry> = java.util.Collections.unmodifiableMap(LinkedHashMap(entries))
    val quarantined:Map<String,String> = java.util.Collections.unmodifiableMap(LinkedHashMap(quarantined))
    private val nativeRejected = nativeRejected.toSet()
    init {
        require(quarantined.keys.containsAll(this.nativeRejected))
        require(configSha256.matches(Regex("[a-f0-9]{64}")) && entries.size+quarantined.size<=2000)
        require(quarantined.keys.none {it in entries} && quarantined.keys.all {it.length in 1..128} && quarantined.values.all {it.matches(Regex("[a-f0-9]{64}"))})
        require(entries.keys.all { it.length in 1..128 })
        require(entries.values.all { it.tag.matches(Regex("nova-exit-(0|[1-9][0-9]{0,3})")) && it.tag.substringAfterLast('-').toInt()<2000 && it.profileSha256.matches(Regex("[a-f0-9]{64}")) })
        require(entries.values.map { it.tag }.distinct().size==entries.size)
    }
    fun validate(config: String, referenced: Set<String>) {
        require(hash(config)==configSha256 && referenced.containsAll(entries.keys+quarantined.keys)) { "Снимок выходов не соответствует конфигурации" }
        val outs=JSONObject(config).getJSONArray("outbounds")
        val tags=(0 until outs.length()).map { outs.getJSONObject(it).getString("tag") }
        require(tags.distinct().size==tags.size)
        require(tags.filter { it.startsWith("nova-exit-") }.toSet()==entries.values.map { it.tag }.toSet())
    }
    /** Detect changes since preparation, including missing exits becoming available again. */
    fun validateCatalog(profiles: List<ImportedProfile>, referenced: Set<String>) {
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Каталог содержит повторяющиеся ID" }
        val available = profiles.filter { !it.retiredFromSubscription && it.id in referenced }
        require(available.map { it.id }.toSet() == entries.keys+quarantined.keys && available.all {
            (entries[it.id]?.profileSha256 ?: quarantined[it.id]) == profileHash(it) &&
                (it.id !in quarantined || it.id in nativeRejected || MultiServerProfileAdapter.problem(it)!=null)
        }) { "Серверы изменились после подготовки. Повторите применение маршрутов." }
    }
    /** Native evidence stays in the owning service and cannot be submitted through START. */
    fun withNativeRejected(config: String, rejected: Set<String>): RouteExitBindings {
        require(rejected.isNotEmpty() && entries.keys.containsAll(rejected))
        val retained = entries.filterKeys { it !in rejected }.toSortedMap().entries.mapIndexed { index, (id, entry) ->
            id to entry.copy(tag = "nova-exit-$index")
        }.toMap()
        return RouteExitBindings(hash(config), retained, quarantined + rejected.associateWith { entries.getValue(it).profileSha256 },
            nativeRejected + rejected)
    }
    fun encode(): String = JSONObject().also { check(nativeRejected.isEmpty()) }.put("schema",if(quarantined.isEmpty())1 else 2).put("configSha256",configSha256)
        .put("entries",JSONArray().apply { entries.toSortedMap().forEach { (id,entry)->put(JSONArray().put(id).put(entry.tag).put(entry.profileSha256)) } }).apply {if(quarantined.isNotEmpty())put("quarantined",JSONArray().apply {quarantined.toSortedMap().forEach {(id,fingerprint)->put(JSONArray().put(id).put(fingerprint))}})}.toString()
    companion object {
        fun hash(value: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(java.util.Locale.ROOT,it.toInt() and 255)}
        fun profileHash(profile: ImportedProfile) = hash(profile.configurationKey())
        fun capture(settings: MultiServerSettings, profiles: List<ImportedProfile>, config: String, quarantinedIds:Set<String> = emptySet()): RouteExitBindings {
            val referenced=MultiServerPolicy(settings.revision,settings.defaultProfileId,settings.rules,emptySet()).referencedProfileIds
            val available=profiles.filter { !it.retiredFromSubscription && it.id in referenced }
            require(quarantinedIds.all {id->available.any {it.id==id && MultiServerProfileAdapter.problem(it)!=null}})
            val selected=available.filter {it.id !in quarantinedIds}.sortedBy { it.id }
            require(profiles.map { it.id }.distinct().size==profiles.size)
            return RouteExitBindings(hash(config),selected.mapIndexed { index,p->p.id to Entry("nova-exit-$index",profileHash(p)) }.toMap(),available.filter {it.id in quarantinedIds}.associate {it.id to profileHash(it)}).also { it.validate(config,referenced) }
        }
        fun decode(value: String?): RouteExitBindings? {
            if(value==null)return null
            require(value.length<=600000)
            val json=JSONObject(value);val schema=json.getInt("schema");require(schema==1 || schema==2)
            require(schema!=1 || !json.has("quarantined"))
            val list=json.getJSONArray("entries");require(list.length()<=2000)
            val result=linkedMapOf<String,Entry>()
            repeat(list.length()) { val item=list.getJSONArray(it);require(item.length()==3);val id=item.getString(0);require(id !in result);result[id]=Entry(item.getString(1),item.getString(2)) }
            val blocked=linkedMapOf<String,String>()
            if(schema==2) {
                val items=json.getJSONArray("quarantined");require(items.length() in 1..2000)
                repeat(items.length()) {val item=items.getJSONArray(it);require(item.length()==2);val id=item.getString(0);require(id !in blocked);blocked[id]=item.getString(1)}
            }
            return RouteExitBindings(json.getString("configSha256"),result,blocked)
        }
    }
}
