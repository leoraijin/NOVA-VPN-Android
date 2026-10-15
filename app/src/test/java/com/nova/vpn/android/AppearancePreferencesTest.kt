package com.nova.vpn.android

import android.content.Context
import com.nova.vpn.android.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppearancePreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun hasExactlyFourStylesAndEightCombinations() {
        assertEquals(4, DesignStyle.entries.size)
        assertEquals(8, DesignStyle.entries.flatMap { s -> ThemeMode.entries.map { Appearance(s, it) } }.distinct().size)
    }

    @Test fun migrationPreservesEveryLegacyChoice() {
        val mappings = mapOf(
            "IOS_GLASS" to Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.LIGHT),
            "IOS_GLASS_DARK" to Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.DARK),
            "AMBER_GLASS" to Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.LIGHT),
            "ONE_UI" to Appearance(DesignStyle.ONE_UI, ThemeMode.LIGHT),
            "WINDOWS_LIGHT" to Appearance(DesignStyle.WINDOWS_11, ThemeMode.LIGHT),
            "WINDOWS_DARK" to Appearance(DesignStyle.WINDOWS_11, ThemeMode.DARK),
            "LAVENDER" to Appearance(DesignStyle.ONE_UI, ThemeMode.LIGHT),
            "SKY" to Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.LIGHT),
            "CREAM" to Appearance(DesignStyle.IOS_LIQUID_GLASS, ThemeMode.LIGHT),
            "NIGHT" to Appearance(DesignStyle.WINDOWS_11, ThemeMode.DARK)
        )
        mappings.forEach { (legacy, expected) ->
            listOf(false, true).forEach { dark -> assertEquals(legacy, expected, Appearance.migrate(legacy, dark)) }
        }
    }

    @Test fun freshInstallSnapshotsSystemModeOnlyOnce() {
        val first = AppPreferencesStore(context).appearance(true)
        assertEquals(ThemeMode.DARK, first.mode)
        assertEquals(first, AppPreferencesStore(context).appearance(false))
    }

    @Test fun materialMigrationSnapshotsSystemBrightness() {
        val store = AppPreferencesStore(context)
        store.setSelectedDesign("MATERIAL_YOU")
        assertEquals(Appearance(DesignStyle.MATERIAL_YOU, ThemeMode.DARK), store.appearance(true))
        assertEquals(ThemeMode.DARK, AppPreferencesStore(context).appearance(false).mode)
    }

    @Test fun allChoicesSurviveRestartWithoutChangingOtherPreferences() {
        val store = AppPreferencesStore(context)
        store.setAutoConnectEnabled(true)
        store.setUpdateChecksEnabled(false)
        store.dismissTutorial()
        DesignStyle.entries.forEach { style -> ThemeMode.entries.forEach { mode ->
            val expected = Appearance(style, mode)
            store.setAppearance(expected)
            val reopened = AppPreferencesStore(context)
            assertEquals(expected, reopened.appearance(mode != ThemeMode.DARK))
            assertTrue(reopened.autoConnectEnabled())
            assertFalse(reopened.updateChecksEnabled())
            assertTrue(reopened.tutorialDismissed())
        } }
    }

    @Test fun malformedPairRecoversDeterministicallyFromLegacyChoice() {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit()
            .putString("design_preset", "WINDOWS_DARK")
            .putString("appearance_style", "unknown").putString("appearance_mode", "DARK").commit()
        assertEquals(Appearance(DesignStyle.WINDOWS_11, ThemeMode.DARK), AppPreferencesStore(context).appearance(false))
    }
}
