package com.nova.vpn.android.data

import android.content.Context

class AppPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun autoConnectEnabled(): Boolean = preferences.getBoolean(KEY_AUTO_CONNECT, false)

    fun tutorialDismissed(): Boolean = preferences.getBoolean("tutorial_dismissed", false)

    fun dismissTutorial() {
        check(preferences.edit().putBoolean("tutorial_dismissed", true).commit()) {
            "Не удалось сохранить завершение обучения"
        }
    }

    fun notificationPermissionRequested(): Boolean = preferences.getBoolean("notification_permission_requested", false)

    fun markNotificationPermissionRequested() {
        preferences.edit().putBoolean("notification_permission_requested", true).apply()
    }

    fun selectedDesign(): String = preferences.getString(KEY_DESIGN, DEFAULT_DESIGN) ?: DEFAULT_DESIGN

    fun setSelectedDesign(design: String) {
        check(preferences.edit().putString(KEY_DESIGN, design).commit()) {
            "Не удалось сохранить оформление"
        }
    }

    fun updateChecksEnabled(): Boolean = preferences.getBoolean(KEY_UPDATE_CHECKS, true)

    fun automaticUpdateCheckDue(now: Long, version: String): Boolean =
        UpdateCheckPolicy.isDue(preferences.getLong("last_update_check", 0L),
            preferences.getString("last_update_check_version", null), version, now)

    fun markUpdateCheck(now: Long, version: String) {
        preferences.edit().putLong("last_update_check", now).putString("last_update_check_version", version).apply()
    }

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

