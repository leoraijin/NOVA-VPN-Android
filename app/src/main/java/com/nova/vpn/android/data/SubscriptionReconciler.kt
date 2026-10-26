package com.nova.vpn.android.data

/** Membership is separate from node identity: one connection can belong to many subscriptions. */
internal data class SubscriptionMembership(val profileId: String, val subscriptionId: String)

internal data class SubscriptionCatalog(
    val profiles: List<ImportedProfile>,
    val memberships: Set<SubscriptionMembership>,
    val manuallyOwned: Set<String>
)

internal data class SubscriptionChanges(
    val catalog: SubscriptionCatalog,
    val added: Int,
    val updated: Int,
    val removed: Int,
    val partial: Boolean
)

internal object SubscriptionReconciler {
    /** Call only after a non-empty, bounded subscription has been successfully parsed.
     * Partial imports may add valid records but cannot prove that missing old records were deleted.
     * Active detached nodes can be retained until the service explicitly releases them.
     */
    fun reconcile(current: SubscriptionCatalog, subscriptionId: String, incoming: List<ImportedProfile>,
        complete: Boolean, retainIds: Set<String> = emptySet()): SubscriptionChanges {
        require(subscriptionId.isNotBlank())
        require(incoming.isNotEmpty()) { "Пустая подписка не заменяет сохранённые серверы" }
        require(incoming.size <= 2000)
        val nodes = current.profiles.associateByTo(linkedMapOf()) { it.id }
        require(nodes.size == current.profiles.size)
        require(current.memberships.all { it.profileId in nodes })
        val oldMembers = current.memberships.filter { it.subscriptionId == subscriptionId }.map { it.profileId }.toSet()
        val received = linkedSetOf<String>()
        var added = 0
        var updated = 0
        val uniqueIncoming = ImportedProfile.distinctByConfiguration(incoming)
        val incomingEndpoints = uniqueIncoming.groupBy(::endpointKey)
        val protectedIds = current.manuallyOwned + current.memberships.filter { it.subscriptionId != subscriptionId }.map { it.profileId }
        val oldEndpoints = current.profiles.filter { it.id in oldMembers && it.id !in protectedIds }.groupBy(::endpointKey)
        for (candidate in uniqueIncoming) {
            val exact = nodes.values.filter { it.configurationKey() == candidate.configurationKey() }.singleOrNull()
            // Key rotation is matched only when both sides have an unambiguous endpoint.
            // Never match a reused password or provider display name.
            val endpoint = endpointKey(candidate)
            val previous = exact ?: if (incomingEndpoints[endpoint]?.size == 1) oldEndpoints[endpoint]?.singleOrNull() else null
            // An exact shared connection retains its existing provider-independent record.
            // A rotation of one source cannot overwrite another source's still-valid key.
            val next = if (exact != null && exact.id in protectedIds) exact else if (previous == null) candidate else candidate.copy(id = previous.id,
                userSettings = previous.userSettings, latencyMillis = if (previous.configurationKey() == candidate.configurationKey())
                    previous.latencyMillis else null)
            require(previous != null || next.id !in nodes) { "Конфликт ID серверов" }
            if (previous == null) added++ else if (previous.configurationKey() != candidate.configurationKey() || previous.name != candidate.name) updated++
            nodes[next.id] = next
            received += next.id
        }
        val memberships = current.memberships.filterNot {
            complete && it.subscriptionId == subscriptionId && it.profileId !in received
        }.toMutableSet()
        memberships += received.map { SubscriptionMembership(it, subscriptionId) }
        val stillOwned = memberships.map { it.profileId }.toSet() + current.manuallyOwned + retainIds
        val detached = oldMembers.filter { it !in stillOwned }.toSet()
        detached.forEach(nodes::remove)
        require(nodes.size <= 2000) { "Слишком много сохранённых серверов" }
        return SubscriptionChanges(SubscriptionCatalog(nodes.values.toList(), memberships,
            current.manuallyOwned.intersect(nodes.keys)), added, updated, detached.size, !complete)
    }

    private fun endpointKey(profile: ImportedProfile): String {
        if (profile.fullConfigJson != null) return "full:${profile.configurationKey()}"
        val outbound = org.json.JSONObject(profile.outboundJson)
        val tls = outbound.optJSONObject("tls")
        val transport = outbound.optJSONObject("transport")
        // Fixed ordered array avoids delimiter collisions in host/path values.
        return org.json.JSONArray(listOf(profile.protocol.lowercase(java.util.Locale.ROOT),
            profile.server.lowercase(java.util.Locale.ROOT), profile.port,
            transport?.optString("type").orEmpty(), transport?.optString("host").orEmpty(),
            transport?.optString("path").orEmpty(), transport?.optString("service_name").orEmpty(),
            tls?.optString("server_name").orEmpty())).toString()
    }
}
