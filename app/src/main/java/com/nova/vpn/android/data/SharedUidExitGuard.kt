package com.nova.vpn.android.data

import org.json.JSONObject

/** Mirrors effective application exits from the generated configuration. */
internal class SharedUidExitGuard private constructor(
    private val assignments: Map<String, String?>,
    private val defaultExit: String?,
    val androidDirectCandidates: Set<String>
) {
    fun hasAssignment(packageName: String): Boolean = packageName in assignments

    fun conflicts(packages: List<String>): Boolean {
        if (packages.size < 2 || assignments.isEmpty()) return false
        fun exit(name: String) = if (name in assignments) assignments[name] else defaultExit
        val first = exit(packages.first())
        return packages.drop(1).any { exit(it) != first }
    }

    companion object {
        // This cannot be a valid installed Android package name.
        const val CONFLICT_PACKAGE = "<nova-shared-uid-conflict>"

        fun hasManagedRules(config: String): Boolean = JSONObject(config).getJSONObject("route").optJSONArray("rules")
            ?.optJSONObject(0)?.optJSONArray("package_name")?.optString(0) == CONFLICT_PACKAGE

        fun capture(config: String): SharedUidExitGuard? {
            val route = JSONObject(config).getJSONObject("route")
            val rules = route.getJSONArray("rules")
            val assignments = mutableMapOf<String, String?>()
            fun exit(rule: JSONObject): String? = when (rule.getString("action")) {
                "route" -> rule.getString("outbound")
                "reject" -> null
                else -> error("Expected a terminal route rule")
            }
            val conflict = rules.getJSONObject(0)
            if (conflict.optJSONArray("package_name")?.optString(0) != CONFLICT_PACKAGE) {
                check((0 until rules.length()).none { rules.getJSONObject(it).has("package_name") })
                return null
            }
            check(conflict.getString("action") == "reject")
            val ordered = (1 until rules.length()).map(rules::getJSONObject)
            val terminal = ordered.filter {
                it.optString("action") in setOf("route", "reject") &&
                    it.keys().asSequence().all { key -> key in setOf("package_name", "invert", "action", "outbound") }
            }
            val defaultRule = terminal.firstOrNull { !it.has("package_name") || it.optBoolean("invert") }
            val fallback = if (defaultRule == null) route.optString("final").takeIf { it.isNotEmpty() } else exit(defaultRule)
            val names = terminal.flatMap { rule -> rule.optJSONArray("package_name")?.let { array ->
                (0 until array.length()).map(array::getString)
            }.orEmpty() }.toSet()
            for (name in names) {
                val first = terminal.firstOrNull { rule ->
                    val packages = rule.optJSONArray("package_name")
                    packages == null || ((0 until packages.length()).any { packages.getString(it) == name } != rule.optBoolean("invert"))
                }
                assignments[name] = if (first == null) fallback else exit(first)
            }
            val directTag = MultiServerConfigFactory.DIRECT_TAG
            // A site override needs to see traffic from direct apps too. Preserve
            // VPN and block rules, as well as explicit site DNS outbounds.
            val siteNeedsTunnel = ordered.any { rule ->
                (rule.has("domain") || rule.has("domain_suffix")) &&
                    (rule.optString("action") == "reject" ||
                        rule.optString("action") == "route" && rule.optString("outbound") != directTag)
            }
            val androidDirect = if (siteNeedsTunnel) emptySet()
                else assignments.filterValues { it == directTag }.keys.toSet()
            return SharedUidExitGuard(assignments.toMap(), fallback, androidDirect)
        }
    }
}
