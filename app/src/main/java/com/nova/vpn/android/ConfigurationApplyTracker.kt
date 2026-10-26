package com.nova.vpn.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Identifiers only. Configuration contents and user names never enter acknowledgements. */
data class AppliedConfiguration(val requestId: String, val profileId: String? = null, val routingPresetId: String? = null)

internal enum class ApplyStatus { PENDING, APPLIED, ROLLED_BACK, FAILED, SUPERSEDED, CANCELLED }
internal data class ConfigurationApplyResult(
    val requested: AppliedConfiguration,
    val status: ApplyStatus,
    val applied: AppliedConfiguration? = null
)

/** Bounded service acknowledgements. A native start returning is the commit point. */
internal class ConfigurationApplyTracker {
    private val mutableResults = MutableStateFlow<Map<String, ConfigurationApplyResult>>(emptyMap())
    val results = mutableResults.asStateFlow()
    private var activeRequest: String? = null

    @Synchronized fun accepted(identity: AppliedConfiguration) {
        activeRequest = identity.requestId
        val next = mutableResults.value.mapValues { (_, result) ->
            if (result.status == ApplyStatus.PENDING) result.copy(status = ApplyStatus.SUPERSEDED) else result
        }.toMutableMap()
        next[identity.requestId] = ConfigurationApplyResult(identity, ApplyStatus.PENDING)
        mutableResults.value = next.entries.toList().takeLast(32).associate { it.toPair() }
    }

    @Synchronized fun applied(identity: AppliedConfiguration, rollbackFor: String? = null): Boolean {
        val target = rollbackFor ?: identity.requestId
        if (activeRequest != target) return false
        val current = mutableResults.value[target] ?: return false
        if (rollbackFor == null && current.requested != identity) return false
        if (current.status !in setOf(ApplyStatus.PENDING, ApplyStatus.APPLIED, ApplyStatus.ROLLED_BACK)) return false
        val status = if (rollbackFor == null) ApplyStatus.APPLIED else ApplyStatus.ROLLED_BACK
        mutableResults.value = mutableResults.value + (target to current.copy(status = status, applied = identity))
        return true
    }

    @Synchronized fun failed(requestId: String) {
        if (activeRequest != requestId) return
        val current = mutableResults.value[requestId] ?: return
        mutableResults.value = mutableResults.value + (requestId to current.copy(status = ApplyStatus.FAILED, applied = null))
    }

    @Synchronized fun stopped() {
        activeRequest = null
        mutableResults.value = mutableResults.value.mapValues { (_, result) ->
            if (result.status == ApplyStatus.PENDING) result.copy(status = ApplyStatus.CANCELLED) else result
        }
    }
}

internal object VpnApplyRequests { val tracker = ConfigurationApplyTracker() }
