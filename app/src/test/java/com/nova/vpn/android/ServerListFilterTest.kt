package com.nova.vpn.android

import com.nova.vpn.android.data.ImportedProfile
import com.nova.vpn.android.data.ProfileUserSettings
import org.junit.Assert.*
import org.junit.Test

class ServerListFilterTest {
    @Test fun personalAndSubscriptionNamesAreSearchableWithoutChangingCountry() {
        val profile = first.copy(userSettings = first.userSettings.copy(personalName = "Мой узел"))
        val names = mapOf(profile.id to listOf("Рабочая подписка", "Резервная подписка"))
        for (query in listOf("мой узел", "рабочая", "резервная", "germany")) {
            assertEquals(listOf(profile), ServerListFilter.apply(listOf(profile), query, false, ServerSort.NAME, names) { "Germany" })
        }
        assertEquals("Германия", profile.name)
        assertTrue(ServerListFilter.apply(listOf(profile), "SECRET_TOKEN", false, ServerSort.NAME, names) { null }.isEmpty())
    }
    private val first = ImportedProfile(id = "a", name = "Германия", protocol = "vless", server = "secret-host.test", port = 443,
        outboundJson = """{"type":"vless","uuid":"SECRET_UUID","transport":{"type":"xhttp"}}""",
        subscriptionUrl = "https://provider.test/SECRET_TOKEN", userSettings = ProfileUserSettings(favorite = true))

    @Test fun normalizedMetadataSearchAndFavoriteCompose() {
        val second = first.copy(id = "b", userSettings = ProfileUserSettings())
        assertEquals(listOf(first), ServerListFilter.apply(listOf(first, second), "  ГЕРМАНИЯ   XHTTP ", true, ServerSort.MANUAL) { "Germany" })
        assertEquals(2, ServerListFilter.apply(listOf(first, second), "provider.test", false, ServerSort.NAME) { null }.size)
    }

    @Test fun secretsAreNeverSearchable() {
        for (secret in listOf("SECRET_UUID", "SECRET_TOKEN", "secret-host.test")) {
            assertTrue(ServerListFilter.apply(listOf(first), secret, false, ServerSort.MANUAL) { null }.isEmpty())
        }
    }

    @Test fun unknownLatencySortsLastAndReimportRetainsUserMetadata() {
        val fast = first.copy(id = "b", latencyMillis = 40)
        assertEquals("b", ServerListFilter.apply(listOf(first, fast), "", false, ServerSort.LATENCY) { null }.first().id)
        val incoming = first.copy(id = "new", userSettings = ProfileUserSettings())
        assertEquals(first.userSettings, ImportedProfile.merge(listOf(first), listOf(incoming)).single().userSettings)
        assertEquals(first, ImportedProfile.fromJson(first.toJson()))
    }

    @Test fun twoThousandProfilesKeepStableIdentityAndInputOrder() {
        val profiles = List(2000) { index -> first.copy(id = "node-$index", name = "Сервер $index",
            userSettings = ProfileUserSettings(favorite = index % 2 == 0)) }
        val filtered = ServerListFilter.apply(profiles, "СЕРВЕР xhttp", true, ServerSort.MANUAL) { "Germany" }
        assertEquals(1000, filtered.size)
        assertEquals(profiles.filter { it.userSettings.favorite }.map { it.id }, filtered.map { it.id })
        assertEquals(2000, profiles.size)
    }
}
