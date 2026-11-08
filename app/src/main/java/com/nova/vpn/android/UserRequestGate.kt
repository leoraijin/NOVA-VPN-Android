package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.ImportedProfile

/** One immutable configuration/identity snapshot survives asynchronous preparation and consent. */
internal data class PreparedVpnConfiguration(val config: String, val name: String, val profileId: String, val routingPresetId: String, val policy: RoutePolicyIdentity? = null, val exits: RouteExitBindings? = null, val commitBinding: RouteCommitBinding? = null) {
    companion object {
        internal fun selectedPrimary(profiles: List<ImportedProfile>, settings: AppRoutingSettings, selectedId: String?): ImportedProfile? {
            val defaultId = settings.multiServer?.defaultProfileId
            val id = defaultId ?: selectedId ?: return null
            val matches = profiles.filter { it.id == id }
            if (matches.size > 1) return null
            val profile = matches.singleOrNull()
            if (defaultId == null) return profile?.takeUnless { it.retiredFromSubscription }
            // Keep the exact saved identity. Only catalog entries can supply outbounds.
            return profile ?: ImportedProfile(id = id, name = "Основной сервер недоступен",
                protocol = "", server = "", port = 0, outboundJson = "{}", retiredFromSubscription = true)
        }

        internal fun primaryForRoutingEdit(profiles: List<ImportedProfile>, previous: AppRoutingSettings, primaryId: String): ImportedProfile? =
            if (previous.multiServer?.defaultProfileId == primaryId) selectedPrimary(profiles, previous, primaryId)
            else profiles.singleOrNull { it.id == primaryId }?.takeUnless { it.retiredFromSubscription }

        fun prepareSelected(context: android.content.Context): PreparedVpnConfiguration = synchronized(com.nova.vpn.android.data.ProfileVault.lock) {
            val active=com.nova.vpn.android.data.AppRoutingStore(context).loadPresets().active
            val profiles=com.nova.vpn.android.data.ProfileVault(context).load()
            val selected=com.nova.vpn.android.data.SelectedProfileStore(context).resolveId(profiles)
            val profile=selectedPrimary(profiles,active.settings,selected)?:throw MissingSelectedProfile()
            // Resume already committed intent safely; an updated incompatible exit
            // stays pinned and blocked. Explicit editor applies remain strict below.
            prepare(context,profile,active.settings,active.id,quarantineUnsupported=active.settings.multiServer!=null)
        }
        fun prepare(context: android.content.Context, profile: com.nova.vpn.android.data.ImportedProfile): PreparedVpnConfiguration =
            synchronized(com.nova.vpn.android.data.ProfileVault.lock) {
                val routes = com.nova.vpn.android.data.AppRoutingStore(context).loadPresets().active
                prepare(context,profile,routes.settings,routes.id)
            }

        fun prepare(context: android.content.Context, profile: com.nova.vpn.android.data.ImportedProfile,
            settings: com.nova.vpn.android.data.AppRoutingSettings, presetId: String, quarantineUnsupported:Boolean=false): PreparedVpnConfiguration =
            synchronized(com.nova.vpn.android.data.ProfileVault.lock) {
                val frozen=settings.copy(packages=settings.packages.toSet(),sites=settings.sites.toList(),
                    multiServer=settings.multiServer?.let { it.copy(rules=it.rules.toList()) })
                val multi = frozen.multiServer
                if (multi != null) {
                    require(android.os.Build.VERSION.SDK_INT >= 29) { "Назначения серверов приложениям требуют Android 10 или новее." }
                    require(com.nova.vpn.android.data.AppPreferencesStore(context).vpnEngine() == com.nova.vpn.android.data.VpnEngine.SING_BOX) {
                        "Для назначений серверов выберите sing-box."
                    }
                }
                if(multi!=null) com.nova.vpn.android.data.SharedUidRouting.validate(multi,
                    { name -> runCatching { context.packageManager.getApplicationInfo(name,0).uid }.getOrNull() },
                    { uid -> context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty() }, context.packageName)
                val snapshot = if (multi != null || frozen.history!=null) com.nova.vpn.android.data.ProfileVault(context).load().toList() else null
                val primary = if(multi!=null) selectedPrimary(requireNotNull(snapshot), frozen, null) ?: throw MissingSelectedProfile()
                    else if(frozen.history!=null) requireNotNull(snapshot?.firstOrNull {it.id==profile.id}) {"Прежний сервер недоступен. Выберите сервер вручную."}
                    else profile
                val quarantined=if(quarantineUnsupported && multi!=null) {
                    val ids=com.nova.vpn.android.data.MultiServerPolicy(multi.revision,multi.defaultProfileId,multi.rules,emptySet()).referencedProfileIds
                    requireNotNull(snapshot).filter {!it.retiredFromSubscription && it.id in ids && com.nova.vpn.android.data.MultiServerProfileAdapter.problem(it)!=null}.map {it.id}.toSet()
                } else emptySet()
                val config=com.nova.vpn.android.data.SingBoxConfigFactory.build(primary,frozen,context.packageName,snapshot,
                    java.io.File(context.filesDir,"multi-server-dns-v2.db").absolutePath,quarantined)
                if(multi!=null) FakeIpNetworkPreflight.validate(config,FakeIpNetworkPreflight.capture(context))
                PreparedVpnConfiguration(config,primary.name,primary.id,presetId,RoutePolicyIdentity.capture(frozen),
                    multi?.let { RouteExitBindings.capture(it,requireNotNull(snapshot),config,quarantined) },RouteCommitBinding.capture(frozen,primary.id,config))
            }
    }
}

