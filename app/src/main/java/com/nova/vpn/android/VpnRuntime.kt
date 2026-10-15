package com.nova.vpn.android

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val trafficChecking: Boolean = false,
    val trafficVerified: Boolean = false,
    val trafficCheckFailed: Boolean = false,
    val transportNotice: String? = null
)

/** In-process connection state shared by the activity and the VPN service. */
object VpnRuntime {
    @Volatile private var appContext: Context? = null
    private val mutableState = MutableStateFlow(VpnUiState())
    val state: StateFlow<VpnUiState> = mutableState.asStateFlow()

    fun attach(context: Context) { appContext = context.applicationContext }

    fun setConnecting() = publish(VpnUiState(connecting = true))
    fun setConnected() = publish(VpnUiState(connected = true))
    fun setTrafficChecking() { if (mutableState.value.connected) publish(mutableState.value.copy(trafficChecking = true, trafficVerified = false, trafficCheckFailed = false, warning = null)) }
    fun setTrafficResult(success: Boolean) {
        val current = mutableState.value
        if (current.connected) publish(ConnectionHealthReducer.probeResult(current, success))
    }
    fun setDisconnected() = publish(VpnUiState())
    fun setFailure(message: String) = publish(VpnUiState(error = message))
    fun setTransportWarning(message: String) {
        val current = mutableState.value
        if (current.connected && current.transportNotice != message) {
            publish(ConnectionHealthReducer.transportNotice(current, message))
        }
    }

    private fun publish(next: VpnUiState) {
        val previous = mutableState.value
        if (previous == next) return
        mutableState.value = next
        // A transport notice does not change the tile. Avoid waking SystemUI and
        // decrypting the profile vault again for every different core warning.
        if (NovaTilePresentation.from(previous, null) != NovaTilePresentation.from(next, null)) {
            appContext?.let(NovaVpnTileService::requestRefresh)
        }
    }
}
