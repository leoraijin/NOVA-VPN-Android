package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject

/** Versioned plaintext exists only in memory, inside the authenticated vault envelope. */
internal object ProfileDocument {
    const val SCHEMA = 2

    fun decode(text: String): List<ImportedProfile> = decodeSnapshot(text).profiles

    fun decodeSnapshot(text: String): ProfileSnapshot {
        val trimmed = text.trimStart()
        val root = if (trimmed.startsWith("[")) null else JSONObject(text)
        val profiles = if (root == null) JSONArray(text) else {
            require(root.getInt("schema") in 1..SCHEMA) { "Неподдерживаемая версия хранилища профилей" }
            root.getJSONArray("profiles")
        }
        val result = List(profiles.length()) { ImportedProfile.fromJson(profiles.getJSONObject(it)) }
        require(result.map { it.id }.toSet().size == result.size) { "Повторяющиеся ID в хранилище профилей" }
        val cleaned = SubscriptionPlaceholders.withoutPlaceholders(result)
        if (root == null || root.getInt("schema") == 1) return ProfileSnapshot.migrate(cleaned)
        val subscriptions = root.getJSONArray("subscriptions").let { array -> List(array.length()) { Subscription.fromJson(array.getJSONObject(it)) } }
        val memberships = root.getJSONArray("memberships").let { array -> List(array.length()) {
            val item = array.getJSONObject(it)
            SubscriptionMembership(item.getString("profileId"), item.getString("subscriptionId"))
        }.toSet() }
        val manual = root.getJSONArray("manuallyOwned").let { array -> List(array.length()) { array.getString(it) }.toSet() }
        return ProfileSnapshot(cleaned, subscriptions, memberships, manual).also(::validate)
    }

    fun encode(profiles: List<ImportedProfile>): String = encodeSnapshot(ProfileSnapshot.migrate(profiles))

    fun encodeSnapshot(snapshot: ProfileSnapshot): String {
        validate(snapshot)
        return JSONObject().put("schema", SCHEMA)
            .put("profiles", JSONArray().apply { snapshot.profiles.forEach { put(it.toJson()) } })
            .put("subscriptions", JSONArray().apply { snapshot.subscriptions.forEach { put(it.toJson()) } })
            .put("memberships", JSONArray().apply { snapshot.memberships.forEach { put(JSONObject().put("profileId", it.profileId).put("subscriptionId", it.subscriptionId)) } })
            .put("manuallyOwned", JSONArray(snapshot.manuallyOwned.toList())).toString()
    }

    private fun validate(snapshot: ProfileSnapshot) {
        val ids = snapshot.profiles.map { it.id }.toSet()
        val sources = snapshot.subscriptions.map { it.id }.toSet()
        require(ids.size == snapshot.profiles.size && ids.size <= 2000) { "Повторяющиеся ID профилей или превышен лимит" }
        require(sources.size == snapshot.subscriptions.size && sources.size <= 100)
        require(snapshot.subscriptions.map { it.url }.distinct().size == snapshot.subscriptions.size)
        snapshot.subscriptions.forEach { it.validate() }
        require(snapshot.memberships.all { it.profileId in ids && it.subscriptionId in sources })
        require(snapshot.manuallyOwned.all { it in ids })
    }
}
