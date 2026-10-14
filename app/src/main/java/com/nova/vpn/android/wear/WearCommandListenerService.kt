package com.nova.vpn.android.wear

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nova.vpn.android.NovaVpnService
import com.nova.vpn.android.VpnRuntime
import com.nova.vpn.android.data.AppRoutingStore
import com.nova.vpn.android.data.ProfileVault
import com.nova.vpn.android.data.SelectedProfileStore
import com.nova.vpn.android.data.SingBoxConfigFactory

/** A watch remote: it can toggle an already-authorized phone tunnel, but never receives a VPN profile. */
class WearCommandListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearStatusSync.TOGGLE_PATH) return
        togglePhoneTunnel()
    }

    private fun togglePhoneTunnel() {
        val state = VpnRuntime.state.value
        if (state.connected || state.connecting) {
            runCatching {
                startService(Intent(this, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP))
                WearStatusSync.publish(this, "disconnecting")
            }.onFailure { publishFailure(it) }
            return
        }

        if (VpnService.prepare(this) != null) {
            WearStatusSync.publish(this, "permission_required")
            return
        }

        runCatching {
            val profiles = ProfileVault(this).load()
            val selectedId = SelectedProfileStore(this).load()
            val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
            if (profile == null) {
                WearStatusSync.publish(this, "profile_required")
                return
            }
            val config = SingBoxConfigFactory.build(profile, AppRoutingStore(this).load(), packageName)
            val intent = Intent(this, NovaVpnService::class.java)
                .setAction(NovaVpnService.ACTION_START)
                .putExtra(NovaVpnService.EXTRA_CONFIG, config)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            WearStatusSync.publish(this, "connecting")
        }.onFailure { publishFailure(it) }
    }

    private fun publishFailure(error: Throwable) {
        Log.w(TAG, "Wear command failed", error)
        WearStatusSync.publish(this, "error")
    }

    private companion object {
        const val TAG = "NovaWearCommand"
    }
}
