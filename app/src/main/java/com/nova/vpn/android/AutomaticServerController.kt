package com.nova.vpn.android

import android.content.Context
import com.nova.vpn.android.data.ProfileVault
import com.nova.vpn.android.data.SelectedProfileStore
import com.nova.vpn.android.data.AppPreferencesStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

internal data class AutoServerSession(val requestId: String, val profileId: String, val network: String)
internal data class AutoServerState(val checking: Boolean = false, val reason: AutoSwitchPolicy.Reason? = null,
    val appliedRequest: String? = null, val profileId: String? = null, val limited: Boolean = false)
internal object AutoServerRuntime {
    private val mutable = MutableStateFlow(AutoServerState())
    val state = mutable.asStateFlow()
    private var owner = 0L
    @Synchronized fun claim(): Long { owner++; mutable.value = AutoServerState(); return owner }
    @Synchronized fun publish(requester: Long, value: AutoServerState) { if (requester == owner) mutable.value = value }
}

/** Lives with the foreground VPN service. Probes run only after explicit opt-in. */
internal class AutomaticServerController(private val context: Context, private val serviceIdentity: Long,
    private val session: () -> AutoServerSession?) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = AutoServerSettingsStore(context)
    private val policy = AutoSwitchPolicy()
    private val budgetStore = AutoSwitchBudgetStore(context)
    private val owner = AutoServerRuntime.claim()
    private var cursor = 0
    private var activeChecks: ServerChecks? = null

    fun start() {
        scope.launch {
            store.state.collectLatest { options ->
                policy.setEnabled(false)
                AutoServerRuntime.publish(owner, AutoServerState())
                if (!options.enabled) return@collectLatest
                policy.setEnabled(true)
                try {
                    while (isActive) {
                        val current = session()
                        if (current == null || AutomaticProbePause.paused) { delay(5_000); continue }
                        runCatching { evaluate(options, current) }.onFailure {
                            if (it is CancellationException) throw it
                            VpnDiagnostics.record("auto_check_failed")
                        }
                        delay(60_000)
                    }
                } finally {
                    activeChecks?.cancel(); activeChecks = null
                    policy.setEnabled(false)
                    AutoServerRuntime.publish(owner, AutoServerRuntime.state.value.copy(checking = false))
                }
            }
        }
    }

    private suspend fun evaluate(options: AutoServerOptions, measuredSession: AutoServerSession) {
        val preferences = AppPreferencesStore(context)
        val engine = preferences.vpnEngine()
        val token = VpnUserRequests.gate.snapshot()
        val vaultRevision = ProfileVault.changes.value
        val profiles = withContext(Dispatchers.IO) { ProfileVault(context).load() }
        val current = profiles.firstOrNull { it.id == measuredSession.profileId } ?: return
        val alternatives = profiles.filter { it.id != current.id && it.id in options.candidateIds }
        if (alternatives.isEmpty()) return
        // Bound work per round; rotate the shortlist so new/unmeasured nodes also get tested.
        val offset = cursor.mod(alternatives.size)
        val shortlist = (alternatives.drop(offset) + alternatives.take(offset)).take(4)
        cursor = (offset + shortlist.size).mod(alternatives.size)
        val checks = ServerChecks(context, scope)
        activeChecks = checks
        AutoServerRuntime.publish(owner, AutoServerRuntime.state.value.copy(checking = true, limited = false))
        var decision: AutoSwitchPolicy.Decision? = null
        var reservation: String? = null
        var dispatched = false
        try {
            coroutineScope {
                val watcher = launch {
                    while (isActive) {
                        if (AutomaticProbePause.paused || session() != measuredSession || !VpnUserRequests.gate.isCurrent(token) || ProfileVault.changes.value != vaultRevision) {
                            checks.cancel(); break
                        }
                        delay(250)
                    }
                }
                try {
                    checks.start(listOf(current) + shortlist)
                    checks.state.first { !it.running }
                } finally { watcher.cancel() }
            }
            ensureCurrent(options, measuredSession, token)
            check(ProfileVault.changes.value == vaultRevision) { "Profiles changed during automatic selection" }
            val latest = withContext(Dispatchers.IO) { ProfileVault(context).load() }
            val latestCurrent = latest.firstOrNull { it.id == current.id } ?: return
            val allowed = latest.filter { it.id in options.candidateIds }
            check(preferences.vpnEngine() == engine) { "Engine changed during automatic selection" }
            val proposed = policy.propose(latestCurrent, allowed, checks.state.value.results, measuredSession.network, android.os.SystemClock.elapsedRealtimeNanos(), engine) ?: return
            decision = proposed
            val chosen = latest.firstOrNull { it.id == proposed.to } ?: return
            val prepared = withContext(Dispatchers.Default) { PreparedVpnConfiguration.prepare(context, chosen) }
            ensureCurrent(options, measuredSession, token)
            check(ProfileVault.changes.value == vaultRevision) { "Profiles changed during automatic preparation" }
            val reservationId = java.util.UUID.randomUUID().toString()
            if (!withContext(NonCancellable + Dispatchers.IO) {
                    budgetStore.reserve(reservationId).also { if (it) reservation = reservationId }
                }) {
                AutoServerRuntime.publish(owner, AutoServerRuntime.state.value.copy(limited = true))
                VpnDiagnostics.record("auto_switch_limited")
                return
            }
            ensureCurrent(options, measuredSession, token)
            var applyToken: String? = null
            VpnUserRequests.gate.accept(token) {
                if (!AutomaticProbePause.paused && preferences.vpnEngine() == engine && session() == measuredSession && store.snapshot() == options && ProfileVault.changes.value == vaultRevision) {
                    val next = VpnUserRequests.gate.begin()
                    AutomaticProbePause.dispatch(next, serviceIdentity) {
                        check(VpnUserRequests.start(context, prepared, next, automatic = true))
                        dispatched = true
                        applyToken = next
                    }
                }
            }
            val requested = applyToken ?: return
            dispatched = true
            val acknowledgement = withTimeoutOrNull(60_000) {
                VpnApplyRequests.tracker.results.first { results -> results[requested]?.status?.let { it != ApplyStatus.PENDING } == true }[requested]
            }
            val applied = acknowledgement?.status == ApplyStatus.APPLIED && acknowledgement.applied?.profileId == chosen.id
            if (acknowledgement != null) {
                withContext(Dispatchers.IO) { budgetStore.finish(reservationId, applied) }
                reservation = null
            }
            policy.complete(proposed, applied, android.os.SystemClock.elapsedRealtimeNanos())
            decision = null
            if (applied && VpnUserRequests.gate.isCurrent(requested)) {
                withContext(Dispatchers.IO) { VpnUserRequests.gate.accept(requested) { SelectedProfileStore(context).save(chosen.id) } }
                AutoServerRuntime.publish(owner, AutoServerState(reason = proposed.reason,
                    appliedRequest = requested, profileId = chosen.id))
                VpnDiagnostics.record("auto_server_applied")
            } else VpnDiagnostics.record("auto_server_not_applied")
        } finally {
            try {
                // Once submitted, an interrupted/unknown result must retain its reservation.
                if (!dispatched) reservation?.let { id -> withContext(NonCancellable + Dispatchers.IO) { budgetStore.finish(id, false) } }
            } finally {
                decision?.let { policy.complete(it, false, android.os.SystemClock.elapsedRealtimeNanos()) }
                checks.cancel(); activeChecks = null
                AutoServerRuntime.publish(owner, AutoServerRuntime.state.value.copy(checking = false))
            }
        }
    }

    private suspend fun ensureCurrent(options: AutoServerOptions, measured: AutoServerSession, token: String) {
        currentCoroutineContext().ensureActive()
        check(!AutomaticProbePause.paused && store.snapshot() == options && session() == measured && VpnUserRequests.gate.isCurrent(token)) { "Stale automatic selection" }
    }

    override fun close() {
        activeChecks?.cancel(); scope.cancel(); store.close(); policy.setEnabled(false)
    }
}
