package com.nova.vpn.android.data

import org.json.JSONObject

/** User-owned metadata is independent of provider configuration and merge keys. */
data class ProfileUserSettings(
    val favorite: Boolean = false,
    val personalName: String? = null,
    val order: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().put("favorite", favorite)
        .put("personalName", personalName).put("order", order)

    companion object {
        fun fromJson(json: JSONObject?) = if (json == null) ProfileUserSettings() else ProfileUserSettings(
            favorite = json.optBoolean("favorite"),
            personalName = if (json.isNull("personalName")) null else json.optString("personalName").takeIf { it.isNotBlank() },
            order = json.optLong("order")
        )
    }
}
