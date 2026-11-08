package com.nova.vpn.android

import android.content.Context
import com.nova.vpn.android.data.*
import kotlinx.coroutines.*

/** Exact applied intent, never the currently selected server or an editor draft. */
internal data class ActiveCatalogSession(val identity:AppliedConfiguration,val exits:RouteExitBindings,val binding:RouteCommitBinding) {
    fun matches(document:RoutingPresets):Boolean {
        val active=document.active
        return document.pending==null && active.id==identity.routingPresetId &&
            RoutePolicyIdentity.capture(active.settings)==identity.policy && binding.matchesTraffic(active.settings,identity.profileId)
    }
    fun needsRefresh(profiles:List<ImportedProfile>):Boolean {
        require(profiles.map {it.id}.distinct().size==profiles.size)
        return try { exits.validateCatalog(profiles,requireNotNull(identity.policy).profileIds);false }
        catch(_:IllegalArgumentException) {true}
    }
}

internal enum class CatalogRefreshOutcome {
    WAITING, APPLIED, REJECTED, CANCELLED;
    companion object {
        fun from(status: ApplyStatus?, owned: Boolean, current: Boolean): CatalogRefreshOutcome = when {
            owned -> WAITING
            !current -> CANCELLED
            status == ApplyStatus.APPLIED -> APPLIED
            status == ApplyStatus.CANCELLED || status == ApplyStatus.SUPERSEDED -> CANCELLED
            else -> REJECTED
        }
    }
}

/** Reconcile catalog changes through the same staged, authenticated START path as manual applies. */
internal class ActiveCatalogRefresh(private val context:Context,private val owner:Long,
    private val session:()->ActiveCatalogSession?):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private data class Submission(val requestId: String, val stamp: Pair<ActiveCatalogSession, Long>)
    private var submitted: Submission? = null
    private var observed:Pair<ActiveCatalogSession,Long>?=null
    private val closed=java.util.concurrent.atomic.AtomicBoolean()
    private val retry=CatalogRefreshBackoff()
    private var catalogFailed=false
    fun start() { scope.launch {
        while(isActive) {
            try { reconcile() } catch(error:CancellationException) {throw error}
            catch(_:Exception) { recordFailure() }
            delay(1000)
        }
    } }
    private fun recordFailure() {
        catalogFailed = true
        retry.failed(android.os.SystemClock.elapsedRealtime())
        VpnDiagnostics.record("catalog_refresh_failed")
    }

    private fun settleSubmission(): Boolean {
        val attempt = submitted ?: return true
        // The service publishes the acknowledgement before releasing ownership.
        val owned = AutomaticProbePause.belongsTo(attempt.requestId, owner)
        val outcome = CatalogRefreshOutcome.from(
            VpnApplyRequests.tracker.results.value[attempt.requestId]?.status, owned,
            VpnUserRequests.gate.isCurrent(attempt.requestId))
        if (outcome == CatalogRefreshOutcome.WAITING) return false
        submitted = null
        when (outcome) {
            CatalogRefreshOutcome.APPLIED -> {
                observed = attempt.stamp
                if (catalogFailed) VpnDiagnostics.record("catalog_refresh_current")
                catalogFailed = false
                retry.succeeded()
            }
            CatalogRefreshOutcome.REJECTED -> recordFailure()
            else -> Unit
        }
        return true
    }

    private fun reconcile() {
        if(closed.get() || !settleSubmission() || AutomaticProbePause.paused || AutomaticProbePause.pending.value.isNotEmpty())return
        val current=session()?:return
        val revision=ProfileVault.changes.value
        val stamp=current to revision
        if(observed==stamp)return
        if(!retry.ready(CatalogRefreshKey(current.identity.requestId,revision),android.os.SystemClock.elapsedRealtime()))return
        val token=VpnUserRequests.gate.snapshot()
        val store=AppRoutingStore(context)
        val captured=synchronized(ProfileVault.lock) {
            val document=store.loadPresets()
            if(!current.matches(document))return
            val profiles=ProfileVault(context).load()
            if(!current.needsRefresh(profiles)) {
                if(catalogFailed)VpnDiagnostics.record("catalog_refresh_current")
                catalogFailed=false;retry.succeeded();observed=stamp;return
            }
            val active=document.active
            val anchor=PreparedVpnConfiguration.selectedPrimary(profiles,active.settings,current.identity.profileId)
                ?:throw MissingSelectedProfile()
            val prepared=PreparedVpnConfiguration.prepare(context,anchor,active.settings,active.id,quarantineUnsupported=true)
            active to prepared
        }
        if(session()!=current || ProfileVault.changes.value!=revision)return
        VpnUserRequests.gate.accept(token) {
            if(closed.get() || session()!=current || AutomaticProbePause.paused || AutomaticProbePause.pending.value.isNotEmpty())return@accept
            synchronized(ProfileVault.lock) {
                if(closed.get() || ProfileVault.changes.value!=revision || !current.matches(store.loadPresets()))return@synchronized
                check(AppPreferencesStore(context).vpnEngine()==VpnEngine.SING_BOX)
                captured.second.exits!!.validateCatalog(ProfileVault(context).load(),current.identity.policy!!.profileIds)
                submit(store,captured.first,captured.second)?.let { submitted = Submission(it, stamp) }
            }
        }
    }
    private fun submit(store:AppRoutingStore,active:RoutingPreset,prepared:PreparedVpnConfiguration):String? {
        val token=VpnUserRequests.gate.begin()
        try {
            val dispatched = AutomaticProbePause.dispatch(token,owner) {
                store.stage(active.id,token,settings=active.settings,selection=RoutingSelectionChange(prepared.profileId),expected=active.settings)
                check(VpnUserRequests.start(context,prepared,token,automatic=true))
                VpnDiagnostics.record("catalog_refresh_submitted")
            }
            return token.takeIf { dispatched }
        } catch(error:Exception) {store.cancelPending(token);AutomaticProbePause.settled(token);throw error}
    }
    override fun close() {closed.set(true);scope.cancel()}
}
