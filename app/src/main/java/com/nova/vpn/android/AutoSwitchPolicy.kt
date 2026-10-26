package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.VpnEngine

/** In-memory policy; callers must acknowledge actual apply/rollback before recording a switch. */
internal class AutoSwitchPolicy {
    enum class Reason { CURRENT_FAILED, BETTER_RESPONSE }
    data class Decision(val serial: Long, val from: String, val to: String, val network: String, val reason: Reason)
    private var enabled = false
    private var serial = 0L
    private var pending: Decision? = null
    private var network: String? = null
    private var observedProfile: String? = null
    private var observedEngine: VpnEngine? = null
    private var lastFailureMeasurement: Long? = null
    private var failures = 0
    private var dwellStarted: Long? = null
    private var retryAfter = Long.MIN_VALUE
    private val switches = ArrayDeque<Long>()

    @Synchronized fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) { pending = null; failures = 0; lastFailureMeasurement = null }
    }

    /** Explicit server selection pins it until the user enables automatic mode again. */
    @Synchronized fun manualSelection() = setEnabled(false)

    @Synchronized fun propose(current: ImportedProfile, candidates: List<ImportedProfile>, results: Map<String, ServerCheckResult>,
        physicalNetwork: String?, now: Long, engine: VpnEngine = VpnEngine.SING_BOX): Decision? {
        if (!enabled) return null
        if (network != physicalNetwork || observedProfile != current.id || observedEngine != engine) {
            pending = null
            network = physicalNetwork; observedProfile = current.id; observedEngine = engine; failures = 0; lastFailureMeasurement = null
            dwellStarted = now
        }
        if (pending != null || physicalNetwork == null) return null
        while (switches.isNotEmpty() && now - switches.first() >= WINDOW) switches.removeFirst()
        if (switches.size >= 3 || now < retryAfter) return null
        val currentResult = results[current.id] ?: return null
        if (currentResult.engine != engine || !sameScope(current, currentResult, physicalNetwork, now)) return null
        if (currentResult.measuredAtNanos != lastFailureMeasurement) {
            failures = if (currentResult.outcome == CheckOutcome.FAILED) (failures + 1).coerceAtMost(2) else 0
            lastFailureMeasurement = currentResult.measuredAtNanos
        }
        val best = ServerSelectionPolicy.best(candidates.filter { it.id != current.id }, results, physicalNetwork, now, engine) ?: return null
        val bestResult = results.getValue(best.id)
        val reason = if (failures >= 2) Reason.CURRENT_FAILED else {
            if (!ServerSelectionPolicy.eligible(current, currentResult, physicalNetwork, now, engine)) return null
            if (now - (dwellStarted ?: now) < MIN_DWELL) return null
            // Reliability must not drop merely to gain a lower response time.
            if (bestResult.successes.toLong() * currentResult.attempts < currentResult.successes.toLong() * bestResult.attempts) return null
            val previous = currentResult.millis!!.toLong()
            val improvement = previous - bestResult.millis!!
            if (improvement < 20 || improvement * 100 < previous * 20) return null
            Reason.BETTER_RESPONSE
        }
        return Decision(++serial, current.id, best.id, physicalNetwork, reason).also { pending = it }
    }

    @Synchronized fun complete(decision: Decision, applied: Boolean, now: Long): Boolean {
        if (!enabled || pending != decision) return false
        pending = null
        if (applied) {
            switches.addLast(now); dwellStarted = now; observedProfile = decision.to
            failures = 0; lastFailureMeasurement = null
        } else retryAfter = now + RETRY_DELAY
        return true
    }

    private fun sameScope(profile: ImportedProfile, result: ServerCheckResult, network: String, now: Long) =
        result.networkSignature == network && result.configurationSignature == ServerSelectionPolicy.configurationSignature(profile) &&
            now - result.measuredAtNanos in 0..MAX_AGE

    companion object {
        private const val MIN_DWELL = 60_000_000_000L
        private const val MAX_AGE = 300_000_000_000L
        private const val WINDOW = 900_000_000_000L
        private const val RETRY_DELAY = 30_000_000_000L
    }
}
