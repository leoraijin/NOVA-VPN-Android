package com.nova.vpn.android

/** A connection log is not evidence that the entire tunnel stopped carrying traffic. */
internal object ConnectionHealthReducer {
    fun transportNotice(state: VpnUiState, message: String): VpnUiState =
        if (state.connected) state.copy(transportNotice = message) else state

    fun probeResult(state: VpnUiState, success: Boolean): VpnUiState =
        if (state.connected) state.copy(trafficChecking = false, trafficVerified = success,
            trafficCheckFailed = !success, warning = null,
            transportNotice = if (success) null else state.transportNotice) else state
}
