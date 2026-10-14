package com.nova.vpn.android.data

import android.content.Context

class AppPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun autoConnectEnabled(): Boolean = preferences.getBoolean(KEY_AUTO_CONNECT, false)

    fun selectedDesign(): String = preferences.getString(KEY_DESIGN, DEFAULT_DESIGN) ?: DEFAULT_DESIGN

    fun setSelectedDesign(design: String) {
        check(preferences.edit().putString(KEY_DESIGN, design).commit()) {
            "Не удалось сохранить оформление"
        }
    }

    fun updateChecksEnabled(): Boolean = preferences.getBoolean(KEY_UPDATE_CHECKS, true)

    fun setUpdateChecksEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean(KEY_UPDATE_CHECKS, enabled).commit()) {
            "Не удалось сохранить настройку обновлений"
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean(KEY_AUTO_CONNECT, enabled).commit()) {
            "Не удалось сохранить настройку автоподключения"
        }
    }

    private companion object {
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_DESIGN = "design_preset"
        const val KEY_UPDATE_CHECKS = "update_checks_enabled"
        const val DEFAULT_DESIGN = "IOS_GLASS"
    }
}
