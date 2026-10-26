package com.nova.vpn.android

import org.json.JSONObject

internal enum class DiagnosticStage { PERMISSION, SERVICE, NETWORK, DNS, SERVER, TLS, TRANSPORT, AUTHORIZATION, INTERNET, ROUTING, AUTOMATIC, UNKNOWN }
internal enum class DiagnosticMetric { RESPONSE_MS, SUCCESSFUL_PROBES, TOTAL_PROBES, RETRY }
internal data class DiagnosticEvent(val elapsedMillis: Long, val stage: DiagnosticStage, val code: String,
    val engine: String, val networkGeneration: Long, val transport: String, val metrics: Map<DiagnosticMetric, Long>) {
    fun toJson(): JSONObject = JSONObject().put("elapsed_ms", elapsedMillis).put("stage", stage.name.lowercase())
        .put("code", code).put("engine", engine).put("network_generation", networkGeneration).put("transport", transport)
        .put("metrics", JSONObject().apply { metrics.forEach { (key, value) -> put(key.name.lowercase(), value) } })
}

/** Memory-only, bounded by both count and age. Clock is injectable for sleep/expiry tests. */
internal class DiagnosticLog(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val events = ArrayDeque<DiagnosticEvent>()
    private var started = clock()
    fun reset() { events.clear(); started = clock() }
    fun append(stage: DiagnosticStage, code: String, engine: String, generation: Long, transport: String,
        metrics: Map<DiagnosticMetric, Long> = emptyMap()) {
        val event = DiagnosticEvent((clock() - started).coerceAtLeast(0), stage, code, engine, generation, transport,
            metrics.mapValues { it.value.coerceIn(0, 1_000_000_000) })
        prune(event.elapsedMillis)
        val previous = events.lastOrNull()
        if (previous != null && previous.code == code && previous.elapsedMillis / 1000 == event.elapsedMillis / 1000 && previous.metrics == event.metrics) return
        if (events.size == 40) events.removeFirst()
        events.addLast(event)
    }
    fun snapshot(): List<DiagnosticEvent> { prune((clock() - started).coerceAtLeast(0)); return events.toList() }
    private fun prune(elapsed: Long) { while (events.isNotEmpty() && elapsed - events.first().elapsedMillis > 1_800_000) events.removeFirst() }
}
