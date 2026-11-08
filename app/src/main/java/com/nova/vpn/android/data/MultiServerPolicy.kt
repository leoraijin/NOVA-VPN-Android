package com.nova.vpn.android.data

/** Immutable route identities; no credentials or raw configurations. */
enum class ServerRouteKind { DEFAULT, PROFILE, DIRECT, BLOCK }
data class ServerRouteTarget(val kind: ServerRouteKind, val profileId: String? = null) {
    init { require(if (kind == ServerRouteKind.PROFILE) !profileId.isNullOrBlank() else profileId == null) }
}
data class MultiServerRule(val packageName: String? = null, val domain: String? = null,
    val includeSubdomains: Boolean = true, val target: ServerRouteTarget) {
    init {
        require((packageName == null) != (domain == null))
        require(packageName == null || packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")))
        require(domain == null || SiteDomain.normalize(domain) == domain && !domain.matches(Regex("[0-9.]+")))
    }
    internal fun matches(app: String?, host: String?) = if (domain != null)
        host != null && (host == domain || includeSubdomains && host.endsWith(".$domain")) else packageName == app
}
data class MultiServerDecision(val requested: ServerRouteTarget, val effective: ServerRouteTarget, val reason: String)

/** Captured editor/profile snapshot. A missing pinned server never falls back to DEFAULT. */
class MultiServerPolicy(val revision: Long, val defaultProfileId: String,
    rules: List<MultiServerRule>, availableProfileIds: Set<String>) {
    private val rules = rules.toList()
    private val profiles = availableProfileIds.toSet()
    val referencedProfileIds: Set<String> get() = (rules.mapNotNull { it.target.profileId } + defaultProfileId).toSet()
    init {
        require(revision >= 0 && defaultProfileId.isNotBlank() && rules.size <= 12000)
        require(rules.map { if (it.domain != null) "d:${it.domain}:${it.includeSubdomains}" else "a:${it.packageName}" }.distinct().size == rules.size)
    }
    fun decide(packageName: String?, domain: String?, ownerKnown: Boolean = true): MultiServerDecision {
        val host = domain?.let(SiteDomain::normalize)
        val match = rules.filter { it.matches(packageName, host) }.sortedWith(
            compareBy<MultiServerRule> { if (it.target.kind == ServerRouteKind.BLOCK) 0 else if (it.domain != null) 1 else 2 }
                .thenByDescending { it.domain?.length ?: 0 }.thenBy { it.includeSubdomains }).firstOrNull()
        val requested = match?.target ?: ServerRouteTarget(ServerRouteKind.DEFAULT)
        if (match == null && !ownerKnown && rules.any { it.packageName != null })
            return MultiServerDecision(requested, ServerRouteTarget(ServerRouteKind.BLOCK), "unknown-owner")
        val effective = if (requested.kind == ServerRouteKind.DEFAULT) ServerRouteTarget(ServerRouteKind.PROFILE, defaultProfileId) else requested
        if (effective.kind == ServerRouteKind.PROFILE && effective.profileId !in profiles)
            return MultiServerDecision(requested, ServerRouteTarget(ServerRouteKind.BLOCK), "missing-profile")
        return MultiServerDecision(requested, effective, if (match == null) "default" else if (match.domain != null) "site" else "application")
    }
}