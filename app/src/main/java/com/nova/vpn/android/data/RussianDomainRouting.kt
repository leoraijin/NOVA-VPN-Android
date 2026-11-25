package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

/** Built-in site route, kept separate from the user's saved rules. */
internal object RussianDomainRouting {
    private const val SUFFIX = ".ru"
    const val PHYSICAL_DNS = "nova-bootstrap-dns"

    fun matches(host: String?) = host?.endsWith(SUFFIX, ignoreCase = true) == true

    fun selector() = JSONObject().put("domain_suffix", JSONArray().put(SUFFIX))

    fun lookup() = selector().put("action", "route").put("server", PHYSICAL_DNS)

    fun isLookup(rule: JSONObject) = rule.length() == 3 &&
        rule.optString("action") == "route" && rule.optString("server") == PHYSICAL_DNS && hasSuffix(rule)

    fun isRoute(rule: JSONObject) = rule.length() == 3 &&
        rule.optString("action") == "route" && rule.has("outbound") && hasSuffix(rule)

    private fun hasSuffix(rule: JSONObject) = rule.optJSONArray("domain_suffix")?.let {
        it.length() == 1 && it.optString(0) == SUFFIX
    } == true
}
