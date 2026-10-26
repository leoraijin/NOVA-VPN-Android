package com.nova.vpn.android

import org.json.JSONArray
import java.security.MessageDigest

/** Compare only Android device policy; changing a server or site rule need not recreate TUN. */
internal object TunReusePolicy {
    fun singBox(deviceOptions: List<String>): String = "sing_box:" + digest(JSONArray(deviceOptions.distinct().sorted()).toString())

    // Reality's Builder uses fixed addresses/routes/DNS/MTU; its only variable
    // device inputs are the validated Android package filters, derived from routing.
    fun reality(included: List<String>, excluded: List<String>, uid: (String) -> Int): String = "reality:" + digest(
        JSONArray().put(JSONArray(included.sorted().map { "$it:${uid(it)}" }))
            .put(JSONArray(excluded.sorted().map { "$it:${uid(it)}" })).toString())

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

}
