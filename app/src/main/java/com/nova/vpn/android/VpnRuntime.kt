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
    val transportNotice: String? = null,
    val recoveryStatus: String? = null,
    val recoveryAttempt: Int = 0,
    val sessionRevision: Long = 0,
    val appliedConfiguration: AppliedConfiguration? = null,
    val exitChecks: Map<String,ExitCheckState> = emptyMap(),
    val activeRoutePreview: ActiveRoutePreview? = null
)

/** In-process connection state shared by the activity and the VPN service. */
object VpnRuntime {
    @Volatile private var appContext: Context? = null
    private val mutableState = MutableStateFlow(VpnUiState())
    private val sessionSequence = java.util.concurrent.atomic.AtomicLong()
    val state: StateFlow<VpnUiState> = mutableState.asStateFlow()

    fun attach(context: Context) { appContext = context.applicationContext }

    @Synchronized fun setConnecting() = publish(VpnUiState(connecting = true))
    @Synchronized fun setConnected(identity: AppliedConfiguration? = null, preview: ActiveRoutePreview? = null) =
        publish(VpnUiState(connected = true, sessionRevision = sessionSequence.incrementAndGet(),
            appliedConfiguration = identity, activeRoutePreview = preview))
    /** A rejected replacement never changed the native tunnel or this completed health evidence. */
    @Synchronized internal fun restoreUntouchedConnection(previous: VpnUiState) {
        check(previous.connected && previous.recoveryStatus == null && !previous.trafficChecking)
        publish(previous)
    }
    @Synchronized fun setRecovery(waiting: Boolean, attempt: Int, tunPresent: Boolean) = publish(VpnUiState(
        connected = tunPresent, connecting = !tunPresent, recoveryStatus = if (waiting) "waiting_network" else "recovering",
        recoveryAttempt = attempt, sessionRevision = mutableState.value.sessionRevision,
        appliedConfiguration = mutableState.value.appliedConfiguration))
    @Synchronized fun setTrafficChecking() { if (mutableState.value.connected && mutableState.value.exitChecks.isEmpty()) publish(mutableState.value.copy(trafficChecking = true, trafficVerified = false, trafficCheckFailed = false, warning = null)) }
    @Synchronized fun setTrafficResult(success: Boolean) {
        val current = mutableState.value
        if (current.connected && current.exitChecks.isEmpty()) publish(ConnectionHealthReducer.probeResult(current, success))
    }
    @Synchronized internal fun beginExitChecks(bindings: RouteExitBindings,policy: RoutePolicyIdentity,unsupported: Set<String>) {
        publish(ExitHealthReducer.begin(mutableState.value,bindings,policy,unsupported))
    }
    @Synchronized internal fun setExitChecking(revision: Long,id: String,fingerprint: String): Boolean {
        val before=mutableState.value;val next=ExitHealthReducer.checking(before,revision,id,fingerprint)
        publish(next);return next!=before
    }
    @Synchronized internal fun setExitResult(revision: Long,id: String,fingerprint: String,success: Boolean,millis: Int?) {
        publish(ExitHealthReducer.result(mutableState.value,revision,id,fingerprint,success,millis))
    }
    @Synchronized fun setDisconnected() = publish(VpnUiState())
    @Synchronized fun setFailure(message: String) = publish(VpnUiState(error = message))
    @Synchronized fun setTransportWarning(message: String) {
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
        if (previous.appliedConfiguration?.profileId != next.appliedConfiguration?.profileId ||
            NovaTilePresentation.from(previous, null) != NovaTilePresentation.from(next, null)) {
            appContext?.let(NovaVpnTileService::requestRefresh)
        }
    }
}

internal fun VpnUiState.recoveryText(): String? = when (recoveryStatus) {
    "waiting_network" -> "Ожидаем сеть"
    "recovering" -> "Восстанавливаем соединение${if (recoveryAttempt > 0) " · попытка $recoveryAttempt" else ""}"
    else -> null
}
