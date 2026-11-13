package com.nova.vpn.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class RoutingSharePackage(val routing: RoutingPresets, val profiles: List<ImportedProfile>,
    val labels: Map<String, String>, val selectedId: String?, val includesKeys: Boolean) {
    val references: Set<String> get() = RoutingShare.references(routing.toJson()) + setOfNotNull(selectedId)
}
internal data class RoutingSharePlan(val routing: RoutingPresets, val profiles: ProfileSnapshot,
    val selection: RoutingSelectionChange?)

internal object RoutingShare {
    private val referenceKeys = setOf("profileId", "defaultProfileId", "secondaryProfileId")
    fun references(value: Any?): Set<String> = when(value) {
        is JSONObject -> value.keys().asSequence().flatMap { key ->
            if(key in referenceKeys && !value.isNull(key)) sequenceOf(value.getString(key))
            else references(value.get(key)).asSequence()
        }.toSet()
        is JSONArray -> (0 until value.length()).flatMap { references(value.get(it)) }.toSet()
        else -> emptySet()
    }
    private fun remap(value: Any?, mapping: Map<String,String>) {
        when(value) {
            is JSONObject -> value.keys().asSequence().toList().forEach { key ->
                if(key in referenceKeys && !value.isNull(key)) value.put(key, mapping.getValue(value.getString(key)))
                else remap(value.get(key), mapping)
            }
            is JSONArray -> repeat(value.length()) { remap(value.get(it), mapping) }
        }
    }
    fun document(routing: RoutingPresets, profiles: List<ImportedProfile>, selected: String?, includeKeys: Boolean): JSONObject {
        val committed = routing.copy(pending = null)
        val active = profiles.filterNot { it.retiredFromSubscription }
        val selectedId = selected?.takeIf { includeKeys && active.any { p -> p.id == it } }
        val refs = references(committed.toJson()) + setOfNotNull(selectedId)
        val names = profiles.associate { it.id to it.displayName.take(160) }
        return JSONObject().put("format", "nova-routing-share-v1").put("scope", if(includeKeys) "keys-and-routing" else "routing")
            .put("routing", committed.toJson()).put("selectedId", selectedId ?: JSONObject.NULL)
            .put("labels", JSONObject().apply { refs.forEach { put(it, names[it] ?: "Недоступный сервер") } })
            .apply { if(includeKeys) put("profiles", JSONArray().apply {
                active.forEach { put(it.copy(subscriptionUrl=null, latencyMillis=null).toJson()) }
            }) }
    }
    fun export(context: Context, includeKeys: Boolean, password: String): String {
        val doc = synchronized(ProfileVault.lock) {
            document(AppRoutingStore(context).loadPresets(), ProfileVault(context).load(), SelectedProfileStore(context).load(), includeKeys)
        }
        decode(doc) // Refuse an incomplete or oversized-reference package before writing it.
        val json = doc.toString()
        require(json.toByteArray(Charsets.UTF_8).size <= IdentityTransfer.MAX_BYTES) { "Пакет слишком большой." }
        return if(includeKeys) IdentityTransfer.seal(json,password) else json
    }
    fun read(text: String, password: String): RoutingSharePackage {
        require(text.toByteArray(Charsets.UTF_8).size <= 12*1024*1024) { "Файл слишком большой." }
        val encrypted = text.trimStart().startsWith("NOVA-ID1:")
        val doc = JSONObject(if(encrypted) IdentityTransfer.open(text,password) else text)
        val result = decode(doc)
        require(!result.includesKeys || encrypted) { "Пакет с ключами должен быть зашифрован." }
        return result
    }
    internal fun decode(doc: JSONObject): RoutingSharePackage {
        require(doc.getString("format") == "nova-routing-share-v1") { "Выберите файл обмена ключами и маршрутизацией NOVA." }
        val scope=doc.getString("scope");require(scope in setOf("routing","keys-and-routing"))
        val keys=scope=="keys-and-routing"
        require(keys || !doc.has("profiles")) { "Файл маршрутизации не должен содержать ключи." }
        val routing=RoutingPresets.fromJson(doc.getJSONObject("routing"))
        require(routing.pending==null) { "Пакет содержит незавершённое переключение маршрутов." }
        val profiles=if(keys) {
            val array=doc.getJSONArray("profiles");require(array.length()<=2000)
            List(array.length()) { ImportedProfile.fromJson(array.getJSONObject(it)).also { p ->
                require(p.id.length in 1..128 && p.name.isNotBlank())
                require(p.subscriptionUrl==null) { "Пакет ключей не должен содержать ссылки подписок." }
                if(p.fullConfigJson!=null) JSONObject(p.fullConfigJson) else require(JSONObject(p.outboundJson).has("type"))
            } }.also { require(it.map { p -> p.id }.distinct().size==it.size) }
        } else emptyList()
        val selected=if(doc.isNull("selectedId")) null else doc.getString("selectedId")
        require(selected==null || keys && profiles.any { it.id==selected })
        val labels=doc.getJSONObject("labels");require(labels.length()<=2000)
        val result=RoutingSharePackage(routing,profiles,labels.keys().asSequence().associateWith { labels.getString(it).take(160) },selected,keys)
        require(result.references==result.labels.keys) { "Неполные назначения серверов в пакете." }
        return result
    }
    fun plan(incoming: RoutingSharePackage, current: RoutingPresets, catalog: ProfileSnapshot,
        choices: Map<String,String>): RoutingSharePlan {
        require(current.pending==null) { "Дождитесь применения маршрутов." }
        val byConfig=catalog.profiles.associateBy { it.configurationKey() }.toMutableMap()
        val map=mutableMapOf<String,String>()
        val additions=mutableListOf<ImportedProfile>()
        incoming.profiles.forEach { profile ->
            val local=byConfig[profile.configurationKey()] ?: profile.copy(id=UUID.randomUUID().toString(),subscriptionUrl=null).also {
                byConfig[it.configurationKey()]=it
            }
            additions+=local.copy(subscriptionUrl=null)
            map[profile.id]=local.id
        }
        val available=catalog.profiles.filterNot { it.retiredFromSubscription }.map { it.id }.toSet()
        incoming.references.filterNot { it in map }.forEach { id ->
            val local=choices[id]
            require(local!=null && local in available) { "Выберите сервер для всех назначений." }
            map[id]=local
        }
        val json=incoming.routing.toJson();remap(json,map)
        val remapped=try { RoutingPresets.fromJson(json) } catch(error: IllegalArgumentException) {
            throw IllegalArgumentException("Выберите разные конфиги для основного VPN и VPN-2.",error)
        }
        val presetIds=remapped.presets.associate { it.id to UUID.randomUUID().toString() }
        require(current.presets.size+remapped.presets.size<=100) { "Недостаточно места: максимум 100 профилей маршрутизации." }
        val next=current.copy(activeId=presetIds.getValue(remapped.activeId),presets=current.presets+remapped.presets.map {
            it.copy(id=presetIds.getValue(it.id))
        }).also { it.validate() }
        val merged=catalog.importManual(additions).withAvailability()
        require(merged.profiles.size<=2000) { "Превышен лимит конфигов." }
        return RoutingSharePlan(next,merged,incoming.selectedId?.let { RoutingSelectionChange(map.getValue(it)) })
    }
}
