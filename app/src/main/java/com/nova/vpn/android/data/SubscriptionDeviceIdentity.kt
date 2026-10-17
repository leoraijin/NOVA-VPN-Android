package com.nova.vpn.android.data

import android.content.Context
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Opt-in subscription identity. No hardware identifiers; different ID per HTTPS origin. */
class SubscriptionDeviceIdentity(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("subscription_identity", Context.MODE_PRIVATE)

    @Synchronized
    fun forUrl(input: String): String {
        val uri = URI(input.trim())
        require(uri.scheme.equals("https", true) && uri.host != null) { "Для HWID нужна HTTPS-подписка." }
        val seed = preferences.getString("seed", null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString("seed", it).commit()) { "Не удалось сохранить идентификатор устройства." }
        }
        return derive(seed, uri)
    }

    companion object {
        internal fun derive(seed: String, uri: URI): String {
            val origin = "${uri.host.lowercase(Locale.ROOT)}:${if (uri.port == -1) 443 else uri.port}"
            return MessageDigest.getInstance("SHA-256").digest("$seed\n$origin".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
