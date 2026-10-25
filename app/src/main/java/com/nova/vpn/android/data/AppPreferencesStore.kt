package com.nova.vpn.android.data

import android.content.Context

class AppPreferencesStore(context: Context) {
    private val recoveryContext = context.applicationContext
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    private inline fun <T> stored(action: () -> T): T = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        action()
    }

    fun serverGrouping(): String = stored { preferences.getString("server_grouping", "NONE") ?: "NONE" }
    fun setServerGrouping(value: String) = stored {
        check(preferences.edit().putString("server_grouping", value).commit()) { "Не удалось сохранить группировку" }
    }
    fun serverSort(): String = stored { preferences.getString("server_sort", "MANUAL") ?: "MANUAL" }
    fun favoritesOnly(): Boolean = stored { preferences.getBoolean("server_favorites_only", false) }
    fun setServerListOptions(sort: String, favoritesOnly: Boolean) = stored {
        check(preferences.edit().putString("server_sort", sort).putBoolean("server_favorites_only", favoritesOnly).commit()) {
            "Не удалось сохранить настройки списка серверов"
        }
    }

    fun autoConnectEnabled(): Boolean = stored { preferences.getBoolean(KEY_AUTO_CONNECT, false) }

    fun vpnEngine(): VpnEngine = stored { VpnEngine.fromId(preferences.getString(KEY_VPN_ENGINE, null)) }

    fun setVpnEngine(engine: VpnEngine) = stored {
        check(preferences.edit().putString(KEY_VPN_ENGINE, engine.id).commit()) {
            "Не удалось сохранить VPN-движок"
        }
    }

    fun tutorialDismissed(): Boolean = stored { preferences.getBoolean("tutorial_dismissed", false) }

    fun dismissTutorial() = stored {
        check(preferences.edit().putBoolean("tutorial_dismissed", true).commit()) {
            "Не удалось сохранить завершение обучения"
        }
    }

    fun notificationPermissionRequested(): Boolean = stored { preferences.getBoolean("notification_permission_requested", false) }

    fun markNotificationPermissionRequested() = stored {
        check(preferences.edit().putBoolean("notification_permission_requested", true).commit()) { "Не удалось сохранить состояние разрешения уведомлений" }
    }

    /** Migrate once, saving both dimensions in the same transaction. */
    fun appearance(systemDark: Boolean): Appearance = stored {
        val style = preferences.getString("appearance_style", null)
            ?.let { value -> if (value == "AMBER_GLASS") DesignStyle.IOS_LIQUID_GLASS else DesignStyle.entries.firstOrNull { it.name == value } }
        val mode = preferences.getString("appearance_mode", null)
            ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
        if (style != null && mode != null) return@stored Appearance(style, mode, preferences.getFloat("material_hue", 275f).let { if (it.isFinite()) it.coerceIn(0f, 360f) else 275f }, preferences.getFloat("glass_frost", .65f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else .65f })
            .also { if (preferences.getString("appearance_style", null) == "AMBER_GLASS") setAppearance(it) }
        Appearance.migrate(preferences.getString(KEY_DESIGN, null), systemDark)
            .also { setAppearance(it) }
    }

    fun setAppearance(appearance: Appearance) = stored {
        check(preferences.edit()
            .putString("appearance_style", appearance.style.name)
            .putString("appearance_mode", appearance.mode.name)
            .putFloat("material_hue", appearance.materialHue.coerceIn(0f, 360f))
            .putFloat("glass_frost", appearance.glassFrost.coerceIn(0f, 1f))
            .commit()) { "Не удалось сохранить оформление" }
    }
    fun selectedDesign(): String = stored { preferences.getString(KEY_DESIGN, DEFAULT_DESIGN) ?: DEFAULT_DESIGN }

    fun setSelectedDesign(design: String) = stored {
        check(preferences.edit().putString(KEY_DESIGN, design).commit()) {
            "Не удалось сохранить оформление"
        }
    }

    fun updateChecksEnabled(): Boolean = stored { preferences.getBoolean(KEY_UPDATE_CHECKS, true) }

    fun automaticUpdateCheckDue(now: Long, version: String): Boolean = stored {
        UpdateCheckPolicy.isDue(preferences.getLong("last_update_check", 0L),
            preferences.getString("last_update_check_version", null), version, now)
    }

    fun markUpdateCheck(now: Long, version: String) = stored {
        check(preferences.edit().putLong("last_update_check", now).putString("last_update_check_version", version).commit()) { "Не удалось сохранить время проверки обновлений" }
    }

    fun setUpdateChecksEnabled(enabled: Boolean) = stored {
        check(preferences.edit().putBoolean(KEY_UPDATE_CHECKS, enabled).commit()) {
            "Не удалось сохранить настройку обновлений"
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) = stored {
        check(preferences.edit().putBoolean(KEY_AUTO_CONNECT, enabled).commit()) {
            "Не удалось сохранить настройку автоподключения"
        }
    }

    private companion object {
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_VPN_ENGINE = "vpn_engine"
        const val KEY_DESIGN = "design_preset"
        const val KEY_UPDATE_CHECKS = "update_checks_enabled"
        const val DEFAULT_DESIGN = "IOS_GLASS"
    }
}

