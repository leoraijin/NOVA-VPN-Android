package com.nova.vpn.android

import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal enum class DiagnosticDnsResult(val code: String) {
    NOT_NEEDED("dns_system_lookup_not_needed"), READY("dns_system_lookup_ready"),
    FAILED("dns_system_lookup_failed"), TIMEOUT("dns_system_lookup_timeout"),
    BUSY("dns_system_lookup_busy"), CANCELLED("dns_system_lookup_cancelled")
}

/** Separate system-DNS observation; never replaces the native core's configured resolver. */
internal object DiagnosticDnsProbe {
    private val inFlight = AtomicBoolean()
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
        if (Thread.currentThread().isInterrupted) return DiagnosticDnsResult.CANCELLED
        // A native resolver can ignore interruption. Keep the slot until the
        // actual lookup exits, not merely until its observer times out.
        if (!inFlight.compareAndSet(false, true)) return DiagnosticDnsResult.BUSY
        var completion: CompletableFuture<Boolean>? = null
        var worker: Thread? = null
        var started = false
        return try {
            val future = CompletableFuture<Boolean>()
            completion = future
            val thread = Thread({
                val outcome = try { runCatching { lookup(host) } } finally { inFlight.set(false) }
                outcome.fold(future::complete, future::completeExceptionally)
            }, "nova-diagnostic-dns").apply { isDaemon = true }
            worker = thread
            thread.start()
            started = true
            if (future.get(timeoutMillis, TimeUnit.MILLISECONDS)) DiagnosticDnsResult.READY else DiagnosticDnsResult.FAILED
        } catch (_: TimeoutException) { DiagnosticDnsResult.TIMEOUT }
        catch (_: InterruptedException) { Thread.currentThread().interrupt(); DiagnosticDnsResult.CANCELLED }
        catch (_: Exception) { DiagnosticDnsResult.FAILED }
        finally {
            if (!started) inFlight.set(false)
            completion?.cancel(false)
            worker?.interrupt()
        }
    }
}
