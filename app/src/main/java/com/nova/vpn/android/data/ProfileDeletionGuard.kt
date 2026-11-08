package com.nova.vpn.android.data

import com.nova.vpn.android.AppliedConfiguration

/** Include the immutable applied policy even when durable editor intent already differs. */
internal object ProfileRoutingReferences {
    fun collect(routes:RoutingPresets,applied:AppliedConfiguration?):Set<String> =
        routes.referencedProfileIds + applied?.policy?.profileIds.orEmpty()
    fun forSubscription(routes:RoutingPresets,selectedId:String?,applied:AppliedConfiguration?):Set<String> =
        setOfNotNull(selectedId,applied?.profileId)+collect(routes,applied)
}

/** Manual deletion only. Full reset/restore have their own coherent transactions. */
internal object ProfileDeletionGuard {
    class InUse:IllegalStateException("Сервер используется в маршрутизации. Сначала удалите его назначения и сохранённые привязки режима.")
    fun requireAllowed(profileId:String,routes:RoutingPresets,applied:AppliedConfiguration?) {
        require(profileId.isNotBlank() && profileId.length<=128)
        if(profileId in ProfileRoutingReferences.collect(routes,applied))throw InUse()
    }
}
