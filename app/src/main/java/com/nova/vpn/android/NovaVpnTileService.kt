package com.nova.vpn.android

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import com.nova.vpn.android.data.AppRoutingStore
import com.nova.vpn.android.data.ProfileVault
import com.nova.vpn.android.data.SelectedProfileStore
import com.nova.vpn.android.data.SingBoxConfigFactory

class NovaVpnTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val state = VpnRuntime.state.value
        if (state.connected || state.connecting) {
            if (pendingStop?.let(VpnUserRequests.gate::isCurrent) == true) return
            pendingStop = VpnUserRequests.stop(this)
            refreshTile()
            return
        }

        val appContext = applicationContext
        val request = VpnUserRequests.gate.begin()
        tileScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {PreparedVpnConfiguration.prepareSelected(appContext)}
            }.onSuccess { prepared ->
                withContext(Dispatchers.Main) {
                    if (!VpnUserRequests.gate.isCurrent(request)) return@withContext
                    if (VpnService.prepare(this@NovaVpnTileService) != null) {
                        openAppForConsent(request)
                    } else {
                        VpnUserRequests.start(this@NovaVpnTileService, prepared, request)
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) { if (VpnUserRequests.gate.isCurrent(request)) openAppForConsent(request) }
            }
            withContext(Dispatchers.Main) { refreshTile() }
        }
    }

    private fun openAppForConsent(request: String) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_CONNECT_FROM_TILE)
            .putExtra(VpnUserRequests.EXTRA_REQUEST, request)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        val vpnState = VpnRuntime.state.value
        updateTile(vpnState, null)
        refreshJob?.cancel()
        refreshJob = tileScope.launch {
            val catalog = withContext(Dispatchers.IO) {
                runCatching {
                    synchronized(ProfileVault.lock) {
                        val profiles=ProfileVault(applicationContext).load()
                        Triple(profiles,
                            SelectedProfileStore(applicationContext).resolveId(profiles),
                            AppRoutingStore(applicationContext).loadPresets().active.settings.multiServer?.defaultProfileId)
                    }
                }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                // Resolve AFTER IO against the current acknowledged session, not the state before loading.
                val current = VpnRuntime.state.value
                val id = ConnectionProfileSelection.id(current, catalog?.second, catalog?.third)
                val name = catalog?.first?.firstOrNull { it.id == id }?.displayName?.take(28)
                updateTile(current, name)
            }
        }
    }

    private fun updateTile(vpnState: VpnUiState, profileName: String?) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_vpn)
        val stopping = (vpnState.connected || vpnState.connecting) &&
            pendingStop?.let(VpnUserRequests.gate::isCurrent) == true
        if (!stopping) pendingStop = null
        val presentation = if (stopping) {
            NovaTilePresentation(false, "Отключение…", "NOVA VPN. Отключение…")
        } else {
            NovaTilePresentation.from(vpnState, profileName)
        }
        tile.state = if (presentation.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = presentation.description
        tile.label = "NOVA VPN"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = presentation.subtitle
        }
        tile.updateTile()
    }

    companion object {
        private var pendingStop: String? = null

        const val ACTION_CONNECT_FROM_TILE = "com.nova.vpn.android.CONNECT_FROM_TILE"

        fun requestRefresh(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { requestListeningState(context, ComponentName(context, NovaVpnTileService::class.java)) }
            }
        }
    }
}

