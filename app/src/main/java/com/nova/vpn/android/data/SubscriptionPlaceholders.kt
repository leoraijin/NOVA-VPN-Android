package com.nova.vpn.android.data

/** A panel status entry, not a VPN endpoint. Never classify by display name alone. */
object SubscriptionPlaceholders {
    private val statusNames = setOf(
        "приложение не поддерживается", "application not supported", "app not supported"
    )

    fun isPlaceholder(profile: ImportedProfile): Boolean =
        profile.fullConfigJson == null &&
            profile.protocol.equals("vless", ignoreCase = true) &&
            profile.server == "0.0.0.0" && profile.port == 1 &&
            profile.name.trim().lowercase() in statusNames

    fun withoutPlaceholders(profiles: List<ImportedProfile>): List<ImportedProfile> =
        profiles.filterNot(::isPlaceholder)

    fun importedProfiles(profiles: List<ImportedProfile>): List<ImportedProfile> {
        val usable = withoutPlaceholders(profiles)
        require(usable.isNotEmpty()) {
            "Провайдер вернул только служебное сообщение: приложение не поддерживается. Рабочих серверов в ответе нет."
        }
        return ImportedProfile.distinctByConfiguration(usable)
    }
}
