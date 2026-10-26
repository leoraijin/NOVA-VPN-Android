package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutingPresetStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() { context.getSharedPreferences("routing", 0).edit().clear().commit() }

    @Test fun readingLegacyDoesNotRewriteAndStageDoesNotChangeActivePreferences() {
        val prefs = context.getSharedPreferences("routing", 0)
        prefs.edit().putString("mode", "INCLUDE_SELECTED").putStringSet("packages", setOf("org.example.old")).commit()
        val before = prefs.all.toMap()
        val store = AppRoutingStore(context)
        val old = store.load()
        assertEquals(before, prefs.all)
        store.updatePresets { it.copy(presets = it.presets + RoutingPreset("new", "Новый", AppRoutingSettings())) }
        store.stage("new", "request")
        assertEquals(old, AppRoutingStore(context).load())
        assertEquals("INCLUDE_SELECTED", prefs.getString("mode", null))
        assertFalse(store.commitPending("stale", "new"))
        assertTrue(store.commitPending("request", "new"))
        assertEquals(AppRoutingSettings(), AppRoutingStore(context).load())
        assertEquals("EXCLUDE_SELECTED", prefs.getString("mode", null))
        assertNull(store.loadPresets().pending)
    }

    @Test fun cancellingPendingChangeKeepsOldRulesAndDoesNotCancelNewRequest() {
        val store = AppRoutingStore(context)
        val old = store.load()
        store.stage("my-routing", "old", old.copy(ipv6Enabled = true))
        store.stage("my-routing", "new", old.copy(ipv6Enabled = true))
        store.cancelPending("old")
        assertEquals("new", store.loadPresets().pending!!.requestId)
        store.cancelPending("new")
        assertNull(store.loadPresets().pending)
        assertEquals(old, store.load())
    }

    @Test fun invalidPersistentDocumentIsNeverOverwrittenWithDefaults() {
        val prefs = context.getSharedPreferences("routing", 0)
        prefs.edit().putString("routing_presets_v1", "{corrupt").commit()
        assertThrows(Exception::class.java) { AppRoutingStore(context).save(AppRoutingSettings()) }
        assertEquals("{corrupt", prefs.getString("routing_presets_v1", null))
    }

    @Test fun supersedingStartOrStopClearsAStageWhoseStartWasNeverDelivered() {
        val store = AppRoutingStore(context)
        val original = store.load()
        store.stage("my-routing", "not-delivered", original.copy(ipv6Enabled = true))
        store.cancelPendingExcept("not-delivered")
        assertNotNull(store.loadPresets().pending)
        store.cancelPendingExcept("new-start")
        assertNull(store.loadPresets().pending)
        assertEquals(original, store.load())
        store.stage("my-routing", "another-not-delivered", original.copy(ipv6Enabled = true))
        store.cancelPendingExcept(null)
        assertNull(store.loadPresets().pending)
        assertEquals(original, store.load())
    }
}
