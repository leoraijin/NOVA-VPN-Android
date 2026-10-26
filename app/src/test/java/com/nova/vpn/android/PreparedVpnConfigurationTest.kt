package com.nova.vpn.android

import com.nova.vpn.android.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreparedVpnConfigurationTest {
    @Test fun configurationAndIdentityRemainBoundAfterActivePresetChanges() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("routing", 0).edit().clear().commit()
        val store = AppRoutingStore(context)
        store.save(AppRoutingSettings(packages = setOf("org.example.old")))
        val profile = ImportedProfile(id = "synthetic", name = "Fixture", protocol = "vless", server = "example.test", port = 443,
            outboundJson = "{\"type\":\"vless\",\"server\":\"example.test\",\"server_port\":443,\"uuid\":\"11111111-1111-4111-8111-111111111111\"}")
        val prepared = PreparedVpnConfiguration.prepare(context, profile)
        store.updatePresets { it.copy(activeId = "other", presets = it.presets + RoutingPreset("other", "Other", AppRoutingSettings(packages = setOf("org.example.new")))) }
        assertEquals("my-routing", prepared.routingPresetId)
        assertEquals("synthetic", prepared.profileId)
        assertTrue(prepared.config.contains("org.example.old"))
        assertFalse(prepared.config.contains("org.example.new"))
        assertEquals("other", PreparedVpnConfiguration.prepare(context, profile).routingPresetId)
    }
}
