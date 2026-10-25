package com.nova.vpn.android

/** One immutable configuration/identity snapshot survives asynchronous preparation and consent. */
internal data class PreparedVpnConfiguration(val config: String, val name: String, val profileId: String, val routingPresetId: String) {
    companion object {
        fun prepare(context: android.content.Context, profile: com.nova.vpn.android.data.ImportedProfile): PreparedVpnConfiguration {
            val routes = com.nova.vpn.android.data.AppRoutingStore(context).loadPresets().active
            return PreparedVpnConfiguration(com.nova.vpn.android.data.SingBoxConfigFactory.build(profile, routes.settings, context.packageName),
                profile.name, profile.id, routes.id)
        }
    }
}

/** Shared intent ordering starts before asynchronous preparation or system consent. */
internal class UserRequestGate {
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
    @Synchronized fun begin(): String = java.util.UUID.randomUUID().toString().also { current = it }
    @Synchronized fun isCurrent(token: String) = token == current
    @Synchronized fun snapshot(): String = current
    @Synchronized fun accept(token: String, action: () -> Unit): Boolean {
        if (token != current) return false
        action()
        return true
    }
}

internal object VpnUserRequests {
    val gate = UserRequestGate()
    const val EXTRA_REQUEST = "nova_user_request"
    const val EXTRA_AUTOMATIC = "nova_automatic_request"

    fun start(context: android.content.Context, prepared: PreparedVpnConfiguration, token: String, automatic: Boolean = false) =
        start(context, prepared.config, prepared.name, token, prepared.profileId, prepared.routingPresetId, automatic)

    fun start(context: android.content.Context, config: String, name: String, token: String,
              profileId: String? = null, routingPresetId: String? = null, automatic: Boolean = false) = gate.accept(token) {
        val intent = android.content.Intent(context, NovaVpnService::class.java)
            .setAction(NovaVpnService.ACTION_START)
            .putExtra(NovaVpnService.EXTRA_CONFIG, config)
            .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, name)
            .putExtra(EXTRA_REQUEST, token)
            .putExtra(EXTRA_AUTOMATIC, automatic)
            .putExtra(NovaVpnService.EXTRA_PROFILE_ID, profileId)
            .putExtra(NovaVpnService.EXTRA_ROUTING_PRESET_ID, routingPresetId)
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    fun stop(context: android.content.Context) {
        val token = gate.begin()
        gate.accept(token) {
            context.startService(android.content.Intent(context, NovaVpnService::class.java)
                .setAction(NovaVpnService.ACTION_STOP).putExtra(EXTRA_REQUEST, token))
        }
    }
}
