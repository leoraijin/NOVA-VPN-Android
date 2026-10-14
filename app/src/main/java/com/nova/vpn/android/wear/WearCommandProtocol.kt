package com.nova.vpn.android.wear

import org.json.JSONObject
import java.util.UUID

internal object WearCommandProtocol {
    fun requestId(data: ByteArray): String? {
        if (data.size > 64) return null
        val id = data.toString(Charsets.UTF_8)
        return if (id.isEmpty() || runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) id else null
    }
    fun reply(id: String, state: String): ByteArray = JSONObject().put("requestId", id).put("state", state)
        .toString().toByteArray(Charsets.UTF_8)
}
