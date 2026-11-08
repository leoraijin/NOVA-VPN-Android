package com.nova.vpn.android.data

import android.content.Context

/** New selection and routing share one SharedPreferences commit; old selection remains read-compatible. */
class SelectedProfileStore(private val context: Context) {
    private val routing=context.getSharedPreferences("routing",Context.MODE_PRIVATE)
    private val legacy=context.getSharedPreferences("profile_selection",Context.MODE_PRIVATE)
    fun load(): String? = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.beforeAccess(context)
        (if(routing.contains(KEY_SELECTED))routing.getString(KEY_SELECTED,null) else legacy.getString("selected_profile_id",null))?.takeIf(String::isNotBlank)
    }
    fun resolveId(profiles: List<ImportedProfile>): String? = synchronized(ProfileVault.lock) {
        val selected=load()
        selected?:if(routing.contains(KEY_SELECTED))null else profiles.firstOrNull()?.id
    }
    fun save(profileId: String) {require(profileId.isNotBlank()&&profileId.length<=128);AppRoutingStore(context).selectProfile(RoutingSelectionChange(profileId))}
    fun clear() {AppRoutingStore(context).selectProfile(RoutingSelectionChange(null))}
    internal companion object {const val KEY_SELECTED="selected_profile_id_v2"}
}
