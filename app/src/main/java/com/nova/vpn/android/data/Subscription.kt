package com.nova.vpn.android.data

import org.json.JSONObject
import java.net.URI
import java.util.UUID

/** All fields, including URLs and conditional request headers, live in the encrypted vault. */
internal data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val sendDeviceId: Boolean = false,
    val intervalHours: Int = 0,
    val lastAttempt: Long = 0,
    val lastSuccess: Long = 0,
    val status: String = "never",
    val etag: String? = null,
    val lastModified: String? = null,
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiresAtSeconds: Long? = null,
    val retryAfterMillis: Long = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0
) {
    fun validate() {
        val uri = URI(url)
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 100)
        require(url.length <= 4096 && uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null)
        require(uri.port == -1 || uri.port in 1..65535)
        require(intervalHours in setOf(0, 6, 12, 24))
        require(status in STATUSES)
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("url", url)
        .put("sendDeviceId", sendDeviceId).put("intervalHours", intervalHours)
        .put("lastAttempt", lastAttempt).put("lastSuccess", lastSuccess).put("status", status)
        .put("etag", etag).put("lastModified", lastModified).put("usedBytes", usedBytes)
        .put("totalBytes", totalBytes).put("expiresAtSeconds", expiresAtSeconds)
        .put("retryAfterMillis", retryAfterMillis).put("added", added).put("updated", updated).put("removed", removed)

    companion object {
        val STATUSES = setOf("never", "ok", "partial", "not_modified", "error", "auth", "rate_limited")
        fun fromJson(json: JSONObject): Subscription {
            fun text(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }
            fun number(key: String) = if (json.isNull(key)) null else json.getLong(key).takeIf { it >= 0 }
            return Subscription(id = json.getString("id"), name = json.getString("name"), url = json.getString("url"),
                sendDeviceId = json.optBoolean("sendDeviceId"), intervalHours = json.optInt("intervalHours"),
                lastAttempt = json.optLong("lastAttempt"), lastSuccess = json.optLong("lastSuccess"),
                status = json.optString("status", "never"), etag = text("etag"), lastModified = text("lastModified"),
                usedBytes = number("usedBytes"), totalBytes = number("totalBytes"), expiresAtSeconds = number("expiresAtSeconds"),
                retryAfterMillis = json.optLong("retryAfterMillis"), added = json.optInt("added"), updated = json.optInt("updated"),
                removed = json.optInt("removed")).also { it.validate() }
        }
    }
}

internal data class ProfileSnapshot(
    val profiles: List<ImportedProfile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val memberships: Set<SubscriptionMembership> = emptySet(),
    val manuallyOwned: Set<String> = emptySet()
) {
    fun catalog() = SubscriptionCatalog(profiles, memberships, manuallyOwned)

    fun importManual(incoming: List<ImportedProfile>): ProfileSnapshot {
        val merged = ImportedProfile.merge(profiles, incoming)
        val keys = incoming.map { it.configurationKey() }.toSet()
        return withProfiles(merged).copy(manuallyOwned = manuallyOwned + merged.filter { it.configurationKey() in keys }.map { it.id })
    }

    /** Compatibility entry point for manual import/delete/favorite operations. */
    fun withProfiles(next: List<ImportedProfile>): ProfileSnapshot {
        val migrated = migrate(next)
        val ids = next.map { it.id }.toSet()
        val existingUrls = subscriptions.map { it.url }.toSet()
        val newSources = migrated.subscriptions.filter { it.url !in existingUrls }
        val sources = subscriptions + newSources
        val sourceByUrl = sources.associateBy { it.url }
        val links = memberships.filter { it.profileId in ids }.toMutableSet()
        next.forEach { profile ->
            profile.subscriptionUrl?.let { sourceByUrl[it] }?.let { links += SubscriptionMembership(profile.id, it.id) }
        }
        return copy(profiles = next, subscriptions = sources, memberships = links,
            manuallyOwned = (manuallyOwned.intersect(ids) + migrated.manuallyOwned))
    }

    companion object {
        fun migrate(profiles: List<ImportedProfile>): ProfileSnapshot {
            val sources = profiles.mapNotNull { it.subscriptionUrl }.distinct().mapNotNull { url ->
                runCatching { Subscription(id = UUID.nameUUIDFromBytes(url.toByteArray(Charsets.UTF_8)).toString(),
                    name = URI(url).host ?: "Подписка", url = url).also { it.validate() } }.getOrNull()
            }
            val byUrl = sources.associateBy { it.url }
            val links = profiles.mapNotNull { p -> byUrl[p.subscriptionUrl]?.let { SubscriptionMembership(p.id, it.id) } }.toSet()
            return ProfileSnapshot(profiles, sources, links, profiles.filter { p -> links.none { it.profileId == p.id } }.map { it.id }.toSet())
        }
    }
}
