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
        // Index the immutable pre-refresh catalog once. Re-parsing every saved
        // JSON for every incoming row is quadratic at the 2,000-server limit.
        // Exact duplicates can reuse an existing ID without changing connection options.
        val exactConnections = current.profiles.groupBy(::matchingKey)
        val uniqueIncoming = incoming.distinctBy(::matchingKey)
        val incomingEndpoints = uniqueIncoming.groupBy(::endpointKey)
        val protectedIds = current.manuallyOwned + current.memberships.filter { it.subscriptionId != subscriptionId }.map { it.profileId }
        val oldEndpoints = current.profiles.filter { it.id in oldMembers && it.id !in protectedIds }.groupBy(::endpointKey)
        for (candidate in uniqueIncoming) {
            val exact = exactConnections[matchingKey(candidate)]?.minWithOrNull(
                compareBy<ImportedProfile> { it.id !in retainIds }
                    .thenBy { it.id !in oldMembers }
                    .thenBy { it.retiredFromSubscription }
                    .thenBy { !it.userSettings.favorite })
            // Key rotation is matched only when both sides have an unambiguous endpoint.
            // Never match a reused password or provider display name.
            val endpoint = endpointKey(candidate)
            val previous = exact ?: if (complete && incomingEndpoints[endpoint]?.size == 1) oldEndpoints[endpoint]?.singleOrNull() else null
            // An exact shared connection retains its existing provider-independent record.
            // A rotation of one source cannot overwrite another source's still-valid key.
            val next = if (exact != null && exact.id in protectedIds) exact else if (previous == null) candidate else candidate.copy(id = previous.id,
                userSettings = previous.userSettings, latencyMillis = if (!previous.retiredFromSubscription && previous.configurationKey() == candidate.configurationKey())
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
        // A previously retained detached record is no longer an old member on
        // the next refresh. Reclaim it only after a complete response and after
        // every durable/live/manual/source owner has released it.
        val detached = if(complete)nodes.keys.filter {it !in stillOwned}.toSet() else emptySet()
        detached.forEach(nodes::remove)
        require(nodes.size <= 2000) { "Слишком много сохранённых серверов" }
        return SubscriptionChanges(SubscriptionCatalog(nodes.values.toList(), memberships,
            current.manuallyOwned.intersect(nodes.keys)), added, updated, detached.size, !complete)
    }

    // JSON object order and a standalone outbound label are not connection identity.
    // Preserve every credential and option; full configurations remain opaque graphs.
    internal fun matchingKey(profile: ImportedProfile): String =
        if(profile.fullConfigJson != null) "full:${profile.configurationKey()}"
        else runCatching { "outbound:"+canonical(org.json.JSONObject(profile.outboundJson).also { it.remove("tag") }) }
            .getOrElse { profile.configurationKey() }

    private fun endpointKey(profile: ImportedProfile): String {
        if (profile.fullConfigJson != null) return "full:${profile.configurationKey()}"
        val outbound=org.json.JSONObject(profile.outboundJson)
        // A dependent graph has no independently known network endpoint.
        if(outbound.has("detour") || outbound.has("domain_resolver"))return "dependent:${profile.configurationKey()}"
        // Compare authoritative native options, not display metadata. Strip only
        // credentials that may rotate; retain Host, path, TLS and dial semantics.
        listOf("tag","uuid","password","username","auth_str").forEach(outbound::remove)
        if(outbound.opt("auth") is String)outbound.remove("auth")
        (outbound.opt("server") as? String)?.let {outbound.put("server",it.lowercase(java.util.Locale.ROOT))}
        outbound.optJSONObject("tls")?.let {tls ->
            (tls.opt("server_name") as? String)?.let {tls.put("server_name",it.lowercase(java.util.Locale.ROOT))}
            tls.optJSONObject("reality")?.let {it.remove("public_key");it.remove("short_id")}
        }
        outbound.optJSONObject("obfs")?.remove("password")
        outbound.optJSONObject("transport")?.let {transport ->
            (transport.opt("host") as? String)?.let {transport.put("host",it.lowercase(java.util.Locale.ROOT))}
            transport.optJSONObject("headers")?.let {headers ->
                headers.keys().asSequence().toList().filter {it.equals("Host",ignoreCase=true)}.forEach {key ->
                    (headers.opt(key) as? String)?.let {headers.put(key,it.lowercase(java.util.Locale.ROOT))}
                }
            }
        }
        return canonical(outbound)
    }
    private fun canonical(value:Any?):String=when(value) {
        is org.json.JSONObject -> value.keys().asSequence().sorted().joinToString(prefix="{",postfix="}") {org.json.JSONObject.quote(it)+":"+canonical(value.get(it))}
        is org.json.JSONArray -> (0 until value.length()).joinToString(prefix="[",postfix="]") {canonical(value.get(it))}
        is String -> org.json.JSONObject.quote(value)
        else -> value.toString()
    }
}
