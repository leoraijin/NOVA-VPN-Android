package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SubscriptionPlaceholdersTest {
    private val placeholder = ImportedProfile(
        id = "status", name = "Приложение не поддерживается", protocol = "vless",
        server = "0.0.0.0", port = 1, outboundJson = "{}"
    )
    private val good = placeholder.copy(id = "real", name = "Fixture", server = "vpn.example", port = 443)
    private fun link(host: String, label: String) =
        "vless://11111111-1111-1111-1111-111111111111@$host#$label"

    @Test fun removesOnlyExactStatusEndpointAndPreservesRealProfileIdentity() {
        assertEquals(listOf(good), SubscriptionPlaceholders.withoutPlaceholders(listOf(placeholder, good)))
        assertSame(good, SubscriptionPlaceholders.withoutPlaceholders(listOf(good)).single())
    }

    @Test fun nameAloneNeverDeletesRealServer() {
        for (profile in listOf(placeholder.copy(server = "vpn.example"),
            placeholder.copy(port = 443), placeholder.copy(name = "Custom"),
            placeholder.copy(protocol = "trojan"), placeholder.copy(fullConfigJson = "{}"))) {
            assertFalse(SubscriptionPlaceholders.isPlaceholder(profile))
        }
    }

    @Test fun uriAndBase64SubscriptionsDropStatusBeforeOrAfterRealServer() {
        val status = link("0.0.0.0:1", "Приложение%20не%20поддерживается")
        val real = link("vpn.example:443", "Fixture")
        for (body in listOf("$status\n$real", "$real\n$status")) {
            for (source in listOf(body, Base64.getEncoder().encodeToString(body.toByteArray()))) {
                assertEquals("vpn.example", ProfileLinkParser.parseText(source).single().server)
            }
        }
    }

    @Test fun jsonArrayOfXrayConfigsDropsPanelStatus() {
        fun config(host: String, port: Int, name: String): JSONObject {
            val user = JSONObject().put("id", "11111111-1111-1111-1111-111111111111")
            val server = JSONObject().put("address", host).put("port", port)
                .put("users", JSONArray().put(user))
            val outbound = JSONObject().put("protocol", "vless")
                .put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            return JSONObject().put("remarks", name).put("outbounds", JSONArray().put(outbound))
        }
        val body = JSONArray().put(config("vpn.example", 443, "Fixture"))
            .put(config("0.0.0.0", 1, placeholder.name)).toString()
        for (source in listOf(body, Base64.getEncoder().encodeToString(body.toByteArray()))) {
            assertEquals("vpn.example", ProfileLinkParser.parseText(source).single().server)
        }
    }

    @Test fun jsonArrayOfLinksDropsPanelStatus() {
        val body = JSONArray().put(link("0.0.0.0:1", "Приложение%20не%20поддерживается"))
            .put(link("vpn.example:443", "Fixture")).toString()
        assertEquals("vpn.example", ProfileLinkParser.parseText(body).single().server)
    }

    @Test fun placeholderOnlyReportsProviderResponseNotSuccessfulImport() {
        val result = runCatching { ProfileLinkParser.parseText(link("0.0.0.0:1", "Приложение%20не%20поддерживается")) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Рабочих серверов"))
    }

    @Test fun previouslyStoredStatusCanBeHiddenWithoutAnyRealProfiles() {
        assertTrue(SubscriptionPlaceholders.withoutPlaceholders(listOf(placeholder)).isEmpty())
        assertTrue(SubscriptionPlaceholders.withoutPlaceholders(emptyList()).isEmpty())
    }
}
