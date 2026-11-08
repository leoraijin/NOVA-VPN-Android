package com.nova.vpn.android.data

import org.json.JSONObject
import java.util.Collections

/** No nested AppRoutingSettings: a saved ordinary snapshot cannot contain another history. */
class SingleServerRouteSnapshot(val mode: AppRoutingMode, packages: Set<String>, sites: List<SiteRoutingRule>,
    val ipv6Enabled: Boolean, val profileId: String?) {
    val packages: Set<String> = Collections.unmodifiableSet(LinkedHashSet(packages))
    val sites: List<SiteRoutingRule> = Collections.unmodifiableList(sites.toList())
    init {
        RoutingSelectionChange(profileId)
        RoutingPresets.validateSettings(AppRoutingSettings(mode,this.packages,this.sites,ipv6Enabled))
    }
    fun settings(): AppRoutingSettings=AppRoutingSettings(mode,packages,sites,ipv6Enabled)
    fun withProfile(id: String?)=SingleServerRouteSnapshot(mode,packages,sites,ipv6Enabled,id)
    fun toJson(): JSONObject=RoutingPresets.settingsJson(settings()).put("profileId",profileId?:JSONObject.NULL)
    override fun equals(other: Any?)=other is SingleServerRouteSnapshot && mode==other.mode&&packages==other.packages&&sites==other.sites&&ipv6Enabled==other.ipv6Enabled&&profileId==other.profileId
    override fun hashCode()=listOf(mode,packages,sites,ipv6Enabled,profileId).hashCode()
    companion object {
        fun capture(settings: AppRoutingSettings,id: String?)=SingleServerRouteSnapshot(settings.mode,settings.packages,settings.sites,settings.ipv6Enabled,id?.takeIf(String::isNotBlank))
        fun fromJson(json: JSONObject): SingleServerRouteSnapshot {
            require((!json.has("multiServer")||json.isNull("multiServer"))&&(!json.has("history")||json.isNull("history"))) { "Вложенный снимок маршрутизации запрещён" }
            val settings=RoutingPresets.readSettings(json)
            return capture(settings,if(json.isNull("profileId"))null else json.getString("profileId"))
        }
    }
}

class MultiServerRoutingHistory(val single: SingleServerRouteSnapshot?, savedAssignments: MultiServerSettings) {
    val savedAssignments=savedAssignments.copy(rules=Collections.unmodifiableList(savedAssignments.rules.toList()))
    init {this.savedAssignments.validate()}
    fun withProfile(id: String?)=MultiServerRoutingHistory(single?.withProfile(id),savedAssignments)
    fun toJson(): JSONObject=JSONObject().put("schema",1).put("single",single?.toJson()?:JSONObject.NULL).put("assignments",savedAssignments.toJson())
    override fun equals(other: Any?)=other is MultiServerRoutingHistory&&single==other.single&&savedAssignments==other.savedAssignments
    override fun hashCode()=31*(single?.hashCode()?:0)+savedAssignments.hashCode()
    companion object {
        fun fromJson(json: JSONObject): MultiServerRoutingHistory {
            require(json.getInt("schema")==1&&json.has("single"))
            require(json.isNull("single")||json.get("single") is JSONObject)
            return MultiServerRoutingHistory(json.optJSONObject("single")?.let(SingleServerRouteSnapshot::fromJson),MultiServerSettings.fromJson(json.getJSONObject("assignments")))
        }
    }
}

internal data class RoutingSelectionChange(val profileId: String?) {
    init {require(profileId==null||profileId.isNotBlank()&&profileId.length<=128)}
    fun toJson(): JSONObject=JSONObject().put("profileId",profileId?:JSONObject.NULL)
    companion object {fun fromJson(json: JSONObject)=RoutingSelectionChange(if(json.isNull("profileId"))null else json.getString("profileId"))}
}
internal data class RoutingModeChange(val baseline: AppRoutingSettings,val settings: AppRoutingSettings,val selection: RoutingSelectionChange?=null)

internal object RoutingModeTransitions {
    fun needsMigrationReview(settings: AppRoutingSettings,resuming: Boolean=false)=settings.multiServer==null&&!resuming&&
        (settings.mode==AppRoutingMode.INCLUDE_SELECTED||settings.packages.isNotEmpty()||settings.sites.isNotEmpty())
    fun enable(settings: AppRoutingSettings,selectedId: String?,primary: String,rules: List<MultiServerRule>,ipv6: Boolean): AppRoutingSettings {
        val multi=MultiServerSettings((settings.multiServer?.revision?:settings.history?.savedAssignments?.revision?:0)+1,primary,rules.toList()).also {it.validate()}
        val previous=if(settings.multiServer==null)SingleServerRouteSnapshot.capture(settings,selectedId) else settings.history?.single
        return settings.copy(packages=emptySet(),sites=emptyList(),ipv6Enabled=ipv6,multiServer=multi,history=MultiServerRoutingHistory(previous,multi))
    }
    fun disable(settings: AppRoutingSettings): RoutingModeChange {
        val multi=requireNotNull(settings.multiServer)
        val single=settings.history?.single?:SingleServerRouteSnapshot.capture(settings.copy(multiServer=null,history=null),multi.defaultProfileId)
        return RoutingModeChange(settings,single.settings().copy(history=MultiServerRoutingHistory(single,multi)),RoutingSelectionChange(single.profileId))
    }
    fun forget(settings: AppRoutingSettings): RoutingModeChange {
        require(settings.multiServer==null)
        return RoutingModeChange(settings,settings.copy(history=null))
    }
    fun selectionFor(settings: AppRoutingSettings)=if(settings.multiServer==null)settings.history?.single?.let {RoutingSelectionChange(it.profileId)} else null
}
