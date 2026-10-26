package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import java.net.URI
import java.text.Normalizer
import java.util.Locale

internal enum class ServerSort { MANUAL, NAME, COUNTRY, LATENCY }

internal object ServerListFilter {
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    fun apply(profiles: List<ImportedProfile>, query: String, favoritesOnly: Boolean,
        sort: ServerSort, subscriptionNames: Map<String, List<String>> = emptyMap(), country: (String) -> String?): List<ImportedProfile> {
        val terms = normalized(query).split(' ').filter { it.isNotEmpty() }
        val filtered = profiles.filter { profile ->
            (!favoritesOnly || profile.userSettings.favorite) && (terms.isEmpty() || run {
                // Explicit allowlist: never index serialized credentials, hosts or subscription paths.
                val host = runCatching { profile.subscriptionUrl?.let { URI(it).host } }.getOrNull().orEmpty()
                val searchable = normalized(listOf(profile.name, profile.userSettings.personalName.orEmpty(),
                    country(profile.name).orEmpty(), profile.protocol, profileTransport(profile), host,
                    subscriptionNames[profile.id].orEmpty().joinToString(" ")).joinToString(" "))
                terms.all { it in searchable }
            })
        }
        return when (sort) {
            ServerSort.MANUAL -> filtered.sortedBy { it.userSettings.order }
            ServerSort.NAME -> filtered.sortedBy { normalized(it.userSettings.personalName ?: it.name) }
            ServerSort.COUNTRY -> filtered.sortedWith(compareBy<ImportedProfile> { country(it.name) == null }
                .thenBy { country(it.name).orEmpty() }.thenBy { normalized(it.name) })
            ServerSort.LATENCY -> filtered.sortedBy { it.latencyMillis?.takeIf { value -> value > 0 } ?: Int.MAX_VALUE }
        }
    }
}
