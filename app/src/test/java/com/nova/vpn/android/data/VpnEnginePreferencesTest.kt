package com.nova.vpn.android.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VpnEnginePreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun freshInstallUsesSingBox() {
        assertEquals(VpnEngine.SING_BOX, AppPreferencesStore(context).vpnEngine())
    }

    @Test fun bothEnginesPersistUsingStableIdentifiers() {
        val store = AppPreferencesStore(context)
        store.setAutoConnectEnabled(true)
        store.setUpdateChecksEnabled(false)
        listOf(VpnEngine.REALITY_CORE to "reality_core", VpnEngine.SING_BOX to "sing_box").forEach { (engine, id) ->
            store.setVpnEngine(engine)
            assertEquals(id, preferences.getString("vpn_engine", null))
            val reopened = AppPreferencesStore(context)
            assertEquals(engine, reopened.vpnEngine())
            assertTrue(reopened.autoConnectEnabled())
            assertFalse(reopened.updateChecksEnabled())
        }
    }

    @Test fun futureValueFallsBackToSingBox() {
        preferences.edit().putString("vpn_engine", "future_engine_v3").commit()
        assertEquals(VpnEngine.SING_BOX, AppPreferencesStore(context).vpnEngine())
    }

    @Test fun emptyValueFallsBackToSingBox() {
        preferences.edit().putString("vpn_engine", "").commit()
        assertEquals(VpnEngine.SING_BOX, AppPreferencesStore(context).vpnEngine())
    }
}
