package com.nova.vpn.android

import com.nova.vpn.android.data.AppRoutingSettings
import com.nova.vpn.android.data.MultiServerConfigFactory
import org.json.JSONArray
import org.json.JSONObject

/** Runs only after the whole candidate was rejected, on the service's native queue. */
internal object NativeExitIsolation {
    private const val MAX_NATIVE_CHECKS = 32

    data class Candidate(val config: String, val bindings: RouteExitBindings)

    fun rebuild(config: String, bindings: RouteExitBindings, settings: AppRoutingSettings, ownPackage: String,
        current: () -> Boolean, accepts: (String) -> Boolean): Candidate? {
        if (bindings.entries.isEmpty() || !current()) return null
        val multi = requireNotNull(settings.multiServer)
        val referenced = requireNotNull(RoutePolicyIdentity.capture(settings)).profileIds
        bindings.validate(config, referenced)
        val root = JSONObject(config)
        val outbounds = root.getJSONArray("outbounds")
        val byTag = (0 until outbounds.length()).map(outbounds::getJSONObject).associateBy { it.getString("tag") }
        val dns = root.getJSONObject("dns").getJSONArray("servers")
        val bootstrap = (0 until dns.length()).map(dns::getJSONObject).single { it.optString("tag") == "nova-bootstrap-dns" }
        var remaining = MAX_NATIVE_CHECKS
        fun check(tags: List<String>): Boolean? {
            if (remaining == 0 || !current()) return null
            remaining--
            val probe = JSONObject()
                .put("outbounds", JSONArray(tags.map(byTag::getValue)))
                .put("dns", JSONObject().put("servers", JSONArray().put(bootstrap)))
                .put("route", JSONObject().put("final", tags.first()))
            return accepts(probe.toString())
        }

        // A shared failure is not evidence against any exit.
        if (check(listOf("nova-direct")) != true) return null
        val rejected = linkedSetOf<String>()
        fun inspect(exits: List<Map.Entry<String, RouteExitBindings.Entry>>): Boolean {
            val accepted = check(exits.map { it.value.tag }) ?: return false
            if (accepted) return true
            if (exits.size == 1) {
                rejected.add(exits.single().key)
                return true
            }
            val middle = exits.size / 2
            return inspect(exits.subList(0, middle)) && inspect(exits.subList(middle, exits.size))
        }
        if (!inspect(bindings.entries.entries.sortedBy { it.key }) || rejected.isEmpty() || !current()) return null

        val healthy = bindings.entries.filterKeys { it !in rejected }
        val cache = root.getJSONObject("experimental").getJSONObject("cache_file").getString("path")
        val rebuilt = MultiServerConfigFactory.build(root, multi, healthy.mapValues { byTag.getValue(it.value.tag) },
            ownPackage, settings.ipv6Enabled, cache).toString()
        return Candidate(rebuilt, bindings.withNativeRejected(rebuilt, rejected).also { it.validate(rebuilt, referenced) })
    }
}
