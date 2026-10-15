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

    /** Migrate once, saving both dimensions in the same transaction. */
    @Synchronized
    fun appearance(systemDark: Boolean): Appearance {
        val style = preferences.getString("appearance_style", null)
            ?.let { value -> if (value == "AMBER_GLASS") DesignStyle.IOS_LIQUID_GLASS else DesignStyle.entries.firstOrNull { it.name == value } }
        val mode = preferences.getString("appearance_mode", null)
            ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
        if (style != null && mode != null) return Appearance(style, mode, preferences.getFloat("material_hue", 275f).let { if (it.isFinite()) it.coerceIn(0f, 360f) else 275f }, preferences.getFloat("glass_frost", .65f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else .65f })
            .also { if (preferences.getString("appearance_style", null) == "AMBER_GLASS") setAppearance(it) }
        return Appearance.migrate(preferences.getString(KEY_DESIGN, null), systemDark)
            .also { setAppearance(it) }
    }

    @Synchronized
    fun setAppearance(appearance: Appearance) {
        check(preferences.edit()
            .putString("appearance_style", appearance.style.name)
            .putString("appearance_mode", appearance.mode.name)
            .putFloat("material_hue", appearance.materialHue.coerceIn(0f, 360f))
            .putFloat("glass_frost", appearance.glassFrost.coerceIn(0f, 1f))
            .commit()) { "Не удалось сохранить оформление" }
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

