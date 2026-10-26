package com.nova.vpn.android

import org.json.JSONArray

/** IPC accepts bounded, revalidated metadata only, never a native log string. */
internal object DiagnosticEventCodec {
    fun encode(events: List<DiagnosticEvent>): String = JSONArray().apply { events.takeLast(40).forEach { put(it.toJson()) } }.toString()
    fun decode(value: String?): List<DiagnosticEvent> = runCatching {
        if (value == null) return emptyList()
        require(value.length <= 32_768)
        val array = JSONArray(value)
        require(array.length() <= 40)
        List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val raw = json.getString("code")
            val code = DiagnosticCatalog.code(raw)
            val elapsed = json.getLong("elapsed_ms").also { require(it in 0..120_000) }
            val engine = json.getString("engine").takeIf { it in setOf("sing_box", "reality_core") } ?: "other"
            val generation = json.getLong("network_generation").coerceIn(0, 1_000_000_000)
            val transport = json.getString("transport").takeIf { it in setOf("tcp", "ws", "grpc", "http", "httpupgrade", "quic", "xhttp") } ?: "other"
            val numbers = json.optJSONObject("metrics")
            val metrics = DiagnosticMetric.entries.mapNotNull { key ->
                val value = numbers?.opt(key.name.lowercase()) as? Number
                value?.toLong()?.coerceIn(0, 1_000_000_000)?.let { key to it }
            }.toMap()
            DiagnosticEvent(elapsed, DiagnosticCatalog.stage(code), code, engine, generation, transport, metrics)
        }
    }.getOrDefault(emptyList())
}
