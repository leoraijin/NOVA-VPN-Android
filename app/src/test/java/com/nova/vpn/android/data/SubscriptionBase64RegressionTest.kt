package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SubscriptionBase64RegressionTest {
    private val link = "vless://11111111-1111-4111-8111-111111111111@fixture.example:443?security=tls&type=tcp#Fixture"

    @Test fun foldedBase64HasTheSameProfilesAsPlainTextForEveryWrappingWidth() {
        val plain = (1..9).joinToString("\n") { index -> link.replace(":443?", ":${440 + index}?") }
        val expected = ProfileLinkParser.parseText(plain).map { it.configurationKey() }
        val encoded = Base64.getEncoder().encodeToString(plain.toByteArray())
        for (width in listOf(4, 5, 37, 64, 76, 80, 128)) {
            for (separator in listOf("\r\n", "\n", " \n")) {
                val folded = encoded.chunked(width).joinToString(separator)
                assertEquals("width=$width separatorLength=${separator.length}", expected,
                    ProfileLinkParser.parseText(folded).map { it.configurationKey() })
            }
        }
    }

    @Test fun base64JsonAlsoAcceptsWhitespaceBeforePaddingIsCalculated() {
        val json = """[{"outbounds":[{"type":"vless","tag":"proxy","server":"fixture.example","server_port":443,"uuid":"11111111-1111-4111-8111-111111111111"}]}]"""
        val expected = ProfileLinkParser.parseText(json).single().configurationKey()
        val folded = Base64.getEncoder().withoutPadding().encodeToString(json.toByteArray()).chunked(37).joinToString("\r\n")
        assertEquals(expected, ProfileLinkParser.parseText(folded).single().configurationKey())
    }
}