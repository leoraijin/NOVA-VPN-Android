package com.nova.vpn.android.data

import org.json.JSONObject

/** Maps Xray share-link/JSON options to the pinned sing-box-lx native transport. */
internal object XhttpTransport {
    private val fields = mapOf(
        "host" to "host", "path" to "path", "mode" to "mode", "headers" to "headers",
        "xPaddingBytes" to "x_padding_bytes", "noGRPCHeader" to "no_grpc_header",
        "sessionIDPlacement" to "session_placement", "sessionIDKey" to "session_key",
        "sessionPlacement" to "session_placement", "sessionKey" to "session_key",
        "sessionIDTable" to "session_table", "sessionIDLength" to "session_length",
        "seqPlacement" to "seq_placement", "seqKey" to "seq_key",
        "uplinkDataPlacement" to "uplink_data_placement", "uplinkDataKey" to "uplink_data_key",
        "uplinkChunkSize" to "uplink_chunk_size", "uplinkHTTPMethod" to "uplink_http_method",
        "xPaddingObfsMode" to "x_padding_obfs_mode", "xPaddingKey" to "x_padding_key",
        "xPaddingHeader" to "x_padding_header", "xPaddingPlacement" to "x_padding_placement",
        "xPaddingMethod" to "x_padding_method", "scMaxEachPostBytes" to "sc_max_each_post_bytes",
        "scMinPostsIntervalMs" to "sc_min_posts_interval_ms", "scMaxConcurrentPosts" to "sc_max_concurrent_posts",
        "noSSEHeader" to "no_sse_header", "scMaxBufferedPosts" to "sc_max_buffered_posts",
        "scStreamUpServerSecs" to "sc_stream_up_server_secs", "serverMaxHeaderBytes" to "server_max_header_bytes"
    )
    private val ranges = setOf("x_padding_bytes", "session_length", "uplink_chunk_size", "sc_max_each_post_bytes",
        "sc_min_posts_interval_ms", "sc_stream_up_server_secs")

    fun fromQuery(query: Map<String, String>): JSONObject {
        val settings = JSONObject()
        query["extra"]?.takeIf(String::isNotBlank)?.let {
            val extra = try { JSONObject(it) } catch (_: Exception) { throw IllegalArgumentException("Некорректный extra XHTTP") }
            extra.keys().forEach { key -> settings.put(key, extra.get(key)) }
        }
        // Explicit query values take priority over extra, matching other transport fields.
        fields.keys.forEach { key -> query[key.lowercase()]?.let { value ->
            settings.put(key, when {
                key in setOf("noGRPCHeader", "xPaddingObfsMode", "noSSEHeader") -> value == "1" || value.equals("true", true)
                key == "headers" -> try { JSONObject(value) } catch (_: Exception) { throw IllegalArgumentException("Некорректные заголовки XHTTP") }
                key in setOf("scMaxConcurrentPosts", "scMaxBufferedPosts", "serverMaxHeaderBytes") ->
                    value.toLongOrNull() ?: throw IllegalArgumentException("Некорректный числовой параметр XHTTP")
                else -> value
            })
        } }
        query["xmux"]?.let { value -> settings.put("xmux", try { JSONObject(value) }
            catch (_: Exception) { throw IllegalArgumentException("Некорректный XMUX XHTTP") }) }
        return fromSettings(settings)
    }

    fun fromSettings(settings: JSONObject): JSONObject {
        val merged = JSONObject(settings.toString())
        settings.optJSONObject("extra")?.let { extra -> extra.keys().forEach { key -> merged.put(key, extra.get(key)) } }
        require(!merged.has("downloadSettings") || merged.isNull("downloadSettings")) { "XHTTP downloadSettings требует отдельного подключения и пока не поддержан" }
        val out = JSONObject().put("type", "xhttp")
        fields.forEach { (source, target) ->
            if (merged.has(source) && !merged.isNull(source)) {
                val value = merged.get(source)
                out.put(target, if (target in ranges) value.toString() else value)
            }
        }
        merged.optJSONObject("xmux")?.let { xmux ->
            val mapped = JSONObject()
            mapOf("maxConcurrency" to "max_concurrency", "maxConnections" to "max_connections",
                "cMaxReuseTimes" to "c_max_reuse_times", "hMaxRequestTimes" to "h_max_request_times",
                "hMaxReusableSecs" to "h_max_reusable_secs", "hKeepAlivePeriod" to "h_keep_alive_period").forEach { (key, target) ->
                if (xmux.has(key)) mapped.put(target, xmux.get(key))
            }
            out.put("xmux", mapped)
        }
        val mode = out.optString("mode", "auto").ifBlank { "auto" }
        require(mode in setOf("auto", "packet-up", "stream-up", "stream-one")) { "Неизвестный режим XHTTP" }
        out.put("mode", mode)
        return out
    }
}