/** Shared intent ordering starts before asynchronous preparation or system consent. */
internal class UserRequestGate(private val onBegin:()->Unit={}) {
    private var current = java.util.UUID.randomUUID().toString()
    private val services = mutableSetOf<Long>()
    @Synchronized fun retainService(owner: Long) { services.add(owner) }
    @Synchronized fun releaseService(owner: Long) { services.remove(owner) }
    /** Keep new START/STOP submissions outside the entire restore/rollback transaction. */
    @Synchronized fun restoreWhenIdle(canRestore: () -> Boolean, action: () -> Unit) {
        check(services.isEmpty() && canRestore()) { "Дождитесь полного отключения VPN и повторите восстановление." }
        begin()
        action()
    }
    @Synchronized fun begin(): String = java.util.UUID.randomUUID().toString().also { current = it; onBegin() }
    @Synchronized fun isCurrent(token: String) = token == current
    @Synchronized fun snapshot(): String = current
    @Synchronized fun accept(token: String, action: () -> Unit): Boolean {
        if (token != current) return false
        action()
        return true
    }
}

internal object VpnUserRequests {
    private val starts=PendingVpnStarts()
    val gate = UserRequestGate(starts::clear)
    const val EXTRA_REQUEST = "nova_user_request"
    const val EXTRA_AUTOMATIC = "nova_automatic_request"
    const val EXTRA_POLICY = "nova_route_policy"
    const val EXTRA_EXITS = "nova_route_exits"
    const val EXTRA_COMMIT_BINDING="nova_route_commit_binding"

    fun start(context: android.content.Context, prepared: PreparedVpnConfiguration, token: String, automatic: Boolean = false) =
        start(context, prepared.config, prepared.name, token, prepared.profileId, prepared.routingPresetId, automatic, prepared.policy, prepared.exits,prepared.commitBinding)

    fun start(context: android.content.Context, config: String, name: String, token: String,
              profileId: String? = null, routingPresetId: String? = null, automatic: Boolean = false, policy: RoutePolicyIdentity? = null, exits: RouteExitBindings? = null,commitBinding: RouteCommitBinding? = null) = gate.accept(token) {
        val payload=PendingVpnStart(config,name,profileId,routingPresetId,policy?.encode(),exits?.encode(),commitBinding?.encode(),automatic)
        if(starts.publish(token,payload)) {
            // NovaVpnService, activity, tile and Wear listener share the default app process.
            // Binder carries only this command; full snapshots never enter the transaction buffer.
            val intent=android.content.Intent(context,NovaVpnService::class.java).setAction(NovaVpnService.ACTION_START)
                .putExtra(EXTRA_REQUEST,token).putExtra(EXTRA_AUTOMATIC,automatic)
            try {
                if(android.os.Build.VERSION.SDK_INT>=26)context.startForegroundService(intent) else context.startService(intent)
            } catch(error:Throwable) { starts.discard(token);throw error }
        }
    }

    /** Called only inside gate.accept, before automatic-request ownership checks; the private snapshot owns that flag. */
    internal fun consumeStart(command:android.content.Intent):android.content.Intent? {
        val token=command.getStringExtra(EXTRA_REQUEST)?:return null
        check(gate.isCurrent(token)) { "Запрос VPN устарел" }
        return starts.take(token)?.localIntent(command,token)
    }
    internal fun discardStart(token:String)=starts.discard(token)

    fun stop(context: android.content.Context) {
        val token = gate.begin()
        gate.accept(token) {
            context.startService(android.content.Intent(context, NovaVpnService::class.java)
                .setAction(NovaVpnService.ACTION_STOP).putExtra(EXTRA_REQUEST, token))
        }
    }
}

internal class MissingSelectedProfile: IllegalStateException("Выбранный сервер недоступен. Выберите сервер вручную в списке.")



