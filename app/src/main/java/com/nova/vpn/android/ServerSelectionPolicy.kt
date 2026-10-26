package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.VpnEngine
import java.security.MessageDigest

/** Only fresh measurements for this exact physical network and configuration may win. */
internal object ServerSelectionPolicy {
    private const val MAX_AGE_NANOS = 5L * 60 * 1_000_000_000

    fun configurationSignature(profile: ImportedProfile): String = MessageDigest.getInstance("SHA-256")
        .digest(profile.configurationKey().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun eligible(profile: ImportedProfile, result: ServerCheckResult?, network: String?, nowNanos: Long, engine: VpnEngine = VpnEngine.SING_BOX): Boolean {
        if (network == null || result == null || result.engine != engine || result.outcome != CheckOutcome.SUCCESS || result.millis == null || result.millis <= 0) return false
        val age = nowNanos - result.measuredAtNanos
        return age in 0..MAX_AGE_NANOS && result.networkSignature == network &&
            result.configurationSignature == configurationSignature(profile) &&
            result.attempts > 0 && result.successes in 2..result.attempts
    }

    fun best(profiles: List<ImportedProfile>, results: Map<String, ServerCheckResult>, network: String?, nowNanos: Long, engine: VpnEngine = VpnEngine.SING_BOX): ImportedProfile? =
        profiles.filter { eligible(it, results[it.id], network, nowNanos, engine) }
            .sortedWith(Comparator { first, second ->
                val a = results.getValue(first.id); val b = results.getValue(second.id)
                // Prefer application-check reliability, then median HTTPS response latency.
                val reliability = (b.successes.toLong() * a.attempts).compareTo(a.successes.toLong() * b.attempts)
                if (reliability != 0) reliability else a.millis!!.compareTo(b.millis!!)
            }).firstOrNull()
}
