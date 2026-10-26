package com.nova.vpn.android.data

import android.content.Context

/** Stores only the chosen profile id; credentials remain in the encrypted vault. */
class SelectedProfileStore(context: Context) {
    private val recoveryContext = context.applicationContext
    private val preferences = context.getSharedPreferences("profile_selection", Context.MODE_PRIVATE)

    fun load(): String? = synchronized(ProfileVault.lock) { StoreRecoveryGuard.beforeAccess(recoveryContext); preferences.getString(KEY_SELECTED_PROFILE, null) }

    fun save(profileId: String) = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        check(preferences.edit().putString(KEY_SELECTED_PROFILE, profileId).commit()) {
            "Не удалось сохранить выбранный профиль"
        }
    }

    fun clear() = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        check(preferences.edit().remove(KEY_SELECTED_PROFILE).commit()) {
            "Не удалось очистить выбранный профиль"
        }
    }

    private companion object {
        const val KEY_SELECTED_PROFILE = "selected_profile_id"
    }
}
