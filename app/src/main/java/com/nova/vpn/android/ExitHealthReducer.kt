package com.nova.vpn.android

/** These states describe one HTTPS check, not UDP/IPv6 availability. */
enum class ExitCheckStatus { QUEUED, CHECKING, VERIFIED, FAILED, UNAVAILABLE, UNSUPPORTED, INCOMPATIBLE }
data class ExitCheckState(val status: ExitCheckStatus,val profileSha256: String? = null,val responseMillis: Int? = null)
internal object ExitHealthReducer {
    private fun aggregate(state: VpnUiState,checks: Map<String,ExitCheckState>): VpnUiState {
        val verified=checks.isNotEmpty()&&checks.values.all { it.status==ExitCheckStatus.VERIFIED }
        return state.copy(exitChecks=java.util.Collections.unmodifiableMap(LinkedHashMap(checks)),
            trafficChecking=checks.values.any { it.status==ExitCheckStatus.QUEUED||it.status==ExitCheckStatus.CHECKING },
            trafficVerified=verified,trafficCheckFailed=checks.values.any { it.status==ExitCheckStatus.FAILED||it.status==ExitCheckStatus.UNAVAILABLE||it.status==ExitCheckStatus.INCOMPATIBLE },
            warning=null,transportNotice=if(verified)null else state.transportNotice)
    }
    fun begin(state: VpnUiState,bindings: RouteExitBindings,policy: RoutePolicyIdentity,unsupported: Set<String>): VpnUiState {
        if(!state.connected||state.recoveryStatus!=null||state.appliedConfiguration?.policy!=policy)return state
        return aggregate(state,policy.profileIds.sorted().associateWith { id ->
            val entry=bindings.entries[id]
            ExitCheckState(if(id in bindings.quarantined)ExitCheckStatus.INCOMPATIBLE else if(entry==null)ExitCheckStatus.UNAVAILABLE else if(id in unsupported)ExitCheckStatus.UNSUPPORTED else ExitCheckStatus.QUEUED,entry?.profileSha256?:bindings.quarantined[id])
        })
    }
    fun checking(state: VpnUiState,revision: Long,id: String,fingerprint: String): VpnUiState {
        val before=state.exitChecks[id]?:return state
        if(!state.connected||state.recoveryStatus!=null||state.sessionRevision!=revision||before.profileSha256!=fingerprint||before.status!=ExitCheckStatus.QUEUED)return state
        return aggregate(state,state.exitChecks+(id to before.copy(status=ExitCheckStatus.CHECKING)))
    }
    fun result(state: VpnUiState,revision: Long,id: String,fingerprint: String,success: Boolean,millis: Int?): VpnUiState {
        val before=state.exitChecks[id]?:return state
        if(!state.connected||state.recoveryStatus!=null||state.sessionRevision!=revision||before.profileSha256!=fingerprint||before.status!=ExitCheckStatus.CHECKING)return state
        require(millis==null||millis>=0)
        return aggregate(state,state.exitChecks+(id to before.copy(status=if(success)ExitCheckStatus.VERIFIED else ExitCheckStatus.FAILED,responseMillis=millis.takeIf { success })))
    }
}
internal fun VpnUiState.exitCheckSummary(): String = when {
    exitChecks.isEmpty() -> "Туннель подключён"
    trafficChecking -> "Проверка выходов: ${exitChecks.values.count { it.status==ExitCheckStatus.VERIFIED }}/${exitChecks.size}"
    trafficVerified -> "HTTPS подтверждён через все назначенные выходы"
    else -> "Туннель активен · подтверждены выходы ${exitChecks.values.count { it.status==ExitCheckStatus.VERIFIED }}/${exitChecks.size}"
}
