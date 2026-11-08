package com.nova.vpn.android.data

import org.json.JSONObject

/** Mirrors effective application exits from the generated configuration. */
internal class SharedUidExitGuard private constructor(
    private val assignments: Map<String, String?>,
    private val defaultExit: String?
) {
    fun conflicts(packages: List<String>): Boolean {
        if (packages.size < 2 || assignments.isEmpty()) return false
        fun exit(name: String) = if (name in assignments) assignments[name] else defaultExit
        val first = exit(packages.first())
        return packages.drop(1).any { exit(it) != first }
    }

    companion object {
        // This cannot be a valid installed Android package name.
        const val CONFLICT_PACKAGE = "<nova-shared-uid-conflict>"

        fun capture(config: String): SharedUidExitGuard? {
            val rules = JSONObject(config).getJSONObject("route").getJSONArray("rules")
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
            for (index in 1 until rules.length()) {
                val rule = rules.getJSONObject(index)
                if (rule.optString("action") !in setOf("route", "reject")) continue
                val names = rule.optJSONArray("package_name") ?: continue
                for (packageIndex in 0 until names.length()) assignments[names.getString(packageIndex)] = exit(rule)
            }
            return SharedUidExitGuard(assignments.toMap(), exit(rules.getJSONObject(rules.length() - 1)))
        }
    }
}
