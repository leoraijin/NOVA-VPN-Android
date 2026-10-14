package com.nova.vpn.android.data

import org.json.JSONObject
import java.util.UUID

/** A user-owned connection entry. Credentials stay in encrypted app-private storage. */
data class ImportedProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val outboundJson: String,
    val fullConfigJson: String? = null,
    val subscriptionUrl: String? = null,
    val latencyMillis: Int? = null
) {
    /**
     * Full sing-box imports store their configuration separately and use an empty outbound
     * placeholder. Deduplicating them by outboundJson would silently discard every later
     * full-config import, regardless of its actual servers or routing rules.
     */
    fun configurationKey(): String = fullConfigJson?.let { "full:$it" } ?: "outbound:$outboundJson"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("protocol", protocol)
        .put("server", server)
        .put("port", port)
        .put("outbound", outboundJson)
        .put("fullConfig", fullConfigJson)
        .put("subscriptionUrl", subscriptionUrl)
        .put("latencyMillis", latencyMillis)

    companion object {
        fun distinctByConfiguration(profiles: List<ImportedProfile>): List<ImportedProfile> =
            profiles.distinctBy(ImportedProfile::configurationKey)

        fun fromJson(json: JSONObject) = ImportedProfile(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.getString("name"),
            protocol = json.getString("protocol"),
            server = json.optString("server"),
            port = json.optInt("port"),
            outboundJson = json.optString("outbound", "{}"),
            fullConfigJson = json.optString("fullConfig").takeIf(String::isNotBlank),
            subscriptionUrl = json.optString("subscriptionUrl").takeIf(String::isNotBlank),
            latencyMillis = json.optInt("latencyMillis").takeIf { it > 0 }
        )
    }
}
