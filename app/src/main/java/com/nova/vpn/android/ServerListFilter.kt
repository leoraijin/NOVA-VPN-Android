package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import java.net.URI
import java.text.Normalizer
import java.util.Locale

internal enum class ServerSort { MANUAL, NAME, COUNTRY, LATENCY }

internal object ServerListFilter {
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(spaces, " ")

    private val spaces = Regex("\\s+")

    fun apply(profiles: List<ImportedProfile>, query: String, favoritesOnly: Boolean,
        sort: ServerSort, subscriptionNames: Map<String, List<String>> = emptyMap(), country: (String) -> String?): List<ImportedProfile> =
        Index(profiles, subscriptionNames, country).filter(query, favoritesOnly, sort)

    class Index(profiles: List<ImportedProfile>, subscriptionNames: Map<String, List<String>>,
        country: (String) -> String?) {
        private data class Row(val profile: ImportedProfile, val searchable: String, val country: String?,
            val providerName: String, val displayName: String)

        private val rows = profiles.map { profile ->
            val location = country(profile.name)
            // Never index credentials, server hosts or subscription paths.
            val host = runCatching { profile.subscriptionUrl?.let { URI(it).host } }.getOrNull().orEmpty()
            val searchable = normalized(listOf(profile.name, profile.userSettings.personalName.orEmpty(),
                location.orEmpty(), profile.protocol, profileTransport(profile), host,
                subscriptionNames[profile.id].orEmpty().joinToString(" ")).joinToString(" "))
            Row(profile, searchable, location, normalized(profile.name),
                normalized(profile.userSettings.personalName ?: profile.name))
        }

        fun filter(query: String, favoritesOnly: Boolean, sort: ServerSort): List<ImportedProfile> {
            val terms = normalized(query).split(' ').filter { it.isNotEmpty() }
            val filtered = rows.filter { row ->
                (!favoritesOnly || row.profile.userSettings.favorite) && terms.all { it in row.searchable }
            }
            val sorted = when (sort) {
                ServerSort.MANUAL -> filtered.sortedBy { it.profile.userSettings.order }
                ServerSort.NAME -> filtered.sortedBy { it.displayName }
                ServerSort.COUNTRY -> filtered.sortedWith(compareBy<Row> { it.country == null }
                    .thenBy { it.country.orEmpty() }.thenBy { it.providerName })
                ServerSort.LATENCY -> filtered.sortedBy { it.profile.latencyMillis?.takeIf { value -> value > 0 } ?: Int.MAX_VALUE }
            }
            return sorted.map { it.profile }
        }
    }
}
