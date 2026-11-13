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
        val profiles=JSONArray().apply { exportedProfiles.profiles.forEach { p->put(JSONObject().put("id",p.id).put("name",p.name).put("protocol",p.protocol).put("server",p.server).put("port",p.port).put("outboundJson",p.outboundJson).put("fullConfigJson",p.fullConfigJson?:JSONObject.NULL).put("subscriptionUrl",p.subscriptionUrl?:JSONObject.NULL).put("userSettings",p.userSettings.toJson()).put("retiredFromSubscription",p.retiredFromSubscription)) } }
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
        repeat(profiles.length()) {
            val profile = profiles.getJSONObject(it)
            require(profile.isNull("retiredFromSubscription") || profile.get("retiredFromSubscription") is Boolean) {
                "Некорректная доступность профиля."
            }
        }
        require(d.getJSONObject("platforms").length()<=4);d.getBoolean("dark");d.getBoolean("ipv6");require(d.getString("selectedId").length<=128)
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
    private fun legacyWindowsRetirements(windows: String?): Set<String> {
        val profiles = windows?.takeIf(String::isNotBlank)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optJSONArray("Profiles") ?: return emptySet()
        val ids = mutableSetOf<String>()
        val retired = mutableSetOf<String>()
        repeat(profiles.length()) {
            val profile = profiles.getJSONObject(it)
            val id = profile.getString("Id")
            require(id.isNotBlank() && ids.add(id)) { "Некорректный каталог Windows в копии переноса." }
            if (!profile.isNull("RetiredFromSubscription")) {
                val unavailable = profile.get("RetiredFromSubscription")
                require(unavailable is Boolean) { "Некорректная доступность профиля Windows." }
                if (unavailable) retired += id
            }
        }
        return retired
    }

    internal fun restoreProfiles(profiles: JSONArray, native: JSONObject?, windows: String? = null): ProfileSnapshot {
        val embedded = native?.optString("profileDocument")?.takeIf(String::isNotBlank)?.let(ProfileDocument::decodeSnapshot)
        val legacy = if (embedded == null && (0 until profiles.length()).any {
                profiles.getJSONObject(it).isNull("retiredFromSubscription")
            }) legacyWindowsRetirements(windows) else emptySet()
        val retired = mutableSetOf<String>()
        val incoming = List(profiles.length()) { index ->
            val profile = profiles.getJSONObject(index)
            val id = profile.getString("id")
            if (profile.optBoolean("retiredFromSubscription", id in legacy)) retired += id
            ImportedProfile(id = id, name = profile.getString("name"), protocol = profile.getString("protocol"),
                server = profile.getString("server"), port = profile.getInt("port"),
                outboundJson = if (profile.isNull("outboundJson")) "{}" else profile.getString("outboundJson"),
                fullConfigJson = if (profile.isNull("fullConfigJson")) null else profile.getString("fullConfigJson"),
                subscriptionUrl = if (profile.isNull("subscriptionUrl")) null else profile.getString("subscriptionUrl"),
                userSettings = ProfileUserSettings.fromJson(profile.optJSONObject("userSettings")))
        }
        val snapshot = embedded ?: ProfileSnapshot.migrate(incoming)
        require(snapshot.profiles.map { it.id }.toSet() == incoming.map { it.id }.toSet())
        // The portable marker can revoke ownership. Only native ownership or a
        // confirmed subscription refresh can make an embedded tombstone available.
        return snapshot.copy(manuallyOwned = snapshot.manuallyOwned - retired,
            memberships = snapshot.memberships.filterNot { it.profileId in retired }.toSet()).withAvailability()
    }

    fun restore(context:Context,d:JSONObject) {
        // All parsing and checks happen before changing persistent data.
        validateDocument(d)

        val platforms=d.getJSONObject("platforms");val identity=IdentityState.fromJson(d.getJSONObject("identity")).copy(platforms=platforms.keys().asSequence().associateWith{platforms.getString(it)})
        val sites=listOf("directDomains" to SiteRoutingTarget.DIRECT,"proxyDomains" to SiteRoutingTarget.VPN).flatMap {(key,target)->val a=d.getJSONArray(key);List(a.length()){SiteRoutingRule(SiteDomain.normalize(a.getString(it))!!,target)}}
        val native=platforms.optString("android").takeIf {it.isNotBlank()}?.let {transferSnapshot(JSONObject(it))}
        val restoredSnapshot = restoreProfiles(d.getJSONArray("profiles"), native, platforms.optString("windows"))
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
            ProfileVault(context).restoreSnapshot(restoredSnapshot);IdentityVault(context).save(identity)
            val current=AppRoutingStore(context).load();AppRoutingStore(context).save(current.copy(sites=sites,ipv6Enabled=d.getBoolean("ipv6")))
            AppPreferencesStore(context).setAutoConnectEnabled(false)
            check(context.getSharedPreferences("app_preferences",0).edit().putString("appearance_mode",if(d.getBoolean("dark"))"DARK" else "LIGHT").commit())
            val selected=d.getString("selectedId");if(selected.isBlank())SelectedProfileStore(context).clear() else SelectedProfileStore(context).save(selected)
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
    /** Shares the encrypted restore journal so profile deletion and route cleanup recover together. */
    internal fun deleteAllConfigurations(context: Context, checkpoint: (String) -> Unit = {}) = synchronized(ProfileVault.lock) {
        val vault=ProfileVault(context)
        val oldProfiles=vault.loadSnapshot() // Refuse corrupt stores before changing preferences or generation.
        val before=snapshot(context)
        val oldIdentity=IdentityVault(context).load()
        val journal=TransferRestoreJournal(context)
        StoreRecoveryGuard.transaction {
            journal.prepare(JSONObject().put("schema",1).put("preferences",before)
                .put("profiles",ProfileDocument.encodeSnapshot(oldProfiles)).put("identity",oldIdentity.toJson()))
            try {
                ProfileVault.invalidatePendingUpdates()
                vault.restoreSnapshot(ProfileSnapshot())
                checkpoint("profiles-cleared")
                AppRoutingStore(context).updatePresets { it.withoutServerAssignments() }
                checkpoint("assignments-cleared")
                SelectedProfileStore(context).clear()
                checkpoint("selection-cleared")
            } catch(error: Exception) {
                try {
                    applySnapshot(context,before)
                    vault.restoreSnapshot(oldProfiles)
                    journal.clear()
                    StoreRecoveryGuard.completed()
                } catch(rollback: Exception) {
                    throw IllegalStateException("Не удалось завершить удаление. Восстановление будет повторено при следующем обращении.",rollback)
                }
                throw error
            }
            // As in restore, once journal deletion starts, do not begin a second rollback.
            journal.clear()
            StoreRecoveryGuard.completed()
        }
    }
    /** Import shared keys/rules together; preserve local appearance, identities and old presets. */
    internal fun restoreShared(context: Context, incoming: RoutingSharePackage, choices: Map<String,String>) {
        com.nova.vpn.android.VpnUserRequests.gate.restoreWhenIdle(canRestore = {
            val state=com.nova.vpn.android.VpnRuntime.state.value
            !state.connected && !state.connecting && state.recoveryStatus==null
        }) {
            synchronized(ProfileVault.lock) {
                recoverPending(context)
                val vault=ProfileVault(context)
                val oldProfiles=vault.loadSnapshot()
                val plan=RoutingShare.plan(incoming,AppRoutingStore(context).loadPresets(),oldProfiles,choices)
                if(!incoming.includesKeys) {
                    writeRoutingAtomically(context) { AppRoutingStore(context).updatePresets(plan.selection) { plan.routing } }
                } else {
                    val before=JSONObject().put("schema",3).put("routing",snapshot(context).getJSONObject("routing"))
                        .put("profiles",ProfileDocument.encodeSnapshot(oldProfiles))
                    StoreRecoveryGuard.transaction {
                        RoutingPreferenceTransaction.runRecord(before, {
                            ProfileVault.invalidatePendingUpdates()
                            vault.restoreSnapshot(plan.profiles)
                            AppRoutingStore(context).updatePresets(plan.selection) { plan.routing }
                        }, { replaySharedPreimage(context,it) }, TransferRestoreJournal(context), StoreRecoveryGuard::completed)
                    }
                }
            }
        }
    }
    private val prefNames=listOf("app_preferences","routing","profile_selection")
    /** Startup retries this idempotent rollback until every pre-image has been restored. */
    internal fun recoverPending(context: Context): Boolean = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.transaction {
        val journal=TransferRestoreJournal(context)
        val previous=journal.read()
        if(previous==null) { StoreRecoveryGuard.completed(); return@transaction false }
        if(previous.getInt("schema")==2) {
            replayRoutingPreimage(context,previous);journal.clear();StoreRecoveryGuard.completed();return@transaction true
        }
        if(previous.getInt("schema")==3) {
            replaySharedPreimage(context,previous);journal.clear();StoreRecoveryGuard.completed();return@transaction true
        }
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
    private fun applySnapshot(c:Context,j:JSONObject){validateSnapshot(j);prefNames.forEach {name->applyPreference(c,name,j.getJSONObject(name))}}
    private fun applyPreference(c:Context,name:String,p:JSONObject) {
        val edit=c.getSharedPreferences(name,0).edit().clear()
        p.keys().forEach {key->val v=p.getJSONObject(key);when(v.getString("type")){
            "bool"->edit.putBoolean(key,v.getBoolean("value"));"int"->edit.putInt(key,v.getInt("value"));"long"->edit.putLong(key,v.getLong("value"));
            "float"->edit.putFloat(key,v.getDouble("value").toFloat());"set"->{val array=v.getJSONArray("value");edit.putStringSet(key,List(array.length()){array.getString(it)}.toSet())};
            "string"->edit.putString(key,v.getString("value"));else->error("Неизвестный тип настройки")}}
        check(edit.commit()) {"Не удалось сохранить настройки"}
    }
    private fun validateRoutingPreimage(routing: JSONObject) {
        validateSnapshot(JSONObject().put("app_preferences",JSONObject()).put("routing",routing).put("profile_selection",JSONObject()))
        routing.optJSONObject("routing_presets_v1")?.let {require(it.getString("type")=="string");RoutingPresets.fromJson(JSONObject(it.getString("value")))}
    }
    private fun replaySharedPreimage(context: Context, record: JSONObject) {
        check(Thread.holdsLock(ProfileVault.lock))
        require(record.getInt("schema")==3)
        val routing=record.getJSONObject("routing");validateRoutingPreimage(routing)
        val profiles=ProfileDocument.decodeSnapshot(record.getString("profiles"))
        ProfileVault.invalidatePendingUpdates()
        ProfileVault(context).restoreSnapshot(profiles)
        applyPreference(context,"routing",routing)
    }
    internal fun replayRoutingPreimage(context:Context,record:JSONObject) {
        check(Thread.holdsLock(ProfileVault.lock))
        require(record.getInt("schema")==2)
        val routing=record.getJSONObject("routing");validateRoutingPreimage(routing);applyPreference(context,"routing",routing)
    }
    internal fun writeRoutingAtomically(context:Context,write:()->Unit) = synchronized(ProfileVault.lock) {
        if(StoreRecoveryGuard.inTransaction){write();return@synchronized}
        val before=snapshot(context).getJSONObject("routing");validateRoutingPreimage(before)
        StoreRecoveryGuard.transaction {
            RoutingPreferenceTransaction.run(before,write,{applyPreference(context,"routing",it)},TransferRestoreJournal(context),StoreRecoveryGuard::completed)
        }
    }
}
