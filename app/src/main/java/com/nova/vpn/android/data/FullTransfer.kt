package com.nova.vpn.android.data
import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/** Cross-platform transfer; foreign platform settings stay encrypted and are preserved. */
object FullTransfer {
    fun export(context:Context,password:String):String = synchronized(ProfileVault.lock) {
        val identity=IdentityVault(context).load();val exportedProfiles=ProfileVault(context).loadSnapshot()
        val routing=AppRoutingStore(context).load()
        val platforms=JSONObject(identity.platforms).put("android",transferSnapshot(snapshot(context)).put("profileDocument",ProfileDocument.encodeSnapshot(exportedProfiles)).toString())
        val profiles=JSONArray().apply { exportedProfiles.profiles.forEach { p->put(JSONObject().put("id",p.id).put("name",p.name).put("protocol",p.protocol).put("server",p.server).put("port",p.port).put("outboundJson",p.outboundJson).put("fullConfigJson",p.fullConfigJson?:JSONObject.NULL).put("subscriptionUrl",p.subscriptionUrl?:JSONObject.NULL).put("userSettings",p.userSettings.toJson())) } }
        val doc=JSONObject().put("format","nova-full-v1").put("identity",identity.copy(platforms=emptyMap()).toJson()).put("profiles",profiles).put("selectedId",SelectedProfileStore(context).load()?:"").put("dark",context.getSharedPreferences("app_preferences",0).getString("appearance_mode","")=="DARK").put("ipv6",routing.ipv6Enabled).put("directDomains",JSONArray(routing.sites.filter {it.target==SiteRoutingTarget.DIRECT}.map {it.domain})).put("proxyDomains",JSONArray(routing.sites.filter {it.target==SiteRoutingTarget.VPN}.map {it.domain})).put("platforms",platforms)
        IdentityTransfer.seal(doc.toString(),password)
    }
    fun read(code:String,password:String):JSONObject {
        return JSONObject(IdentityTransfer.open(code,password)).also(::validateDocument)
    }
    internal fun validateDocument(d:JSONObject) {
        require(d.getString("format")=="nova-full-v1") {"Это не полный перенос NOVA."}
        IdentityState.fromJson(d.getJSONObject("identity"));val profiles=d.getJSONArray("profiles");require(profiles.length()<=2000)
        val ids=mutableSetOf<String>()
        repeat(profiles.length()) { i->val p=profiles.getJSONObject(i);require(p.getString("id").isNotBlank()&&ids.add(p.getString("id"))&&p.getString("name").isNotBlank());p.getString("protocol");p.getString("server");p.getInt("port");if(!p.isNull("fullConfigJson"))JSONObject(p.getString("fullConfigJson")) else require(JSONObject(p.getString("outboundJson")).has("type")) }
        require(d.getJSONObject("platforms").length()<=4);d.getBoolean("dark");d.getBoolean("ipv6");d.getString("selectedId")
        for(key in listOf("directDomains","proxyDomains")){val array=d.getJSONArray(key);require(array.length()<=10000);repeat(array.length()){require(SiteDomain.normalize(array.getString(it))!=null){"В пакете есть правило домена, которое Android пока не поддерживает: импорт не применён."}}}
        d.getJSONObject("platforms").optString("android").takeIf {it.isNotBlank()}?.let {
            val native=transferSnapshot(JSONObject(it))
            native.optString("profileDocument").takeIf(String::isNotBlank)?.let { encoded ->
                val embedded=ProfileDocument.decodeSnapshot(encoded).profiles.associateBy { profile -> profile.id }
                require(embedded.keys==ids) {"Несогласованные профили в пакете переноса"}
                repeat(profiles.length()) { index ->
                    val portable=profiles.getJSONObject(index);val saved=embedded.getValue(portable.getString("id"))
                    val full=portable.optString("fullConfigJson").takeUnless { portable.isNull("fullConfigJson") || it.isBlank() }
                    require(saved.name==portable.getString("name") && saved.protocol==portable.getString("protocol") &&
                        saved.server==portable.getString("server") && saved.port==portable.getInt("port") &&
                        (saved.fullConfigJson==null)==(full==null) &&
                        canonical(JSONObject(saved.fullConfigJson?:saved.outboundJson))==canonical(JSONObject(full?:portable.getString("outboundJson")))) {
                        "Конфигурации в пакете переноса не совпадают"
                    }
                }
            }
        }
    }
    fun restore(context:Context,d:JSONObject) {
        // All parsing and checks happen before changing persistent data.
        validateDocument(d)
        val profiles=d.getJSONArray("profiles");val incoming=List(profiles.length()){i->val p=profiles.getJSONObject(i);ImportedProfile(id=p.getString("id"),name=p.getString("name"),protocol=p.getString("protocol"),server=p.getString("server"),port=p.getInt("port"),outboundJson=if(p.isNull("outboundJson")) "{}" else p.getString("outboundJson"),fullConfigJson=if(p.isNull("fullConfigJson"))null else p.getString("fullConfigJson"),subscriptionUrl=if(p.isNull("subscriptionUrl"))null else p.getString("subscriptionUrl"),userSettings=ProfileUserSettings.fromJson(p.optJSONObject("userSettings")))}
        val platforms=d.getJSONObject("platforms");val identity=IdentityState.fromJson(d.getJSONObject("identity")).copy(platforms=platforms.keys().asSequence().associateWith{platforms.getString(it)})
        val sites=listOf("directDomains" to SiteRoutingTarget.DIRECT,"proxyDomains" to SiteRoutingTarget.VPN).flatMap {(key,target)->val a=d.getJSONArray(key);List(a.length()){SiteRoutingRule(SiteDomain.normalize(a.getString(it))!!,target)}}
        val native=platforms.optString("android").takeIf {it.isNotBlank()}?.let {transferSnapshot(JSONObject(it))}
        val restoredSnapshot=native?.optString("profileDocument")?.takeIf {it.isNotBlank()}?.let(ProfileDocument::decodeSnapshot);require(restoredSnapshot==null || restoredSnapshot.profiles.map {it.id}.toSet()==incoming.map {it.id}.toSet())
        com.nova.vpn.android.VpnUserRequests.gate.restoreWhenIdle(canRestore = {
            val state = com.nova.vpn.android.VpnRuntime.state.value
            !state.connected && !state.connecting
        }) {
        synchronized(ProfileVault.lock) {
        recoverPending(context)
        ProfileVault.invalidatePendingUpdates()
        val before=snapshot(context);val oldProfiles=ProfileVault(context).loadSnapshot();val oldIdentities=IdentityVault(context).load()
        val journal=TransferRestoreJournal(context)
        StoreRecoveryGuard.transaction {
        journal.prepare(JSONObject().put("schema",1).put("preferences",before)
            .put("profiles",ProfileDocument.encodeSnapshot(oldProfiles)).put("identity",oldIdentities.toJson()))
        try {
            native?.let {applySnapshot(context,it)}
            ProfileVault(context).restoreSnapshot(restoredSnapshot?:ProfileSnapshot.migrate(incoming));IdentityVault(context).save(identity)
            val current=AppRoutingStore(context).load();AppRoutingStore(context).save(current.copy(sites=sites,ipv6Enabled=d.getBoolean("ipv6")))
            AppPreferencesStore(context).setAutoConnectEnabled(false)
            check(context.getSharedPreferences("app_preferences",0).edit().putString("appearance_mode",if(d.getBoolean("dark"))"DARK" else "LIGHT").commit())
            val selected=d.getString("selectedId");SelectedProfileStore(context).save(incoming.firstOrNull {it.id==selected}?.id?:incoming.firstOrNull()?.id.orEmpty())
        } catch(e:Exception) {
            try {applySnapshot(context,before);ProfileVault(context).restoreSnapshot(oldProfiles);IdentityVault(context).save(oldIdentities);journal.clear();StoreRecoveryGuard.completed()}catch(rollback:Exception){throw IllegalStateException("Ошибка восстановления. Не удалось полностью вернуть прежние настройки.",rollback)}
            throw e
        }
        // Once journal deletion starts, do not begin a new rollback: a failed
        // directory sync can mean the pre-image has already disappeared.
        journal.clear()
        StoreRecoveryGuard.completed()
        }
        }
        }
        runCatching { com.nova.vpn.android.SubscriptionScheduling.request(context, force = true) }
    }
    private val prefNames=listOf("app_preferences","routing","profile_selection")
    /** Startup retries this idempotent rollback until every pre-image has been restored. */
    internal fun recoverPending(context: Context): Boolean = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.transaction {
        val journal=TransferRestoreJournal(context)
        val previous=journal.read()
        if(previous==null) { StoreRecoveryGuard.completed(); return@transaction false }
        require(previous.getInt("schema")==1)
        val preferences=previous.getJSONObject("preferences")
        validateSnapshot(preferences)
        val profiles=ProfileDocument.decodeSnapshot(previous.getString("profiles"))
        val identity=IdentityState.fromJson(previous.getJSONObject("identity"))
        ProfileVault.invalidatePendingUpdates()
        applySnapshot(context,preferences)
        ProfileVault(context).restoreSnapshot(profiles)
        IdentityVault(context).save(identity)
        journal.clear()
        StoreRecoveryGuard.completed()
        true
        }
    }
    /** A transfer contains committed settings only, even when exported by this process mid-switch. */
    internal fun transferSnapshot(source:JSONObject):JSONObject {
        validateSnapshot(source)
        val copy=JSONObject(source.toString())
        copy.getJSONObject("routing").optJSONObject("routing_presets_v1")?.let { entry ->
            require(entry.getString("type")=="string")
            val document=RoutingPresets.fromJson(JSONObject(entry.getString("value")))
            entry.put("value",document.copy(pending=null).toJson().toString())
        }
        return copy
    }
    private fun canonical(value:Any?):String = when(value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix="{",postfix="}") { JSONObject.quote(it)+":"+canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(prefix="[",postfix="]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
    private fun snapshot(c:Context)=JSONObject().apply {prefNames.forEach {name->put(name,JSONObject().apply {c.getSharedPreferences(name,0).all.forEach {(key,value)->put(key,JSONObject().put("type",when(value){is Boolean->"bool";is Int->"int";is Long->"long";is Float->"float";is Set<*>->"set";else->"string"}).put("value",if(value is Set<*>)JSONArray(value.toList()) else value))}})}}
    private fun validateSnapshot(j:JSONObject) {prefNames.forEach {name->val p=j.getJSONObject(name);require(p.length()<=500);p.keys().forEach {key->val v=p.getJSONObject(key);when(v.getString("type")){"bool"->v.getBoolean("value");"int"->v.getInt("value");"long"->v.getLong("value");"float"->require(v.getDouble("value").isFinite());"set"->{val a=v.getJSONArray("value");repeat(a.length()){a.getString(it)}};"string"->v.getString("value");else->error("Неизвестный тип настройки")}}}}
    private fun applySnapshot(c:Context,j:JSONObject){validateSnapshot(j);prefNames.forEach {name->val edit=c.getSharedPreferences(name,0).edit().clear();val p=j.getJSONObject(name);p.keys().forEach {key->val v=p.getJSONObject(key);when(v.getString("type")){"bool"->edit.putBoolean(key,v.getBoolean("value"));"int"->edit.putInt(key,v.getInt("value"));"long"->edit.putLong(key,v.getLong("value"));"float"->edit.putFloat(key,v.getDouble("value").toFloat());"set"->{val a=v.getJSONArray("value");edit.putStringSet(key,List(a.length()){a.getString(it)}.toSet())};"string"->edit.putString(key,v.getString("value"))}};check(edit.commit()){"Не удалось сохранить настройки"}}}
}
