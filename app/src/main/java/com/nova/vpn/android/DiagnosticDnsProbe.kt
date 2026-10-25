package com.nova.vpn.android

import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal enum class DiagnosticDnsResult(val code: String) {
    NOT_NEEDED("dns_system_lookup_not_needed"), READY("dns_system_lookup_ready"),
    FAILED("dns_system_lookup_failed"), TIMEOUT("dns_system_lookup_timeout")
}

/** Separate system-DNS observation; never replaces the native core's configured resolver. */
internal object DiagnosticDnsProbe {
    fun host(config: String): String? = runCatching {
        val json = JSONObject(config)
        val finalTag = json.getJSONObject("route").getString("final")
        val all = json.getJSONArray("outbounds")
        val server = (0 until all.length()).mapNotNull(all::optJSONObject)
            .firstOrNull { it.optString("tag") == finalTag }?.optString("server").orEmpty()
        server.takeIf { it.isNotBlank() && it.length <= 253 && ':' !in it && !it.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) }
    }.getOrNull()

    fun check(host: String?, timeoutMillis: Long = 2000, lookup: (String) -> Boolean): DiagnosticDnsResult {
        if (host == null) return DiagnosticDnsResult.NOT_NEEDED
        require(timeoutMillis in 1..5000)
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "nova-diagnostic-dns").apply { isDaemon = true } }
        val future = executor.submit<Boolean> { lookup(host) }
        return try {
            if (future.get(timeoutMillis, TimeUnit.MILLISECONDS)) DiagnosticDnsResult.READY else DiagnosticDnsResult.FAILED
        } catch (_: TimeoutException) { DiagnosticDnsResult.TIMEOUT }
        catch (_: Exception) { DiagnosticDnsResult.FAILED }
        finally { future.cancel(true); executor.shutdownNow() }
    }
}
