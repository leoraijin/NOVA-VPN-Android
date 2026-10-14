package com.nova.vpn.android.wear

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nova.vpn.android.NovaVpnService
import com.nova.vpn.android.VpnRuntime
import com.nova.vpn.android.data.AppRoutingStore
import com.nova.vpn.android.data.ProfileVault
import com.nova.vpn.android.data.SelectedProfileStore
import com.nova.vpn.android.data.SingBoxConfigFactory
import org.json.JSONObject
import java.util.UUID

/** Optional watch remote. Replies confirm handling, not merely Bluetooth delivery. */
class WearCommandListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearStatusSync.TOGGLE_PATH) return
        val requestId = WearCommandProtocol.requestId(messageEvent.data) ?: return
        // Empty messages remain compatible with older watch clients.
        val result = runCatching { togglePhoneTunnel() }.getOrElse { error ->
            Log.w(TAG, "Wear command failed: ${error.javaClass.simpleName}")
            if (error.javaClass.simpleName == "ForegroundServiceStartNotAllowedException") "foreground_required" else "error"
        }
        WearStatusSync.publish(this, result)
        if (requestId.isNotEmpty()) {
            val response = WearCommandProtocol.reply(requestId, result)
            runCatching {
                Wearable.getMessageClient(this).sendMessage(messageEvent.sourceNodeId, "/nova/command-result", response)
                    .addOnFailureListener { Log.d(TAG, "Unable to deliver command acknowledgement") }
            }
        }
    }

    private fun togglePhoneTunnel(): String {
        val state = VpnRuntime.state.value
        if (state.connected || state.connecting) {
            startService(Intent(this, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP))
            return "disconnecting"
        }
        if (VpnService.prepare(this) != null) return "permission_required"
        val profiles = ProfileVault(this).load()
        val selectedId = SelectedProfileStore(this).load()
        val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
            ?: return "profile_required"
        val config = SingBoxConfigFactory.build(profile, AppRoutingStore(this).load(), packageName)
        val intent = Intent(this, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_START)
            .putExtra(NovaVpnService.EXTRA_CONFIG, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        return "connecting"
    }

    private companion object { const val TAG = "NovaWearCommand" }
}
