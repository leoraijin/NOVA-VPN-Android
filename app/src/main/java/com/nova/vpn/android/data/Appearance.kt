package com.nova.vpn.android.data

/** Style and brightness are independent persisted choices. */
enum class DesignStyle(val label: String, val cardRadius: Int) {
    IOS_LIQUID_GLASS("iOS 27 · Liquid Glass", 22),
    MATERIAL_YOU("Pixel", 28),
    ONE_UI("One UI 9", 28),
    WINDOWS_11("Windows 11", 8)
}

enum class ThemeMode { LIGHT, DARK }

data class Appearance(val style: DesignStyle, val mode: ThemeMode, val materialHue: Float = 275f, val glassFrost: Float = .65f) {
    val isDark: Boolean get() = mode == ThemeMode.DARK

    companion object {
        fun migrate(legacy: String?, systemDark: Boolean): Appearance {
            val systemMode = if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
            return when (legacy) {
                "IOS_GLASS", "SKY" -> Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.LIGHT)
                "IOS_GLASS_DARK" -> Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.DARK)
                "MATERIAL_YOU" -> Appearance(DesignStyle.MATERIAL_YOU, systemMode)
                "AMBER_GLASS", "CREAM" -> Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.LIGHT)
                "ONE_UI", "LAVENDER" -> Appearance(DesignStyle.ONE_UI, ThemeMode.LIGHT)
                "WINDOWS_LIGHT" -> Appearance(DesignStyle.WINDOWS_11, ThemeMode.LIGHT)
                "WINDOWS_DARK", "NIGHT" -> Appearance(DesignStyle.WINDOWS_11, ThemeMode.DARK)
                else -> Appearance(DesignStyle.IOS_LIQUID_GLASS, systemMode)
            }
        }
    }
}
